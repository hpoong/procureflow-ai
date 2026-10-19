package com.hopoong.procureflow.domain.repository.purchaseorder;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderAuditLog;

public interface PurchaseOrderAuditLogRepository extends JpaRepository<PurchaseOrderAuditLog, UUID> {
}
