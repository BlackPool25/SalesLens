package com.shreyas.saleslens.service.inference;

import com.shreyas.saleslens.model.enums.InferredType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TypeDetectionServiceTest {

    private final TypeDetectionService service = new TypeDetectionService();

    @Test
    void testDetectTypeInteger() {
        List<String> values = Arrays.asList("123", "0", "-456", "", null);
        assertEquals(InferredType.INTEGER, service.detectType(values));
    }

    @Test
    void testDetectTypeIntegerLeadingZeroRejected() {
        List<String> values = Arrays.asList("05", "123");
        assertEquals(InferredType.CATEGORY, service.detectType(values));
    }

    @Test
    void testDetectTypeDecimal() {
        List<String> values = Arrays.asList("123.45", "0.0", "-0.1", null);
        assertEquals(InferredType.DECIMAL, service.detectType(values));
    }

    @Test
    void testDetectTypeBoolean() {
        List<String> values = Arrays.asList("true", "FALSE", "True", null);
        assertEquals(InferredType.BOOLEAN, service.detectType(values));
    }

    @Test
    void testDetectTypeDate() {
        List<String> values = Arrays.asList("2024-03-15", "2024-12-31", null);
        assertEquals(InferredType.DATE, service.detectType(values));
        assertEquals("yyyy-MM-dd", service.detectDateFormat(values));
    }

    @Test
    void testDetectTypeDateTime() {
        List<String> values = Arrays.asList("2024-03-15T14:22:00", "2024-12-31T23:59:59", null);
        assertEquals(InferredType.DATETIME, service.detectType(values));
    }

    @Test
    void testDetectTypeEmail() {
        List<String> values = Arrays.asList("test@example.com", "user.name+tag@domain.org", null);
        assertEquals(InferredType.EMAIL, service.detectType(values));
    }

    @Test
    void testDetectTypePhone() {
        List<String> values = Arrays.asList("+1234567890", "123-456-7890", "(123) 456 7890", null);
        assertEquals(InferredType.PHONE, service.detectType(values));
    }

    @Test
    void testDetectTypeCurrency() {
        List<String> values = Arrays.asList("$100.50", "€20", "50 £", "¥1000", null);
        assertEquals(InferredType.CURRENCY_AMOUNT, service.detectType(values));
    }

    @Test
    void testDetectTypeCategory() {
        List<String> values = Arrays.asList("Red", "Blue", "Red", "Green", "Blue", null);
        assertEquals(InferredType.CATEGORY, service.detectType(values));
    }

    @Test
    void testDetectTypeFreeText() {
        List<String> values = Arrays.asList(
                "val1", "val2", "val3", "val4", "val5",
                "val6", "val7", "val8", "val9", "val10",
                "val11", "val12", "val13", "val14", "val15",
                "val16", "val17", "val18", "val19", "val20"
        );
        assertEquals(InferredType.FREE_TEXT, service.detectType(values));
    }

    @Test
    void testDetectTypeEmpty() {
        assertEquals(InferredType.FREE_TEXT, service.detectType(Collections.emptyList()));
        assertEquals(InferredType.FREE_TEXT, service.detectType(null));
    }

    @Test
    void testDetectDateFormatInvalid() {
        assertNull(service.detectDateFormat(Arrays.asList("not-a-date", "invalid")));
    }
}
