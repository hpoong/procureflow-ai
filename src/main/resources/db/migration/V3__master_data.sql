-- 발주 생성 검증에 필요한 최소 기준정보. 관리 API 없이 SQL 로 시드한다.
CREATE TABLE plant (
    id uuid PRIMARY KEY,
    org_id uuid NOT NULL,
    plant_code varchar(32) NOT NULL,
    name varchar(255) NOT NULL,
    status varchar(20) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by uuid NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_plant_org_code UNIQUE (org_id, plant_code),
    CONSTRAINT uq_plant_id_org UNIQUE (id, org_id),
    CONSTRAINT ck_plant_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE warehouse (
    id uuid PRIMARY KEY,
    org_id uuid NOT NULL,
    plant_id uuid NOT NULL,
    warehouse_code varchar(32) NOT NULL,
    name varchar(255) NOT NULL,
    status varchar(20) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by uuid NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_warehouse_org_code UNIQUE (org_id, warehouse_code),
    CONSTRAINT uq_warehouse_id_org UNIQUE (id, org_id),
    CONSTRAINT fk_warehouse_plant_org FOREIGN KEY (plant_id, org_id)
        REFERENCES plant (id, org_id) ON DELETE RESTRICT,
    CONSTRAINT ck_warehouse_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE supplier (
    id uuid PRIMARY KEY,
    org_id uuid NOT NULL,
    supplier_code varchar(64) NOT NULL,
    name varchar(255) NOT NULL,
    status varchar(20) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by uuid NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_supplier_org_code UNIQUE (org_id, supplier_code),
    CONSTRAINT uq_supplier_id_org UNIQUE (id, org_id),
    CONSTRAINT ck_supplier_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE item (
    id uuid PRIMARY KEY,
    org_id uuid NOT NULL,
    item_code varchar(64) NOT NULL,
    name varchar(255) NOT NULL,
    status varchar(20) NOT NULL,
    purchase_enabled boolean NOT NULL DEFAULT false,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_by uuid NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_item_org_code UNIQUE (org_id, item_code),
    CONSTRAINT uq_item_id_org UNIQUE (id, org_id),
    CONSTRAINT ck_item_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_warehouse_plant ON warehouse (plant_id);

COMMENT ON TABLE plant IS '사업장 기준정보. 발주 생성 시 조직 일치·활성 여부를 검증한다.';
COMMENT ON TABLE warehouse IS '창고 기준정보. 소속 사업장과 같은 조직이어야 하며 발주 입고지로 쓴다.';
COMMENT ON TABLE supplier IS '공급사 기준정보. 발주 생성 시 조직 일치·활성 여부를 검증한다.';
COMMENT ON TABLE item IS '품목 기준정보. ACTIVE 이고 purchase_enabled 인 품목만 발주할 수 있다.';
COMMENT ON COLUMN plant.status IS 'ACTIVE 또는 INACTIVE';
COMMENT ON COLUMN warehouse.status IS 'ACTIVE 또는 INACTIVE';
COMMENT ON COLUMN supplier.status IS 'ACTIVE 또는 INACTIVE';
COMMENT ON COLUMN item.status IS 'ACTIVE 또는 INACTIVE';
COMMENT ON COLUMN item.purchase_enabled IS '구매 가능 여부. false 이면 발주할 수 없다.';
