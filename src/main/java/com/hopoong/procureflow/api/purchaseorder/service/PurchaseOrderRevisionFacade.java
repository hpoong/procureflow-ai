package com.hopoong.procureflow.api.purchaseorder.service;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRevisionRequest;
import com.hopoong.procureflow.api.purchaseorder.model.PurchaseOrderRevisionCreatedResponse;
import com.hopoong.procureflow.common.auth.RequestActor;

/** 트랜잭션 밖에서 멱등 키를 먼저 확인하고, 같은 키 동시 요청이 충돌하면 기존 결과를 재생한다. */
@Service
public class PurchaseOrderRevisionFacade {

    private static final String IDEMPOTENCY_CONSTRAINT = "uq_po_command_idempotency";

    private final PurchaseOrderSnapshotWriter snapshotWriter;
    private final PurchaseOrderReplayService replayService;
    private final PurchaseOrderRevisionService revisionService;

    public PurchaseOrderRevisionFacade(PurchaseOrderSnapshotWriter snapshotWriter,
            PurchaseOrderReplayService replayService, PurchaseOrderRevisionService revisionService) {
        this.snapshotWriter = snapshotWriter;
        this.replayService = replayService;
        this.revisionService = revisionService;
    }

    public PurchaseOrderRevisionCreatedResponse create(RequestActor actor, UUID poId, String idempotencyKey,
            UUID correlationId, CreatePurchaseOrderRevisionRequest request) {
        String requestSha256 = snapshotWriter.revisionRequestHash(actor.userId(), poId, request);
        Optional<PurchaseOrderRevisionCreatedResponse> replay = replayService.findRevision(actor.orgId(),
                idempotencyKey, requestSha256);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return revisionService.create(actor, poId, idempotencyKey, requestSha256, correlationId, request);
        } catch (DataIntegrityViolationException e) {
            if (!isIdempotencyConflict(e)) {
                throw e;
            }
            return replayService.findRevision(actor.orgId(), idempotencyKey, requestSha256).orElseThrow(() -> e);
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
