package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.codemove.move.EstimateDoc;

import tools.jackson.databind.json.JsonMapper;

class SheetReaderTest {

	private final JsonMapper json = JsonMapper.builder().build();

	private static final String SAMPLE = """
			{"readable": true, "customer": "홍길순", "phone": "010-1234-5678", "estimate_date": "26.06.20",
			 "pack_date": "26.06.26", "transport_date": "26.06.26", "origin": "광주 북구", "destination": "광주 동구",
			 "volume": "5톤", "work_method": "고급", "ladder_truck": "전", "elevator": "후", "stairs": "",
			 "total": "1,770,000", "ladder_subtotal": "", "note": "당일 정리",
			 "options": [{"label": "벽걸이TV", "choice": "탈착", "cost": "50,000"}, {"label": "LG워시타워", "choice": "분리", "cost": ""}],
			 "items": [
			   {"group": "방", "room": "안방", "name": "침대", "quantity": "1 (퀸)"},
			   {"group": "방", "room": "방Ⅰ", "name": "의류Box", "quantity": "5 이상", "unsure": true},
			   {"group": "거실", "room": "", "name": "TV", "quantity": "1 (벽걸이)"},
			   {"group": "주방", "room": "", "name": "냉장고", "quantity": "1 (정수기형)"},
			   {"group": "베란다/다용도실", "room": "", "name": "세탁기", "quantity": "1"}
			 ],
			 "unsure_fields": ["phone", "total", "없는칸"]}
			""";

	@Test
	void fillsSheetFields() {
		EstimateDoc d = SheetReader.toDoc(json.readTree(SAMPLE));
		assertEquals("홍길순", d.getCustomer());
		assertEquals("26.06.26", d.getMoveDate());
		assertEquals("5톤", d.getVolume());
		assertEquals("포장", d.getWorkMethod(), "고급은 포장으로 + 비고");
		assertTrue(d.getNote().contains("작업내용: 고급"));
		assertEquals("전", d.getLadder());
		assertEquals("후", d.getElevator());
		assertEquals(List.of("방", "거실", "주방", "베란다·다용도실"), d.getItemGroups());
		// 1행: 방 · 거실 · 주방 · 베란다 순서
		assertEquals("침대(안방)", d.itemName(0));
		assertEquals("1 (퀸)", d.itemQuantity(0));
		assertEquals("TV", d.itemName(1));
		assertEquals("냉장고", d.itemName(2));
		assertEquals("세탁기", d.itemName(3), "베란다/다용도실 → 4번째 묶음");
		assertEquals("의류Box(방Ⅰ)", d.itemName(4));
		assertEquals(8, d.getItemRowCount());
	}

	@Test
	void optionsMatchRowsAndOthersGoToNote() {
		EstimateDoc d = SheetReader.toDoc(json.readTree(SAMPLE));
		int tv = 3; // OPTION_ROWS: 피아노, 돌침대, 조립장, 벽걸이TV …
		assertEquals("벽걸이TV", EstimateDoc.OPTION_ROWS[tv][0]);
		assertEquals("탈착", d.getOptionSelections().get(tv));
		assertEquals("50,000", d.getOptionCosts().get(tv));
		assertTrue(d.getNote().contains("LG워시타워"), "앱 양식에 없는 옵션은 비고로");
	}

	@Test
	void marksUnsureCells() {
		EstimateDoc d = SheetReader.toDoc(json.readTree(SAMPLE));
		assertTrue(d.needsReview("phone"));
		assertTrue(d.needsReview("total"));
		assertTrue(d.needsReview("itemQuantities[4]"), "흐린 품목 칸");
		assertFalse(d.needsReview("customer"));
		assertFalse(d.needsReview("없는칸"));
	}

