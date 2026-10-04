package com.getddo.db.ticket;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 동시성 테스트의 작업 스레드를 끝낸다.
 *
 * <p>{@code shutdownNow()}는 interrupt만 보내고 기다리지 않는다. DB 잠금이나 JDBC 응답을 기다리는 스레드는 interrupt로
 * 멈추지 않으므로, 기다리지 않으면 트랜잭션이 열린 채로 {@code @AfterEach} 정리가 시작되어 외래 키 오류와 남은 데이터가
 * 다른 테스트로 번진다. 테스트는 대기 중인 latch를 먼저 풀고 이 메서드를 {@code finally}에서 부른다.</p>
 */
public final class ConcurrentTasks {

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private ConcurrentTasks() {
	}

	/**
	 * 새 작업을 받지 않고 실행 중인 작업이 끝나기를 기다린다. 제한 시간 안에 끝나지 않으면 interrupt한 뒤 한 번 더 기다린다.
	 *
	 * @throws AssertionError 작업 스레드가 끝나지 않은 경우. 정리를 진행하면 다른 테스트가 오염되므로 실패로 알린다
	 */
	public static void shutdownAndAwait(ExecutorService executor) throws InterruptedException {
		executor.shutdown();
		if (executor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
			return;
		}
		executor.shutdownNow();
		if (!executor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
			throw new AssertionError("동시성 테스트의 작업 스레드가 끝나지 않았다.");
		}
	}
}
