package com.codemove.estimate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 상세주소에서 층수 읽기 (고객은 층수를 따로 적지 않고 상세주소에 "3층 301호", "101동 1203호" 처럼 적음).
 * quote.js 의 floorOf 와 같은 규칙입니다.
 * <ol>
 * <li>"N층" 이 있으면 N</li>
 * <li>"NNN호" · "NNNN호" 이면 호수 앞자리 (301호 → 3층, 1203호 → 12층)</li>
 * <li>지하 · B1 이나 알 수 없으면 null</li>
 * </ol>
 */
public final class Floors {

	private static final Pattern FLOOR = Pattern.compile("(?<![\\dA-Za-z])(\\d{1,2})\\s*층");
	private static final Pattern UNIT = Pattern.compile("(?<!\\d)(\\d{3,4})\\s*호");
	private static final Pattern BASEMENT = Pattern.compile("지하|\\bB\\s*\\d", Pattern.CASE_INSENSITIVE);

	private Floors() {
	}

	public static Integer of(String detail) {
		if (detail == null || detail.isBlank() || BASEMENT.matcher(detail).find()) {
			return null;
		}
		Matcher f = FLOOR.matcher(detail);
		if (f.find()) {
			return valid(Integer.parseInt(f.group(1)));
		}
		Matcher u = UNIT.matcher(detail);
		if (u.find()) {
			return valid(Integer.parseInt(u.group(1)) / 100);
		}
		return null;
	}

	private static Integer valid(int floor) {
		return floor >= 1 && floor <= 80 ? floor : null;
	}
}
