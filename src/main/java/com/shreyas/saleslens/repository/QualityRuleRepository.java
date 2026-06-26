package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.QualityRule;
import com.shreyas.saleslens.model.enums.QualityDimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QualityRuleRepository extends JpaRepository<QualityRule, UUID> {
    List<QualityRule> findByDimensionAndActiveTrue(QualityDimension dimension);
    List<QualityRule> findByActiveTrue();
}
