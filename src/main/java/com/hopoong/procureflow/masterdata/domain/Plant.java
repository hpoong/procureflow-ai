package com.hopoong.procureflow.masterdata.domain;

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
@Table(name = "plant")
public class Plant {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "plant_code", nullable = false, length = 32)
    private String plantCode;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MasterStatus status;

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

    public static Plant create(UUID id, UUID orgId, String plantCode, String name, MasterStatus status,
            UUID actorId, Instant now) {
        Plant entity = new Plant();
        entity.id = id;
        entity.orgId = orgId;
        entity.plantCode = plantCode;
        entity.name = name;
        entity.status = status;
        entity.createdBy = actorId;
        entity.createdAt = now;
        entity.updatedBy = actorId;
        entity.updatedAt = now;
        return entity;
    }

    public boolean isActive() {
        return status == MasterStatus.ACTIVE;
    }
}
