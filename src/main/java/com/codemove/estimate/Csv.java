package com.codemove.estimate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
		List<List<String>> rows = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			r.readLine();
			for (String line; (line = r.readLine()) != null;) {
				if (!line.isBlank()) {
					rows.add(split(line));
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return rows;
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
