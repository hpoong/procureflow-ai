package com.hopoong.procureflow.api.masterdata.model;

import java.time.Instant;
import java.util.UUID;

import com.hopoong.procureflow.domain.entity.masterdata.MasterStatus;
import com.hopoong.procureflow.domain.entity.masterdata.Supplier;

public record SupplierResponse(
        UUID id,
        String supplierCode,
        String name,
        MasterStatus status,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        Long version) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getSupplierCode(), supplier.getName(),
                supplier.getStatus(), supplier.getCreatedBy(), supplier.getCreatedAt(), supplier.getUpdatedBy(),
                supplier.getUpdatedAt(), supplier.getVersion());
    }
}
