# ERD (화면 대조 반영, 2026-11-08)

TEAM03 DB(테이블 27개)의 데이터 사전에서 만든 그림입니다. DDL은 [`db/schema.sql`](../../db/schema.sql), 이전 ERD(`CodeMove_ERD.pdf`)와 달라진 점은 [ERD_CHANGES.md](ERD_CHANGES.md)에 있습니다.

- 관계선: `||--o{` 필수 1:N, `|o--o{` 선택(NULL 허용 FK) 1:N, `||--o|` 1:1(유니크 FK)
- 관리자 FK(`ADMIN_ID`, 마지막 수정·담당 관리자)는 그림이 복잡해져 각 영역 그림에서 생략하고 맨 아래에 따로 그렸습니다.

## 회원·로그인

```mermaid
erDiagram
    TBL_MEMBER {
        NUMBER MEMBER_ID PK "회원ID"
        VARCHAR2 MEMBER_NAME "회원명"
        VARCHAR2 PHONE "휴대폰번호"
        CHAR IS_VERIFIED "인증여부"
        CHAR IS_ACTIVE "사용여부"
        DATE JOINED_AT "가입일"
        DATE TERMS_AGREED_AT "약관동의일시"
        DATE WITHDRAWN_AT "탈퇴일시"
    }
    TBL_SOCIAL {
        NUMBER SOCIAL_ID PK "연결ID"
        NUMBER MEMBER_ID FK "회원ID"
        CHAR LOGIN_TYPE "로그인방식"
        VARCHAR2 SOCIAL_NO "소셜번호"
        DATE CONNECTED_AT "연결일시"
    }
    TBL_ADMIN {
        NUMBER ADMIN_ID PK "관리자ID"
        VARCHAR2 LOGIN_ID "로그인아이디(사번)"
        VARCHAR2 PASSWORD "비밀번호(해시)"
        VARCHAR2 ADMIN_NAME "관리자명"
        VARCHAR2 PHONE "휴대폰번호"
        DATE PW_CHANGED_AT "비밀번호변경일시"
        CHAR IS_ACTIVE "사용여부"
        CHAR PUSH_ENABLED "알림받기"
        DATE CREATED_AT "등록일시"
    }
    TBL_MEMBER ||--o{ TBL_SOCIAL : "MEMBER_ID"
```

## 견적 요청·견적서

