package com.getddo.db.event;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.repository.EventRepository;
import com.getddo.core.event.service.EventStatusChangeService;
import com.getddo.db.common.config.JpaAuditingConfig;
import com.getddo.db.common.util.UuidBinary;
import com.getddo.db.event.entity.EventEntity;
import com.getddo.db.event.mapper.EventEntityMapper;
import com.getddo.db.event.repository.EventJpaRepository;
import com.getddo.db.event.repository.EventRepositoryImpl;
import com.getddo.db.support.MySqlTestConfiguration;
import com.getddo.db.ticket.ConcurrentTasks;
import com.getddo.db.ticket.LockWaitProbe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 서비스 프록시·JPA 저장소·MySQL로 호출자 트랜잭션의 잠금과 원자성을 검증한다. */
@SpringBootTest(classes = EventStatusChangeIntegrationTest.TestApplication.class,
		properties = "spring.jpa.hibernate.ddl-auto=validate")
class EventStatusChangeIntegrationTest {
	private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");
	private static final Instant NOW = Instant.parse("2026-10-14T03:00:00.123456Z");
	private static final int WAIT_SECONDS = 45;

	@Autowired private EventStatusChangeService service;
	@Autowired private EventRepository repository;
	@Autowired private EventJpaRepository events;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MySQLContainer mysql;
	private UUID eventId;

