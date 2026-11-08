package com.codemove.customer.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 간편 로그인 앱 키 (application-local.yml 의 codemove.social, Git 제외). 키가 없는 제공자는 로그인 버튼이 안내만 띄웁니다.
 *
 * @param redirectBase 콜백 주소 앞부분 (비우면 현재 접속 주소). 애플은 https 도메인만 허용해 터널·배포 주소를 적습니다.
 */
@ConfigurationProperties("codemove.social")
public record SocialLoginProperties(String redirectBase, Client kakao, Client naver, Apple apple) {

	/** 카카오: REST API 키 + (보안 사용 시) Client Secret / 네이버: Client ID + Client Secret */
	public record Client(String clientId, String clientSecret) {
	}

	/**
	 * 애플: Services ID, 팀 ID, 키 ID, 개인키(.p8 내용 또는 파일 경로)
	 */
	public record Apple(String clientId, String teamId, String keyId, String privateKey) {
	}

	public boolean configured(SocialProvider provider) {
		return switch (provider) {
			case KAKAO -> kakao != null && hasText(kakao.clientId());
			case NAVER -> naver != null && hasText(naver.clientId()) && hasText(naver.clientSecret());
			case APPLE -> apple != null && hasText(apple.clientId()) && hasText(apple.teamId()) && hasText(apple.keyId())
					&& hasText(apple.privateKey());
		};
	}

	static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}
}
