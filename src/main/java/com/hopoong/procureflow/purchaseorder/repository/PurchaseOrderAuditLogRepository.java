package com.hopoong.procureflow.purchaseorder.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderAuditLog;

public interface PurchaseOrderAuditLogRepository extends JpaRepository<PurchaseOrderAuditLog, UUID> {
}
