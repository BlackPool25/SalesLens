package com.shreyas.saleslens.service;

import com.shreyas.saleslens.model.FieldMapping;
import com.shreyas.saleslens.model.StagedRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransformationServiceTest {

    private final TransformationService transformationService = new TransformationService();

    @Test
    void testTransform() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"Row ID\": \"1\", \"Sales\": \"123.45\", \"IgnoredCol\": \"foo\", \"PendingCol\": \"bar\"}");

        List<FieldMapping> mappings = new ArrayList<>();

        // 1. Confirmed mapping for Sales -> orders.total_amount
        FieldMapping m1 = new FieldMapping();
        m1.setSourceFieldName("Sales");
        m1.setCanonicalEntity("orders");
        m1.setCanonicalField("total_amount");
        m1.setStatus("AUTO_CONFIRMED");
        mappings.add(m1);

        // 2. Confirmed mapping for Row ID -> customers.name (just as a mock example to test plural to singular customer)
        FieldMapping m2 = new FieldMapping();
        m2.setSourceFieldName("Row ID");
        m2.setCanonicalEntity("customers");
        m2.setCanonicalField("name");
        m2.setStatus("AUTO_CONFIRMED");
        mappings.add(m2);

        // 3. Pending mapping (should be skipped)
        FieldMapping m3 = new FieldMapping();
        m3.setSourceFieldName("PendingCol");
        m3.setCanonicalEntity("products");
        m3.setCanonicalField("sku");
        m3.setStatus("PENDING");
        mappings.add(m3);

        // 4. Ignored mapping (should be skipped)
        FieldMapping m4 = new FieldMapping();
        m4.setSourceFieldName("IgnoredCol");
        m4.setCanonicalEntity("");
        m4.setCanonicalField("");
        m4.setStatus("IGNORED");
        mappings.add(m4);

        Map<String, String> result = transformationService.transform(record, mappings);

        // Assert size
        assertEquals(3, result.size());

        // Assert key-value mappings
        assertEquals("123.45", result.get("order.total_amount"));
        assertEquals("1", result.get("customer.name"));
        assertEquals("{\"sku\":\"bar\"}", result.get("product.additional_attributes"));

        // Assert skipped items are not present
        assertFalse(result.containsKey("product.sku"));
        assertFalse(result.containsKey("IgnoredCol"));
    }

    @Test
    void testTransform_NullRecord() {
        Map<String, String> result = transformationService.transform(null, new ArrayList<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void testTransform_NullPayload() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload(null);
        Map<String, String> result = transformationService.transform(record, new ArrayList<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void testTransform_MalformedJson() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{invalid-json}");
        List<FieldMapping> mappings = new ArrayList<>();
        FieldMapping m1 = new FieldMapping();
        m1.setSourceFieldName("Sales");
        m1.setCanonicalEntity("orders");
        m1.setCanonicalField("total_amount");
        m1.setStatus("AUTO_CONFIRMED");
        mappings.add(m1);

        Map<String, String> result = transformationService.transform(record, mappings);
        assertTrue(result.isEmpty());
    }

    @Test
    void testTransform_NullMappings() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"Sales\": \"123.45\"}");
        // Even if mappings list is null, the try-catch block inside transform() should capture the NPE and return an empty map.
        Map<String, String> result = transformationService.transform(record, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testTransform_NullValues() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"Sales\": null}");
        List<FieldMapping> mappings = new ArrayList<>();
        FieldMapping m1 = new FieldMapping();
        m1.setSourceFieldName("Sales");
        m1.setCanonicalEntity("orders");
        m1.setCanonicalField("total_amount");
        m1.setStatus("AUTO_CONFIRMED");
        mappings.add(m1);

        Map<String, String> result = transformationService.transform(record, mappings);
        assertEquals(1, result.size());
        assertNull(result.get("order.total_amount"));
    }

    @Test
    void testTransform_NumericAndBooleanValues() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"Sales\": 123.45, \"IsActive\": true}");
        List<FieldMapping> mappings = new ArrayList<>();
        FieldMapping m1 = new FieldMapping();
        m1.setSourceFieldName("Sales");
        m1.setCanonicalEntity("orders");
        m1.setCanonicalField("total_amount");
        m1.setStatus("AUTO_CONFIRMED");
        mappings.add(m1);

        FieldMapping m2 = new FieldMapping();
        m2.setSourceFieldName("IsActive");
        m2.setCanonicalEntity("customers");
        m2.setCanonicalField("status");
        m2.setStatus("AUTO_CONFIRMED");
        mappings.add(m2);

        Map<String, String> result = transformationService.transform(record, mappings);
        assertEquals(2, result.size());
        assertEquals("123.45", result.get("order.total_amount"));
        assertEquals("true", result.get("customer.status"));
    }

    @Test
    void testTransform_EntityNotEndingWithS() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"Curr\": \"USD\"}");
        List<FieldMapping> mappings = new ArrayList<>();
        FieldMapping m1 = new FieldMapping();
        m1.setSourceFieldName("Curr");
        m1.setCanonicalEntity("currency"); // Doesn't end with 's'
        m1.setCanonicalField("currency_code");
        m1.setStatus("AUTO_CONFIRMED");
        mappings.add(m1);

        Map<String, String> result = transformationService.transform(record, mappings);
        assertEquals(1, result.size());
        assertEquals("USD", result.get("currency.currency_code"));
    }

    @Test
    void testTransform_NullFieldsInMappings() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"Sales\": \"123.45\"}");
        List<FieldMapping> mappings = new ArrayList<>();

        // Missing canonical field
        FieldMapping m1 = new FieldMapping();
        m1.setSourceFieldName("Sales");
        m1.setCanonicalEntity("orders");
        m1.setCanonicalField(null);
        m1.setStatus("AUTO_CONFIRMED");
        mappings.add(m1);

        // Missing canonical entity
        FieldMapping m2 = new FieldMapping();
        m2.setSourceFieldName("Sales");
        m2.setCanonicalEntity(null);
        m2.setCanonicalField("total_amount");
        m2.setStatus("AUTO_CONFIRMED");
        mappings.add(m2);

        // Missing source field name
        FieldMapping m3 = new FieldMapping();
        m3.setSourceFieldName(null);
        m3.setCanonicalEntity("orders");
        m3.setCanonicalField("total_amount");
        m3.setStatus("AUTO_CONFIRMED");
        mappings.add(m3);

        Map<String, String> result = transformationService.transform(record, mappings);
        assertTrue(result.isEmpty());
    }
}
