package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DataSourceRepository extends JpaRepository<DataSource, UUID> {
}
