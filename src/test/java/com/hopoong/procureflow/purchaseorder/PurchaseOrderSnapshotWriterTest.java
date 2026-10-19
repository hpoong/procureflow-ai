package com.hopoong.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hopoong.procureflow.domain.entity.purchaseorder.ReceiptRouting;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderLineRequest;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRequest;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderSnapshotWriter;

import tools.jackson.databind.json.JsonMapper;

class PurchaseOrderSnapshotWriterTest {

    private final PurchaseOrderSnapshotWriter writer = new PurchaseOrderSnapshotWriter(JsonMapper.builder().build());
    private final UUID userId = UUID.randomUUID();
    private final UUID plantId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID warehouseId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @Test
    void sameHashForEquivalentNumbersAndKeyOrder() {
        Map<String, Object> terms1 = new LinkedHashMap<>();
        terms1.put("a", 1);
        terms1.put("b", "x");
        Map<String, Object> terms2 = new LinkedHashMap<>();
        terms2.put("b", "x");
        terms2.put("a", 1);

        String h1 = writer.requestHash(userId, request(new BigDecimal("10"), terms1));
        String h2 = writer.requestHash(userId, request(new BigDecimal("10.0"), terms2));
        String h3 = writer.requestHash(userId, request(new BigDecimal("1E+1"), terms1));

        assertThat(h1).isEqualTo(h2).isEqualTo(h3).matches("^[0-9a-f]{64}$");
    }

    @Test
    void differentUserGivesDifferentHash() {
        CreatePurchaseOrderRequest request = request(new BigDecimal("10"), null);
        assertThat(writer.requestHash(userId, request)).isNotEqualTo(writer.requestHash(UUID.randomUUID(), request));
    }

    @Test
    void canonicalizeIsStableAcrossKeyOrderAndNumberForms() {
        assertThat(writer.canonicalize("{\"b\":\"1\",\"a\":{\"d\":null,\"c\":[2.50,true]}}"))
                .isEqualTo("{\"a\":{\"c\":[\"2.5\",true]},\"b\":\"1\"}");
    }

    private CreatePurchaseOrderRequest request(BigDecimal qty, Map<String, Object> terms) {
        CreatePurchaseOrderLineRequest line = new CreatePurchaseOrderLineRequest(itemId, qty, "EA", "EA", 1L, 1L,
                new BigDecimal("100"), "VAT10", new BigDecimal("0.1"), null, null, ReceiptRouting.STANDARD, null, null);
        return new CreatePurchaseOrderRequest(plantId, supplierId, warehouseId, "KRW", null, null, null, null, terms,
                List.of(line));
    }
}
