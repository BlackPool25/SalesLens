package com.shreyas.saleslens.batch.jdbc;

import com.shreyas.saleslens.service.ingestion.JdbcConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@StepScope
public class JdbcItemReader implements ItemReader<Map<String, String>>, ItemStream {

    private static final Logger log = LoggerFactory.getLogger(JdbcItemReader.class);

    private static final int DEFAULT_FETCH_SIZE = 1000;

    private final JdbcConnectionService jdbcConnectionService;
    private final String sourceId;

    private Connection connection;
    private Statement statement;
    private ResultSet resultSet;
    private ResultSetMetaData metaData;
    private int columnCount;

    public JdbcItemReader(
            JdbcConnectionService jdbcConnectionService,
            @Value("#{jobParameters['sourceId']}") String sourceId) {
        this.jdbcConnectionService = jdbcConnectionService;
        this.sourceId = sourceId;
    }

    @Override
    public void open(ExecutionContext ctx) {
        UUID sourceUuid = UUID.fromString(sourceId);
        javax.sql.DataSource dataSource = jdbcConnectionService.getDataSource(sourceUuid);
        String query = jdbcConnectionService.getQuery(sourceUuid);

        try {
            this.connection = dataSource.getConnection();
            this.statement = connection.createStatement();
            this.statement.setFetchSize(DEFAULT_FETCH_SIZE);
            this.resultSet = statement.executeQuery(query);
            this.metaData = resultSet.getMetaData();
            this.columnCount = metaData.getColumnCount();
            log.debug("Opened JDBC reader for source {} ({} columns)", sourceId, columnCount);
        } catch (SQLException e) {
            close();
            throw new IllegalStateException("Failed to execute JDBC query for source " + sourceId, e);
        }
    }

    @Override
    public Map<String, String> read() {
        try {
            if (resultSet == null || !resultSet.next()) {
                return null;
            }

            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                String value = resultSet.getString(i);
                row.put(columnName, value);
            }
            return row;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read row from JDBC source " + sourceId, e);
        }
    }

    @Override
    public void close() {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                log.warn("Failed to close ResultSet for source {}", sourceId, e);
            }
        }
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                log.warn("Failed to close Statement for source {}", sourceId, e);
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn("Failed to close Connection for source {}", sourceId, e);
            }
        }
        log.debug("Closed JDBC reader for source {}", sourceId);
    }

    @Override
    public void update(ExecutionContext ctx) {
        // no-op
    }
}
