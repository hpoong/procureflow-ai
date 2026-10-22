-- 개정 단위 명령(개정 초안 작성 등)의 멱등 재생을 위해 명령이 만든 개정을 가리킨다.
ALTER TABLE purchase_order_command
    ADD COLUMN po_revision_id uuid REFERENCES purchase_order_revision (id) ON DELETE RESTRICT;

COMMENT ON COLUMN purchase_order_command.po_revision_id IS
    '명령이 생성·대상으로 한 발주 개정 ID. 개정 단위 명령의 재생에 사용. 개정과 무관한 명령은 NULL.';
