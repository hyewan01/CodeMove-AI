package com.codemove.estimate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.codemove.estimate.VideoAnalysis.MappedItem;
import com.codemove.move.ItemCatalog;

/**
 * 테스트 영상의 정답 품목 (docs/estimate/testset/{이름}.csv, 사람이 영상을 보고 기록) 과 AI 결과 비교.
 * 가이드 9장: 품목 인식은 품목명별 수량 겹침으로 Precision·Recall·F1, 부피는 정답 규격 기준 CBM 과의 오차.
 * "기타"(품목표에 없는 물건)는 이름이 제각각이라 개수만 따로 셉니다.
 */
public final class GroundTruth {

	/** 테스트셋 목록 (build.gradle 이 docs/estimate/testset 을 클래스패스 estimate/testset 으로 복사) */
	public static final List<String> SETS = List.of("house-01");

	/** 품목명별 비교 한 줄 */
	public record Row(String name, int expected, int detected, double expectedCbm, double detectedCbm) {

		public int matched() {
			return Math.min(expected, detected);
		}
	}

	public record Score(String set, List<Row> rows, int expectedPieces, int detectedPieces, int matchedPieces,
			double precision, double recall, double f1, double expectedCbm, double detectedCbm, int expectedOther,
			int detectedOther) {

		/** (AI − 정답) / 정답 CBM */
		public double cbmErrorPct() {
			return expectedCbm == 0 ? 0 : (detectedCbm - expectedCbm) / expectedCbm * 100;
		}
	}

	private record Line(String name, String size, int qty) {
	}

	private GroundTruth() {
	}

	public static Score score(String set, List<MappedItem> items) {
		Map<String, int[]> qty = new LinkedHashMap<>(); // name → {정답, AI}
		Map<String, double[]> cbm = new LinkedHashMap<>();
		int expOther = 0, detOther = 0;
		for (Line l : load(set)) {
			ItemCatalog.Item item = ItemCatalog.find(l.name());
			if (item == null) {
				expOther += l.qty();
				continue;
			}
			qty.computeIfAbsent(l.name(), k -> new int[2])[0] += l.qty();
			cbm.computeIfAbsent(l.name(), k -> new double[2])[0] += item.size(l.size()).cbm() * l.qty();
		}
		for (MappedItem i : items) {
			if (VideoAnalysis.UNMAPPED.equals(i.mappingType())) {
				detOther += i.count();
				continue;
			}
			qty.computeIfAbsent(i.name(), k -> new int[2])[1] += i.count();
			cbm.computeIfAbsent(i.name(), k -> new double[2])[1] += i.cbm();
		}
		List<Row> rows = new ArrayList<>();
		int e = 0, d = 0, m = 0;
		double ec = 0, dc = 0;
		for (var en : qty.entrySet()) {
			double[] c = cbm.get(en.getKey());
			Row r = new Row(en.getKey(), en.getValue()[0], en.getValue()[1], c[0], c[1]);
			rows.add(r);
			e += r.expected();
			d += r.detected();
			m += r.matched();
			ec += r.expectedCbm();
			dc += r.detectedCbm();
		}
		double p = d == 0 ? 0 : (double) m / d, rc = e == 0 ? 0 : (double) m / e;
		return new Score(set, rows, e, d, m, p, rc, p + rc == 0 ? 0 : 2 * p * rc / (p + rc), ec, dc, expOther,
				detOther);
	}

	/** 공간,품목명,크기코드,수량,... */
	private static List<Line> load(String set) {
		if (!SETS.contains(set)) {
			throw new IllegalArgumentException("없는 테스트셋: " + set);
		}
		return Csv.rows("testset/" + set + ".csv").stream()
				.map(c -> new Line(c.get(1).trim(), c.get(2).trim(), Integer.parseInt(c.get(3).trim()))).toList();
	}
}
