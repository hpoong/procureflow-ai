package com.hopoong.procureflow.purchaseorder.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hopoong.procureflow.common.auth.RequestActor;
import com.hopoong.procureflow.purchaseorder.model.CreatePurchaseOrderRequest;
import com.hopoong.procureflow.purchaseorder.model.PurchaseOrderCreatedResponse;
import com.hopoong.procureflow.purchaseorder.service.PurchaseOrderCreateFacade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderCreateFacade createFacade;

    public PurchaseOrderController(PurchaseOrderCreateFacade createFacade) {
        this.createFacade = createFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderCreatedResponse create(RequestActor actor,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return createFacade.create(actor, idempotencyKey, parseCorrelationId(requestId), request);
    }

    private UUID parseCorrelationId(String requestId) {
        if (requestId != null) {
            try {
                return UUID.fromString(requestId.trim());
            } catch (IllegalArgumentException e) {
                // UUID 가 아니면 거절하지 않고 새로 만든다.
            }
        }
        return UUID.randomUUID();
    }
}
