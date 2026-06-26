package com.shreyas.saleslens.service.ingestion;

import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.repository.StagedRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "jwt.secret=test-secret-key-for-testing-purposes-only"
})
@EmbeddedKafka(
    partitions = 1,
    topics = {"sales.live", "sales.live.DLT", "inventory-updates"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@ActiveProfiles("test")
@Tag("kafka")
class LiveSalesEventConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private KafkaSourceRegistryService sourceRegistry;

    @MockitoBean
    private StreamIngestionJobManager jobManager;

    @MockitoBean
    private StagedRecordRepository stagedRecordRepository;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private LettuceConnectionFactory redisConnectionFactory;

    private com.shreyas.saleslens.model.DataSource testSource;
    private IngestionJob testJob;

    @BeforeEach
    void setUp() throws Exception {
        // Mock DataSource connection to prevent Hibernate NPE on context init
        // (mirrors the pattern in SaleslensApplicationTests)
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(conn.getMetaData()).thenReturn(meta);
        when(dataSource.getConnection()).thenReturn(conn);

        // Set up test DataSource (no DB persistence needed — services are mocked)
        testSource = new com.shreyas.saleslens.model.DataSource();
        testSource.setId(UUID.randomUUID());
        testSource.setName("Test POS Terminal");

        // Set up test IngestionJob
        testJob = new IngestionJob();
        testJob.setId(UUID.randomUUID());
        testJob.setSource(testSource);
        testJob.setStatus(JobStatus.PENDING);

        // Configure mocks for successful consumer flow
        when(sourceRegistry.resolveSource("pos_terminal_01")).thenReturn(testSource);
        when(jobManager.getOrCreateWindowJob(testSource)).thenReturn(testJob);
    }

    @Test
    void consume_validMessage_createsStagedRecord() throws Exception {
        // Arrange: mock repository to return the saved record
        when(stagedRecordRepository.save(any(StagedRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        String validJson = """
            {
                "event_id": "evt-test-001",
                "source_system": "pos_terminal_01",
                "event_time": "2026-06-26T12:00:00Z",
                "quantity": 5,
                "unit_price": 49.99,
                "currency": "USD"
            }
            """;

        // Act: send message to embedded Kafka
        kafkaTemplate.send("sales.live", validJson).get(10, SECONDS);

        // Assert: wait for consumer to process and save the record via real Kafka flow
        ArgumentCaptor<StagedRecord> captor = ArgumentCaptor.forClass(StagedRecord.class);
        await().atMost(10, SECONDS).untilAsserted(() ->
            verify(stagedRecordRepository, atLeastOnce()).save(captor.capture())
        );

        StagedRecord record = captor.getValue();
        assertThat(record.getSource().getId()).isEqualTo(testSource.getId());
        assertThat(record.getJob().getId()).isEqualTo(testJob.getId());
        assertThat(record.getRawPayload()).contains("evt-test-001");
        assertThat(record.getRecordHash()).isNotBlank();
        assertThat(record.getRowNumber()).isPositive();
    }

    @Test
    void consume_duplicateMessage_dedupWorks() throws Exception {
        // Arrange: first save succeeds, second throws DataIntegrityViolationException
        when(stagedRecordRepository.save(any(StagedRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        String json = """
            {
                "event_id": "evt-dup-001",
                "source_system": "pos_terminal_01",
                "event_time": "2026-06-26T12:00:00Z",
                "quantity": 3
            }
            """;

        // Act: send identical message twice
        kafkaTemplate.send("sales.live", json).get(10, SECONDS);
        kafkaTemplate.send("sales.live", json).get(10, SECONDS);

        // Assert: save is called twice (first success, second caught by consumer)
        await().atMost(10, SECONDS).untilAsserted(() ->
            verify(stagedRecordRepository, times(2)).save(any(StagedRecord.class))
        );

        // No exception should propagate to the test — consumer handles duplicates gracefully
    }

    @Test
    void consume_invalidJson_handlesGracefully() throws Exception {
        // Arrange: mock repository for subsequent valid message check
        when(stagedRecordRepository.save(any(StagedRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: send invalid JSON — consumer throws, error handler retries 3x then DLT
        kafkaTemplate.send("sales.live", "not-valid-json-at-all").get(10, SECONDS);

        // Wait for consumer to process (and fail on) the invalid message
        await().pollDelay(3, SECONDS).untilAsserted(() -> assertThat(true).isTrue());

        // Send a valid message — consumer should still be running after DLT routing
        String validJson = """
            {"event_id":"evt-after-invalid","source_system":"pos_terminal_01","event_time":"2026-06-26T12:05:00Z","quantity":1}
            """;
        kafkaTemplate.send("sales.live", validJson).get(10, SECONDS);

        // Assert: consumer processed the valid message after the invalid one
        await().atMost(10, SECONDS).untilAsserted(() ->
            verify(stagedRecordRepository, atLeastOnce()).save(any(StagedRecord.class))
        );
    }
}
