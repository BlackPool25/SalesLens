package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.Users;
import com.shreyas.saleslens.model.enums.SourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DataSourceRepository extends JpaRepository<DataSource, UUID> {
    List<DataSource> findByCreatedBy(Users createdBy);
    Page<DataSource> findByCreatedBy(Users createdBy, Pageable pageable);

    List<DataSource> findBySourceTypeInAndActive(List<SourceType> sourceTypes, boolean active);

    List<DataSource> findBySourceTypeAndActive(SourceType sourceType, boolean active);

    @Query(value = "SELECT * FROM data_sources WHERE source_type = 'KAFKA_STREAM' AND active = true AND connection_config @> jsonb_build_object('sourceSystems', to_jsonb(ARRAY[?1]::text[]))", nativeQuery = true)
    List<DataSource> findBySourceSystem(String sourceSystem);
}
