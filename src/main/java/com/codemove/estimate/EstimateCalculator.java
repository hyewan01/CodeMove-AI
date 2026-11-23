package com.codemove.estimate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import com.codemove.common.LunarDays;

/**
 * 예상 견적 계산 (대표님 견적 방식, 2026-11-23 통화 + 아정당 평균가).
 *
 * <pre>
 * ① 물품 부피   = Σ 품목 기준 CBM × 수량                       item-master.csv
 * ② 적재 부피   = ① × 적재계수(2.0)                            estimate-rules.csv
 * ③ 짐 톤수     = 적재 부피를 담는 차량 조합 중 톤 합이 가장 작은 것  truck-capacity.csv (1톤 10 · 5톤 35 CBM)
 *                 예) 55 CBM → 5톤 + 1톤 + 1톤 = 7톤 (대표님 우리 집 예시)
 * ④ 기본 요금   = 톤 × 포장 방식 (표에 없는 톤은 앞뒤 톤 사이 비율)   fee-base.csv  (최소~최대)
 * ⑤ 거리 추가   = 50km마다 톤별 금액                            fee-distance.csv
 * ⑥ 나르는 방법 = 사다리차 층수별 · 계단 3개 층마다 인력 15~20만 · 엘리베이터는 안내만   fee-ladder.csv, estimate-rules.csv
 * ⑦ 옵션        = 에어컨 대당 25만 · 안마의자 10만 · 벽걸이 TV 인치별 · 피아노 · 모션침대  fee-option.csv, fee-tv.csv
 * ⑧ 날짜 할증   = ④ × 해당 할증률 중 가장 큰 값 (손 없는 날 주말 30%)   fee-surcharge.csv
 * 보관이사      = 포장이사 × 1.5~2배 + 보관료(5톤 하루 7천~1만 원, 톤 비례 × 일수)   estimate-rules.csv
 * 예상 견적     = ④+⑤+⑥+⑦+⑧ (최소~최대, 만 원 단위)   계약금 = 10%
 * </pre>
 *
 * 고가품·고객 성향·현장 여건(사다리 불가 등 20만~50만 원)은 AI 가 판단할 수 없어 방문 상담에서 정합니다(대표님).
 */
public final class EstimateCalculator {

	/** PACK_TYPE 공통코드 */
	public static final String GENERAL = "01", HALF = "02", STORAGE = "03", FULL = "04";

	/** 계산에 쓰는 품목 한 줄 (영상 분석 결과나 고객이 고친 목록) */
	public record Line(String name, String sizeCode, int count, Integer inch, boolean wallMounted) {

		public static Line of(String name, String sizeCode, int count) {
			return new Line(name, sizeCode, count, null, false);
		}
	}

	/** 짐 나르는 방법 (대표님: 사다리차로 할지 엘리베이터로 할지 고객과 정함) */
	public enum Carry {
		ELEVATOR("엘리베이터"), LADDER("사다리차"), STAIRS("계단");

		public final String label;

		Carry(String label) {
			this.label = label;
		}

		/** "E" · "L" · "S" 또는 이름 → 값, 모르면 null */
		public static Carry of(String code) {
			if (code == null || code.isBlank()) {
				return null;
			}
			return switch (code.trim().toUpperCase()) {
				case "E", "ELEVATOR" -> ELEVATOR;
				case "L", "LADDER" -> LADDER;
				case "S", "STAIRS" -> STAIRS;
				default -> null;
			};
		}
	}

	/** 출발지·도착지 한 곳: 층수 + 나르는 방법 (모르면 null) */
	public record Site(Integer floor, Carry carry) {

		public static final Site UNKNOWN = new Site(null, null);
	}

