package com.codemove.pay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class PaymentRulesTest {

	@Test
	void orderIdFollowsTossRules() {
		String a = PaymentService.orderId(287), b = PaymentService.orderId(287);
		// 영문 · 숫자 · - _ = 만, 6~64자, 매번 다르게 (추측 불가)
		assertTrue(a.matches("[A-Za-z0-9_=-]{6,64}"), a);
		assertTrue(a.startsWith("CM287-"), a);
		assertNotEquals(a, b);
	}

	@Test
	void basicAuthIsSecretKeyWithColon() {
		// base64("test_gsk_abc:")
		assertEquals("Basic dGVzdF9nc2tfYWJjOg==", TossPayments.basic("test_gsk_abc"));
	}

	@Test
	void withoutKeysPaymentIsOff() {
		JsonMapper json = JsonMapper.builder().build();
		assertFalse(new TossPayments("", "", json).configured());
		assertFalse(new TossPayments("test_gck_x", "", json).configured());
		TossPayments t = new TossPayments("test_gck_x", "test_gsk_y", json);
		assertTrue(t.configured());
		assertTrue(t.testMode());
	}
}
