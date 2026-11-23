package com.codemove.estimate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.codemove.estimate.VideoAnalysis.MappedItem;
import com.codemove.estimate.VideoAnalysis.RawItem;
import com.codemove.move.ItemCatalog;

/**
 * AI 인식 품목 → 표준 품목·크기 매핑 (AI_VIDEO_ESTIMATE.md 2.3, 가이드 5장).
 * 순서: 품목명 정확 일치 → 별칭(item-alias.csv) → 못 찾으면 미매핑(부피 0, 확인 필요).
 * AI 호출과 분리된 순수 계산이라 같은 입력이면 항상 같은 결과입니다.
 */
public final class ItemMatcher {

	/** 수량 허용 범위 (밖이면 잘라 내고 확인 필요) */
	public static final int MIN_QTY = 1, MAX_QTY = 200;

	record Alias(String alias, String itemName, String sizeCode) {
	}

	private static volatile List<Alias> aliases;

	private ItemMatcher() {
	}

	public static List<MappedItem> map(List<RawItem> raw) {
		List<MappedItem> out = new ArrayList<>();
		if (raw == null) {
			return out;
		}
		for (RawItem r : raw) {
			if (r != null) {
				out.add(map(r));
			}
		}
		return out;
	}

	public static MappedItem map(RawItem r) {
		boolean review = r.needsReview();
		int qty = r.quantity();
		if (qty < MIN_QTY || qty > MAX_QTY) {
			qty = Math.max(MIN_QTY, Math.min(MAX_QTY, qty));
			review = true;
		}
		String detected = blank(r.detectedName()) ? r.itemName() : r.detectedName();

		// 1. 품목표 이름 그대로
		ItemCatalog.Item item = ItemCatalog.find(trim(r.itemName()));
		if (item != null) {
			String size = validSize(r.sizeCode());
			String type = VideoAnalysis.EXACT;
			if (size == null) {
				size = "02";
				type = VideoAnalysis.AI_GUESS;
				review = true;
			}
			return mapped(item, size, qty, detected, r, type, review);
		}

		// 2. 별칭: AI 가 쓴 이름 → 본 이름 순서로 찾음
		Alias a = findAlias(r.itemName());
		if (a == null) {
			a = findAlias(r.detectedName());
		}
		if (a != null) {
			item = ItemCatalog.find(a.itemName());
			if (item != null) {
				String size = a.sizeCode().isEmpty() ? validSize(r.sizeCode()) : a.sizeCode();
				if (size == null) {
					size = "02";
					review = true;
				}
				return mapped(item, size, qty, detected, r, VideoAnalysis.ALIAS, review);
			}
		}

		// 3. 미매핑: 부피 0, 고객·관리자가 고르게 넘김
		String name = blank(detected) ? "기타" : trim(detected);
		return new MappedItem(name.length() > 50 ? name.substring(0, 50) : name, "02", qty, 0, detected, r.room(),
				r.evidence(), VideoAnalysis.UNMAPPED, true);
	}

	private static MappedItem mapped(ItemCatalog.Item item, String size, int qty, String detected, RawItem r,
			String type, boolean review) {
		return new MappedItem(item.name(), size, qty, item.size(size).cbm(), detected, r.room(), r.evidence(), type,
				review);
	}

	private static String validSize(String code) {
		String c = trim(code);
		return c.matches("0[1-3]") ? c : null;
	}

	/** 공백을 무시하고 별칭과 같은 이름, 없으면 별칭을 포함하는 가장 긴 별칭 */
	static Alias findAlias(String name) {
		if (blank(name)) {
			return null;
		}
		String n = name.replaceAll("\\s+", "");
		List<Alias> all = aliases();
		for (Alias a : all) {
			if (a.alias().replaceAll("\\s+", "").equals(n)) {
				return a;
			}
		}
		return all.stream().filter(a -> n.contains(a.alias().replaceAll("\\s+", "")))
				.max(Comparator.comparingInt(a -> a.alias().length())).orElse(null);
	}

	static List<Alias> aliases() {
		if (aliases == null) {
			aliases = load();
		}
		return aliases;
	}

	/** item-alias.csv: 별칭,품목명,크기코드,비고 */
	private static List<Alias> load() {
		return Csv.rows("item-alias.csv").stream()
				.map(c -> new Alias(c.get(0).trim(), c.get(1).trim(), c.size() > 2 ? c.get(2).trim() : "")).toList();
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
