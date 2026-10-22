package com.hopoong.procureflow.api.masterdata.model;

import java.time.Instant;
import java.util.UUID;

import com.hopoong.procureflow.domain.entity.masterdata.MasterStatus;
import com.hopoong.procureflow.domain.entity.masterdata.Plant;

public record PlantResponse(
        UUID id,
        String plantCode,
        String name,
        MasterStatus status,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        Long version) {

    public static PlantResponse from(Plant plant) {
        return new PlantResponse(plant.getId(), plant.getPlantCode(), plant.getName(), plant.getStatus(),
                plant.getCreatedBy(), plant.getCreatedAt(), plant.getUpdatedBy(), plant.getUpdatedAt(),
                plant.getVersion());
    }
}
