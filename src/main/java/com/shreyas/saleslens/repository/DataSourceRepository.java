package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DataSourceRepository extends JpaRepository<DataSource, UUID> {
    List<DataSource> findByCreatedBy(Users createdBy);
}
