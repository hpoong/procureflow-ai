package com.hopoong.procureflow.api.purchaseorder.model;

import java.time.Instant;
import java.util.UUID;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderRevision;
import com.hopoong.procureflow.domain.entity.purchaseorder.RevisionStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

public record PurchaseOrderRevisionDetailResponse(
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
        Instant rejectedAt,
        @Schema(description = "작성 시점 현재 유효 개정의 afterSnapshot. 개정 1 은 null")
        JsonNode beforeSnapshot,
        @Schema(description = "header.status·header.currentRevision·lines[].effectiveRevision 은 스냅샷 메타로, 업무 변경이 아니다")
        JsonNode afterSnapshot) {

    public static PurchaseOrderRevisionDetailResponse of(PurchaseOrderRevision r, JsonNode beforeSnapshot,
            JsonNode afterSnapshot) {
        return new PurchaseOrderRevisionDetailResponse(r.getId(), r.getRevision(), r.getRevisionStatus(),
                r.getChangeReasonCode(), r.getChangeReasonDetail(), r.getContentSha256(), r.getCreatedBy(),
                r.getCreatedAt(), r.getSubmittedAt(), r.getApprovedAt(), r.getRejectedAt(), beforeSnapshot,
                afterSnapshot);
    }
}
