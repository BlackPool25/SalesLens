package com.shreyas.saleslens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaManagementService {

    private final JdbcTemplate jdbcTemplate;
    private final TransformationService transformationService;

    private static final Set<String> ALLOWED_ENTITIES = Set.of(
            "customers", "products", "salespersons", "orders", "order_line_items"
    );

    @Transactional
    public void promoteAttribute(String entityName, String attributeKey, String dataType) {
        validateInputs(entityName, attributeKey);

        String postgresType = getPostgresType(dataType);
        log.info("Promoting JSONB attribute '{}' to dedicated column on entity '{}' with type '{}'", 
                attributeKey, entityName, postgresType);

        // 1. Execute dynamic DDL
        String addColumnSql = String.format("ALTER TABLE canonical.%s ADD COLUMN %s %s", entityName, attributeKey, postgresType);
        jdbcTemplate.execute(addColumnSql);

        // 2. Migrate data from JSONB to new column
        String migrateSql = String.format(
                "UPDATE canonical.%s SET %s = jsonb_extract_path_text(additional_attributes, ?)::%s " +
                "WHERE additional_attributes IS NOT NULL AND jsonb_extract_path_text(additional_attributes, ?) IS NOT NULL",
                entityName, attributeKey, postgresType
        );
        jdbcTemplate.update(migrateSql, attributeKey, attributeKey);

        // 3. Remove key from JSONB column
        String removeJsonbSql = String.format(
                "UPDATE canonical.%s SET additional_attributes = additional_attributes - ? " +
                "WHERE additional_attributes IS NOT NULL AND jsonb_extract_path_text(additional_attributes, ?) IS NOT NULL",
                entityName
        );
        jdbcTemplate.update(removeJsonbSql, attributeKey, attributeKey);

        // 4. Update memory registry metadata
        SemanticMapperService.registerCanonicalField(entityName, attributeKey, dataType);

        // 5. Invalidate column schema cache
        transformationService.clearSchemaCache();
    }

    @Transactional
    public void demoteColumn(String entityName, String columnName) {
        validateInputs(entityName, columnName);
        log.info("Demoting column '{}' back to JSONB attribute on entity '{}'", columnName, entityName);

        // 1. Move column data to JSONB additional_attributes
        String migrateBackSql = String.format(
                "UPDATE canonical.%s SET additional_attributes = COALESCE(additional_attributes, '{}'::jsonb) || jsonb_build_object(?, %s) " +
                "WHERE %s IS NOT NULL",
                entityName, columnName, columnName
        );
        jdbcTemplate.update(migrateBackSql, columnName);

        // 2. Drop the column
        String dropColumnSql = String.format("ALTER TABLE canonical.%s DROP COLUMN %s", entityName, columnName);
        jdbcTemplate.execute(dropColumnSql);

        // 3. Update memory registry metadata
        SemanticMapperService.deregisterCanonicalField(entityName, columnName);

        // 4. Invalidate column schema cache
        transformationService.clearSchemaCache();
    }

    private void validateInputs(String entityName, String identifier) {
        if (!ALLOWED_ENTITIES.contains(entityName.toLowerCase())) {
            throw new IllegalArgumentException("Entity name is not allowed: " + entityName);
        }
        if (identifier == null || !identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Identifier contains illegal characters: " + identifier);
        }
    }

    private String getPostgresType(String dataType) {
        if (dataType == null) {
            return "VARCHAR(255)";
        }
        switch (dataType.toUpperCase()) {
            case "DECIMAL":
            case "CURRENCY_AMOUNT":
                return "NUMERIC(15, 2)";
            case "INTEGER":
                return "INTEGER";
            case "DATE":
                return "DATE";
            case "BOOLEAN":
                return "BOOLEAN";
            case "FREE_TEXT":
            case "EMAIL":
            case "PHONE":
            case "CATEGORY":
            default:
                return "VARCHAR(255)";
        }
    }
}