	@BeforeEach
	void setUp() {
		eventId = UUID.randomUUID();
		jdbc.update("""
			insert into events(id,title,description,event_type,weighting_enabled,max_tickets_per_user,
				membership_rule,starts_at,ends_at,status,created_at,updated_at)
			values(?,'상태 변경 테스트','설명','TICKET',true,5,'vip',?,?,'CLOSED',?,?)
			""", bytes(), utc(CREATED.minusSeconds(3600)), utc(CREATED), utc(CREATED), utc(CREATED));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from events where id=?", bytes());
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"CLOSED", "DRAW_CONFIRMED"})
	@DisplayName("잠금 대기 후 커밋된 상태를 새로 읽어 예상 상태에 따라 성공하거나 충돌한다")
	void waitingCallerUsesCommittedStatus(EventStatus expected) throws Exception {
		assertConcurrentChange(false, expected);
	}

	@Test
	@DisplayName("잠금 보유자의 변경이 롤백되면 대기한 호출자는 원래 상태를 기준으로 성공한다")
	void waitingCallerUsesOriginalStatusAfterRollback() throws Exception {
		assertConcurrentChange(true, EventStatus.CLOSED);
	}

	@Test
	@DisplayName("호출자 실패로 롤백되면 flush된 상태와 공통 수정 시각도 원래 값으로 복원된다")
	void callerRollbackRestoresStatusAndUpdatedAt() {
		// given
		assertThat(updatedAt()).isEqualTo(utc(CREATED));
		// when / then
		assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
			service.changeStatus(eventId, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED);
			// 같은 트랜잭션의 JDBC 조회로 실제 flush된 값도 확인한 뒤 호출자 업무 실패를 발생시킨다.
			assertThat(storedStatus()).isEqualTo(EventStatus.DRAW_CONFIRMED);
			assertThat(updatedAt()).isEqualTo(utc(NOW));
			throw new IllegalStateException("호출자 업무 실패");
		})).isInstanceOf(IllegalStateException.class).hasMessage("호출자 업무 실패");
		assertThat(storedStatus()).isEqualTo(EventStatus.CLOSED);
		assertThat(updatedAt()).isEqualTo(utc(CREATED));
	}

	@Test
	@DisplayName("호출자가 Entity에 미리 변경한 상태는 분리 전에 flush하여 보존한다")
	void preservesCallersPendingEventChange() {
		// given / when
		transaction().executeWithoutResult(status -> {
			EventEntity pending = events.findById(eventId).orElseThrow();
			pending.changeStatus(EventStatus.DRAW_CONFIRMED);
			service.changeStatus(eventId, EventStatus.DRAW_CONFIRMED, EventStatus.PUBLISHED);
			// then
			assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventStatus.PUBLISHED);
		});
		assertThat(storedStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(updatedAt()).isEqualTo(utc(NOW));
	}

	@Test
	@DisplayName("호출자 트랜잭션이 없으면 MANDATORY 프록시가 호출을 거절하고 DB를 바꾸지 않는다")
	void requiresCallerTransaction() {
		// given / when / then
		assertThatThrownBy(() -> service.changeStatus(eventId, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(storedStatus()).isEqualTo(EventStatus.CLOSED);
		assertThat(updatedAt()).isEqualTo(utc(CREATED));
	}

	@Test
	@DisplayName("존재하지 않는 이벤트는 프록시 예외 대신 EVENT_NOT_FOUND를 반환한다")
	void rejectsMissingEventWithBusinessError() {
		// given
		UUID missingId = UUID.randomUUID();
		// when / then
		assertError(EventErrorCode.EVENT_NOT_FOUND, missingId, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED);
		assertThat(jdbc.queryForObject("select count(*) from events where id=?", Long.class,
				UuidBinary.toBytes(missingId))).isZero();
		assertThat(storedStatus()).isEqualTo(EventStatus.CLOSED);
		assertThat(updatedAt()).isEqualTo(utc(CREATED));
	}

	@Test
	@DisplayName("삭제된 이벤트는 EVENT_NOT_FOUND를 반환하고 상태·수정 시각·삭제 시각을 유지한다")
	void rejectsDeletedEventWithoutChangingRow() {
		// given
		jdbc.update("update events set deleted_at=? where id=?", utc(CREATED), bytes());
		// when / then
		assertError(EventErrorCode.EVENT_NOT_FOUND, eventId, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED);
		assertThat(storedStatus()).isEqualTo(EventStatus.CLOSED);
		assertThat(updatedAt()).isEqualTo(utc(CREATED));
		assertThat(jdbc.queryForObject("select deleted_at from events where id=?", LocalDateTime.class, bytes()))
				.isEqualTo(utc(CREATED));
	}

	@Test
	@DisplayName("취소된 이벤트의 상태 변경은 충돌을 반환하고 상태와 수정 시각을 유지한다")
	void rejectsLeavingCanceledStatusWithoutWrite() {
		// given
		jdbc.update("update events set status='CANCELED' where id=?", bytes());
		// when / then
		assertError(EventErrorCode.STATUS_CHANGE_CONFLICT, eventId, EventStatus.CANCELED, EventStatus.OPEN);
		assertThat(storedStatus()).isEqualTo(EventStatus.CANCELED);
		assertThat(updatedAt()).isEqualTo(utc(CREATED));
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"CLOSED", "CANCELED"})
	@DisplayName("동일 상태 요청은 취소 상태를 포함해 실제 DB의 상태와 수정 시각을 바꾸지 않는다")
	void sameStatusDoesNotChangeUpdatedAt(EventStatus state) {
		// given
		jdbc.update("update events set status=? where id=?", state.name(), bytes());
		// when
		transaction().executeWithoutResult(status -> service.changeStatus(eventId, state, state));
		// then
		assertThat(storedStatus()).isEqualTo(state);
		assertThat(updatedAt()).isEqualTo(utc(CREATED));
	}

	@Test
	@DisplayName("잠금 없는 직접 상태 저장은 호출 계약 위반으로 거절하고 DB를 바꾸지 않는다")
	void rejectsRepositoryWriteWithoutExclusiveLock() {
		// given / when / then
		assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
			// 일반 조회로 Entity를 관리 중이어도 쓰기 잠금이 없다면 저장할 수 없다.
			events.findById(eventId).orElseThrow();
			repository.updateStatus(eventId, EventStatus.DRAW_CONFIRMED);
		})).isInstanceOf(InvalidDataAccessApiUsageException.class)
				.hasRootCauseInstanceOf(IllegalStateException.class)
				.rootCause().hasMessage("상태 변경 전에 같은 트랜잭션에서 이벤트 쓰기 잠금을 확보해야 합니다.");
		assertThat(storedStatus()).isEqualTo(EventStatus.CLOSED);
		assertThat(updatedAt()).isEqualTo(utc(CREATED));
	}

	private void assertError(EventErrorCode code, UUID id, EventStatus expected, EventStatus next) {
		assertThatThrownBy(() -> transaction().executeWithoutResult(status -> service.changeStatus(id, expected, next)))
				.isInstanceOfSatisfying(EventException.class, failure -> assertThat(failure.getErrorCode()).isEqualTo(code));
	}

	/** 각 스레드에 독립 트랜잭션을 열고 실제 InnoDB 잠금 대기를 확인한 뒤 보유자를 종료한다. */
	private void assertConcurrentChange(boolean rollbackFirst, EventStatus expected) throws Exception {
		// given
		CountDownLatch changed = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> first = executor.submit(() -> transaction().executeWithoutResult(status -> {
				service.changeStatus(eventId, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED);
				changed.countDown();
				await(release);
				if (rollbackFirst) {
					status.setRollbackOnly();
				}
			}));
			assertThat(changed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			// when
			Future<?> second = executor.submit(() -> transaction().executeWithoutResult(status -> {
				// 첫 변경은 미커밋이므로 일반 조회는 CLOSED를 캐시한다. 서비스는 잠금 읽기로 최신 값을 다시 적재해야 한다.
				assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventStatus.CLOSED);
				service.changeStatus(eventId, expected, EventStatus.PUBLISHED);
			}));
			new LockWaitProbe(mysql).awaitLockWaits(1);
			assertThat(first.isDone()).isFalse();
			assertThat(second.isDone()).isFalse();
			// 다른 연결에는 아직 미커밋 상태·수정 시각이 노출되지 않는다.
			assertThat(storedStatus()).isEqualTo(EventStatus.CLOSED);
			assertThat(updatedAt()).isEqualTo(utc(CREATED));
			release.countDown();
			// then
			first.get(WAIT_SECONDS, TimeUnit.SECONDS);
			if (!rollbackFirst && expected == EventStatus.CLOSED) {
				assertThatThrownBy(() -> second.get(WAIT_SECONDS, TimeUnit.SECONDS))
						.isInstanceOf(ExecutionException.class)
						.hasCauseInstanceOf(EventException.class)
						.satisfies(failure -> assertThat(((EventException) failure.getCause()).getErrorCode())
								.isEqualTo(EventErrorCode.STATUS_CHANGE_CONFLICT));
				assertThat(storedStatus()).isEqualTo(EventStatus.DRAW_CONFIRMED);
			} else {
				second.get(WAIT_SECONDS, TimeUnit.SECONDS);
				assertThat(storedStatus()).isEqualTo(EventStatus.PUBLISHED);
			}
			assertThat(updatedAt()).isEqualTo(utc(NOW));
		} finally {
			release.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private EventStatus storedStatus() {
		return EventStatus.valueOf(jdbc.queryForObject("select status from events where id=?", String.class, bytes()));
	}

	private LocalDateTime updatedAt() {
		return jdbc.queryForObject("select updated_at from events where id=?", LocalDateTime.class, bytes());
	}

	private byte[] bytes() {
		return UuidBinary.toBytes(eventId);
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("상태 변경 트랜잭션 대기 시간 초과");
			}
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("상태 변경 트랜잭션 대기 중 중단", failure);
		}
	}

	/** 이벤트 저장 경로와 공통 Auditing만 올리고 운영 Flyway로 생성한 독립 MySQL을 사용한다. */
	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EntityScan(basePackageClasses = EventEntity.class)
	@EnableJpaRepositories(basePackageClasses = EventJpaRepository.class)
	@ComponentScan(basePackageClasses = EventEntityMapper.class)
	@Import({MySqlTestConfiguration.class, JpaAuditingConfig.class,
			EventRepositoryImpl.class, EventStatusChangeService.class})
	static class TestApplication {
		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
