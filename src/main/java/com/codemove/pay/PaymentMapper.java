package com.codemove.pay;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 계약금 결제 (mapper/PaymentMapper.xml, TBL_PAYMENT) */
@Mapper
public interface PaymentMapper {

	void insert(Payment p);

	Payment findByOrder(String orderId);

	/** 예약의 결제들 (최근 순) */
	List<Payment> listByReservation(long reservationId);

	/** 대기 중인 결제를 완료로 (금액 · 대기 상태가 맞을 때만) */
	int markDone(@Param("orderId") String orderId, @Param("paymentKey") String paymentKey,
			@Param("method") String method, @Param("receiptUrl") String receiptUrl);

	int markFailed(@Param("orderId") String orderId, @Param("code") String code, @Param("message") String message);
}
