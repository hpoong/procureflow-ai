package com.hopoong.procureflow.api.purchaseorder.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.hopoong.procureflow.common.error.BusinessException;

@Component
public class PurchaseOrderTermsValidator {

    /**
     * 단가의 유효 소수 자릿수가 통화 소수 자릿수를 넘으면 거절한다.
     * null 항목은 검사하지 않고 순번만 센다(개정에서 단가를 바꾸지 않은 라인).
     */
    public void validate(int currencyScale, List<BigDecimal> unitPrices) {
        List<Integer> invalidLineNos = new ArrayList<>();
        for (int i = 0; i < unitPrices.size(); i++) {
            BigDecimal unitPrice = unitPrices.get(i);
            if (unitPrice != null && unitPrice.stripTrailingZeros().scale() > currencyScale) {
                invalidLineNos.add(i + 1);
            }
        }
        if (!invalidLineNos.isEmpty()) {
            String lineNos = invalidLineNos.stream().map(String::valueOf).collect(Collectors.joining(", "));
            throw new BusinessException(PurchaseOrderErrorCode.PRICE_PRECISION_INVALID,
                    PurchaseOrderErrorCode.PRICE_PRECISION_INVALID.message() + " lineNo=[" + lineNos + "]");
        }
    }
}
