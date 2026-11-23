package com.codemove.estimate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 출발지 → 도착지 자동차 거리 (km): 카카오 로컬 API(주소 → 좌표) + 카카오모빌리티 길찾기.
 * 카카오 앱의 REST API 키(로그인과 같은 앱)를 쓰며, 앱 설정에서 '카카오맵' 사용이 켜져 있어야 주소를 좌표로 바꿀 수 있습니다.
 * 실패하면 null → 견적은 50km 미만으로 계산하고 "거리 요금은 상담 때 확인" 안내를 붙입니다.
 */
@Service
public class DistanceService {

	private static final Logger log = LoggerFactory.getLogger(DistanceService.class);

	private final String restKey;
	private final JsonMapper json;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	/** 같은 주소 쌍은 다시 묻지 않음 (견적 화면에서 단계를 오갈 때) */
	private final Map<String, Double> cache = new ConcurrentHashMap<>();

	public DistanceService(@Value("${codemove.kakao.rest-key:${codemove.social.kakao.client-id:}}") String restKey,
			JsonMapper json) {
		this.restKey = restKey == null ? "" : restKey.trim();
		this.json = json;
	}

	/** 도로 거리(km, 소수 첫째 자리), 모르면 null */
	public Double km(String fromAddress, String toAddress) {
		if (restKey.isEmpty() || blank(fromAddress) || blank(toAddress)) {
			return null;
		}
		String key = fromAddress.trim() + "→" + toAddress.trim();
		Double cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		try {
			double[] a = coord(fromAddress), b = coord(toAddress);
			if (a == null || b == null) {
				return null;
			}
			JsonNode r = get("https://apis-navi.kakaomobility.com/v1/directions?origin=" + a[0] + "," + a[1]
					+ "&destination=" + b[0] + "," + b[1] + "&priority=RECOMMEND");
			JsonNode route = r.path("routes").path(0);
			if (route.path("result_code").asInt(-1) != 0) {
				return null;
			}
			double km = Math.round(route.path("summary").path("distance").asDouble() / 100.0) / 10.0;
			cache.put(key, km);
			return km;
		} catch (RuntimeException e) {
			log.warn("거리 계산 실패: {}", e.getMessage());
			return null;
		}
	}

	/** 주소 → {경도 x, 위도 y} */
	private double[] coord(String address) {
		JsonNode doc = get("https://dapi.kakao.com/v2/local/search/address.json?query="
				+ URLEncoder.encode(address.trim(), StandardCharsets.UTF_8)).path("documents").path(0);
		if (doc.isMissingNode()) {
			return null;
		}
		return new double[] { doc.path("x").asDouble(), doc.path("y").asDouble() };
	}

	private JsonNode get(String url) {
		try {
			HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
					.header("Authorization", "KakaoAK " + restKey).GET().build(), HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) {
				String body = res.body() == null ? "" : res.body();
				throw new IllegalStateException(res.statusCode() + " " + (body.length() > 200 ? body.substring(0, 200) : body));
			}
			return json.readTree(res.body());
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("중단됨");
		}
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
