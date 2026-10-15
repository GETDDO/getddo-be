package com.getddo.api.entry.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.entry.dto.response.EntryStatisticsResponse;
import com.getddo.core.entry.service.EntryStatisticsService;
import com.getddo.core.user.domain.User;

/**
 * 이벤트 응모 현황(E03) API다. 접수 완료된 응모자 수와 차감 합계, 요청 사용자 본인의 차감 수량을 돌려준다.
 *
 * <p>멤버십 조건이 없어 멤버십 헤더는 선택이다. 갱신은 클라이언트가 다시 조회하는 방식이다.</p>
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}")
public class EntryStatisticsController {

	private final EntryStatisticsService statisticsService;

	public EntryStatisticsController(EntryStatisticsService statisticsService) {
		this.statisticsService = statisticsService;
	}

	/**
	 * 이벤트의 응모 현황을 조회한다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param eventId 조회할 이벤트. 없거나 삭제됐으면 404 {@code ENTRY-004}
	 */
	@GetMapping("/statistics")
	public ResponseEnvelope<EntryStatisticsResponse> findStatistics(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID eventId) {
		return ResponseEnvelope.success(
				EntryStatisticsResponse.from(statisticsService.getStatistics(user.getId(), eventId)));
	}
}
