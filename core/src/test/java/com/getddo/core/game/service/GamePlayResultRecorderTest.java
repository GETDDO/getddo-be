package com.getddo.core.game.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.game.domain.ColorWordAnswer;
import com.getddo.core.game.domain.ColorWordValidationResult;
import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.domain.GamePlayCredentials;
import com.getddo.core.game.domain.GameResultSubmission;
import com.getddo.core.game.domain.GameRewardClaim;
import com.getddo.core.game.repository.GamePlayRepository;
import com.getddo.core.game.repository.GameRewardClaimRepository;
import com.getddo.core.game.repository.GameStatisticsRepository;
import com.getddo.core.ticket.service.TicketGrantService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GamePlayResultRecorderTest {
	private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
	private final GamePlayRepository plays = mock(GamePlayRepository.class);
	private final ColorWordValidator validator = mock(ColorWordValidator.class);
	private final GameStatisticsRepository stats = mock(GameStatisticsRepository.class);
	private final GameRewardClaimRepository claims = mock(GameRewardClaimRepository.class);
	private final TicketGrantService grants = mock(TicketGrantService.class);
	private final UUID user = UUID.randomUUID();
	private final UUID game = UUID.randomUUID();
	private final UUID id = UUID.randomUUID();
	private final GamePlayCredentials token = GamePlayCredentials.issue(new java.security.SecureRandom());
	private final GameResultSubmission submission = new GameResultSubmission(game, id, token.getPlayToken(),
			List.of(new ColorWordAnswer(1, null, 3000)));
	private final GamePlayResultRecorder recorder = new GamePlayResultRecorder(plays, validator, stats, claims, grants,
			new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	private GamePlay play;

	@BeforeEach
	void prepare() {
		play = new GamePlay(id, game, user, "color-word-v1", "{}", token.getPlayTokenHash(),
				"color-word-generator-v1", 1L, NOW.minusSeconds(1), null, null, null);
		when(plays.findForUpdateNowait(id, user, game)).thenReturn(Optional.of(play));
		when(plays.complete(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	@DisplayName("정상 0점은 통계만 한 번 반영하고 일일 청구와 지급은 하지 않는다")
	void validZeroCountsWithoutConsumingReward() {
		// given
		when(validator.validate(play, submission.getAnswers())).thenReturn(ColorWordValidationResult.valid(0));
		// when
		assertThat(recorder.record(user, submission).getScore()).isZero();
		// then
		verify(stats).addScore(user, game, 0);
		verifyNoInteractions(claims, grants);
	}

	@Test
	@DisplayName("규칙 위반은 INVALID 확정이며 통계·청구·지급은 호출하지 않는다")
	void invalidDoesNotCount() {
		// given
		when(validator.validate(play, submission.getAnswers())).thenReturn(ColorWordValidationResult.invalid());
		// when
		assertThat(recorder.record(user, submission).getScore()).isNull();
		// then
		verifyNoInteractions(stats, claims, grants);
	}

	@Test
	@DisplayName("기존 낮은 점수 플레이 재시도는 나중에 받은 다른 플레이 보상을 붙이지 않는다")
	void replayLooksUpOnlyOriginalPlayClaim() {
		// given
		GamePlay stored = play.complete(10L, submission.getResultHash(), NOW);
		when(plays.findForUpdateNowait(id, user, game)).thenReturn(Optional.of(stored));
		// when
		assertThat(recorder.record(user, submission).getReward()).isNull();
		// then
		verify(claims).findByPlay(id);
		verifyNoInteractions(stats, validator, grants);
	}

	@Test
	@DisplayName("완료된 플레이의 변경 입력은 409이고 기존 결과를 다시 저장하지 않는다")
	void rejectsChangedReplay() {
		// given
		when(plays.findForUpdateNowait(id, user, game)).thenReturn(Optional.of(play.complete(10L, "a".repeat(64), NOW)));
		// when / then
		assertThatThrownBy(() -> recorder.record(user, submission)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode().getCode()).isEqualTo("GAME-006"));
		verify(plays, never()).complete(any());
		verifyNoInteractions(stats, validator, claims, grants);
	}

	@Test
	@DisplayName("당일 다른 플레이 청구가 있으면 기준 점수도 통계만 반영한다")
	void sameDayRewardIsNotGrantedTwice() {
		// given
		when(validator.validate(play, submission.getAnswers())).thenReturn(ColorWordValidationResult.valid(50));
		when(claims.findByDate(user, game, java.time.LocalDate.of(2026, 10, 15)))
				.thenReturn(Optional.of(mock(GameRewardClaim.class)));
		// when / then
		assertThat(recorder.record(user, submission).getReward()).isNull();
		verify(stats).addScore(user, game, 50);
		verifyNoInteractions(grants);
	}
}
