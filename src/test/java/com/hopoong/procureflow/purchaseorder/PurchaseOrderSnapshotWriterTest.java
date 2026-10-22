package com.hopoong.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hopoong.procureflow.domain.entity.purchaseorder.LineStatus;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrder;
import com.hopoong.procureflow.domain.entity.purchaseorder.ReceiptRouting;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderLineRequest;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRequest;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderSnapshotWriter;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderSnapshotWriter.HeaderTerms;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderSnapshotWriter.SnapshotLine;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PurchaseOrderSnapshotWriterTest {

    private final PurchaseOrderSnapshotWriter writer = new PurchaseOrderSnapshotWriter(JsonMapper.builder().build());
    private final String userId = "user-" + UUID.randomUUID();
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
        assertThat(writer.requestHash(userId, request)).isNotEqualTo(writer.requestHash("user-" + UUID.randomUUID(), request));
    }

    @Test
    void canonicalizeIsStableAcrossKeyOrderAndNumberForms() {
        assertThat(writer.canonicalize("{\"b\":\"1\",\"a\":{\"d\":null,\"c\":[2.50,true]}}"))
                .isEqualTo("{\"a\":{\"c\":[\"2.5\",true]},\"b\":\"1\"}");
    }

    @Test
    void revisionSnapshotFollowsCanonicalRules() {
        PurchaseOrder po = PurchaseOrder.draft(UUID.randomUUID(), "org-" + UUID.randomUUID(), plantId,
                "PO-20261022-00000001", supplierId, "KRW", 0, null, null, null, null, null, warehouseId, userId,
                Instant.parse("2026-10-22T00:00:00Z"));
        HeaderTerms terms = new HeaderTerms(warehouseId, null, "NET30", "{\"days\": 30.0}", null, null);
        SnapshotLine second = line(2, LineStatus.ACTIVE, null);
        SnapshotLine first = line(1, LineStatus.CANCELLED, new BigDecimal("5.00"));

        String snapshot = writer.revisionSnapshot(po, terms, List.of(second, first), 3);

        JsonNode tree = JsonMapper.builder().build().readTree(snapshot);
        assertThat(tree.get("header").get("currentRevision").asString()).isEqualTo("3");
        assertThat(tree.get("header").get("paymentTermsSnapshot").get("days").asString()).isEqualTo("30");
        JsonNode lines = tree.get("lines");
        assertThat(lines.get(0).get("lineNo").asString()).isEqualTo("1");
        assertThat(lines.get(0).get("lineStatus").asString()).isEqualTo("CANCELLED");
        assertThat(lines.get(0).get("overReceiptPct").asString()).isEqualTo("5");
        assertThat(lines.get(1).get("lineNo").asString()).isEqualTo("2");
        assertThat(lines.get(1).get("overReceiptPct").asString()).isEqualTo("0");
        lines.forEach(l -> assertThat(l.get("effectiveRevision").asString()).isEqualTo("3"));
        assertThat(writer.canonicalize(snapshot)).isEqualTo(snapshot);
    }

    private SnapshotLine line(int lineNo, LineStatus status, BigDecimal overReceiptPct) {
        return new SnapshotLine(UUID.randomUUID(), lineNo, itemId, status, new BigDecimal("10.000"), BigDecimal.ZERO,
                "EA", "EA", 1L, 1L, new BigDecimal("1500"), "VAT10", new BigDecimal("0.1"), null, null,
                ReceiptRouting.STANDARD, null, overReceiptPct, 1);
    }

    private CreatePurchaseOrderRequest request(BigDecimal qty, Map<String, Object> terms) {
        CreatePurchaseOrderLineRequest line = new CreatePurchaseOrderLineRequest(itemId, qty, "EA", "EA", 1L, 1L,
                new BigDecimal("100"), "VAT10", new BigDecimal("0.1"), null, null, ReceiptRouting.STANDARD, null, null);
        return new CreatePurchaseOrderRequest(plantId, supplierId, warehouseId, "KRW", null, null, null, null, terms,
                List.of(line));
    }
}
