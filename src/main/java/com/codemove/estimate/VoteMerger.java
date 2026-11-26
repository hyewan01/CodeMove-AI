package com.codemove.estimate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.codemove.estimate.VideoAnalysis.RawItem;
import com.codemove.estimate.VideoAnalysis.RawResult;

/**
 * 같은 영상을 여러 번(3번) 분석한 결과를 다수결로 합칩니다.
 * <p>
 * 온도 0 이어도 생각(thinking) 과정이 매번 달라 같은 영상에서 짐이 1~2개씩 바뀝니다
 * (예: 같은 냉장고를 한 번은 김치냉장고로, 커튼 뒤 침대를 3번 중 1번만).
 * 품목 이름마다 회차별 수량의 가운데 값(중앙값)을 정하고, 그 수량을 낸 회차의 줄(방 · 크기 · 근거)을 그대로 씁니다.
 * 그래서 3번 중 1번만 나온 짐은 빠지고, 2번 이상 나온 짐만 남습니다.
 */
final class VoteMerger {

	private VoteMerger() {
	}

	/** 다수결에 필요한 최소 회차. 이보다 적게 성공하면 첫 결과를 그대로 씁니다. */
	static final int MIN_VOTES = 3;

	/** 합친 결과 + 기준으로 삼은 회차(방 목록 · 메모를 가져온 회차) */
	record Merged(RawResult result, int baseIndex) {
	}

	static Merged merge(List<RawResult> runs) {
		List<RawResult> ok = runs.stream().filter(r -> r.videoOk() && !r.items().isEmpty()).toList();
		if (runs.size() >= MIN_VOTES && ok.size() * 2 < runs.size()) {
			// 집 영상이 아니라고(또는 짐이 없다고) 본 회차가 더 많으면 그 판단을 따름
			RawResult reject = runs.stream().filter(r -> !ok.contains(r)).findFirst().orElseThrow();
			return new Merged(reject, runs.indexOf(reject));
		}
		if (ok.size() < MIN_VOTES) {
			RawResult first = ok.isEmpty() ? runs.get(0) : ok.get(0);
			return new Merged(first, runs.indexOf(first));
		}
		List<Map<String, Integer>> totals = ok.stream().map(VoteMerger::totals).toList();
		Set<String> names = new LinkedHashSet<>();
		totals.forEach(t -> names.addAll(t.keySet()));
		Map<String, Integer> median = new LinkedHashMap<>();
		for (String n : names) {
			median.put(n, median(totals.stream().mapToInt(t -> t.getOrDefault(n, 0)).toArray()));
		}
		// 가운데 값과 가장 가까운 회차를 기준으로 (방 목록 · 메모 · 품목 순서)
		int base = 0;
		long best = Long.MAX_VALUE;
		for (int i = 0; i < ok.size(); i++) {
			long d = distance(totals.get(i), median);
			if (d < best) {
				best = d;
				base = i;
			}
		}
		List<String> order = new ArrayList<>(totals.get(base).keySet());
		names.stream().filter(n -> !order.contains(n)).forEach(order::add);
		List<RawItem> items = new ArrayList<>();
		for (String n : order) {
			int m = median.get(n);
			if (m == 0) {
				continue;
			}
			// 기준 회차가 가운데 값이면 그 회차, 아니면 가운데 값을 낸 첫 회차의 줄을 씀
			int pick = totals.get(base).getOrDefault(n, 0) == m ? base : -1;
			for (int i = 0; pick < 0 && i < ok.size(); i++) {
				if (totals.get(i).getOrDefault(n, 0) == m) {
					pick = i;
				}
			}
			ok.get(pick).items().stream().filter(it -> key(it).equals(n)).forEach(items::add);
		}
		RawResult b = ok.get(base);
		return new Merged(new RawResult(true, null, b.rooms(), items, b.memo()), runs.indexOf(b));
	}

	/** 품목 이름별 수량 합 (나온 순서 유지) */
	static Map<String, Integer> totals(RawResult r) {
		Map<String, Integer> t = new LinkedHashMap<>();
		for (RawItem i : r.items()) {
			t.merge(key(i), Math.max(0, i.quantity()), Integer::sum);
		}
		return t;
	}

	private static String key(RawItem i) {
		return i.itemName() == null ? "" : i.itemName().strip();
	}

	static int median(int[] v) {
		int[] s = v.clone();
		Arrays.sort(s);
		return s[(s.length - 1) / 2];
	}

	/** 가운데 값과 다른 품목 수를 먼저, 같으면 수량 차이 합을 봄 */
	private static long distance(Map<String, Integer> t, Map<String, Integer> median) {
		long diffNames = 0, diffQty = 0;
		for (Map.Entry<String, Integer> e : median.entrySet()) {
			int d = Math.abs(t.getOrDefault(e.getKey(), 0) - e.getValue());
			if (d > 0) {
				diffNames++;
				diffQty += d;
			}
		}
		return diffNames * 10_000 + diffQty;
	}
}
