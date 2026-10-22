package com.hopoong.procureflow.api.masterdata.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hopoong.procureflow.api.masterdata.model.CreateItemRequest;
import com.hopoong.procureflow.api.masterdata.model.CreatePlantRequest;
import com.hopoong.procureflow.api.masterdata.model.CreateSupplierRequest;
import com.hopoong.procureflow.api.masterdata.model.CreateWarehouseRequest;
import com.hopoong.procureflow.api.masterdata.model.ItemResponse;
import com.hopoong.procureflow.api.masterdata.model.PlantResponse;
import com.hopoong.procureflow.api.masterdata.model.SupplierResponse;
import com.hopoong.procureflow.api.masterdata.model.WarehouseResponse;
import com.hopoong.procureflow.api.masterdata.service.MasterDataService;
import com.hopoong.procureflow.common.auth.RequestActor;
import com.hopoong.procureflow.common.error.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Master Data", description = "기준정보 (사업장·창고·공급사·품목)")
@RestController
@RequestMapping("/v1")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @Operation(summary = "사업장 등록")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @ApiResponse(responseCode = "201", description = "생성됨")
    @ApiResponse(responseCode = "400", description = "요청 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "조직 내 사업장 코드 중복", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/plants")
    @ResponseStatus(HttpStatus.CREATED)
    public PlantResponse createPlant(@Parameter(hidden = true) RequestActor actor,
            @Valid @RequestBody CreatePlantRequest request) {
        return masterDataService.createPlant(actor, request);
    }

    @Operation(summary = "사업장 목록", description = "요청자 조직의 사업장을 코드순으로 돌려준다.")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @GetMapping("/plants")
    public List<PlantResponse> listPlants(@Parameter(hidden = true) RequestActor actor) {
        return masterDataService.listPlants(actor);
    }

    @Operation(summary = "창고 등록", description = "소속 사업장은 요청자 조직에 있어야 한다.")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @ApiResponse(responseCode = "201", description = "생성됨")
    @ApiResponse(responseCode = "400", description = "요청 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "조직 내 창고 코드 중복", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "소속 사업장 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse createWarehouse(@Parameter(hidden = true) RequestActor actor,
            @Valid @RequestBody CreateWarehouseRequest request) {
        return masterDataService.createWarehouse(actor, request);
    }

    @Operation(summary = "창고 목록", description = "요청자 조직의 창고를 코드순으로 돌려준다.")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @GetMapping("/warehouses")
    public List<WarehouseResponse> listWarehouses(@Parameter(hidden = true) RequestActor actor) {
        return masterDataService.listWarehouses(actor);
    }

    @Operation(summary = "공급사 등록")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @ApiResponse(responseCode = "201", description = "생성됨")
    @ApiResponse(responseCode = "400", description = "요청 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "조직 내 공급사 코드 중복", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse createSupplier(@Parameter(hidden = true) RequestActor actor,
            @Valid @RequestBody CreateSupplierRequest request) {
        return masterDataService.createSupplier(actor, request);
    }

    @Operation(summary = "공급사 목록", description = "요청자 조직의 공급사를 코드순으로 돌려준다.")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @GetMapping("/suppliers")
    public List<SupplierResponse> listSuppliers(@Parameter(hidden = true) RequestActor actor) {
        return masterDataService.listSuppliers(actor);
    }

    @Operation(summary = "품목 등록")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @ApiResponse(responseCode = "201", description = "생성됨")
    @ApiResponse(responseCode = "400", description = "요청 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "조직 내 품목 코드 중복", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse createItem(@Parameter(hidden = true) RequestActor actor,
            @Valid @RequestBody CreateItemRequest request) {
        return masterDataService.createItem(actor, request);
    }

    @Operation(summary = "품목 목록", description = "요청자 조직의 품목을 코드순으로 돌려준다.")
    @Parameter(name = "X-Org-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string", maxLength = 64))
    @GetMapping("/items")
    public List<ItemResponse> listItems(@Parameter(hidden = true) RequestActor actor) {
        return masterDataService.listItems(actor);
    }
}
