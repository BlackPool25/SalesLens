package com.shreyas.saleslens.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class JdbcConnectionService {

    private final DataSourceRepository dataSourceRepository;
    private final CredentialEncryptionService credentialEncryptionService;
    private final ObjectMapper objectMapper;

    // Cache of active DataSources per sourceId
    private final Map<UUID, javax.sql.DataSource> dataSourceCache = new ConcurrentHashMap<>();

    public javax.sql.DataSource getDataSource(UUID sourceId) {
        return dataSourceCache.computeIfAbsent(sourceId, this::createDataSource);
    }

    private javax.sql.DataSource createDataSource(UUID sourceId) {
        DataSource source = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        String configJson = source.getConnectionConfig();
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("connectionConfig is required for JDBC source " + sourceId);
        }

        try {
            JsonNode config = objectMapper.readTree(configJson);
            String jdbcUrl = getRequiredField(config, "jdbcUrl", sourceId);
            String user = getRequiredField(config, "user", sourceId);
            String encryptedPassword = getRequiredField(config, "password", sourceId);
            String driverClassName = getRequiredField(config, "driverClassName", sourceId);

            String decryptedPassword = credentialEncryptionService.decrypt(encryptedPassword);

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(user);
            hikariConfig.setPassword(decryptedPassword);
            hikariConfig.setDriverClassName(driverClassName);
            hikariConfig.setConnectionTimeout(10000);   // 10 seconds
            hikariConfig.setMaximumPoolSize(1);          // 1 per source (lightweight)
            hikariConfig.setInitializationFailTimeout(-1); // lazy validation: don't fail on pool create
            hikariConfig.setPoolName("jdbc-" + sourceId.toString().substring(0, 8));

            log.info("Created DataSource for JDBC source {}", sourceId);
            return new HikariDataSource(hikariConfig);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create DataSource for JDBC source " + sourceId + ": " + e.getMessage(), e);
        }
    }

    public void evictDataSource(UUID sourceId) {
        javax.sql.DataSource ds = dataSourceCache.remove(sourceId);
        if (ds instanceof HikariDataSource hikariDs) {
            hikariDs.close();
            log.info("Evicted and closed DataSource for JDBC source {}", sourceId);
        }
    }

    public String getQuery(UUID sourceId) {
        DataSource source = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));
        try {
            JsonNode config = objectMapper.readTree(source.getConnectionConfig());
            JsonNode query = config.get("query");
            if (query == null || query.asText().isBlank()) {
                throw new IllegalArgumentException("query is required in connectionConfig for source " + sourceId);
            }
            // SQL injection guard: warn for non-SELECT queries
            String sql = query.asText().trim();
            if (!sql.toUpperCase().startsWith("SELECT")) {
                log.warn("JDBC source {} has a non-SELECT query: {}", sourceId, sql);
            }
            return sql;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse query for source " + sourceId, e);
        }
    }

    public String getCronExpression(UUID sourceId) {
        DataSource source = dataSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));
        return source.getCronExpression();
    }

    private String getRequiredField(JsonNode config, String field, UUID sourceId) {
        JsonNode node = config.get(field);
        if (node == null || node.asText().isBlank()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in connectionConfig for source " + sourceId);
        }
        return node.asText();
    }
}
