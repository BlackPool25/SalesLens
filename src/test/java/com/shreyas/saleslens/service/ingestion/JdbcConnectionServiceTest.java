package com.shreyas.saleslens.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JdbcConnectionService}.
 * <p>
 * Uses Mockito for repository/encryption mocks and a real {@link ObjectMapper}
 * for JSON parsing. HikariCP pools are created with
 * {@code initializationFailTimeout(-1)} to skip connectivity checks.
 */
@ExtendWith(MockitoExtension.class)
class JdbcConnectionServiceTest {

    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final String VALID_CONFIG = """
            {
                "jdbcUrl": "jdbc:postgresql://localhost:5432/testdb",
                "user": "testuser",
                "password": "encrypted_password_value",
                "driverClassName": "org.postgresql.Driver",
                "query": "SELECT * FROM test_table"
            }
            """;
    private static final String DECRYPTED_PASSWORD = "decrypted_s3cret!";

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Mock
    private CredentialEncryptionService credentialEncryptionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JdbcConnectionService service;

    @BeforeEach
    void setUp() {
        service = new JdbcConnectionService(dataSourceRepository, credentialEncryptionService, objectMapper);
    }

    @AfterEach
    void tearDown() {
        // Evict any DataSource left in cache to close HikariCP pools
        service.evictDataSource(SOURCE_ID);
    }

    // ---------------------------------------------------------------
    // Helper: create a mock DataSource entity with a connectionConfig
    // ---------------------------------------------------------------

    private DataSource createMockDataSource(String configJson) {
        DataSource source = mock(DataSource.class);
        lenient().when(source.getConnectionConfig()).thenReturn(configJson);
        return source;
    }

    private void stubRepositoryFindById(UUID sourceId, DataSource source) {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
    }

    // ---------------------------------------------------------------
    // 1. Happy: getDataSource creates DataSource from valid config
    // ---------------------------------------------------------------

    @Test
    void getDataSource_validConfig_createsHikariDataSource() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        stubRepositoryFindById(SOURCE_ID, source);
        when(credentialEncryptionService.decrypt("encrypted_password_value")).thenReturn(DECRYPTED_PASSWORD);

        javax.sql.DataSource ds = service.getDataSource(SOURCE_ID);

        assertThat(ds).isInstanceOf(HikariDataSource.class);
        HikariDataSource hds = (HikariDataSource) ds;
        assertThat(hds.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/testdb");
        assertThat(hds.getUsername()).isEqualTo("testuser");
        assertThat(hds.getMaximumPoolSize()).isEqualTo(1);
        assertThat(hds.getConnectionTimeout()).isEqualTo(10000);
        assertThat(hds.getPoolName()).startsWith("jdbc-");

        // Verify password was decrypted
        verify(credentialEncryptionService).decrypt("encrypted_password_value");
    }

    // ---------------------------------------------------------------
    // 2. Second call returns cached instance (identity check)
    // ---------------------------------------------------------------

    @Test
    void getDataSource_cached_returnsSameInstance() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        stubRepositoryFindById(SOURCE_ID, source);
        when(credentialEncryptionService.decrypt("encrypted_password_value")).thenReturn(DECRYPTED_PASSWORD);

        javax.sql.DataSource ds1 = service.getDataSource(SOURCE_ID);
        javax.sql.DataSource ds2 = service.getDataSource(SOURCE_ID);

        // Identity check — same instance from cache
        assertThat(ds1).isSameAs(ds2);

