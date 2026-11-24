package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestVideoFolderTest {

	private static final String HEADER = "공간,품목명,크기코드,수량,본 물건\n";

	@TempDir
	Path dir;

	@Test
	void listsVideosAndReadsTruthNextToThem() throws Exception {
		Files.write(dir.resolve("테스트 1.mp4"), new byte[10]);
		Files.write(dir.resolve("테스트 2.mp4"), new byte[10]);
		Files.writeString(dir.resolve("메모.txt"), "영상 아님");
		// 엑셀에서 저장한 CP949 정답 파일
		Files.write(dir.resolve("테스트 2.csv"),
				(HEADER + "거실,냉장고,03,1,\"4도어, 은색\"\n거실,의자,01,4,\n").getBytes(Charset.forName("MS949")));

		TestVideoFolder f = new TestVideoFolder(dir.toString(), null);
		assertTrue(f.configured());
		List<TestVideoFolder.Video> v = f.videos();
		assertEquals(List.of("테스트 1.mp4", "테스트 2.mp4"), v.stream().map(x -> x.name()).toList());
		assertFalse(v.get(0).hasTruth());
		assertEquals("폴더:테스트 2", v.get(1).truthKey());
		assertTrue(f.truthNames().containsAll(List.of("house-01", "폴더:테스트 2")));
		assertEquals("폴더:테스트 2", f.truthFor("테스트 2.mp4"));
		assertEquals("", f.truthFor("테스트 1.mp4"));

		GroundTruth.Score s = f.score("폴더:테스트 2", List.of());
		assertNotNull(s);
		assertEquals(5, s.expectedPieces());
		assertEquals(0, s.recall());
		assertNotNull(f.score("house-01", List.of()), "저장소 테스트셋도 그대로");
		assertNull(f.score("폴더:테스트 1", List.of()), "정답 없는 영상");
	}

	@Test
	void onlyListedNamesResolveToPaths() throws Exception {
		Files.write(dir.resolve("a.mp4"), new byte[1]);
		TestVideoFolder f = new TestVideoFolder(dir.toString(), null);
		assertEquals(dir.resolve("a.mp4"), f.path("a.mp4"));
		assertNull(f.path("../a.mp4"));
		assertNull(f.path("..\\..\\secret.mp4"));
	}

	@Test
	void brokenTruthFileIsReportedNotThrown() throws Exception {
		Files.write(dir.resolve("b.mp4"), new byte[1]);
		Files.writeString(dir.resolve("b.csv"), HEADER + "거실,냉장고,03,한 대\n", StandardCharsets.UTF_8);
		TestVideoFolder.Video v = new TestVideoFolder(dir.toString(), null).videos().get(0);
		assertTrue(v.hasTruth());
		assertTrue(v.truthError().contains("1번째 품목 줄"), v.truthError());
		assertEquals("", v.truthKey());
	}

	@Test
	void reviewFileReadsBackAsTheSameAnswerList() throws Exception {
		Files.write(dir.resolve("테스트 1.mp4"), new byte[1]);
		List<VideoAnalysis.MappedItem> items = List.of(
				new VideoAnalysis.MappedItem("소파", "02", 1, 0.9, "갈색 원목 소파베드, 3인", "거실", "쿠션 3개", "01", false,
						null, false),
				new VideoAnalysis.MappedItem("TV", "03", 1, 0.3, "벽걸이 TV", "거실", "벽에 걸림", "01", true, 75, true),
				new VideoAnalysis.MappedItem("이사박스", "02", 12, 0.05, "선반 잡동사니", "거실", "선반 3단", "01", false,
						null, false),
				new VideoAnalysis.MappedItem("실내 자전거", "02", 1, 0, "실내 자전거", "거실", "", VideoAnalysis.UNMAPPED, true,
						null, false));
		VideoAnalysisService.Logged log = new VideoAnalysisService.Logged("15", "2026-11-24T15:10:00", "테스트 1.mp4", 5.7,
				"gemini-4-hydrogen", "v3", true, null, null, VideoAnalysis.Usage.NONE, 0.05, 0, 0, 0, 0, items, 1.8, 2, 1,
				"{}");
		TestVideoFolder f = new TestVideoFolder(dir.toString(), null);

		String saved = f.saveReview(log);
		assertEquals("테스트 1_AI검토.csv", saved);
		assertEquals("테스트 1_AI검토(2).csv", f.saveReview(log), "대표님이 고치던 파일은 덮지 않음");
		Path review = dir.resolve(saved);
		String text = Files.readString(review);
		assertTrue(text.startsWith("﻿# 대표님 검토용"), "엑셀용 BOM + 요약");
		assertTrue(text.contains("포장이사 "), text);
		assertTrue(text.contains("# TV: 01 "), "크기코드 참고");
		assertFalse(f.videos().get(0).hasTruth(), "검토 파일은 아직 정답이 아님");
		assertTrue(f.videos().get(0).hasReview());

		// 대표님이 엑셀에서 고쳐 저장한 것처럼: 크기코드 02 → 2, 판단 톤 적기, 이름 바꾸기
		String edited = text.replace("\"# " + GroundTruth.TON_LABEL + "\",,", "# " + GroundTruth.TON_LABEL + ",3.5톤,")
				.replace("# " + GroundTruth.TON_LABEL + ",,", "# " + GroundTruth.TON_LABEL + ",3.5톤,")
				.replace(",이사박스,02,12,", ",이사박스,2,15,");
		Files.writeString(dir.resolve("테스트 1.csv"), edited);
		TestVideoFolder.Video v = f.videos().get(0);
		assertTrue(v.hasTruth());
		assertNull(v.truthError());
		assertEquals(3.5, v.truthTon());
		List<GroundTruth.Line> truth = GroundTruth.read(dir.resolve("테스트 1.csv"));
		assertEquals(List.of(new GroundTruth.Line("소파", "02", 1), new GroundTruth.Line("TV", "03", 1),
				new GroundTruth.Line("이사박스", "02", 15), new GroundTruth.Line("기타", "", 1)), truth);
		GroundTruth.Score s = f.score("폴더:테스트 1", items);
		assertEquals(14.0 / 17, s.recall(), 1e-9, "정답 17개(소파 1 + TV 1 + 바구니 15) 중 14개");
		assertEquals(1, s.expectedOther());
	}

	@Test
	void missingFolderIsNotConfigured() {
		TestVideoFolder f = new TestVideoFolder(dir.resolve("없음").toString(), null);
		assertFalse(f.configured());
		assertTrue(f.videos().isEmpty());
		assertEquals(new TestVideoFolder("", null).truthNames(), GroundTruth.SETS);
	}
}
