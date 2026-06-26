package com.shreyas.saleslens.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.repository.StagedRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveSalesEventConsumerTest {

    @Mock
    private KafkaSourceRegistryService sourceRegistry;
    @Mock
    private StreamIngestionJobManager jobManager;
    @Mock
    private StagedRecordRepository stagedRecordRepository;

    private ObjectMapper objectMapper;
    private LiveSalesEventConsumer consumer;

    private DataSource dataSource;
    private IngestionJob job;
    private UUID sourceId;
    private UUID jobId;

    @Captor
    private ArgumentCaptor<StagedRecord> recordCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new LiveSalesEventConsumer(sourceRegistry, jobManager, stagedRecordRepository, objectMapper);

        sourceId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        dataSource = new DataSource();
        dataSource.setId(sourceId);

        job = new IngestionJob();
        job.setId(jobId);
        job.setSource(dataSource);
        job.setStatus(JobStatus.PENDING);
    }

    @Test
    void consume_validMessage_createsStagedRecord() {
        String validJson = "{\"event_id\":\"evt-001\",\"source_system\":\"pos_01\",\"event_time\":\"2026-06-26T12:00:00Z\",\"quantity\":5}";

        when(sourceRegistry.resolveSource("pos_01")).thenReturn(dataSource);
        when(jobManager.getOrCreateWindowJob(dataSource)).thenReturn(job);
        when(stagedRecordRepository.save(any(StagedRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        consumer.consume(validJson, "sales.live", 1L, 0);

        verify(stagedRecordRepository).save(recordCaptor.capture());
        StagedRecord saved = recordCaptor.getValue();

        assertThat(saved.getJob().getId()).isEqualTo(jobId);
        assertThat(saved.getSource().getId()).isEqualTo(sourceId);
        assertThat(saved.getRawPayload()).isEqualTo(validJson);
        assertThat(saved.getRecordHash()).isNotBlank();
        assertThat(saved.getRowNumber()).isPositive();
    }

    @Test
    void consume_duplicateMessage_caughtAndSkipped() {
        String validJson = "{\"event_id\":\"evt-002\",\"source_system\":\"pos_01\",\"event_time\":\"2026-06-26T12:01:00Z\",\"quantity\":3}";

        when(sourceRegistry.resolveSource("pos_01")).thenReturn(dataSource);
        when(jobManager.getOrCreateWindowJob(dataSource)).thenReturn(job);
        doThrow(DataIntegrityViolationException.class).when(stagedRecordRepository).save(any(StagedRecord.class));

        consumer.consume(validJson, "sales.live", 2L, 0);

        verify(stagedRecordRepository).save(any(StagedRecord.class));
        // No exception should propagate — duplicate is caught and logged
    }

    @Test
    void consume_missingEventId_throwsIllegalArgument() {
        String jsonMissingEventId = "{\"source_system\":\"pos_01\",\"event_time\":\"2026-06-26T12:00:00Z\",\"quantity\":5}";

        assertThatThrownBy(() -> consumer.consume(jsonMissingEventId, "sales.live", 3L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event_id");
    }

    @Test
    void consume_missingSourceSystem_throwsIllegalArgument() {
        String jsonMissingSource = "{\"event_id\":\"evt-003\",\"event_time\":\"2026-06-26T12:00:00Z\",\"quantity\":5}";

        assertThatThrownBy(() -> consumer.consume(jsonMissingSource, "sales.live", 4L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source_system");
    }

    @Test
    void consume_invalidJson_throwsIllegalArgument() {
        String invalidJson = "not-valid-json-at-all";

        assertThatThrownBy(() -> consumer.consume(invalidJson, "sales.live", 5L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    void consume_unknownSource_throwsUnknownSourceException() {
        String validJson = "{\"event_id\":\"evt-004\",\"source_system\":\"unknown_pos\",\"event_time\":\"2026-06-26T12:00:00Z\",\"quantity\":5}";

        when(sourceRegistry.resolveSource("unknown_pos"))
                .thenThrow(new UnknownSourceException("unknown_pos"));

        assertThatThrownBy(() -> consumer.consume(validJson, "sales.live", 6L, 0))
                .isInstanceOf(UnknownSourceException.class)
                .hasMessageContaining("unknown_pos");
    }

    @Test
    void consume_invalidEventTime_throwsIllegalArgument() {
        String jsonBadTime = "{\"event_id\":\"evt-005\",\"source_system\":\"pos_01\",\"event_time\":\"not-a-date\",\"quantity\":5}";

        assertThatThrownBy(() -> consumer.consume(jsonBadTime, "sales.live", 7L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event_time");
    }
}
