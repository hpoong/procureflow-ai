CREATE SEQUENCE po_number_seq AS bigint START WITH 1 INCREMENT BY 1 NO CYCLE;

COMMENT ON SEQUENCE po_number_seq IS '발주 번호 일련부. 전역 증가, 롤백·실패 시 공백 허용.';
