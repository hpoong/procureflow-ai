package com.hopoong.procureflow.domain.entity.purchaseorder;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** purchase_order_audit_log: 발주 변경과 상태 전이의 행위자·사유·전후 스냅샷을 append-only 로 보관한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "purchase_order_audit_log")
public class PurchaseOrderAuditLog implements Persistable<UUID> {

    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_REVISION_CREATE = "REVISION_CREATE";

    @Id
    private UUID id;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    private Integer revision;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot")
    private String beforeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot")
    private String afterSnapshot;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Transient
    private boolean newEntity = true;

    public static PurchaseOrderAuditLog created(UUID poId, int revision, String actorId, String afterSnapshot,
            UUID correlationId, Instant now) {
        PurchaseOrderAuditLog log = new PurchaseOrderAuditLog();
        log.id = UUID.randomUUID();
        log.poId = poId;
        log.revision = revision;
        log.action = ACTION_CREATE;
        log.actorId = actorId;
        log.afterSnapshot = afterSnapshot;
        log.occurredAt = now;
        log.correlationId = correlationId;
        return log;
    }

    public static PurchaseOrderAuditLog revisionCreated(UUID poId, int revision, String actorId, String reasonCode,
            String beforeSnapshot, String afterSnapshot, UUID correlationId, Instant now) {
        PurchaseOrderAuditLog log = new PurchaseOrderAuditLog();
        log.id = UUID.randomUUID();
        log.poId = poId;
        log.revision = revision;
        log.action = ACTION_REVISION_CREATE;
        log.actorId = actorId;
        log.reasonCode = reasonCode;
        log.beforeSnapshot = beforeSnapshot;
        log.afterSnapshot = afterSnapshot;
        log.occurredAt = now;
        log.correlationId = correlationId;
        return log;
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
