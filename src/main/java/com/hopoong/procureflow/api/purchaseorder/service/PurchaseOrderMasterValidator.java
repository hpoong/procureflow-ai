package com.hopoong.procureflow.api.purchaseorder.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        validateWarehouse(orgId, plantId, warehouseId);
        boolean supplierUsable = supplierRepository.findByIdAndOrgId(supplierId, orgId).filter(s -> s.isActive())
                .isPresent();
        if (!supplierUsable) {
            throw new BusinessException(PurchaseOrderErrorCode.SUPPLIER_NOT_AVAILABLE);
        }
        validateItems(orgId, lineItemIds);
    }

    /**
     * 개정에서 바뀌는 마스터만 검사한다. 공급사·사업장·기존 라인 품목은 개정 불가라 다시 보지 않는다.
     *
     * @param changedWarehouseId 현재와 다를 때만 값, 같으면 null
     * @param lineItemIds 요청 lines 순서. 신규 라인만 품목 ID, 기존 라인은 null (message lineNo 는 요청 순번)
     */
    public void validateRevision(String orgId, UUID plantId, UUID changedWarehouseId, List<UUID> lineItemIds) {
        if (changedWarehouseId != null) {
            validateWarehouse(orgId, plantId, changedWarehouseId);
        }
        validateItems(orgId, lineItemIds);
    }

    private void validateWarehouse(String orgId, UUID plantId, UUID warehouseId) {
        boolean warehouseUsable = warehouseRepository.findByIdAndOrgId(warehouseId, orgId)
                .filter(Warehouse::isActive)
                .filter(w -> plantId.equals(w.getPlantId()))
                .isPresent();
        if (!warehouseUsable) {
            throw new BusinessException(PurchaseOrderErrorCode.WAREHOUSE_NOT_AVAILABLE);
        }
    }

    private void validateItems(String orgId, List<UUID> lineItemIds) {
        Set<UUID> distinct = lineItemIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return;
        }
        Map<UUID, Item> items = new HashMap<>();
        itemRepository.findAllByOrgIdAndIdIn(orgId, distinct).forEach(item -> items.put(item.getId(), item));
        List<Integer> invalidLineNos = new ArrayList<>();
        for (int i = 0; i < lineItemIds.size(); i++) {
            if (lineItemIds.get(i) == null) {
                continue;
            }
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
