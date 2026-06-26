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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates that field values conform to expected types:
 * <ul>
 *   <li>Date fields can be parsed as ISO-8601 or yyyy-MM-dd</li>
 *   <li>Numeric fields (total_amount, quantity, unit_price, shipping_cost) are non-negative</li>
 *   <li>Email field (if present) matches a basic regex</li>
 * </ul>
 * Issues are HIGH severity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValidityChecker implements QualityChecker {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private static final List<String> DATE_FIELDS    = List.of("order_date", "Order Date", "ship_date", "Ship Date");
    private static final List<String> NUMERIC_FIELDS = List.of("total_amount", "Sales", "quantity",
                                                                "unit_price", "shipping_cost", "Shipping Cost");
    private static final List<String> EMAIL_FIELDS   = List.of("email", "email_address", "contact_email");

    private final ObjectMapper objectMapper;

    @Override
    public QualityDimension dimension() {
        return QualityDimension.VALIDITY;
    }

    @Override
    public List<QualityIssue> check(StagedRecord record, QualityRun run) {
        List<QualityIssue> issues = new ArrayList<>();
        JsonNode payload = parsePayload(record.getRawPayload());

        payload.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String val = entry.getValue().asText(null);
            if (val == null || val.isBlank()) return;

            if (DATE_FIELDS.contains(key)) {
                if (!isValidDate(val)) {
                    issues.add(buildIssue(run, record, key, "VALIDITY_DATE_FORMAT",
                            "Field '" + key + "' value '" + val + "' is not a recognisable date format"));
                }
            } else if (NUMERIC_FIELDS.contains(key)) {
                try {
                    double num = Double.parseDouble(val);
                    if (num < 0) {
                        issues.add(buildIssue(run, record, key, "VALIDITY_NEGATIVE_NUMBER",
                                "Field '" + key + "' has negative value: " + val));
                    }
                } catch (NumberFormatException e) {
                    issues.add(buildIssue(run, record, key, "VALIDITY_NON_NUMERIC",
                            "Field '" + key + "' value '" + val + "' cannot be parsed as a number"));
                }
            } else if (EMAIL_FIELDS.contains(key)) {
                if (!EMAIL_PATTERN.matcher(val).matches()) {
                    issues.add(buildIssue(run, record, key, "VALIDITY_EMAIL_FORMAT",
                            "Field '" + key + "' value '" + val + "' is not a valid email address"));
                }
            }
        });

        return issues;
    }

    private boolean isValidDate(String val) {
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                LocalDate.parse(val, fmt);
                return true;
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }
        return false;
    }

    private JsonNode parsePayload(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            log.warn("ValidityChecker: failed to parse payload: {}", e.getMessage());
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
        issue.setSeverity(QualitySeverity.HIGH);
        issue.setDimension(QualityDimension.VALIDITY);
        issue.setMessage(message);
        issue.setStatus(IssueStatus.OPEN);
        return issue;
    }
}
