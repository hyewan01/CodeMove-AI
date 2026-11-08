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
}
