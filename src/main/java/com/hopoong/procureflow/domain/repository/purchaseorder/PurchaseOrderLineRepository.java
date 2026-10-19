package com.hopoong.procureflow.domain.repository.purchaseorder;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderLine;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    List<PurchaseOrderLine> findByPoIdOrderByLineNo(UUID poId);
}
