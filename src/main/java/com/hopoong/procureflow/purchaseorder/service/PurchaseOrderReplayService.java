package com.hopoong.procureflow.purchaseorder.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hopoong.procureflow.common.error.BusinessException;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrder;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderCommand;
import com.hopoong.procureflow.purchaseorder.domain.PurchaseOrderCommandType;
import com.hopoong.procureflow.purchaseorder.model.PurchaseOrderCreatedResponse;
import com.hopoong.procureflow.purchaseorder.repository.PurchaseOrderCommandRepository;
import com.hopoong.procureflow.purchaseorder.repository.PurchaseOrderLineRepository;
import com.hopoong.procureflow.purchaseorder.repository.PurchaseOrderRepository;

@Service
public class PurchaseOrderReplayService {

    private final PurchaseOrderCommandRepository commandRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository lineRepository;

    public PurchaseOrderReplayService(PurchaseOrderCommandRepository commandRepository,
            PurchaseOrderRepository purchaseOrderRepository, PurchaseOrderLineRepository lineRepository) {
        this.commandRepository = commandRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.lineRepository = lineRepository;
    }

    /** 같은 키·같은 요청이면 해당 발주의 현재 상태를 돌려주고, 요청이 다르면 409 로 거절한다. */
    @Transactional(readOnly = true)
    public Optional<PurchaseOrderCreatedResponse> find(UUID orgId, String idempotencyKey, String requestSha256) {
        Optional<PurchaseOrderCommand> found = commandRepository.findByOrgIdAndCommandTypeAndIdempotencyKey(orgId,
                PurchaseOrderCommandType.CREATE_PURCHASE_ORDER, idempotencyKey);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PurchaseOrderCommand command = found.get();
        if (!command.getRequestSha256().equals(requestSha256)) {
            throw new BusinessException(PurchaseOrderErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        PurchaseOrder po = purchaseOrderRepository.findById(command.getPoId()).orElseThrow();
        return Optional.of(PurchaseOrderCreatedResponse.of(po, lineRepository.findByPoIdOrderByLineNo(po.getId())));
    }
}
