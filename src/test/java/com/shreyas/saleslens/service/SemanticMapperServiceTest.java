package com.shreyas.saleslens.service;

import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.InferredType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.FieldMappingRepository;
import com.shreyas.saleslens.repository.SourceSchemaFieldRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SemanticMapperServiceTest {

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Mock
    private FieldMappingRepository fieldMappingRepository;

    @Mock
    private SourceSchemaFieldRepository sourceSchemaFieldRepository;

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @Mock
    private JdbcTemplate jdbcTemplate;

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

        // Default LLM mock: return empty/invalid to ensure heuristic path is taken
        // unless overridden in specific tests
        lenient().when(chatLanguageModel.chat(any()))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("{\"canonicalEntity\": \"\", \"canonicalField\": \"\", \"confidence\": 0.0}"))
                        .build());
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

    // --- New LLM-related tests (heuristic-primary with optional LLM overlay) ---

    @Test
    void testLlmOverridesHeuristicWhenHigherConfidence() {
        // Heuristic: "Customer Name" → customer.name confidence=1.0 (exact match via synonym)
        // LLM says: customers.something_else confidence=0.95 → LLM is lower, heuristic wins
        // Actually, 1.0 > 0.95 so heuristic wins. Let's test the opposite:
        // Heuristic can't match "unusual_field_name" (type-based fallback gives 0.55 for DECIMAL to DECIMAL)
        // But there's no DECIMAL expected type for non-matching fields... Let me use a field where heuristic gives low confidence

        // "category" → "products.sub_category" comes from computeConfidence where
        // "category" matches "sub_category" via Levenshtein distance of 4 (cat → sub_cat: not ≤2)
        // Token overlap: "category" → ["category"], "sub_category" → ["sub", "category"]
        // intersection = {"category"}, union = {"category", "sub"} → 1/2 = 0.5 ≥ 0.5 → 0.70 confidence
        // That's a token overlap match. Let me use a field where heuristic gives 0.55 or less.

        // Use a field that no heuristic rule matches well
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("weird_column_name");
        field.setInferredType(InferredType.FREE_TEXT);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        // LLM returns strong match with confidence > heuristic (0.0 since no heuristic matches FREE_TEXT well)
        when(chatLanguageModel.chat(any())).thenReturn(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(
                                "{\"canonicalEntity\": \"customers\", \"canonicalField\": \"name\", \"confidence\": 0.90}"))
                        .build()
        );

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        assertEquals("customers", m.getCanonicalEntity());
        assertEquals("name", m.getCanonicalField());
        assertTrue(m.getConfidence().doubleValue() >= 0.80);
    }

    @Test
    void testHeuristicWinsWhenLlmLowerConfidence() {
        // "Customer Name" → heuristic gives exact match confidence=1.0
        // LLM returns confidence=0.60 → heuristic (1.0) > LLM (0.6) → heuristic wins
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("Customer Name");
        field.setInferredType(InferredType.FREE_TEXT);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        // LLM returns lower confidence
        when(chatLanguageModel.chat(any())).thenReturn(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(
                                "{\"canonicalEntity\": \"customers\", \"canonicalField\": \"segment\", \"confidence\": 0.60}"))
                        .build()
        );

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        // Should be heuristic result: customer.name with confidence 1.0
        assertEquals("customers", m.getCanonicalEntity());
        assertEquals("name", m.getCanonicalField());
        assertEquals(0, BigDecimal.valueOf(1.0).compareTo(m.getConfidence()));
    }

    @Test
    void testLlmFailsGracefully_DefaultsToHeuristic() {
        // LLM throws exception → heuristic fallback should be used
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("Order Date");
        field.setInferredType(InferredType.DATE);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        // LLM throws
        when(chatLanguageModel.chat(any())).thenThrow(new RuntimeException("LLM unavailable"));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        // Heuristic should match "Order Date" → orders.order_date with confidence 1.0
        assertEquals("orders", m.getCanonicalEntity());
        assertEquals("order_date", m.getCanonicalField());
        assertEquals(0, BigDecimal.valueOf(1.0).compareTo(m.getConfidence()));
    }

    @Test
    void testLlmInvalidEntity_DefaultsToHeuristic() {
        // LLM returns non-existent entity → registry validation fails → heuristic used
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("Sales");
        field.setInferredType(InferredType.DECIMAL);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        // LLM returns a non-existent entity (not in REGISTRY)
        when(chatLanguageModel.chat(any())).thenReturn(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(
                                "{\"canonicalEntity\": \"nonexistent_table\", \"canonicalField\": \"fake_column\", \"confidence\": 0.99}"))
                        .build()
        );

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        // Heuristic should handle "Sales" → orders.total_amount (exact match via synonym "sales")
        assertEquals("orders", m.getCanonicalEntity());
        assertEquals("total_amount", m.getCanonicalField());
    }

    @Test
    void testLlmRetryThenFallback() {
        // LLM fails 3 times (all retries exhausted) → heuristic fallback
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("Order Date");
        field.setInferredType(InferredType.DATE);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        // LLM always throws
        when(chatLanguageModel.chat(any())).thenThrow(new RuntimeException("LLM persistent failure"));

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        // Heuristic should still work
        assertEquals("orders", m.getCanonicalEntity());
        assertEquals("order_date", m.getCanonicalField());
    }

    @Test
    void testLlmEmptyMapping_DefaultsToHeuristic() {
        // LLM returns empty mapping (no match) → heuristic used
        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(dataSource));

        SourceSchemaField field = new SourceSchemaField();
        field.setFieldName("Sales");
        field.setInferredType(InferredType.DECIMAL);
        when(sourceSchemaFieldRepository.findBySchemaId(schema.getId())).thenReturn(List.of(field));

        // LLM returns empty (no match)
        when(chatLanguageModel.chat(any())).thenReturn(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(
                                "{\"canonicalEntity\": \"\", \"canonicalField\": \"\", \"confidence\": 0.0}"))
                        .build()
        );

        semanticMapperService.generateMappings(sourceId, schema);

        ArgumentCaptor<List<FieldMapping>> captor = ArgumentCaptor.forClass(List.class);
        verify(fieldMappingRepository).saveAll(captor.capture());
        List<FieldMapping> mappings = captor.getValue();

        assertEquals(1, mappings.size());
        FieldMapping m = mappings.get(0);
        // Heuristic should handle "Sales" → orders.total_amount (synonym match)
        assertEquals("orders", m.getCanonicalEntity());
        assertEquals("total_amount", m.getCanonicalField());
    }
}
