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
import java.util.Set;

/**
 * Validates fields against strict dictionaries:
 * - Currency fields (e.g., 'currency', 'currency_code', 'curr') are checked against ISO-4217.
 * - Country fields (e.g., 'country', 'country_code') are checked against ISO-3166 registries.
 * Issues are LOW severity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccuracyChecker implements QualityChecker {

    private final ObjectMapper objectMapper;

    // ISO-4217 Currencies
    private static final Set<String> VALID_CURRENCIES = Set.of(
            "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG", "AZN",
            "BAM", "BBD", "BDT", "BGN", "BHD", "BIF", "BMD", "BND", "BOB", "BRL",
            "BSD", "BTN", "BWP", "BYN", "BZD", "CAD", "CDF", "CHF", "CLP", "CNY",
            "COP", "CRC", "CUC", "CUP", "CVE", "CZK", "DJF", "DKK", "DOP", "DZD",
            "EGP", "ERN", "ETB", "EUR", "FJD", "FKP", "GBP", "GEL", "GHS", "GIP",
            "GMD", "GNF", "GTQ", "GYD", "HKD", "HNL", "HRK", "HTG", "HUF", "IDR",
            "ILS", "INR", "IQD", "IRR", "ISK", "JMD", "JOD", "JPY", "KES", "KGS",
            "KHR", "KMF", "KPW", "KRW", "KWD", "KYD", "KZT", "LAK", "LBP", "LKR",
            "LRD", "LSL", "LYD", "MAD", "MDL", "MGA", "MKD", "MMK", "MNT", "MOP",
            "MRU", "MUR", "MVR", "MWK", "MXN", "MYR", "MZN", "NAD", "NGN", "NIO",
            "NOK", "NPR", "NZD", "OMR", "PAB", "PEN", "PGK", "PHP", "PKR", "PLN",
            "PYG", "QAR", "RON", "RSD", "RUB", "RWF", "SAR", "SBD", "SCR", "SDG",
            "SEK", "SGD", "SHP", "SLL", "SOS", "SRD", "SSP", "STN", "SVC", "SYP",
            "SZL", "THB", "TJS", "TMT", "TND", "TOP", "TRY", "TTD", "TWD", "TZS",
            "UAH", "UGX", "USD", "UYU", "UZS", "VES", "VND", "VUV", "WST", "XAF",
            "XCD", "XOF", "XPF", "YER", "ZAR", "ZMW", "ZWL"
    );

    // ISO-3166-1 alpha-2, alpha-3, and full country names (common/standardized list)
    private static final Set<String> VALID_COUNTRIES = Set.of(
            "US", "USA", "UNITED STATES", "GB", "GBR", "UNITED KINGDOM", "CA", "CAN", "CANADA",
            "FR", "FRA", "FRANCE", "DE", "DEU", "GERMANY", "IN", "IND", "INDIA",
            "AU", "AUS", "AUSTRALIA", "JP", "JPN", "JAPAN", "CN", "CHN", "CHINA",
            "BR", "BRA", "BRAZIL", "RU", "RUS", "RUSSIA", "ZA", "ZAF", "SOUTH AFRICA",
            "MX", "MEX", "MEXICO", "IT", "ITA", "ITALY", "ES", "ESP", "SPAIN",
            "NL", "NLD", "NETHERLANDS", "CH", "CHE", "SWITZERLAND", "SE", "SWE", "SWEDEN",
            "NO", "NOR", "NORWAY", "FI", "FIN", "FINLAND", "DK", "DNK", "DENMARK",
            "SG", "SGP", "SINGAPORE", "NZ", "NZL", "NEW ZEALAND", "AE", "ARE", "UNITED ARAB EMIRATES",
            "SA", "SAU", "SAUDI ARABIA", "KR", "KOR", "SOUTH KOREA", "KP", "PRK", "NORTH KOREA",
            "AR", "ARG", "ARGENTINA", "CO", "COL", "COLOMBIA", "CL", "CHL", "CHILE",
            "PE", "PER", "PERU", "VE", "VEN", "VENEZUELA", "MY", "MYS", "MALAYSIA",
            "ID", "IDN", "INDONESIA", "TH", "THA", "THAILAND", "PH", "PHL", "PHILIPPINES",
            "VN", "VNM", "VIETNAM", "PK", "PAK", "PAKISTAN", "BD", "BGD", "BANGLADESH",
            "TR", "TUR", "TURKEY", "PL", "POL", "POLAND", "UA", "UKR", "UKRAINE",
            "BE", "BEL", "BELGIUM", "AT", "AUT", "AUSTRIA", "IE", "IRL", "IRELAND",
            "PT", "PRT", "PORTUGAL", "GR", "GRC", "GREECE"
    );

    private static final List<String> CURRENCY_FIELDS = List.of("currency", "currency_code", "curr");
    private static final List<String> COUNTRY_FIELDS = List.of("country", "country_code", "nation");

    @Override
    public QualityDimension dimension() {
        return QualityDimension.ACCURACY;
    }

    @Override
    public List<QualityIssue> check(StagedRecord record, QualityRun run) {
        List<QualityIssue> issues = new ArrayList<>();
        JsonNode payload = parsePayload(record.getRawPayload());

        payload.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String val = entry.getValue().asText(null);
            if (val == null || val.isBlank()) return;

            String normalizedKey = key.toLowerCase().replace(" ", "_").replace("-", "_");
            String normalizedVal = val.trim().toUpperCase();

            if (CURRENCY_FIELDS.contains(normalizedKey)) {
                if (!VALID_CURRENCIES.contains(normalizedVal)) {
                    issues.add(buildIssue(run, record, key, "ACCURACY_INVALID_CURRENCY",
                            "Field '" + key + "' value '" + val + "' is not a valid ISO-4217 currency code"));
                }
            } else if (COUNTRY_FIELDS.contains(normalizedKey)) {
                if (!VALID_COUNTRIES.contains(normalizedVal)) {
                    issues.add(buildIssue(run, record, key, "ACCURACY_INVALID_COUNTRY",
                            "Field '" + key + "' value '" + val + "' is not a recognized ISO-3166 country code/name"));
                }
            }
        });

        return issues;
    }

    private JsonNode parsePayload(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            log.warn("AccuracyChecker: failed to parse payload: {}", e.getMessage());
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
        issue.setSeverity(QualitySeverity.LOW);
        issue.setDimension(QualityDimension.ACCURACY);
        issue.setMessage(message);
        issue.setStatus(IssueStatus.OPEN);
        return issue;
    }
}
