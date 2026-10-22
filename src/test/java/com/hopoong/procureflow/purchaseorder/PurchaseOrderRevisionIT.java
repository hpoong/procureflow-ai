package com.hopoong.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderSnapshotWriter;
import com.hopoong.procureflow.domain.entity.masterdata.Item;
import com.hopoong.procureflow.domain.entity.masterdata.MasterStatus;
import com.hopoong.procureflow.domain.entity.masterdata.Warehouse;
import com.hopoong.procureflow.domain.repository.masterdata.ItemRepository;
import com.hopoong.procureflow.domain.repository.masterdata.PlantRepository;
import com.hopoong.procureflow.domain.repository.masterdata.SupplierRepository;
import com.hopoong.procureflow.domain.repository.masterdata.WarehouseRepository;
import com.hopoong.procureflow.support.PostgresContainerSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseOrderRevisionIT extends PostgresContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlantRepository plants;
    @Autowired WarehouseRepository warehouses;
    @Autowired SupplierRepository suppliers;
    @Autowired ItemRepository items;
    @Autowired PurchaseOrderSnapshotWriter snapshotWriter;

    PurchaseOrderFixture fx;

    @BeforeEach
    void setUp() {
        fx = new PurchaseOrderFixture(plants, warehouses, suppliers, items);
    }

    @Test
    void draftsRevisionWithoutChangingCurrentPurchaseOrder() throws Exception {
        UUID poId = approvedPo(fx);
        List<Map<String, Object>> linesBefore = lineRows(poId);
        Map<String, Object> body = baseline(poId, 0);
        lines(body).get(0).put("orderedQty", "12");
        lines(body).add(PurchaseOrderFixture.line(fx.item2Id, "STANDARD", null));
        UUID requestId = UUID.randomUUID();

        MvcResult result = revise(fx, poId, key(), body, requestId.toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poId").value(poId.toString()))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.revisionStatus").value("DRAFT"))
                .andExpect(jsonPath("$.currentRevision").value(1))
                .andExpect(jsonPath("$.poVersion").value(1))
                .andReturn();
        String revisionId = json(result).get("revisionId").asString();

        Map<String, Object> po = jdbc.queryForMap("SELECT * FROM purchase_order WHERE id = ?", poId);
        assertThat(po.get("version")).isEqualTo(1L);
        assertThat(po.get("current_revision")).isEqualTo(1);
        assertThat(po.get("status")).isEqualTo("APPROVED");
        assertThat(lineRows(poId)).isEqualTo(linesBefore);

        Map<String, Object> rev = jdbc.queryForMap(
                "SELECT * FROM purchase_order_revision WHERE po_id = ? AND revision = 2", poId);
        assertThat(rev.get("id").toString()).isEqualTo(revisionId);
        assertThat(rev.get("revision_status")).isEqualTo("DRAFT");
        assertThat(rev.get("approval_policy_version")).isEqualTo("UNEVALUATED");
        assertThat(rev.get("change_reason_code")).isEqualTo("QTY");
        assertThat(rev.get("org_id")).isEqualTo(fx.orgId);
        assertThat(rev.get("po_number")).isEqualTo(po.get("po_number"));
        assertThat(rev.get("created_by")).isEqualTo(fx.userId);
        String before = snapshotText("before_snapshot", poId, 2);
        String after = snapshotText("after_snapshot", poId, 2);
        assertThat(snapshotWriter.canonicalize(before))
                .isEqualTo(snapshotWriter.canonicalize(snapshotText("after_snapshot", poId, 1)));
        assertThat(snapshotWriter.sha256Hex(snapshotWriter.canonicalize(after))).isEqualTo(rev.get("content_sha256"));

        JsonNode afterTree = objectMapper.readTree(after);
        assertThat(afterTree.get("header").get("currentRevision").asString()).isEqualTo("2");
        assertThat(afterTree.get("lines").size()).isEqualTo(3);
        assertThat(afterTree.get("lines").get(0).get("orderedQty").asString()).isEqualTo("12");
        assertThat(afterTree.get("lines").get(2).get("lineNo").asString()).isEqualTo("3");
        afterTree.get("lines").forEach(l -> assertThat(l.get("effectiveRevision").asString()).isEqualTo("2"));

        Map<String, Object> audit = jdbc.queryForMap(
                "SELECT * FROM purchase_order_audit_log WHERE po_id = ? AND action = 'REVISION_CREATE'", poId);
        assertThat(audit.get("revision")).isEqualTo(2);
        assertThat(audit.get("actor_id")).isEqualTo(fx.userId);
        assertThat(audit.get("reason_code")).isEqualTo("QTY");
        assertThat(audit.get("correlation_id")).isEqualTo(requestId);
        assertThat(snapshotWriter.canonicalize(auditText("before_snapshot", poId)))
                .isEqualTo(snapshotWriter.canonicalize(before));
        assertThat(snapshotWriter.canonicalize(auditText("after_snapshot", poId)))
                .isEqualTo(snapshotWriter.canonicalize(after));

        Map<String, Object> command = jdbc.queryForMap(
                "SELECT * FROM purchase_order_command WHERE org_id = ? AND command_type = 'CREATE_PURCHASE_ORDER_REVISION'",
                fx.orgId);
        assertThat(command.get("po_revision_id").toString()).isEqualTo(revisionId);
        assertThat(command.get("po_id")).isEqualTo(poId);
        assertThat(command.get("response_status")).isEqualTo(201);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "DRAFT", "PENDING_APPROVAL", "RECEIVED", "CLOSED", "REJECTED", "CANCELLED" })
    void rejectsNonRevisableStatus(String status) throws Exception {
        UUID poId = approvedPo(fx);
        jdbc.update("UPDATE purchase_order SET status = ? WHERE id = ?", status, poId);
        DbState before = state(poId);

        expectError(revise(fx, poId, key(), changedQty(poId, 0), null), 409, "PO_NOT_REVISABLE");
        assertThat(state(poId)).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "SENT", "PARTIALLY_RECEIVED" })
    void revisesSentAndPartiallyReceived(String status) throws Exception {
        UUID poId = approvedPo(fx);
        jdbc.update("UPDATE purchase_order SET status = ? WHERE id = ?", status, poId);

        revise(fx, poId, key(), changedQty(poId, 0), null).andExpect(status().isCreated());
    }

    @Test
    void rejectsWhileRevisionIsOpen() throws Exception {
        UUID poId = approvedPo(fx);
        revise(fx, poId, key(), changedQty(poId, 0), null).andExpect(status().isCreated());
        DbState before = state(poId);

        expectError(revise(fx, poId, key(), changedQty(poId, 1), null), 409, "OPEN_REVISION_EXISTS");
        assertThat(state(poId)).isEqualTo(before);
    }

    @Test
    void rejectsStaleExpectedVersion() throws Exception {
        UUID poId = approvedPo(fx);
        DbState before = state(poId);

        expectError(revise(fx, poId, key(), changedQty(poId, 5), null), 409, "PO_VERSION_CONFLICT");
        assertThat(state(poId)).isEqualTo(before);
    }

    @Test
    void concurrentRevisionsWithSameVersionCreateOnlyOne() throws Exception {
        UUID poId = approvedPo(fx);
        Map<String, Object> body = changedQty(poId, 0);

        List<MvcResult> results = runConcurrently(2, () -> revise(fx, poId, key(), body, null).andReturn());

        assertThat(results).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(201, 409);
        results.stream().filter(r -> r.getResponse().getStatus() == 409)
                .forEach(r -> assertThat(code(r)).isEqualTo("PO_VERSION_CONFLICT"));
        assertThat(jdbc.queryForList("SELECT revision FROM purchase_order_revision WHERE po_id = ? ORDER BY revision",
                Integer.class, poId)).containsExactly(1, 2);
        assertThat(version(poId)).isEqualTo(1L);
    }

    @Test
    void replaysSameKeyAndRejectsDifferentBody() throws Exception {
        UUID poId = approvedPo(fx);
        String key = key();
        Map<String, Object> body = changedQty(poId, 0);
        List<String> revisionIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            revisionIds.add(json(revise(fx, poId, key, body, null).andExpect(status().isCreated()).andReturn())
                    .get("revisionId").asString());
        }
        assertThat(revisionIds).containsOnly(revisionIds.get(0));
        assertThat(revisionCount(poId)).isEqualTo(2);
        assertThat(revisionCommandCount(fx.orgId)).isEqualTo(1);

        Map<String, Object> changed = changedQty(poId, 0);
        lines(changed).get(0).put("orderedQty", "13");
        expectError(revise(fx, poId, key, changed, null), 409, "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void concurrentSameKeyCreatesOnce() throws Exception {
        UUID poId = approvedPo(fx);
        String key = key();
        Map<String, Object> body = changedQty(poId, 0);

        List<MvcResult> results = runConcurrently(2, () -> revise(fx, poId, key, body, null).andReturn());

        assertThat(results).extracting(r -> r.getResponse().getStatus()).containsOnly(201);
        assertThat(results).extracting(r -> json(r).get("revisionId").asString())
                .containsOnly(json(results.get(0)).get("revisionId").asString());
        assertThat(revisionCount(poId)).isEqualTo(2);
    }

    @Test
    void hidesUnknownAndForeignPurchaseOrders() throws Exception {
        expectError(revise(fx, UUID.randomUUID(), key(), changedQty(approvedPo(fx), 0), null), 404,
                "PURCHASE_ORDER_NOT_FOUND");

        PurchaseOrderFixture other = new PurchaseOrderFixture(plants, warehouses, suppliers, items);
        UUID foreignPoId = approvedPo(other);
        expectError(revise(fx, foreignPoId, key(), changedQty(foreignPoId, 0), null), 404,
                "PURCHASE_ORDER_NOT_FOUND");
        assertThat(revisionCount(foreignPoId)).isEqualTo(1);

        mockMvc.perform(post("/v1/purchase-orders/abc/revisions").contentType("application/json")
                .header("X-Org-Id", fx.orgId).header("X-User-Id", fx.userId).header("Idempotency-Key", key())
                .content(objectMapper.writeValueAsString(changedQty(foreignPoId, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsInvalidLines() throws Exception {
        UUID poId = approvedPo(fx);
        UUID otherPoId = approvedPo(fx);
        String otherLineId = lineRows(otherPoId).get(0).get("id").toString();
        DbState before = state(poId);

        Map<String, Object> foreignLine = baseline(poId, 0);
        lines(foreignLine).get(0).put("lineId", otherLineId);
        expectLineInvalid(poId, foreignLine);

        Map<String, Object> duplicated = baseline(poId, 0);
        lines(duplicated).get(1).put("lineId", lines(duplicated).get(0).get("lineId"));
        expectLineInvalid(poId, duplicated);

        Map<String, Object> missing = baseline(poId, 0);
        lines(missing).remove(1);
        expectLineInvalid(poId, missing);

        Map<String, Object> itemChanged = baseline(poId, 0);
        lines(itemChanged).get(0).put("itemId", fx.item2Id.toString());
        expectLineInvalid(poId, itemChanged);

        Map<String, Object> both = baseline(poId, 0);
        both.put("cancelLineIds", List.of(lines(both).get(0).get("lineId")));
        expectLineInvalid(poId, both);

        assertThat(state(poId)).isEqualTo(before);
    }

    @Test
    void cancelsLineOnlyInProposal() throws Exception {
        UUID poId = approvedPo(fx);
        Map<String, Object> body = baseline(poId, 0);
        Map<String, Object> cancelled = lines(body).remove(0);
        body.put("cancelLineIds", List.of(cancelled.get("lineId")));

        revise(fx, poId, key(), body, null).andExpect(status().isCreated());

        JsonNode first = objectMapper.readTree(snapshotText("after_snapshot", poId, 2)).get("lines").get(0);
        assertThat(first.get("id").asString()).isEqualTo(cancelled.get("lineId"));
        assertThat(first.get("lineStatus").asString()).isEqualTo("CANCELLED");
        assertThat(lineRows(poId).get(0).get("line_status")).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsUnavailableChangedMasters() throws Exception {
        UUID poId = approvedPo(fx);
        Instant now = Instant.now();
        UUID inactiveWarehouse = UUID.randomUUID();
        warehouses.save(Warehouse.create(inactiveWarehouse, fx.orgId, fx.plantId,
                "iw-" + inactiveWarehouse.toString().substring(0, 8), "W", MasterStatus.INACTIVE, fx.userId, now));
        UUID disabledItem = UUID.randomUUID();
        items.save(Item.create(disabledItem, fx.orgId, "di-" + disabledItem.toString().substring(0, 8), "I",
                MasterStatus.ACTIVE, false, fx.userId, now));

        Map<String, Object> warehouseChanged = baseline(poId, 0);
        warehouseChanged.put("shipToWarehouseId", inactiveWarehouse.toString());
        expectError(revise(fx, poId, key(), warehouseChanged, null), 422, "WAREHOUSE_NOT_AVAILABLE");

        Map<String, Object> disabledNewLine = baseline(poId, 0);
        lines(disabledNewLine).add(PurchaseOrderFixture.line(disabledItem, "STANDARD", null));
        expectError(revise(fx, poId, key(), disabledNewLine, null), 422, "ITEM_NOT_PURCHASABLE");
    }

    @Test
    void doesNotRecheckUnchangedSupplier() throws Exception {
        UUID poId = approvedPo(fx);
        jdbc.update("UPDATE supplier SET status = 'INACTIVE' WHERE id = ?", fx.supplierId);

        revise(fx, poId, key(), changedQty(poId, 0), null).andExpect(status().isCreated());
    }

    @Test
    void rejectsProposalEqualToCurrent() throws Exception {
        UUID poId = approvedPo(fx);
        DbState before = state(poId);

        expectError(revise(fx, poId, key(), baseline(poId, 0), null), 422, "REVISION_NO_CHANGE");

        Map<String, Object> sameInOtherForm = baseline(poId, 0);
        lines(sameInOtherForm).get(0).remove("overReceiptPct");
        lines(sameInOtherForm).get(0).put("orderedQty", "10.000");
        expectError(revise(fx, poId, key(), sameInOtherForm, null), 422, "REVISION_NO_CHANGE");
        assertThat(state(poId)).isEqualTo(before);
    }

    @Test
    void listsAndShowsRevisions() throws Exception {
        UUID poId = approvedPo(fx);
        revise(fx, poId, key(), changedQty(poId, 0), null).andExpect(status().isCreated());
        String base = "/v1/purchase-orders/" + poId + "/revisions";

        mockMvc.perform(get(base).header("X-Org-Id", fx.orgId).header("X-User-Id", fx.userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRevision").value(1))
                .andExpect(jsonPath("$.revisions[*].revision").value(Matchers.contains(2, 1)));

        MvcResult detail = mockMvc.perform(get(base + "/2").header("X-Org-Id", fx.orgId).header("X-User-Id", fx.userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.afterSnapshot.header.currentRevision").value("2"))
                .andReturn();
        JsonNode before = json(detail).get("beforeSnapshot");
        assertThat(before.isObject()).isTrue();
        assertThat(snapshotWriter.canonicalize(before.toString()))
                .isEqualTo(snapshotWriter.canonicalize(snapshotText("after_snapshot", poId, 1)));

        mockMvc.perform(get(base + "/x").header("X-Org-Id", fx.orgId).header("X-User-Id", fx.userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(base + "/9").header("X-Org-Id", fx.orgId).header("X-User-Id", fx.userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVISION_NOT_FOUND"));
        mockMvc.perform(get(base).header("X-Org-Id", "org-" + UUID.randomUUID()).header("X-User-Id", fx.userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_NOT_FOUND"));
    }

    @Test
    void numbersRevisionsSequentiallyAfterActivation() throws Exception {
        UUID poId = approvedPo(fx);
        Map<String, Object> body = baseline(poId, 0);
        body.put("incotermsPlace", "Busan");
        revise(fx, poId, key(), body, null).andExpect(status().isCreated());
        // PO-04 활성화 전이라 SQL 로 개정 2 를 현재 유효본으로 만든다.
        jdbc.update("UPDATE purchase_order_revision SET revision_status = 'APPROVED' WHERE po_id = ? AND revision = 2", poId);
        jdbc.update("UPDATE purchase_order SET current_revision = 2, incoterms_place = 'Busan' WHERE id = ?", poId);
        jdbc.update("UPDATE purchase_order_line SET effective_revision = 2 WHERE po_id = ?", poId);

        revise(fx, poId, key(), changedQty(poId, 1), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(3));

        mockMvc.perform(get("/v1/purchase-orders/" + poId + "/revisions").header("X-Org-Id", fx.orgId)
                .header("X-User-Id", fx.userId))
                .andExpect(jsonPath("$.revisions[*].revision").value(Matchers.contains(3, 2, 1)));
        String poNumber = jdbc.queryForObject("SELECT po_number FROM purchase_order WHERE id = ?", String.class, poId);
        assertThat(jdbc.queryForList("SELECT DISTINCT org_id || '/' || po_number FROM purchase_order_revision WHERE po_id = ?",
                String.class, poId)).containsExactly(fx.orgId + "/" + poNumber);
        assertThat(snapshotWriter.canonicalize(snapshotText("before_snapshot", poId, 3)))
                .isEqualTo(snapshotWriter.canonicalize(snapshotText("after_snapshot", poId, 2)));
    }

    @Test
    void checksPricePrecisionOnlyForNewAndChangedPrices() throws Exception {
        UUID poId = approvedPo(fx);
        DbState before = state(poId);

        Map<String, Object> newLine = baseline(poId, 0);
        Map<String, Object> added = PurchaseOrderFixture.line(fx.item2Id, "STANDARD", null);
        added.put("unitPrice", "100.5");
        lines(newLine).add(added);
        expectPrecisionInvalid(poId, newLine, "lineNo=[3]");

        Map<String, Object> changedPrice = baseline(poId, 0);
        lines(changedPrice).get(0).put("unitPrice", "1500.5");
        expectPrecisionInvalid(poId, changedPrice, "lineNo=[1]");

        UUID inactiveWarehouse = UUID.randomUUID();
        warehouses.save(Warehouse.create(inactiveWarehouse, fx.orgId, fx.plantId,
                "iw-" + inactiveWarehouse.toString().substring(0, 8), "W", MasterStatus.INACTIVE, fx.userId,
                Instant.now()));
        newLine.put("shipToWarehouseId", inactiveWarehouse.toString());
        expectPrecisionInvalid(poId, newLine, "lineNo=[3]");
        assertThat(state(poId)).isEqualTo(before);

        Map<String, Object> sameValue = changedQty(poId, 0);
        lines(sameValue).get(0).put("unitPrice", "1500.0");
        revise(fx, poId, key(), sameValue, null).andExpect(status().isCreated());
    }

    @Test
    void keepsLegacyPriceWhenUnchanged() throws Exception {
        UUID poId = approvedPo(fx);
        jdbc.update("UPDATE purchase_order_line SET unit_price = 1500.5 WHERE po_id = ? AND line_no = 1", poId);

        revise(fx, poId, key(), changedQty(poId, 0), null).andExpect(status().isCreated());
    }

    private record DbState(int revisions, int audits, int commands, long version) {
    }

    private DbState state(UUID poId) {
        return new DbState(revisionCount(poId),
                jdbc.queryForObject("SELECT count(*) FROM purchase_order_audit_log WHERE po_id = ?", Integer.class, poId),
                revisionCommandCount(fx.orgId), version(poId));
    }

    private int revisionCount(UUID poId) {
        return jdbc.queryForObject("SELECT count(*) FROM purchase_order_revision WHERE po_id = ?", Integer.class, poId);
    }

    private int revisionCommandCount(String orgId) {
        return jdbc.queryForObject("SELECT count(*) FROM purchase_order_command WHERE org_id = ? "
                + "AND command_type = 'CREATE_PURCHASE_ORDER_REVISION'", Integer.class, orgId);
    }

    private long version(UUID poId) {
        return jdbc.queryForObject("SELECT version FROM purchase_order WHERE id = ?", Long.class, poId);
    }

    private List<Map<String, Object>> lineRows(UUID poId) {
        return jdbc.queryForList("SELECT * FROM purchase_order_line WHERE po_id = ? ORDER BY line_no", poId);
    }

    private String snapshotText(String column, UUID poId, int revision) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM purchase_order_revision WHERE po_id = ? AND revision = ?",
                String.class, poId, revision);
    }

    private String auditText(String column, UUID poId) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM purchase_order_audit_log "
                + "WHERE po_id = ? AND action = 'REVISION_CREATE'", String.class, poId);
    }

    /** 초안 생성 API 로 만든 뒤 승인 API 가 없어(PO-04) SQL 로 APPROVED 를 만든다. version 은 0 그대로다. */
    private UUID approvedPo(PurchaseOrderFixture f) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/purchase-orders").contentType("application/json")
                .header("X-Org-Id", f.orgId).header("X-User-Id", f.userId).header("Idempotency-Key", key())
                .content(objectMapper.writeValueAsString(f.validRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID poId = UUID.fromString(json(result).get("id").asString());
        jdbc.update("UPDATE purchase_order SET status = 'APPROVED' WHERE id = ?", poId);
        jdbc.update("UPDATE purchase_order_revision SET revision_status = 'APPROVED' WHERE po_id = ? AND revision = 1", poId);
        return poId;
    }

    /** 현재 유효본과 같은 값의 개정 요청. */
    private Map<String, Object> baseline(UUID poId, long expectedVersion) {
        Map<String, Object> po = jdbc.queryForMap("SELECT * FROM purchase_order WHERE id = ?", poId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedVersion", expectedVersion);
        body.put("changeReasonCode", "QTY");
        body.put("shipToWarehouseId", po.get("ship_to_warehouse_id").toString());
        putIfPresent(body, "exchangeRate", po.get("exchange_rate"));
        putIfPresent(body, "paymentTermsCode", po.get("payment_terms_code"));
        putIfPresent(body, "incotermsCode", po.get("incoterms_code"));
        putIfPresent(body, "incotermsPlace", po.get("incoterms_place"));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT * FROM purchase_order_line WHERE po_id = ? AND line_status = 'ACTIVE' ORDER BY line_no", poId)) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("lineId", row.get("id").toString());
            line.put("itemId", row.get("item_id").toString());
            line.put("orderedQty", plain(row.get("ordered_qty")));
            line.put("purchaseUom", row.get("purchase_uom"));
            line.put("inventoryUom", row.get("inventory_uom"));
            line.put("conversionNumerator", row.get("conversion_numerator"));
            line.put("conversionDenominator", row.get("conversion_denominator"));
            line.put("unitPrice", plain(row.get("unit_price")));
            line.put("taxCode", row.get("tax_code"));
            line.put("taxRate", plain(row.get("tax_rate")));
            putIfPresent(line, "requestedDate", row.get("requested_date"));
            putIfPresent(line, "promisedDate", row.get("promised_date"));
            line.put("receiptRouting", row.get("receipt_routing"));
            putIfPresent(line, "inspectionSpecRevisionId", row.get("inspection_spec_revision_id"));
            line.put("overReceiptPct", plain(row.get("over_receipt_pct")));
            lines.add(line);
        }
        body.put("lines", lines);
        return body;
    }

    private Map<String, Object> changedQty(UUID poId, long expectedVersion) {
        Map<String, Object> body = baseline(poId, expectedVersion);
        lines(body).get(0).put("orderedQty", "12");
        return body;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value instanceof BigDecimal decimal ? plain(decimal) : value.toString());
        }
    }

    private static String plain(Object decimal) {
        return ((BigDecimal) decimal).toPlainString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lines(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("lines");
    }

    private void expectLineInvalid(UUID poId, Map<String, Object> body) throws Exception {
        expectError(revise(fx, poId, key(), body, null), 422, "REVISION_LINE_INVALID");
    }

    private void expectPrecisionInvalid(UUID poId, Map<String, Object> body, String lineNos) throws Exception {
        expectError(revise(fx, poId, key(), body, null), 422, "PRICE_PRECISION_INVALID")
                .andExpect(jsonPath("$.message").value(Matchers.endsWith(lineNos)));
    }

    private ResultActions expectError(ResultActions actions, int httpStatus, String code) throws Exception {
        return actions.andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code));
    }

    private List<MvcResult> runConcurrently(int n, Callable<MvcResult> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return call.call();
            }));
        }
        ready.await();
        go.countDown();
        List<MvcResult> results = new ArrayList<>();
        for (Future<MvcResult> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    private JsonNode json(MvcResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String code(MvcResult result) {
        return json(result).get("code").asString();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private ResultActions revise(PurchaseOrderFixture f, UUID poId, String key, Map<String, Object> body,
            String requestId) throws Exception {
        var builder = post("/v1/purchase-orders/" + poId + "/revisions")
                .contentType("application/json")
                .header("X-Org-Id", f.orgId)
                .header("X-User-Id", f.userId)
                .header("Idempotency-Key", key)
                .content(objectMapper.writeValueAsString(body));
        if (requestId != null) {
            builder.header("X-Request-Id", requestId);
        }
        return mockMvc.perform(builder);
    }
}
