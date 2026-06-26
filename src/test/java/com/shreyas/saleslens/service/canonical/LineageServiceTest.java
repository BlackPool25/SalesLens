package com.shreyas.saleslens.service.canonical;

import com.shreyas.saleslens.model.DataLineage;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.repository.DataLineageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LineageServiceTest {

    @Mock
    private DataLineageRepository dataLineageRepository;

    @InjectMocks
    private LineageService lineageService;

    @Test
    void writeLineage_SavesDataLineageWithAllCorrectFields() {
        UUID canonicalId = UUID.randomUUID();
        String canonicalType = "order";

        DataSource source = new DataSource();
        source.setId(UUID.randomUUID());

        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());

        StagedRecord stagedRecord = new StagedRecord();
        stagedRecord.setId(UUID.randomUUID());

        String transformations = "[{\"step\":\"transform\",\"fromField\":\"source_col\",\"toField\":\"canonical.field\",\"rule\":\"exact\",\"inputValue\":\"x\",\"outputValue\":\"y\"}]";

        DataLineage savedLineage = new DataLineage();
        savedLineage.setId(UUID.randomUUID());
        savedLineage.setCanonicalId(canonicalId);
        savedLineage.setCanonicalType(canonicalType);
        savedLineage.setSource(source);
        savedLineage.setJob(job);
        savedLineage.setStagedRecord(stagedRecord);
        savedLineage.setTransformations(transformations);

        when(dataLineageRepository.save(any(DataLineage.class))).thenReturn(savedLineage);

        DataLineage result = lineageService.writeLineage(canonicalId, canonicalType, source, job, stagedRecord, transformations);

        assertNotNull(result);
        assertEquals(canonicalId, result.getCanonicalId());
        assertEquals(canonicalType, result.getCanonicalType());
        assertEquals(source, result.getSource());
        assertEquals(job, result.getJob());
        assertEquals(stagedRecord, result.getStagedRecord());
        assertEquals(transformations, result.getTransformations());

        verify(dataLineageRepository).save(any(DataLineage.class));
    }

    @Test
    void getLineageByCanonicalId_ReturnsRecordsInDescOrder() {
        UUID canonicalId = UUID.randomUUID();

        DataLineage lineage1 = new DataLineage();
        lineage1.setId(UUID.randomUUID());
        lineage1.setCanonicalId(canonicalId);

        DataLineage lineage2 = new DataLineage();
        lineage2.setId(UUID.randomUUID());
        lineage2.setCanonicalId(canonicalId);

        List<DataLineage> expected = List.of(lineage1, lineage2);
        when(dataLineageRepository.findByCanonicalIdOrderByCreatedAtDesc(canonicalId)).thenReturn(expected);

        List<DataLineage> result = lineageService.getLineageByCanonicalId(canonicalId);

        assertEquals(2, result.size());
        assertSame(lineage1, result.get(0));
        assertSame(lineage2, result.get(1));
        verify(dataLineageRepository).findByCanonicalIdOrderByCreatedAtDesc(canonicalId);
    }

    @Test
    void getLineageByCanonicalId_WithNonExistentId_ReturnsEmptyList() {
        UUID canonicalId = UUID.randomUUID();
        when(dataLineageRepository.findByCanonicalIdOrderByCreatedAtDesc(canonicalId)).thenReturn(Collections.emptyList());

        List<DataLineage> result = lineageService.getLineageByCanonicalId(canonicalId);

        assertTrue(result.isEmpty());
        verify(dataLineageRepository).findByCanonicalIdOrderByCreatedAtDesc(canonicalId);
    }

    @Test
    void writeLineage_WithNullTransformations_SavedAsNullJsonb() {
        UUID canonicalId = UUID.randomUUID();
        DataSource source = new DataSource();
        source.setId(UUID.randomUUID());
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        StagedRecord stagedRecord = new StagedRecord();
        stagedRecord.setId(UUID.randomUUID());

        DataLineage savedLineage = new DataLineage();
        savedLineage.setId(UUID.randomUUID());
        savedLineage.setCanonicalId(canonicalId);
        savedLineage.setCanonicalType("order");
        savedLineage.setSource(source);
        savedLineage.setJob(job);
        savedLineage.setStagedRecord(stagedRecord);
        savedLineage.setTransformations(null);

        when(dataLineageRepository.save(any(DataLineage.class))).thenReturn(savedLineage);

        DataLineage result = lineageService.writeLineage(canonicalId, "order", source, job, stagedRecord, null);

        assertNotNull(result);
        assertNull(result.getTransformations());
        assertNotNull(result.getStagedRecord());
        verify(dataLineageRepository).save(any(DataLineage.class));
    }

    @Test
    void writeLineage_WithNullStagedRecord_SavedAsNullReference() {
        UUID canonicalId = UUID.randomUUID();
        DataSource source = new DataSource();
        source.setId(UUID.randomUUID());
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());

        DataLineage savedLineage = new DataLineage();
        savedLineage.setId(UUID.randomUUID());
        savedLineage.setCanonicalId(canonicalId);
        savedLineage.setCanonicalType("order");
        savedLineage.setSource(source);
        savedLineage.setJob(job);
        savedLineage.setStagedRecord(null);

        when(dataLineageRepository.save(any(DataLineage.class))).thenReturn(savedLineage);

        DataLineage result = lineageService.writeLineage(canonicalId, "order", source, job, null, null);

        assertNotNull(result);
        assertNull(result.getStagedRecord());
        verify(dataLineageRepository).save(any(DataLineage.class));
    }
}
