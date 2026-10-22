package com.hopoong.procureflow.api.purchaseorder.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRevisionRequest;
import com.hopoong.procureflow.api.purchaseorder.model.PurchaseOrderRevisionCreatedResponse;
import com.hopoong.procureflow.api.purchaseorder.model.RevisePurchaseOrderLineRequest;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderSnapshotWriter.HeaderTerms;
import com.hopoong.procureflow.api.purchaseorder.service.PurchaseOrderSnapshotWriter.SnapshotLine;
import com.hopoong.procureflow.common.auth.RequestActor;
import com.hopoong.procureflow.common.error.BusinessException;
import com.hopoong.procureflow.domain.entity.purchaseorder.LineStatus;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrder;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderAuditLog;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderCommand;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderCommandType;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderLine;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderRevision;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderStatus;
import com.hopoong.procureflow.domain.entity.purchaseorder.RevisionStatus;
import com.hopoong.procureflow.domain.repository.purchaseorder.PurchaseOrderAuditLogRepository;
import com.hopoong.procureflow.domain.repository.purchaseorder.PurchaseOrderCommandRepository;
import com.hopoong.procureflow.domain.repository.purchaseorder.PurchaseOrderLineRepository;
import com.hopoong.procureflow.domain.repository.purchaseorder.PurchaseOrderRepository;
import com.hopoong.procureflow.domain.repository.purchaseorder.PurchaseOrderRevisionRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 승인 이후 발주의 변경 제안을 새 개정(DRAFT)으로만 저장한다.
 * 현재 유효본(purchase_order 조건·current_revision, purchase_order_line)은 바꾸지 않는다 — 활성화는 PO-04.
 */
@Slf4j
@Service
public class PurchaseOrderRevisionService {

    private static final int CREATED_STATUS = 201;
    private static final Set<PurchaseOrderStatus> REVISABLE_STATUSES = Set.of(PurchaseOrderStatus.APPROVED,
            PurchaseOrderStatus.SENT, PurchaseOrderStatus.PARTIALLY_RECEIVED);
    private static final Set<RevisionStatus> OPEN_REVISION_STATUSES = Set.of(RevisionStatus.DRAFT,
            RevisionStatus.PENDING_APPROVAL);

