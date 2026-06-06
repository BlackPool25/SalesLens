package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.IngestionJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {
}