```mermaid
erDiagram
    TBL_MOVE_REQUEST {
        NUMBER REQUEST_ID PK "요청ID"
        NUMBER MEMBER_ID FK "회원ID"
        CHAR HOUSE_TYPE "주거형태"
        CHAR PACK_TYPE "포장방식"
        DATE MOVE_DATE "이사일"
        CHAR PREFER_TIME "희망시간대"
        VARCHAR2 FROM_ADDRESS "출발지주소"
        VARCHAR2 FROM_DETAIL "출발지상세주소"
        VARCHAR2 TO_ADDRESS "도착지주소"
        VARCHAR2 TO_DETAIL "도착지상세주소"
        NUMBER AI_CBM "AI산출부피"
        NUMBER AI_EST_AMOUNT "AI예상견적금액"
        NUMBER AI_EST_MIN "AI예상견적최소"
        NUMBER AI_EST_MAX "AI예상견적최대"
        NUMBER AI_ANALYSIS_SEC "AI분석시간(초)"
        NUMBER AI_CONFIDENCE "AI신뢰도"
        DATE REQUESTED_AT "요청일시"
        VARCHAR2 MOVE_TIME "희망시각(HH24:MI)"
        VARCHAR2 FROM_ZIPCODE "출발지우편번호"
        VARCHAR2 TO_ZIPCODE "도착지우편번호"
    }
    TBL_RE_VIDEO {
        NUMBER VIDEO_ID PK "영상ID"
        NUMBER REQUEST_ID FK "요청ID"
        VARCHAR2 FILE_NAME "파일명"
        VARCHAR2 FILE_PATH "파일경로"
        DATE UPLOADED_AT "업로드일시"
    }
    TBL_REQUEST_ITEM {
        NUMBER RE_ITEM_ID PK "요청품목ID"
        NUMBER REQUEST_ID FK "요청ID"
        CHAR CATEGORY "카테고리"
        VARCHAR2 ITEM_NAME "품목명"
        CHAR ITEM_SIZE "크기"
        NUMBER QUANTITY "수량"
        NUMBER VOLUME "부피(수량 반영)"
        NUMBER CONFIDENCE "AI신뢰도"
        CHAR NEEDS_REVIEW "확인필요여부"
        CHAR SOURCE "등록출처"
    }
    TBL_QUOTE {
        NUMBER QUOTE_ID PK "견적서ID"
        VARCHAR2 QUOTE_NO "견적번호"
        NUMBER REQUEST_ID FK "요청ID"
        NUMBER MEMBER_ID FK "회원ID"
        NUMBER ADMIN_ID FK "승인관리자ID"
        CHAR SOURCE_TYPE "견적서출처"
        CHAR STATUS "견적상태"
        VARCHAR2 COMPANY_NAME "업체명"
        VARCHAR2 CONTACT_NAME "담당자(고객명)"
        VARCHAR2 CONTACT_PHONE "전화번호(고객)"
        DATE ESTIMATE_DATE "견적일"
        VARCHAR2 FROM_ADDRESS "출발지"
        VARCHAR2 TO_ADDRESS "도착지"
        DATE WORK_DATE "작업일"
        VARCHAR2 WORK_TIME "작업시각"
        DATE TRANSPORT_DATE "운송일"
        CHAR WORK_TYPE "작업내용"
        CHAR FROM_LADDER_USE "출발지사다리차"
        CHAR TO_LADDER_USE "도착지사다리차"
        CHAR FROM_ELEVATOR_USE "출발지승강기"
        CHAR TO_ELEVATOR_USE "도착지승강기"
        CHAR FROM_STAIRS_USE "출발지계단작업"
        CHAR TO_STAIRS_USE "도착지계단작업"
        NUMBER VOLUME_TON "이사물량(톤)"
        VARCHAR2 NOTE "비고"
        NUMBER MOVING_COST "이사비용"
        NUMBER FROM_LADDER_FLOOR "출발지사다리층수"
        NUMBER FROM_LADDER_COST "출발지사다리비용"
        NUMBER TO_LADDER_FLOOR "도착지사다리층수"
        NUMBER TO_LADDER_COST "도착지사다리비용"
        NUMBER TOTAL_AMOUNT "총비용"
        NUMBER DEPOSIT_AMOUNT "계약금"
        NUMBER BALANCE_AMOUNT "잔금"
        VARCHAR2 DOCUMENT_PATH "견적서파일경로"
        DATE APPROVED_AT "승인일시"
        DATE EXPIRES_AT "만료일시"
        DATE UPDATED_AT "수정일시"
        VARCHAR2 ITEM_GROUP1 "품목그룹1제목"
        VARCHAR2 ITEM_GROUP2 "품목그룹2제목"
        VARCHAR2 ITEM_GROUP3 "품목그룹3제목"
        VARCHAR2 ITEM_GROUP4 "품목그룹4제목"
    }
    TBL_QUOTE_ITEM {
        NUMBER QT_ITEM_ID PK "견적품목ID"
        NUMBER QUOTE_ID FK "견적서ID"
        CHAR CATEGORY "카테고리"
        VARCHAR2 ITEM_NAME "품목명"
        NUMBER QUANTITY "수량"
        VARCHAR2 UNIT "단위"
        NUMBER SORT_ORDER "정렬순서"
    }
    TBL_QUOTE_OPTION {
        NUMBER QT_OPTION_ID PK "견적옵션ID"
        NUMBER QUOTE_ID FK "견적서ID"
        CHAR OPTION_CODE "옵션코드"
        CHAR CHOICE1_YN "선택1여부"
        CHAR CHOICE2_YN "선택2여부"
        NUMBER OPTION_COST "옵션비용"
    }
    TBL_MOVE_REQUEST |o--o| TBL_QUOTE : "REQUEST_ID"
    TBL_QUOTE ||--o{ TBL_QUOTE_ITEM : "QUOTE_ID"
    TBL_QUOTE ||--o{ TBL_QUOTE_OPTION : "QUOTE_ID"
    TBL_MOVE_REQUEST ||--o{ TBL_REQUEST_ITEM : "REQUEST_ID"
    TBL_MOVE_REQUEST ||--o{ TBL_RE_VIDEO : "REQUEST_ID"
```

다른 영역 참조: `TBL_MOVE_REQUEST.MEMBER_ID` → `TBL_MEMBER`, `TBL_QUOTE.MEMBER_ID` → `TBL_MEMBER`

## 예약·후기·일정

