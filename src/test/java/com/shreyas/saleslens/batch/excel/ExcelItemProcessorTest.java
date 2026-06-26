package com.shreyas.saleslens.batch.excel;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.model.enums.JobStatus;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import com.shreyas.saleslens.repository.IngestionJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelItemProcessorTest {

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private DataSourceRepository dataSourceRepository;

    private ExcelItemProcessor processor;
    private IngestionJob job;
    private DataSource source;

    @BeforeEach
    void setUp() {
        UUID jobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        job = new IngestionJob();
        job.setId(jobId);
        job.setStatus(JobStatus.RUNNING);

        source = new DataSource();
        source.setId(sourceId);
        source.setName("Test Excel Source");
        source.setSourceType(SourceType.EXCEL_FILE);
        source.setTrustScore(BigDecimal.valueOf(0.8));
        source.setActive(true);

        when(ingestionJobRepository.getReferenceById(jobId)).thenReturn(job);
        when(dataSourceRepository.getReferenceById(sourceId)).thenReturn(source);

        processor = new ExcelItemProcessor(
                ingestionJobRepository, dataSourceRepository,
                jobId.toString(), sourceId.toString());
    }

    @Test
    void process_normalMap_createsStagedRecordWithCorrectRawPayload() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", "John");
        row.put("email", "john@example.com");

        StagedRecord record = processor.process(row);

        assertThat(record.getJob()).isSameAs(job);
        assertThat(record.getSource()).isSameAs(source);
        assertThat(record.getRawPayload()).isEqualTo("{\"name\":\"John\",\"email\":\"john@example.com\"}");
        assertThat(record.getRowNumber()).isEqualTo(1);
        assertThat(record.getRecordHash()).isNotBlank();
    }

    @Test
    void process_sameInput_producesDeterministicHash() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", "Alice");
        row.put("email", "alice@example.com");

        StagedRecord first = processor.process(row);
        StagedRecord second = processor.process(row);

        assertThat(first.getRecordHash()).isEqualTo(second.getRecordHash());
    }

    @Test
    void process_multipleRows_incrementsRowNumber() {
        Map<String, String> row1 = Map.of("name", "A");
        Map<String, String> row2 = Map.of("name", "B");
        Map<String, String> row3 = Map.of("name", "C");

        assertThat(processor.process(row1).getRowNumber()).isEqualTo(1);
        assertThat(processor.process(row2).getRowNumber()).isEqualTo(2);
        assertThat(processor.process(row3).getRowNumber()).isEqualTo(3);
    }

    @Test
    void process_mapWithNullValues_includesNullInJson() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", "John");
        row.put("phone", null);

        StagedRecord record = processor.process(row);

        assertThat(record.getRawPayload()).contains("\"phone\":null");
    }

    @Test
    void process_emptyMap_returnsEmptyJsonObject() {
        Map<String, String> row = new HashMap<>();

        StagedRecord record = processor.process(row);

        assertThat(record.getRawPayload()).isEqualTo("{}");
        assertThat(record.getRowNumber()).isEqualTo(1);
        assertThat(record.getRecordHash()).isNotBlank();
    }

    @Test
    void process_largeRowNumber_continuesIncrementing() {
        Map<String, String> row = Map.of("col", "val");

        int count = 100;
        for (int i = 1; i <= count; i++) {
            StagedRecord record = processor.process(row);
            assertThat(record.getRowNumber()).isEqualTo(i);
        }
    }
}