	/**
	 * 출발·도착 조건. 모르면 null (거리 → 50km 미만으로 보고 안내, 층수·방법 → 추가 없음).
	 * @param storageDays 보관이사의 보관 일수 (모르면 null → 하루 보관료만 안내)
	 */
	public record Conditions(String packType, Double distanceKm, LocalDate moveDate, Site from, Site to,
			Integer storageDays) {

		public static Conditions basic(String packType) {
			return new Conditions(packType, null, null, Site.UNKNOWN, Site.UNKNOWN, null);
		}

		public Conditions withDistance(Double km) {
			return new Conditions(packType, km, moveDate, from, to, storageDays);
		}
	}

	/** 금액 범위 한 줄 */
	public record Fee(String label, long min, long max, String note) {
	}

	public record Estimate(double itemCbm, double loadCbm, double loadFactor, List<Double> trucks, double ton,
			int workers, String packType, List<Fee> fees, long totalMin, long totalMax, long deposit,
			List<String> notes) {

		/** "5톤 1대 + 1톤 2대" */
		public String truckLabel() {
			Map<Double, Integer> c = new TreeMap<>(Comparator.reverseOrder());
			trucks.forEach(t -> c.merge(t, 1, (a, b) -> a + b));
			List<String> parts = new ArrayList<>();
			c.forEach((t, n) -> parts.add(tonLabel(t) + " " + n + "대"));
			return String.join(" + ", parts);
		}

		public String tonText() {
			return tonLabel(ton);
		}
	}

	/* ---------- 표 ---------- */

	record Truck(double ton, double cbm) {
	}

	record Base(double ton, String pack, long min, long max, int workers) {
	}

	record Distance(double ton, double fromKm, Double toKm, long add, long perKm) {
	}

	record Ladder(int from, Integer to, Long amount) {
	}

	record TvFee(int maxInch, long min, long max) {
	}

	private static volatile Tables tables;

	record Tables(List<Truck> trucks, List<Base> bases, List<Distance> distances, List<Ladder> ladders,
			List<TvFee> tvs, Map<String, long[]> options, Map<String, Integer> surcharges, Map<String, String> rules) {

		double rule(String key) {
			String v = rules.get(key);
			if (v == null) {
				throw new IllegalStateException("estimate-rules.csv 에 " + key + " 가 없습니다");
			}
			return Double.parseDouble(v.trim());
		}

		double loadFactor() {
			return rule("적재계수");
		}
	}

	static Tables tables() {
		if (tables == null) {
			tables = load();
		}
		return tables;
	}

	private static Tables load() {
		List<Truck> trucks = Csv.rows("truck-capacity.csv").stream()
				.map(c -> new Truck(d(c.get(0)), d(c.get(7)))).sorted(Comparator.comparingDouble(tr -> tr.ton())).toList();
		List<Base> bases = Csv.rows("fee-base.csv").stream()
				.map(c -> new Base(d(c.get(0)), c.get(1), l(c.get(3)), l(c.get(4)), (int) l(c.get(5)))).toList();
		List<Distance> dist = Csv.rows("fee-distance.csv").stream().map(c -> new Distance(d(c.get(0)), d(c.get(1)),
				c.get(2).isBlank() ? null : d(c.get(2)), l(c.get(3)), l(c.get(4)))).toList();
		List<Ladder> ladders = Csv.rows("fee-ladder.csv").stream().map(c -> new Ladder((int) l(c.get(0)),
				c.get(1).isBlank() ? null : (int) l(c.get(1)), c.get(2).isBlank() ? null : l(c.get(2)))).toList();
		List<TvFee> tvs = Csv.rows("fee-tv.csv").stream()
				.map(c -> new TvFee((int) l(c.get(0)), l(c.get(1)), l(c.get(2)))).toList();
		Map<String, long[]> options = new TreeMap<>(); // "08-1" → {금액}
		Csv.rows("fee-option.csv").forEach(c -> options.put(c.get(0) + "-" + c.get(2), new long[] { l(c.get(4)) }));
		Map<String, Integer> sur = new TreeMap<>();
		Csv.rows("fee-surcharge.csv").forEach(c -> sur.put(c.get(0), (int) l(c.get(3))));
		Map<String, String> rules = new TreeMap<>();
		Csv.rows("estimate-rules.csv").forEach(c -> rules.put(c.get(0), c.get(1)));
		return new Tables(trucks, bases, dist, ladders, tvs, options, sur, rules);
	}

