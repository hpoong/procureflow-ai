package com.hopoong.procureflow.purchaseorder;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import com.hopoong.procureflow.api.purchaseorder.controller.PurchaseOrderRevisionController;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderRevisionFacade;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderRevisionQueryService;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(PurchaseOrderRevisionController.class)
class PurchaseOrderRevisionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean PurchaseOrderRevisionFacade facade;
    @MockitoBean PurchaseOrderRevisionQueryService queryService;

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                mutate("expectedVersion missing", b -> b.remove("expectedVersion")),
                mutate("expectedVersion -1", b -> b.put("expectedVersion", -1)),
                mutate("changeReasonCode blank", b -> b.put("changeReasonCode", " ")),
                mutate("changeReasonCode 51", b -> b.put("changeReasonCode", "C".repeat(51))),
                mutate("changeReasonDetail 1001", b -> b.put("changeReasonDetail", "D".repeat(1001))),
                mutate("shipToWarehouseId missing", b -> b.remove("shipToWarehouseId")),
                mutate("empty lines", b -> b.put("lines", List.of())),
                mutate("qty 0", b -> line(b).put("orderedQty", "0")),
                mutate("INSPECTION without spec", b -> line(b).put("receiptRouting", "INSPECTION")),
                mutate("lineId not uuid", b -> line(b).put("lineId", "abc")),
                mutate("cancelLineIds null element", b -> b.put("cancelLineIds", java.util.Arrays.asList((Object) null))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void rejectsInvalidBody(String name, Map<String, Object> body) throws Exception {
        mockMvc.perform(post(url()).contentType("application/json")
                .header("X-Org-Id", "org-" + UUID.randomUUID())
                .header("X-User-Id", "user-" + UUID.randomUUID())
                .header("Idempotency-Key", "k")
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(facade);
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post(url()).contentType("application/json")
                .header("X-Org-Id", "org-" + UUID.randomUUID())
                .header("X-User-Id", "user-" + UUID.randomUUID())
                .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(facade);
    }

    @Test
    void rejectsNonUuidPoId() throws Exception {
        mockMvc.perform(post("/v1/purchase-orders/abc/revisions").contentType("application/json")
                .header("X-Org-Id", "org-" + UUID.randomUUID())
                .header("X-User-Id", "user-" + UUID.randomUUID())
                .header("Idempotency-Key", "k")
                .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(facade);
    }

    private static String url() {
        return "/v1/purchase-orders/" + UUID.randomUUID() + "/revisions";
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
        Map<String, Object> line = new LinkedHashMap<>(PurchaseOrderFixture.line(UUID.randomUUID(), "STANDARD", null));
        line.put("lineId", UUID.randomUUID().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedVersion", 0);
        body.put("changeReasonCode", "QTY");
        body.put("shipToWarehouseId", UUID.randomUUID().toString());
        body.put("lines", new ArrayList<>(List.of(line)));
        return body;
    }
}
