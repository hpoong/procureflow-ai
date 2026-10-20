package com.hopoong.procureflow.api.purchaseorder.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hopoong.procureflow.common.auth.RequestActor;
import com.hopoong.procureflow.common.error.ErrorResponse;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRequest;
import com.hopoong.procureflow.api.purchaseorder.model.PurchaseOrderCreatedResponse;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderCreateFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Tag(name = "Purchase Order", description = "발주")
@RestController
@RequestMapping("/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderCreateFacade createFacade;

    public PurchaseOrderController(PurchaseOrderCreateFacade createFacade) {
        this.createFacade = createFacade;
    }

    @Operation(summary = "발주 생성", description = "DRAFT 상태 발주를 만든다. 같은 Idempotency-Key·같은 요청이면 기존 결과를 돌려준다.")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @ApiResponse(responseCode = "201", description = "생성됨 (또는 기존 결과 재생)")
    @ApiResponse(responseCode = "400", description = "요청·헤더 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "X-Org-Id / X-User-Id 누락·64자 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "같은 Idempotency-Key 로 다른 요청 재사용", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "사업장·창고·공급사·품목 사용 불가", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderCreatedResponse create(@Parameter(hidden = true) RequestActor actor,
            @Parameter(description = "재시도 시 같은 값을 보낸다 (최대 255자)")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Parameter(description = "추적용 UUID. 없거나 형식이 다르면 서버가 생성한다")
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
