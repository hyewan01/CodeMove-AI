"""docs/estimate/data/*.csv → db/migration/V4__item_spec_and_fee.data.sql

요금·규격 값은 CSV 에서만 고치고 이 스크립트로 SQL 을 다시 만듭니다.
실행: py -3 db/tools/gen_v4_data.py  (Windows 스토어 python 별칭은 실행되지 않음)
"""
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "estimate" / "data"
OUT = ROOT / "db" / "migration" / "V4__item_spec_and_fee.data.sql"

CODES = {
    ("SPEC_BASIS", "규격 근거"): [("01", "제조사 치수 중간값"), ("02", "가정값")],
    ("SURCHARGE_TYPE", "날짜 할증"): [("01", "손 없는 날"), ("02", "주말"), ("03", "공휴일"), ("04", "월말")],
    ("MAPPING_TYPE", "품목 매핑 방식"): [
        ("01", "정확 일치"), ("02", "별칭"), ("03", "AI 후보(확인 필요)"), ("04", "미매핑"), ("05", "사용자 수정")],
    ("ANALYSIS_ERROR", "영상 분석 실패"): [
        ("01", "파일 형식 오류"), ("02", "용량·길이 초과"), ("03", "영상 품질 불량(어둡거나 흔들림)"),
        ("04", "집 안 영상 아님(품목 없음)"), ("05", "AI 시간 초과"), ("06", "AI 사용량 한도"),
        ("07", "AI 응답 형식 오류"), ("08", "AI 호출 실패"), ("09", "AI 키 없음"), ("10", "AI 서버 혼잡")],
}


