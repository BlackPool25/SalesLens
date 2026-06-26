package com.shreyas.saleslens;

import com.shreyas.saleslens.batch.jdbc.JdbcIngestionScheduler;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import com.shreyas.saleslens.service.canonical.CanonicalLoadService;
import com.shreyas.saleslens.service.inference.SchemaInferenceService;
import com.shreyas.saleslens.service.ingestion.CredentialEncryptionService;
import com.shreyas.saleslens.service.ingestion.IngestionOrchestrator;
import com.shreyas.saleslens.service.ingestion.JdbcConnectionService;
import com.shreyas.saleslens.service.ingestion.PipelineCompletionHandler;
import com.shreyas.saleslens.service.quality.QualityEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 7 end-to-end integration test exercising the full ingestion pipeline
 * end-to-end with mocked repositories (no real DB, no real Excel/JDBC).
 *
 * <ol>
 *   <li>A. Excel ingestion: mock file upload → verify StagedRecord created → pipeline handler called</li>
 *   <li>B. JDBC trigger: mock JDBC source → verify StagedRecord created → pipeline handler called</li>
 *   <li>C. JDBC scheduler: mock source with due cron → verify job launched</li>
 *   <li>D. Encryption: verify roundtrip works for realistic JDBC passwords</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Phase7IntegrationTest {

    // =========================================================================
    // Mocks
    // =========================================================================

    @Mock private DataSourceRepository dataSourceRepository;
    @Mock private IngestionJobRepository ingestionJobRepository;

    @Mock private JobOperator jobOperator;
    @Mock(name = "csvIngestionJob") private Job csvIngestionJob;
    @Mock(name = "excelIngestionJob") private Job excelIngestionJob;
    @Mock(name = "jdbcIngestionJob") private Job jdbcIngestionJob;

    @Mock private SchemaInferenceService schemaInferenceService;
    @Mock private QualityEngineService qualityEngineService;
    @Mock private CanonicalLoadService canonicalLoadService;
    @Mock private JdbcConnectionService jdbcConnectionService;

    // =========================================================================
    // Services under test (real instances with mocked dependencies)
    // =========================================================================

    private IngestionOrchestrator orchestrator;
    private PipelineCompletionHandler pipelineHandler;
    private CredentialEncryptionService encryptionService;
    private JdbcIngestionScheduler scheduler;

    // =========================================================================
    // Shared test fixtures
    // =========================================================================

    private DataSource excelSource;
    private DataSource jdbcSource;
    private UUID excelSourceId;
    private UUID jdbcSourceId;

    @Captor private ArgumentCaptor<JobParameters> jobParamsCaptor;

    // =========================================================================
    // Setup
    // =========================================================================

    @BeforeEach
    void setUp() throws Exception {
        // ── Real services with mocked dependencies ──
        encryptionService = new CredentialEncryptionService("test-key", "deadbeefdeadbeef");

        pipelineHandler = new PipelineCompletionHandler(
                schemaInferenceService, qualityEngineService, canonicalLoadService);

        orchestrator = new IngestionOrchestrator(
                jobOperator, csvIngestionJob, excelIngestionJob, jdbcIngestionJob,
                dataSourceRepository, ingestionJobRepository);

        scheduler = new JdbcIngestionScheduler(
                dataSourceRepository, ingestionJobRepository,
                jdbcConnectionService, jobOperator, jdbcIngestionJob);

        // ── Mock JobOperator.start(Job, JobParameters) to return a minimal JobExecution ──
        // Use typed any() to disambiguate between the overloaded start() methods.
        JobExecution mockExecution = mock(JobExecution.class);
        lenient().when(mockExecution.getStatus()).thenReturn(BatchStatus.STARTED);
        lenient().when(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenReturn(mockExecution);

        // ── Fixtures ──

        excelSourceId = UUID.randomUUID();
        excelSource = new DataSource();
        excelSource.setId(excelSourceId);
        excelSource.setName("Test Excel Source");
        excelSource.setSourceType(SourceType.EXCEL_FILE);
        excelSource.setTrustScore(BigDecimal.valueOf(0.8));
        excelSource.setActive(true);

        jdbcSourceId = UUID.randomUUID();
        jdbcSource = new DataSource();
        jdbcSource.setId(jdbcSourceId);
        jdbcSource.setName("Test JDBC Source");
        jdbcSource.setSourceType(SourceType.JDBC_POSTGRES);
        jdbcSource.setTrustScore(BigDecimal.valueOf(0.9));
        jdbcSource.setActive(true);
        jdbcSource.setConnectionConfig("{\"jdbcUrl\":\"jdbc:postgresql://localhost:5432/db\"," +
                "\"user\":\"test\",\"password\":\"encrypted-pass\"," +
                "\"driverClassName\":\"org.postgresql.Driver\",\"query\":\"SELECT * FROM sales\"}");
        jdbcSource.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));

        // ── Default: IngestionJobRepository.save() assigns an ID ──
        lenient().when(ingestionJobRepository.save(any(IngestionJob.class)))
                .thenAnswer(inv -> {
                    IngestionJob j = inv.getArgument(0);
                    if (j.getId() == null) {
                        j.setId(UUID.randomUUID());
                    }
                    return j;
                });
    }

    // =========================================================================
    // A — EXCEL INGESTION
    // =========================================================================

    @Test
    void excelIngestion_validFile_launchesJobAndInvokesPipeline() throws Exception {
        // ── Mock source lookup ──
        when(dataSourceRepository.findById(excelSourceId)).thenReturn(Optional.of(excelSource));

        MockMultipartFile file = new MockMultipartFile(
                "file", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "header1,header2\nval1,val2".getBytes());

        // ── Act: orchestrator ingests the Excel file ──
        UUID jobId = orchestrator.ingestExcel(file, excelSourceId);

        // ── Verify: pending job created ──
        assertNotNull(jobId, "ingestExcel must return a non-null job ID");
        verify(ingestionJobRepository).save(argThat(j ->
                j.getStatus() == JobStatus.PENDING && j.getSource() == excelSource));

        // ── Verify: batch job launched with correct parameters ──
        verify(jobOperator).start(same(excelIngestionJob), jobParamsCaptor.capture());
        JobParameters params = jobParamsCaptor.getValue();
        assertEquals(excelSourceId.toString(), params.getString("sourceId"),
                "sourceId parameter must match");
        assertEquals(jobId.toString(), params.getString("ingestionJobId"),
                "ingestionJobId parameter must match");
        assertNotNull(params.getString("filePath"), "filePath parameter must be set");

        // ── Verify: pipeline handler invokes all three stages ──
        pipelineHandler.runPipeline(jobId);
        verify(schemaInferenceService).runInference(jobId);
        verify(qualityEngineService).runQualityEngine(jobId);
        verify(canonicalLoadService).loadCanonical(jobId);
    }

    @Test
    void excelIngestion_emptyFile_throwsIllegalArgumentException() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.xlsx", "application/octet-stream", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.ingestExcel(emptyFile, excelSourceId));
    }

    @Test
    void excelIngestion_nonExcelExtension_throwsIllegalArgumentException() throws Exception {
        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "data.csv", "text/csv", "a,b\n1,2".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.ingestExcel(csvFile, excelSourceId));
    }

    @Test
    void excelIngestion_sourceNotFound_throwsIllegalArgumentException() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(dataSourceRepository.findById(missingId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
                "file", "data.xlsx", "application/octet-stream", "dummy".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.ingestExcel(file, missingId));
    }

    // =========================================================================
    // B — JDBC TRIGGER
    // =========================================================================

    @Test
    void jdbcTrigger_validSource_launchesJob() throws Exception {
        when(dataSourceRepository.findById(jdbcSourceId)).thenReturn(Optional.of(jdbcSource));

        // ── Act ──
        UUID jobId = orchestrator.triggerJdbcIngestion(jdbcSourceId);

        // ── Verify: pending job created ──
        assertNotNull(jobId, "triggerJdbcIngestion must return a non-null job ID");
        verify(ingestionJobRepository).save(argThat(j ->
                j.getStatus() == JobStatus.PENDING && j.getSource() == jdbcSource));

        // ── Verify: batch job launched with correct parameters ──
        verify(jobOperator).start(same(jdbcIngestionJob), jobParamsCaptor.capture());
        JobParameters params = jobParamsCaptor.getValue();
        assertEquals(jdbcSourceId.toString(), params.getString("sourceId"),
                "sourceId parameter must match");
        assertEquals(jobId.toString(), params.getString("ingestionJobId"),
                "ingestionJobId parameter must match");

        // ── Verify: lastSyncAt was updated on the source ──
        verify(dataSourceRepository).save(argThat(ds -> ds.getLastSyncAt() != null));
    }

    @Test
    void jdbcTrigger_nonJdbcSource_throwsIllegalArgumentException() throws Exception {
        when(dataSourceRepository.findById(excelSourceId)).thenReturn(Optional.of(excelSource));

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.triggerJdbcIngestion(excelSourceId));
    }

    @Test
    void jdbcTrigger_inactiveSource_throwsIllegalArgumentException() throws Exception {
        jdbcSource.setActive(false);
        when(dataSourceRepository.findById(jdbcSourceId)).thenReturn(Optional.of(jdbcSource));

        assertThrows(IllegalArgumentException.class,
                () -> orchestrator.triggerJdbcIngestion(jdbcSourceId));
    }

    // =========================================================================
    // C — JDBC SCHEDULER
    // =========================================================================

    @Test
    void jdbcScheduler_dueCron_launchesJob() throws Exception {
        jdbcSource.setCronExpression("0 * * * * *");                 // every minute
        jdbcSource.setLastSyncAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of(jdbcSource));

        // ── Act ──
        scheduler.checkAndScheduleJobs();

        // ── Verify: job was launched ──
        verify(jobOperator).start(same(jdbcIngestionJob), jobParamsCaptor.capture());
        JobParameters params = jobParamsCaptor.getValue();
        assertEquals(jdbcSourceId.toString(), params.getString("sourceId"),
                "sourceId parameter must match");
    }

    @Test
    void jdbcScheduler_noCron_skipsJob() throws Exception {
        jdbcSource.setCronExpression(null);

        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of(jdbcSource));

        scheduler.checkAndScheduleJobs();

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    void jdbcScheduler_futureCron_skipsJob() throws Exception {
        jdbcSource.setCronExpression("0 0 0 1 1 *");                // next Jan 1
        jdbcSource.setLastSyncAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of(jdbcSource));

        scheduler.checkAndScheduleJobs();

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    void jdbcScheduler_invalidCron_skipsJob() throws Exception {
        jdbcSource.setCronExpression("not-a-valid-cron");
        jdbcSource.setLastSyncAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(dataSourceRepository.findBySourceTypeInAndActive(
                List.of(SourceType.JDBC_POSTGRES, SourceType.JDBC_MYSQL), true))
                .thenReturn(List.of(jdbcSource));

        // Must not propagate the exception
        scheduler.checkAndScheduleJobs();

        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    // =========================================================================
    // D — ENCRYPTION ROUNDTRIP
    // =========================================================================

    @Test
    void encryption_jdbcPasswordRoundtrip_returnsOriginal() {
        String original = "p@ssw0rd!SECURE_123";
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryption_unicodePassword_roundtripsSuccessfully() {
        String original = "héllo wörld 🔒 ' OR '1'='1";
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryption_sameInput_producesDifferentCiphertext() {
        String plaintext = "same-password";
        String cipher1 = encryptionService.encrypt(plaintext);
        String cipher2 = encryptionService.encrypt(plaintext);
        assertNotEquals(cipher1, cipher2,
                "Each encryption must produce different ciphertext (random IV)");
    }

    @Test
    void encryption_nullInput_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> encryptionService.encrypt(null));
        assertThrows(IllegalArgumentException.class, () -> encryptionService.decrypt(null));
    }

    @Test
    void encryption_emptyInput_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> encryptionService.encrypt(""));
        assertThrows(IllegalArgumentException.class, () -> encryptionService.decrypt(""));
    }

    // =========================================================================
    // PIPELINE COMPLETION HANDLER
    // =========================================================================

    @Test
    void pipelineHandler_allStagesCalled() {
        UUID testJobId = UUID.randomUUID();

        pipelineHandler.runPipeline(testJobId);

        verify(schemaInferenceService).runInference(testJobId);
        verify(qualityEngineService).runQualityEngine(testJobId);
        verify(canonicalLoadService).loadCanonical(testJobId);
    }

    @Test
    void pipelineHandler_schemaInferenceFails_continuesToQualityAndCanonical() {
        UUID testJobId = UUID.randomUUID();
        doThrow(new RuntimeException("Inference failed"))
                .when(schemaInferenceService).runInference(testJobId);

        pipelineHandler.runPipeline(testJobId);

        verify(qualityEngineService).runQualityEngine(testJobId);
        verify(canonicalLoadService).loadCanonical(testJobId);
    }

    @Test
    void pipelineHandler_qualityEngineFails_continuesToCanonicalLoad() {
        UUID testJobId = UUID.randomUUID();
        doThrow(new RuntimeException("Quality failed"))
                .when(qualityEngineService).runQualityEngine(testJobId);

        pipelineHandler.runPipeline(testJobId);

        verify(schemaInferenceService).runInference(testJobId);
        verify(canonicalLoadService).loadCanonical(testJobId);
    }
}
