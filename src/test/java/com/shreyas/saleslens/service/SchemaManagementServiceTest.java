package com.shreyas.saleslens.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SchemaManagementServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TransformationService transformationService;

    @InjectMocks
    private SchemaManagementService schemaManagementService;

    @Test
    void testPromoteAttribute_Success() {
        schemaManagementService.promoteAttribute("orders", "discount", "DECIMAL");

        verify(jdbcTemplate).execute("ALTER TABLE canonical.orders ADD COLUMN discount NUMERIC(15, 2)");
        verify(jdbcTemplate).update(
                contains("UPDATE canonical.orders SET discount = jsonb_extract_path_text(additional_attributes, ?)::NUMERIC(15, 2)"),
                eq("discount"),
                eq("discount")
        );
        verify(jdbcTemplate).update(
                contains("UPDATE canonical.orders SET additional_attributes = additional_attributes - ?"),
                eq("discount"),
                eq("discount")
        );
        verify(transformationService).clearSchemaCache();
    }

    @Test
    void testPromoteAttribute_InvalidEntity() {
        assertThrows(IllegalArgumentException.class, () ->
                schemaManagementService.promoteAttribute("users", "discount", "DECIMAL")
        );
    }

    @Test
    void testPromoteAttribute_InvalidKey() {
        assertThrows(IllegalArgumentException.class, () ->
                schemaManagementService.promoteAttribute("orders", "discount; DROP TABLE canonical.orders; --", "DECIMAL")
        );
    }

    @Test
    void testDemoteColumn_Success() {
        schemaManagementService.demoteColumn("orders", "discount");

        verify(jdbcTemplate).update(
                contains("UPDATE canonical.orders SET additional_attributes = COALESCE(additional_attributes, '{}'::jsonb) || jsonb_build_object(?, discount)"),
                eq("discount")
        );
        verify(jdbcTemplate).execute("ALTER TABLE canonical.orders DROP COLUMN discount");
        verify(transformationService).clearSchemaCache();
    }
}
