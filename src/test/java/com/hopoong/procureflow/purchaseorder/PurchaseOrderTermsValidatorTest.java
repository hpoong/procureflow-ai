package com.hopoong.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderErrorCode;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderTermsValidator;
import com.hopoong.procureflow.common.error.BusinessException;

class PurchaseOrderTermsValidatorTest {

    private final PurchaseOrderTermsValidator validator = new PurchaseOrderTermsValidator();

    @ParameterizedTest(name = "scale {0}: {1}")
    @CsvSource({ "0, 1500.000000", "0, 1E+3", "0, 0", "0, 0.000000", "0, 1500", "2, 1500.55", "3, 1.235" })
    void acceptsPriceWithinCurrencyScale(int scale, String price) {
        assertThatCode(() -> validator.validate(scale, List.of(new BigDecimal(price)))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "scale {0}: {1}")
    @CsvSource({ "0, 1500.10", "2, 1500.555" })
    void rejectsPriceOverCurrencyScale(int scale, String price) {
        assertThatThrownBy(() -> validator.validate(scale, List.of(new BigDecimal("1"), new BigDecimal(price))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(PurchaseOrderErrorCode.PRICE_PRECISION_INVALID);
                    assertThat(e.getMessage()).endsWith("lineNo=[2]");
                });
    }

    @Test
    void skipsNullButCountsItsPosition() {
        assertThatThrownBy(() -> validator.validate(0, Arrays.asList(null, new BigDecimal("1500.5"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageEndingWith("lineNo=[2]");
    }
}
