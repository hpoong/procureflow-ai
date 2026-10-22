package com.hopoong.procureflow.api.purchaseorder.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRevisionRequest;
import com.hopoong.procureflow.api.purchaseorder.model.PurchaseOrderRevisionCreatedResponse;
import com.hopoong.procureflow.api.purchaseorder.model.PurchaseOrderRevisionDetailResponse;
import com.hopoong.procureflow.api.purchaseorder.model.PurchaseOrderRevisionListResponse;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderRevisionFacade;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderRevisionQueryService;
import com.hopoong.procureflow.common.auth.RequestActor;
import com.hopoong.procureflow.common.error.ErrorResponse;

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

@Tag(name = "Purchase Order Revision", description = "발주 개정")
@RestController
@RequestMapping("/v1/purchase-orders/{poId}/revisions")
public class PurchaseOrderRevisionController {

    private final PurchaseOrderRevisionFacade revisionFacade;
    private final PurchaseOrderRevisionQueryService queryService;

    public PurchaseOrderRevisionController(PurchaseOrderRevisionFacade revisionFacade,
            PurchaseOrderRevisionQueryService queryService) {
        this.revisionFacade = revisionFacade;
        this.queryService = queryService;
    }

    @Operation(summary = "발주 개정 초안 작성", description = "승인 이후 발주(APPROVED·SENT·PARTIALLY_RECEIVED)의 변경 제안을 새 개정(DRAFT)으로 저장한다. 현재 유효본은 바뀌지 않는다.")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @ApiResponse(responseCode = "201", description = "작성됨 (또는 기존 결과 재생)")
    @ApiResponse(responseCode = "400", description = "요청·헤더·경로 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "X-Org-Id / X-User-Id 누락·64자 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "발주 없음 또는 다른 조직", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Idempotency-Key 재사용, version 불일치, 개정 불가 상태, 진행 중 개정 있음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "라인 규칙 위반, 단가 정밀도 위반, 창고·품목 사용 불가, 변경 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderRevisionCreatedResponse create(@Parameter(hidden = true) RequestActor actor,
            @PathVariable UUID poId,
            @Parameter(description = "재시도 시 같은 값을 보낸다 (최대 255자)")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Parameter(description = "추적용 UUID. 없거나 형식이 다르면 서버가 생성한다")
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody CreatePurchaseOrderRevisionRequest request) {
        return revisionFacade.create(actor, poId, idempotencyKey, parseCorrelationId(requestId), request);
    }

    @Operation(summary = "발주 개정 목록", description = "개정 번호 내림차순")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @ApiResponse(responseCode = "200", description = "조회됨")
    @ApiResponse(responseCode = "400", description = "경로 형식 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "X-Org-Id / X-User-Id 누락·64자 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "발주 없음 또는 다른 조직", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PurchaseOrderRevisionListResponse list(@Parameter(hidden = true) RequestActor actor,
            @PathVariable UUID poId) {
        return queryService.list(actor.orgId(), poId);
    }

    @Operation(summary = "발주 개정 상세", description = "개정 전후 스냅샷을 포함한다")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @ApiResponse(responseCode = "200", description = "조회됨")
    @ApiResponse(responseCode = "400", description = "경로 형식 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "X-Org-Id / X-User-Id 누락·64자 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "발주 없음·다른 조직(PURCHASE_ORDER_NOT_FOUND) 또는 개정 없음(REVISION_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{revision}")
    public PurchaseOrderRevisionDetailResponse detail(@Parameter(hidden = true) RequestActor actor,
            @PathVariable UUID poId, @PathVariable int revision) {
        return queryService.detail(actor.orgId(), poId, revision);
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
