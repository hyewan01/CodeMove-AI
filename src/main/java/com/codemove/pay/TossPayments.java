package com.codemove.pay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 토스페이먼츠 결제위젯 (https://docs.tosspayments.com).
 * <ul>
 * <li>키는 개발자센터(developers.tosspayments.com)에서 받음. 테스트 키(test_gck… / test_gsk…)는 개인 가입만으로 받을 수 있고
 * 실제 돈이 빠지지 않음. 실결제 키(live_…)는 업체 사업자로 계약해야 받음</li>
 * <li>클라이언트 키는 화면(결제위젯)에, 시크릿 키는 서버에만. application-local.yml(Git 제외)의
 * codemove.pay.toss.client-key / secret-key</li>
 * <li>결제 승인: POST /v1/payments/confirm, Authorization: Basic base64(시크릿키 + ":")</li>
 * </ul>
 */
@Component
public class TossPayments {

	static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

	private final String clientKey;
	private final String secretKey;
	private final JsonMapper json;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	/** 승인 결과. ok 이면 status DONE (가상계좌는 WAITING_FOR_DEPOSIT) */
	public record Result(boolean ok, String status, String method, String receiptUrl, String code, String message) {
	}

	public TossPayments(@Value("${codemove.pay.toss.client-key:}") String clientKey,
			@Value("${codemove.pay.toss.secret-key:}") String secretKey, JsonMapper json) {
		this.clientKey = clientKey == null ? "" : clientKey.strip();
		this.secretKey = secretKey == null ? "" : secretKey.strip();
		this.json = json;
	}

	/** 키가 둘 다 있어야 결제 버튼을 보여 줌 */
	public boolean configured() {
		return !clientKey.isEmpty() && !secretKey.isEmpty();
	}

	public String clientKey() {
		return clientKey;
	}

	/** 테스트 키면 화면에 "테스트 결제 (실제 돈이 빠지지 않아요)" 안내 */
	public boolean testMode() {
		return clientKey.startsWith("test_");
	}

	/** 결제 승인 요청 (successUrl 로 돌아온 뒤 10분 안에) */
	public Result confirm(String paymentKey, String orderId, long amount) {
		try {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("paymentKey", paymentKey);
			body.put("orderId", orderId);
			body.put("amount", amount);
			HttpRequest req = HttpRequest.newBuilder(URI.create(CONFIRM_URL)).timeout(Duration.ofSeconds(30))
					.header("Authorization", basic(secretKey)).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			JsonNode n = json.readTree(res.body());
			if (res.statusCode() / 100 != 2) {
				return new Result(false, null, null, null, n.path("code").asString("ERROR"),
						n.path("message").asString("결제 승인에 실패했어요"));
			}
			String status = n.path("status").asString("");
			// 간편결제면 "간편결제(카카오페이)"처럼 어느 앱인지까지
			String method = n.path("method").asString("");
			String provider = n.path("easyPay").path("provider").asString("");
			if (!provider.isBlank()) {
				method = method + "(" + provider + ")";
			}
			return new Result("DONE".equals(status), status, method, n.path("receipt").path("url").asString(null), null,
					null);
		} catch (IOException e) {
			return new Result(false, null, null, null, "NETWORK", "결제사와 연결하지 못했어요. 잠시 후 다시 시도해 주세요");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Result(false, null, null, null, "INTERRUPTED", "결제 승인이 중단됐어요");
		}
	}

	static String basic(String secretKey) {
		return "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
	}
}
