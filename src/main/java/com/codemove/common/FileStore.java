package com.codemove.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일 저장 (견적 영상, 후기 사진, 견적서 첨부, 상담 첨부).
 * 디스크 폴더(codemove.upload-dir, 기본 ./uploads)에 저장하고 /uploads/** 주소로 내려줍니다.
 * 파일 이름은 추측할 수 없게 UUID 로 바꿉니다. DB 에는 /uploads/... 주소를 저장합니다.
 */
@Component
public class FileStore {

	public static final String URL_PREFIX = "/uploads/";

	private final Path root;

	public FileStore(@Value("${codemove.upload-dir:uploads}") String uploadDir) {
		this.root = Path.of(uploadDir).toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	/** @param folder videos, reviews, quotes, chat */
	public String save(String folder, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return null;
		}
		String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
		if (!ext.matches("\\.[a-z0-9]{1,5}")) {
			ext = "";
		}
		String relative = folder + "/" + LocalDate.now().toString().replace("-", "") + "/" + UUID.randomUUID() + ext;
		Path target = root.resolve(relative).normalize();
		try (InputStream in = file.getInputStream()) {
			Files.createDirectories(target.getParent());
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException("파일을 저장하지 못했습니다: " + original, e);
		}
		return URL_PREFIX + relative;
	}
}
