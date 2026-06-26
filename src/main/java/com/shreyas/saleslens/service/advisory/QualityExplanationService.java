package com.shreyas.saleslens.service.advisory;

import com.shreyas.saleslens.model.QualityIssue;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Optional LLM-powered quality explanation service.
 * <p>
 * Generates human-readable explanations for quality issues with remediation suggestions.
 * This is purely advisory — it never blocks the pipeline. If the LLM is unavailable
 * or returns invalid output, the system falls back gracefully (returns null).
 * <p>
 * All methods are {@link Async} and return {@link CompletableFuture} for non-blocking operation.
 * This bean is only active when the {@code !test} profile is active, matching the
 * OllamaConfig pattern.
 */
@Service
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class QualityExplanationService {

    @Autowired(required = false)
    private ChatLanguageModel chatLanguageModel;

    /**
     * Generate a human-readable explanation for a quality issue.
     * <p>
     * The explanation includes what went wrong, why it matters, and how to fix the source data.
     *
     * @param issue the quality issue to explain
     * @return a future containing the explanation text, or null if LLM is unavailable
     */
    @Async
    public CompletableFuture<String> generateExplanation(QualityIssue issue) {
        if (chatLanguageModel == null) {
            log.debug("LLM not available — skipping quality explanation generation");
            return CompletableFuture.completedFuture(null);
        }

        try {
            String prompt = buildExplanationPrompt(issue);
            log.debug("Requesting quality explanation for issue: {} (rule={})",
                    issue.getId(), issue.getRuleCode());

            String response = chatLanguageModel.generate(prompt);
            String explanation = response != null ? response.trim() : null;

            if (explanation != null && !explanation.isEmpty()) {
                log.info("Generated quality explanation for issue {} ({} chars)",
                        issue.getId(), explanation.length());
                return CompletableFuture.completedFuture(explanation);
            }

            log.warn("LLM returned empty explanation for issue {}", issue.getId());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.warn("Failed to generate quality explanation for issue {}: {}",
                    issue.getId(), e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Builds a structured prompt for the LLM based on issue context.
     */
    private String buildExplanationPrompt(QualityIssue issue) {
        return String.format(
                "You are a data quality analyst. Explain the following data quality issue " +
                "in simple, non-technical language and suggest how to fix the source data.\n\n" +
                "Issue Details:\n" +
                "- Dimension: %s\n" +
                "- Severity: %s\n" +
                "- Rule: %s\n" +
                "- Field: %s\n" +
                "- Description: %s\n\n" +
                "Provide a concise 2-3 sentence explanation with a specific remediation suggestion. " +
                "Do not use markdown formatting. Respond in plain text only.",
                issue.getDimension(),
                issue.getSeverity(),
                issue.getRuleCode(),
                issue.getSourceFieldName() != null ? issue.getSourceFieldName() : "N/A",
                issue.getMessage() != null ? issue.getMessage() : "N/A"
        );
    }
}
