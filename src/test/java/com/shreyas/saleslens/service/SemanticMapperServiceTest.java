package com.shreyas.saleslens.service;

import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.InferredType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.FieldMappingRepository;
import com.shreyas.saleslens.repository.SourceSchemaFieldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SemanticMapperServiceTest {

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Mock
    private FieldMappingRepository fieldMappingRepository;

    @Mock
    private SourceSchemaFieldRepository sourceSchemaFieldRepository;

    @InjectMocks
    private SemanticMapperService semanticMapperService;

    private UUID sourceId;
    private DataSource dataSource;
    private SourceSchema schema;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        dataSource = new DataSource();
        dataSource.setId(sourceId);

        schema = new SourceSchema();
        schema.setId(UUID.randomUUID());
        schema.setSource(dataSource);
        schema.setVersion(1);
    }

    @Test
    void testGenerateMappings_ExactMatch() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("Order Date");
        field.setInferredType(InferredType.DATE);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        assertEquals("Order Date", m.getSourceFieldName());
        assertEquals("orders", m.getCanonicalEntity());
        assertEquals("order_date", m.getCanonicalField());
        assertEquals(0, BigDecimal.valueOf(1.0).compareTo(m.getConfidence()));
        assertEquals("AUTO_CONFIRMED", m.getStatus());
    }

    @Test
    void testGenerateMappings_LevenshteinMatch() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("Ordr Date"); // distance 1 from order_date
        field.setInferredType(InferredType.DATE);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        assertEquals("orders", m.getCanonicalEntity());
        assertEquals("order_date", m.getCanonicalField());
        assertEquals(0, BigDecimal.valueOf(0.85).compareTo(m.getConfidence()));
        assertEquals("AUTO_CONFIRMED", m.getStatus());
    }

    @Test
    void testGenerateMappings_TokenOverlapMatch() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        // Target: shipping_cost (synonyms: shipping_cost, freight, shipping_fee)
        // Synonym shipping_fee has tokens: shipping, fee.
        // Source field shipping_cost_fee has tokens: shipping, cost, fee.
        // intersection: shipping, fee (size 2). union: shipping, cost, fee (size 3).
        // intersection / union = 2 / 3 = 0.67 >= 0.5.
        // This yields token overlap confidence of 0.70.
        field.setFieldName("shipping_cost_fee");
        field.setInferredType(InferredType.DECIMAL);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        assertEquals("orders", m.getCanonicalEntity());
        assertEquals("shipping_cost", m.getCanonicalField());
        assertEquals(0, BigDecimal.valueOf(0.70).compareTo(m.getConfidence()));
        assertEquals("PENDING", m.getStatus());
    }

    @Test
    void testGenerateMappings_TypeMatch() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("Random Date Column");
        field.setInferredType(InferredType.DATE);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        // Will match order_date or ship_date based on type fallback
        assertEquals("orders", m.getCanonicalEntity());
        assertTrue(m.getCanonicalField().equals("order_date") || m.getCanonicalField().equals("ship_date"));
        assertEquals(0, BigDecimal.valueOf(0.55).compareTo(m.getConfidence()));
        assertEquals("PENDING", m.getStatus());
    }

    @Test
    void testGenerateMappings_Ignored() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("completely_random_field_name");
        field.setInferredType(InferredType.BOOLEAN); // No BOOLEAN expected type in registry
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        assertEquals("", m.getCanonicalEntity());
        assertEquals("", m.getCanonicalField());
        assertEquals(0, BigDecimal.ZERO.compareTo(m.getConfidence()));
        assertEquals("IGNORED", m.getStatus());
    }

    @Test
    void testGenerateMappings_NullFieldName() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName(null);
        field.setInferredType(InferredType.DATE);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        assertNull(m.getSourceFieldName());
        assertEquals("", m.getCanonicalEntity());
        assertEquals("", m.getCanonicalField());
        assertEquals(0, BigDecimal.ZERO.compareTo(m.getConfidence()));
        assertEquals("IGNORED", m.getStatus());
    }

    @Test
    void testGenerateMappings_EmptyFieldName() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("");
        field.setInferredType(InferredType.DECIMAL);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        assertEquals("", m.getSourceFieldName());
        assertEquals("", m.getCanonicalEntity());
        assertEquals("", m.getCanonicalField());
        assertEquals(0, BigDecimal.ZERO.compareTo(m.getConfidence()));
        assertEquals("IGNORED", m.getStatus());
    }

    @Test
    void testGenerateMappings_SpecialCharacters() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("$#@! Order * & Date)(");
        field.setInferredType(InferredType.DATE);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        assertEquals("$#@! Order * & Date)(", m.getSourceFieldName());
        assertEquals("orders", m.getCanonicalEntity());
        assertEquals("order_date", m.getCanonicalField());
        assertEquals(0, BigDecimal.valueOf(1.00).compareTo(m.getConfidence()));
        assertEquals("AUTO_CONFIRMED", m.getStatus());
    }

    @Test
    void testGenerateMappings_NullInferredType() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("Order Date");
        field.setInferredType(null);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        assertEquals("Order Date", m.getSourceFieldName());
        assertEquals("orders", m.getCanonicalEntity());
        assertEquals("order_date", m.getCanonicalField());
        assertEquals(0, BigDecimal.valueOf(1.00).compareTo(m.getConfidence()));
        assertEquals("AUTO_CONFIRMED", m.getStatus());
    }

    @Test
    void testGenerateMappings_DataSourceNotFound() {
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            semanticMapperService.generateMappings(sourceId, schema);
        });
    }
}
