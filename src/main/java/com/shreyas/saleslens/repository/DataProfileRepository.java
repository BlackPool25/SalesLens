package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.DataProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataProfileRepository extends JpaRepository<DataProfile, UUID> {

    Optional<DataProfile> findTopBySourceIdOrderByCreatedAtDesc(UUID sourceId);

    List<DataProfile> findTop3BySourceIdOrderByCreatedAtDesc(UUID sourceId);
}
