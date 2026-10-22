package com.hopoong.procureflow.api.masterdata.model;

import java.time.Instant;
import java.util.UUID;

import com.hopoong.procureflow.domain.entity.masterdata.MasterStatus;
import com.hopoong.procureflow.domain.entity.masterdata.Warehouse;

public record WarehouseResponse(
        UUID id,
        UUID plantId,
        String warehouseCode,
        String name,
        MasterStatus status,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        Long version) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getPlantId(), warehouse.getWarehouseCode(),
                warehouse.getName(), warehouse.getStatus(), warehouse.getCreatedBy(), warehouse.getCreatedAt(),
                warehouse.getUpdatedBy(), warehouse.getUpdatedAt(), warehouse.getVersion());
    }
}
