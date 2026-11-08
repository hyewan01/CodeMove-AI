package com.codemove.move;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 견적 요청 · 예약 · 후기 (mapper/MoveMapper.xml) */
@Mapper
public interface MoveMapper {

	/* ---------- 견적 요청 ---------- */

	/** requestId 는 SEQ_MOVE_REQUEST 로 채워짐 */
	void insertRequest(MoveRequest request);

	void insertRequestItem(RequestItem item);

	void insertVideo(ReVideo video);

	MoveRequest findRequest(long requestId);

	/** 최근 요청 먼저 */
	List<MoveRequest> listRequests(long memberId);

	List<RequestItem> listRequestItems(long requestId);

	ReVideo findVideo(long requestId);

	/* ---------- 예약 ---------- */

	Reservation findReservationByRequest(long requestId);

	/** reservationId 는 SEQ_RESERVATION 으로 채워짐 */
	void insertReservation(Reservation reservation);

	/** 같은 요청의 취소된 예약을 다시 예약 완료로 */
	int reopenReservation(@Param("reservationId") long reservationId, @Param("moveDate") java.time.LocalDate moveDate,
			@Param("moveTime") String moveTime);

	int cancelReservation(@Param("reservationId") long reservationId, @Param("cancelledBy") String cancelledBy);

	int updateMemo(@Param("reservationId") long reservationId, @Param("memo") String memo);

	int markPaid(long reservationId);

	/** 예약 + 요청 + 견적서 + 회원. memberId 가 있으면 그 회원 것만 */
	List<MoveLine> listMoveLines(@Param("memberId") Long memberId);

	MoveLine findMoveLine(long reservationId);

	/* ---------- 후기 ---------- */

	/** reviewId 는 SEQ_REVIEW 로 채워짐 */
	void insertReview(Review review);

	void insertReviewPhoto(@Param("reviewId") long reviewId, @Param("filePath") String filePath,
			@Param("sortOrder") int sortOrder);

	/** 후기 + 회원·이사 정보. memberId 가 있으면 그 회원 것만, minRating 이상만 */
	List<Review> listReviews(@Param("memberId") Long memberId, @Param("minRating") Integer minRating);

	int updateReply(@Param("reviewId") long reviewId, @Param("content") String content, @Param("adminId") Long adminId);
}
