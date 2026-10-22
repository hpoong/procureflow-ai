package com.hopoong.procureflow.api.purchaseorder.service;

import org.springframework.http.HttpStatus;

import com.hopoong.procureflow.common.error.ErrorCode;

public enum PurchaseOrderErrorCode implements ErrorCode {

    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 Idempotency-Key 로 다른 요청이 이미 처리되었습니다."),
    PLANT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "사용할 수 없는 사업장입니다."),
    WAREHOUSE_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "사용할 수 없는 창고입니다."),
    SUPPLIER_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "사용할 수 없는 공급사입니다."),
    ITEM_NOT_PURCHASABLE(HttpStatus.UNPROCESSABLE_CONTENT, "발주할 수 없는 품목이 있습니다."),
    PRICE_PRECISION_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "통화 소수 자릿수를 초과한 단가가 있습니다."),
    PURCHASE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "발주를 찾을 수 없습니다."),
    REVISION_NOT_FOUND(HttpStatus.NOT_FOUND, "발주 개정을 찾을 수 없습니다."),
    PO_VERSION_CONFLICT(HttpStatus.CONFLICT, "발주가 다른 요청으로 변경되었습니다. 최신 version 으로 다시 요청하세요."),
    PO_NOT_REVISABLE(HttpStatus.CONFLICT, "개정할 수 없는 발주 상태입니다."),
    OPEN_REVISION_EXISTS(HttpStatus.CONFLICT, "진행 중인 개정이 이미 있습니다."),
    REVISION_LINE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "개정 라인이 올바르지 않습니다."),
    REVISION_NO_CHANGE(HttpStatus.UNPROCESSABLE_CONTENT, "현재 발주와 달라진 내용이 없습니다.");

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