	private EstimateCalculator() {
	}

	/* ---------- 계산 ---------- */

	public static Estimate estimate(List<Line> lines, Conditions cond) {
		Tables t = tables();
		double itemCbm = 0;
		for (Line line : lines) {
			itemCbm += unitCbm(line) * Math.max(0, line.count());
		}
		return estimate(itemCbm, lines, cond, t);
	}

	/** 품목표 기준 1개 부피 (표에 없으면 0) */
	static double unitCbm(Line line) {
		var item = com.codemove.move.ItemCatalog.find(line.name());
		return item == null ? 0 : item.size(line.sizeCode()).cbm();
	}

	static Estimate estimate(double itemCbm, List<Line> lines, Conditions cond, Tables t) {
		String pack = cond.packType() == null ? FULL : cond.packType();
		String feePack = STORAGE.equals(pack) ? FULL : pack;
		double load = round(itemCbm * t.loadFactor(), 1);
		List<Double> trucks = pickTrucks(load, t.trucks());
		double ton = trucks.stream().mapToDouble(x -> x).sum();
		List<Fee> fees = new ArrayList<>();
		List<String> notes = new ArrayList<>();

		// ④ 기본 요금 (보관이사: 창고에 넣고 다시 빼는 두 번 작업이라 포장이사 × 1.5~2배, 아정당)
		List<Base> rows = t.bases().stream().filter(b -> b.pack().equals(feePack)).toList();
		long baseMin = won(interp(ton, rows, b -> b.ton(), b -> (double) b.min()));
		long baseMax = Math.max(baseMin, won(interp(ton, rows, b -> b.ton(), b -> (double) b.max())));
		int workers = (int) Math.round(interp(ton, rows, b -> b.ton(), b -> (double) b.workers()));
		if (STORAGE.equals(pack)) {
			baseMin = won(baseMin * t.rule("보관배수최소"));
			baseMax = won(baseMax * t.rule("보관배수최대"));
		}
		fees.add(new Fee(packName(pack) + " " + tonLabel(ton), baseMin, baseMax,
				workers + "명 작업" + (STORAGE.equals(pack) ? " · 입고와 출고 두 번" : "")));
		if (STORAGE.equals(pack)) {
			// 보관료: 5톤 기준 하루 7천~1만 원을 톤에 비례
			double dayMin = t.rule("보관료일최소") * ton / 5, dayMax = t.rule("보관료일최대") * ton / 5;
			Integer days = cond.storageDays();
			if (days != null && days > 0) {
				fees.add(new Fee("보관료 " + days + "일", won(dayMin * days), won(dayMax * days), "하루 "
						+ Math.round(dayMin / 100) * 100 + "~" + Math.round(dayMax / 100) * 100 + "원"));
			} else {
				notes.add("보관료는 하루 " + Math.round(dayMin / 100) * 100 + "~" + Math.round(dayMax / 100) * 100
						+ "원이 보관 기간만큼 더해져요.");
			}
		}

		// ⑤ 거리
		if (cond.distanceKm() != null && cond.distanceKm() >= 50) {
			double km = cond.distanceKm();
			long add = won(interpByTon(ton, t.distances(), dist -> distanceAmount(dist, km)));
			fees.add(new Fee("거리 추가 (" + Math.round(km) + "km)", add, add, "50km마다 추가"));
		} else if (cond.distanceKm() != null) {
			notes.add("이동 거리 약 " + Math.round(cond.distanceKm()) + "km라 거리 요금은 없어요.");
		} else {
			notes.add("이동 거리를 확인하지 못했어요. 50km 이상이면 50km마다 거리 요금이 더해져요.");
		}

		// ⑥ 나르는 방법: 사다리차 층수별 · 계단은 인력 추가 · 엘리베이터 이용료는 관리사무소에 고객이 냄(안내만)
		site("출발지", cond.from(), t, fees, notes);
		site("도착지", cond.to(), t, fees, notes);
		Site from = cond.from() == null ? Site.UNKNOWN : cond.from(), to = cond.to() == null ? Site.UNKNOWN : cond.to();
		if (from.carry() == Carry.ELEVATOR || to.carry() == Carry.ELEVATOR) {
			notes.add("엘리베이터 이용료(보통 " + Math.round(t.rule("엘리베이터이용료최소") / 10_000) + "만~"
					+ Math.round(t.rule("엘리베이터이용료최대") / 10_000) + "만 원)는 관리사무소에 따로 내요. 미리 예약해 주세요.");
		}
		if (from.carry() == null || to.carry() == null) {
			notes.add("층수와 나르는 방법(엘리베이터·사다리차·계단)에 따라 사다리차 요금이나 인력이 더해질 수 있어요.");
		}

		// ⑦ 옵션 (기술이 필요한 작업만 별도: 에어컨 · 안마의자 · 벽걸이 TV · 피아노 · 모션 침대)
		fees.addAll(options(lines, t));

		// ⑧ 날짜 할증
		if (cond.moveDate() != null) {
			int pct = surchargePct(cond.moveDate(), t);
			if (pct > 0) {
				fees.add(new Fee("날짜 할증 " + pct + "%", won(baseMin * pct / 100.0), won(baseMax * pct / 100.0),
						"아정당: 평일 < 주말·월말 < 손 없는 날 < 손 없는 날 주말"));
			}
		}

		long min = fees.stream().mapToLong(f -> f.min()).sum(), max = fees.stream().mapToLong(f -> f.max()).sum();
		notes.add("현장 여건(사다리차를 댈 수 없어 짐을 멀리 날라야 하는 경우 등)에 따라 "
				+ Math.round(t.rule("별도비용최소") / 10_000) + "만~" + Math.round(t.rule("별도비용최대") / 10_000)
				+ "만 원의 별도 비용이 발생할 수 있습니다.");
		notes.add("고가 가구·미술품·도자기 등 파손 위험이 큰 물품은 방문 상담에서 따로 견적해요.");
		long deposit = won((min + max) / 2.0 * t.rule("계약금비율") / 100.0);
		return new Estimate(round(itemCbm, 2), load, t.loadFactor(), trucks, ton, workers, pack, fees, min, max,
				deposit, notes);
	}