	@Test
	void fullGroupSpillsIntoFreeCellsThenNote() {
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < 16; i++) {
			items.append(i == 0 ? "" : ",").append("{\"group\":\"방\",\"room\":\"방Ⅱ\",\"name\":\"짐").append(i)
					.append("\",\"quantity\":\"1\"}");
		}
		EstimateDoc d = SheetReader.toDoc(json.readTree("{\"readable\":true,\"items\":[" + items + "]}"));
		assertEquals(14, d.getItemRowCount());
		assertEquals("짐14(방Ⅱ)", d.itemName(1), "방이 가득 차면 거실 묶음 빈 칸으로");
		assertEquals("짐15(방Ⅱ)", d.itemName(5));
		assertEquals("", d.getNote());

		StringBuilder many = new StringBuilder();
		for (int i = 0; i < 58; i++) {
			many.append(i == 0 ? "" : ",").append("{\"group\":\"주방\",\"name\":\"짐").append(i).append("\",\"quantity\":\"1\"}");
		}
		d = SheetReader.toDoc(json.readTree("{\"readable\":true,\"items\":[" + many + "]}"));
		assertTrue(d.getNote().contains("짐56 1") && d.getNote().contains("짐57 1"), "56칸이 다 차면 비고로");
	}

	@Test
	void floorKeepsDigitsAndMovesWordsToNote() {
		EstimateDoc d = SheetReader.toDoc(json.readTree(
				"{\"readable\":true,\"departure_floor\":\"6층 엘리베이터\",\"destination_floor\":\"17\",\"items\":[]}"));
		assertEquals("6", d.getDepartureFloor());
		assertEquals("17", d.getDestinationFloor());
		assertTrue(d.getNote().contains("출발지 엘리베이터"));
	}

	@Test
	void masksNamesAndPhones() {
		assertEquals("김**", SheetReader.maskName("김선국"));
		assertEquals("김*", SheetReader.maskName("김"));
		assertEquals("", SheetReader.maskName(" "));
		String t = SheetReader.maskText("고객 홍길순 010-1234-5678, 담당 01094458224, 사무실 062)513-8224", "홍길순");
		assertEquals("고객 홍** 010-****-****, 담당 010-****-****, 사무실 062-****-****", t);
		assertTrue(SheetReader.maskText("1,770,000원 · 26.06.26", null).contains("1,770,000"), "금액 · 날짜는 그대로");
	}

	@Test
	void masksEveryNameReadInEachRun() {
		String raw = "{\"customer\": \"이선희\", \"phone\": \"010-9999-1111\"}\n\n== 다음 회차 ==\n{\"customer\":\"이선화\", \"note\": \"이선화 고객\"}";
		String t = SheetReader.maskRaw(raw, "이선희");
		assertFalse(t.contains("이선희") || t.contains("이선화") || t.contains("9999"), t);
		assertTrue(t.contains("\"이**\""));
	}

	@Test
	void logHasNoNameOrPhone(@TempDir Path dir) throws Exception {
		// AI 없이 기록 부분만: 키가 없으면 기록 없이 안내만
		GeminiVideoAnalyzer ai = new GeminiVideoAnalyzer("", "m", 10, "low", json);
		SheetReader r = new SheetReader(ai, json, dir.toString(), 1.5, 7.5);
		Path img = dir.resolve("a.jpg");
		Files.write(img, new byte[] { 1 });
		SheetReader.Result res = r.read(img, "a.jpg");
		assertFalse(res.success());
		assertTrue(res.notice().contains("직접"));
		assertEquals("", res.doc().getPhone(), "예시 번호(010-1234-5678)를 채우지 않음");
		assertNull(SheetReader.mimeOf("a.txt"));
		assertEquals("application/pdf", SheetReader.mimeOf("A.PDF"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(String j) {
		return json.convertValue(json.readTree(j), Map.class);
	}

	@Test
	void mergeFillsBlanksByMajorityAndFlagsDisagreement() {
		Map<String, Object> a = map("""
				{"readable":true,"origin":"","total":"1,800,000","pack_date":"26.11.24",
				 "items":[{"group":"방","room":"안방","name":"침대","quantity":"1"},{"group":"거실","name":"소파","quantity":"1"},
				          {"group":"주방","name":"화분","quantity":"1"}]}""");
		Map<String, Object> b = map("""
				{"readable":true,"origin":"서구 북촌길 18","total":"1,800,000","pack_date":"26.10.24",
				 "items":[{"group":"방","room":"안방","name":"침대","quantity":"1"}]}""");
		Map<String, Object> c = map("""
				{"readable":true,"origin":"서구 북촌길 18","total":"","pack_date":"26.11.24",
				 "items":[{"group":"거실","name":"소파","quantity":"1"}]}""");
		Map<String, Object> m = SheetReader.merge(List.of(a, b, c));
		assertEquals("서구 북촌길 18", m.get("origin"), "한 회차가 비워도 다른 회차 값으로");
		assertEquals("1,800,000", m.get("total"));
		assertEquals("26.11.24", m.get("pack_date"), "3번 중 2번");
		List<String> unsure = (List<String>) m.get("unsure_fields");
		assertTrue(unsure.contains("pack_date"), "회차마다 다르면 확인 필요");
		assertFalse(unsure.contains("total"));
		List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
		assertEquals(3, items.size(), "품목을 가장 많이 읽은 회차 기준");
		assertEquals(null, items.get(0).get("unsure"));
		assertEquals(true, items.get(2).get("unsure"), "다른 회차에 없던 화분은 확인 필요");
	}

	@Test
	void mergeRejectsWhenMostRunsCannotRead() {
		Map<String, Object> no = map("{\"readable\":false,\"reject_reason\":\"흐림\",\"items\":[]}");
		Map<String, Object> yes = map("{\"readable\":true,\"items\":[{\"group\":\"방\",\"name\":\"침대\",\"quantity\":\"1\"}]}");
		assertEquals(false, SheetReader.merge(List.of(no, yes, no)).get("readable"));
		assertEquals(true, SheetReader.merge(List.of(yes, no, yes)).get("readable"));
	}

	@Test
	void tidiesDoubledParens() {
		assertEquals("1 (킹)", SheetReader.tidy("1 (킹))"));
		assertEquals("1 (4도어, 정수기형)", SheetReader.tidy(" 1 (4도어, 정수기형)) "));
		assertEquals("(a(b))", SheetReader.tidy("(a(b))"), "짝이 맞으면 그대로");
		assertEquals("1 (킹)", SheetReader.tidy("1 (킹?)"), "물음표는 지우고 노란 칸으로");
		EstimateDoc d = SheetReader.toDoc(json.readTree(
				"{\"readable\":true,\"items\":[{\"group\":\"거실\",\"name\":\"TV\",\"quantity\":\"1 (65인치?)\"}]}"));
		assertEquals("1 (65인치)", d.itemQuantity(1));
		assertTrue(d.needsReview("itemQuantities[1]"));
	}

	@Test
	void tilesPhotoIntoFullPlusSix(@TempDir Path dir) throws Exception {
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(3000, 4000,
				java.awt.image.BufferedImage.TYPE_INT_RGB);
		Path p = dir.resolve("sheet.jpg");
		javax.imageio.ImageIO.write(img, "jpg", p.toFile());
		List<byte[]> tiles = SheetImages.tiles(p);
		assertEquals(7, tiles.size());
		java.awt.image.BufferedImage first = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(tiles.get(0)));
		assertEquals(1600, first.getHeight(), "긴 변 1600 으로 줄임");
		assertEquals(1, SheetImages.orientation(Files.readAllBytes(p)), "EXIF 없으면 정상 방향");
		java.awt.image.BufferedImage side = SheetImages.upright(img, 6);
		assertEquals(4000, side.getWidth(), "방향 6 은 90° 돌려 가로세로가 바뀜");
		Path txt = dir.resolve("a.heic");
		Files.write(txt, new byte[] { 0, 1, 2 });
		assertNull(SheetImages.tiles(txt), "자바가 못 여는 형식은 원본 그대로 보냄");
	}

	@Test
	void promptAndSchemaLoad() {
		assertTrue(SheetReader.prompt().contains("5-"));
		@SuppressWarnings("unchecked")
		Map<String, Object> props = (Map<String, Object>) SheetReader.schema().get("properties");
		assertTrue(props.containsKey("unsure_fields") && props.containsKey("items"));
	}
}
