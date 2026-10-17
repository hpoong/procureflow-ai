package com.hopoong.procureflow.purchaseorder.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderLine;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    List<PurchaseOrderLine> findByPoIdOrderByLineNo(UUID poId);
}
