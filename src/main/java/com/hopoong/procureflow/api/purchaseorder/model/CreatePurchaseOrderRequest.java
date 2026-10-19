package com.hopoong.procureflow.api.purchaseorder.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePurchaseOrderRequest(
        @NotNull UUID plantId,
        @NotNull UUID supplierId,
        @NotNull UUID shipToWarehouseId,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Positive @Digits(integer = 10, fraction = 10) BigDecimal exchangeRate,
        @Size(max = 50) String paymentTermsCode,
        @Size(max = 10) String incotermsCode,
        @Size(max = 255) String incotermsPlace,
        Map<String, Object> paymentTermsSnapshot,
        @NotEmpty @Valid List<CreatePurchaseOrderLineRequest> lines) {

    private static final int MAX_CURRENCY_SCALE = 6;

    @AssertTrue(message = "지원하지 않는 통화입니다")
    public boolean isCurrencySupported() {
        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            return true;
        }
        try {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return digits >= 0 && digits <= MAX_CURRENCY_SCALE;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
