package com.getddo.core.game.exception;

/** NOWAIT에서 확인한 실제 플레이 잠금 경합. 트랜잭션 롤백 뒤에만 PROCESSING 응답으로 변환한다. */
public final class GamePlayBusyException extends RuntimeException {
	public GamePlayBusyException(Throwable cause) {
		super("플레이 결과를 다른 요청에서 처리 중입니다.", cause);
	}
}
