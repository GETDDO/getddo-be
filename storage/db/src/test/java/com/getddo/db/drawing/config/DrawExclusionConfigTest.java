package com.getddo.db.drawing.config;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.exception.DrawingErrorCode;
import com.getddo.core.drawing.repository.DrawExclusionRepository;

import static org.assertj.core.api.Assertions.*;

class DrawExclusionConfigTest {
	private final ApplicationContextRunner context = new ApplicationContextRunner()
			.withUserConfiguration(DrawExclusionConfig.class);

	@Test
	void missingProviderRejectsInsteadOfAssumingNobodyIsExcluded() {
		context.run(application -> assertThatThrownBy(() -> application.getBean(DrawExclusionRepository.class)
				.findExcludedParticipantIds(UUID.randomUUID())).isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(DrawingErrorCode.EXCLUSION_UNAVAILABLE));
	}

	@Test
	void connectedProviderReplacesUnavailableFallback() {
		UUID participant = UUID.randomUUID();
		context.withBean(DrawExclusionRepository.class, () -> eventId -> Set.of(participant))
				.run(application -> {
					assertThat(application).hasSingleBean(DrawExclusionRepository.class);
					assertThat(application.getBean(DrawExclusionRepository.class)
							.findExcludedParticipantIds(UUID.randomUUID())).containsExactly(participant);
				});
	}
}
