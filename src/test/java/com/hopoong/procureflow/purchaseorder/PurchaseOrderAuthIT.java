package com.hopoong.procureflow.purchaseorder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.hopoong.procureflow.support.PostgresContainerSupport;

class PurchaseOrderAuthIT {

    private static final String BODY = "{}";

    @SpringBootTest(properties = "procureflow.auth.header-actor.enabled=false")
    @AutoConfigureMockMvc
    static class Disabled extends PostgresContainerSupport {
        @Autowired MockMvc mockMvc;

        @Test
        void returns401WhenHeaderActorDisabled() throws Exception {
            mockMvc.perform(post("/v1/purchase-orders").contentType("application/json")
                    .header("X-Org-Id", UUID.randomUUID().toString())
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .header("Idempotency-Key", "k").content(BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }
    }

    @SpringBootTest
    @AutoConfigureMockMvc
    static class Enabled extends PostgresContainerSupport {
        @Autowired MockMvc mockMvc;

        @Test
        void returns401WhenOrgHeaderMissing() throws Exception {
            mockMvc.perform(post("/v1/purchase-orders").contentType("application/json")
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .header("Idempotency-Key", "k").content(BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        @Test
        void returns401WhenUserHeaderTooLong() throws Exception {
            mockMvc.perform(post("/v1/purchase-orders").contentType("application/json")
                    .header("X-Org-Id", UUID.randomUUID().toString())
                    .header("X-User-Id", "u".repeat(65))
                    .header("Idempotency-Key", "k").content(BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }
    }
}
