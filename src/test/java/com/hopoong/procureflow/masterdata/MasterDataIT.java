package com.hopoong.procureflow.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.hopoong.procureflow.support.PostgresContainerSupport;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataIT extends PostgresContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    final String orgId = "org-" + UUID.randomUUID();
    final String userId = "user-" + UUID.randomUUID();

    @Test
    void createsPlantWithTrimmedValuesAndActorAudit() throws Exception {
        MvcResult result = create(orgId, "/v1/plants", plant("  P-01  ", "  Plant 01  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plantCode").value("P-01"))
                .andExpect(jsonPath("$.name").value("Plant 01"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM plant WHERE id = ?", UUID.fromString(idOf(result)));
        assertThat(row.get("org_id")).isEqualTo(orgId);
        assertThat(row.get("created_by")).isEqualTo(userId);
        assertThat(row.get("updated_by")).isEqualTo(userId);
    }

    @Test
    void rejectsDuplicateCodeInSameOrgButAllowsOtherOrg() throws Exception {
        create(orgId, "/v1/suppliers", supplier("S-01")).andExpect(status().isCreated());

        create(orgId, "/v1/suppliers", supplier("S-01"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPLIER_CODE_DUPLICATED"));
        create("org-" + UUID.randomUUID(), "/v1/suppliers", supplier("S-01")).andExpect(status().isCreated());
    }

    @Test
    void createsWarehouseUnderOwnPlantOnly() throws Exception {
        String plantId = idOf(create(orgId, "/v1/plants", plant("P-01", "Plant")).andReturn());
        String otherOrgPlantId = idOf(create("org-" + UUID.randomUUID(), "/v1/plants", plant("P-01", "Plant"))
                .andReturn());

        create(orgId, "/v1/warehouses", warehouse(plantId, "W-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plantId").value(plantId));
        create(orgId, "/v1/warehouses", warehouse(otherOrgPlantId, "W-02"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PLANT_NOT_FOUND"));
    }

    @Test
    void createsItemWithPurchaseEnabledFlag() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemCode", "I-01");
        body.put("name", "Item");
        body.put("status", "ACTIVE");
        body.put("purchaseEnabled", false);

        create(orgId, "/v1/items", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseEnabled").value(false));
    }

    @Test
    void listsOnlyRequesterOrgSortedByCode() throws Exception {
        create(orgId, "/v1/plants", plant("P-02", "Second")).andExpect(status().isCreated());
        create(orgId, "/v1/plants", plant("P-01", "First")).andExpect(status().isCreated());
        create("org-" + UUID.randomUUID(), "/v1/plants", plant("P-00", "Other")).andExpect(status().isCreated());

        mockMvc.perform(get("/v1/plants").header("X-Org-Id", orgId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].plantCode").value("P-01"))
                .andExpect(jsonPath("$[1].plantCode").value("P-02"));
    }

    @Test
    void rejectsBlankCodeAndTooLongCode() throws Exception {
        create(orgId, "/v1/plants", plant(" ", "Plant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        create(orgId, "/v1/plants", plant("P".repeat(33), "Plant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private ResultActions create(String org, String url, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(url).contentType("application/json")
                .header("X-Org-Id", org)
                .header("X-User-Id", userId)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String idOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
    }

    private static Map<String, Object> plant(String code, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plantCode", code);
        body.put("name", name);
        body.put("status", "ACTIVE");
        return body;
    }

    private static Map<String, Object> supplier(String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supplierCode", code);
        body.put("name", "Supplier");
        body.put("status", "ACTIVE");
        return body;
    }

    private static Map<String, Object> warehouse(String plantId, String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plantId", plantId);
        body.put("warehouseCode", code);
        body.put("name", "Warehouse");
        body.put("status", "ACTIVE");
        return body;
    }
}
