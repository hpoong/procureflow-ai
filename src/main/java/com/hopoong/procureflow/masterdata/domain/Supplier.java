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
@Table(name = "supplier")
public class Supplier {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "supplier_code", nullable = false, length = 64)
    private String supplierCode;

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

    public static Supplier create(UUID id, UUID orgId, String supplierCode, String name, MasterStatus status,
            UUID actorId, Instant now) {
        Supplier entity = new Supplier();
        entity.id = id;
        entity.orgId = orgId;
        entity.supplierCode = supplierCode;
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
