package com.shreyas.saleslens.service.advisory;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.QualityIssue;
import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualityExplanationServiceTest {

    private ChatLanguageModel chatLanguageModel;
    private QualityExplanationService explanationService;

    @BeforeEach
    void setUp() {
        chatLanguageModel = mock(ChatLanguageModel.class);
        explanationService = new QualityExplanationService();
        // Inject mock via reflection (since @Autowired(required=false) is used)
        setField(explanationService, "chatLanguageModel", chatLanguageModel);
    }

    @Test
    void testExplanationGenerated() throws Exception {
        QualityIssue issue = createTestIssue();

        String expectedExplanation = "The field 'customer_name' is missing a value, which affects completeness. " +
                "Check your source data to ensure all required fields are populated.";
        when(chatLanguageModel.generate(anyString())).thenReturn(expectedExplanation);

        CompletableFuture<String> future = explanationService.generateExplanation(issue);
        String explanation = future.get();

        assertNotNull(explanation);
        assertTrue(explanation.contains("customer_name"));
        verify(chatLanguageModel, times(1)).generate(anyString());
    }

    @Test
    void testLlmUnavailable_ReturnsNull() throws Exception {
        // When chatLanguageModel is null, explanation should be null
        QualityExplanationService serviceNoLlm = new QualityExplanationService();

        QualityIssue issue = createTestIssue();
        CompletableFuture<String> future = serviceNoLlm.generateExplanation(issue);
        String explanation = future.get();

        assertNull(explanation);
    }

    @Test
    void testLlmExceptionHandled() throws Exception {
        QualityIssue issue = createTestIssue();

        when(chatLanguageModel.generate(anyString())).thenThrow(new RuntimeException("LLM timeout"));

        CompletableFuture<String> future = explanationService.generateExplanation(issue);
        String explanation = future.get();

        // Should gracefully return null instead of propagating the exception
        assertNull(explanation);
    }

    @Test
    void testLlmReturnsEmptyString_ReturnsNull() throws Exception {
        QualityIssue issue = createTestIssue();

        when(chatLanguageModel.generate(anyString())).thenReturn("   ");

        CompletableFuture<String> future = explanationService.generateExplanation(issue);
        String explanation = future.get();

        assertNull(explanation);
    }

    @Test
    void testMultipleIssuesInParallel() throws Exception {
        QualityIssue issue1 = createTestIssue();
        QualityIssue issue2 = createTestIssue();
        issue2.setRuleCode("VALIDITY_NEGATIVE_NUMBER");
        issue2.setMessage("Field 'Sales' has negative value: -50.00");

        when(chatLanguageModel.generate(anyString()))
                .thenReturn("Explanation 1")
                .thenReturn("Explanation 2");

        CompletableFuture<String> future1 = explanationService.generateExplanation(issue1);
        CompletableFuture<String> future2 = explanationService.generateExplanation(issue2);

        CompletableFuture.allOf(future1, future2).get();

        assertEquals("Explanation 1", future1.get());
        assertEquals("Explanation 2", future2.get());
        verify(chatLanguageModel, times(2)).generate(anyString());
    }

    private QualityIssue createTestIssue() {
        DataSource source = new DataSource();
        source.setId(UUID.randomUUID());

        QualityIssue issue = new QualityIssue();
        issue.setId(UUID.randomUUID());
        issue.setSource(source);
        issue.setSourceFieldName("customer_name");
        issue.setRuleCode("COMPLETENESS_CUSTOMER_NAME");
        issue.setSeverity(QualitySeverity.CRITICAL);
        issue.setDimension(QualityDimension.COMPLETENESS);
        issue.setMessage("Required field 'customer_name' is null or blank");
        issue.setStatus(IssueStatus.OPEN);
        return issue;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
