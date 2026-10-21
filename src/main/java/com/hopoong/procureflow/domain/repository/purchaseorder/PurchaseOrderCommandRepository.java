package com.hopoong.procureflow.domain.repository.purchaseorder;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderCommand;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderCommandType;

public interface PurchaseOrderCommandRepository extends JpaRepository<PurchaseOrderCommand, UUID> {

    Optional<PurchaseOrderCommand> findByOrgIdAndCommandTypeAndIdempotencyKey(String orgId,
            PurchaseOrderCommandType commandType, String idempotencyKey);
}
