package com.hopoong.procureflow.masterdata.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.masterdata.domain.Plant;

public interface PlantRepository extends JpaRepository<Plant, UUID> {

    Optional<Plant> findByIdAndOrgId(UUID id, UUID orgId);
}
