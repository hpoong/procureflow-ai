package com.hopoong.procureflow.api.purchaseorder.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.hopoong.procureflow.common.error.BusinessException;
import com.hopoong.procureflow.domain.entity.masterdata.Item;
import com.hopoong.procureflow.domain.entity.masterdata.Warehouse;
import com.hopoong.procureflow.domain.repository.masterdata.ItemRepository;
import com.hopoong.procureflow.domain.repository.masterdata.PlantRepository;
import com.hopoong.procureflow.domain.repository.masterdata.SupplierRepository;
import com.hopoong.procureflow.domain.repository.masterdata.WarehouseRepository;

@Component
public class PurchaseOrderMasterValidator {

    private final PlantRepository plantRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;

    public PurchaseOrderMasterValidator(PlantRepository plantRepository, WarehouseRepository warehouseRepository,
            SupplierRepository supplierRepository, ItemRepository itemRepository) {
        this.plantRepository = plantRepository;
        this.warehouseRepository = warehouseRepository;
        this.supplierRepository = supplierRepository;
        this.itemRepository = itemRepository;
    }

    /** 사업장 → 창고 → 공급사 → 품목 순으로 검사한다. 조회는 모두 조직 조건을 포함한다. */
    public void validate(String orgId, UUID plantId, UUID warehouseId, UUID supplierId, List<UUID> lineItemIds) {
        boolean plantUsable = plantRepository.findByIdAndOrgId(plantId, orgId).filter(p -> p.isActive()).isPresent();
        if (!plantUsable) {
            throw new BusinessException(PurchaseOrderErrorCode.PLANT_NOT_AVAILABLE);
        }
        boolean warehouseUsable = warehouseRepository.findByIdAndOrgId(warehouseId, orgId)
                .filter(Warehouse::isActive)
                .filter(w -> plantId.equals(w.getPlantId()))
                .isPresent();
        if (!warehouseUsable) {
            throw new BusinessException(PurchaseOrderErrorCode.WAREHOUSE_NOT_AVAILABLE);
        }
        boolean supplierUsable = supplierRepository.findByIdAndOrgId(supplierId, orgId).filter(s -> s.isActive())
                .isPresent();
        if (!supplierUsable) {
            throw new BusinessException(PurchaseOrderErrorCode.SUPPLIER_NOT_AVAILABLE);
        }
        validateItems(orgId, lineItemIds);
    }

    private void validateItems(String orgId, List<UUID> lineItemIds) {
        Set<UUID> distinct = Set.copyOf(lineItemIds);
        Map<UUID, Item> items = new HashMap<>();
        itemRepository.findAllByOrgIdAndIdIn(orgId, distinct).forEach(item -> items.put(item.getId(), item));
        List<Integer> invalidLineNos = new ArrayList<>();
        for (int i = 0; i < lineItemIds.size(); i++) {
            Item item = items.get(lineItemIds.get(i));
            if (item == null || !item.isPurchasable()) {
                invalidLineNos.add(i + 1);
            }
        }
        if (!invalidLineNos.isEmpty()) {
            String lineNos = invalidLineNos.stream().map(String::valueOf).collect(Collectors.joining(", "));
            throw new BusinessException(PurchaseOrderErrorCode.ITEM_NOT_PURCHASABLE,
                    PurchaseOrderErrorCode.ITEM_NOT_PURCHASABLE.message() + " lineNo=[" + lineNos + "]");
        }
    }
}
