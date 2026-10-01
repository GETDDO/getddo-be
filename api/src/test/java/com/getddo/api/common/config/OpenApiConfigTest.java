package com.getddo.api.common.config;

import java.util.Arrays;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import com.getddo.api.notification.controller.NotificationController;
import com.getddo.api.user.controller.UserController;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

	@Test
	@DisplayName("알림 API 세 개의 Swagger는 멤버십을 선택 입력과 DB 대조로 안내한다")
	void notificationsDocumentOptionalMembership() {
		// given
		NotificationController controller = new NotificationController(null);

		// when / then
		for (String method : new String[]{"findMine", "markRead", "markAllRead"}) {
			Parameter membership = membershipHeader(controller, method);
			assertThat(membership.getRequired()).isFalse();
			assertThat(membership.getDescription()).contains("선택", "DB").doesNotContain("필수");
		}
	}

	@Test
	@DisplayName("내 정보 API의 Swagger는 기존 USER 멤버십 필수 안내를 유지한다")
	void userProfileKeepsRequiredMembershipDescription() {
		// given / when
		Parameter membership = membershipHeader(new UserController(null), "getProfile");

		// then
		assertThat(membership.getDescription()).contains("USER는 필수");
	}

	private Parameter membershipHeader(Object controller, String methodName) {
		var method = Arrays.stream(controller.getClass().getDeclaredMethods())
				.filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
		Operation operation = new OpenApiConfig().currentUserHeaders()
				.customize(new Operation(), new HandlerMethod(controller, method));
		return operation.getParameters().stream()
				.filter(parameter -> parameter.getName().equals("X-User-Membership"))
				.findFirst().orElseThrow();
	}
}
