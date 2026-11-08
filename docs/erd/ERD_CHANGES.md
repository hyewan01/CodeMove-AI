# ERD 변경 사항

TEAM03 DB에 **2026-11-03** 생성된 스키마(테이블 26개)에 **2026-11-08 화면 대조(V2)**를 더한 것이 기준입니다(테이블 27개).
DDL은 [`db/schema.sql`](../../db/schema.sql), 공통코드 데이터는 [`db/data-code.sql`](../../db/data-code.sql), 그림은 [ERD.md](ERD.md)에 있습니다.
아래는 `CodeMove_ERD.pdf`(2026-11-02, 테이블 20개)와 실제 DB가 다른 점입니다.

## 2026-11-08 화면 대조 (V2)

고객·관리자 화면(SCR-U, SCR-A)을 만든 뒤 화면에 필요한 값과 DB를 맞췄습니다. **추가만** 했고(새 테이블·컬럼·코드, 필수 해제) 기존 테이블·컬럼은 지우지 않았습니다.
이미 V1이 있는 DB는 [`db/migration/V2__screen_alignment.sql`](../../db/migration/V2__screen_alignment.sql)을 한 번 실행하면 됩니다. 새 DB는 `schema.sql`에 V2까지 들어 있습니다.

| 대상 | 변경 | 이유 (화면) |
| --- | --- | --- |
| TBL_SCHEDULE | **새 테이블** (일정명, 분류, 날짜, 시각, 장소, 연결 회원·예약, 등록 관리자) + `SEQ_SCHEDULE`, `IX_SCHEDULE_DATE` | 일정 등록(SCR-A-021)으로 만든 일정을 담을 곳이 없었음 |
| TBL_ADMIN | `LOGIN_ID`, `PASSWORD` NULL 허용. `PHONE` UK 추가 | 관리자는 휴대전화 인증으로만 로그인 (아래 "로그인 방식") |
| TBL_MOVE_REQUEST | `MOVE_TIME`(HH24:MI), `FROM_ZIPCODE`, `TO_ZIPCODE` 추가 | 날짜 선택(SCR-U-008)에서 시각을 고름 / 주소 검색이 우편번호를 줌. `PREFER_TIME`(오전·오후)은 시각에서 계산해 함께 저장 |
| TBL_QUOTE | `ITEM_GROUP1`~`4` 추가 | 견적서 양식 품목표의 4칸 제목. 3·4번째는 관리자가 직접 쓰는 글자라 코드로 담을 수 없음 |
| TBL_CLOSED_DAY | `CLOSED_TYPE CHAR(2)` 추가 (기본 `01`) | 일정 관리(SCR-A-022)의 날짜 상태가 "업체 휴무일"·"이사 예약 불가능" 2가지 |
| 공통코드 | `CLOSED_TYPE`(01 업체 휴무일, 02 이사 예약 불가능), `NTF_DETAIL` 16 상태 변경, 17 데이터 삭제 | 관리자 알림 패널(SCR-A-019) 분류 |

### 화면 값 ↔ 컬럼

DB에 컬럼을 새로 만들지 않고 기존 컬럼으로 담거나 계산하는 값입니다.

| 화면 값 | 저장·계산 |
| --- | --- |
| 견적서 사다리차·승강기·계단작업 "전/후" | 전 = `FROM_*_USE`, 후 = `TO_*_USE` |
| 견적서 사다리비용(A), 사다리 소계(B) | `FROM_LADDER_COST + TO_LADDER_COST` |
| 견적서 품목 수량 "1대", "25개", "1식" | 숫자 → `QUANTITY`, 나머지 글자 → `UNIT` |
| 견적서 품목 칸 위치 | `SORT_ORDER` = 행 × 4 + 칸, 칸 제목이 가구·전자기기·생활용품이면 그 `CATEGORY`, 아니면 기타(04) |
| 견적서 작업내용 포장·일반·보관 | `WORK_TYPE` = `PACK_TYPE` 코드 04·01·03 |
| 견적 번호, 예약 번호 | `QUOTE_NO` = `QT-yyyyMMdd-0000`, `RESERVATION_NO` = `RSV-yyyyMMdd-0000` |
| 채팅 운영 설정: 챗봇 사용 / 상담 운영 시간 사용 / 지연 안내 | `BOT_ENABLED` / `CONSULT_ENABLED` / `DELAY_MESSAGE` |
| 회원 관리 분류 "관리자" | 같은 휴대폰 번호의 `TBL_ADMIN`(`IS_ACTIVE = 'Y'`)이 있으면 관리자. 관리자로 바꾸면 그 번호로 `TBL_ADMIN`을 만들거나 다시 사용, 고객으로 되돌리면 `IS_ACTIVE = 'N'` |
| 회원 삭제 | `TBL_MEMBER.IS_ACTIVE = 'N'`, `WITHDRAWN_AT` (예약·견적 이력이 FK로 남아 있어 행은 지우지 않음) |
| 회원 지역, 리뷰 이용 회차·이사 유형 | 회원의 견적 요청·예약에서 계산 |
| 자동 메시징 루틴 | 테이블 없음. 편집 화면이 없고 "일정 기준 자동 알림 규칙 설정"은 제외 범위(README §6)라 코드에 고정. 루틴이 만든 알림은 `TBL_NOTIFICATION`에 저장 |

