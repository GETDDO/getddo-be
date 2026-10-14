package com.getddo.core.drawing.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.drawing.domain.DrawPublicationChange;
import com.getddo.core.drawing.repository.DrawPublicationRepository;

import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.ADMIN_REQUIRED;
import static com.getddo.core.drawing.exception.DrawingErrorCode.EVENT_NOT_FOUND;

/** 관리자 권한을 확인한 뒤 실제 공개 명단의 변경 이력을 페이지로 조회한다. */
@RequiredArgsConstructor
public class DrawPublicationQueryService {
	private final DrawPublicationService publications;
	private final DrawPublicationRepository repository;
	private final DrawAdminAuthorization admins;

	/** 읽기 스냅샷에서 페이지 개수·버전·변경 전후를 일관되게 조회한다. 취소·공개 상태를 바꾸지 않는다. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawPublicationChange> changes(UUID eventId, UUID actorId, PageQuery query) {
		admins.requireActiveAdmin(actorId, ADMIN_REQUIRED);
		if (eventId == null || !repository.eventExists(eventId)) throw new BusinessException(EVENT_NOT_FOUND);
		return publications.changes(eventId, query);
	}
}
