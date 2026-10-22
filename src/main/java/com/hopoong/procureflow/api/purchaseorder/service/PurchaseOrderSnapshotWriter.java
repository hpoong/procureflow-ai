package com.hopoong.procureflow.api.purchaseorder.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.hopoong.procureflow.domain.entity.purchaseorder.LineStatus;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrder;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderLine;
import com.hopoong.procureflow.domain.entity.purchaseorder.ReceiptRouting;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderLineRequest;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRequest;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRevisionRequest;
import com.hopoong.procureflow.api.purchaseorder.model.RevisePurchaseOrderLineRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 요청 해시와 after_snapshot(초안·개정) 의 canonical 직렬화를 한 곳에서 만든다.
 * 규칙: 키 사전순, compact, null 필드 생략, 숫자는 stripTrailingZeros().toPlainString() 문자열.
 */
@Component
public class PurchaseOrderSnapshotWriter {

    private final ObjectMapper objectMapper;

    public PurchaseOrderSnapshotWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String requestHash(String userId, CreatePurchaseOrderRequest request) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("actorUserId", userId);
        root.put("request", requestTree(request));
        return sha256Hex(write(root));
    }

    public String revisionRequestHash(String userId, UUID poId, CreatePurchaseOrderRevisionRequest request) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("actorUserId", userId);
        root.put("poId", poId);
        root.put("request", revisionRequestTree(request));
        return sha256Hex(write(root));
    }

    public String afterSnapshot(PurchaseOrder po, List<PurchaseOrderLine> lines) {
        List<Object> lineTrees = new ArrayList<>();
        for (PurchaseOrderLine line : lines) {
            lineTrees.add(lineTree(SnapshotLine.of(line)));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("header", headerTree(po, HeaderTerms.of(po), po.getCurrentRevision()));
        root.put("lines", lineTrees);
        return write(root);
    }

    /**
     * 개정 n 의 afterSnapshot. afterSnapshot 과 같은 키·canonical 규칙이고 값의 출처만 다르다.
     * 헤더의 고정 필드·status 는 현재 발주, 조건은 terms. currentRevision·라인 effectiveRevision 은 n(스냅샷 메타).
     * 라인은 lineNo 순, overReceiptPct null 은 PurchaseOrderLine.create 와 같게 0 으로 둔다.
     */
    public String revisionSnapshot(PurchaseOrder po, HeaderTerms terms, List<SnapshotLine> lines, int revision) {
        List<Object> lineTrees = new ArrayList<>();
        lines.stream().sorted(Comparator.comparingInt(SnapshotLine::lineNo)).forEach(line -> {
            Map<String, Object> tree = lineTree(line);
            if (line.overReceiptPct() == null) {
                tree.put("overReceiptPct", BigDecimal.ZERO);
            }
            tree.put("effectiveRevision", revision);
            lineTrees.add(tree);
        });
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("header", headerTree(po, terms, revision));
        root.put("lines", lineTrees);
        return write(root);
    }

    /** 개정으로 바꿀 수 있는 헤더 조건. paymentTermsSnapshot 은 컬럼과 같은 JSON 문자열로 받아 같은 경로로 직렬화한다. */
    public record HeaderTerms(UUID shipToWarehouseId, BigDecimal exchangeRate, String paymentTermsCode,
            String paymentTermsSnapshot, String incotermsCode, String incotermsPlace) {

        public static HeaderTerms of(PurchaseOrder po) {
            return new HeaderTerms(po.getShipToWarehouseId(), po.getExchangeRate(), po.getPaymentTermsCode(),
                    po.getPaymentTermsSnapshot(), po.getIncotermsCode(), po.getIncotermsPlace());
        }
    }

    public record SnapshotLine(UUID id, int lineNo, UUID itemId, LineStatus lineStatus, BigDecimal orderedQty,
            BigDecimal closedQty, String purchaseUom, String inventoryUom, Long conversionNumerator,
            Long conversionDenominator, BigDecimal unitPrice, String taxCode, BigDecimal taxRate,
            LocalDate requestedDate, LocalDate promisedDate, ReceiptRouting receiptRouting,
            UUID inspectionSpecRevisionId, BigDecimal overReceiptPct, Integer effectiveRevision) {

        public static SnapshotLine of(PurchaseOrderLine line) {
            return new SnapshotLine(line.getId(), line.getLineNo(), line.getItemId(), line.getLineStatus(),
                    line.getOrderedQty(), line.getClosedQty(), line.getPurchaseUom(), line.getInventoryUom(),
                    line.getConversionNumerator(), line.getConversionDenominator(), line.getUnitPrice(),
                    line.getTaxCode(), line.getTaxRate(), line.getRequestedDate(), line.getPromisedDate(),
                    line.getReceiptRouting(), line.getInspectionSpecRevisionId(), line.getOverReceiptPct(),
                    line.getEffectiveRevision());
        }

        public SnapshotLine withLineStatus(LineStatus status) {
            return new SnapshotLine(id, lineNo, itemId, status, orderedQty, closedQty, purchaseUom, inventoryUom,
                    conversionNumerator, conversionDenominator, unitPrice, taxCode, taxRate, requestedDate,
                    promisedDate, receiptRouting, inspectionSpecRevisionId, overReceiptPct, effectiveRevision);
        }
    }

    private Map<String, Object> headerTree(PurchaseOrder po, HeaderTerms terms, Integer currentRevision) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("id", po.getId());
        header.put("orgId", po.getOrgId());
        header.put("poNumber", po.getPoNumber());
        header.put("plantId", po.getPlantId());
        header.put("supplierId", po.getSupplierId());
        header.put("shipToWarehouseId", terms.shipToWarehouseId());
        header.put("currency", po.getCurrency());
        header.put("currencyScale", po.getCurrencyScale());
        header.put("exchangeRate", terms.exchangeRate());
        header.put("paymentTermsCode", terms.paymentTermsCode());
        header.put("paymentTermsSnapshot", terms.paymentTermsSnapshot() == null ? null
                : toTree(objectMapper.readTree(terms.paymentTermsSnapshot())));
        header.put("incotermsCode", terms.incotermsCode());
        header.put("incotermsPlace", terms.incotermsPlace());
        header.put("status", po.getStatus());
        header.put("currentRevision", currentRevision);
        header.put("createdBy", po.getCreatedBy());
        header.put("createdAt", po.getCreatedAt());
        return header;
    }

    private Map<String, Object> lineTree(SnapshotLine line) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", line.id());
        tree.put("lineNo", line.lineNo());
        tree.put("itemId", line.itemId());
        tree.put("lineStatus", line.lineStatus());
        tree.put("orderedQty", line.orderedQty());
        tree.put("closedQty", line.closedQty());
        tree.put("purchaseUom", line.purchaseUom());
        tree.put("inventoryUom", line.inventoryUom());
        tree.put("conversionNumerator", line.conversionNumerator());
        tree.put("conversionDenominator", line.conversionDenominator());
        tree.put("unitPrice", line.unitPrice());
        tree.put("taxCode", line.taxCode());
        tree.put("taxRate", line.taxRate());
        tree.put("requestedDate", line.requestedDate());
        tree.put("promisedDate", line.promisedDate());
        tree.put("receiptRouting", line.receiptRouting());
        tree.put("inspectionSpecRevisionId", line.inspectionSpecRevisionId());
        tree.put("overReceiptPct", line.overReceiptPct());
        tree.put("effectiveRevision", line.effectiveRevision());
        return tree;
    }

    /** DB 에서 읽은 jsonb 문자열을 같은 규칙으로 다시 직렬화한다(해시 재현 검증용). */
    public String canonicalize(String json) {
        return write(toTree(objectMapper.readTree(json)));
    }

    public String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private Map<String, Object> requestTree(CreatePurchaseOrderRequest r) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("plantId", r.plantId());
        tree.put("supplierId", r.supplierId());
        tree.put("shipToWarehouseId", r.shipToWarehouseId());
        tree.put("currency", r.currency());
        tree.put("exchangeRate", r.exchangeRate());
        tree.put("paymentTermsCode", r.paymentTermsCode());
        tree.put("incotermsCode", r.incotermsCode());
        tree.put("incotermsPlace", r.incotermsPlace());
        tree.put("paymentTermsSnapshot", r.paymentTermsSnapshot());
        List<Object> lines = new ArrayList<>();
        for (CreatePurchaseOrderLineRequest l : r.lines()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemId", l.itemId());
            line.put("orderedQty", l.orderedQty());
            line.put("purchaseUom", l.purchaseUom());
            line.put("inventoryUom", l.inventoryUom());
            line.put("conversionNumerator", l.conversionNumerator());
            line.put("conversionDenominator", l.conversionDenominator());
            line.put("unitPrice", l.unitPrice());
            line.put("taxCode", l.taxCode());
            line.put("taxRate", l.taxRate());
            line.put("requestedDate", l.requestedDate());
            line.put("promisedDate", l.promisedDate());
            line.put("receiptRouting", l.receiptRouting());
            line.put("inspectionSpecRevisionId", l.inspectionSpecRevisionId());
            line.put("overReceiptPct", l.overReceiptPct());
            lines.add(line);
        }
        tree.put("lines", lines);
        return tree;
    }

    private Map<String, Object> revisionRequestTree(CreatePurchaseOrderRevisionRequest r) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("expectedVersion", r.expectedVersion());
        tree.put("changeReasonCode", r.changeReasonCode());
        tree.put("changeReasonDetail", r.changeReasonDetail());
        tree.put("shipToWarehouseId", r.shipToWarehouseId());
        tree.put("exchangeRate", r.exchangeRate());
        tree.put("paymentTermsCode", r.paymentTermsCode());
        tree.put("incotermsCode", r.incotermsCode());
        tree.put("incotermsPlace", r.incotermsPlace());
        tree.put("paymentTermsSnapshot", r.paymentTermsSnapshot());
        List<Object> lines = new ArrayList<>();
        for (RevisePurchaseOrderLineRequest l : r.lines()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("lineId", l.lineId());
            line.put("itemId", l.itemId());
            line.put("orderedQty", l.orderedQty());
            line.put("purchaseUom", l.purchaseUom());
            line.put("inventoryUom", l.inventoryUom());
            line.put("conversionNumerator", l.conversionNumerator());
            line.put("conversionDenominator", l.conversionDenominator());
            line.put("unitPrice", l.unitPrice());
            line.put("taxCode", l.taxCode());
            line.put("taxRate", l.taxRate());
            line.put("requestedDate", l.requestedDate());
            line.put("promisedDate", l.promisedDate());
            line.put("receiptRouting", l.receiptRouting());
            line.put("inspectionSpecRevisionId", l.inspectionSpecRevisionId());
            line.put("overReceiptPct", l.overReceiptPct());
            lines.add(line);
        }
        tree.put("lines", lines);
        tree.put("cancelLineIds", r.cancelLineIds());
        return tree;
    }

    private Object toTree(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.properties().forEach(entry -> map.put(entry.getKey(), toTree(entry.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(child -> list.add(toTree(child)));
            return list;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asString();
    }

    private String write(Object tree) {
        StringBuilder out = new StringBuilder();
        append(out, tree);
        return out.toString();
    }

    private void append(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> {
                if (v != null) {
                    sorted.put(String.valueOf(k), v);
                }
            });
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(objectMapper.writeValueAsString(entry.getKey())).append(':');
                append(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Collection<?> collection) {
            out.append('[');
            boolean first = true;
            for (Object element : collection) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                append(out, element);
            }
            out.append(']');
        } else if (value instanceof Number number) {
            out.append(objectMapper.writeValueAsString(plain(number)));
        } else if (value instanceof Boolean bool) {
            out.append(bool);
        } else {
            out.append(objectMapper.writeValueAsString(value.toString()));
        }
    }

    private String plain(Number number) {
        BigDecimal decimal = number instanceof BigDecimal bd ? bd : new BigDecimal(number.toString());
        return decimal.signum() == 0 ? "0" : decimal.stripTrailingZeros().toPlainString();
    }
}
