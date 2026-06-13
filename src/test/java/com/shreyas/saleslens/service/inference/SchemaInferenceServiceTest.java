package com.shreyas.saleslens.service.inference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.InferredType;
import com.shreyas.saleslens.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaInferenceServiceTest {

    @Mock
    private StagedRecordRepository stagedRecordRepository;

    @Mock
    private SourceSchemaRepository sourceSchemaRepository;

    @Mock
    private SourceSchemaFieldRepository sourceSchemaFieldRepository;

    @Mock
    private DataProfileRepository dataProfileRepository;

    @Mock
    private FieldProfileRepository fieldProfileRepository;

    @Mock
    private TypeDetectionService typeDetectionService;

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SchemaInferenceService service;

    private UUID jobId;
    private IngestionJob job;
    private DataSource source;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        source = new DataSource();
        source.setId(UUID.randomUUID());

        job = new IngestionJob();
        job.setId(jobId);
        job.setSource(source);
    }

    @Test
    void testRunInferenceNoRecords() {
        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(stagedRecordRepository.findByJobId(eq(jobId), any(Pageable.class))).thenReturn(Collections.emptyList());

        service.runInference(jobId);

        verifyNoInteractions(typeDetectionService, sourceSchemaRepository, dataProfileRepository);
    }

    @Test
    void testRunInferenceNewSchemaCreated() throws IOException {
        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"Sales\": \"123.45\", \"Category\": \"Furniture\"}");
        when(stagedRecordRepository.findByJobId(eq(jobId), any(Pageable.class))).thenReturn(List.of(record));

        when(typeDetectionService.detectType(any())).thenReturn(InferredType.DECIMAL);

        when(sourceSchemaRepository.findBySourceIdAndStatus(source.getId(), SourceSchema.STATUS_ACTIVE)).thenReturn(Optional.empty());
        when(sourceSchemaRepository.findTopBySourceIdOrderByVersionDesc(source.getId())).thenReturn(Optional.empty());

        SourceSchema savedSchema = new SourceSchema();
        savedSchema.setId(UUID.randomUUID());
        savedSchema.setVersion(1);
        savedSchema.setStatus(SourceSchema.STATUS_ACTIVE);
        when(sourceSchemaRepository.save(any(SourceSchema.class))).thenReturn(savedSchema);

        DataProfile savedProfile = new DataProfile();
        savedProfile.setId(UUID.randomUUID());
        when(dataProfileRepository.save(any(DataProfile.class))).thenReturn(savedProfile);

        service.runInference(jobId);

        ArgumentCaptor<SourceSchema> schemaCaptor = ArgumentCaptor.forClass(SourceSchema.class);
        verify(sourceSchemaRepository).save(schemaCaptor.capture());
        assertEquals(1, schemaCaptor.getValue().getVersion());
        assertEquals(SourceSchema.STATUS_ACTIVE, schemaCaptor.getValue().getStatus());

        verify(sourceSchemaFieldRepository, times(2)).save(any(SourceSchemaField.class));
        verify(dataProfileRepository).save(any(DataProfile.class));
        verify(fieldProfileRepository, times(2)).save(any(FieldProfile.class));
    }

    @Test
    void testRunInferenceNoDriftUsesExistingSchema() {
        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"Sales\": \"123.45\"}");
        when(stagedRecordRepository.findByJobId(eq(jobId), any(Pageable.class))).thenReturn(List.of(record));

        when(typeDetectionService.detectType(any())).thenReturn(InferredType.DECIMAL);

        SourceSchema activeSchema = new SourceSchema();
        activeSchema.setId(UUID.randomUUID());
        activeSchema.setVersion(1);
        activeSchema.setStatus(SourceSchema.STATUS_ACTIVE);
        when(sourceSchemaRepository.findBySourceIdAndStatus(source.getId(), SourceSchema.STATUS_ACTIVE)).thenReturn(Optional.of(activeSchema));

        SourceSchemaField activeField = new SourceSchemaField();
        activeField.setFieldName("Sales");
        activeField.setInferredType(InferredType.DECIMAL);
        when(sourceSchemaFieldRepository.findBySchemaId(activeSchema.getId())).thenReturn(List.of(activeField));

        DataProfile savedProfile = new DataProfile();
        savedProfile.setId(UUID.randomUUID());
        when(dataProfileRepository.save(any(DataProfile.class))).thenReturn(savedProfile);

        service.runInference(jobId);

        verify(sourceSchemaRepository, never()).save(any(SourceSchema.class));
        verify(sourceSchemaFieldRepository, never()).save(any(SourceSchemaField.class));
        verify(dataProfileRepository).save(any(DataProfile.class));
        verify(fieldProfileRepository).save(any(FieldProfile.class));
    }

    @Test
    void testRunInferenceWithDriftCreatesNewSchemaVersion() {
        when(ingestionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        StagedRecord record = new StagedRecord();
        record.setRawPayload("{\"Sales\": \"123.45\", \"NewField\": \"hello\"}");
        when(stagedRecordRepository.findByJobId(eq(jobId), any(Pageable.class))).thenReturn(List.of(record));

        when(typeDetectionService.detectType(any())).thenReturn(InferredType.DECIMAL);

        SourceSchema activeSchema = new SourceSchema();
        activeSchema.setId(UUID.randomUUID());
        activeSchema.setVersion(1);
        activeSchema.setStatus(SourceSchema.STATUS_ACTIVE);
        when(sourceSchemaRepository.findBySourceIdAndStatus(source.getId(), SourceSchema.STATUS_ACTIVE)).thenReturn(Optional.of(activeSchema));

        SourceSchemaField activeField = new SourceSchemaField();
        activeField.setFieldName("Sales");
        activeField.setInferredType(InferredType.DECIMAL);
        when(sourceSchemaFieldRepository.findBySchemaId(activeSchema.getId())).thenReturn(List.of(activeField));

        when(sourceSchemaRepository.findTopBySourceIdOrderByVersionDesc(source.getId())).thenReturn(Optional.of(activeSchema));

        SourceSchema savedSchema = new SourceSchema();
        savedSchema.setId(UUID.randomUUID());
        savedSchema.setVersion(2);
        savedSchema.setStatus(SourceSchema.STATUS_ACTIVE);
        when(sourceSchemaRepository.save(any(SourceSchema.class))).thenReturn(savedSchema);

        DataProfile savedProfile = new DataProfile();
        savedProfile.setId(UUID.randomUUID());
        when(dataProfileRepository.save(any(DataProfile.class))).thenReturn(savedProfile);
        service.runInference(jobId);

        ArgumentCaptor<SourceSchema> schemaCaptor = ArgumentCaptor.forClass(SourceSchema.class);
        verify(sourceSchemaRepository, times(2)).save(schemaCaptor.capture());
        
        List<SourceSchema> captured = schemaCaptor.getAllValues();
        assertEquals(SourceSchema.STATUS_SUPERSEDED, captured.get(0).getStatus());
        assertEquals(SourceSchema.STATUS_ACTIVE, captured.get(1).getStatus());
        assertEquals(2, captured.get(1).getVersion());

        verify(sourceSchemaFieldRepository, times(2)).save(any(SourceSchemaField.class));
    }
}
