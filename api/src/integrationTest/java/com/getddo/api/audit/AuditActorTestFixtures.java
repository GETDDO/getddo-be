package com.getddo.api.audit;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.user.domain.User;

/** 운영 API와 소비 기능에 영향을 주지 않는 처리자 전달 검증용 구성. */
@TestConfiguration(proxyBeanMethods = false)
@Import({AuditActorTestFixtures.ActorController.class, AuditActorTestFixtures.ActorService.class})
public class AuditActorTestFixtures {

	@TestComponent
	@RestController
	static class ActorController {

		private final ActorService actorService;

		ActorController(ActorService actorService) {
			this.actorService = actorService;
		}

		@PostMapping("/test/audit/record")
		ResponseEnvelope<UUID> record(@CurrentUser User user, @RequestBody ActorRequest request) {
			// 본문에 처리자 정보가 있어도 사용자 문맥의 ID만 업무 Service로 전달한다.
			return ResponseEnvelope.success(actorService.record(user.id(), request.targetId()));
		}
	}

	@TestComponent
	static class ActorService {

		private final AuditLogService auditLogService;

		ActorService(AuditLogService auditLogService) {
			this.auditLogService = auditLogService;
		}

		@Transactional
		public UUID record(UUID actorId, UUID targetId) {
			return auditLogService.record(new AuditLogCommand(actorId, "TEST_RECORD", "TEST_TARGET",
					targetId, null, null, Map.of("status", "RECORDED"), null));
		}
	}

	// 위조 입력을 받아도 사용하지 않는 것을 확인하기 위한 테스트 전용 HTTP DTO다.
	record ActorRequest(UUID targetId, UUID actorId, String role) {}
}
