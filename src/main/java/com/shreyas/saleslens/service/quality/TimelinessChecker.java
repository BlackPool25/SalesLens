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
 * Validates that transaction dates are within a dynamic acceptable window:
 * <ul>
 *   <li>order_date must not be more than 2 years in the past</li>
 *   <li>order_date must not be in the future</li>
 * </ul>
 * Issues are MEDIUM severity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TimelinessChecker implements QualityChecker {

    /** Maximum number of years a record can be in the past. */
    private static final int MAX_YEARS_IN_PAST = 2;

    private final ObjectMapper objectMapper;

    @Override
    public QualityDimension dimension() {
        return QualityDimension.TIMELINESS;
    }

    @Override
    public List<QualityIssue> check(StagedRecord record, QualityRun run) {
        List<QualityIssue> issues = new ArrayList<>();
        JsonNode payload = parsePayload(record.getRawPayload());

        String dateStr = getDateString(payload, "order_date", "Order Date", "transaction_date");
        if (dateStr == null) return issues;

        try {
            LocalDate date = LocalDate.parse(dateStr);
            LocalDate today = LocalDate.now();
            LocalDate threshold = today.minusYears(MAX_YEARS_IN_PAST);

            if (date.isAfter(today)) {
                issues.add(buildIssue(run, record, "order_date", "TIMELINESS_FUTURE_DATE",
                        "order_date '" + dateStr + "' is in the future (today=" + today + ")"));
            } else if (date.isBefore(threshold)) {
                issues.add(buildIssue(run, record, "order_date", "TIMELINESS_STALE_DATE",
                        "order_date '" + dateStr + "' is more than " + MAX_YEARS_IN_PAST +
                                " years in the past (threshold=" + threshold + ")"));
            }
        } catch (DateTimeParseException ignored) {
            // Date format issues are ValidityChecker's responsibility
        }

        return issues;
    }

    private String getDateString(JsonNode node, String... keys) {
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
            log.warn("TimelinessChecker: failed to parse payload: {}", e.getMessage());
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
        issue.setDimension(QualityDimension.TIMELINESS);
        issue.setMessage(message);
        issue.setStatus(IssueStatus.OPEN);
        return issue;
    }
}
