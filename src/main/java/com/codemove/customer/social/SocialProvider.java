package com.codemove.customer.social;

import java.util.Arrays;
import java.util.Optional;

/** 간편 로그인 제공자 (TBL_SOCIAL.PROVIDER 값과 같은 소문자 코드) */
public enum SocialProvider {

	KAKAO("kakao", "카카오"), NAVER("naver", "네이버"), APPLE("apple", "애플");

	private final String code;
	private final String label;

	SocialProvider(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String code() {
		return code;
	}

	public String label() {
		return label;
	}

	public static Optional<SocialProvider> of(String code) {
		return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
	}
}