```mermaid
erDiagram
    TBL_RESERVATION {
        NUMBER RESERVATION_ID PK "예약ID"
        VARCHAR2 RESERVATION_NO "예약번호"
        NUMBER REQUEST_ID FK "요청ID"
        CHAR STATUS "예약상태"
        CHAR PAY_STATUS "결제상태"
        DATE MOVE_DATE "이사일"
        VARCHAR2 MOVE_TIME "이사시각"
        VARCHAR2 MOVE_MEMO "요청사항"
        DATE APPLIED_AT "신청일시"
        DATE DEPOSIT_PAID_AT "계약금결제일시"
        DATE CANCELLED_AT "취소일시"
        CHAR CANCELLED_BY "취소주체"
    }
    TBL_REVIEW {
        NUMBER REVIEW_ID PK "후기ID"
        NUMBER RESERVATION_ID FK "예약ID"
        NUMBER RATING "별점"
        VARCHAR2 CONTENT "후기내용"
        DATE CREATED_AT "작성일시"
        VARCHAR2 REPLY_CONTENT "관리자답변"
        DATE REPLIED_AT "답변일시"
        NUMBER ADMIN_ID FK "관리자ID"
    }
    TBL_REVIEW_PHOTO {
        NUMBER PHOTO_ID PK "사진ID"
        NUMBER REVIEW_ID FK "후기ID"
        VARCHAR2 FILE_PATH "파일경로"
        NUMBER SORT_ORDER "순서"
    }
    TBL_SCHEDULE {
        NUMBER SCHEDULE_ID PK "일정ID"
        VARCHAR2 TITLE "일정명"
        VARCHAR2 CATEGORY "분류"
        DATE SCHEDULE_DATE "일정날짜"
        VARCHAR2 SCHEDULE_TIME "일정시각(HH24:MI)"
        VARCHAR2 PLACE "장소"
        NUMBER MEMBER_ID FK "연결회원ID"
        NUMBER RESERVATION_ID FK "연결예약ID"
        NUMBER ADMIN_ID FK "등록관리자ID"
        DATE CREATED_AT "등록일시"
    }
    TBL_RESERVATION_SETTING {
        NUMBER SETTING_ID PK "설정ID"
        NUMBER DAILY_CAPACITY "하루최대예약건수"
        VARCHAR2 CLOSED_WEEKDAYS "정기휴무요일(0일~6토,쉼표구분)"
        CHAR HOLIDAY_CLOSED "공휴일휴무"
        DATE UPDATED_AT "수정일시"
        NUMBER ADMIN_ID FK "관리자ID"
    }
    TBL_CLOSED_DAY {
        DATE CLOSED_DATE PK "휴무날짜"
        DATE UPDATED_AT "수정일시"
        NUMBER ADMIN_ID FK "관리자ID"
        CHAR CLOSED_TYPE "날짜상태(CLOSED_TYPE)"
    }
    TBL_RESERVATION ||--o| TBL_REVIEW : "RESERVATION_ID"
    TBL_REVIEW ||--o{ TBL_REVIEW_PHOTO : "REVIEW_ID"
    TBL_RESERVATION |o--o{ TBL_SCHEDULE : "RESERVATION_ID"
```

다른 영역 참조: `TBL_RESERVATION.REQUEST_ID` → `TBL_MOVE_REQUEST`, `TBL_SCHEDULE.MEMBER_ID` → `TBL_MEMBER`

## 챗봇·채팅

