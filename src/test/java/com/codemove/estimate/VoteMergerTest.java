package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.codemove.estimate.VideoAnalysis.RawItem;
import com.codemove.estimate.VideoAnalysis.RawResult;

class VoteMergerTest {

	private static RawItem item(String name, String size, int qty, String room) {
		return new RawItem(name, size, qty, name, room, "", false);
	}

	private static RawResult run(String memo, RawItem... items) {
		return new RawResult(true, null, List.of("거실", "주방"), List.of(items), memo);
	}

	@Test
	void keepsOnlyItemsSeenInMostRuns() {
		RawResult a = run("a", item("냉장고", "03", 1, "주방"), item("침대", "01", 1, "방"), item("이사박스", "01", 10, "거실"));
		RawResult b = run("b", item("냉장고", "03", 1, "주방"), item("이사박스", "01", 12, "거실"));
		RawResult c = run("c", item("김치냉장고", "02", 1, "주방"), item("이사박스", "01", 11, "거실"),
				item("냉장고", "03", 1, "주방"));
		VoteMerger.Merged m = VoteMerger.merge(List.of(a, b, c));
		Map<String, Integer> t = VoteMerger.totals(m.result());
		// 침대 · 김치냉장고는 3번 중 1번만 → 빠짐, 이사박스는 가운데 값 11
		assertEquals(Map.of("냉장고", 1, "이사박스", 11), t);
	}

	@Test
	void usesRowsOfTheRunThatGaveTheMedian() {
		// 이사박스가 방마다 나뉘어 있어도 가운데 값을 낸 회차의 줄을 그대로 씀
		RawResult a = run("a", item("이사박스", "01", 3, "거실"), item("이사박스", "01", 2, "주방"));
		RawResult b = run("b", item("이사박스", "01", 9, "거실"));
		RawResult c = run("c", item("이사박스", "01", 1, "거실"));
		RawResult m = VoteMerger.merge(List.of(a, b, c)).result();
		assertEquals(2, m.items().size());
		assertEquals("주방", m.items().get(1).room());
	}

	@Test
	void baseRunGivesMemoAndRooms() {
		RawResult a = run("가운데", item("소파", "02", 1, "거실"), item("TV", "02", 1, "거실"));
		RawResult b = run("TV 빠짐", item("소파", "02", 1, "거실"));
		RawResult c = run("소파 둘", item("소파", "02", 2, "거실"), item("TV", "02", 1, "거실"));
		VoteMerger.Merged m = VoteMerger.merge(List.of(a, b, c));
		assertEquals(0, m.baseIndex());
		assertEquals("가운데", m.result().memo());
	}

	@Test
	void fewerThanThreeRunsUsesFirstAsIs() {
		RawResult a = run("a", item("침대", "01", 1, "방"));
		RawResult b = run("b", item("소파", "02", 1, "거실"));
		VoteMerger.Merged m = VoteMerger.merge(List.of(a, b));
		assertSame(a, m.result());
	}

	@Test
	void majorityNotHomeIsRejected() {
		RawResult no = new RawResult(false, "집이 아님", List.of(), List.of(), "");
		RawResult yes = run("a", item("침대", "01", 1, "방"));
		// 3번 중 2번이 집 영상이 아니라고 봄 → 거절을 따름
		assertFalse(VoteMerger.merge(List.of(no, yes, no)).result().videoOk());
		// 3번 중 2번이 집 영상 → 성공한 회차가 3개 미만이라 다수결 없이 첫 성공 회차
		assertSame(yes, VoteMerger.merge(List.of(yes, no, yes)).result());
	}
}
