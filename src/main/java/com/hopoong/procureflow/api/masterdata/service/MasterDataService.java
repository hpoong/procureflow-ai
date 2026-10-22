package com.hopoong.procureflow.api.masterdata.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hopoong.procureflow.api.masterdata.model.CreateItemRequest;
import com.hopoong.procureflow.api.masterdata.model.CreatePlantRequest;
import com.hopoong.procureflow.api.masterdata.model.CreateSupplierRequest;
import com.hopoong.procureflow.api.masterdata.model.CreateWarehouseRequest;
import com.hopoong.procureflow.api.masterdata.model.ItemResponse;
import com.hopoong.procureflow.api.masterdata.model.PlantResponse;
import com.hopoong.procureflow.api.masterdata.model.SupplierResponse;
import com.hopoong.procureflow.api.masterdata.model.WarehouseResponse;
import com.hopoong.procureflow.common.auth.RequestActor;
import com.hopoong.procureflow.common.error.BusinessException;
import com.hopoong.procureflow.domain.entity.masterdata.Item;
import com.hopoong.procureflow.domain.entity.masterdata.Plant;
import com.hopoong.procureflow.domain.entity.masterdata.Supplier;
import com.hopoong.procureflow.domain.entity.masterdata.Warehouse;
import com.hopoong.procureflow.domain.repository.masterdata.ItemRepository;
import com.hopoong.procureflow.domain.repository.masterdata.PlantRepository;
import com.hopoong.procureflow.domain.repository.masterdata.SupplierRepository;
import com.hopoong.procureflow.domain.repository.masterdata.WarehouseRepository;

import lombok.extern.slf4j.Slf4j;

/** 기준정보 등록·목록. 코드는 조직 안에서 유일하고, 모든 조회는 요청자 조직으로 한정한다. */
@Slf4j
@Service
public class MasterDataService {

    private final PlantRepository plantRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final Clock clock;

    public MasterDataService(PlantRepository plantRepository, WarehouseRepository warehouseRepository,
            SupplierRepository supplierRepository, ItemRepository itemRepository, Clock clock) {
        this.plantRepository = plantRepository;
        this.warehouseRepository = warehouseRepository;
        this.supplierRepository = supplierRepository;
        this.itemRepository = itemRepository;
        this.clock = clock;
    }

    @Transactional
    public PlantResponse createPlant(RequestActor actor, CreatePlantRequest request) {
        String code = request.plantCode().strip();
        if (plantRepository.existsByOrgIdAndPlantCode(actor.orgId(), code)) {
            throw new BusinessException(MasterDataErrorCode.PLANT_CODE_DUPLICATED);
        }
        Plant plant = Plant.create(UUID.randomUUID(), actor.orgId(), code, request.name().strip(), request.status(),
                actor.userId(), now());
        Plant saved = saveUnique(plant, plantRepository::saveAndFlush, MasterDataErrorCode.PLANT_CODE_DUPLICATED);
        log.info("plant created plantId={}", saved.getId());
        return PlantResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PlantResponse> listPlants(RequestActor actor) {
        return plantRepository.findAllByOrgIdOrderByPlantCode(actor.orgId()).stream().map(PlantResponse::from).toList();
    }

    @Transactional
    public WarehouseResponse createWarehouse(RequestActor actor, CreateWarehouseRequest request) {
        if (plantRepository.findByIdAndOrgId(request.plantId(), actor.orgId()).isEmpty()) {
            throw new BusinessException(MasterDataErrorCode.PLANT_NOT_FOUND);
        }
        String code = request.warehouseCode().strip();
        if (warehouseRepository.existsByOrgIdAndWarehouseCode(actor.orgId(), code)) {
            throw new BusinessException(MasterDataErrorCode.WAREHOUSE_CODE_DUPLICATED);
        }
        Warehouse warehouse = Warehouse.create(UUID.randomUUID(), actor.orgId(), request.plantId(), code,
                request.name().strip(), request.status(), actor.userId(), now());
        Warehouse saved = saveUnique(warehouse, warehouseRepository::saveAndFlush,
                MasterDataErrorCode.WAREHOUSE_CODE_DUPLICATED);
        log.info("warehouse created warehouseId={} plantId={}", saved.getId(), saved.getPlantId());
        return WarehouseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> listWarehouses(RequestActor actor) {
        return warehouseRepository.findAllByOrgIdOrderByWarehouseCode(actor.orgId()).stream()
                .map(WarehouseResponse::from).toList();
    }

    @Transactional
    public SupplierResponse createSupplier(RequestActor actor, CreateSupplierRequest request) {
        String code = request.supplierCode().strip();
        if (supplierRepository.existsByOrgIdAndSupplierCode(actor.orgId(), code)) {
            throw new BusinessException(MasterDataErrorCode.SUPPLIER_CODE_DUPLICATED);
        }
        Supplier supplier = Supplier.create(UUID.randomUUID(), actor.orgId(), code, request.name().strip(),
                request.status(), actor.userId(), now());
        Supplier saved = saveUnique(supplier, supplierRepository::saveAndFlush,
                MasterDataErrorCode.SUPPLIER_CODE_DUPLICATED);
        log.info("supplier created supplierId={}", saved.getId());
        return SupplierResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> listSuppliers(RequestActor actor) {
        return supplierRepository.findAllByOrgIdOrderBySupplierCode(actor.orgId()).stream()
                .map(SupplierResponse::from).toList();
    }

    @Transactional
    public ItemResponse createItem(RequestActor actor, CreateItemRequest request) {
        String code = request.itemCode().strip();
        if (itemRepository.existsByOrgIdAndItemCode(actor.orgId(), code)) {
            throw new BusinessException(MasterDataErrorCode.ITEM_CODE_DUPLICATED);
        }
        Item item = Item.create(UUID.randomUUID(), actor.orgId(), code, request.name().strip(), request.status(),
                request.purchaseEnabled(), actor.userId(), now());
        Item saved = saveUnique(item, itemRepository::saveAndFlush, MasterDataErrorCode.ITEM_CODE_DUPLICATED);
        log.info("item created itemId={}", saved.getId());
        return ItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> listItems(RequestActor actor) {
        return itemRepository.findAllByOrgIdOrderByItemCode(actor.orgId()).stream().map(ItemResponse::from).toList();
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    /** 존재 확인과 INSERT 사이에 같은 코드가 먼저 들어오면 unique 제약 위반을 중복 오류로 돌려준다. */
    private <T> T saveUnique(T entity, UnaryOperator<T> save, MasterDataErrorCode duplicated) {
        try {
            return save.apply(entity);
        } catch (DataIntegrityViolationException e) {
            log.debug("master data unique violation", e);
            throw new BusinessException(duplicated);
        }
    }
}
