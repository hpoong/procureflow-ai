package com.hopoong.procureflow.api.purchaseorder.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrder;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderRevision;
import com.hopoong.procureflow.domain.entity.purchaseorder.RevisionStatus;

public record PurchaseOrderRevisionListResponse(
        UUID poId,
        String poNumber,
        int currentRevision,
        List<Item> revisions) {

    public record Item(
            UUID revisionId,
            int revision,
            RevisionStatus revisionStatus,
            String changeReasonCode,
            String changeReasonDetail,
            String contentSha256,
            String createdBy,
            Instant createdAt,
            Instant submittedAt,
            Instant approvedAt,
            Instant rejectedAt) {

        public static Item of(PurchaseOrderRevision r) {
            return new Item(r.getId(), r.getRevision(), r.getRevisionStatus(), r.getChangeReasonCode(),
                    r.getChangeReasonDetail(), r.getContentSha256(), r.getCreatedBy(), r.getCreatedAt(),
                    r.getSubmittedAt(), r.getApprovedAt(), r.getRejectedAt());
        }
    }

    public static PurchaseOrderRevisionListResponse of(PurchaseOrder po, List<PurchaseOrderRevision> revisions) {
        return new PurchaseOrderRevisionListResponse(po.getId(), po.getPoNumber(), po.getCurrentRevision(),
                revisions.stream().map(Item::of).toList());
    }
}
