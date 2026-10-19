package com.hopoong.procureflow.domain.repository.masterdata;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.domain.entity.masterdata.Warehouse;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    Optional<Warehouse> findByIdAndOrgId(UUID id, UUID orgId);
}
