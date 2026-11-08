package com.codemove.customer.social;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오 · 네이버 · 애플 OAuth 2.0 (인가 코드 방식).
 * 인가 화면 주소를 만들고, 돌아온 code 로 토큰을 받아 사용자 정보를 읽습니다.
 */
@Service
public class SocialLoginService {

	/** 로그인한 사용자 (id: 제공자 고유 번호 → TBL_SOCIAL.SOCIAL_ID) */
	public record SocialProfile(SocialProvider provider, String id, String name, String email) {
	}

	private final SocialLoginProperties props;
	private final RestClient http;
	private final JsonMapper json;

	public SocialLoginService(SocialLoginProperties props, JsonMapper json) {
		this.props = props;
		this.http = RestClient.create();
		this.json = json;
	}

	public boolean configured(SocialProvider provider) {
		return props.configured(provider);
	}

	/** 콜백 주소: redirect-base 가 있으면 그 주소, 없으면 현재 접속 주소 기준 */
	public String redirectUri(SocialProvider provider, String currentBase) {
		String base = SocialLoginProperties.hasText(props.redirectBase()) ? props.redirectBase() : currentBase;
		return base.replaceAll("/+$", "") + "/login/oauth/" + provider.code() + "/callback";
	}

	/** 제공자 로그인 화면 주소 */
	public String authorizeUrl(SocialProvider provider, String redirectUri, String state) {
		UriComponentsBuilder url = switch (provider) {
			case KAKAO -> UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
					.queryParam("client_id", props.kakao().clientId());
			case NAVER -> UriComponentsBuilder.fromUriString("https://nid.naver.com/oauth2.0/authorize")
					.queryParam("client_id", props.naver().clientId());
			// 이름·이메일을 요청하면 애플은 결과를 POST(form_post)로 보냄
			case APPLE -> UriComponentsBuilder.fromUriString("https://appleid.apple.com/auth/authorize")
					.queryParam("client_id", props.apple().clientId())
					.queryParam("response_mode", "form_post")
					.queryParam("scope", "name email");
		};
		return url.queryParam("response_type", "code")
				.queryParam("redirect_uri", redirectUri)
				.queryParam("state", state)
				.encode()
				.toUriString();
	}

	/**
	 * code → 토큰 → 사용자 정보.
	 *
	 * @param appleUser 애플이 처음 로그인할 때만 보내는 이름 JSON (없으면 null)
	 */
	public SocialProfile fetchProfile(SocialProvider provider, String code, String redirectUri, String state,
			String appleUser) {
		return switch (provider) {
			case KAKAO -> kakao(code, redirectUri);
			case NAVER -> naver(code, state);
			case APPLE -> apple(code, redirectUri, appleUser);
		};
	}

	private SocialProfile kakao(String code, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", props.kakao().clientId());
		form.add("redirect_uri", redirectUri);
		form.add("code", code);
		if (SocialLoginProperties.hasText(props.kakao().clientSecret())) {
			form.add("client_secret", props.kakao().clientSecret());
		}
		String token = postForm("https://kauth.kakao.com/oauth/token", form).path("access_token").asString();
		JsonNode me = getJson("https://kapi.kakao.com/v2/user/me", token);
		String nickname = me.path("kakao_account").path("profile").path("nickname").asString(null);
		if (nickname == null) {
			nickname = me.path("properties").path("nickname").asString(null);
		}
		return new SocialProfile(SocialProvider.KAKAO, me.path("id").asString(), nickname,
				me.path("kakao_account").path("email").asString(null));
	}

	private SocialProfile naver(String code, String state) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", props.naver().clientId());
		form.add("client_secret", props.naver().clientSecret());
		form.add("code", code);
		form.add("state", state);
		String token = postForm("https://nid.naver.com/oauth2.0/token", form).path("access_token").asString();
		JsonNode me = getJson("https://openapi.naver.com/v1/nid/me", token).path("response");
		String name = me.path("name").asString(null);
		return new SocialProfile(SocialProvider.NAVER, me.path("id").asString(),
				name != null ? name : me.path("nickname").asString(null), me.path("email").asString(null));
	}

	private SocialProfile apple(String code, String redirectUri, String appleUser) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", props.apple().clientId());
		form.add("client_secret", appleClientSecret());
		form.add("redirect_uri", redirectUri);
		form.add("code", code);
		String idToken = postForm("https://appleid.apple.com/auth/token", form).path("id_token").asString();
		// 애플 서버에서 TLS 로 직접 받은 토큰이라 서명 검증 없이 내용만 읽음
		JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(idToken.split("\\.")[1]));
		String name = null;
		if (appleUser != null && !appleUser.isBlank()) {
			JsonNode n = json.readTree(appleUser).path("name");
			String full = (n.path("lastName").asString("") + n.path("firstName").asString("")).trim();
			name = full.isEmpty() ? null : full;
		}
		return new SocialProfile(SocialProvider.APPLE, claims.path("sub").asString(), name,
				claims.path("email").asString(null));
	}

	/** 애플 client_secret: 팀 개인키(ES256)로 서명한 JWT (유효 5분) */
	String appleClientSecret() {
		SocialLoginProperties.Apple apple = props.apple();
		long now = Instant.now().getEpochSecond();
		String header = "{\"alg\":\"ES256\",\"kid\":\"" + apple.keyId() + "\"}";
		String payload = "{\"iss\":\"" + apple.teamId() + "\",\"iat\":" + now + ",\"exp\":" + (now + 300)
				+ ",\"aud\":\"https://appleid.apple.com\",\"sub\":\"" + apple.clientId() + "\"}";
		Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
		String unsigned = b64.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
				+ b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		try {
			Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
			signer.initSign(applePrivateKey(apple.privateKey()));
			signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
			return unsigned + "." + b64.encodeToString(signer.sign());
		} catch (Exception e) {
			throw new IllegalStateException("애플 개인키로 client_secret 을 만들지 못했습니다.", e);
		}
	}

	/** .p8 내용(-----BEGIN PRIVATE KEY-----) 또는 파일 경로 */
	private static PrivateKey applePrivateKey(String value) throws Exception {
		String pem = value.contains("BEGIN PRIVATE KEY") ? value : Files.readString(Path.of(value.trim()));
		String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
		return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
	}

	private JsonNode postForm(String url, MultiValueMap<String, String> form) {
		String body = http.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
				.body(String.class);
		return json.readTree(body);
	}

	private JsonNode getJson(String url, String accessToken) {
		return json.readTree(http.get().uri(url).header("Authorization", "Bearer " + accessToken).retrieve()
				.body(String.class));
	}
}
