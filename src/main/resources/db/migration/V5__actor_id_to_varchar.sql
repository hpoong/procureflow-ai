-- 조직·사용자 식별자를 uuid 에서 문자열(varchar(64))로 바꾼다. 엔티티 자체 PK·참조 ID 는 uuid 를 유지한다.
-- org_id 를 포함한 복합 FK 는 양쪽 타입이 같아야 하므로 지우고 바꾼 뒤 다시 건다.
ALTER TABLE purchase_order_revision DROP CONSTRAINT fk_po_revision_order;
ALTER TABLE warehouse DROP CONSTRAINT fk_warehouse_plant_org;

ALTER TABLE purchase_order
    ALTER COLUMN org_id TYPE varchar(64) USING org_id::text,
    ALTER COLUMN created_by TYPE varchar(64) USING created_by::text;

ALTER TABLE purchase_order_revision
    ALTER COLUMN org_id TYPE varchar(64) USING org_id::text,
    ALTER COLUMN created_by TYPE varchar(64) USING created_by::text;

ALTER TABLE po_approval
    ALTER COLUMN approver_id TYPE varchar(64) USING approver_id::text,
    ALTER COLUMN delegated_from_id TYPE varchar(64) USING delegated_from_id::text;

ALTER TABLE po_line_close
    ALTER COLUMN actor_id TYPE varchar(64) USING actor_id::text;

ALTER TABLE purchase_order_attachment
    ALTER COLUMN uploaded_by TYPE varchar(64) USING uploaded_by::text;

ALTER TABLE purchase_order_audit_log
    ALTER COLUMN actor_id TYPE varchar(64) USING actor_id::text;

ALTER TABLE purchase_order_command
    ALTER COLUMN org_id TYPE varchar(64) USING org_id::text;

ALTER TABLE outbox_event
    ALTER COLUMN org_id TYPE varchar(64) USING org_id::text;

ALTER TABLE plant
    ALTER COLUMN org_id TYPE varchar(64) USING org_id::text,
    ALTER COLUMN created_by TYPE varchar(64) USING created_by::text,
    ALTER COLUMN updated_by TYPE varchar(64) USING updated_by::text;

ALTER TABLE warehouse
    ALTER COLUMN org_id TYPE varchar(64) USING org_id::text,
    ALTER COLUMN created_by TYPE varchar(64) USING created_by::text,
    ALTER COLUMN updated_by TYPE varchar(64) USING updated_by::text;

ALTER TABLE supplier
    ALTER COLUMN org_id TYPE varchar(64) USING org_id::text,
    ALTER COLUMN created_by TYPE varchar(64) USING created_by::text,
    ALTER COLUMN updated_by TYPE varchar(64) USING updated_by::text;

ALTER TABLE item
    ALTER COLUMN org_id TYPE varchar(64) USING org_id::text,
    ALTER COLUMN created_by TYPE varchar(64) USING created_by::text,
    ALTER COLUMN updated_by TYPE varchar(64) USING updated_by::text;

ALTER TABLE purchase_order_revision ADD CONSTRAINT fk_po_revision_order FOREIGN KEY (po_id, org_id, po_number)
    REFERENCES purchase_order (id, org_id, po_number) ON DELETE RESTRICT;
ALTER TABLE warehouse ADD CONSTRAINT fk_warehouse_plant_org FOREIGN KEY (plant_id, org_id)
    REFERENCES plant (id, org_id) ON DELETE RESTRICT;

COMMENT ON COLUMN outbox_event.org_id IS '이벤트의 조직/테넌트 식별자. 모든 소비자 조회·멱등 처리의 권한 경계.';
