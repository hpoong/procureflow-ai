package com.hopoong.procureflow.domain.repository.masterdata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.domain.entity.masterdata.Plant;

public interface PlantRepository extends JpaRepository<Plant, UUID> {

    Optional<Plant> findByIdAndOrgId(UUID id, String orgId);

    boolean existsByOrgIdAndPlantCode(String orgId, String plantCode);

    List<Plant> findAllByOrgIdOrderByPlantCode(String orgId);
}
