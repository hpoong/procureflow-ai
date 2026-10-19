package com.hopoong.procureflow.domain.entity.masterdata;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "item")
public class Item {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MasterStatus status;

    @Column(name = "purchase_enabled", nullable = false)
    private boolean purchaseEnabled;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static Item create(UUID id, UUID orgId, String itemCode, String name, MasterStatus status, boolean purchaseEnabled,
            UUID actorId, Instant now) {
        Item entity = new Item();
        entity.id = id;
        entity.orgId = orgId;
        entity.itemCode = itemCode;
        entity.name = name;
        entity.status = status;
        entity.purchaseEnabled = purchaseEnabled;
        entity.createdBy = actorId;
        entity.createdAt = now;
        entity.updatedBy = actorId;
        entity.updatedAt = now;
        return entity;
    }

    public boolean isActive() {
        return status == MasterStatus.ACTIVE;
    }

    public boolean isPurchasable() {
        return isActive() && purchaseEnabled;
    }
}
