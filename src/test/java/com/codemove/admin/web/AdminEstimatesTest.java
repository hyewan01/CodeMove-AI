package com.codemove.admin.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.codemove.admin.web.AdminEstimates.ReviewChip;
import com.codemove.move.EstimateDoc;

class AdminEstimatesTest {

	@Test
	void reviewChipsNameFieldsAndOneChipPerItem() {
		EstimateDoc d = new EstimateDoc();
		List<String> names = new ArrayList<>(List.of("침대(안방)", "", "", "", "TV"));
		d.setItemNames(names);
		d.setItemQuantities(new ArrayList<>(List.of("1", "", "", "", "1 (65인치)")));
		d.setReviewFields(new LinkedHashSet<>(List.of("volume", "phone", "itemNames[0]", "itemQuantities[0]", "itemQuantities[4]",
				"없는칸", "itemQuantities[2]")));
		d.setCustomer("홍길순");
		d.setVolume("");
		List<ReviewChip> chips = AdminEstimates.reviewChips(d);
		assertEquals(List.of(new ReviewChip("이사물량(빈칸)", "volume"), new ReviewChip("연락처", "phone"), new ReviewChip("침대(안방)", "itemQuantities[0]"),
				new ReviewChip("TV", "itemQuantities[4]"), new ReviewChip("1행 품목", "itemQuantities[2]")), chips);
	}

	@Test
	void noChipsWithoutReviewFields() {
		assertTrue(AdminEstimates.reviewChips(null).isEmpty());
		assertTrue(AdminEstimates.reviewChips(new EstimateDoc()).isEmpty());
		assertEquals(6, AdminEstimates.itemCount(new EstimateDoc()), "기본 견적서 예시 품목 6개");
	}
}
