package com.hopoong.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.hopoong.procureflow.domain.entity.masterdata.Item;
import com.hopoong.procureflow.domain.entity.masterdata.MasterStatus;
import com.hopoong.procureflow.domain.entity.masterdata.Plant;
import com.hopoong.procureflow.domain.entity.masterdata.Supplier;
import com.hopoong.procureflow.domain.entity.masterdata.Warehouse;
import com.hopoong.procureflow.domain.repository.masterdata.ItemRepository;
import com.hopoong.procureflow.domain.repository.masterdata.PlantRepository;
import com.hopoong.procureflow.domain.repository.masterdata.SupplierRepository;
import com.hopoong.procureflow.domain.repository.masterdata.WarehouseRepository;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderSnapshotWriter;
import com.hopoong.procureflow.support.PostgresContainerSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseOrderCreateIT extends PostgresContainerSupport {

    private static final String URL = "/v1/purchase-orders";

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
    void createsDraftWithLines() throws Exception {
        UUID requestId = UUID.randomUUID();
        MvcResult result = send(fx.orgId, fx.userId, key(), fx.validRequest(), requestId.toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currentRevision").value(1))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.lines[1].lineNo").value(2))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("poNumber").asString()).matches("^PO-\\d{8}-\\d{8,}$");
        UUID poId = UUID.fromString(body.get("id").asString());

        Map<String, Object> po = jdbc.queryForMap("SELECT * FROM purchase_order WHERE id = ?", poId);
        assertThat(po.get("status")).isEqualTo("DRAFT");
        assertThat(po.get("current_revision")).isEqualTo(1);
        assertThat(po.get("org_id")).isEqualTo(fx.orgId);
        assertThat(po.get("created_by")).isEqualTo(fx.userId);
        assertThat(po.get("ordered_at")).isNull();
        assertThat(po.get("approved_at")).isNull();
        assertThat(po.get("sent_at")).isNull();
        assertThat(((Number) po.get("currency_scale")).intValue()).isZero();
        assertThat(po.get("currency")).isEqualTo("KRW");

        Map<String, Object> rev = jdbc.queryForMap("SELECT * FROM purchase_order_revision WHERE po_id = ?", poId);
        assertThat(rev.get("revision")).isEqualTo(1);
        assertThat(rev.get("revision_status")).isEqualTo("DRAFT");
        assertThat(rev.get("approval_policy_version")).isEqualTo("UNEVALUATED");
        assertThat(rev.get("before_snapshot")).isNull();
        assertThat(rev.get("org_id")).isEqualTo(fx.orgId);
        assertThat(rev.get("po_number")).isEqualTo(body.get("poNumber").asString());

        List<Map<String, Object>> lines = jdbc.queryForList(
                "SELECT * FROM purchase_order_line WHERE po_id = ? ORDER BY line_no", poId);
        assertThat(lines).hasSize(2);
        for (Map<String, Object> line : lines) {
            assertThat(line.get("effective_revision")).isEqualTo(1);
            assertThat(line.get("line_status")).isEqualTo("ACTIVE");
            assertThat(((java.math.BigDecimal) line.get("closed_qty")).signum()).isZero();
            assertThat(((java.math.BigDecimal) line.get("over_receipt_pct")).signum()).isZero();
            assertThat(line.get("version")).isEqualTo(0L);
        }

        Map<String, Object> audit = jdbc.queryForMap("SELECT * FROM purchase_order_audit_log WHERE po_id = ?", poId);
        assertThat(audit.get("action")).isEqualTo("CREATE");
        assertThat(audit.get("revision")).isEqualTo(1);
        assertThat(audit.get("actor_id")).isEqualTo(fx.userId);
        assertThat(audit.get("correlation_id")).isEqualTo(requestId);

        // TEST-07(c): 저장된 스냅샷을 canonical 로 재직렬화하면 해시와 같다.
        String stored = jdbc.queryForObject("SELECT after_snapshot::text FROM purchase_order_revision WHERE po_id = ?",
                String.class, poId);
        String sha = (String) rev.get("content_sha256");
        assertThat(sha).matches("^[0-9a-f]{64}$");
        assertThat(snapshotWriter.sha256Hex(snapshotWriter.canonicalize(stored))).isEqualTo(sha);
        assertThat(audit.get("after_snapshot").toString()).isNotBlank();
        String auditStored = jdbc.queryForObject(
                "SELECT after_snapshot::text FROM purchase_order_audit_log WHERE po_id = ?", String.class, poId);
        assertThat(snapshotWriter.canonicalize(auditStored)).isEqualTo(snapshotWriter.canonicalize(stored));
    }

    @Test
    void ignoresOrgIdInBody() throws Exception {
        Map<String, Object> body = fx.validRequest();
        body.put("orgId", UUID.randomUUID().toString());
        send(fx.orgId, fx.userId, key(), body, null).andExpect(status().isCreated());
        assertThat(count("purchase_order", fx.orgId)).isEqualTo(1);
    }

    @Test
    void rejectsUnavailableMasters() throws Exception {
        String otherOrg = "org-" + UUID.randomUUID();
        Instant now = Instant.now();
        UUID inactivePlant = UUID.randomUUID();
        plants.save(Plant.create(inactivePlant, fx.orgId, "ip-" + inactivePlant.toString().substring(0, 8), "P", MasterStatus.INACTIVE, fx.userId, now));
        UUID foreignPlant = UUID.randomUUID();
        plants.save(Plant.create(foreignPlant, otherOrg, "fp-" + foreignPlant.toString().substring(0, 8), "P", MasterStatus.ACTIVE, fx.userId, now));
        UUID otherPlant = UUID.randomUUID();
        plants.save(Plant.create(otherPlant, fx.orgId, "op-" + otherPlant.toString().substring(0, 8), "P", MasterStatus.ACTIVE, fx.userId, now));
        UUID otherPlantWarehouse = UUID.randomUUID();
        warehouses.save(Warehouse.create(otherPlantWarehouse, fx.orgId, otherPlant, "ow-" + otherPlantWarehouse.toString().substring(0, 8), "W",
                MasterStatus.ACTIVE, fx.userId, now));
        UUID inactiveWarehouse = UUID.randomUUID();
        warehouses.save(Warehouse.create(inactiveWarehouse, fx.orgId, fx.plantId, "iw-" + inactiveWarehouse.toString().substring(0, 8), "W",
                MasterStatus.INACTIVE, fx.userId, now));
        UUID inactiveSupplier = UUID.randomUUID();
        suppliers.save(Supplier.create(inactiveSupplier, fx.orgId, "is-" + inactiveSupplier.toString().substring(0, 8), "S", MasterStatus.INACTIVE, fx.userId, now));
        UUID foreignSupplier = UUID.randomUUID();
        suppliers.save(Supplier.create(foreignSupplier, otherOrg, "fs-" + foreignSupplier.toString().substring(0, 8), "S", MasterStatus.ACTIVE, fx.userId, now));
        UUID disabledItem = UUID.randomUUID();
        items.save(Item.create(disabledItem, fx.orgId, "di-" + disabledItem.toString().substring(0, 8), "I", MasterStatus.ACTIVE, false, fx.userId, now));
        UUID inactiveItem = UUID.randomUUID();
        items.save(Item.create(inactiveItem, fx.orgId, "ii-" + inactiveItem.toString().substring(0, 8), "I", MasterStatus.INACTIVE, true, fx.userId, now));
        UUID foreignItem = UUID.randomUUID();
        items.save(Item.create(foreignItem, otherOrg, "fi-" + foreignItem.toString().substring(0, 8), "I", MasterStatus.ACTIVE, true, fx.userId, now));

        expect422(with("plantId", inactivePlant), "PLANT_NOT_AVAILABLE");
        expect422(with("plantId", foreignPlant), "PLANT_NOT_AVAILABLE");
        expect422(with("shipToWarehouseId", otherPlantWarehouse), "WAREHOUSE_NOT_AVAILABLE");
        expect422(with("shipToWarehouseId", inactiveWarehouse), "WAREHOUSE_NOT_AVAILABLE");
        expect422(with("supplierId", inactiveSupplier), "SUPPLIER_NOT_AVAILABLE");
        expect422(with("supplierId", foreignSupplier), "SUPPLIER_NOT_AVAILABLE");
        expect422(with("supplierId", UUID.randomUUID()), "SUPPLIER_NOT_AVAILABLE");
        expect422(fx.validRequest(disabledItem, fx.item2Id), "ITEM_NOT_PURCHASABLE");
        expect422(fx.validRequest(fx.item1Id, inactiveItem), "ITEM_NOT_PURCHASABLE");
        expect422(fx.validRequest(foreignItem, fx.item2Id), "ITEM_NOT_PURCHASABLE");

        assertNoRows(fx.orgId);
    }

    @Test
    void replaysSameKeyAndRejectsDifferentBody() throws Exception {
        String key = key();
        Map<String, Object> body = fx.validRequest();
        List<String> ids = new ArrayList<>();
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            MvcResult r = send(fx.orgId, fx.userId, key, body, null).andExpect(status().isCreated()).andReturn();
            JsonNode json = objectMapper.readTree(r.getResponse().getContentAsString());
            ids.add(json.get("id").asString());
            numbers.add(json.get("poNumber").asString());
        }
        assertThat(ids).containsOnly(ids.get(0));
        assertThat(numbers).containsOnly(numbers.get(0));
        assertThat(count("purchase_order", fx.orgId)).isEqualTo(1);
        assertThat(count("purchase_order_command", fx.orgId)).isEqualTo(1);

        Map<String, Object> changed = fx.validRequest();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) changed.get("lines");
        lines.get(0).put("orderedQty", "11");
        send(fx.orgId, fx.userId, key, changed, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(count("purchase_order", fx.orgId)).isEqualTo(1);
    }

    @Test
    void rejectsSameKeyFromAnotherUser() throws Exception {
        String key = key();
        send(fx.orgId, fx.userId, key, fx.validRequest(), null).andExpect(status().isCreated());
        send(fx.orgId, "user-" + UUID.randomUUID(), key, fx.validRequest(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(count("purchase_order", fx.orgId)).isEqualTo(1);
    }

    @Test
    void sameKeyInDifferentOrgsCreatesSeparately() throws Exception {
        PurchaseOrderFixture other = new PurchaseOrderFixture(plants, warehouses, suppliers, items);
        String key = key();
        String id1 = idOf(send(fx.orgId, fx.userId, key, fx.validRequest(), null).andExpect(status().isCreated()).andReturn());
        String id2 = idOf(send(other.orgId, other.userId, key, other.validRequest(), null).andExpect(status().isCreated()).andReturn());
        assertThat(id1).isNotEqualTo(id2);
        assertThat(count("purchase_order_command", fx.orgId)).isEqualTo(1);
        assertThat(count("purchase_order_command", other.orgId)).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyCreatesOnce() throws Exception {
        String key = key();
        Map<String, Object> body = fx.validRequest();
        List<String> ids = runConcurrently(2, i -> send(fx.orgId, fx.userId, key, body, null));
        assertThat(ids).hasSize(2).containsOnly(ids.get(0));
        assertThat(count("purchase_order", fx.orgId)).isEqualTo(1);
        assertThat(count("purchase_order_command", fx.orgId)).isEqualTo(1);
    }

    @Test
    void issuesDistinctPoNumbers() throws Exception {
        List<String> numbers = new ArrayList<>(runConcurrentlyNumbers(2));
        MvcResult sequential = send(fx.orgId, fx.userId, key(), fx.validRequest(), null).andExpect(status().isCreated()).andReturn();
        numbers.add(objectMapper.readTree(sequential.getResponse().getContentAsString()).get("poNumber").asString());
        assertThat(numbers).hasSize(3).doesNotHaveDuplicates().allMatch(n -> n.matches("^PO-\\d{8}-\\d{8,}$"));
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(numbers).allMatch(n -> n.startsWith("PO-" + today + "-"));
    }

    private List<String> runConcurrentlyNumbers(int n) throws Exception {
        List<String> numbers = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                MvcResult r = send(fx.orgId, fx.userId, key(), fx.validRequest(), null).andExpect(status().isCreated()).andReturn();
                return objectMapper.readTree(r.getResponse().getContentAsString()).get("poNumber").asString();
            }));
        }
        ready.await();
        go.countDown();
        for (Future<String> f : futures) {
            numbers.add(f.get());
        }
        pool.shutdown();
        return numbers;
    }

    private interface Sender {
        org.springframework.test.web.servlet.ResultActions send(int index) throws Exception;
    }

    private List<String> runConcurrently(int n, Sender sender) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                MvcResult r = sender.send(index).andExpect(status().isCreated()).andReturn();
                return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asString();
            }));
        }
        ready.await();
        go.countDown();
        List<String> ids = new ArrayList<>();
        for (Future<String> f : futures) {
            ids.add(f.get());
        }
        pool.shutdown();
        return ids;
    }

    private Map<String, Object> with(String field, UUID value) {
        Map<String, Object> body = fx.validRequest();
        body.put(field, value.toString());
        return body;
    }

    private void expect422(Map<String, Object> body, String code) throws Exception {
        send(fx.orgId, fx.userId, key(), body, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void assertNoRows(String orgId) {
        assertThat(count("purchase_order", orgId)).isZero();
        assertThat(count("purchase_order_revision", orgId)).isZero();
        assertThat(count("purchase_order_command", orgId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_line l JOIN purchase_order p ON p.id = l.po_id WHERE p.org_id = ?",
                Integer.class, orgId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_audit_log a JOIN purchase_order p ON p.id = a.po_id WHERE p.org_id = ?",
                Integer.class, orgId)).isZero();
    }

    private int count(String table, String orgId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE org_id = ?", Integer.class, orgId);
    }

    private String idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private org.springframework.test.web.servlet.ResultActions send(String orgId, String userId, String key,
            Map<String, Object> body, String requestId) throws Exception {
        var builder = post(URL)
                .contentType("application/json")
                .header("X-Org-Id", orgId)
                .header("X-User-Id", userId)
                .header("Idempotency-Key", key)
                .content(objectMapper.writeValueAsString(body));
        if (requestId != null) {
            builder.header("X-Request-Id", requestId);
        }
        return mockMvc.perform(builder);
    }
}
