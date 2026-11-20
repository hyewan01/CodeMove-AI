# AI 영상 견적 설계 (2026-11-20 제안)

고객이 찍은 집 영상으로 **품목 → 부피(CBM) → 차량(톤) → 견적 금액**을 내는 방법입니다. [codemove_video_test_guide.md](../../codemove_video_test_guide.md)의 1~5단계에 해당합니다.

- 상태: **제안**. TEAM03 DB에는 아직 적용하지 않았습니다. DDL은 [V4__item_spec_and_fee.ddl.sql](../../db/migration/V4__item_spec_and_fee.ddl.sql), 초기 데이터는 [V4__item_spec_and_fee.data.sql](../../db/migration/V4__item_spec_and_fee.data.sql)에 있습니다.
- 값의 원본은 [data/](data/) 폴더의 CSV입니다. 값을 바꿀 때는 CSV만 고치고 `py -3 db/tools/gen_v4_data.py`로 data SQL을 다시 만듭니다.
- `[업체 확인]`이 붙은 값은 인터넷 시세나 가정으로 채운 값입니다. 대표님께 확인받기 전에는 실제 견적에 쓰지 않습니다. 확인할 질문은 [7장](#7-업체대표님-확인-질문)에 모았습니다.

## 1. 전체 흐름

```mermaid
flowchart LR
    V[고객 영상] --> C{업로드 검사<br/>형식·용량·길이}
    C -- 실패 --> E1[다시 찍기 안내]
    C --> AI[멀티모달 AI<br/>품목표를 주고<br/>코드로만 답하게 함]
    AI --> J{JSON 검사}
    J -- 실패 --> R[1회 재시도 → 실패 기록]
    J --> M[표준 규격 매핑<br/>TBL_ITEM_SPEC]
    M --> U[고객 품목 확인·수정<br/>TBL_REQUEST_ITEM]
    U --> T[CBM 합계 → 톤<br/>TBL_TRUCK]
    T --> F[요금 계산<br/>TBL_FEE_*]
    F --> Q[예상 견적 → 관리자 검토]
    AI -. 토큰·비용·시간 .-> L[(TBL_AI_ANALYSIS)]
```

AI는 **보이는 품목과 개수, 크기 등급만** 답합니다. 부피·톤·금액은 서버가 DB 표로 계산합니다. 그래서 같은 품목 목록이면 항상 같은 금액이 나오고, 요금이 바뀌어도 AI 프롬프트는 고칠 필요가 없습니다.

## 2. 영상 분석 AI를 만드는 방법

AI 모델을 직접 학습시키지 않고, **영상을 그대로 받는 멀티모달 생성형 AI API**를 씁니다. 가이드 문서도 이 방식을 전제로 합니다.

### 2.1 핵심: AI에게 우리 품목표를 주고 그중에서만 고르게 한다

AI가 "양문형 냉장고", "큰 냉장고", "LG 냉장고"처럼 제각각 답하면 DB와 맞추기 어렵습니다. 그래서 프롬프트에 `TBL_ITEM` + `TBL_ITEM_SPEC`의 **품목명·크기코드·판정기준**을 넣고, 응답은 그 코드로만 받습니다.

프롬프트에 넣는 품목표 (DB에서 만들어 붙임, 31개 품목 × 3개 크기):

```text
품목명 | 01 | 02 | 03
침대 | 싱글: 폭 1m 안팎, 1인용 | 슈퍼싱글~퀸: 폭 1.1~1.6m | 킹 이상: 폭 1.7m 이상 또는 수납·헤드 대형 프레임
냉장고 | 1도어·소형: 허리 높이 이하 | 일반 2도어: 위아래 2도어 | 양문형·4도어: 좌우로 열리는 대형
...
```

지시 사항 (프롬프트 버전 `v1`):

1. 영상에 **실제로 보이는** 이삿짐만 적는다. 보이지 않는 물건을 추측해서 넣지 않는다.
2. 같은 물건이 여러 장면에 나와도 **한 번만** 센다. 방을 옮겨 다시 나오면 위치·색·모양으로 같은 물건인지 판단한다.
3. 품목표에 있으면 `item_name`과 `size_code`를 표 그대로 쓴다. 없으면 `item_name`은 `"기타"`, `detected_name`에 본 이름을 적는다.
4. 크기가 애매하면 가까운 등급을 고르고 `needs_review: true`로 둔다.
5. 붙박이장·시스템 에어컨처럼 건물에 고정된 것은 넣지 않는다.
6. 작은 짐(책, 옷, 그릇)은 낱개로 세지 않고 이사박스 개수로 추정한다.

응답 형식 (API의 JSON 스키마 강제 기능을 사용):

```json
{
  "video_ok": true,
  "reject_reason": null,
  "rooms": ["거실", "안방", "주방"],
  "items": [
    { "item_name": "냉장고", "size_code": "03", "quantity": 1,
      "detected_name": "양문형 냉장고", "evidence": "주방, 좌우로 열리는 문 2개", "needs_review": false },
    { "item_name": "소파", "size_code": "02", "quantity": 1,
      "detected_name": "3인용 패브릭 소파", "evidence": "거실, 쿠션 3개", "needs_review": false },
    { "item_name": "이사박스", "size_code": "02", "quantity": 12,
      "detected_name": "책·옷 등 잔짐", "evidence": "책장 2칸, 행거 옷", "needs_review": true }
  ]
}
```

- `video_ok: false`면 `reject_reason`에 `"집 안 영상 아님"`, `"너무 어두움"` 같은 이유를 적게 합니다 → [6장 예외 처리](#6-예외-처리).
- 서버 검사: `item_name`은 품목표 또는 `"기타"`, `size_code`는 `01~03`, `quantity`는 1~200 정수. 어긋나면 그 품목은 `needs_review`로 바꿉니다.
- 가이드 4장대로 AI가 주는 신뢰도 숫자는 확률로 보지 않습니다. 대신 `needs_review`와 매핑 방식으로 확인 필요 여부를 정합니다.

### 2.2 모델 선택

| 후보 | 방식 | 장점 | 단점 |
| --- | --- | --- | --- |
| **Gemini (1순위)** | 영상 파일을 그대로 전송 | 영상 입력이 기본 기능. 프레임을 직접 뽑을 필요 없음. JSON 스키마 강제 지원 | 영상 길이에 비례해 토큰이 늘어남 |
| 이미지 모델(Claude·GPT 등) | 서버에서 ffmpeg로 1~2초마다 프레임을 뽑아 이미지 여러 장으로 전송 | 모델을 바꿔 비교하기 쉬움. 보낼 장면 수를 직접 조절 | 프레임 추출 단계가 더 필요. 장면 사이 중복 판단이 약할 수 있음 |

- 1차 구현은 Gemini 하나로 하고, 비교 모델은 7단계(반복 테스트)에서 같은 영상으로 돌려 봅니다.
- 영상 토큰은 대략 **영상 1초당 수백 토큰**입니다(Gemini 기본 해상도 기준, 낮은 해상도 옵션이 있음). 1분 영상이면 입력 2만 토큰 안팎이 됩니다. 정확한 값은 응답의 사용량(usage) 정보를 `TBL_AI_ANALYSIS`에 그대로 저장해서 확인합니다. 모델명과 단가는 공식 요금표를 보고 `PRICE_IN`·`PRICE_OUT`·`PRICE_DATE`에 기록합니다.
- 고객 촬영 안내: **1~3분, 방마다 천천히 한 바퀴, 불 켜고 촬영, 문 닫힌 수납장은 열어서**. 업로드 한도는 200MB·5분으로 시작해 실측 후 조정합니다.
- API 키는 서버 환경변수로만 관리합니다(`application.yml`에 직접 쓰지 않음).

### 2.3 매핑 순서 (ItemMappingService)

AI가 품목표 코드로 답하므로 대부분 1번에서 끝납니다.

| 순서 | 방법 | `MAPPING_TYPE` | 확인 필요 |
| --- | --- | --- | --- |
| 1 | `item_name` = `TBL_ITEM.ITEM_NAME` 정확 일치 | 01 정확 일치 | AI가 `needs_review`라고 한 것만 |
| 2 | `detected_name`을 `TBL_ITEM_ALIAS`에서 찾음 (별칭에 크기가 있으면 크기도 채움) | 02 별칭 | 크기를 별칭에서 못 정하면 Y |
| 3 | 품목은 찾았는데 크기 코드가 없거나 잘못됨 → 중형(02)으로 두고 | 03 AI 후보 | Y |
| 4 | 못 찾음(`"기타"`) → 규격 없이 저장, 부피 0으로 두고 관리자에게 넘김 | 04 미매핑 | Y |
| - | 고객·관리자가 화면에서 고침 | 05 사용자 수정 | N |

매핑 결과는 `TBL_REQUEST_ITEM`에 `SPEC_ID`, `DETECTED_NAME`(AI 원본 이름), `MAPPING_TYPE`을 함께 저장합니다. 기존 컬럼(`ITEM_NAME`, `ITEM_SIZE`, `VOLUME`, `NEEDS_REVIEW`, `SOURCE`)은 지금처럼 채웁니다.

## 3. 테이블 설계

모두 새로 추가하는 테이블이고, 기존 테이블은 `TBL_REQUEST_ITEM`에 NULL 허용 컬럼 3개만 추가합니다. 코드는 기존 공통코드(`ITEM_CATEGORY`, `ITEM_SIZE`, `PACK_TYPE`, `QUOTE_OPTION`)를 그대로 씁니다.

```mermaid
erDiagram
    TBL_ITEM {
        NUMBER ITEM_ID PK "품목ID"
        VARCHAR2 ITEM_NAME UK "품목명"
        CHAR CATEGORY "카테고리(ITEM_CATEGORY)"
        CHAR OPTION_CODE "특수옵션(QUOTE_OPTION)"
        NUMBER SORT_ORDER "정렬순서"
        CHAR IS_ACTIVE "사용여부"
    }
    TBL_ITEM_SPEC {
        NUMBER SPEC_ID PK "규격ID"
        NUMBER ITEM_ID FK "품목ID"
        CHAR ITEM_SIZE "크기(ITEM_SIZE)"
        VARCHAR2 SIZE_NAME "크기명"
        VARCHAR2 SIZE_RULE "판정기준"
        NUMBER WIDTH_CM "가로"
        NUMBER DEPTH_CM "깊이"
        NUMBER HEIGHT_CM "높이"
        NUMBER CBM "기준부피"
        NUMBER WEIGHT_KG "기준무게"
        CHAR BASIS "근거구분(SPEC_BASIS)"
    }
    TBL_SPEC_SOURCE {
        NUMBER SOURCE_ID PK "출처ID"
        NUMBER SPEC_ID FK "규격ID"
        VARCHAR2 BRAND "브랜드"
        VARCHAR2 MODEL_NAME "모델명"
        NUMBER CBM "부피"
        VARCHAR2 SOURCE_URL "출처URL"
    }
    TBL_ITEM_ALIAS {
        NUMBER ALIAS_ID PK "별칭ID"
        VARCHAR2 ALIAS_NAME UK "별칭"
        NUMBER ITEM_ID FK "품목ID"
        CHAR ITEM_SIZE "크기"
    }
    TBL_REQUEST_ITEM {
        NUMBER RE_ITEM_ID PK "요청품목ID"
        NUMBER SPEC_ID FK "규격ID(추가)"
        VARCHAR2 DETECTED_NAME "AI원본인식명(추가)"
        CHAR MAPPING_TYPE "매핑방식(추가)"
    }
    TBL_TRUCK {
        NUMBER TRUCK_ID PK "차량ID"
        NUMBER TON UK "톤수"
        NUMBER LOAD_CBM "적재기준부피"
        NUMBER MAX_CBM "최대적재부피"
    }
    TBL_FEE_BASE {
        NUMBER FEE_ID PK "요금ID"
        NUMBER TRUCK_ID FK "차량ID"
        CHAR PACK_TYPE "포장방식(PACK_TYPE)"
        NUMBER BASE_AMOUNT "기본요금"
        NUMBER WORKERS "작업인원"
    }
    TBL_FEE_DISTANCE {
        NUMBER FEE_ID PK "요금ID"
        NUMBER TRUCK_ID FK "차량ID"
        NUMBER FROM_KM "구간시작"
        NUMBER TO_KM "구간끝"
        NUMBER ADD_AMOUNT "추가금액"
        NUMBER PER_KM "초과km당"
    }
    TBL_FEE_OPTION {
        NUMBER FEE_ID PK "요금ID"
        CHAR OPTION_CODE "옵션코드(QUOTE_OPTION)"
        NUMBER CHOICE_NO "선택번호(1·2)"
        VARCHAR2 CHOICE_NAME "선택명"
        NUMBER AMOUNT "금액"
        VARCHAR2 UNIT "단위"
    }
    TBL_FEE_LADDER {
        NUMBER FEE_ID PK "요금ID"
        NUMBER FROM_FLOOR UK "시작층"
        NUMBER TO_FLOOR "끝층"
        NUMBER AMOUNT "금액"
    }
    TBL_FEE_SURCHARGE {
        CHAR SURCHARGE_CODE PK "할증코드(SURCHARGE_TYPE)"
        NUMBER RATE_PCT "할증률"
        CHAR IS_ACTIVE "사용여부"
    }
    TBL_AI_ANALYSIS {
        NUMBER ANALYSIS_ID PK "분석ID"
        NUMBER REQUEST_ID FK "요청ID"
        NUMBER VIDEO_ID FK "영상ID"
        VARCHAR2 MODEL_NAME "모델명"
        VARCHAR2 PROMPT_VERSION "프롬프트버전"
        NUMBER INPUT_TOKENS "입력토큰"
        NUMBER OUTPUT_TOKENS "출력토큰"
        NUMBER EST_COST "추정비용"
        NUMBER TOTAL_MS "전체시간"
        CHAR IS_SUCCESS "성공여부"
        CHAR ERROR_CODE "실패코드(ANALYSIS_ERROR)"
        CLOB RAW_JSON "AI원본응답"
    }
    TBL_ITEM ||--o{ TBL_ITEM_SPEC : "ITEM_ID"
    TBL_ITEM_SPEC ||--o{ TBL_SPEC_SOURCE : "SPEC_ID"
    TBL_ITEM ||--o{ TBL_ITEM_ALIAS : "ITEM_ID"
    TBL_ITEM_SPEC |o--o{ TBL_REQUEST_ITEM : "SPEC_ID"
    TBL_TRUCK ||--o{ TBL_FEE_BASE : "TRUCK_ID"
    TBL_TRUCK ||--o{ TBL_FEE_DISTANCE : "TRUCK_ID"
```

다른 영역 참조: `TBL_AI_ANALYSIS.REQUEST_ID` → `TBL_MOVE_REQUEST`, `VIDEO_ID` → `TBL_RE_VIDEO`. 요금 테이블의 `ADMIN_ID`(수정 관리자) → `TBL_ADMIN`.

| 팀 메모 항목 | 테이블 | 데이터 파일 | 건수 |
| --- | --- | --- | --- |
| 어떤 품목이 있는지 (가전·가구 카테고리 매핑) | `TBL_ITEM` | [item-master.csv](data/item-master.csv) | 31 |
| 품목 크기별 기준값(CBM) | `TBL_ITEM_SPEC` | 〃 | 93 |
| 기준값 근거(제조사 모델 치수) | `TBL_SPEC_SOURCE` | [cbm-sources.csv](data/cbm-sources.csv) | 89 |
| AI·고객이 쓰는 다른 이름 | `TBL_ITEM_ALIAS` | [item-alias.csv](data/item-alias.csv) | 40 |
| CBM → 톤 환산(차량 적재 기준) | `TBL_TRUCK` | [truck-capacity.csv](data/truck-capacity.csv) | 3 |
| 기본 요금: 톤 × 포장 방식 | `TBL_FEE_BASE` | [fee-base.csv](data/fee-base.csv) | 9 |
| 특수품목 요금 | `TBL_FEE_OPTION` | [fee-option.csv](data/fee-option.csv) | 16 |
| 거리 추가 요금 | `TBL_FEE_DISTANCE` | [fee-distance.csv](data/fee-distance.csv) | 15 |
| 날짜 할증(손 없는 날 등) | `TBL_FEE_SURCHARGE` | [fee-surcharge.csv](data/fee-surcharge.csv) | 4 |
| 사다리차 | `TBL_FEE_LADDER` | [fee-ladder.csv](data/fee-ladder.csv) | 8 |
| 영상 분석 토큰·비용·시간 기록 | `TBL_AI_ANALYSIS` | - | - |

새 공통코드 그룹: `SPEC_BASIS`(규격 근거), `SURCHARGE_TYPE`(날짜 할증), `MAPPING_TYPE`(매핑 방식), `ANALYSIS_ERROR`(분석 실패).

설계 메모:

- **특수품목 요금**은 견적서 양식의 옵션 8종(`QUOTE_OPTION`)과 선택1·선택2(예: 에어컨 탈착/설치)를 그대로 따릅니다. 계산 결과는 기존 `TBL_QUOTE_OPTION.OPTION_COST`에 들어갑니다. 품목에 `OPTION_CODE`가 있으면(피아노 01, 안마의자 07, 에어컨 08) AI가 그 품목을 찾았을 때 옵션을 자동으로 켭니다.
- **거리 요금**은 택시처럼 km마다 오르는 방식이 아니라, 업계 시세가 구간제(50km까지 기본 포함, 50~100km, 100~150km …)라서 구간 표로 만들었습니다. 마지막 구간(200km 초과)만 km당 단가를 씁니다.
- **사다리차**는 출발지와 도착지에 따로 적용하고, 결과는 기존 `TBL_QUOTE.FROM_LADDER_COST`·`TO_LADDER_COST`에 들어갑니다. 25층 이상은 금액을 비워 두고 "별도 협의"로 표시합니다.
- **무게**: 가이드는 무게 산정도 요구하지만, 팀원 표에 무게 자료가 없어 `WEIGHT_KG`는 비워 두었습니다. 견적 금액에는 쓰지 않으므로 나중에 채워도 됩니다.
- **손 없는 날**: 지금 일정 화면([AdminScheduleController.java:39](../../src/main/java/com/codemove/admin/web/AdminScheduleController.java#L39))은 양력 7·8·17·18·27·28일로 표시하는 시연값입니다. 실제로는 **음력 끝자리가 9·0인 날**이므로, 음력 변환(한국천문연구원 음양력 API 또는 음력 변환 라이브러리)을 붙여 달력 표시와 할증 계산이 같은 함수를 쓰게 합니다.

## 4. 견적 계산식

```text
① 총 CBM      = Σ (품목 규격 CBM × 수량)                         TBL_ITEM_SPEC
② 차량        = MAX_CBM ≥ 총 CBM 인 가장 작은 톤                   TBL_TRUCK
                (5톤 MAX_CBM 초과면 5톤 + 남은 CBM으로 다시 ②)
③ 기본 요금   = 차량 톤 × 포장 방식                              TBL_FEE_BASE
④ 거리 추가   = 이동 거리 구간 금액 (+ 200km 초과분 × km당 단가)    TBL_FEE_DISTANCE
⑤ 사다리차    = 출발지 층수 금액 + 도착지 층수 금액 (승강기 쓰면 0)   TBL_FEE_LADDER
⑥ 특수품목    = Σ 옵션 선택별 금액 × 단위 수량                    TBL_FEE_OPTION
⑦ 날짜 할증   = ③ × 해당 할증률 중 가장 큰 값                     TBL_FEE_SURCHARGE
예상 견적     = ③ + ④ + ⑤ + ⑥ + ⑦  (만 원 단위 반올림)
```

- `TBL_TRUCK.LOAD_CBM`·`MAX_CBM`은 트럭 적재함 부피에 적재율(빈 공간)을 이미 반영한 값입니다. 그래서 ①에는 품목 부피를 그대로 더합니다.
- 할증을 기본 요금에만 적용할지, 여러 할증을 더할지(예: 손 없는 날 + 주말) 가장 큰 것 하나만 적용할지는 [7장](#7-업체대표님-확인-질문)에서 확인합니다. 위 식은 "기본 요금에만, 가장 큰 것 하나"로 가정했습니다.
- 이동 거리는 카카오 길찾기 API(자동차 경로 거리)로 구합니다. API를 못 쓰면 우편번호 좌표의 직선거리 × 1.3으로 대신하고 확인 필요로 표시합니다.
- 예상 범위(최소~최대): `needs_review` 품목을 한 단계 작은 크기로 계산한 값 ~ 한 단계 큰 크기로 계산한 값. 확인 필요 품목이 없으면 ±5%.

### 계산 예시

서울 → 성남 25km, 포장이사, 토요일, 출발지 8층(사다리차), 도착지 승강기. 품목은 지금 견적 화면 시연값([AiDemo.java](../../src/main/java/com/codemove/customer/web/AiDemo.java))과 비슷하게 잡았습니다.

| 품목 | 크기 | 수량 | CBM |
| --- | --- | --- | --- |
| 냉장고 | 03 양문형·4도어 | 1 | 1.492 |
| 침대 | 02 슈퍼싱글~퀸 | 1 | 1.447 |
| 소파 | 02 3인 | 1 | 1.804 |
| 이사박스 | 02 중 | 12 | 0.924 |
| 세탁기 | 02 일반 | 1 | 0.537 |
| 식탁·테이블 | 02 4인 | 1 | 0.720 |
| 의자 | 01 식탁·보조 의자 | 4 | 0.764 |
| 에어컨 | 02 스탠드 | 1 | 0.405 |
| **합계** | | | **8.093** |

| 단계 | 계산 | 금액 |
| --- | --- | --- |
| ② 차량 | 8.093 CBM > 1톤 최대 6.0 → 2.5톤 | - |
| ③ 기본 요금 | 2.5톤 · 포장 | 1,000,000 |
| ④ 거리 | 25km → 0~50km 구간 | 0 |
| ⑤ 사다리차 | 출발 8층(6~10층) + 도착 승강기 | 170,000 |
| ⑥ 특수품목 | 에어컨 탈착 50,000 + 설치 130,000 | 180,000 |
| ⑦ 할증 | 토요일(주말 10%) × 기본 요금 | 100,000 |
| **예상 견적** | | **1,450,000** |

참고로 지금 코드의 계산식([MoveService.java:56](../../src/main/java/com/codemove/move/MoveService.java#L56): 10만 원 + CBM × 2.4만 원)으로는 같은 짐이 약 29만 원이 나옵니다. 요금 표가 확정되면 이 계산식을 위 방식으로 바꿔야 합니다.

## 5. 기존 코드와 연결되는 곳

| 지금 | 바꿀 내용 |
| --- | --- |
| [AiDemo.java](../../src/main/java/com/codemove/customer/web/AiDemo.java) 품목 6개·고정 CBM | `TBL_ITEM`·`TBL_ITEM_SPEC`에서 읽기 (품목 선택지, 크기별 CBM) |
| 고객 화면의 크기 계수(quote.js `SIZE_FACTOR` 소 0.75·중 1.0·대 1.25배) | 크기별 규격 CBM을 그대로 사용 (팀원 표의 "현재CBM" 열이 지금 값) |
| [MoveService.estimate()](../../src/main/java/com/codemove/move/MoveService.java#L62) | 4장 계산식을 하는 `EstimateCalculator`로 교체 (AI 코드와 분리해서 단위 테스트) |
| [QuoteService.java:316](../../src/main/java/com/codemove/move/QuoteService.java#L316) `1톤 ≈ 5CBM` 고정값 | `TBL_TRUCK`으로 톤 선택 |
| `TBL_QUOTE_OPTION.OPTION_COST`, `FROM/TO_LADDER_COST` | 관리자가 견적 검토 화면을 열 때 요금 표 값으로 미리 채움 (관리자가 수정 가능) |
| `TBL_MOVE_REQUEST.AI_ANALYSIS_SEC` | `TBL_AI_ANALYSIS.TOTAL_MS`에서 채움 |

## 6. 예외 처리

| 상황 | 판단 위치 | 고객 안내 | `ERROR_CODE` |
| --- | --- | --- | --- |
| mp4·mov·webm 아님 | 업로드 | "동영상 파일만 올릴 수 있어요" | 01 |
| 200MB 또는 5분 초과, 5초 미만 | 업로드 | "1~3분 길이로 다시 찍어 주세요" | 02 |
| 너무 어둡거나 흔들려서 판단 불가 | AI `video_ok:false` | "불을 켜고 천천히 다시 찍어 주세요" | 03 |
| 집 안 영상이 아님, 품목 0개 | AI `video_ok:false` 또는 items 비어 있음 | "집 안 가구·가전이 보이게 찍어 주세요" | 04 |
| AI 응답 시간 초과(90초) | API | 1회 재시도 후 "잠시 후 다시 시도" + 품목 직접 입력 버튼 | 05 |
| AI 사용량 한도(429) | API | 위와 같음 | 06 |
| JSON 형식이 깨짐 | 서버 검사 | 1회 재시도, 그래도 실패면 05와 같게 | 07 |
| 그 밖의 API 오류 | API | 위와 같음 | 08 |
| 품목은 찾았지만 표에 없음 | 매핑 | 품목 목록에 "확인 필요"로 보여 주고 고객이 고르거나 지움 | (실패 아님, `MAPPING_TYPE` 04) |

AI가 실패해도 고객은 **품목을 직접 추가해서 견적을 계속** 받을 수 있게 합니다(기존 `ITEM_SOURCE` 02 사용자 추가). 실패한 요청도 비용 계산에 들어가므로 모두 `TBL_AI_ANALYSIS`에 남깁니다.

## 7. 업체(대표님) 확인 질문

요금 표의 값과 계산 규칙은 이 답을 받아 확정합니다.

**기본 요금**
1. 1톤·2.5톤·5톤 × 일반·반포장·포장 기본 요금은 얼마인가요? ([fee-base.csv](data/fee-base.csv)의 가정값을 보여 드리고 고쳐 받기)
2. 견적을 낼 때 톤수를 먼저 정하나요, 작업 인원 수를 먼저 정하나요? 인원 1명 추가는 얼마인가요?
3. 5톤을 넘으면 차를 어떻게 조합하나요(5톤 + 1톤, 5톤 2대 등)? 요금은 각 차 요금의 합인가요?
4. 보관이사(`PACK_TYPE` 03)는 보관료를 하루·한 달 중 어떤 단위로 받나요?

**짐 양**
5. 1톤·2.5톤·5톤 차에 이삿짐이 실제로 몇 CBM 정도 들어가나요? (또는 원룸·투룸·쓰리룸이 보통 몇 톤인가요?)
6. 이사 바구니 1개에 짐이 어느 정도 들어가나요? 바구니 크기(가로·세로·높이)와 원룸·투룸·쓰리룸에 보통 몇 개 쓰나요?

**추가 요금**
7. 거리가 멀면 추가 요금을 어떻게 받나요? 구간(예: 50km마다)인가요, km당인가요? 어디까지가 시내 기본인가요?
8. 사다리차 요금을 층수별로 알려 주세요. 1톤·2.5톤처럼 짐이 적으면 더 싼가요?
9. 엘리베이터가 없는 계단 작업은 층마다 얼마를 더 받나요?
10. 피아노·돌침대·조립장·벽걸이TV·홈씨어터·안마의자·에어컨의 분해·설치 비용은요? 에어컨은 직접 하시나요, 기사님을 부르시나요?

**날짜 할증**
11. 손 없는 날·주말·공휴일·월말에 몇 % 더 받나요? 겹치면(손 없는 날이면서 토요일) 더하나요, 큰 것 하나만 받나요?
12. 할증은 기본 요금에만 붙나요, 사다리차·옵션까지 포함한 전체에 붙나요?
13. 성수기(2~3월, 이사 많은 달)에도 할증이 있나요?

## 8. 데이터 근거

- 품목·크기·CBM: 팀원 조사 표. 가전·침대·소파·옷장은 제조사·판매처 모델 치수의 중간값([cbm-sources.csv](data/cbm-sources.csv)), 나머지는 가정값입니다.
- 톤별 기본 요금·거리별 요금: 아정당 [2.5톤, 5톤 예상 견적](https://www.ajd.co.kr/contents/basic-tip/detail/2.5%ED%86%A4,_5%ED%86%A4_%EC%98%88%EC%83%81_%EA%B2%AC%EC%A0%81%EC%9D%80_%EC%96%BC%EB%A7%88%EC%9D%BC%EA%B9%8C%EC%9A%94_%ED%8A%B8%EB%9F%AD%EC%9D%B4%EC%82%AC_%EB%B9%84%EC%9A%A9_%EC%95%8C%EC%95%84%EB%B3%B4%EA%B8%B0-42748) (2025-10-27 갱신), [포장이사 톤수별](https://miso.kr/blog/포장이사-톤수별), 이사 중개업체 블로그의 톤별 포장·반포장·일반 시세.
- 사다리차: 아정당 [포장이사 사다리차 비용 요금표](https://www.ajd.co.kr/contents/basic-tip/detail/%ED%8F%AC%EC%9E%A5%EC%9D%B4%EC%82%AC_%EC%82%AC%EB%8B%A4%EB%A6%AC%EC%B0%A8_%EB%B9%84%EC%9A%A9_%EC%9A%94%EA%B8%88%ED%91%9C_%ED%95%9C%EB%88%88%EC%97%90_%EB%B3%B4%EA%B8%B0_%EC%89%BD%EA%B2%8C-47059) (2026-05-29 갱신, 5톤 기준 2~5층 15만·14층 20만·20층 28만·24층 40만 원). 사이 층은 보간한 가정값입니다.
- 특수품목: 숨고 피아노 운반 평균(약 14.4만 원), 에어컨 이전설치 평균(약 18만 원). 나머지는 가정값입니다.
- 날짜 할증: 팀 메모(손 없는 날 10~20%)와 아정당(손 없는 날·주말 20~30%).
- 차량 적재 CBM: 공인 기준이 없어 탑차 적재함 치수 × 적재율 0.7로 계산한 가정값입니다. 1톤은 기존 코드의 1톤 ≈ 5CBM에 맞췄습니다.

인터넷 시세는 업체 홍보 글이 많고 출처마다 차이가 커서(예: 5톤 포장이사 95만~130만 원) 업체 확인 전에는 실제 견적에 쓰지 않습니다.

## 9. 다음 단계 (가이드 8단계 기준)

| 가이드 단계 | 이 문서에서 한 것 | 남은 일 |
| --- | --- | --- |
| 1. 기존 프로젝트 분석 | 재사용할 테이블·코드, 연결 지점 정리 (3·5장) | 팀 확인 |
| 2. 테스트 화면·업로드 | - | `/admin/ai-test` 화면, 업로드 검사 |
| 3. AI API 연동 | 프롬프트·응답 형식 (2장) | Gemini 호출, 재시도, `TBL_AI_ANALYSIS` 저장 |
| 4. DB 자동 매핑 | 테이블·매핑 순서 (2.3·3장) | V4 적용, `ItemMappingService` |
| 5. 부피·무게 산정 | 계산식·예시 (4장) | `EstimateCalculator` + 단위 테스트 |
| 6~8. 비용 기록·반복 테스트·통합 | 기록 테이블 (`TBL_AI_ANALYSIS`) | 테스트 영상 5~10개와 정답 목록 |
