package com.hopoong.procureflow.api.masterdata.service;

import org.springframework.http.HttpStatus;

import com.hopoong.procureflow.common.error.ErrorCode;

public enum MasterDataErrorCode implements ErrorCode {

    PLANT_CODE_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 사업장 코드입니다."),
    WAREHOUSE_CODE_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 창고 코드입니다."),
    SUPPLIER_CODE_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 공급사 코드입니다."),
    ITEM_CODE_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 품목 코드입니다."),
    PLANT_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "존재하지 않는 사업장입니다.");

    private final HttpStatus status;
    private final String message;

    MasterDataErrorCode(HttpStatus status, String message) {
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
