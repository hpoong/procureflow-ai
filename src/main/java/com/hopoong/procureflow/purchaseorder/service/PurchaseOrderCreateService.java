package com.hopoong.procureflow.purchaseorder.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hopoong.procureflow.common.auth.RequestActor;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrder;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderAuditLog;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderCommand;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderCommandType;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderLine;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderRevision;
import com.hopoong.procureflow.purchaseorder.model.CreatePurchaseOrderLineRequest;
import com.hopoong.procureflow.purchaseorder.model.CreatePurchaseOrderRequest;
import com.hopoong.procureflow.purchaseorder.model.PurchaseOrderCreatedResponse;
import com.hopoong.procureflow.purchaseorder.repository.PurchaseOrderAuditLogRepository;
import com.hopoong.procureflow.purchaseorder.repository.PurchaseOrderCommandRepository;
import com.hopoong.procureflow.purchaseorder.repository.PurchaseOrderLineRepository;
import com.hopoong.procureflow.purchaseorder.repository.PurchaseOrderRepository;
import com.hopoong.procureflow.purchaseorder.repository.PurchaseOrderRevisionRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class PurchaseOrderCreateService {

    private static final int CREATED_STATUS = 201;

    private final PurchaseOrderMasterValidator masterValidator;
    private final PurchaseOrderNumberGenerator numberGenerator;
    private final PurchaseOrderSnapshotWriter snapshotWriter;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderRevisionRepository revisionRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final PurchaseOrderCommandRepository commandRepository;
    private final PurchaseOrderAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PurchaseOrderCreateService(PurchaseOrderMasterValidator masterValidator,
            PurchaseOrderNumberGenerator numberGenerator, PurchaseOrderSnapshotWriter snapshotWriter,
            PurchaseOrderRepository purchaseOrderRepository, PurchaseOrderRevisionRepository revisionRepository,
            PurchaseOrderLineRepository lineRepository, PurchaseOrderCommandRepository commandRepository,
            PurchaseOrderAuditLogRepository auditLogRepository, ObjectMapper objectMapper, Clock clock) {
        this.masterValidator = masterValidator;
        this.numberGenerator = numberGenerator;
        this.snapshotWriter = snapshotWriter;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.revisionRepository = revisionRepository;
        this.lineRepository = lineRepository;
        this.commandRepository = commandRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public PurchaseOrderCreatedResponse create(RequestActor actor, String idempotencyKey, String requestSha256,
            UUID correlationId, CreatePurchaseOrderRequest request) {
        masterValidator.validate(actor.orgId(), request.plantId(), request.shipToWarehouseId(), request.supplierId(),
                request.lines().stream().map(CreatePurchaseOrderLineRequest::itemId).toList());

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        // 같은 키의 동시 요청은 이 INSERT 에서 먼저 들어온 트랜잭션이 끝날 때까지 대기한다.
        PurchaseOrderCommand command = commandRepository.saveAndFlush(PurchaseOrderCommand.start(actor.orgId(),
                PurchaseOrderCommandType.CREATE_PURCHASE_ORDER, idempotencyKey, requestSha256, now));

        PurchaseOrder po = PurchaseOrder.draft(UUID.randomUUID(), actor.orgId(), request.plantId(),
                numberGenerator.next(), request.supplierId(), request.currency(),
                Currency.getInstance(request.currency()).getDefaultFractionDigits(), request.exchangeRate(),
                request.paymentTermsCode(), toJson(request), request.incotermsCode(), request.incotermsPlace(),
                request.shipToWarehouseId(), actor.userId(), now);
        List<PurchaseOrderLine> lines = buildLines(po.getId(), request, now);

        String afterSnapshot = snapshotWriter.afterSnapshot(po, lines);
        PurchaseOrderRevision revision = PurchaseOrderRevision.initialDraft(po, afterSnapshot,
                snapshotWriter.sha256Hex(afterSnapshot), actor.userId(), now);

        // FK 순서: purchase_order → revision → line
        purchaseOrderRepository.save(po);
        revisionRepository.save(revision);
        lineRepository.saveAll(lines);
        auditLogRepository.save(PurchaseOrderAuditLog.created(po.getId(), revision.getRevision(), actor.userId(),
                afterSnapshot, correlationId, now));
        command.complete(po.getId(), CREATED_STATUS, now);

        log.info("purchase order draft created poId={} poNumber={} lines={}", po.getId(), po.getPoNumber(),
                lines.size());
        return PurchaseOrderCreatedResponse.of(po, lines);
    }

    private List<PurchaseOrderLine> buildLines(UUID poId, CreatePurchaseOrderRequest request, Instant now) {
        List<PurchaseOrderLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (CreatePurchaseOrderLineRequest l : request.lines()) {
            lines.add(PurchaseOrderLine.create(poId, lineNo++, l.itemId(), l.orderedQty(), l.purchaseUom(),
                    l.inventoryUom(), l.conversionNumerator(), l.conversionDenominator(), l.unitPrice(), l.taxCode(),
                    l.taxRate(), l.requestedDate(), l.promisedDate(), l.receiptRouting(),
                    l.inspectionSpecRevisionId(), l.overReceiptPct(), now));
        }
        return lines;
    }

    private String toJson(CreatePurchaseOrderRequest request) {
        if (request.paymentTermsSnapshot() == null) {
            return null;
        }
        return objectMapper.writeValueAsString(request.paymentTermsSnapshot());
    }
}
