package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.FieldMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface FieldMappingRepository extends JpaRepository<FieldMapping, UUID> {

    List<FieldMapping> findBySourceId(UUID sourceId);
    Page<FieldMapping> findBySourceId(UUID sourceId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from FieldMapping f where f.source.id = :sourceId")
    void deleteBySourceId(@Param("sourceId") UUID sourceId);

    @Modifying
    @Transactional
    @Query("delete from FieldMapping f where f.source.id = :sourceId and f.sourceFieldName = :fieldName")
    void deleteBySourceIdAndSourceFieldName(@Param("sourceId") UUID sourceId, @Param("fieldName") String fieldName);
}
