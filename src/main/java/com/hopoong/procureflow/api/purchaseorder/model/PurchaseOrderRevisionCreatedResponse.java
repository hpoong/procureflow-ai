package com.hopoong.procureflow.api.purchaseorder.model;

import java.time.Instant;
import java.util.UUID;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrder;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderRevision;
import com.hopoong.procureflow.domain.entity.purchaseorder.RevisionStatus;

public record PurchaseOrderRevisionCreatedResponse(
        UUID poId,
        String poNumber,
        UUID revisionId,
        int revision,
        RevisionStatus revisionStatus,
        int currentRevision,
        long poVersion,
        Instant createdAt) {

    public static PurchaseOrderRevisionCreatedResponse of(PurchaseOrder po, PurchaseOrderRevision revision) {
        return new PurchaseOrderRevisionCreatedResponse(po.getId(), po.getPoNumber(), revision.getId(),
                revision.getRevision(), revision.getRevisionStatus(), po.getCurrentRevision(), po.getVersion(),
                revision.getCreatedAt());
    }
}
