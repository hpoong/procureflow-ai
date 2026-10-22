package com.hopoong.procureflow.api.purchaseorder.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 헤더 조건은 전체 교체다. 생략·null 은 "값 없음"으로 저장된다. */
public record CreatePurchaseOrderRevisionRequest(
        @Schema(description = "개정 기준으로 읽은 발주 version. 현재 version 과 다르면 409")
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 50) String changeReasonCode,
        @Size(max = 1000) String changeReasonDetail,
        @NotNull UUID shipToWarehouseId,
        @Positive @Digits(integer = 10, fraction = 10) BigDecimal exchangeRate,
        @Size(max = 50) String paymentTermsCode,
        @Size(max = 10) String incotermsCode,
        @Size(max = 255) String incotermsPlace,
        Map<String, Object> paymentTermsSnapshot,
        @Schema(description = "개정 후 진행 중 라인 전체. 기존 진행 중 라인은 lines 또는 cancelLineIds 중 한 곳에 정확히 한 번")
        @NotEmpty @Valid List<RevisePurchaseOrderLineRequest> lines,
        @Schema(description = "취소할 기존 진행 중 라인 ID")
        List<@NotNull UUID> cancelLineIds) {

    public CreatePurchaseOrderRevisionRequest {
        cancelLineIds = cancelLineIds == null ? List.of() : cancelLineIds;
    }
}