```mermaid
erDiagram
    TBL_CHATBOT_TOPIC {
        NUMBER TOPIC_ID PK "주제ID"
        VARCHAR2 TITLE "주제제목"
        VARCHAR2 INTRO "소개"
        CHAR TOPIC_TYPE "주제유형"
        CHAR IS_ACTIVE "사용여부"
        DATE UPDATED_AT "수정일시"
        NUMBER ADMIN_ID FK "관리자ID"
    }
    TBL_LOREBOOK {
        NUMBER LOREBOOK_ID PK "로어북ID"
        NUMBER TOPIC_ID FK "주제ID"
        VARCHAR2 KEYWORDS "키워드"
        VARCHAR2 CONTENT "AI전달내용"
        NUMBER SORT_ORDER "정렬순서"
        DATE UPDATED_AT "수정일시"
    }
    TBL_CHATBOT_ACTION {
        NUMBER ACTION_ID PK "버튼ID"
        NUMBER TOPIC_ID FK "주제ID"
        VARCHAR2 LABEL "버튼명"
        CHAR ACTION_TYPE "동작유형"
        VARCHAR2 ACTION_VALUE "동작값(주소/질문)"
        NUMBER SORT_ORDER "정렬순서"
    }
    TBL_QUICK_MENU {
        NUMBER MENU_ID PK "퀵메뉴ID"
        NUMBER TOPIC_ID FK "주제ID"
        VARCHAR2 MENU_NAME "메뉴명(버튼문구)"
        NUMBER SORT_ORDER "노출순서"
        CHAR IS_ACTIVE "사용여부"
        NUMBER ADMIN_ID FK "관리자ID"
    }
    TBL_CHAT_ROOM {
        NUMBER ROOM_ID PK "채팅방ID"
        NUMBER MEMBER_ID FK "회원ID"
        NUMBER ADMIN_ID FK "담당관리자ID"
        CHAR STATUS "채팅상태"
        DATE STARTED_AT "시작일시"
        DATE WAITING_AT "상담요청일시"
        DATE ENDED_AT "종료일시"
    }
    TBL_CHAT_MESSAGE {
        NUMBER MESSAGE_ID PK "메시지ID"
        NUMBER ROOM_ID FK "채팅방ID"
        NUMBER ADMIN_ID FK "관리자ID"
        NUMBER TOPIC_ID FK "주제ID"
        CHAR SENDER_TYPE "발신자구분"
        CHAR MESSAGE_TYPE "메시지유형"
        VARCHAR2 CONTENT "메시지내용"
        VARCHAR2 ATTACHMENT_PATH "첨부파일경로"
        VARCHAR2 ATTACHMENT_NAME "첨부파일명"
        CHAR IS_READ "읽음여부"
        DATE SENT_AT "발송일시"
    }
    TBL_CHAT_SETTING {
        NUMBER SETTING_ID PK "설정ID"
        CHAR BOT_ENABLED "챗봇사용"
        CHAR CONSULT_ENABLED "상담운영(상담원연결받기)"
        VARCHAR2 START_TIME "운영시작시간"
        VARCHAR2 END_TIME "운영종료시간"
        VARCHAR2 GREETING "인사말"
        VARCHAR2 DELAY_MESSAGE "지연안내문구"
        VARCHAR2 AWAY_MESSAGE "부재안내문구"
        DATE UPDATED_AT "수정일시"
        NUMBER ADMIN_ID FK "관리자ID"
    }
    TBL_SAVED_REPLY {
        NUMBER REPLY_ID PK "답변ID"
        VARCHAR2 TITLE "제목"
        VARCHAR2 CONTENT "내용"
        DATE UPDATED_AT "수정일시"
        NUMBER ADMIN_ID FK "관리자ID"
    }
    TBL_CHATBOT_TOPIC ||--o{ TBL_CHATBOT_ACTION : "TOPIC_ID"
    TBL_CHATBOT_TOPIC |o--o{ TBL_CHAT_MESSAGE : "TOPIC_ID"
    TBL_CHAT_ROOM ||--o{ TBL_CHAT_MESSAGE : "ROOM_ID"
    TBL_CHATBOT_TOPIC ||--o{ TBL_LOREBOOK : "TOPIC_ID"
    TBL_CHATBOT_TOPIC ||--o{ TBL_QUICK_MENU : "TOPIC_ID"
```

다른 영역 참조: `TBL_CHAT_ROOM.MEMBER_ID` → `TBL_MEMBER`

## 알림

```mermaid
erDiagram
    TBL_NOTIFICATION {
        NUMBER NOTIFICATION_ID PK "알림ID"
        CHAR RECEIVER_TYPE "수신자구분"
        NUMBER MEMBER_ID FK "회원ID(수신·관련)"
        CHAR NTF_TYPE "알림분류"
        CHAR NTF_DETAIL "알림세부종류"
        VARCHAR2 NTF_TITLE "알림제목"
        VARCHAR2 NTF_CONTENT "알림내용"
        VARCHAR2 LINK_URL "이동링크"
        CHAR IS_IMPORTANT "중요여부"
        CHAR IS_READ "읽음여부"
        CHAR NTF_CHANNEL "발송채널"
        DATE CREATED_AT "발생일시"
        DATE SENT_AT "발송일시"
    }
    TBL_NOTIFICATION_SETTING {
        NUMBER SETTING_ID PK "설정ID"
        CHAR IS_ACTIVE "자동메시징사용"
        CHAR QUIET_ENABLED "에티켓시간사용"
        VARCHAR2 QUIET_START "에티켓시작시각"
        VARCHAR2 QUIET_END "에티켓종료시각"
        CHAR SMS_FALLBACK "문자대체발송"
        DATE UPDATED_AT "수정일시"
        NUMBER ADMIN_ID FK "관리자ID"
    }
```

