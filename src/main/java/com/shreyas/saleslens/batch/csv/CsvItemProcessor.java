package com.shreyas.saleslens.batch.csv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@StepScope
public class CsvItemProcessor implements ItemProcessor<FieldSet, StagedRecord> {

    private final ObjectMapper objectMapper;
    private final IngestionJob jobRef;
    private final DataSource sourceRef;

    public CsvItemProcessor(
            ObjectMapper objectMapper,
            @Value("#{jobParameters['ingestionJobId']}") String ingestionJobId,
            @Value("#{jobParameters['sourceId']}") String sourceId) {
        this.objectMapper = objectMapper;
        this.jobRef = referenceWithId(IngestionJob::new, UUID.fromString(ingestionJobId));
        this.sourceRef = referenceWithId(DataSource::new, UUID.fromString(sourceId));
    }

    private static <T> T referenceWithId(java.util.function.Supplier<T> factory, UUID id) {
        T ref = factory.get();
        try {
            var idField = ref.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(ref, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set id on " + ref.getClass().getSimpleName(), e);
        }
        return ref;
    }

    @Override
    public StagedRecord process(FieldSet fieldSet) {
        Map<String, String> row = new LinkedHashMap<>();
        String[] names = fieldSet.getNames();
        for (int i = 0; i < names.length; i++) {
            String value = fieldSet.readString(i);
            row.put(names[i], value.isEmpty() ? null : value);
        }

        StagedRecord record = new StagedRecord();
        record.setJob(jobRef);
        record.setSource(sourceRef);
        try {
            record.setRawPayload(objectMapper.writeValueAsString(row));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CSV row to JSON", e);
        }
        return record;
    }
}
