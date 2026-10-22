package com.hopoong.procureflow.domain.repository.purchaseorder;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrder;

import jakarta.persistence.LockModeType;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Query(value = "SELECT nextval('po_number_seq')", nativeQuery = true)
    long nextPoNumberSequence();

    Optional<PurchaseOrder> findByIdAndOrgId(UUID id, String orgId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PurchaseOrder p WHERE p.id = :id AND p.orgId = :orgId")
    Optional<PurchaseOrder> findForUpdateByIdAndOrgId(@Param("id") UUID id, @Param("orgId") String orgId);
}
