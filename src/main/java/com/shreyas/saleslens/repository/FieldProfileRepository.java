package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.FieldProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FieldProfileRepository extends JpaRepository<FieldProfile, UUID> {

    List<FieldProfile> findByProfileId(UUID profileId);
}
