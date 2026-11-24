package com.codemove.estimate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** docs/estimate/data/*.csv 읽기 (build.gradle processResources 가 클래스패스 estimate/ 로 복사) */
final class Csv {

	private Csv() {
	}

	/** 헤더를 뺀 행들. 파일이 없으면 IllegalStateException */
	static List<List<String>> rows(String file) {
		InputStream in = Csv.class.getResourceAsStream("/estimate/" + file);
		if (in == null) {
			throw new IllegalStateException("estimate/" + file + " 가 클래스패스에 없습니다 (gradle processResources 확인)");
		}
		try (in) {
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), false);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * 디스크의 CSV (테스트 영상 폴더의 정답 파일). 엑셀에서 저장하면 UTF-8(BOM) 이 아니라 CP949 일 수 있어서
	 * UTF-8 로 읽히지 않으면 CP949 로 다시 읽습니다.
	 */
	static List<List<String>> rows(java.nio.file.Path file) {
		return parse(read(file), false);
	}

	/** "#" 으로 시작하는 설명 줄들 (대표님 검토용 파일의 요약 · 판단 톤) */
	static List<List<String>> comments(java.nio.file.Path file) {
		return parse(read(file), true);
	}

	private static String read(java.nio.file.Path file) {
		try {
			byte[] bytes = java.nio.file.Files.readAllBytes(file);
			try {
				return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
			} catch (java.nio.charset.CharacterCodingException e) {
				return new String(bytes, java.nio.charset.Charset.forName("MS949"));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * 첫 칸이 "#" 인 줄은 설명, 그 밖의 첫 줄은 헤더, 나머지는 데이터.
	 * 엑셀이 남기는 빈 칸만 있는 줄(",,,,")은 건너뜀.
	 */
	private static List<List<String>> parse(String text, boolean comments) {
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		List<List<String>> rows = new ArrayList<>();
		boolean header = true;
		try (BufferedReader r = new BufferedReader(new java.io.StringReader(text))) {
			for (String line; (line = r.readLine()) != null;) {
				List<String> cells = split(line);
				if (cells.stream().allMatch(c -> c.isBlank())) {
					continue;
				}
				boolean comment = cells.get(0).strip().startsWith("#");
				if (comment) {
					if (comments) {
						rows.add(cells);
					}
				} else if (header) {
					header = false;
				} else if (!comments) {
					rows.add(cells);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return rows;
	}

	/** 엑셀에서 열어도 깨지지 않게 칸 하나를 CSV 로 */
	static String cell(Object v) {
		String s = v == null ? "" : v.toString();
		return s.matches(".*[,\"\\n\\r].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
	}

	/** 큰따옴표로 묶인 칸(쉼표 포함)을 처리하는 한 줄 분리 */
	static List<String> split(String line) {
		List<String> out = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (quoted) {
				if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else if (ch == '"') {
					quoted = false;
				} else {
					cell.append(ch);
				}
			} else if (ch == '"') {
				quoted = true;
			} else if (ch == ',') {
				out.add(cell.toString());
				cell.setLength(0);
			} else {
				cell.append(ch);
			}
		}
		out.add(cell.toString());
		return out;
	}
}
