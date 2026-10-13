package com.getddo.api.mission.controller;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.mission.dto.request.MissionSubmissionRequest;
import com.getddo.api.mission.dto.response.MissionDetailResponse;
import com.getddo.api.mission.dto.response.MissionSubmissionResponse;
import com.getddo.api.mission.dto.response.MissionSummaryResponse;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.mission.domain.MissionSubmissionCommand;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.domain.MissionType;
import com.getddo.core.mission.service.MissionQueryService;
import com.getddo.core.mission.service.MissionSubmissionService;
import com.getddo.core.user.domain.User;

/**
 * 미션 목록(M01)·상세(M02) 조회와 제출(M03) API다.
 *
 * <p>공통 MVC에서 헤더와 DB 정보를 대조한 사용자를 기준으로 처리한다. 제출 ID는 서버가 만들지 않고 클라이언트가 요청 전에 정한
 * UUID를 {@code Idempotency-Key} 헤더로 보낸다.</p>
 */
@RestController
@RequestMapping("/api/v1/missions")
public class MissionController {

	private final MissionQueryService queryService;
	private final MissionSubmissionService submissionService;
	private final TimeProvider timeProvider;

	public MissionController(MissionQueryService queryService, MissionSubmissionService submissionService,
			TimeProvider timeProvider) {
		this.queryService = queryService;
		this.submissionService = submissionService;
		this.timeProvider = timeProvider;
	}

	/**
	 * 미션 목록을 사용자의 완료 표시와 함께 조회한다. 운영 상태와 기간에 관계없이 모든 미션을 시작 시각 내림차순으로 돌려주므로
	 * 진행 여부는 응답의 기간으로 판단한다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param page 1부터 시작하는 페이지 번호
	 * @param size 페이지 크기(1~100)
	 * @param missionType 지정하면 해당 유형의 미션만 조회한다
	 */
	@GetMapping
	public ResponseEnvelope<PageResult<MissionSummaryResponse>> list(
			@CurrentUser User user,
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) MissionType missionType) {
		Instant now = timeProvider.now();
		return ResponseEnvelope.success(queryService.findMissions(user.getId(), missionType, new PageQuery(page, size))
				.map(summary -> MissionSummaryResponse.from(summary, now)));
	}

	/**
	 * 미션 한 건을 문항·선택지와 함께 조회한다. 정답 정보는 응답에 포함하지 않는다.
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param missionId 조회할 미션. 없으면 404다
	 */
	@GetMapping("/{missionId}")
	public ResponseEnvelope<MissionDetailResponse> detail(@CurrentUser User user, @PathVariable UUID missionId) {
		return ResponseEnvelope.success(MissionDetailResponse.from(queryService.findMission(user.getId(), missionId),
				timeProvider.now()));
	}

	/**
	 * 미션에 답변을 제출한다. 서버가 채점하고, 완료하면 보상 청구와 응모권 지급까지 한 번에 확정한다.
	 *
	 * <p>같은 {@code Idempotency-Key}로 같은 요청을 다시 보내거나 이미 완료한 미션을 다시 제출하면 새로 처리하지 않고 이미 확정된
	 * 결과를 돌려준다. 같은 키로 다른 내용의 요청을 보내면 409다. 서버가 제출을 받은 시각이 운영 기간 밖이면 409다.</p>
	 *
	 * @param user 공통 MVC에서 확인한 등록 사용자
	 * @param missionId 제출할 미션
	 * @param submissionId 제출 ID. 요청 전에 클라이언트가 정한 UUID이며 같은 제출의 재시도는 같은 값을 쓴다
	 * @return 새로 접수한 제출이면 201, 같은 키의 재요청이나 이미 완료한 미션의 재제출이면 기존 결과와 200
	 */
	@PostMapping("/{missionId}/submissions")
	public ResponseEntity<ResponseEnvelope<MissionSubmissionResponse>> submit(
			@CurrentUser User user,
			@PathVariable UUID missionId,
			@Parameter(description = "제출 요청을 구분하는 UUID. 같은 제출의 재시도는 같은 값을 보낸다.")
			@RequestHeader("Idempotency-Key") UUID submissionId,
			@Valid @RequestBody MissionSubmissionRequest request) {
		MissionSubmissionResult result = submissionService.submit(
				new MissionSubmissionCommand(user.getId(), missionId, submissionId, request.toAnswers()));
		return ResponseEntity.status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ResponseEnvelope.success(MissionSubmissionResponse.from(result)));
	}
}
