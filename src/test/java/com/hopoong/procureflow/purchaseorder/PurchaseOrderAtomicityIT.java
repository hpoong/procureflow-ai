package com.hopoong.procureflow.purchaseorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hopoong.procureflow.domain.repository.masterdata.ItemRepository;
import com.hopoong.procureflow.domain.repository.masterdata.PlantRepository;
import com.hopoong.procureflow.domain.repository.masterdata.SupplierRepository;
import com.hopoong.procureflow.domain.repository.masterdata.WarehouseRepository;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderAuditLog;
import com.hopoong.procureflow.domain.repository.purchaseorder.PurchaseOrderAuditLogRepository;
import com.hopoong.procureflow.support.PostgresContainerSupport;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseOrderAtomicityIT extends PostgresContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlantRepository plants;
    @Autowired WarehouseRepository warehouses;
    @Autowired SupplierRepository suppliers;
    @Autowired ItemRepository items;
    @MockitoBean PurchaseOrderAuditLogRepository auditLogRepository;

    @Test
    void rollsBackEverythingWhenAuditSaveFails() throws Exception {
        PurchaseOrderFixture fx = new PurchaseOrderFixture(plants, warehouses, suppliers, items);
        doThrow(new IllegalStateException("boom")).when(auditLogRepository).save(any(PurchaseOrderAuditLog.class));

        mockMvc.perform(post("/v1/purchase-orders")
                .contentType("application/json")
                .header("X-Org-Id", fx.orgId.toString())
                .header("X-User-Id", fx.userId.toString())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(objectMapper.writeValueAsString(fx.validRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        for (String table : new String[] {"purchase_order", "purchase_order_revision", "purchase_order_command"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE org_id = ?", Integer.class,
                    fx.orgId)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_line l JOIN purchase_order p ON p.id = l.po_id WHERE p.org_id = ?",
                Integer.class, fx.orgId)).isZero();
    }
}
