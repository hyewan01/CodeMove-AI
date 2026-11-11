-- ==================================================
-- V3 고객 ↔ 관리자 연동 (2026-11-11) : V2 위에 실행
-- 추가만 함: 새 컬럼·코드·설정 행. 기존 테이블·컬럼은 바꾸거나 지우지 않음
-- 근거: docs/erd/ERD_CHANGES.md "2026-11-11 고객 ↔ 관리자 연동"
-- ==================================================

-- ---------- TBL_CHAT_MESSAGE : 답변이 필요한 고객 메시지 ----------
-- 챗봇이 로어북으로 답하지 못했거나(대응 규칙 안내) 고객이 상담사 연결을 요청한 고객 메시지 = 'Y'.
-- 관리자 채팅 상세에서 그 뒤에 관리자 답장이 없으면 붉게 표시
ALTER TABLE TBL_CHAT_MESSAGE ADD (NEEDS_REPLY CHAR(1) DEFAULT 'N' NOT NULL);
COMMENT ON COLUMN TBL_CHAT_MESSAGE.NEEDS_REPLY IS '답변필요여부(챗봇 미응답·상담사 요청)';

-- ---------- CHAT_STATUS 05 : 챗봇이 답하지 못한 채팅 ----------
-- 관리자 채팅 목록에는 02(상담사 요청)와 함께 '대기 중'으로 보이고,
-- 채팅 관리의 '실시간 상담 현황'은 02(상담사 요청)만 셈
INSERT INTO TBL_CODE (GROUP_CODE, CODE, CODE_NAME, USER_NAME, SORT_ORDER, IS_ACTIVE)
SELECT 'CHAT_STATUS', '05', '답변 필요', '상담 대기', 5, 'Y' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TBL_CODE WHERE GROUP_CODE = 'CHAT_STATUS' AND CODE = '05');

-- ---------- TBL_RESERVATION_SETTING : 예약 설정 기본값 ----------
-- 하루 최대 예약 3건, 정기 휴무 요일 없음, 공휴일 영업 (일정 관리 화면에서 변경)
INSERT INTO TBL_RESERVATION_SETTING (SETTING_ID, DAILY_CAPACITY, CLOSED_WEEKDAYS, HOLIDAY_CLOSED, UPDATED_AT)
SELECT SEQ_RESERVATION_SETTING.NEXTVAL, 3, NULL, 'N', SYSDATE FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TBL_RESERVATION_SETTING);

COMMIT;
