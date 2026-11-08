package com.codemove.move;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/** TBL_REVIEW (후기) + 화면용 조인 값 */
@Data
public class Review {
	private Long reviewId;
	private Long reservationId;
	private Integer rating;
	private String content;
	private LocalDateTime createdAt;
	private String replyContent;
	private LocalDateTime repliedAt;
	private Long adminId;
	private String memberName;
	private Long memberId;
	private String houseType;
	private String packType;
	private String fromAddress;
	private String toAddress;
	private LocalDate moveDate;
	private Integer usageCount;
	private Integer photoCount;
	private String firstPhoto;
}
