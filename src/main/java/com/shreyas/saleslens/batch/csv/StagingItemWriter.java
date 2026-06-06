package com.shreyas.saleslens.batch.csv;

import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.repository.StagedRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StagingItemWriter implements ItemWriter<StagedRecord> {

    private final StagedRecordRepository stagedRecordRepository;

    @Override
    public void write(Chunk<? extends StagedRecord> chunk) {
        stagedRecordRepository.saveAll(chunk.getItems());
    }
}
