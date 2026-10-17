package com.hopoong.procureflow.purchaseorder.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrder;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderLine;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderStatus;

public record PurchaseOrderCreatedResponse(
        UUID id,
        String poNumber,
        PurchaseOrderStatus status,
        int currentRevision,
        long version,
        Instant createdAt,
        List<Line> lines) {

    public record Line(UUID id, int lineNo) {
    }

    public static PurchaseOrderCreatedResponse of(PurchaseOrder po, List<PurchaseOrderLine> lines) {
        return new PurchaseOrderCreatedResponse(po.getId(), po.getPoNumber(), po.getStatus(), po.getCurrentRevision(),
                po.getVersion(), po.getCreatedAt(),
                lines.stream().map(line -> new Line(line.getId(), line.getLineNo())).toList());
    }
}