    private final PurchaseOrderTermsValidator termsValidator;
    private final PurchaseOrderMasterValidator masterValidator;
    private final PurchaseOrderSnapshotWriter snapshotWriter;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderRevisionRepository revisionRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final PurchaseOrderCommandRepository commandRepository;
    private final PurchaseOrderAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PurchaseOrderRevisionService(PurchaseOrderTermsValidator termsValidator,
            PurchaseOrderMasterValidator masterValidator, PurchaseOrderSnapshotWriter snapshotWriter,
            PurchaseOrderRepository purchaseOrderRepository, PurchaseOrderRevisionRepository revisionRepository,
            PurchaseOrderLineRepository lineRepository, PurchaseOrderCommandRepository commandRepository,
            PurchaseOrderAuditLogRepository auditLogRepository, ObjectMapper objectMapper, Clock clock) {
        this.termsValidator = termsValidator;
        this.masterValidator = masterValidator;
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
    public PurchaseOrderRevisionCreatedResponse create(RequestActor actor, UUID poId, String idempotencyKey,
            String requestSha256, UUID correlationId, CreatePurchaseOrderRevisionRequest request) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        // 같은 키의 동시 요청은 이 INSERT 에서 대기한다. 발주 행 잠금보다 먼저 두어야
        // 같은 키 요청이 발주 잠금을 잡은 채 키 대기에 들어가는 교착이 생기지 않는다.
        PurchaseOrderCommand command = commandRepository.saveAndFlush(PurchaseOrderCommand.start(actor.orgId(),
                PurchaseOrderCommandType.CREATE_PURCHASE_ORDER_REVISION, idempotencyKey, requestSha256, now));

        // 같은 발주의 개정 작성은 이 행 잠금으로 직렬화되고, 뒤 요청은 커밋된 새 version 을 읽어 409 가 된다.
        PurchaseOrder po = purchaseOrderRepository.findForUpdateByIdAndOrgId(poId, actor.orgId())
                .orElseThrow(() -> new BusinessException(PurchaseOrderErrorCode.PURCHASE_ORDER_NOT_FOUND));
        if (!po.getVersion().equals(request.expectedVersion())) {
            throw new BusinessException(PurchaseOrderErrorCode.PO_VERSION_CONFLICT);
        }
        if (!REVISABLE_STATUSES.contains(po.getStatus())) {
            throw new BusinessException(PurchaseOrderErrorCode.PO_NOT_REVISABLE);
        }
        if (revisionRepository.existsByPoIdAndRevisionStatusIn(poId, OPEN_REVISION_STATUSES)) {
            throw new BusinessException(PurchaseOrderErrorCode.OPEN_REVISION_EXISTS);
        }

        List<PurchaseOrderLine> currentLines = lineRepository.findByPoIdOrderByLineNo(poId);
        Map<UUID, PurchaseOrderLine> activeLines = activeLinesById(currentLines);
        validateLines(request, activeLines);
        termsValidator.validate(po.getCurrencyScale(), changedUnitPrices(request, activeLines));
        masterValidator.validateRevision(actor.orgId(), po.getPlantId(),
                request.shipToWarehouseId().equals(po.getShipToWarehouseId()) ? null : request.shipToWarehouseId(),
                request.lines().stream().map(l -> l.lineId() == null ? l.itemId() : null).toList());

        int revisionNo = revisionRepository.findMaxRevision(poId) + 1;
        String proposed = snapshotWriter.revisionSnapshot(po, proposedTerms(request),
                proposedLines(request, currentLines), revisionNo);
        String current = snapshotWriter.revisionSnapshot(po, HeaderTerms.of(po),
                currentLines.stream().map(SnapshotLine::of).toList(), revisionNo);
        if (proposed.equals(current)) {
            throw new BusinessException(PurchaseOrderErrorCode.REVISION_NO_CHANGE);
        }

        String before = revisionRepository.findByPoIdAndRevision(poId, po.getCurrentRevision()).orElseThrow()
                .getAfterSnapshot();
        PurchaseOrderRevision revision = PurchaseOrderRevision.draft(po, revisionNo, request.changeReasonCode(),
                request.changeReasonDetail(), before, proposed, snapshotWriter.sha256Hex(proposed), actor.userId(),
                now);
        revisionRepository.save(revision);
        auditLogRepository.save(PurchaseOrderAuditLog.revisionCreated(poId, revisionNo, actor.userId(),
                request.changeReasonCode(), before, proposed, correlationId, now));

        po.markRevised(now);
        // 응답의 poVersion 이 증가한 값이 되도록 여기서 반영한다.
        purchaseOrderRepository.flush();
        command.complete(poId, revision.getId(), CREATED_STATUS, now);

        log.info("purchase order revision drafted poId={} revision={}", poId, revisionNo);
        return PurchaseOrderRevisionCreatedResponse.of(po, revision);
    }

    private Map<UUID, PurchaseOrderLine> activeLinesById(List<PurchaseOrderLine> lines) {
        Map<UUID, PurchaseOrderLine> active = new HashMap<>();
        for (PurchaseOrderLine line : lines) {
            if (line.getLineStatus() == LineStatus.ACTIVE) {
                active.put(line.getId(), line);
            }
        }
        return active;
    }

    /** 기존 진행 중 라인은 lines(lineId) 와 cancelLineIds 를 합쳐 정확히 한 번 나와야 한다. */
    private void validateLines(CreatePurchaseOrderRevisionRequest request, Map<UUID, PurchaseOrderLine> activeLines) {
        Set<UUID> seen = new HashSet<>();
        for (RevisePurchaseOrderLineRequest l : request.lines()) {
            if (l.lineId() == null) {
                continue;
            }
            PurchaseOrderLine current = activeLines.get(l.lineId());
            if (current == null) {
                throw lineInvalid("이 발주의 진행 중 라인이 아닙니다.", l.lineId());
            }
            if (!seen.add(l.lineId())) {
                throw lineInvalid("같은 라인이 두 번 지정되었습니다.", l.lineId());
            }
            if (!current.getItemId().equals(l.itemId())) {
                throw lineInvalid("기존 라인의 품목은 바꿀 수 없습니다.", l.lineId());
            }
            if (l.orderedQty().compareTo(current.getClosedQty()) < 0) {
                throw lineInvalid("발주 수량이 종결 수량보다 작습니다.", l.lineId());
            }
        }
        for (UUID cancelId : request.cancelLineIds()) {
            if (!activeLines.containsKey(cancelId)) {
                throw lineInvalid("취소할 라인이 이 발주의 진행 중 라인이 아닙니다.", cancelId);
            }
            if (!seen.add(cancelId)) {
                throw lineInvalid("같은 라인이 두 번 지정되었습니다.", cancelId);
            }
        }
        for (UUID activeId : activeLines.keySet()) {
            if (!seen.contains(activeId)) {
                throw lineInvalid("누락된 기존 라인이 있습니다.", activeId);
            }
        }
    }

