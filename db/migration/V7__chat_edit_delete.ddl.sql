-- V7: 채팅 메시지 수정 · 삭제 표시 2026-11-25
-- 추가만 함 (열 두 개). 삭제해도 줄은 남기고 화면에만 "삭제된 메시지"로 보임 (분쟁 확인용).
-- 규칙(ChatService): 내 메시지만, 보낸 지 24시간 안. 삭제는 상대가 아직 읽지 않았을 때만 (IS_READ = 'N').

ALTER TABLE TBL_CHAT_MESSAGE ADD (EDITED_AT DATE, DELETED_AT DATE);

COMMENT ON COLUMN TBL_CHAT_MESSAGE.EDITED_AT IS '마지막으로 고친 시각 (화면에 "수정됨")';
COMMENT ON COLUMN TBL_CHAT_MESSAGE.DELETED_AT IS '지운 시각 (화면에 "삭제된 메시지예요", 내용은 남김)';
