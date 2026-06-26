package com.shreyas.saleslens.service.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.QualityIssue;
import com.shreyas.saleslens.model.QualityRun;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that required canonical fields are non-null / non-blank in the staged payload.
 * <p>
 * Required fields (CRITICAL severity if missing):
 * <ul>
 *   <li>customer_name  (top-level key or dot-notation "customer.name")</li>
 *   <li>total_amount   (top-level or "order.total_amount")</li>
 *   <li>sku            (top-level or "product.sku")</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompletenessChecker implements QualityChecker {

    private static final List<RequiredField> REQUIRED_FIELDS = List.of(
            new RequiredField("customer_name",  new String[]{"customer_name", "Customer Name", "name"},          "COMPLETENESS_CUSTOMER_NAME"),
            new RequiredField("total_amount",   new String[]{"total_amount",  "Sales",          "amount"},        "COMPLETENESS_TOTAL_AMOUNT"),
            new RequiredField("sku",            new String[]{"sku",           "product_sku",    "product_code"},  "COMPLETENESS_SKU")
    );

    private final ObjectMapper objectMapper;

    @Override
    public QualityDimension dimension() {
        return QualityDimension.COMPLETENESS;
    }

    @Override
    public List<QualityIssue> check(StagedRecord record, QualityRun run) {
        List<QualityIssue> issues = new ArrayList<>();
        JsonNode payload = parsePayload(record.getRawPayload());

        for (RequiredField rf : REQUIRED_FIELDS) {
            boolean found = false;
            for (String alias : rf.aliases) {
                JsonNode node = payload.get(alias);
                if (node != null && !node.isNull() && !node.asText().isBlank()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                issues.add(buildIssue(run, record, rf.canonicalName, rf.ruleCode,
                        "Required field '" + rf.canonicalName + "' is null or blank"));
            }
        }
        return issues;
    }

    private JsonNode parsePayload(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            log.warn("CompletenessChecker: failed to parse payload: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private QualityIssue buildIssue(QualityRun run, StagedRecord record,
                                    String fieldName, String ruleCode, String message) {
        QualityIssue issue = new QualityIssue();
        issue.setRun(run);
        issue.setSource(record.getSource());
        issue.setStagedRecord(record);
        issue.setSourceFieldName(fieldName);
        issue.setRuleCode(ruleCode);
        issue.setSeverity(QualitySeverity.CRITICAL);
        issue.setDimension(QualityDimension.COMPLETENESS);
        issue.setMessage(message);
        issue.setStatus(IssueStatus.OPEN);
        return issue;
    }

    private record RequiredField(String canonicalName, String[] aliases, String ruleCode) {}
}
