package com.hopoong.procureflow.api.masterdata.model;

import com.hopoong.procureflow.domain.entity.masterdata.MasterStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateItemRequest(
        @NotBlank @Size(max = 64) String itemCode,
        @NotBlank @Size(max = 255) String name,
        @NotNull MasterStatus status,
        @NotNull Boolean purchaseEnabled) {
}
