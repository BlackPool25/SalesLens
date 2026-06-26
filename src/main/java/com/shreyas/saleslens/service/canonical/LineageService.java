package com.shreyas.saleslens.service.canonical;

import com.shreyas.saleslens.model.DataLineage;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.IngestionJob;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.repository.DataLineageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LineageService {

    private final DataLineageRepository dataLineageRepository;

    @Transactional
    public DataLineage writeLineage(UUID canonicalId, String canonicalType, DataSource source,
                                    IngestionJob job, StagedRecord stagedRecord, String transformations) {
        DataLineage lineage = new DataLineage();
        lineage.setCanonicalId(canonicalId);
        lineage.setCanonicalType(canonicalType);
        lineage.setSource(source);
        lineage.setJob(job);
        lineage.setStagedRecord(stagedRecord);
        lineage.setTransformations(transformations);

        DataLineage saved = dataLineageRepository.save(lineage);
        log.info("Lineage written: {} for canonicalType={} canonicalId={}", saved.getId(), canonicalType, canonicalId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DataLineage> getLineageByCanonicalId(UUID canonicalId) {
        return dataLineageRepository.findByCanonicalIdOrderByCreatedAtDesc(canonicalId);
    }
}
