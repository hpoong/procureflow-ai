package com.hopoong.procureflow.api.purchaseorder.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.hopoong.procureflow.domain.entity.purchaseorder.ReceiptRouting;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePurchaseOrderLineRequest(
        @NotNull UUID itemId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal orderedQty,
        @NotBlank @Size(max = 20) String purchaseUom,
        @NotBlank @Size(max = 20) String inventoryUom,
        @NotNull @Positive Long conversionNumerator,
        @NotNull @Positive Long conversionDenominator,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal unitPrice,
        @NotBlank @Size(max = 50) String taxCode,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 3, fraction = 6) BigDecimal taxRate,
        LocalDate requestedDate,
        LocalDate promisedDate,
        @NotNull ReceiptRouting receiptRouting,
        UUID inspectionSpecRevisionId,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal overReceiptPct) {

    @AssertTrue(message = "INSPECTION 이면 inspectionSpecRevisionId 가 필요하고, 그 외에는 없어야 합니다")
    public boolean isInspectionSpecConsistent() {
        if (receiptRouting == null) {
            return true;
        }
        boolean inspection = receiptRouting == ReceiptRouting.INSPECTION;
        return inspection == (inspectionSpecRevisionId != null);
    }
}