def rows(name):
    with open(DATA / name, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def s(v):
    """문자열 리터럴 (빈 값은 NULL)"""
    if v is None or v == "":
        return "NULL"
    return "'" + v.replace("'", "''") + "'"


def n(v):
    """숫자 (빈 값은 NULL)"""
    return "NULL" if v is None or v == "" else v


def truck(ton):
    return f"(SELECT TRUCK_ID FROM TBL_TRUCK WHERE TON = {ton})"


def item(name):
    return f"(SELECT ITEM_ID FROM TBL_ITEM WHERE ITEM_NAME = {s(name)})"


def spec(name, size):
    return (f"(SELECT SPEC_ID FROM TBL_ITEM_SPEC WHERE ITEM_ID = {item(name)} "
            f"AND ITEM_SIZE = {s(size)})")


def main():
    out = [
        "-- ==================================================",
        "-- V4 초기 데이터 : V4__item_spec_and_fee.ddl.sql 다음에 실행",
        "-- 자동 생성 파일입니다. 직접 고치지 말고 docs/estimate/data/*.csv 를 고친 뒤",
        "--   py -3 db/tools/gen_v4_data.py 로 다시 만드세요.",
        "-- [업체 확인] 이 붙은 값은 대표님 확인 전 가정값입니다.",
        "-- ==================================================",
        "",
        "-- ---------- 공통코드 ----------",
    ]
    for (group, gname), codes in CODES.items():
        out.append(f"INSERT INTO TBL_CODE_GROUP (GROUP_CODE, GROUP_NAME, IS_ACTIVE) SELECT {s(group)}, {s(gname)}, 'Y' "
                   f"FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TBL_CODE_GROUP WHERE GROUP_CODE = {s(group)});")
        for i, (code, cname) in enumerate(codes, 1):
            out.append("INSERT INTO TBL_CODE (GROUP_CODE, CODE, CODE_NAME, USER_NAME, SORT_ORDER, IS_ACTIVE) "
                       f"SELECT {s(group)}, {s(code)}, {s(cname)}, NULL, {i}, 'Y' FROM DUAL WHERE NOT EXISTS "
                       f"(SELECT 1 FROM TBL_CODE WHERE GROUP_CODE = {s(group)} AND CODE = {s(code)});")

    master = rows("item-master.csv")
    out += ["", f"-- ---------- TBL_ITEM ({len({r['품목명'] for r in master})}개) ----------"]
    seen = []
    for r in master:
        if r["품목명"] in seen:
            continue
        seen.append(r["품목명"])
        out.append("INSERT INTO TBL_ITEM (ITEM_ID, ITEM_NAME, CATEGORY, OPTION_CODE, SORT_ORDER) VALUES "
                   f"(SEQ_ITEM.NEXTVAL, {s(r['품목명'])}, {s(r['카테고리'])}, {s(r['옵션코드'])}, {len(seen)});")

    out += ["", f"-- ---------- TBL_ITEM_SPEC ({len(master)}건) ----------"]
    for r in master:
        basis = "01" if r["근거"].startswith("제조사") else "02"
        out.append("INSERT INTO TBL_ITEM_SPEC (SPEC_ID, ITEM_ID, ITEM_SIZE, SIZE_NAME, SIZE_RULE, WIDTH_CM, DEPTH_CM, "
                   "HEIGHT_CM, CBM, BASIS, BASIS_NOTE) VALUES "
                   f"(SEQ_ITEM_SPEC.NEXTVAL, {item(r['품목명'])}, {s(r['크기코드'])}, {s(r['크기명'])}, "
                   f"{s(r['판정기준'])}, {n(r['가로cm'])}, {n(r['깊이cm'])}, {n(r['높이cm'])}, {r['제안CBM']}, "
                   f"{s(basis)}, {s(r['근거'])});")

    sources = rows("cbm-sources.csv")
    out += ["", f"-- ---------- TBL_SPEC_SOURCE ({len(sources)}건) ----------"]
    for r in sources:
        out.append("INSERT INTO TBL_SPEC_SOURCE (SOURCE_ID, SPEC_ID, BRAND, MODEL_NAME, WIDTH_MM, DEPTH_MM, HEIGHT_MM, "
                   "CBM, SOURCE_URL) VALUES "
                   f"(SEQ_SPEC_SOURCE.NEXTVAL, {spec(r['품목명'], r['크기코드'])}, {s(r['브랜드'])}, "
                   f"{s(r['모델명'])}, {n(r['가로mm'])}, {n(r['깊이mm'])}, {n(r['높이mm'])}, {r['CBM']}, "
                   f"{s(r['출처URL'])});")

    aliases = rows("item-alias.csv")
    out += ["", f"-- ---------- TBL_ITEM_ALIAS ({len(aliases)}건) ----------"]
    for r in aliases:
        out.append("INSERT INTO TBL_ITEM_ALIAS (ALIAS_ID, ALIAS_NAME, ITEM_ID, ITEM_SIZE) VALUES "
                   f"(SEQ_ITEM_ALIAS.NEXTVAL, {s(r['별칭'])}, {item(r['품목명'])}, {s(r['크기코드'])});")

    out += ["", "-- ---------- TBL_TRUCK ----------"]
    for r in rows("truck-capacity.csv"):
        out.append("INSERT INTO TBL_TRUCK (TRUCK_ID, TON, LOAD_CBM, MAX_CBM, BASIS_NOTE) VALUES "
                   f"(SEQ_TRUCK.NEXTVAL, {r['톤']}, {r['적재CBM']}, {r['최대적재CBM']}, {s(r['근거'])});")

    out += ["", "-- ---------- TBL_FEE_BASE ----------"]
    for r in rows("fee-base.csv"):
        out.append("INSERT INTO TBL_FEE_BASE (FEE_ID, TON, PACK_TYPE, BASE_AMOUNT, MAX_AMOUNT, WORKERS, BASIS_NOTE) "
                   f"VALUES (SEQ_FEE_BASE.NEXTVAL, {r['톤']}, {s(r['포장방식코드'])}, {r['기본요금']}, "
                   f"{r['기본요금상한']}, {n(r['작업인원'])}, {s(r['근거'])});")

    out += ["", "-- ---------- TBL_FEE_OPTION ----------"]
    for r in rows("fee-option.csv"):
        out.append("INSERT INTO TBL_FEE_OPTION (FEE_ID, OPTION_CODE, CHOICE_NO, CHOICE_NAME, AMOUNT, UNIT, BASIS_NOTE) "
                   f"VALUES (SEQ_FEE_OPTION.NEXTVAL, {s(r['옵션코드'])}, {r['선택번호']}, {s(r['선택명'])}, "
                   f"{r['금액']}, {s(r['단위'])}, {s(r['근거'])});")

    out += ["", "-- ---------- TBL_FEE_DISTANCE ----------"]
    for r in rows("fee-distance.csv"):
        out.append("INSERT INTO TBL_FEE_DISTANCE (FEE_ID, TON, FROM_KM, TO_KM, ADD_AMOUNT, PER_KM, BASIS_NOTE) "
                   f"VALUES (SEQ_FEE_DISTANCE.NEXTVAL, {r['톤']}, {r['시작km']}, {n(r['끝km'])}, "
                   f"{r['추가금액']}, {r['초과km당단가']}, {s(r['근거'])});")

    out += ["", "-- ---------- TBL_FEE_SURCHARGE ----------"]
    for r in rows("fee-surcharge.csv"):
        out.append("INSERT INTO TBL_FEE_SURCHARGE (SURCHARGE_CODE, RATE_PCT, BASIS_NOTE) VALUES "
                   f"({s(r['할증코드'])}, {r['할증률%']}, {s(r['판정방법'] + ' / ' + r['근거'])});")

    out += ["", "-- ---------- TBL_FEE_LADDER ----------"]
    for r in rows("fee-ladder.csv"):
        out.append("INSERT INTO TBL_FEE_LADDER (FEE_ID, FROM_FLOOR, TO_FLOOR, AMOUNT, WORK_HOURS, BASIS_NOTE) VALUES "
                   f"(SEQ_FEE_LADDER.NEXTVAL, {r['시작층']}, {n(r['끝층'])}, {n(r['금액'])}, {s(r['작업시간'])}, "
                   f"{s(r['근거'])});")

    out += ["", "-- ---------- TBL_FEE_TV ----------"]
    for r in rows("fee-tv.csv"):
        out.append("INSERT INTO TBL_FEE_TV (FEE_ID, MAX_INCH, MIN_AMOUNT, MAX_AMOUNT, BASIS_NOTE) VALUES "
                   f"(SEQ_FEE_TV.NEXTVAL, {r['최대인치']}, {r['최소금액']}, {r['최대금액']}, {s(r['근거'])});")

    out += ["", "-- ---------- TBL_ESTIMATE_RULE ----------"]
    for r in rows("estimate-rules.csv"):
        out.append("INSERT INTO TBL_ESTIMATE_RULE (RULE_KEY, RULE_VALUE, BASIS_NOTE) VALUES "
                   f"({s(r['항목'])}, {r['값']}, {s(r['근거'])});")

    out += ["", "COMMIT;", ""]
    OUT.write_text("\n".join(out), encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)} ({len(out)} lines)")


if __name__ == "__main__":
    main()
