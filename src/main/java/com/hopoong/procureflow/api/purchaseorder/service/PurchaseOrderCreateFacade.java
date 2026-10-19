package com.hopoong.procureflow.api.purchaseorder.service;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.hopoong.procureflow.common.auth.RequestActor;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRequest;
import com.hopoong.procureflow.api.purchaseorder.model.PurchaseOrderCreatedResponse;

/** 트랜잭션 밖에서 멱등 키를 먼저 확인하고, 같은 키 동시 요청이 충돌하면 기존 결과를 재생한다. */
@Service
public class PurchaseOrderCreateFacade {

    private static final String IDEMPOTENCY_CONSTRAINT = "uq_po_command_idempotency";

    private final PurchaseOrderSnapshotWriter snapshotWriter;
    private final PurchaseOrderReplayService replayService;
    private final PurchaseOrderCreateService createService;

    public PurchaseOrderCreateFacade(PurchaseOrderSnapshotWriter snapshotWriter,
            PurchaseOrderReplayService replayService, PurchaseOrderCreateService createService) {
        this.snapshotWriter = snapshotWriter;
        this.replayService = replayService;
        this.createService = createService;
    }

    public PurchaseOrderCreatedResponse create(RequestActor actor, String idempotencyKey, UUID correlationId,
            CreatePurchaseOrderRequest request) {
        String requestSha256 = snapshotWriter.requestHash(actor.userId(), request);
        Optional<PurchaseOrderCreatedResponse> replay = replayService.find(actor.orgId(), idempotencyKey,
                requestSha256);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return createService.create(actor, idempotencyKey, requestSha256, correlationId, request);
        } catch (DataIntegrityViolationException e) {
            if (!isIdempotencyConflict(e)) {
                throw e;
            }
            return replayService.find(actor.orgId(), idempotencyKey, requestSha256).orElseThrow(() -> e);
        }
    }

    private boolean isIdempotencyConflict(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && IDEMPOTENCY_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
