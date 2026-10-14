package com.getddo.core.mission.domain;

import lombok.Getter;

/**
 * 미션 목록 조회 조건. 값이 없는 조건은 적용하지 않는다.
 */
@Getter
public final class MissionSearch {

	private final MissionType missionType;
	private final MissionStatus status;
	/** 제목에 포함된 문자열. 대소문자를 구분하지 않는다. */
	private final String keyword;

	public MissionSearch(MissionType missionType, MissionStatus status, String keyword) {
		this.missionType = missionType;
		this.status = status;
		this.keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
	}

	/** 모든 미션을 조회하는 조건. */
	public static MissionSearch all() {
		return new MissionSearch(null, null, null);
	}
}