    private BusinessException lineInvalid(String reason, UUID lineId) {
        return new BusinessException(PurchaseOrderErrorCode.REVISION_LINE_INVALID,
                PurchaseOrderErrorCode.REVISION_LINE_INVALID.message() + " " + reason + " lineId=" + lineId);
    }

    /** 요청 lines 순서. 신규 라인과 단가가 바뀐 라인만 값, 그대로인 라인은 null(규칙 도입 전 단가를 다시 막지 않는다). */
    private List<BigDecimal> changedUnitPrices(CreatePurchaseOrderRevisionRequest request,
            Map<UUID, PurchaseOrderLine> activeLines) {
        List<BigDecimal> prices = new ArrayList<>();
        for (RevisePurchaseOrderLineRequest l : request.lines()) {
            boolean unchanged = l.lineId() != null
                    && activeLines.get(l.lineId()).getUnitPrice().compareTo(l.unitPrice()) == 0;
            prices.add(unchanged ? null : l.unitPrice());
        }
        return prices;
    }

    private HeaderTerms proposedTerms(CreatePurchaseOrderRevisionRequest request) {
        String paymentTermsSnapshot = request.paymentTermsSnapshot() == null ? null
                : objectMapper.writeValueAsString(request.paymentTermsSnapshot());
        return new HeaderTerms(request.shipToWarehouseId(), request.exchangeRate(), request.paymentTermsCode(),
                paymentTermsSnapshot, request.incotermsCode(), request.incotermsPlace());
    }

    /** 유지 라인은 기존 id·lineNo·closedQty + 제안 값, 신규 라인은 최대 lineNo 다음부터, 취소 라인은 CANCELLED. */
    private List<SnapshotLine> proposedLines(CreatePurchaseOrderRevisionRequest request,
            List<PurchaseOrderLine> currentLines) {
        Map<UUID, RevisePurchaseOrderLineRequest> kept = new LinkedHashMap<>();
        List<RevisePurchaseOrderLineRequest> added = new ArrayList<>();
        for (RevisePurchaseOrderLineRequest l : request.lines()) {
            if (l.lineId() == null) {
                added.add(l);
            } else {
                kept.put(l.lineId(), l);
            }
        }
        Set<UUID> cancelled = Set.copyOf(request.cancelLineIds());

        List<SnapshotLine> lines = new ArrayList<>();
        int maxLineNo = 0;
        for (PurchaseOrderLine current : currentLines) {
            maxLineNo = Math.max(maxLineNo, current.getLineNo());
            RevisePurchaseOrderLineRequest proposal = kept.get(current.getId());
            if (proposal != null) {
                lines.add(snapshotLine(current.getId(), current.getLineNo(), LineStatus.ACTIVE,
                        current.getClosedQty(), proposal));
            } else if (cancelled.contains(current.getId())) {
                lines.add(SnapshotLine.of(current).withLineStatus(LineStatus.CANCELLED));
            } else {
                lines.add(SnapshotLine.of(current));
            }
        }
        int lineNo = maxLineNo;
        for (RevisePurchaseOrderLineRequest l : added) {
            lines.add(snapshotLine(UUID.randomUUID(), ++lineNo, LineStatus.ACTIVE, BigDecimal.ZERO, l));
        }
        return lines;
    }

    private SnapshotLine snapshotLine(UUID id, int lineNo, LineStatus status, BigDecimal closedQty,
            RevisePurchaseOrderLineRequest l) {
        return new SnapshotLine(id, lineNo, l.itemId(), status, l.orderedQty(), closedQty, l.purchaseUom(),
                l.inventoryUom(), l.conversionNumerator(), l.conversionDenominator(), l.unitPrice(), l.taxCode(),
                l.taxRate(), l.requestedDate(), l.promisedDate(), l.receiptRouting(), l.inspectionSpecRevisionId(),
                l.overReceiptPct(), null);
    }
}
