package com.getddo.api.drawing.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.api.drawing.dto.request.AwardCancellationRequest;

import static org.assertj.core.api.Assertions.*;

class AwardCancellationRequestTest {
	@Test
	@DisplayName("양끝 공백을 제거하고 사유 1~1000자를 허용한다")
	void acceptsTrimmedReasonAndLengthBoundary() {
		// given
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			AwardCancellationRequest request = new AwardCancellationRequest("  관리자 사유  ");
			// when / then
			assertThat(request.reason()).isEqualTo("관리자 사유");
			assertThat(validator.validate(request)).isEmpty();
			assertThat(validator.validate(new AwardCancellationRequest("가".repeat(1000)))).isEmpty();
		}
	}

	@Test
	@DisplayName("사유 누락·공백·1000자 초과를 거절한다")
	void rejectsMissingBlankAndOversizedReason() {
		// given
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			// when / then
			assertThat(validator.validate(new AwardCancellationRequest(null))).isNotEmpty();
			assertThat(validator.validate(new AwardCancellationRequest(" \t\n "))).isNotEmpty();
			assertThat(validator.validate(new AwardCancellationRequest("가".repeat(1001)))).isNotEmpty();
		}
	}
}
