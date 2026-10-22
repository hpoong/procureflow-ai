package com.hopoong.procureflow.api.masterdata.model;

import java.time.Instant;
import java.util.UUID;

import com.hopoong.procureflow.domain.entity.masterdata.Item;
import com.hopoong.procureflow.domain.entity.masterdata.MasterStatus;

public record ItemResponse(
        UUID id,
        String itemCode,
        String name,
        MasterStatus status,
        boolean purchaseEnabled,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        Long version) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getItemCode(), item.getName(), item.getStatus(),
                item.isPurchaseEnabled(), item.getCreatedBy(), item.getCreatedAt(), item.getUpdatedBy(),
                item.getUpdatedAt(), item.getVersion());
    }
}
