package com.hopoong.procureflow.domain.repository.masterdata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.domain.entity.masterdata.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByIdAndOrgId(UUID id, String orgId);

    boolean existsByOrgIdAndSupplierCode(String orgId, String supplierCode);

    List<Supplier> findAllByOrgIdOrderBySupplierCode(String orgId);
}
