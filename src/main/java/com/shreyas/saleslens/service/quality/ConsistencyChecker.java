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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates cross-field constraints:
 * <ul>
 *   <li>line_total ≈ quantity × unit_price (within $0.01 tolerance)</li>
 *   <li>order_date must not be in the future</li>
 * </ul>
 * Issues are MEDIUM severity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConsistencyChecker implements QualityChecker {

    private static final double LINE_TOTAL_TOLERANCE = 0.01;

    private final ObjectMapper objectMapper;

    @Override
    public QualityDimension dimension() {
        return QualityDimension.CONSISTENCY;
    }

    @Override
    public List<QualityIssue> check(StagedRecord record, QualityRun run) {
        List<QualityIssue> issues = new ArrayList<>();
        JsonNode payload = parsePayload(record.getRawPayload());

        // 1. line_total = quantity × unit_price
        Double quantity  = getDouble(payload, "quantity");
        Double unitPrice = getDouble(payload, "unit_price");
        Double lineTotal = getDouble(payload, "line_total");

        if (quantity != null && unitPrice != null && lineTotal != null) {
            double expected = quantity * unitPrice;
            if (Math.abs(expected - lineTotal) > LINE_TOTAL_TOLERANCE) {
                issues.add(buildIssue(run, record, "line_total", "CONSISTENCY_LINE_TOTAL",
                        String.format("line_total (%.4f) ≠ quantity (%.4f) × unit_price (%.4f); expected ≈ %.4f",
                                lineTotal, quantity, unitPrice, expected)));
            }
        }

        // 2. order_date must not be in the future
        String orderDateStr = getString(payload, "order_date", "Order Date");
        if (orderDateStr != null) {
            try {
                LocalDate orderDate = LocalDate.parse(orderDateStr);
                if (orderDate.isAfter(LocalDate.now())) {
                    issues.add(buildIssue(run, record, "order_date", "CONSISTENCY_FUTURE_ORDER_DATE",
                            "order_date '" + orderDateStr + "' is in the future"));
                }
            } catch (DateTimeParseException ignored) {
                // Date format issues are ValidityChecker's responsibility
            }
        }

        return issues;
    }

    private Double getDouble(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode n = node.get(key);
            if (n != null && !n.isNull()) {
                try { return Double.parseDouble(n.asText()); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String getString(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode n = node.get(key);
            if (n != null && !n.isNull() && !n.asText().isBlank()) return n.asText();
        }
        return null;
    }

    private JsonNode parsePayload(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            log.warn("ConsistencyChecker: failed to parse payload: {}", e.getMessage());
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
        issue.setSeverity(QualitySeverity.MEDIUM);
        issue.setDimension(QualityDimension.CONSISTENCY);
        issue.setMessage(message);
        issue.setStatus(IssueStatus.OPEN);
        return issue;
    }
}
