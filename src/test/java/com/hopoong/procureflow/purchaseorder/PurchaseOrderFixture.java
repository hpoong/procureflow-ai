package com.hopoong.procureflow.purchaseorder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hopoong.procureflow.masterdata.domain.Item;
import com.hopoong.procureflow.masterdata.domain.MasterStatus;
import com.hopoong.procureflow.masterdata.domain.Plant;
import com.hopoong.procureflow.masterdata.domain.Supplier;
import com.hopoong.procureflow.masterdata.domain.Warehouse;
import com.hopoong.procureflow.masterdata.repository.ItemRepository;
import com.hopoong.procureflow.masterdata.repository.PlantRepository;
import com.hopoong.procureflow.masterdata.repository.SupplierRepository;
import com.hopoong.procureflow.masterdata.repository.WarehouseRepository;

/** 테스트마다 새 조직과 그 조직의 마스터를 만든다. */
final class PurchaseOrderFixture {

    final UUID orgId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID plantId = UUID.randomUUID();
    final UUID warehouseId = UUID.randomUUID();
    final UUID supplierId = UUID.randomUUID();
    final UUID item1Id = UUID.randomUUID();
    final UUID item2Id = UUID.randomUUID();

    PurchaseOrderFixture(PlantRepository plants, WarehouseRepository warehouses, SupplierRepository suppliers,
            ItemRepository items) {
        Instant now = Instant.now();
        String code = orgId.toString().substring(0, 8);
        plants.save(Plant.create(plantId, orgId, "p-" + code, "Plant", MasterStatus.ACTIVE, userId, now));
        warehouses.save(Warehouse.create(warehouseId, orgId, plantId, "w-" + code, "WH", MasterStatus.ACTIVE, userId, now));
        suppliers.save(Supplier.create(supplierId, orgId, "s-" + code, "Supplier", MasterStatus.ACTIVE, userId, now));
        items.save(Item.create(item1Id, orgId, "i1-" + code, "Item1", MasterStatus.ACTIVE, true, userId, now));
        items.save(Item.create(item2Id, orgId, "i2-" + code, "Item2", MasterStatus.ACTIVE, true, userId, now));
    }

    Map<String, Object> validRequest() {
        return validRequest(item1Id, item2Id);
    }

    Map<String, Object> validRequest(UUID firstItem, UUID secondItem) {
        return new java.util.LinkedHashMap<>(Map.of(
                "plantId", plantId.toString(),
                "supplierId", supplierId.toString(),
                "shipToWarehouseId", warehouseId.toString(),
                "currency", "KRW",
                "lines", List.of(
                        line(firstItem, "STANDARD", null),
                        line(secondItem, "INSPECTION", UUID.randomUUID().toString()))));
    }

    static Map<String, Object> line(UUID itemId, String routing, String specId) {
        Map<String, Object> line = new java.util.LinkedHashMap<>();
        line.put("itemId", itemId.toString());
        line.put("orderedQty", "10");
        line.put("purchaseUom", "EA");
        line.put("inventoryUom", "EA");
        line.put("conversionNumerator", 1);
        line.put("conversionDenominator", 1);
        line.put("unitPrice", "1500.5");
        line.put("taxCode", "VAT10");
        line.put("taxRate", "0.1");
        line.put("receiptRouting", routing);
        if (specId != null) {
            line.put("inspectionSpecRevisionId", specId);
        }
        return line;
    }
}
