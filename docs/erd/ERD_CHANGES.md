# ERD 변경 사항

`CodeMove_ERD.pdf` 기준으로 DDL 작성 시 반영할 변경 사항입니다.

| 날짜 | 테이블 | 변경 전 | 변경 후 | 사유 |
| --- | --- | --- | --- | --- |
| 2026-11-02 | TBL_SCHEDULE | `SCHDULE_ID`, `SCHDULE_DATE`, `SCHDULE_TIME` | `SCHEDULE_ID`, `SCHEDULE_DATE`, `SCHEDULE_TIME` | 오타 수정, 시퀀스 `SEQ_SCHEDULE`와 명칭 통일 |
| 2026-11-02 | TBL_RESERVATION | `SCHDULE_ID` (FK) | `SCHEDULE_ID` (FK) | TBL_SCHEDULE PK 명칭 변경에 따른 FK 수정 |

## 명명 규칙

- 테이블: `TBL_` 접두사 (예: `TBL_MEMBER`)
- 시퀀스: `SEQ_` + 테이블명에서 `TBL_` 제외 (예: `SEQ_MEMBER`)
- DB(TEAM03 계정)에 ERD 테이블 20개에 대응하는 시퀀스 20개가 생성되어 있음 (2026-11-02 확인)
