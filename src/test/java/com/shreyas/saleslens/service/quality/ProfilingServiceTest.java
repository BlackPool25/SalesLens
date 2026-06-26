package com.shreyas.saleslens.service.quality;

import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import com.shreyas.saleslens.repository.DataProfileRepository;
import com.shreyas.saleslens.repository.FieldProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfilingServiceTest {

    @Mock
    private DataProfileRepository dataProfileRepository;

    @Mock
    private FieldProfileRepository fieldProfileRepository;

    @InjectMocks
    private ProfilingService profilingService;

    private UUID sourceId;
    private DataSource source;
    private QualityRun run;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        source = new DataSource();
        source.setId(sourceId);

        run = new QualityRun();
        run.setId(UUID.randomUUID());
        run.setSource(source);
        run.setRunTimestamp(Instant.now());
    }

    @Test
    void testInsufficientBatches_NoIssues() {
        // Only 2 profiles → cold-start guard prevents drift detection
        when(dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(List.of(createProfile(1), createProfile(2)));

        List<QualityIssue> issues = profilingService.detectDrift(run, sourceId);
        assertTrue(issues.isEmpty());
    }

    @Test
    void testNullProfiles_EmptyResult() {
        when(dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(Collections.emptyList());

        List<QualityIssue> issues = profilingService.detectDrift(run, sourceId);
        assertTrue(issues.isEmpty());
    }

    @Test
    void testCurrentBatchHasNoFields_EmptyResult() {
        List<DataProfile> profiles = List.of(createProfile(1), createProfile(2), createProfile(3));
        when(dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(profiles);
        // Current profile has no field profiles
        when(fieldProfileRepository.findByProfileId(profiles.get(0).getId()))
                .thenReturn(Collections.emptyList());

        List<QualityIssue> issues = profilingService.detectDrift(run, sourceId);
        assertTrue(issues.isEmpty());
    }

    @Test
    void testNullRateDrift_TriggersCompletenessIssue() {
        // Current batch: 50% null rate
        // Historical batches: 5% null rate average
        // Diff = 45pp > 20pp threshold → COMPLETENESS issue
        DataProfile currentProfile = createProfile(1);
        DataProfile histProfile2 = createProfile(2);
        DataProfile histProfile3 = createProfile(3);

        List<DataProfile> profiles = List.of(currentProfile, histProfile2, histProfile3);
        when(dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(profiles);

        // Current field profile: 50% null rate
        FieldProfile currentField = createFieldProfile(currentProfile, "customer_name",
                new BigDecimal("0.5000"),  // 50% null
                List.of("Acme", "Globex"),
                "1", "100");

        // Historical field profiles: 5% null rate average
        FieldProfile histField2 = createFieldProfile(histProfile2, "customer_name",
                new BigDecimal("0.0400"),
                List.of("Acme", "Globex", "Initech"),
                "10", "200");
        FieldProfile histField3 = createFieldProfile(histProfile3, "customer_name",
                new BigDecimal("0.0600"),
                List.of("Acme", "Globex", "Initech"),
                "5", "150");

        when(fieldProfileRepository.findByProfileId(currentProfile.getId()))
                .thenReturn(List.of(currentField));
        when(fieldProfileRepository.findByProfileId(histProfile2.getId()))
                .thenReturn(List.of(histField2));
        when(fieldProfileRepository.findByProfileId(histProfile3.getId()))
                .thenReturn(List.of(histField3));

        List<QualityIssue> issues = profilingService.detectDrift(run, sourceId);
        assertFalse(issues.isEmpty());

        boolean hasNullRateDrift = issues.stream()
                .anyMatch(i -> "COMPLETENESS_NULL_RATE_DRIFT".equals(i.getRuleCode())
                        && QualityDimension.COMPLETENESS == i.getDimension()
                        && QualitySeverity.MEDIUM == i.getSeverity());
        assertTrue(hasNullRateDrift, "Expected a COMPLETENESS_NULL_RATE_DRIFT issue");
    }

    @Test
    void testNullRateDrift_NoIssueWithinThreshold() {
        // Current batch: 15% null rate
        // Historical batches: 5% null rate average
        // Diff = 10pp < 20pp → no issue
        DataProfile currentProfile = createProfile(1);
        DataProfile histProfile2 = createProfile(2);
        DataProfile histProfile3 = createProfile(3);

        List<DataProfile> profiles = List.of(currentProfile, histProfile2, histProfile3);
        when(dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(profiles);

        FieldProfile currentField = createFieldProfile(currentProfile, "customer_name",
                new BigDecimal("0.1500"),
                List.of("Acme"),
                "10", "200");
        FieldProfile histField2 = createFieldProfile(histProfile2, "customer_name",
                new BigDecimal("0.0400"),
                List.of("Acme"),
                "50", "500");
        FieldProfile histField3 = createFieldProfile(histProfile3, "customer_name",
                new BigDecimal("0.0600"),
                List.of("Acme"),
                "20", "300");

        when(fieldProfileRepository.findByProfileId(currentProfile.getId()))
                .thenReturn(List.of(currentField));
        when(fieldProfileRepository.findByProfileId(histProfile2.getId()))
                .thenReturn(List.of(histField2));
        when(fieldProfileRepository.findByProfileId(histProfile3.getId()))
                .thenReturn(List.of(histField3));

        List<QualityIssue> issues = profilingService.detectDrift(run, sourceId);
        boolean hasNullRateDrift = issues.stream()
                .anyMatch(i -> "COMPLETENESS_NULL_RATE_DRIFT".equals(i.getRuleCode()));
        assertFalse(hasNullRateDrift, "Null rate drift should not trigger within threshold");
    }

    @Test
    void testValueDistributionSkew_TriggersValidityIssue() {
        DataProfile currentProfile = createProfile(1);
        DataProfile histProfile2 = createProfile(2);
        DataProfile histProfile3 = createProfile(3);

        List<DataProfile> profiles = List.of(currentProfile, histProfile2, histProfile3);
        when(dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(profiles);

        // Current top-10: 8 new values not in historical union (8/10 = 80% > 50%)
        FieldProfile currentField = createFieldProfile(currentProfile, "category",
                new BigDecimal("0.0000"),
                List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"), // 10 values, all new except A
                null, null);

        FieldProfile histField2 = createFieldProfile(histProfile2, "category",
                new BigDecimal("0.0000"),
                List.of("X", "Y", "Z"), // Only 3 values, none overlap with A-J
                null, null);
        FieldProfile histField3 = createFieldProfile(histProfile3, "category",
                new BigDecimal("0.0000"),
                List.of("A"), // Only A is common
                null, null);

        when(fieldProfileRepository.findByProfileId(currentProfile.getId()))
                .thenReturn(List.of(currentField));
        when(fieldProfileRepository.findByProfileId(histProfile2.getId()))
                .thenReturn(List.of(histField2));
        when(fieldProfileRepository.findByProfileId(histProfile3.getId()))
                .thenReturn(List.of(histField3));

        List<QualityIssue> issues = profilingService.detectDrift(run, sourceId);
        boolean hasSkew = issues.stream()
                .anyMatch(i -> "VALIDITY_VALUE_DISTRIBUTION_DRIFT".equals(i.getRuleCode()));
        assertTrue(hasSkew, "Expected a VALIDITY_VALUE_DISTRIBUTION_DRIFT issue");
    }

    @Test
    void testRangeExpansion_TriggersAccuracyIssue() {
        DataProfile currentProfile = createProfile(1);
        DataProfile histProfile2 = createProfile(2);
        DataProfile histProfile3 = createProfile(3);

        List<DataProfile> profiles = List.of(currentProfile, histProfile2, histProfile3);
        when(dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(profiles);

        // Historical values: min=100, max=200 across 2 batches → mean=150, low stddev
        // Current: min=10, max=1000 → way outside 3σ → ACCURACY issue
        FieldProfile currentField = createFieldProfile(currentProfile, "unit_price",
                new BigDecimal("0.0000"),
                List.of("10", "20"),
                "10", "1000");

        FieldProfile histField2 = createFieldProfile(histProfile2, "unit_price",
                new BigDecimal("0.0000"),
                List.of("150"),
                "100", "200");
        FieldProfile histField3 = createFieldProfile(histProfile3, "unit_price",
                new BigDecimal("0.0000"),
                List.of("160"),
                "120", "180");

        when(fieldProfileRepository.findByProfileId(currentProfile.getId()))
                .thenReturn(List.of(currentField));
        when(fieldProfileRepository.findByProfileId(histProfile2.getId()))
                .thenReturn(List.of(histField2));
        when(fieldProfileRepository.findByProfileId(histProfile3.getId()))
                .thenReturn(List.of(histField3));

        List<QualityIssue> issues = profilingService.detectDrift(run, sourceId);
        boolean hasRangeIssue = issues.stream()
                .anyMatch(i -> "ACCURACY_RANGE_EXPANSION_DRIFT".equals(i.getRuleCode()));
        assertTrue(hasRangeIssue, "Expected a ACCURACY_RANGE_EXPANSION_DRIFT issue");
    }

    @Test
    void testNoDrift_EmptyResult() {
        DataProfile currentProfile = createProfile(1);
        DataProfile histProfile2 = createProfile(2);
        DataProfile histProfile3 = createProfile(3);

        List<DataProfile> profiles = List.of(currentProfile, histProfile2, histProfile3);
        when(dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(profiles);

        // All batches have identical stats — no drift expected
        FieldProfile currentField = createFieldProfile(currentProfile, "customer_name",
                new BigDecimal("0.0500"),
                List.of("Acme", "Globex"),
                "100", "200");
        FieldProfile histField2 = createFieldProfile(histProfile2, "customer_name",
                new BigDecimal("0.0400"),
                List.of("Acme", "Globex"),
                "110", "190");
        FieldProfile histField3 = createFieldProfile(histProfile3, "customer_name",
                new BigDecimal("0.0600"),
                List.of("Acme", "Globex"),
                "90", "210");

        when(fieldProfileRepository.findByProfileId(currentProfile.getId()))
                .thenReturn(List.of(currentField));
        when(fieldProfileRepository.findByProfileId(histProfile2.getId()))
                .thenReturn(List.of(histField2));
        when(fieldProfileRepository.findByProfileId(histProfile3.getId()))
                .thenReturn(List.of(histField3));

        List<QualityIssue> issues = profilingService.detectDrift(run, sourceId);
        assertTrue(issues.isEmpty(), "No drift expected with identical stats across batches");
    }

    @Test
    void testMultipleFields_SomeDriftSomeNot() {
        DataProfile currentProfile = createProfile(1);
        DataProfile histProfile2 = createProfile(2);
        DataProfile histProfile3 = createProfile(3);

        List<DataProfile> profiles = List.of(currentProfile, histProfile2, histProfile3);
        when(dataProfileRepository.findTop3BySourceIdOrderByCreatedAtDesc(sourceId))
                .thenReturn(profiles);

        // Field 1: null rate drift (50% vs 5%)
        FieldProfile field1Current = createFieldProfile(currentProfile, "email",
                new BigDecimal("0.5000"),
                List.of("a@b.com"),
                null, null);
        // Field 2: no drift
        FieldProfile field2Current = createFieldProfile(currentProfile, "name",
                new BigDecimal("0.0500"),
                List.of("Alice", "Bob"),
                null, null);

        FieldProfile field1Hist2 = createFieldProfile(histProfile2, "email",
                new BigDecimal("0.0400"),
                List.of("a@b.com", "c@d.com"),
                null, null);
        FieldProfile field1Hist3 = createFieldProfile(histProfile3, "email",
                new BigDecimal("0.0600"),
                List.of("a@b.com"),
                null, null);

        FieldProfile field2Hist2 = createFieldProfile(histProfile2, "name",
                new BigDecimal("0.0300"),
                List.of("Alice", "Bob", "Charlie"),
                null, null);
        FieldProfile field2Hist3 = createFieldProfile(histProfile3, "name",
                new BigDecimal("0.0700"),
                List.of("Alice", "Bob"),
                null, null);

        when(fieldProfileRepository.findByProfileId(currentProfile.getId()))
                .thenReturn(List.of(field1Current, field2Current));
        when(fieldProfileRepository.findByProfileId(histProfile2.getId()))
                .thenReturn(List.of(field1Hist2, field2Hist2));
        when(fieldProfileRepository.findByProfileId(histProfile3.getId()))
                .thenReturn(List.of(field1Hist3, field2Hist3));

        List<QualityIssue> issues = profilingService.detectDrift(run, sourceId);

        // Should have COMPLETENESS_NULL_RATE_DRIFT for email
        boolean hasEmailDrift = issues.stream()
                .anyMatch(i -> "COMPLETENESS_NULL_RATE_DRIFT".equals(i.getRuleCode())
                        && "email".equals(i.getSourceFieldName()));
        assertTrue(hasEmailDrift, "Expected null rate drift for email field");

        // Should NOT have any issue for name
        boolean hasNameIssue = issues.stream()
                .anyMatch(i -> "name".equals(i.getSourceFieldName()));
        assertFalse(hasNameIssue, "Expected no drift issue for name field");
    }

    // --- Helpers ---

    private DataProfile createProfile(int seq) {
        DataProfile profile = new DataProfile();
        profile.setId(UUID.nameUUIDFromBytes(("profile-" + seq).getBytes()));
        profile.setSource(source);
        profile.setTotalRecords(100 * seq);
        return profile;
    }

    private FieldProfile createFieldProfile(DataProfile profile, String fieldName,
                                             BigDecimal nullRate, List<String> topValues,
                                             String minValue, String maxValue) {
        FieldProfile fp = new FieldProfile();
        fp.setProfile(profile);
        fp.setFieldName(fieldName);
        fp.setNullRate(nullRate);
        fp.setTopValues(serializeJson(topValues));
        fp.setMinValue(minValue);
        fp.setMaxValue(maxValue);
        return fp;
    }

    private String serializeJson(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(values.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
