package com.shreyas.saleslens.service.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.IssueStatus;
import com.shreyas.saleslens.model.enums.QualityDimension;
import com.shreyas.saleslens.model.enums.QualitySeverity;
import com.shreyas.saleslens.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.*;

import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QualityEngineServiceTest {

    private ObjectMapper objectMapper;
    private CompletenessChecker completenessChecker;
    private ValidityChecker validityChecker;
    private UniquenessChecker uniquenessChecker;
    private ConsistencyChecker consistencyChecker;
    private TimelinessChecker timelinessChecker;
    private AccuracyChecker accuracyChecker;

    private StagedRecordRepository stagedRecordRepository;
    private IngestionJobRepository ingestionJobRepository;
    private QualityRunRepository qualityRunRepository;
    private QualityIssueRepository qualityIssueRepository;
    private RejectedRecordRepository rejectedRecordRepository;
    private QualityScoreRepository qualityScoreRepository;
    private QualityScoreService qualityScoreService;
    private QualityEngineService qualityEngineService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Checkers
        completenessChecker = new CompletenessChecker(objectMapper);
        validityChecker = new ValidityChecker(objectMapper);

        stagedRecordRepository = mock(StagedRecordRepository.class);
        uniquenessChecker = new UniquenessChecker(stagedRecordRepository);
        consistencyChecker = new ConsistencyChecker(objectMapper);
        timelinessChecker = new TimelinessChecker(objectMapper);
        accuracyChecker = new AccuracyChecker(objectMapper);

        List<QualityChecker> checkers = List.of(
                completenessChecker,
                validityChecker,
                uniquenessChecker,
                consistencyChecker,
                timelinessChecker,
                accuracyChecker
        );

        ingestionJobRepository = mock(IngestionJobRepository.class);
        qualityRunRepository = mock(QualityRunRepository.class);
        qualityIssueRepository = mock(QualityIssueRepository.class);
        rejectedRecordRepository = mock(RejectedRecordRepository.class);
        qualityScoreRepository = mock(QualityScoreRepository.class);

        qualityScoreService = new QualityScoreService(qualityScoreRepository);

        qualityEngineService = new QualityEngineService(
                checkers,
                stagedRecordRepository,
                ingestionJobRepository,
                qualityRunRepository,
                qualityIssueRepository,
                rejectedRecordRepository,
                qualityScoreService
        );
    }

    @Test
    void testCompletenessChecker_MissingRequiredFields() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"customer_name\": \"\", \"total_amount\": null}"); // sku missing entirely

        QualityRun run = new QualityRun();
        List<QualityIssue> issues = completenessChecker.check(record, run);

        // Expect issues for customer_name, total_amount, and sku (all 3 missing/blank/null)
        assertEquals(3, issues.size());
        assertTrue(issues.stream().anyMatch(i -> "COMPLETENESS_CUSTOMER_NAME".equals(i.getRuleCode())));
        assertTrue(issues.stream().anyMatch(i -> "COMPLETENESS_TOTAL_AMOUNT".equals(i.getRuleCode())));
        assertTrue(issues.stream().anyMatch(i -> "COMPLETENESS_SKU".equals(i.getRuleCode())));

        issues.forEach(i -> {
            assertEquals(QualitySeverity.CRITICAL, i.getSeverity());
            assertEquals(QualityDimension.COMPLETENESS, i.getDimension());
            assertEquals(IssueStatus.OPEN, i.getStatus());
        });
    }

    @Test
    void testCompletenessChecker_AllRequiredFieldsPresent() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"customer_name\": \"Acme Corp\", \"total_amount\": \"150.00\", \"sku\": \"PROD-101\"}");

        QualityRun run = new QualityRun();
        List<QualityIssue> issues = completenessChecker.check(record, run);
        assertTrue(issues.isEmpty());
    }

    @Test
    void testValidityChecker_InvalidFormats() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{" +
                "\"order_date\": \"not-a-date\"," +
                "\"total_amount\": \"-12.50\"," +
                "\"quantity\": \"abc\"," +
                "\"email\": \"bademail.com\"" +
                "}");

        QualityRun run = new QualityRun();
        List<QualityIssue> issues = validityChecker.check(record, run);

        assertEquals(4, issues.size());
        assertTrue(issues.stream().anyMatch(i -> "VALIDITY_DATE_FORMAT".equals(i.getRuleCode())));
        assertTrue(issues.stream().anyMatch(i -> "VALIDITY_NEGATIVE_NUMBER".equals(i.getRuleCode())));
        assertTrue(issues.stream().anyMatch(i -> "VALIDITY_NON_NUMERIC".equals(i.getRuleCode())));
        assertTrue(issues.stream().anyMatch(i -> "VALIDITY_EMAIL_FORMAT".equals(i.getRuleCode())));

        issues.forEach(i -> assertEquals(QualitySeverity.HIGH, i.getSeverity()));
    }

    @Test
    void testValidityChecker_ValidFormats() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{" +
                "\"order_date\": \"2024-03-15\"," +
                "\"total_amount\": \"1250.00\"," +
                "\"quantity\": \"10\"," +
                "\"email\": \"test@example.com\"" +
                "}");

        QualityRun run = new QualityRun();
        List<QualityIssue> issues = validityChecker.check(record, run);
        assertTrue(issues.isEmpty());
    }

    @Test
    void testUniquenessChecker_DuplicateHash() {
        UUID sourceId = UUID.randomUUID();
        DataSource source = new DataSource();
        source.setId(sourceId);

        StagedRecord record = new StagedRecord();
        record.setId(UUID.randomUUID());
        record.setSource(source);
        record.setRecordHash("HASH123");

        StagedRecord otherRecord = new StagedRecord();
        otherRecord.setId(UUID.randomUUID()); // different ID, same hash

        when(stagedRecordRepository.findBySourceIdAndRecordHash(sourceId, "HASH123"))
                .thenReturn(List.of(record, otherRecord));

        QualityRun run = new QualityRun();
        List<QualityIssue> issues = uniquenessChecker.check(record, run);

        assertEquals(1, issues.size());
        QualityIssue issue = issues.get(0);
        assertEquals("UNIQUENESS_DUPLICATE_HASH", issue.getRuleCode());
        assertEquals(QualitySeverity.HIGH, issue.getSeverity());
    }

    @Test
    void testConsistencyChecker_CalculationsAndDates() {
        StagedRecord record = new StagedRecord();
        // Expected line_total: 5 * 10 = 50. But line_total is 45. Also date is in the future.
        record.setRawPayload("{" +
                "\"quantity\": \"5\"," +
                "\"unit_price\": \"10.00\"," +
                "\"line_total\": \"45.00\"," +
                "\"order_date\": \"2099-12-31\"" +
                "}");

        QualityRun run = new QualityRun();
        List<QualityIssue> issues = consistencyChecker.check(record, run);

        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(i -> "CONSISTENCY_LINE_TOTAL".equals(i.getRuleCode())));
        assertTrue(issues.stream().anyMatch(i -> "CONSISTENCY_FUTURE_ORDER_DATE".equals(i.getRuleCode())));

        issues.forEach(i -> assertEquals(QualitySeverity.MEDIUM, i.getSeverity()));
    }

    @Test
    void testTimelinessChecker_AcceptableWindow() {
        StagedRecord record = new StagedRecord();
        // Today is 2026. 2020-01-01 is > 2 years in past. 2099 is in future.
        record.setRawPayload("{\"order_date\": \"2020-01-01\"}");

        QualityRun run = new QualityRun();
        List<QualityIssue> issues = timelinessChecker.check(record, run);

        assertEquals(1, issues.size());
        assertEquals("TIMELINESS_STALE_DATE", issues.get(0).getRuleCode());
        assertEquals(QualitySeverity.MEDIUM, issues.get(0).getSeverity());
    }

    @Test
    void testAccuracyChecker_Dictionaries() {
        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"currency\": \"XYZ\", \"country\": \"Atlantis\"}");

        QualityRun run = new QualityRun();
        List<QualityIssue> issues = accuracyChecker.check(record, run);

        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(i -> "ACCURACY_INVALID_CURRENCY".equals(i.getRuleCode())));
        assertTrue(issues.stream().anyMatch(i -> "ACCURACY_INVALID_COUNTRY".equals(i.getRuleCode())));

        issues.forEach(i -> assertEquals(QualitySeverity.LOW, i.getSeverity()));
    }

    @Test
    void testScoringCalculation() {
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        DataSource source = new DataSource();
        source.setId(UUID.randomUUID());
        job.setSource(source);

        // Setup mock response for lookup
        when(qualityScoreRepository.findByJobId(job.getId())).thenReturn(Optional.empty());
        when(qualityScoreRepository.save(any(QualityScore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Create issues
        List<QualityIssue> issues = new ArrayList<>();
        // 1 Critical Completeness Issue
        issues.add(createIssue(QualityDimension.COMPLETENESS, QualitySeverity.CRITICAL));
        // 1 High Validity Issue
        issues.add(createIssue(QualityDimension.VALIDITY, QualitySeverity.HIGH));
        // 1 Medium Consistency Issue
        issues.add(createIssue(QualityDimension.CONSISTENCY, QualitySeverity.MEDIUM));
        // 1 Low Accuracy Issue
        issues.add(createIssue(QualityDimension.ACCURACY, QualitySeverity.LOW));

        int totalRecords = 10;

        QualityScore score = qualityScoreService.computeAndSaveScores(job, issues, totalRecords);

        // Completeness score: 1.0 - (1 * 1.0) / 10 = 0.9000
        assertEquals(0.9000, score.getScoreCompleteness().doubleValue(), 0.0001);
        // Validity score: 1.0 - (1 * 0.5) / 10 = 0.9500
        assertEquals(0.9500, score.getScoreValidity().doubleValue(), 0.0001);
        // Uniqueness: 1.0
        assertEquals(1.0000, score.getScoreUniqueness().doubleValue(), 0.0001);
        // Consistency: 1.0 - (1 * 0.2) / 10 = 0.9800
        assertEquals(0.9800, score.getScoreConsistency().doubleValue(), 0.0001);
        // Timeliness: 1.0
        assertEquals(1.0000, score.getScoreTimeliness().doubleValue(), 0.0001);
        // Accuracy: 1.0 - (1 * 0.05) / 10 = 0.9950
        assertEquals(0.9950, score.getScoreAccuracy().doubleValue(), 0.0001);

        // Overall Weighted Average:
        // 0.90 * 0.20 + 0.95 * 0.25 + 1.00 * 0.20 + 0.98 * 0.20 + 1.00 * 0.10 + 0.995 * 0.05
        // = 0.18 + 0.2375 + 0.20 + 0.196 + 0.10 + 0.04975 = 0.96325 (rounds to 0.9633)
        assertEquals(0.9633, score.getScoreOverall().doubleValue(), 0.0001);
        assertEquals("A", score.getLetterGrade());
    }

    @Test
    void testQualityEngineOrchestration() {
        UUID jobId = UUID.randomUUID();
        DataSource source = new DataSource();
        source.setId(UUID.randomUUID());

        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setSource(source);

        StagedRecord r1 = new StagedRecord();
        r1.setId(UUID.randomUUID());
        r1.setSource(source);
        // Passes validation
        r1.setRawPayload("{\"customer_name\": \"Acme Corp\", \"total_amount\": \"150.00\", \"sku\": \"PROD-101\"}");

        StagedRecord r2 = new StagedRecord();
        r2.setId(UUID.randomUUID());
        r2.setSource(source);
        // Fails with a critical Completeness issue
        r2.setRawPayload("{\"customer_name\": \"\", \"total_amount\": \"150.00\", \"sku\": \"PROD-102\"}");

        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stagedRecordRepository.findByJobId(eq(jobId), any(PageRequest.class))).thenReturn(List.of(r1, r2), Collections.emptyList());
        when(qualityRunRepository.save(any(QualityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(qualityScoreRepository.findByJobId(jobId)).thenReturn(Optional.empty());

        qualityEngineService.runQualityEngine(jobId);

        // Verify rejected record was cataloged
        ArgumentCaptor<List<RejectedRecord>> rejectedCaptor = ArgumentCaptor.forClass(List.class);
        verify(rejectedRecordRepository).saveAll(rejectedCaptor.capture());
        assertEquals(1, rejectedCaptor.getValue().size());
        assertEquals("Required field 'customer_name' is null or blank", rejectedCaptor.getValue().get(0).getReason());

        // Verify issues were saved
        verify(qualityIssueRepository).saveAll(anyList());

        // Verify job stats were updated: passed = 1, failed = 1
        verify(ingestionJobRepository).save(job);
        assertEquals(1, job.getTotalQualityPass());
        assertEquals(1, job.getTotalQualityFail());
    }

    private QualityIssue createIssue(QualityDimension dim, QualitySeverity sev) {
        QualityIssue issue = new QualityIssue();
        issue.setDimension(dim);
        issue.setSeverity(sev);
        return issue;
    }
}
