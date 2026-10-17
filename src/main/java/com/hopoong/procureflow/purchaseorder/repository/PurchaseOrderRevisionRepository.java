package com.hopoong.procureflow.purchaseorder.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderRevision;

public interface PurchaseOrderRevisionRepository extends JpaRepository<PurchaseOrderRevision, UUID> {
}
