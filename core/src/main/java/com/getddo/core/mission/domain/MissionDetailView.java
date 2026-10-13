package com.getddo.core.mission.domain;

import java.util.Objects;

import lombok.Getter;

/**
 * 사용자에게 보여 주는 미션 상세다. 문항·선택지를 포함한 미션과, 요청한 사용자가 이미 완료했는지를 담는다.
 *
 * <p>{@link MissionDetail}은 채점용 정답 정보를 포함하므로, 이 모델을 응답으로 옮기는 쪽이 정답 필드를 가져가지 않도록
 * 해야 한다.</p>
 */
@Getter
public final class MissionDetailView {

	private final MissionDetail detail;
	private final boolean completed;

	/**
	 * @param detail    문항·선택지를 포함한 미션
	 * @param completed 요청한 사용자가 이 미션을 완료했는지 여부
	 */
	public MissionDetailView(MissionDetail detail, boolean completed) {
		this.detail = Objects.requireNonNull(detail, "detail");
		this.completed = completed;
	}
}