	/**
	 * 차량 조합 (대표님 방식): 가장 큰 차(5톤)를 꽉 채워 쓰고, 남은 짐은
	 * "1톤 2대"와 "남은 짐이 들어가는 가장 작은 차 1대" 중 톤 합이 작은 쪽 (같으면 1대).
	 * 예) 55 CBM → 5톤(35) + 남은 20 → 1톤 2대(2톤) < 2.5톤 → 5톤 + 1톤 + 1톤 = 7톤.
	 * 짐 톤수는 "몇 톤 차"가 아니라 "몇 톤 분량의 짐"이라 요금은 톤 합으로 냄.
	 */
	static List<Double> pickTrucks(double load, List<Truck> trucks) {
		Truck small = trucks.get(0), big = trucks.get(trucks.size() - 1);
		List<Double> out = new ArrayList<>();
		double rest = Math.max(0, load);
		while (rest > big.cbm() + 1e-9) {
			out.add(big.ton());
			rest -= big.cbm();
		}
		if (rest <= 1e-9 && !out.isEmpty()) {
			return out;
		}
		final double r = rest;
		Truck single = trucks.stream().filter(t -> t.cbm() + 1e-9 >= r).findFirst().orElse(big);
		if (r > small.cbm() + 1e-9 && r <= 2 * small.cbm() + 1e-9 && 2 * small.ton() < single.ton() - 1e-9) {
			out.add(small.ton());
			out.add(small.ton());
		} else {
			out.add(single.ton());
		}
		return out;
	}

