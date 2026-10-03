package com.getddo.api.notification.dto.response;

/** 본인의 미읽음 알림 전체를 읽음 처리한 결과다. */
public final class NotificationReadAllResponse {
	private final long updatedCount;

	/**
	 * 실제 변경 건수를 응답에 보관한다.
	 *
	 * @param updatedCount 실제 변경한 알림 건수. 이미 읽은 알림은 제외
	 */
	public NotificationReadAllResponse(long updatedCount) {
		this.updatedCount = updatedCount;
	}

	/**
	 * 이번 요청으로 읽음 처리한 알림 건수를 반환한다.
	 *
	 * @return 실제 변경한 건수. 이미 읽은 알림은 제외
	 */
	public long getUpdatedCount() {
		return updatedCount;
	}
}
