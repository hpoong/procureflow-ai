package com.hopoong.procureflow.purchaseorder.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderCommand;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderCommandType;

public interface PurchaseOrderCommandRepository extends JpaRepository<PurchaseOrderCommand, UUID> {

    Optional<PurchaseOrderCommand> findByOrgIdAndCommandTypeAndIdempotencyKey(UUID orgId,
            PurchaseOrderCommandType commandType, String idempotencyKey);
}
