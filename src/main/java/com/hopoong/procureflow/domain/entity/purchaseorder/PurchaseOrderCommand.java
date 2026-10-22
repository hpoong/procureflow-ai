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

/** purchase_order_command: 변경 명령의 Idempotency-Key 와 요청 해시, 재시도 결과를 보관한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "purchase_order_command")
public class PurchaseOrderCommand implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 50)
    private PurchaseOrderCommandType commandType;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_sha256", nullable = false, length = 64)
    private String requestSha256;

    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "po_revision_id")
    private UUID poRevisionId;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Transient
    private boolean newEntity = true;

    public static PurchaseOrderCommand start(String orgId, PurchaseOrderCommandType commandType, String idempotencyKey,
            String requestSha256, Instant now) {
        PurchaseOrderCommand command = new PurchaseOrderCommand();
        command.id = UUID.randomUUID();
        command.orgId = orgId;
        command.commandType = commandType;
        command.idempotencyKey = idempotencyKey;
        command.requestSha256 = requestSha256;
        command.createdAt = now;
        return command;
    }

    public void complete(UUID poId, int responseStatus, Instant now) {
        this.poId = poId;
        this.responseStatus = responseStatus;
        this.completedAt = now;
    }

    public void complete(UUID poId, UUID poRevisionId, int responseStatus, Instant now) {
        this.poRevisionId = poRevisionId;
        complete(poId, responseStatus, now);
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
