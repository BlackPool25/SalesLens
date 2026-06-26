package com.shreyas.saleslens.batch.jdbc;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import com.shreyas.saleslens.service.ingestion.StagedRecordHelper;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@StepScope
public class JdbcItemProcessor implements ItemProcessor<Map<String, String>, StagedRecord> {

    private final IngestionJob jobRef;
    private final DataSource sourceRef;
    private final AtomicInteger rowCounter = new AtomicInteger(1);

    public JdbcItemProcessor(
            IngestionJobRepository ingestionJobRepository,
            DataSourceRepository dataSourceRepository,
            @Value("#{jobParameters['ingestionJobId']}") String ingestionJobId,
            @Value("#{jobParameters['sourceId']}") String sourceId) {
        this.jobRef = ingestionJobRepository.getReferenceById(UUID.fromString(ingestionJobId));
        this.sourceRef = dataSourceRepository.getReferenceById(UUID.fromString(sourceId));
    }

    @Override
    public StagedRecord process(Map<String, String> row) {
        return StagedRecordHelper.toStagedRecord(jobRef, sourceRef, rowCounter.getAndIncrement(), row);
    }
}
