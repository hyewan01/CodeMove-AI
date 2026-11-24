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
		assertTrue(v.truthError().contains("2번째 줄"), v.truthError());
		assertEquals("", v.truthKey());
	}

	@Test
	void missingFolderIsNotConfigured() {
		TestVideoFolder f = new TestVideoFolder(dir.resolve("없음").toString(), null);
		assertFalse(f.configured());
		assertTrue(f.videos().isEmpty());
		assertEquals(new TestVideoFolder("", null).truthNames(), GroundTruth.SETS);
	}
}
