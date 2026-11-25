-- V9: 고객 메시지를 누구에게 보냈는지 2026-11-25
-- 추가만 함 (빈 값을 허용하는 열 하나). 예전 메시지는 NULL = 구분 없음 (화면은 지금 색 그대로).

ALTER TABLE TBL_CHAT_MESSAGE ADD (SEND_TO CHAR(2));

COMMENT ON COLUMN TBL_CHAT_MESSAGE.SEND_TO IS '고객 메시지 받는 쪽: 01 AI 도우미, 02 상담사, NULL 구분 없음';
