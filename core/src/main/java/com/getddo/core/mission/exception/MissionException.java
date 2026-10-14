package com.getddo.core.mission.exception;

import com.getddo.core.common.exception.BusinessException;

public class MissionException extends BusinessException {

	public MissionException(MissionErrorCode errorCode) {
		super(errorCode);
	}
}