## 공통

- 문자열은 모두 `VARCHAR2`이고, 한글이 들어가는 컬럼은 문자 단위(`VARCHAR2(n CHAR)`)로 길이를 잡았습니다. 길이도 대부분 다시 정했습니다.
- 코드성 컬럼(상태, 유형, 구분)은 `CHAR(2)`로 통일했고, 값은 `TBL_CODE`의 공통코드를 씁니다.
- `IS_*`, `*_YN`, 등록·수정 일시 컬럼에는 기본값(`'Y'`/`'N'`, `SYSDATE`)이 있습니다.
- 제약조건 이름: `PK_<테이블>`, `UK_<테이블>_<의미>`, `FK_<테이블>_<참조>`, `CK_<테이블>_<컬럼>`

## 추가된 테이블

| 테이블 | 설명 | 비고 |
| --- | --- | --- |
| TBL_CODE_GROUP / TBL_CODE | 공통코드 그룹 / 공통코드 | PK는 `GROUP_CODE`, `(GROUP_CODE, CODE)`. 그룹 22개, 코드 81건 |
| TBL_CHATBOT_TOPIC | 챗봇 주제 | TBL_CHATBOT_SCENARIO 대체 |
| TBL_LOREBOOK | 로어북 (주제별 AI 전달 내용) | TBL_CHATBOT_SCENARIO 대체 |
| TBL_CHATBOT_ACTION | 챗봇 답변 버튼 | TBL_CHATBOT_SCENARIO 대체 |
| TBL_SAVED_REPLY | 자주 쓰는 답변 | |
| TBL_RESERVATION_SETTING | 예약 설정 (하루 최대 건수, 정기 휴무 요일) | TBL_SCHEDULE 대체 |
| TBL_CLOSED_DAY | 임시 휴무일 | PK는 `CLOSED_DATE`. TBL_SCHEDULE 대체 |

## 삭제된 테이블

| 테이블 | 대체 |
| --- | --- |
| TBL_SCHEDULE | TBL_RESERVATION_SETTING + TBL_CLOSED_DAY. 그래서 2026-11-02에 정한 `SCHDULE_*` → `SCHEDULE_*` 변경은 적용할 곳이 없습니다 |
| TBL_CHATBOT_SCENARIO | TBL_CHATBOT_TOPIC + TBL_LOREBOOK + TBL_CHATBOT_ACTION |

## 컬럼 변경

