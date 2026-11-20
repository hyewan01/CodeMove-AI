package com.codemove.move;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 표준 품목 · 크기별 기준 부피 (팀원 조사 docs/estimate/data/item-master.csv, 31개 품목 × 3개 크기).
 * build.gradle 의 processResources 가 CSV 를 클래스패스 estimate/ 로 복사합니다.
 * V4(TBL_ITEM · TBL_ITEM_SPEC)를 DB에 적용하면 이 클래스만 DB 조회로 바꾸면 됩니다.
 */
public final class ItemCatalog {

	private ItemCatalog() {
	}

	/** code 01~03 = ITEM_SIZE, grade 소형·중형·대형 (TBL_REQUEST_ITEM.ITEM_SIZE 화면 이름), name 싱글 등 */
	public record Size(String code, String grade, String name, String rule, double cbm) {
	}

	/** category = ITEM_CATEGORY (01 가구, 02 가전, 03 생활용품, 04 기타), optionCode = QUOTE_OPTION */
	public record Item(String name, String category, String icon, String optionCode, List<Size> sizes) {

		public Size size(String codeOrGrade) {
			return sizes.stream().filter(s -> s.code().equals(codeOrGrade) || s.grade().equals(codeOrGrade)).findFirst()
					.orElse(sizes.get(Math.min(1, sizes.size() - 1)));
		}
	}

	/** 카테고리 탭 (코드 → 고객 화면 이름) */
	public static final Map<String, String> CATEGORIES = Map.of("01", "가구", "02", "가전", "03", "생활용품", "04", "기타");

	private static final String[] GRADES = { "소형", "중형", "대형" };

	/** 품목 → 아이콘 (static/assets/item-*.svg) */
	private static final Map<String, String> ICONS = Map.ofEntries(
			Map.entry("침대", "bed"), Map.entry("소파", "sofa"), Map.entry("옷장", "wardrobe"),
			Map.entry("서랍장", "drawer"), Map.entry("책상", "desk"), Map.entry("식탁·테이블", "table"),
			Map.entry("의자", "chair"), Map.entry("책장", "bookshelf"), Map.entry("화장대", "vanity"),
			Map.entry("TV장", "tvstand"), Map.entry("수납장", "cabinet"), Map.entry("냉장고", "fridge"),
			Map.entry("세탁기", "washer"), Map.entry("TV", "tv"), Map.entry("에어컨", "aircon"),
			Map.entry("건조기", "dryer"), Map.entry("김치냉장고", "kimchi"), Map.entry("전자레인지", "microwave"),
			Map.entry("정수기", "purifier"), Map.entry("컴퓨터", "computer"), Map.entry("이사박스", "box"),
			Map.entry("이불·침구", "bedding"), Map.entry("옷·행거", "hanger"), Map.entry("주방용품", "kitchen"),
			Map.entry("자전거", "bicycle"), Map.entry("캐리어", "suitcase"), Map.entry("피아노", "piano"),
			Map.entry("화분", "plant"), Map.entry("운동기구", "fitness"), Map.entry("안마의자", "massage"),
			Map.entry("어항", "aquarium"));

	private static volatile List<Item> items;

	public static List<Item> items() {
		if (items == null) {
			items = load();
		}
		return items;
	}

	/** 이름이 정확히 같은 품목 (없으면 null) */
	public static Item find(String name) {
		return items().stream().filter(i -> i.name().equals(name)).findFirst().orElse(null);
	}

	private static List<Item> load() {
		InputStream in = ItemCatalog.class.getResourceAsStream("/estimate/item-master.csv");
		if (in == null) {
			throw new IllegalStateException("estimate/item-master.csv 가 클래스패스에 없습니다 (gradle processResources 확인)");
		}
		Map<String, String[]> heads = new LinkedHashMap<>();
		Map<String, List<Size>> sizes = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			r.readLine(); // 헤더
			for (String line; (line = r.readLine()) != null;) {
				if (line.isBlank()) {
					continue;
				}
				// 품목명,카테고리,크기코드,크기명,판정기준,가로,깊이,높이,제안CBM,현재CBM,옵션코드,근거
				List<String> c = csv(line);
				String name = c.get(0);
				heads.putIfAbsent(name, new String[] { c.get(1), c.get(10) });
				int idx = Integer.parseInt(c.get(2)) - 1;
				sizes.computeIfAbsent(name, k -> new ArrayList<>())
						.add(new Size(c.get(2), GRADES[idx], c.get(3), c.get(4), Double.parseDouble(c.get(8))));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		List<Item> list = new ArrayList<>();
		heads.forEach((name, h) -> list.add(new Item(name, h[0], "item-" + ICONS.getOrDefault(name, "box") + ".svg",
				h[1].isEmpty() ? null : h[1], List.copyOf(sizes.get(name)))));
		return List.copyOf(list);
	}

	/** 큰따옴표로 묶인 칸(쉼표 포함)을 처리하는 CSV 한 줄 분리 */
	static List<String> csv(String line) {
		List<String> out = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (quoted) {
				if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else if (ch == '"') {
					quoted = false;
				} else {
					cell.append(ch);
				}
			} else if (ch == '"') {
				quoted = true;
			} else if (ch == ',') {
				out.add(cell.toString());
				cell.setLength(0);
			} else {
				cell.append(ch);
			}
		}
		out.add(cell.toString());
		return out;
	}
}
