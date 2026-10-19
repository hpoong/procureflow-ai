package com.hopoong.procureflow.api.purchaseorder.service;

import org.springframework.http.HttpStatus;

import com.hopoong.procureflow.common.error.ErrorCode;

public enum PurchaseOrderErrorCode implements ErrorCode {

    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 Idempotency-Key 로 다른 요청이 이미 처리되었습니다."),
    PLANT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "사용할 수 없는 사업장입니다."),
    WAREHOUSE_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "사용할 수 없는 창고입니다."),
    SUPPLIER_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "사용할 수 없는 공급사입니다."),
    ITEM_NOT_PURCHASABLE(HttpStatus.UNPROCESSABLE_CONTENT, "발주할 수 없는 품목이 있습니다.");

    private final HttpStatus status;
    private final String message;

    PurchaseOrderErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }
}
