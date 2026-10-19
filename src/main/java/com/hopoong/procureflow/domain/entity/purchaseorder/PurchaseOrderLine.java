package com.hopoong.procureflow.domain.entity.purchaseorder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "purchase_order_line")
public class PurchaseOrderLine {

    private static final int INITIAL_REVISION = 1;

    @Id
    private UUID id;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_status", nullable = false, length = 20)
    private LineStatus lineStatus;

    @Column(name = "ordered_qty", nullable = false, precision = 19, scale = 6)
    private BigDecimal orderedQty;

    @Column(name = "closed_qty", nullable = false, precision = 19, scale = 6)
    private BigDecimal closedQty;

    @Column(name = "purchase_uom", nullable = false, length = 20)
    private String purchaseUom;

    @Column(name = "inventory_uom", nullable = false, length = 20)
    private String inventoryUom;

    @Column(name = "conversion_numerator", nullable = false)
    private Long conversionNumerator;

    @Column(name = "conversion_denominator", nullable = false)
    private Long conversionDenominator;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "tax_code", nullable = false, length = 50)
    private String taxCode;

    @Column(name = "tax_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal taxRate;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_routing", nullable = false, length = 20)
    private ReceiptRouting receiptRouting;

    @Column(name = "inspection_spec_revision_id")
    private UUID inspectionSpecRevisionId;

    @Column(name = "over_receipt_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal overReceiptPct;

    @Column(name = "effective_revision", nullable = false)
    private Integer effectiveRevision;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PurchaseOrderLine create(UUID poId, int lineNo, UUID itemId, BigDecimal orderedQty,
            String purchaseUom, String inventoryUom, long conversionNumerator, long conversionDenominator,
            BigDecimal unitPrice, String taxCode, BigDecimal taxRate, LocalDate requestedDate, LocalDate promisedDate,
            ReceiptRouting receiptRouting, UUID inspectionSpecRevisionId, BigDecimal overReceiptPct, Instant now) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.id = UUID.randomUUID();
        line.poId = poId;
        line.lineNo = lineNo;
        line.itemId = itemId;
        line.lineStatus = LineStatus.ACTIVE;
        line.orderedQty = orderedQty;
        line.closedQty = BigDecimal.ZERO;
        line.purchaseUom = purchaseUom;
        line.inventoryUom = inventoryUom;
        line.conversionNumerator = conversionNumerator;
        line.conversionDenominator = conversionDenominator;
        line.unitPrice = unitPrice;
        line.taxCode = taxCode;
        line.taxRate = taxRate;
        line.requestedDate = requestedDate;
        line.promisedDate = promisedDate;
        line.receiptRouting = receiptRouting;
        line.inspectionSpecRevisionId = inspectionSpecRevisionId;
        line.overReceiptPct = overReceiptPct != null ? overReceiptPct : BigDecimal.ZERO;
        line.effectiveRevision = INITIAL_REVISION;
        line.createdAt = now;
        line.updatedAt = now;
        return line;
    }
}