| 테이블 | 변경 내용 |
| --- | --- |
| TBL_MEMBER | `TERMS_AGREED_AT`, `WITHDRAWN_AT` 추가. `IS_VERIFIED`는 NOT NULL로 바뀜. `PHONE` UK |
| TBL_SOCIAL | `MEMBER_ID` `CHAR(19)` → `NUMBER(19)` (FK 타입 맞춤). `(LOGIN_TYPE, SOCIAL_NO)` UK |
| TBL_ADMIN | `LOGIN_ID`(UK), `PW_CHANGED_AT`, `PUSH_ENABLED`, `CREATED_AT` 추가. `IS_ACTIVE` `VARCHAR2(255)` → `CHAR(1)`. `PHONE`은 NOT NULL로 바뀜 |
| TBL_MOVE_REQUEST | `REQUSTED_AT` → `REQUESTED_AT` (오타 수정). `MOVE_TIME` → `PREFER_TIME CHAR(2)`. `STATUS` 삭제. `AI_ANALYSIS_SEC`, `AI_CONFIDENCE` 추가 |
| TBL_REQUEST_ITEM | `CATEGORY`, `CONFIDENCE` 추가 |
| TBL_QUOTE | `SOURCE_TYPE`, `WORK_TIME`, `EXPIRES_AT`, `UPDATED_AT` 추가. `LADDER_USE`/`ELEVATOR_USE`/`STAIRS_USE`를 `FROM_*`·`TO_*`로 나눔. `REQUEST_ID`와 금액 컬럼은 NULL 허용으로 바뀜. `QUOTE_NO` UK, `REQUEST_ID` UK |
| TBL_QUOTE_ITEM | `UNIT`, `SORT_ORDER` 추가. `QUANTITY` `NUMBER(2)` → `NUMBER(3)` |
| TBL_QUOTE_OPTION | `OPTION_NAME`, `WORK_TYPE` 삭제 → `OPTION_CODE`, `CHOICE1_YN`, `CHOICE2_YN`로 바뀜. `(QUOTE_ID, OPTION_CODE)` UK |
| TBL_RESERVATION | `SCHDULE_ID` 삭제. `PAY_STATUS`, `DEPOSIT_PAID_AT` 추가. `RESERVATION_NO` UK, `REQUEST_ID` UK, `MOVE_DATE` 인덱스 |
| TBL_REVIEW | `RATING` `CHAR(1) NULL` → `NUMBER(1) NOT NULL` + CHECK(1~5). `RESERVATION_ID` UK |
| TBL_CHAT_ROOM | `ADMIN_ID`(담당 관리자), `WAITING_AT` 추가 |
| TBL_CHAT_MESSAGE | `TOPIC_ID`, `ATTACHMENT_NAME` 추가. `CONTENT` `VARCHAR2(4000) NOT NULL` → `VARCHAR2(1000 CHAR) NULL` |
| TBL_CHAT_SETTING | `CONSULT_ENABLED`, `DELAY_MESSAGE`, `AWAY_MESSAGE` 추가. `START_TIME`/`END_TIME` `TIMESTAMP` → `VARCHAR2(5)`. `ADMIN_ID`는 NULL 허용으로 바뀜 |
| TBL_QUICK_MENU | `ACTION_TYPE`, `ACTION_CODE` 삭제 → `TOPIC_ID` FK로 바뀜 |
| TBL_NOTIFICATION | `SETTING_ID` FK 삭제. `RECEIVER_TYPE`, `NTF_DETAIL`, `LINK_URL`, `IS_IMPORTANT`, `CREATED_AT` 추가. `MEMBER_ID`, `SENT_AT`은 NULL 허용으로 바뀜 |
| TBL_NOTIFICATION_SETTING | `QUIET_ENABLED`, `SMS_FALLBACK`, `ADMIN_ID` 추가 |

## 시퀀스

- 24개 (V2에서 `SEQ_SCHEDULE` 추가). 코드·휴무일 테이블은 자연키라 시퀀스가 없습니다.
- `SEQ_SCHEDULE`, `SEQ_CHATBOT_SCENARIO` 삭제
- `SEQ_CHATBOT_TOPIC`, `SEQ_LOREBOOK`, `SEQ_CHATBOT_ACTION`, `SEQ_SAVED_REPLY`, `SEQ_RESERVATION_SETTING` 추가
- 모두 `INCREMENT BY 1 NOCACHE`. `schema.sql`은 1부터 시작합니다. TEAM03 DB의 시퀀스는 이미 값이 올라가 있습니다.

## 로그인 방식 (2026-11-08 결정)

- 고객과 관리자가 **같은 로그인 화면**을 쓰고, 로그인한 계정의 권한에 따라 고객 화면(`/home`)이나 관리자 화면(`/admin`)으로 이동합니다.
- 관리자는 **휴대전화 본인인증으로만** 로그인합니다. 인증한 번호를 `TBL_ADMIN.PHONE`(`IS_ACTIVE = 'Y'`)에서 찾습니다.
- 소셜 로그인(카카오·네이버·애플)은 고객 전용이므로 `TBL_SOCIAL`은 지금처럼 `MEMBER_ID`만 연결합니다.
- 이 방식에서는 `TBL_ADMIN.LOGIN_ID`, `PASSWORD`, `PW_CHANGED_AT`를 쓰지 않습니다. V2에서 컬럼은 남기고 필수만 풀었습니다(2026-11-08 결정).

## 명명 규칙

- 테이블: `TBL_` 접두사 (예: `TBL_MEMBER`)
- 시퀀스: `SEQ_` + 테이블명에서 `TBL_` 제외 (예: `SEQ_MEMBER`)
