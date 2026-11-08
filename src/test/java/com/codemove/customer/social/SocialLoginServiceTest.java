package com.codemove.customer.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SocialLoginServiceTest {

	private static SocialLoginService service(SocialLoginProperties props) {
		return new SocialLoginService(props, JsonMapper.builder().build());
	}

	@Test
	void authorizeUrlsCarryClientRedirectAndState() {
		SocialLoginService s = service(new SocialLoginProperties(null, new SocialLoginProperties.Client("kakao-key", null),
				new SocialLoginProperties.Client("naver-id", "naver-secret"), null));
		String redirect = s.redirectUri(SocialProvider.KAKAO, "http://localhost:8080/");

		assertThat(redirect).isEqualTo("http://localhost:8080/login/oauth/kakao/callback");
		assertThat(s.authorizeUrl(SocialProvider.KAKAO, redirect, "st1"))
				.startsWith("https://kauth.kakao.com/oauth/authorize?")
				.contains("client_id=kakao-key", "response_type=code", "state=st1",
						"redirect_uri=http://localhost:8080/login/oauth/kakao/callback");
		assertThat(s.authorizeUrl(SocialProvider.NAVER, "http://x/cb", "st2"))
				.startsWith("https://nid.naver.com/oauth2.0/authorize?").contains("client_id=naver-id", "state=st2");
		assertThat(s.configured(SocialProvider.KAKAO)).isTrue();
		assertThat(s.configured(SocialProvider.APPLE)).isFalse();
	}

	@Test
	void redirectBaseOverridesCurrentAddress() {
		SocialLoginService s = service(new SocialLoginProperties("https://codemove.example.com", null, null, null));
		assertThat(s.redirectUri(SocialProvider.APPLE, "http://localhost:8080"))
				.isEqualTo("https://codemove.example.com/login/oauth/apple/callback");
	}

	@Test
	void appleClientSecretIsEs256JwtSignedWithTeamKey() throws Exception {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
		gen.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair keys = gen.generateKeyPair();
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder().encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
		SocialLoginService s = service(new SocialLoginProperties(null, null, null,
				new SocialLoginProperties.Apple("com.codemove.login", "TEAM123", "KEY456", pem)));

		String[] jwt = s.appleClientSecret().split("\\.");
		String header = new String(Base64.getUrlDecoder().decode(jwt[0]), StandardCharsets.UTF_8);
		String payload = new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
		assertThat(header).contains("\"alg\":\"ES256\"", "\"kid\":\"KEY456\"");
		assertThat(payload).contains("\"iss\":\"TEAM123\"", "\"sub\":\"com.codemove.login\"",
				"\"aud\":\"https://appleid.apple.com\"");

		Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
		verifier.initVerify(keys.getPublic());
		verifier.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(verifier.verify(Base64.getUrlDecoder().decode(jwt[2]))).isTrue();
	}
}
