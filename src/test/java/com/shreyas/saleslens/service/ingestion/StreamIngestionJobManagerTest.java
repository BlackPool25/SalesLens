package com.shreyas.saleslens.service.ingestion;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamIngestionJobManagerTest {

    @Mock
    private IngestionJobRepository ingestionJobRepository;
    @Mock
    private DataSourceRepository dataSourceRepository;

    private StreamIngestionJobManager manager;

    private DataSource source;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        manager = new StreamIngestionJobManager(ingestionJobRepository, dataSourceRepository);

        sourceId = UUID.randomUUID();
        source = new DataSource();
        source.setId(sourceId);
        source.setSourceType(SourceType.KAFKA_STREAM);
        source.setActive(true);
    }

    @Test
    void getOrCreateWindowJob_createsFirstJob() {
        IngestionJob savedJob = new IngestionJob();
        savedJob.setId(UUID.randomUUID());
        savedJob.setSource(source);
        savedJob.setStatus(JobStatus.PENDING);
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenReturn(savedJob);

        IngestionJob result = manager.getOrCreateWindowJob(source);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(savedJob.getId());
        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void getOrCreateWindowJob_returnsSameJobOnSecondCall() {
        IngestionJob savedJob = new IngestionJob();
        savedJob.setId(UUID.randomUUID());
        savedJob.setSource(source);
        savedJob.setStatus(JobStatus.PENDING);
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenReturn(savedJob);

        IngestionJob firstCall = manager.getOrCreateWindowJob(source);
        IngestionJob secondCall = manager.getOrCreateWindowJob(source);

        assertThat(firstCall).isNotNull();
        assertThat(secondCall).isNotNull();
        assertThat(firstCall.getId()).isEqualTo(secondCall.getId());
    }

    @Test
    void rotateWindow_returnsJobAndRemovesWindow() {
        IngestionJob savedJob = new IngestionJob();
        savedJob.setId(UUID.randomUUID());
        savedJob.setSource(source);
        savedJob.setStatus(JobStatus.PENDING);
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenReturn(savedJob);

        manager.getOrCreateWindowJob(source);

        Optional<IngestionJob> rotated = manager.rotateWindow(sourceId);

        assertThat(rotated).isPresent();
        assertThat(rotated.get().getId()).isEqualTo(savedJob.getId());
        assertThat(manager.hasActiveWindow(sourceId)).isFalse();
    }

    @Test
    void rotateWindow_emptyIfNoWindow() {
        Optional<IngestionJob> rotated = manager.rotateWindow(sourceId);

        assertThat(rotated).isEmpty();
    }

    @Test
    void rotateWindow_thenGetOrCreate_createsNewJob() {
        IngestionJob firstJob = new IngestionJob();
        firstJob.setId(UUID.randomUUID());
        firstJob.setSource(source);
        firstJob.setStatus(JobStatus.PENDING);

        IngestionJob secondJob = new IngestionJob();
        secondJob.setId(UUID.randomUUID());
        secondJob.setSource(source);
        secondJob.setStatus(JobStatus.PENDING);

        when(ingestionJobRepository.save(any(IngestionJob.class)))
                .thenReturn(firstJob, secondJob);

        manager.getOrCreateWindowJob(source);

        Optional<IngestionJob> rotated = manager.rotateWindow(sourceId);
        assertThat(rotated).isPresent();
        assertThat(rotated.get().getId()).isEqualTo(firstJob.getId());

        IngestionJob newJob = manager.getOrCreateWindowJob(source);
        assertThat(newJob.getId()).isEqualTo(secondJob.getId());
        assertThat(newJob.getId()).isNotEqualTo(firstJob.getId());
    }

    @Test
    void hasActiveWindow_falseAfterRotation() {
        IngestionJob savedJob = new IngestionJob();
        savedJob.setId(UUID.randomUUID());
        savedJob.setSource(source);
        savedJob.setStatus(JobStatus.PENDING);
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenReturn(savedJob);

        assertThat(manager.hasActiveWindow(sourceId)).isFalse();

        manager.getOrCreateWindowJob(source);
        assertThat(manager.hasActiveWindow(sourceId)).isTrue();

        manager.rotateWindow(sourceId);
        assertThat(manager.hasActiveWindow(sourceId)).isFalse();
    }
}
