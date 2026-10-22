package com.hopoong.procureflow.api.masterdata.model;

import java.util.UUID;

import com.hopoong.procureflow.domain.entity.masterdata.MasterStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        @NotNull UUID plantId,
        @NotBlank @Size(max = 32) String warehouseCode,
        @NotBlank @Size(max = 255) String name,
        @NotNull MasterStatus status) {
}
