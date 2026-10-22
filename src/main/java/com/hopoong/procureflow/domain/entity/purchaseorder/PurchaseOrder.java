package com.hopoong.procureflow.domain.entity.purchaseorder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** purchase_order: 발주 집합 루트. 재고 수량은 직접 보관하지 않는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

    private static final int INITIAL_REVISION = 1;

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "plant_id", nullable = false)
    private UUID plantId;

    @Column(name = "po_number", nullable = false, length = 64)
    private String poNumber;

    @Column(name = "current_revision", nullable = false)
    private Integer currentRevision;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "currency_scale", nullable = false)
    private Short currencyScale;

    @Column(name = "exchange_rate", precision = 20, scale = 10)
    private BigDecimal exchangeRate;

    @Column(name = "payment_terms_code", length = 50)
    private String paymentTermsCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_terms_snapshot")
    private String paymentTermsSnapshot;

    @Column(name = "incoterms_code", length = 10)
    private String incotermsCode;

    @Column(name = "incoterms_place")
    private String incotermsPlace;

    @Column(name = "ship_to_warehouse_id", nullable = false)
    private UUID shipToWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static PurchaseOrder draft(UUID id, String orgId, UUID plantId, String poNumber, UUID supplierId,
            String currency, int currencyScale, BigDecimal exchangeRate, String paymentTermsCode,
            String paymentTermsSnapshot, String incotermsCode, String incotermsPlace, UUID shipToWarehouseId,
            String createdBy, Instant now) {
        PurchaseOrder po = new PurchaseOrder();
        po.id = id;
        po.orgId = orgId;
        po.plantId = plantId;
        po.poNumber = poNumber;
        po.currentRevision = INITIAL_REVISION;
        po.supplierId = supplierId;
        po.currency = currency;
        po.currencyScale = (short) currencyScale;
        po.exchangeRate = exchangeRate;
        po.paymentTermsCode = paymentTermsCode;
        po.paymentTermsSnapshot = paymentTermsSnapshot;
        po.incotermsCode = incotermsCode;
        po.incotermsPlace = incotermsPlace;
        po.shipToWarehouseId = shipToWarehouseId;
        po.status = PurchaseOrderStatus.DRAFT;
        po.createdBy = createdBy;
        po.createdAt = now;
        po.updatedAt = now;
        return po;
    }

    /** 개정 초안이 생겨도 현재 유효본은 그대로 두고, version 증가로 동시 개정을 막는다. */
    public void markRevised(Instant now) {
        this.updatedAt = now;
    }
}
