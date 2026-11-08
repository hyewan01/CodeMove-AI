package com.codemove.move;

import java.time.LocalDateTime;

import lombok.Data;

/** TBL_RE_VIDEO (견적 요청 영상) */
@Data
public class ReVideo {
	private Long videoId;
	private Long requestId;
	private String fileName;
	private String filePath;
	private LocalDateTime uploadedAt;
}
