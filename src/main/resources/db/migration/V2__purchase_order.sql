-- 발주 집합 루트: 재고 수량은 직접 보관하지 않는다.
CREATE TABLE purchase_order (
    id uuid PRIMARY KEY,
    org_id uuid NOT NULL,
    plant_id uuid NOT NULL,
    po_number varchar(64) NOT NULL,
    current_revision integer NOT NULL DEFAULT 1 CHECK (current_revision > 0),
    supplier_id uuid NOT NULL,
    currency char(3) NOT NULL,
    currency_scale smallint NOT NULL CHECK (currency_scale BETWEEN 0 AND 6),
    exchange_rate numeric(20, 10),
    payment_terms_code varchar(50),
    payment_terms_snapshot jsonb,
    incoterms_code varchar(10),
    incoterms_place varchar(255),
    ship_to_warehouse_id uuid NOT NULL,
    status varchar(30) NOT NULL,
    ordered_at timestamptz,
    approved_at timestamptz,
    sent_at timestamptz,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_purchase_order_org_number UNIQUE (org_id, po_number),
    CONSTRAINT uq_purchase_order_id_org_number UNIQUE (id, org_id, po_number),
    CONSTRAINT ck_purchase_order_status CHECK (status IN
        ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SENT',
         'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_purchase_order_exchange_rate CHECK (exchange_rate IS NULL OR exchange_rate > 0),
    CONSTRAINT ck_purchase_order_payment_terms_json CHECK
        (payment_terms_snapshot IS NULL OR jsonb_typeof(payment_terms_snapshot) = 'object')
);

-- 발주 개정의 불변 계약 스냅샷과 승인 상태를 보관한다.
CREATE TABLE purchase_order_revision (
    id uuid PRIMARY KEY,
    po_id uuid NOT NULL,
    org_id uuid NOT NULL,
    po_number varchar(64) NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    revision_status varchar(30) NOT NULL,
    approval_policy_version varchar(100) NOT NULL,
    change_reason_code varchar(50),
    change_reason_detail varchar(1000),
    before_snapshot jsonb,
    after_snapshot jsonb NOT NULL,
    content_sha256 char(64) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    submitted_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    CONSTRAINT fk_po_revision_order FOREIGN KEY (po_id, org_id, po_number)
        REFERENCES purchase_order (id, org_id, po_number) ON DELETE RESTRICT,
    CONSTRAINT uq_po_revision_org_number_revision UNIQUE (org_id, po_number, revision),
    CONSTRAINT uq_po_revision_order_revision UNIQUE (po_id, revision),
    CONSTRAINT ck_po_revision_status CHECK (revision_status IN
        ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_po_revision_snapshot CHECK (jsonb_typeof(after_snapshot) = 'object'),
    CONSTRAINT ck_po_revision_before_snapshot CHECK
        (before_snapshot IS NULL OR jsonb_typeof(before_snapshot) = 'object'),
    CONSTRAINT ck_po_revision_sha256 CHECK (content_sha256 ~ '^[0-9a-f]{64}$')
);

-- 현재 유효 발주 개정의 라인 투영이며 received_qty는 GRN 원장에서 산출한다.
CREATE TABLE purchase_order_line (
    id uuid PRIMARY KEY,
    po_id uuid NOT NULL REFERENCES purchase_order (id) ON DELETE RESTRICT,
    line_no integer NOT NULL CHECK (line_no > 0),
    item_id uuid NOT NULL,
    line_status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    ordered_qty numeric(19, 6) NOT NULL CHECK (ordered_qty > 0),
    closed_qty numeric(19, 6) NOT NULL DEFAULT 0 CHECK (closed_qty >= 0),
    purchase_uom varchar(20) NOT NULL,
    inventory_uom varchar(20) NOT NULL,
    conversion_numerator bigint NOT NULL CHECK (conversion_numerator > 0),
    conversion_denominator bigint NOT NULL CHECK (conversion_denominator > 0),
    unit_price numeric(19, 6) NOT NULL CHECK (unit_price >= 0),
    tax_code varchar(50) NOT NULL,
    tax_rate numeric(9, 6) NOT NULL CHECK (tax_rate >= 0 AND tax_rate <= 1),
    requested_date date,
    promised_date date,
    receipt_routing varchar(20) NOT NULL,
    inspection_spec_revision_id uuid,
    over_receipt_pct numeric(5, 2) NOT NULL DEFAULT 0 CHECK (over_receipt_pct BETWEEN 0 AND 100),
    effective_revision integer NOT NULL CHECK (effective_revision > 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_purchase_order_line_number UNIQUE (po_id, line_no),
    CONSTRAINT ck_purchase_order_line_status CHECK (line_status IN ('ACTIVE', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_purchase_order_line_closed_qty CHECK (closed_qty <= ordered_qty),
    CONSTRAINT ck_purchase_order_line_routing CHECK (receipt_routing IN ('DIRECT', 'STANDARD', 'INSPECTION')),
    CONSTRAINT ck_purchase_order_line_inspection_spec CHECK
        ((receipt_routing = 'INSPECTION' AND inspection_spec_revision_id IS NOT NULL)
         OR (receipt_routing IN ('DIRECT', 'STANDARD') AND inspection_spec_revision_id IS NULL)),
    CONSTRAINT fk_purchase_order_line_revision FOREIGN KEY (po_id, effective_revision)
        REFERENCES purchase_order_revision (po_id, revision) ON DELETE RESTRICT
);

-- 발주 개정별 결재 단계, 결재자, 판정, 전결 원본, 정책 규칙 snapshot을 보관한다.
CREATE TABLE po_approval (
    id uuid PRIMARY KEY,
    po_revision_id uuid NOT NULL REFERENCES purchase_order_revision (id) ON DELETE RESTRICT,
    step_no integer NOT NULL CHECK (step_no > 0),
    approver_id uuid NOT NULL,
    delegated_from_id uuid,
    decision varchar(20) NOT NULL DEFAULT 'PENDING',
    approval_rule_snapshot jsonb NOT NULL,
    decided_at timestamptz,
    comment varchar(2000),
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_po_approval_step_approver UNIQUE (po_revision_id, step_no, approver_id),
    CONSTRAINT ck_po_approval_decision CHECK (decision IN ('PENDING', 'APPROVED', 'REJECTED', 'SKIPPED')),
    CONSTRAINT ck_po_approval_rule_json CHECK (jsonb_typeof(approval_rule_snapshot) = 'object'),
    CONSTRAINT ck_po_approval_decision_time CHECK
        ((decision = 'PENDING' AND decided_at IS NULL) OR (decision <> 'PENDING' AND decided_at IS NOT NULL))
);

-- 미입고 잔량 종결의 수량·사유·행위자·멱등 키 이력이다.
CREATE TABLE po_line_close (
    id uuid PRIMARY KEY,
    po_line_id uuid NOT NULL REFERENCES purchase_order_line (id) ON DELETE RESTRICT,
    close_qty numeric(19, 6) NOT NULL CHECK (close_qty > 0),
    reason_code varchar(50) NOT NULL,
    reason_detail varchar(1000),
    actor_id uuid NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    closed_at timestamptz NOT NULL,
    CONSTRAINT uq_po_line_close_request UNIQUE (po_line_id, idempotency_key)
);

-- 공급사 송부 시도의 채널·멱등 키·외부 메시지 식별자·응답 이력이다.
CREATE TABLE po_transmission (
    id uuid PRIMARY KEY,
    po_revision_id uuid NOT NULL REFERENCES purchase_order_revision (id) ON DELETE RESTRICT,
    channel varchar(20) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    status varchar(20) NOT NULL,
    external_message_id varchar(255),
    response_payload jsonb,
    requested_at timestamptz NOT NULL,
    sent_at timestamptz,
    failed_at timestamptz,
    CONSTRAINT uq_po_transmission_request UNIQUE (po_revision_id, idempotency_key),
    CONSTRAINT ck_po_transmission_channel CHECK (channel IN ('EDI', 'EMAIL', 'PORTAL', 'API')),
    CONSTRAINT ck_po_transmission_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_po_transmission_response_json CHECK
        (response_payload IS NULL OR jsonb_typeof(response_payload) IN ('object', 'array'))
);

-- 공급사가 회신한 발주 확인의 상태·참조 번호·응답 payload 이력이다.
CREATE TABLE po_supplier_ack (
    id uuid PRIMARY KEY,
    po_revision_id uuid NOT NULL REFERENCES purchase_order_revision (id) ON DELETE RESTRICT,
    ack_status varchar(20) NOT NULL,
    supplier_reference varchar(255),
    response_payload jsonb,
    acknowledged_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    CONSTRAINT uq_po_supplier_ack_reference UNIQUE (po_revision_id, supplier_reference),
    CONSTRAINT ck_po_supplier_ack_status CHECK (ack_status IN ('ACCEPTED', 'REJECTED', 'CHANGED', 'PENDING')),
    CONSTRAINT ck_po_supplier_ack_response_json CHECK
        (response_payload IS NULL OR jsonb_typeof(response_payload) IN ('object', 'array'))
);

-- 견적서·계약서·규격서의 저장소 객체 키, 해시, 버전을 보관한다.
CREATE TABLE purchase_order_attachment (
    id uuid PRIMARY KEY,
    po_id uuid NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    document_type varchar(50) NOT NULL,
    document_version integer NOT NULL CHECK (document_version > 0),
    object_key varchar(1000) NOT NULL,
    content_sha256 char(64) NOT NULL,
    content_type varchar(100) NOT NULL,
    file_size_bytes bigint NOT NULL CHECK (file_size_bytes >= 0),
    uploaded_by uuid NOT NULL,
    uploaded_at timestamptz NOT NULL,
    CONSTRAINT fk_po_attachment_revision FOREIGN KEY (po_id, revision)
        REFERENCES purchase_order_revision (po_id, revision) ON DELETE RESTRICT,
    CONSTRAINT uq_po_attachment_version UNIQUE (po_id, revision, document_type, document_version),
    CONSTRAINT ck_po_attachment_sha256 CHECK (content_sha256 ~ '^[0-9a-f]{64}$')
);

-- 발주 변경과 상태 전이의 행위자·사유·전후 snapshot을 append-only로 보관한다.
CREATE TABLE purchase_order_audit_log (
    id uuid PRIMARY KEY,
    po_id uuid NOT NULL REFERENCES purchase_order (id) ON DELETE RESTRICT,
    revision integer,
    action varchar(50) NOT NULL,
    actor_id uuid,
    reason_code varchar(50),
    before_snapshot jsonb,
    after_snapshot jsonb,
    occurred_at timestamptz NOT NULL,
    correlation_id uuid NOT NULL,
    CONSTRAINT ck_po_audit_before_json CHECK
        (before_snapshot IS NULL OR jsonb_typeof(before_snapshot) = 'object'),
    CONSTRAINT ck_po_audit_after_json CHECK
        (after_snapshot IS NULL OR jsonb_typeof(after_snapshot) = 'object')
);

-- 변경 명령의 Idempotency-Key와 요청 hash, 재시도 결과를 보관한다.
CREATE TABLE purchase_order_command (
    id uuid PRIMARY KEY,
    org_id uuid NOT NULL,
    command_type varchar(50) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    request_sha256 char(64) NOT NULL,
    po_id uuid REFERENCES purchase_order (id) ON DELETE RESTRICT,
    response_status integer,
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT uq_po_command_idempotency UNIQUE (org_id, command_type, idempotency_key),
    CONSTRAINT ck_po_command_sha256 CHECK (request_sha256 ~ '^[0-9a-f]{64}$')
);

-- 발주 변경과 함께 기록하고 워커가 발행·재시도하는 트랜잭션 아웃박스다.
CREATE TABLE outbox_event (
    id uuid PRIMARY KEY,
    org_id uuid NOT NULL,
    aggregate_type varchar(50) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type varchar(100) NOT NULL,
    deduplication_key varchar(255) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    published_at timestamptz,
    publish_attempts integer NOT NULL DEFAULT 0 CHECK (publish_attempts >= 0),
    last_error varchar(2000),
    CONSTRAINT uq_outbox_event_deduplication UNIQUE (deduplication_key),
    CONSTRAINT ck_outbox_event_payload CHECK (jsonb_typeof(payload) = 'object')
);

COMMENT ON TABLE purchase_order IS
    '발주 집합 루트. 조직별 발주 번호, 현재 유효 개정, 공급사·사업장·통화·상태를 보관하며 재고 수량은 직접 보관하지 않는다.';
COMMENT ON COLUMN purchase_order.id IS '발주 집합 루트 식별자. 애플리케이션이 생성한 UUID.';
COMMENT ON COLUMN purchase_order.org_id IS '조직(테넌트) 식별자. 조회·명령의 권한 경계. 마스터 FK는 후속 migration 후보.';
COMMENT ON COLUMN purchase_order.plant_id IS '사업장 식별자. 마스터 FK는 후속 migration 후보.';
COMMENT ON COLUMN purchase_order.po_number IS '조직 내 발주 번호. (org_id, po_number)로 유일.';
COMMENT ON COLUMN purchase_order.current_revision IS '현재 유효 승인 개정의 번호. 개정 활성화 트랜잭션에서만 갱신.';
COMMENT ON COLUMN purchase_order.supplier_id IS '공급사 식별자. 활성·동일 조직 검증은 애플리케이션/마스터 계약 책임.';
COMMENT ON COLUMN purchase_order.currency IS '계약 통화 ISO 4217 코드(3자).';
COMMENT ON COLUMN purchase_order.currency_scale IS '통화 소수 자릿수(0~6). 금액 반올림 기준.';
COMMENT ON COLUMN purchase_order.exchange_rate IS '계약 시점 환율. NULL 허용, 값이 있으면 양수.';
COMMENT ON COLUMN purchase_order.payment_terms_code IS '결제 조건 코드.';
COMMENT ON COLUMN purchase_order.payment_terms_snapshot IS '결제 조건의 계약 시점 JSON 스냅샷(object).';
COMMENT ON COLUMN purchase_order.incoterms_code IS '인코텀즈 코드.';
COMMENT ON COLUMN purchase_order.incoterms_place IS '인코텀즈 지정 장소.';
COMMENT ON COLUMN purchase_order.ship_to_warehouse_id IS '납품(입고) 창고 식별자. 마스터 FK는 후속 migration 후보.';
COMMENT ON COLUMN purchase_order.status IS '발주 헤더 상태. DRAFT→…→CLOSED 및 REJECTED/CANCELLED. 전이 규칙은 애플리케이션 책임.';
COMMENT ON COLUMN purchase_order.ordered_at IS '발주(주문) 확정 시각(UTC).';
COMMENT ON COLUMN purchase_order.approved_at IS '현재 유효본 승인 시각(UTC). 목록 정렬 키로 사용.';
COMMENT ON COLUMN purchase_order.sent_at IS '공급사 송부 성공 시각(UTC).';
COMMENT ON COLUMN purchase_order.created_by IS '초안 작성자 식별자. 자기 결재 차단 비교에 사용.';
COMMENT ON COLUMN purchase_order.created_at IS '행 생성 시각(UTC).';
COMMENT ON COLUMN purchase_order.updated_at IS '행 최종 갱신 시각(UTC).';
COMMENT ON COLUMN purchase_order.version IS '낙관적 잠금 버전. expectedVersion 조건 갱신에 사용.';

COMMENT ON TABLE purchase_order_revision IS
    '발주 개정의 불변 계약 스냅샷과 승인 상태. 조직·발주번호·개정 번호의 유일성을 보장하고 정책 버전과 변경 사유를 보존한다.';
COMMENT ON COLUMN purchase_order_revision.id IS '개정 행 식별자. 결재·송부·확인·첨부 FK 대상.';
COMMENT ON COLUMN purchase_order_revision.po_id IS '소속 발주 ID. org_id·po_number와 함께 헤더 복합 FK.';
COMMENT ON COLUMN purchase_order_revision.org_id IS '조직 식별자. 헤더와 일치해야 함.';
COMMENT ON COLUMN purchase_order_revision.po_number IS '발주 번호. 헤더와 일치해야 함.';
COMMENT ON COLUMN purchase_order_revision.revision IS '개정 번호(1부터). (org_id, po_number, revision)·(po_id, revision)로 유일.';
COMMENT ON COLUMN purchase_order_revision.revision_status IS '개정 승인 상태. DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/CANCELLED.';
COMMENT ON COLUMN purchase_order_revision.approval_policy_version IS '결재에 적용된 정책 버전 식별자.';
COMMENT ON COLUMN purchase_order_revision.change_reason_code IS '개정 변경 사유 코드. 최초 개정은 NULL 가능.';
COMMENT ON COLUMN purchase_order_revision.change_reason_detail IS '개정 변경 사유 상세.';
COMMENT ON COLUMN purchase_order_revision.before_snapshot IS '변경 전 계약 JSON 스냅샷(object). 최초 개정은 NULL.';
COMMENT ON COLUMN purchase_order_revision.after_snapshot IS '변경 후 불변 계약 JSON 스냅샷(object). 승인 조건의 원본.';
COMMENT ON COLUMN purchase_order_revision.content_sha256 IS 'after_snapshot 내용의 SHA-256(소문자 hex 64자). 활성화 시 무결성 검증.';
COMMENT ON COLUMN purchase_order_revision.created_by IS '개정 작성자 식별자.';
COMMENT ON COLUMN purchase_order_revision.created_at IS '개정 행 생성 시각(UTC).';
COMMENT ON COLUMN purchase_order_revision.submitted_at IS '결재 제출 시각(UTC).';
COMMENT ON COLUMN purchase_order_revision.approved_at IS '개정 승인 시각(UTC).';
COMMENT ON COLUMN purchase_order_revision.rejected_at IS '개정 반려 시각(UTC).';

COMMENT ON TABLE purchase_order_line IS
    '현재 유효 발주 개정의 라인 투영. 품목·수량·UOM·단가·세금·납기·입고 라우팅을 보관하며 received_qty는 GRN 원장에서 산출한다.';
COMMENT ON COLUMN purchase_order_line.id IS '안정적 라인 식별자. GRN·종결이 참조. 입고가 있으면 삭제하지 않는다.';
COMMENT ON COLUMN purchase_order_line.po_id IS '소속 발주 ID.';
COMMENT ON COLUMN purchase_order_line.line_no IS '발주 내 라인 번호. (po_id, line_no)로 유일.';
COMMENT ON COLUMN purchase_order_line.item_id IS '품목 식별자. 거래 가능·동일 조직 검증은 마스터 계약 책임.';
COMMENT ON COLUMN purchase_order_line.line_status IS '라인 상태. ACTIVE/CLOSED/CANCELLED. 종료는 삭제가 아닌 상태로 표현.';
COMMENT ON COLUMN purchase_order_line.ordered_qty IS '주문 수량(>0). 인정 입고량보다 작아질 수 없음(앱+GRN 검증).';
COMMENT ON COLUMN purchase_order_line.closed_qty IS '잔량 종결 누적 투영(≥0, ≤ordered_qty). 이력 합계와 일치해야 함.';
COMMENT ON COLUMN purchase_order_line.purchase_uom IS '구매 단위(UOM).';
COMMENT ON COLUMN purchase_order_line.inventory_uom IS '재고 단위(UOM).';
COMMENT ON COLUMN purchase_order_line.conversion_numerator IS '구매→재고 UOM 전환 분자(>0).';
COMMENT ON COLUMN purchase_order_line.conversion_denominator IS '구매→재고 UOM 전환 분모(>0).';
COMMENT ON COLUMN purchase_order_line.unit_price IS '계약 단가(≥0). currency_scale 기준으로 해석.';
COMMENT ON COLUMN purchase_order_line.tax_code IS '세금 코드.';
COMMENT ON COLUMN purchase_order_line.tax_rate IS '세율(0~1). 계약 시점 값.';
COMMENT ON COLUMN purchase_order_line.requested_date IS '요청 납기(업무일 date).';
COMMENT ON COLUMN purchase_order_line.promised_date IS '약속(확정) 납기(업무일 date).';
COMMENT ON COLUMN purchase_order_line.receipt_routing IS '입고 라우팅. DIRECT/STANDARD/INSPECTION.';
COMMENT ON COLUMN purchase_order_line.inspection_spec_revision_id IS 'IQC inspection_spec_revision.id를 복사한 검사 규격 revision UUID. INSPECTION일 때만 NOT NULL, 그 외는 NULL.';
COMMENT ON COLUMN purchase_order_line.over_receipt_pct IS '초과 입고 허용 비율(0~100%).';
COMMENT ON COLUMN purchase_order_line.effective_revision IS '이 라인 투영이 반영된 유효 개정 번호. (po_id, revision) FK.';
COMMENT ON COLUMN purchase_order_line.version IS '라인 낙관적 잠금 버전.';
COMMENT ON COLUMN purchase_order_line.created_at IS '라인 생성 시각(UTC).';
COMMENT ON COLUMN purchase_order_line.updated_at IS '라인 최종 갱신 시각(UTC).';

COMMENT ON TABLE po_approval IS
    '발주 개정별 결재 단계와 결재자·판정·전결 원본·정책 규칙 스냅샷을 보관한다. 자기 결재 및 전결 권한은 애플리케이션 정책이 검증한다.';
COMMENT ON COLUMN po_approval.id IS '결재 행 식별자.';
COMMENT ON COLUMN po_approval.po_revision_id IS '대상 발주 개정 ID.';
COMMENT ON COLUMN po_approval.step_no IS '결재 단계 번호(>0).';
COMMENT ON COLUMN po_approval.approver_id IS '실제 결재자 식별자. 작성자 본인 결재 차단은 앱 정책.';
COMMENT ON COLUMN po_approval.delegated_from_id IS '전결·위임 원본 결재자 식별자. 위임이 없으면 NULL.';
COMMENT ON COLUMN po_approval.decision IS '판정. PENDING/APPROVED/REJECTED/SKIPPED.';
COMMENT ON COLUMN po_approval.approval_rule_snapshot IS '해당 단계에 적용된 정책 규칙 JSON 스냅샷(object).';
COMMENT ON COLUMN po_approval.decided_at IS '판정 시각(UTC). PENDING이면 NULL, 그 외는 NOT NULL.';
COMMENT ON COLUMN po_approval.comment IS '결재 의견.';
COMMENT ON COLUMN po_approval.created_at IS '결재 행 생성 시각(UTC).';

COMMENT ON TABLE po_line_close IS
    '미입고 잔량 종결 이력. 종결 수량·사유·행위자·멱등 키를 보관하며 GRN 인정 수량과의 합계 검증은 공통 트랜잭션 계약이 담당한다.';
COMMENT ON COLUMN po_line_close.id IS '종결 이력 식별자.';
COMMENT ON COLUMN po_line_close.po_line_id IS '대상 발주 라인 ID.';
COMMENT ON COLUMN po_line_close.close_qty IS '이번 요청의 종결 수량(>0).';
COMMENT ON COLUMN po_line_close.reason_code IS '종결 사유 코드.';
COMMENT ON COLUMN po_line_close.reason_detail IS '종결 사유 상세.';
COMMENT ON COLUMN po_line_close.actor_id IS '종결 행위자 식별자.';
COMMENT ON COLUMN po_line_close.idempotency_key IS '라인별 종결 명령 멱등 키. (po_line_id, idempotency_key)로 유일.';
COMMENT ON COLUMN po_line_close.closed_at IS '종결 처리 시각(UTC).';

COMMENT ON TABLE po_transmission IS
    '발주 개정의 공급사 송부 시도 이력. 채널·멱등 키·외부 메시지 식별자·응답·성공 또는 실패 시각을 보관한다.';
COMMENT ON COLUMN po_transmission.id IS '송부 시도 식별자.';
COMMENT ON COLUMN po_transmission.po_revision_id IS '송부 대상 발주 개정 ID.';
COMMENT ON COLUMN po_transmission.channel IS '송부 채널. EDI/EMAIL/PORTAL/API.';
COMMENT ON COLUMN po_transmission.idempotency_key IS '개정별 송부 멱등 키. (po_revision_id, idempotency_key)로 유일.';
COMMENT ON COLUMN po_transmission.status IS '송부 상태. PENDING/SENT/FAILED.';
COMMENT ON COLUMN po_transmission.external_message_id IS '외부 시스템 메시지·전송 식별자. 재조회·중복 송부 탐지에 사용.';
COMMENT ON COLUMN po_transmission.response_payload IS '외부 응답 JSON(object 또는 array). 필요 최소·마스킹.';
COMMENT ON COLUMN po_transmission.requested_at IS '송부 요청 시각(UTC).';
COMMENT ON COLUMN po_transmission.sent_at IS '송부 성공 시각(UTC).';
COMMENT ON COLUMN po_transmission.failed_at IS '송부 실패 시각(UTC).';

COMMENT ON TABLE po_supplier_ack IS
    '공급사가 회신한 발주 개정 확인 이력. 수락·거절·변경 요청 상태와 공급사 참조 번호·응답 payload를 보관한다.';
COMMENT ON COLUMN po_supplier_ack.id IS '공급사 확인 이력 식별자.';
COMMENT ON COLUMN po_supplier_ack.po_revision_id IS '확인 대상 발주 개정 ID.';
COMMENT ON COLUMN po_supplier_ack.ack_status IS '확인 상태. ACCEPTED/REJECTED/CHANGED/PENDING.';
COMMENT ON COLUMN po_supplier_ack.supplier_reference IS '공급사 측 참조 번호. 개정 내 유일(NULL 제외 시).';
COMMENT ON COLUMN po_supplier_ack.response_payload IS '공급사 응답 JSON(object 또는 array). 필요 최소·마스킹.';
COMMENT ON COLUMN po_supplier_ack.acknowledged_at IS '공급사 확인 시각(UTC).';
COMMENT ON COLUMN po_supplier_ack.received_at IS '시스템이 응답을 수신한 시각(UTC).';

COMMENT ON TABLE purchase_order_attachment IS
    '발주 개정에 연결된 견적서·계약서·규격서 등의 문서 메타데이터. 저장소 객체 키, SHA-256, 문서 버전을 보관한다.';
COMMENT ON COLUMN purchase_order_attachment.id IS '첨부 메타데이터 식별자.';
COMMENT ON COLUMN purchase_order_attachment.po_id IS '소속 발주 ID. revision과 함께 개정 복합 FK.';
COMMENT ON COLUMN purchase_order_attachment.revision IS '문서가 연결된 발주 개정 번호.';
COMMENT ON COLUMN purchase_order_attachment.document_type IS '문서 유형(견적서·계약서·규격서 등).';
COMMENT ON COLUMN purchase_order_attachment.document_version IS '동일 유형 내 문서 버전(>0). PO·개정·유형·버전으로 유일.';
COMMENT ON COLUMN purchase_order_attachment.object_key IS '객체 저장소 키. 파일 본문은 저장소에 보관.';
COMMENT ON COLUMN purchase_order_attachment.content_sha256 IS '파일 내용 SHA-256(소문자 hex 64자). 다운로드 시 검증.';
COMMENT ON COLUMN purchase_order_attachment.content_type IS 'MIME 타입.';
COMMENT ON COLUMN purchase_order_attachment.file_size_bytes IS '파일 크기(바이트, ≥0).';
COMMENT ON COLUMN purchase_order_attachment.uploaded_by IS '업로드 행위자 식별자.';
COMMENT ON COLUMN purchase_order_attachment.uploaded_at IS '업로드 시각(UTC).';

COMMENT ON TABLE purchase_order_audit_log IS
    '발주 생성·변경·상태 전이·종결·외부 응답의 감사 이력. 행위자·사유·전후 스냅샷·상관관계를 append-only로 보존한다.';
COMMENT ON COLUMN purchase_order_audit_log.id IS '감사 행 식별자. UPDATE/DELETE 금지(DB role 분리).';
COMMENT ON COLUMN purchase_order_audit_log.po_id IS '대상 발주 ID.';
COMMENT ON COLUMN purchase_order_audit_log.revision IS '관련 개정 번호. 해당 없으면 NULL.';
COMMENT ON COLUMN purchase_order_audit_log.action IS '감사 행위 코드(생성·승인·송부·종결 등).';
COMMENT ON COLUMN purchase_order_audit_log.actor_id IS '행위자 식별자. 시스템 자동이면 NULL 가능.';
COMMENT ON COLUMN purchase_order_audit_log.reason_code IS '행위 사유 코드.';
COMMENT ON COLUMN purchase_order_audit_log.before_snapshot IS '변경 전 JSON 스냅샷(object).';
COMMENT ON COLUMN purchase_order_audit_log.after_snapshot IS '변경 후 JSON 스냅샷(object).';
COMMENT ON COLUMN purchase_order_audit_log.occurred_at IS '감사 사건 발생 시각(UTC).';
COMMENT ON COLUMN purchase_order_audit_log.correlation_id IS '요청·트랜잭션 상관관계 ID.';

COMMENT ON TABLE purchase_order_command IS
    '변경 명령의 Idempotency-Key 처리 이력. 동일 조직·명령 유형·키의 재시도 결과를 재생하고 요청 hash 불일치를 탐지한다.';
COMMENT ON COLUMN purchase_order_command.id IS '명령 멱등 이력 식별자.';
COMMENT ON COLUMN purchase_order_command.org_id IS '조직 식별자. 멱등 키 범위의 일부.';
COMMENT ON COLUMN purchase_order_command.command_type IS '명령 유형(생성·송부·종결 등).';
COMMENT ON COLUMN purchase_order_command.idempotency_key IS 'Idempotency-Key. (org_id, command_type, idempotency_key)로 유일.';
COMMENT ON COLUMN purchase_order_command.request_sha256 IS '요청 body canonical hash(소문자 hex 64자). 동일 키·다른 body면 409.';
COMMENT ON COLUMN purchase_order_command.po_id IS '명령이 생성·대상이 된 발주 ID. 생성 전이면 NULL 가능.';
COMMENT ON COLUMN purchase_order_command.response_status IS '재생할 HTTP(또는 동등) 응답 상태 코드.';
COMMENT ON COLUMN purchase_order_command.created_at IS '명령 접수 시각(UTC).';
COMMENT ON COLUMN purchase_order_command.completed_at IS '명령 완료 시각(UTC).';

COMMENT ON TABLE outbox_event IS
    '트랜잭션 아웃박스 이벤트. 발주 변경과 함께 기록한 발행 대기 이벤트를 워커가 멱등 키 기준으로 발행·재시도한다.';
COMMENT ON COLUMN outbox_event.id IS '아웃박스 이벤트 식별자.';
COMMENT ON COLUMN outbox_event.org_id IS '이벤트의 조직/테넌트 UUID. 모든 소비자 조회·멱등 처리의 권한 경계.';
COMMENT ON COLUMN outbox_event.aggregate_type IS '집합 유형(예: PurchaseOrder).';
COMMENT ON COLUMN outbox_event.aggregate_id IS '집합 식별자(보통 po_id).';
COMMENT ON COLUMN outbox_event.event_type IS '이벤트 유형 이름.';
COMMENT ON COLUMN outbox_event.deduplication_key IS '전역 발행 멱등 키. UNIQUE.';
COMMENT ON COLUMN outbox_event.payload IS '이벤트 payload JSON(object). 개인정보·비밀값 비포함.';
COMMENT ON COLUMN outbox_event.occurred_at IS '이벤트 발생(기록) 시각(UTC). 미발행 폴링 정렬 키.';
COMMENT ON COLUMN outbox_event.published_at IS '발행 완료 시각(UTC). NULL이면 미발행.';
COMMENT ON COLUMN outbox_event.publish_attempts IS '발행 시도 횟수(≥0).';
COMMENT ON COLUMN outbox_event.last_error IS '마지막 발행 실패 메시지.';

CREATE INDEX ix_purchase_order_list
    ON purchase_order (org_id, status, approved_at DESC, id DESC);
CREATE INDEX ix_purchase_order_supplier_status
    ON purchase_order (org_id, supplier_id, status, id);
CREATE INDEX ix_po_revision_order
    ON purchase_order_revision (po_id, revision DESC);
CREATE INDEX ix_po_approval_revision_step
    ON po_approval (po_revision_id, step_no, decision);
CREATE INDEX ix_po_transmission_revision
    ON po_transmission (po_revision_id, requested_at DESC);
CREATE INDEX ix_po_supplier_ack_revision
    ON po_supplier_ack (po_revision_id, acknowledged_at DESC);
CREATE INDEX ix_po_attachment_order
    ON purchase_order_attachment (po_id, revision DESC);
CREATE INDEX ix_po_audit_order_time
    ON purchase_order_audit_log (po_id, occurred_at DESC);
CREATE INDEX ix_outbox_unpublished
    ON outbox_event (occurred_at)
    WHERE published_at IS NULL;
