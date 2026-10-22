package com.hopoong.procureflow.api.purchaseorder.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hopoong.procureflow.api.purchaseorder.model.PurchaseOrderRevisionDetailResponse;
import com.hopoong.procureflow.api.purchaseorder.model.PurchaseOrderRevisionListResponse;
import com.hopoong.procureflow.common.error.BusinessException;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrder;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderRevision;
import com.hopoong.procureflow.domain.repository.purchaseorder.PurchaseOrderRepository;
import com.hopoong.procureflow.domain.repository.purchaseorder.PurchaseOrderRevisionRepository;

import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class PurchaseOrderRevisionQueryService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderRevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;

    public PurchaseOrderRevisionQueryService(PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderRevisionRepository revisionRepository, ObjectMapper objectMapper) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.revisionRepository = revisionRepository;
        this.objectMapper = objectMapper;
    }

    public PurchaseOrderRevisionListResponse list(String orgId, UUID poId) {
        PurchaseOrder po = findPurchaseOrder(orgId, poId);
        return PurchaseOrderRevisionListResponse.of(po, revisionRepository.findByPoIdOrderByRevisionDesc(poId));
    }

    public PurchaseOrderRevisionDetailResponse detail(String orgId, UUID poId, int revision) {
        findPurchaseOrder(orgId, poId);
        PurchaseOrderRevision found = revisionRepository.findByPoIdAndRevision(poId, revision)
                .orElseThrow(() -> new BusinessException(PurchaseOrderErrorCode.REVISION_NOT_FOUND));
        return PurchaseOrderRevisionDetailResponse.of(found,
                found.getBeforeSnapshot() == null ? null : objectMapper.readTree(found.getBeforeSnapshot()),
                objectMapper.readTree(found.getAfterSnapshot()));
    }

    /** 다른 조직의 발주는 존재 여부를 드러내지 않도록 없는 발주와 같이 404 로 응답한다. */
    private PurchaseOrder findPurchaseOrder(String orgId, UUID poId) {
        return purchaseOrderRepository.findByIdAndOrgId(poId, orgId)
                .orElseThrow(() -> new BusinessException(PurchaseOrderErrorCode.PURCHASE_ORDER_NOT_FOUND));
    }
}
