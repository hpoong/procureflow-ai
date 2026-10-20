package com.hopoong.procureflow.domain.entity.purchaseorder;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** purchase_order_revision: 발주 개정의 불변 계약 스냅샷과 승인 상태를 보관한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "purchase_order_revision")
public class PurchaseOrderRevision implements Persistable<UUID> {

    /** 정책 평가는 상신(PO-04) 시점에 하므로 초안은 이 값으로 저장한다. */
    public static final String UNEVALUATED_POLICY_VERSION = "UNEVALUATED";

    private static final int INITIAL_REVISION = 1;

    @Id
    private UUID id;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "po_number", nullable = false, length = 64)
    private String poNumber;

    @Column(nullable = false)
    private Integer revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_status", nullable = false, length = 30)
    private RevisionStatus revisionStatus;

    @Column(name = "approval_policy_version", nullable = false, length = 100)
    private String approvalPolicyVersion;

    @Column(name = "change_reason_code", length = 50)
    private String changeReasonCode;

    @Column(name = "change_reason_detail", length = 1000)
    private String changeReasonDetail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot")
    private String beforeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot", nullable = false)
    private String afterSnapshot;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Transient
    private boolean newEntity = true;

    public static PurchaseOrderRevision initialDraft(PurchaseOrder po, String afterSnapshot, String contentSha256,
            String createdBy, Instant now) {
        PurchaseOrderRevision revision = new PurchaseOrderRevision();
        revision.id = UUID.randomUUID();
        revision.poId = po.getId();
        revision.orgId = po.getOrgId();
        revision.poNumber = po.getPoNumber();
        revision.revision = INITIAL_REVISION;
        revision.revisionStatus = RevisionStatus.DRAFT;
        revision.approvalPolicyVersion = UNEVALUATED_POLICY_VERSION;
        revision.afterSnapshot = afterSnapshot;
        revision.contentSha256 = contentSha256;
        revision.createdBy = createdBy;
        revision.createdAt = now;
        return revision;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }
}
