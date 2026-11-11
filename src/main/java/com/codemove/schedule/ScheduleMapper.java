package com.codemove.schedule;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 관리자 일정 (TBL_SCHEDULE) · 휴무일 (TBL_CLOSED_DAY) */
@Mapper
public interface ScheduleMapper {

	@Select("""
			SELECT s.*, m.MEMBER_NAME, m.PHONE AS MEMBER_PHONE
			  FROM TBL_SCHEDULE s LEFT JOIN TBL_MEMBER m ON m.MEMBER_ID = s.MEMBER_ID
			 WHERE s.SCHEDULE_DATE >= #{from} AND s.SCHEDULE_DATE < #{to}
			 ORDER BY s.SCHEDULE_DATE, s.SCHEDULE_TIME, s.SCHEDULE_ID""")
	List<Schedule> listSchedules(@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Select("""
			SELECT s.*, m.MEMBER_NAME, m.PHONE AS MEMBER_PHONE
			  FROM TBL_SCHEDULE s LEFT JOIN TBL_MEMBER m ON m.MEMBER_ID = s.MEMBER_ID
			 WHERE s.SCHEDULE_ID = #{id}""")
	Schedule findSchedule(long id);

	@Insert("""
			INSERT INTO TBL_SCHEDULE (SCHEDULE_ID, TITLE, CATEGORY, SCHEDULE_DATE, SCHEDULE_TIME, PLACE, MEMBER_ID,
			       RESERVATION_ID, ADMIN_ID, CREATED_AT)
			VALUES (SEQ_SCHEDULE.NEXTVAL, #{title}, #{category}, #{scheduleDate}, #{scheduleTime,jdbcType=VARCHAR},
			       #{place,jdbcType=VARCHAR}, #{memberId,jdbcType=NUMERIC}, #{reservationId,jdbcType=NUMERIC},
			       #{adminId,jdbcType=NUMERIC}, SYSDATE)""")
	void insertSchedule(Schedule s);

	@Delete("DELETE FROM TBL_SCHEDULE WHERE SCHEDULE_ID = #{id}")
	void deleteSchedule(long id);

	@Select("SELECT CLOSED_DATE, CLOSED_TYPE FROM TBL_CLOSED_DAY ORDER BY CLOSED_DATE")
	List<ClosedDay> listClosedDays();

	@Delete("DELETE FROM TBL_CLOSED_DAY WHERE CLOSED_DATE = #{date}")
	void deleteClosedDay(LocalDate date);

	@Insert("""
			INSERT INTO TBL_CLOSED_DAY (CLOSED_DATE, CLOSED_TYPE, UPDATED_AT, ADMIN_ID)
			VALUES (#{date}, #{type}, SYSDATE, #{adminId,jdbcType=NUMERIC})""")
	void insertClosedDay(@Param("date") LocalDate date, @Param("type") String type, @Param("adminId") Long adminId);

	/* ---------- 예약 설정 (TBL_RESERVATION_SETTING, 한 행) · 날짜별 예약 수 ---------- */

	@Select("SELECT * FROM (SELECT * FROM TBL_RESERVATION_SETTING ORDER BY SETTING_ID) WHERE ROWNUM = 1")
	ReservationSetting findReservationSetting();

	@Insert("""
			INSERT INTO TBL_RESERVATION_SETTING (SETTING_ID, DAILY_CAPACITY, CLOSED_WEEKDAYS, HOLIDAY_CLOSED, UPDATED_AT)
			VALUES (SEQ_RESERVATION_SETTING.NEXTVAL, 3, NULL, 'N', SYSDATE)""")
	void insertReservationSetting();

	@org.apache.ibatis.annotations.Update("""
			UPDATE TBL_RESERVATION_SETTING
			   SET DAILY_CAPACITY = #{s.dailyCapacity}, CLOSED_WEEKDAYS = #{s.closedWeekdays,jdbcType=VARCHAR},
			       HOLIDAY_CLOSED = #{s.holidayClosed}, UPDATED_AT = SYSDATE, ADMIN_ID = NVL(#{adminId,jdbcType=NUMERIC}, ADMIN_ID)
			 WHERE SETTING_ID = #{s.settingId}""")
	void updateReservationSetting(@Param("s") ReservationSetting s, @Param("adminId") Long adminId);

	/** 예약 완료(취소·이사 완료 제외) 수를 이사 날짜별로 [날짜, 건수] */
	@Select("""
			SELECT MOVE_DATE AS MOVE_DAY, COUNT(*) AS CNT FROM TBL_RESERVATION
			 WHERE STATUS = '01' AND MOVE_DATE >= #{from} AND MOVE_DATE < #{to}
			 GROUP BY MOVE_DATE""")
	List<java.util.Map<String, Object>> countBookingsByDate(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
