package com.codemove.checklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ChecklistRulesTest {

	@Test
	void templatesLoadForEveryPhaseWithUniqueCodes() {
		List<ChecklistService.Template> t = ChecklistService.load();
		assertTrue(t.size() >= 30, "기본 할 일 " + t.size());
		Set<String> codes = new HashSet<>();
		for (ChecklistService.Template x : t) {
			assertTrue(codes.add(x.code()), "코드 중복 " + x.code());
			assertTrue(ChecklistService.PHASES.containsKey(x.phase()), x.code() + " 단계 " + x.phase());
			assertFalse(x.title().isBlank(), x.code());
			assertTrue(x.link().isEmpty() || x.link().startsWith("/"), x.code() + " 바로가기는 우리 사이트 안");
		}
		for (String p : ChecklistService.PHASES.keySet()) {
			assertTrue(t.stream().anyMatch(x -> x.phase().equals(p)), p + " 단계에 할 일이 없음");
		}
	}

	@Test
	void phaseFollowsDaysLeft() {
		assertEquals("01", ChecklistService.phaseFor(30));
		assertEquals("01", ChecklistService.phaseFor(11));
		assertEquals("02", ChecklistService.phaseFor(10));
		assertEquals("02", ChecklistService.phaseFor(4));
		assertEquals("03", ChecklistService.phaseFor(3));
		assertEquals("03", ChecklistService.phaseFor(2));
		assertEquals("04", ChecklistService.phaseFor(1));
		assertEquals("05", ChecklistService.phaseFor(0));
		assertEquals("07", ChecklistService.phaseFor(-3));
	}

	@Test
	void ddayLabel() {
		LocalDate today = LocalDate.now();
		assertEquals("D-day", ChecklistService.dday(today));
		assertEquals("D-3", ChecklistService.dday(today.plusDays(3)));
		assertEquals("D+2", ChecklistService.dday(today.minusDays(2)));
		assertEquals("", ChecklistService.dday(null));
	}

	@Test
	void quotedCommasStayInOneCell() {
		assertEquals(List.of("M01", "설명, 쉼표 포함", ""), ChecklistService.split("M01,\"설명, 쉼표 포함\","));
	}
}