	/** 구간 표에서 거리 금액 */
	private static double distanceAmount(List<Distance> rows, double km) {
		for (Distance r : rows) {
			if (km >= r.fromKm() && (r.toKm() == null || km < r.toKm())) {
				return r.add() + (r.toKm() == null ? (km - r.fromKm()) * r.perKm() : 0);
			}
		}
		return 0;
	}

	/** 톤별 표(거리)를 톤 사이 비율로 */
	private static double interpByTon(double ton, List<Distance> all, Function<List<Distance>, Double> f) {
		Map<Double, List<Distance>> byTon = new TreeMap<>();
		all.forEach(d -> byTon.computeIfAbsent(d.ton(), k -> new ArrayList<>()).add(d));
		record P(double ton, double v) {
		}
		List<P> pts = byTon.entrySet().stream().map(e -> new P(e.getKey(), f.apply(e.getValue()))).toList();
		return interp(ton, pts, q -> q.ton(), q -> q.v());
	}

	/** 점들 사이 직선 보간, 범위 밖은 가장 가까운 두 점의 기울기로 (작은 쪽은 첫 값) */
	static <T> double interp(double x, List<T> pts, Function<T, Double> fx, Function<T, Double> fy) {
		List<T> p = pts.stream().sorted(Comparator.comparingDouble(v -> fx.apply(v))).toList();
		if (p.size() == 1 || x <= fx.apply(p.get(0))) {
			return fy.apply(p.get(0));
		}
		for (int i = 1; i < p.size(); i++) {
			if (x <= fx.apply(p.get(i)) || i == p.size() - 1) {
				double x0 = fx.apply(p.get(i - 1)), x1 = fx.apply(p.get(i));
				double y0 = fy.apply(p.get(i - 1)), y1 = fy.apply(p.get(i));
				return y0 + (y1 - y0) * (x - x0) / (x1 - x0);
			}
		}
		return fy.apply(p.get(p.size() - 1));
	}

	/** 출발지·도착지 한 곳의 사다리차 · 계단 인력 */
	private static void site(String where, Site s, Tables t, List<Fee> fees, List<String> notes) {
		if (s == null || s.floor() == null || s.floor() <= 1 || s.carry() == null) {
			return;
		}
		if (s.carry() == Carry.LADDER) {
			long amount = ladder(s.floor(), t, notes);
			if (amount > 0) {
				fees.add(new Fee(where + " 사다리차 " + s.floor() + "층", amount, amount, null));
			}
		} else if (s.carry() == Carry.STAIRS) {
			// 계단: 층 3개마다 인력 1명 (아정당: 1명 15~20만 원, 층수에 따라 늘어남)
			int people = (int) Math.ceil((s.floor() - 1) / t.rule("계단층당인력"));
			fees.add(new Fee(where + " 계단 " + s.floor() + "층", won(people * t.rule("계단인력최소")),
					won(people * t.rule("계단인력최대")), "인력 " + people + "명 추가"));
		}
	}

	private static long ladder(Integer floor, Tables t, List<String> notes) {
		if (floor == null || floor <= 1) {
			return 0;
		}
		for (Ladder l : t.ladders()) {
			if (floor >= l.from() && (l.to() == null || floor <= l.to())) {
				if (l.amount() == null) {
					notes.add(floor + "층 사다리차는 별도 협의예요.");
					return 0;
				}
				return l.amount();
			}
		}
		return 0;
	}