        // repository and encryption should only be called once
        verify(dataSourceRepository, times(1)).findById(SOURCE_ID);
        verify(credentialEncryptionService, times(1)).decrypt(anyString());
    }

    // ---------------------------------------------------------------
    // 3. Missing/invalid connectionConfig → IllegalArgumentException
    // ---------------------------------------------------------------

    @Test
    void getDataSource_nullConfig_throwsIllegalArgumentException() {
        DataSource source = createMockDataSource(null);
        stubRepositoryFindById(SOURCE_ID, source);

        assertThatThrownBy(() -> service.getDataSource(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionConfig is required");

        verify(dataSourceRepository).findById(SOURCE_ID);
        verifyNoInteractions(credentialEncryptionService);
    }

    @Test
    void getDataSource_blankConfig_throwsIllegalArgumentException() {
        DataSource source = createMockDataSource("   ");
        stubRepositoryFindById(SOURCE_ID, source);

        assertThatThrownBy(() -> service.getDataSource(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectionConfig is required");

        verifyNoInteractions(credentialEncryptionService);
    }

    // ---------------------------------------------------------------
    // 4. Corrupted encrypted password → decryption failure → throw
    // ---------------------------------------------------------------

    @Test
    void getDataSource_decryptionFailure_throwsIllegalArgumentException() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        stubRepositoryFindById(SOURCE_ID, source);
        when(credentialEncryptionService.decrypt("encrypted_password_value"))
                .thenThrow(new IllegalArgumentException("Decryption failed: corrupted data"));

        assertThatThrownBy(() -> service.getDataSource(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to create DataSource")
                .hasMessageContaining("Decryption failed");
    }

    // ---------------------------------------------------------------
    // 5. JSON parse failure → throw
    // ---------------------------------------------------------------

    @Test
    void getDataSource_invalidJson_throwsIllegalArgumentException() {
        DataSource source = createMockDataSource("{invalid json!!!}");
        stubRepositoryFindById(SOURCE_ID, source);

        assertThatThrownBy(() -> service.getDataSource(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to create DataSource");
    }

    // ---------------------------------------------------------------
    // 6. Missing required field → throw
    // ---------------------------------------------------------------

    @Test
    void getDataSource_missingJdbcUrl_throwsIllegalArgumentException() {
        String configMissingUrl = """
                {
                    "user": "testuser",
                    "password": "encrypted",
                    "driverClassName": "org.postgresql.Driver"
                }
                """;
        DataSource source = createMockDataSource(configMissingUrl);
        stubRepositoryFindById(SOURCE_ID, source);

        assertThatThrownBy(() -> service.getDataSource(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required field 'jdbcUrl'");
    }

    @Test
    void getDataSource_missingPassword_throwsIllegalArgumentException() {
        String configMissingPassword = """
                {
                    "jdbcUrl": "jdbc:postgresql://localhost:5432/testdb",
                    "user": "testuser",
                    "driverClassName": "org.postgresql.Driver"
                }
                """;
        DataSource source = createMockDataSource(configMissingPassword);
        stubRepositoryFindById(SOURCE_ID, source);

        assertThatThrownBy(() -> service.getDataSource(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required field 'password'");
    }

    @Test
    void getDataSource_blankField_throwsIllegalArgumentException() {
        String configBlankField = """
                {
                    "jdbcUrl": "",
                    "user": "testuser",
                    "password": "encrypted",
                    "driverClassName": "org.postgresql.Driver"
                }
                """;
        DataSource source = createMockDataSource(configBlankField);
        stubRepositoryFindById(SOURCE_ID, source);

        assertThatThrownBy(() -> service.getDataSource(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required field 'jdbcUrl'");
    }

    // ---------------------------------------------------------------
    // 7. Evict: removes from cache, next get creates new
    // ---------------------------------------------------------------

    @Test
    void evictDataSource_removesFromCache_nextCallCreatesNew() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        stubRepositoryFindById(SOURCE_ID, source);
        when(credentialEncryptionService.decrypt("encrypted_password_value")).thenReturn(DECRYPTED_PASSWORD);

        javax.sql.DataSource ds1 = service.getDataSource(SOURCE_ID);
        service.evictDataSource(SOURCE_ID);
        javax.sql.DataSource ds2 = service.getDataSource(SOURCE_ID);

        assertThat(ds1).isNotSameAs(ds2);
        assertThat(ds1).isInstanceOf(HikariDataSource.class);
        assertThat(ds2).isInstanceOf(HikariDataSource.class);

        // The first DataSource should be closed after eviction
        assertThat(((HikariDataSource) ds1).isClosed()).isTrue();
        assertThat(((HikariDataSource) ds2).isClosed()).isFalse();

        // repository and encryption called twice (second creation after eviction)
        verify(dataSourceRepository, times(2)).findById(SOURCE_ID);
        verify(credentialEncryptionService, times(2)).decrypt(anyString());

        // Cleanup second DataSource
        service.evictDataSource(SOURCE_ID);
    }

    @Test
    void evictDataSource_nonExistentSource_noOp() {
        // Should not throw when evicting a source not in cache
        UUID randomId = UUID.randomUUID();
        service.evictDataSource(randomId);
        // No assertion — confirming no exception thrown
    }

    // ---------------------------------------------------------------
    // 8. Security: no password in log output
    // ---------------------------------------------------------------

    @Test
    void getDataSource_logDoesNotContainPassword() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        stubRepositoryFindById(SOURCE_ID, source);
        when(credentialEncryptionService.decrypt("encrypted_password_value")).thenReturn(DECRYPTED_PASSWORD);

        // Capture log output using Logback ListAppender
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(JdbcConnectionService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender =
                new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            service.getDataSource(SOURCE_ID);

            // Verify no log event contains the decrypted password or encrypted password
            assertThat(listAppender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains(DECRYPTED_PASSWORD));
            assertThat(listAppender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains("encrypted_password_value"));
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    // ---------------------------------------------------------------
    // 9. getQuery: Returns valid SQL from config
    // ---------------------------------------------------------------

    @Test
    void getQuery_validConfig_returnsSql() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        stubRepositoryFindById(SOURCE_ID, source);

        String query = service.getQuery(SOURCE_ID);

        assertThat(query).isEqualTo("SELECT * FROM test_table");
    }

    // ---------------------------------------------------------------
    // 10. getQuery: Warns on non-SELECT query
    // ---------------------------------------------------------------

    @Test
    void getQuery_nonSelectQuery_logsWarning() {
        String configWithDelete = """
                {
                    "query": "DELETE FROM test_table WHERE id = 1"
                }
                """;
        DataSource source = createMockDataSource(configWithDelete);
        stubRepositoryFindById(SOURCE_ID, source);

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(JdbcConnectionService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender =
                new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            String query = service.getQuery(SOURCE_ID);

            assertThat(query).isEqualTo("DELETE FROM test_table WHERE id = 1");
            assertThat(listAppender.list)
                    .anyMatch(event ->
                            event.getFormattedMessage().contains("non-SELECT query")
                                    && event.getFormattedMessage().contains(SOURCE_ID.toString()));
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    @Test
    void getQuery_selectQuery_doesNotLogWarning() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        stubRepositoryFindById(SOURCE_ID, source);

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(JdbcConnectionService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender =
                new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            service.getQuery(SOURCE_ID);

            assertThat(listAppender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains("non-SELECT query"));
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    // ---------------------------------------------------------------
    // 11. getCronExpression: Returns cronExpression from entity
    // ---------------------------------------------------------------

    @Test
    void getCronExpression_validSource_returnsCron() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        when(source.getCronExpression()).thenReturn("0 */5 * * * ?");
        stubRepositoryFindById(SOURCE_ID, source);

        String cron = service.getCronExpression(SOURCE_ID);

        assertThat(cron).isEqualTo("0 */5 * * * ?");
    }

    @Test
    void getCronExpression_nullCron_returnsNull() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        when(source.getCronExpression()).thenReturn(null);
        stubRepositoryFindById(SOURCE_ID, source);

        String cron = service.getCronExpression(SOURCE_ID);

        assertThat(cron).isNull();
    }

    // ---------------------------------------------------------------
    // Additional edge cases
    // ---------------------------------------------------------------

    @Test
    void getDataSource_sourceNotFound_throwsIllegalArgumentException() {
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDataSource(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source not found");
    }

    @Test
    void getQuery_sourceNotFound_throwsIllegalArgumentException() {
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getQuery(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source not found");
    }

    @Test
    void getQuery_missingQueryField_throwsIllegalArgumentException() {
        String configWithoutQuery = """
                {
                    "jdbcUrl": "jdbc:postgresql://localhost:5432/testdb",
                    "user": "testuser",
                    "password": "encrypted",
                    "driverClassName": "org.postgresql.Driver"
                }
                """;
        DataSource source = createMockDataSource(configWithoutQuery);
        stubRepositoryFindById(SOURCE_ID, source);

        // The outer exception is wrapped by the catch block; root cause has the detail
        assertThatThrownBy(() -> service.getQuery(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse query");
    }

    @Test
    void getQuery_blankQuery_throwsIllegalArgumentException() {
        String configBlankQuery = """
                {
                    "query": ""
                }
                """;
        DataSource source = createMockDataSource(configBlankQuery);
        stubRepositoryFindById(SOURCE_ID, source);

        assertThatThrownBy(() -> service.getQuery(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse query");
    }

    @Test
    void getCronExpression_sourceNotFound_throwsIllegalArgumentException() {
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCronExpression(SOURCE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source not found");
    }

    @Test
    void evictDataSource_closesHikariPool() {
        DataSource source = createMockDataSource(VALID_CONFIG);
        stubRepositoryFindById(SOURCE_ID, source);
        when(credentialEncryptionService.decrypt("encrypted_password_value")).thenReturn(DECRYPTED_PASSWORD);

        javax.sql.DataSource ds = service.getDataSource(SOURCE_ID);
        HikariDataSource hds = (HikariDataSource) ds;

        assertThat(hds.isClosed()).isFalse();

        service.evictDataSource(SOURCE_ID);

        assertThat(hds.isClosed()).isTrue();
    }
}