다른 영역 참조: `TBL_NOTIFICATION.MEMBER_ID` → `TBL_MEMBER`

## 공통코드

```mermaid
erDiagram
    TBL_CODE_GROUP {
        VARCHAR2 GROUP_CODE PK "그룹코드"
        VARCHAR2 GROUP_NAME "그룹명"
        CHAR IS_ACTIVE "사용여부"
    }
    TBL_CODE {
        VARCHAR2 GROUP_CODE PK,FK "그룹코드"
        CHAR CODE PK "코드"
        VARCHAR2 CODE_NAME "코드명(관리자)"
        VARCHAR2 USER_NAME "코드명(사용자)"
        NUMBER SORT_ORDER "정렬순서"
        CHAR IS_ACTIVE "사용여부"
    }
    TBL_CODE_GROUP ||--o{ TBL_CODE : "GROUP_CODE"
```

## 관리자 참조

```mermaid
erDiagram
    TBL_ADMIN |o--o{ TBL_CHATBOT_TOPIC : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_CHAT_MESSAGE : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_CHAT_ROOM : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_CHAT_SETTING : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_CLOSED_DAY : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_NOTIFICATION_SETTING : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_QUICK_MENU : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_QUOTE : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_RESERVATION_SETTING : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_REVIEW : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_SAVED_REPLY : "ADMIN_ID"
    TBL_ADMIN |o--o{ TBL_SCHEDULE : "ADMIN_ID"
```

## 테이블 목록

| 영역 | 테이블 | 설명 |
| --- | --- | --- |
| 회원·로그인 | `TBL_MEMBER` | 회원 |
| 회원·로그인 | `TBL_SOCIAL` | 간편로그인 |
| 회원·로그인 | `TBL_ADMIN` | 관리자 |
| 견적 요청·견적서 | `TBL_MOVE_REQUEST` | 견적요청 |
| 견적 요청·견적서 | `TBL_RE_VIDEO` | 요청영상 |
| 견적 요청·견적서 | `TBL_REQUEST_ITEM` | 요청품목 |
| 견적 요청·견적서 | `TBL_QUOTE` | 견적서 |
| 견적 요청·견적서 | `TBL_QUOTE_ITEM` | 견적품목 |
| 견적 요청·견적서 | `TBL_QUOTE_OPTION` | 견적옵션 |
| 예약·후기·일정 | `TBL_RESERVATION` | 예약 |
| 예약·후기·일정 | `TBL_REVIEW` | 후기 |
| 예약·후기·일정 | `TBL_REVIEW_PHOTO` | 후기사진 |
| 예약·후기·일정 | `TBL_SCHEDULE` | 관리자일정 |
| 예약·후기·일정 | `TBL_RESERVATION_SETTING` | 예약설정 |
| 예약·후기·일정 | `TBL_CLOSED_DAY` | 임시휴무일 |
| 챗봇·채팅 | `TBL_CHATBOT_TOPIC` | 챗봇주제 |
| 챗봇·채팅 | `TBL_LOREBOOK` | 로어북 |
| 챗봇·채팅 | `TBL_CHATBOT_ACTION` | 챗봇답변버튼 |
| 챗봇·채팅 | `TBL_QUICK_MENU` | 퀵메뉴 |
| 챗봇·채팅 | `TBL_CHAT_ROOM` | 채팅방 |
| 챗봇·채팅 | `TBL_CHAT_MESSAGE` | 채팅메시지 |
| 챗봇·채팅 | `TBL_CHAT_SETTING` | 챗봇설정 |
| 챗봇·채팅 | `TBL_SAVED_REPLY` | 자주쓰는답변 |
| 알림 | `TBL_NOTIFICATION` | 알림 |
| 알림 | `TBL_NOTIFICATION_SETTING` | 알림설정 |
| 공통코드 | `TBL_CODE_GROUP` | 공통코드그룹 |
| 공통코드 | `TBL_CODE` | 공통코드 |
