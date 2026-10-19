package com.hopoong.procureflow.domain.repository.purchaseorder;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Query(value = "SELECT nextval('po_number_seq')", nativeQuery = true)
    long nextPoNumberSequence();
}
