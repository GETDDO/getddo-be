package com.getddo.api.mission.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.mission.dto.request.MissionSubmissionRequest;
import com.getddo.api.mission.dto.response.MissionSubmissionResponse;
import com.getddo.core.mission.domain.MissionSubmissionCommand;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.service.MissionSubmissionService;
import com.getddo.core.user.domain.User;

/**
 * 미션 제출(M03) API다.
 *
 * <p>공통 MVC에서 헤더와 DB 정보를 대조한 사용자를 기준으로 처리한다. 제출 ID는 서버가 만들지 않고 클라이언트가 요청 전에 정한
 * UUID를 {@code Idempotency-Key} 헤더로 보낸다.</p>
 */
@RestController
@RequestMapping("/api/v1/missions/{missionId}")
public class MissionController {

	private final MissionSubmissionService submissionService;

	public MissionController(MissionSubmissionService submissionService) {
		this.submissionService = submissionService;
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
	@PostMapping("/submissions")
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
