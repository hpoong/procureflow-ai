package com.hopoong.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.hopoong.procureflow.api.purchaseorder.controller.PurchaseOrderController;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderCreateFacade;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(PurchaseOrderController.class)
class PurchaseOrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean PurchaseOrderCreateFacade facade;

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                mutate("empty lines", b -> b.put("lines", List.of())),
                mutate("qty 0", b -> line(b).put("orderedQty", "0")),
                mutate("qty 7 decimals", b -> line(b).put("orderedQty", "1.1234567")),
                mutate("unitPrice -1", b -> line(b).put("unitPrice", "-1")),
                mutate("taxRate 1.5", b -> line(b).put("taxRate", "1.5")),
                mutate("overReceiptPct 100.01", b -> line(b).put("overReceiptPct", "100.01")),
                mutate("denominator 0", b -> line(b).put("conversionDenominator", 0)),
                mutate("routing X", b -> line(b).put("receiptRouting", "X")),
                mutate("INSPECTION without spec", b -> line(b).put("receiptRouting", "INSPECTION")),
                mutate("STANDARD with spec", b -> line(b).put("inspectionSpecRevisionId", UUID.randomUUID().toString())),
                mutate("currency krw", b -> b.put("currency", "krw")),
                mutate("currency ZZZ", b -> b.put("currency", "ZZZ")),
                mutate("currency XXX", b -> b.put("currency", "XXX")),
                mutate("exchangeRate 0", b -> b.put("exchangeRate", "0")),
                mutate("paymentTermsSnapshot array", b -> b.put("paymentTermsSnapshot", List.of(1, 2))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void rejectsInvalidBody(String name, Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/v1/purchase-orders").contentType("application/json")
                .header("X-Org-Id", UUID.randomUUID().toString())
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("Idempotency-Key", "k")
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(facade);
    }

    @Test
    void rejectsMissingAndTooLongIdempotencyKey() throws Exception {
        String body = objectMapper.writeValueAsString(validBody());
        mockMvc.perform(post("/v1/purchase-orders").contentType("application/json")
                .header("X-Org-Id", UUID.randomUUID().toString())
                .header("X-User-Id", UUID.randomUUID().toString())
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/v1/purchase-orders").contentType("application/json")
                .header("X-Org-Id", UUID.randomUUID().toString())
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("Idempotency-Key", "k".repeat(256))
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(facade);
    }

    @Test
    void brokenJsonDoesNotLeakInternals() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/purchase-orders").contentType("application/json")
                .header("X-Org-Id", UUID.randomUUID().toString())
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("Idempotency-Key", "k")
                .content("{\"plantId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andReturn();
        String message = result.getResponse().getContentAsString();
        assertThat(message).doesNotContain("tools.jackson").doesNotContain("com.hopoong");
        verifyNoInteractions(facade);
    }

    @Test
    void keepsClientErrorStatusForSpringMvcExceptions() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/purchase-orders"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    private static Arguments mutate(String name, Consumer<Map<String, Object>> mutation) {
        Map<String, Object> body = validBody();
        mutation.accept(body);
        return Arguments.of(name, body);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> line(Map<String, Object> body) {
        return ((List<Map<String, Object>>) body.get("lines")).get(0);
    }

    private static Map<String, Object> validBody() {
        Map<String, Object> line = new java.util.LinkedHashMap<>(PurchaseOrderFixture.line(UUID.randomUUID(), "STANDARD", null));
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("plantId", UUID.randomUUID().toString());
        body.put("supplierId", UUID.randomUUID().toString());
        body.put("shipToWarehouseId", UUID.randomUUID().toString());
        body.put("currency", "KRW");
        body.put("lines", new java.util.ArrayList<>(List.of(line)));
        return body;
    }
}
