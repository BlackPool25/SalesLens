package com.shreyas.saleslens.batch.jdbc;

import com.shreyas.saleslens.service.ingestion.JdbcConnectionService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcItemReaderTest {

    @Mock
    private JdbcConnectionService jdbcConnectionService;

    private final UUID sourceId = UUID.randomUUID();

    private HikariDataSource dataSource;

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Sets up an H2 in-memory DataSource, creates a table with the given
     * DDL, inserts rows, and wires mocks to return it.
     */
    private void setupH2(String dbName, String createSql, String... insertSqls) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(1);
        config.setInitializationFailTimeout(-1);
        dataSource = new HikariDataSource(config);

        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.execute(createSql);
            for (String sql : insertSqls) {
                s.execute(sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set up H2 database '" + dbName + "'", e);
        }

        when(jdbcConnectionService.getDataSource(sourceId)).thenReturn(dataSource);
        when(jdbcConnectionService.getQuery(sourceId)).thenReturn("SELECT * FROM " + dbName + " ORDER BY 1");
    }

    // ---------------------------------------------------------------
    // 1) Query returns 3 rows → 3 Map entries read
    // ---------------------------------------------------------------
    @Test
    void read_threeRows_returnsThreeMaps() {
        setupH2("TEST_THREE",
                "CREATE TABLE TEST_THREE (id INT, name VARCHAR(50), email VARCHAR(100))",
                "INSERT INTO TEST_THREE VALUES (1, 'Alice', 'alice@test.com')",
                "INSERT INTO TEST_THREE VALUES (2, 'Bob', 'bob@test.com')",
                "INSERT INTO TEST_THREE VALUES (3, 'Charlie', 'charlie@test.com')");

        JdbcItemReader reader = new JdbcItemReader(jdbcConnectionService, sourceId.toString());
        reader.open(new ExecutionContext());

        Map<String, String> r1 = reader.read();
        assertThat(r1).isNotNull().hasSize(3);
        assertThat(r1)
                .containsEntry("ID", "1")
                .containsEntry("NAME", "Alice")
                .containsEntry("EMAIL", "alice@test.com");

        Map<String, String> r2 = reader.read();
        assertThat(r2).isNotNull().hasSize(3);
        assertThat(r2).containsEntry("ID", "2");

        Map<String, String> r3 = reader.read();
        assertThat(r3).isNotNull().hasSize(3);
        assertThat(r3).containsEntry("ID", "3");

        assertThat(reader.read()).isNull();

        reader.close();
    }

    // ---------------------------------------------------------------
    // 2) Empty result → read() returns null immediately
    // ---------------------------------------------------------------
    @Test
    void read_emptyResult_returnsNull() {
        setupH2("TEST_EMPTY",
                "CREATE TABLE TEST_EMPTY (id INT, name VARCHAR(50))",
                // No data rows
                "INSERT INTO TEST_EMPTY SELECT * FROM (VALUES (1, 'dummy')) t WHERE 1=0");

        JdbcItemReader reader = new JdbcItemReader(jdbcConnectionService, sourceId.toString());
        reader.open(new ExecutionContext());

        assertThat(reader.read()).isNull();

        reader.close();
    }

    // ---------------------------------------------------------------
    // 3) NULL in result set → null value in Map
    // ---------------------------------------------------------------
    @Test
    void read_nullValue_returnsNullInMap() {
        setupH2("TEST_NULL",
                "CREATE TABLE TEST_NULL (id INT, name VARCHAR(50), nullable_col VARCHAR(50))",
                "INSERT INTO TEST_NULL VALUES (1, 'Alice', NULL)");

        JdbcItemReader reader = new JdbcItemReader(jdbcConnectionService, sourceId.toString());
        reader.open(new ExecutionContext());

        Map<String, String> row = reader.read();
        assertThat(row).isNotNull();
        assertThat(row).containsEntry("ID", "1");
        assertThat(row).containsEntry("NAME", "Alice");
        assertThat(row).containsKey("NULLABLE_COL");
        assertThat(row.get("NULLABLE_COL")).isNull();

        assertThat(reader.read()).isNull();

        reader.close();
    }

    // ---------------------------------------------------------------
    // 4) Numeric column → string representation in Map
    // ---------------------------------------------------------------
    @Test
    void read_numericColumn_returnsStringRepresentation() {
        setupH2("TEST_NUM",
                "CREATE TABLE TEST_NUM (id INT, price DECIMAL(10,2), quantity BIGINT)",
                "INSERT INTO TEST_NUM VALUES (1, 49.99, 100)");

        JdbcItemReader reader = new JdbcItemReader(jdbcConnectionService, sourceId.toString());
        reader.open(new ExecutionContext());

        Map<String, String> row = reader.read();
        assertThat(row).isNotNull();
        // H2 DECIMAL getString() may return "49.99" — verify the numeric value
        assertThat(row).containsEntry("ID", "1");
        assertThat(row.get("PRICE")).contains("49.99");
        assertThat(row).containsEntry("QUANTITY", "100");

        reader.close();
    }

    // ---------------------------------------------------------------
    // 5) Column order preserved (LinkedHashMap)
    // ---------------------------------------------------------------
    @Test
    void read_maintainsColumnOrder() {
        setupH2("TEST_ORDER",
                "CREATE TABLE TEST_ORDER (z_col VARCHAR(10), a_col VARCHAR(10), m_col VARCHAR(10))",
                "INSERT INTO TEST_ORDER VALUES ('z-val', 'a-val', 'm-val')");

        JdbcItemReader reader = new JdbcItemReader(jdbcConnectionService, sourceId.toString());
        reader.open(new ExecutionContext());

        Map<String, String> row = reader.read();
        assertThat(row).isNotNull();
        assertThat(row.keySet()).containsExactly("Z_COL", "A_COL", "M_COL");

        reader.close();
    }

    // ---------------------------------------------------------------
    // 6) Close releases resources (connection returned to pool)
    // ---------------------------------------------------------------
    @Test
    void close_releasesResources() {
        setupH2("TEST_CLOSE",
                "CREATE TABLE TEST_CLOSE (id INT, name VARCHAR(50))",
                "INSERT INTO TEST_CLOSE VALUES (1, 'Alice')");

        JdbcItemReader reader = new JdbcItemReader(jdbcConnectionService, sourceId.toString());
        reader.open(new ExecutionContext());

        // Verify we can read
        assertThat(reader.read()).isNotNull();

        // Close
        reader.close();

        // After close, connection should be closed (HikariCP returns it to pool,
        // but since pool size is 1 and we closed it, the underlying connection is closed).
        // Verify no exception — the resources are safely released.
        // Also verify we can get a new connection from the DataSource
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) cnt FROM TEST_CLOSE")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("CNT")).isEqualTo(1);
        } catch (SQLException e) {
            throw new RuntimeException("DataSource should still be usable after reader close", e);
        }
    }
}
