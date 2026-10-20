package com.hopoong.procureflow.api.purchaseorder.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrder;
import com.hopoong.procureflow.domain.entity.purchaseorder.PurchaseOrderLine;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderLineRequest;
import com.hopoong.procureflow.api.purchaseorder.model.CreatePurchaseOrderRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 요청 해시와 after_snapshot 의 canonical 직렬화를 한 곳에서 만든다.
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

    public String afterSnapshot(PurchaseOrder po, List<PurchaseOrderLine> lines) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("id", po.getId());
        header.put("orgId", po.getOrgId());
        header.put("poNumber", po.getPoNumber());
        header.put("plantId", po.getPlantId());
        header.put("supplierId", po.getSupplierId());
        header.put("shipToWarehouseId", po.getShipToWarehouseId());
        header.put("currency", po.getCurrency());
        header.put("currencyScale", po.getCurrencyScale());
        header.put("exchangeRate", po.getExchangeRate());
        header.put("paymentTermsCode", po.getPaymentTermsCode());
        header.put("paymentTermsSnapshot", po.getPaymentTermsSnapshot() == null ? null
                : toTree(objectMapper.readTree(po.getPaymentTermsSnapshot())));
        header.put("incotermsCode", po.getIncotermsCode());
        header.put("incotermsPlace", po.getIncotermsPlace());
        header.put("status", po.getStatus());
        header.put("currentRevision", po.getCurrentRevision());
        header.put("createdBy", po.getCreatedBy());
        header.put("createdAt", po.getCreatedAt());

        List<Object> lineTrees = new ArrayList<>();
        for (PurchaseOrderLine line : lines) {
            Map<String, Object> tree = new LinkedHashMap<>();
            tree.put("id", line.getId());
            tree.put("lineNo", line.getLineNo());
            tree.put("itemId", line.getItemId());
            tree.put("lineStatus", line.getLineStatus());
            tree.put("orderedQty", line.getOrderedQty());
            tree.put("closedQty", line.getClosedQty());
            tree.put("purchaseUom", line.getPurchaseUom());
            tree.put("inventoryUom", line.getInventoryUom());
            tree.put("conversionNumerator", line.getConversionNumerator());
            tree.put("conversionDenominator", line.getConversionDenominator());
            tree.put("unitPrice", line.getUnitPrice());
            tree.put("taxCode", line.getTaxCode());
            tree.put("taxRate", line.getTaxRate());
            tree.put("requestedDate", line.getRequestedDate());
            tree.put("promisedDate", line.getPromisedDate());
            tree.put("receiptRouting", line.getReceiptRouting());
            tree.put("inspectionSpecRevisionId", line.getInspectionSpecRevisionId());
            tree.put("overReceiptPct", line.getOverReceiptPct());
            tree.put("effectiveRevision", line.getEffectiveRevision());
            lineTrees.add(tree);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("header", header);
        root.put("lines", lineTrees);
        return write(root);
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
