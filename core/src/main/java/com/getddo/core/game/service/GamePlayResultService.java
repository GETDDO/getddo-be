package com.getddo.core.game.service;

import java.sql.SQLException;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.game.domain.GamePlayCredentials;
import com.getddo.core.game.domain.GamePlayResult;
import com.getddo.core.game.domain.GameResultSubmission;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.exception.GamePlayBusyException;
import com.getddo.core.game.repository.GamePlayRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

/** G04 인증·재시도 경계. 롤백된 트랜잭션을 버린 뒤에만 재처리하거나 처리 중을 반환한다. */
@Service
@RequiredArgsConstructor
public class GamePlayResultService {
	private final GamePlayRepository plays;
	private final GamePlayResultRecorder recorder;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public GamePlayResult submit(User user, GameResultSubmission submission) {
		if (user == null || user.getRole() != UserRole.USER || user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(GameErrorCode.RESULT_ACCESS_DENIED);
		}
		// 잠금 경합 응답으로 타인의 플레이 존재가 유출되지 않도록 먼저 소유권과 토큰을 확인한다.
		plays.find(submission.getPlayId(), user.getId(), submission.getGameId())
				.filter(play -> GamePlayCredentials.matches(play, user.getId(), submission.getGameId(), submission.getPlayToken()))
				.orElseThrow(() -> new BusinessException(GameErrorCode.PLAY_NOT_FOUND));
		for (int attempt = 0; ; attempt++) {
			try {
				return recorder.record(user.getId(), submission);
			} catch (GamePlayBusyException busy) {
				return GamePlayResult.processing(submission.getPlayId());
			} catch (PessimisticLockingFailureException concurrent) {
				if (attempt >= 1) throw concurrent;
			} catch (DataIntegrityViolationException concurrent) {
				if (attempt >= 1 || !duplicateKey(concurrent)) throw concurrent;
			}
		}
	}

	private static boolean duplicateKey(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) return true;
		}
		return false;
	}
}