	private static List<Fee> options(List<Line> lines, Tables t) {
		List<Fee> out = new ArrayList<>();
		int aircon = 0, massage = 0, piano = 0, grand = 0;
		for (Line l : lines) {
			switch (l.name()) {
				// 2in1 은 스탠드 + 벽걸이 2대
				case "에어컨" -> aircon += l.count() * ("03".equals(l.sizeCode()) ? 2 : 1);
				case "안마의자" -> massage += l.count();
				case "피아노" -> {
					if ("03".equals(l.sizeCode())) grand += l.count();
					else if ("02".equals(l.sizeCode())) piano += l.count();
				}
				case "TV" -> {
					if (l.wallMounted()) {
						TvFee f = tvFee(l.inch(), l.sizeCode(), t);
						out.add(new Fee("벽걸이 TV" + (l.inch() == null ? "" : " " + l.inch() + "인치")
								+ (l.count() > 1 ? " × " + l.count() : ""), f.min() * l.count(), f.max() * l.count(),
								"탈착·설치"));
					}
				}
				default -> {
				}
			}
		}
		if (aircon > 0) {
			long unit = opt(t, "08-1") + opt(t, "08-2");
			out.add(new Fee("에어컨 탈착·설치 × " + aircon, unit * aircon, unit * aircon, "천장형은 이전 불가"));
		}
		if (massage > 0) {
			long unit = opt(t, "07-1") + opt(t, "07-2");
			out.add(new Fee("안마의자 × " + massage, unit * massage, unit * massage, null));
		}
		if (piano + grand > 0) {
			long amount = opt(t, "01-1") * piano + opt(t, "01-2") * grand;
			out.add(new Fee("피아노 × " + (piano + grand), amount, amount, null));
		}
		return out;
	}

	/** 인치를 모르면 TV 크기 코드로 짐작 (01 ≤43 → 55인치 표, 02 50~65 → 65, 03 75↑ → 75) */
	private static TvFee tvFee(Integer inch, String sizeCode, Tables t) {
		int in = inch != null ? inch : "03".equals(sizeCode) ? 75 : "02".equals(sizeCode) ? 65 : 55;
		return t.tvs().stream().filter(f -> in <= f.maxInch()).findFirst().orElse(t.tvs().get(t.tvs().size() - 1));
	}

	private static long opt(Tables t, String key) {
		long[] v = t.options().get(key);
		return v == null ? 0 : v[0];
	}

	/** 손 없는 날 · 주말 · 월말 중 가장 큰 할증, 손 없는 날이면서 주말이면 05 (공휴일 달력은 아직 없음) */
	static int surchargePct(LocalDate date, Tables t) {
		int pct = 0;
		boolean noSon = LunarDays.isNoSonDay(date);
		boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
		if (noSon && weekend) {
			pct = Math.max(pct, t.surcharges().getOrDefault("05", 0));
		}
		if (noSon) {
			pct = Math.max(pct, t.surcharges().getOrDefault("01", 0));
		}
		if (weekend) {
			pct = Math.max(pct, t.surcharges().getOrDefault("02", 0));
		}
		if (date.getDayOfMonth() > date.lengthOfMonth() - 3) {
			pct = Math.max(pct, t.surcharges().getOrDefault("04", 0));
		}
		return pct;
	}

	/* ---------- 도우미 ---------- */

	public static String packName(String code) {
		return switch (code == null ? "" : code) {
			case GENERAL -> "일반이사";
			case HALF -> "반포장이사";
			case STORAGE -> "보관이사";
			default -> "포장이사";
		};
	}

	static String tonLabel(double ton) {
		return (ton == Math.rint(ton) ? String.valueOf((long) ton) : String.valueOf(ton)) + "톤";
	}

	/** 만 원 단위 반올림 */
	static long won(double v) {
		return Math.round(v / 10_000) * 10_000;
	}

	private static double round(double v, int digits) {
		double p = Math.pow(10, digits);
		return Math.round(v * p) / p;
	}

	private static double d(String s) {
		return Double.parseDouble(s.trim());
	}

	private static long l(String s) {
		return Math.round(Double.parseDouble(s.trim()));
	}
}
