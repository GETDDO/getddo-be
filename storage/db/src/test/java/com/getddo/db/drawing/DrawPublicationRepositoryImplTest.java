package com.getddo.db.drawing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.ResultSet;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.getddo.core.drawing.domain.*;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.db.drawing.entity.*;
import com.getddo.db.drawing.repository.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.getddo.db.common.util.UuidBinary.toBytes;

/** JPA 대역으로 공개·확인 변환을 검증한다. 실제 DB 제약·잠금·시각 정밀도 검증은 아니다. */
class DrawPublicationRepositoryImplTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	private final DrawPublicationJpaRepository versions = mock(DrawPublicationJpaRepository.class);
	private final DrawPublicationResultJpaRepository entries = mock(DrawPublicationResultJpaRepository.class);
	private final DrawResultConfirmationJpaRepository confirmations = mock(DrawResultConfirmationJpaRepository.class);
	private final EntityManager entityManager = mock(EntityManager.class);
	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final DrawPublicationExecutionJpaRepository evidence = mock(DrawPublicationExecutionJpaRepository.class);
	private final DrawPublicationRepositoryImpl repository = new DrawPublicationRepositoryImpl(versions, entries,
			entityManager, jdbc, confirmations, evidence);

	@Test @DisplayName("빈 공개 실행 근거는 저장·조회 없이 빈 맵을 반환한다")
	void skipsEmptyExecutionEvidence() {
		// given / when / then
		repository.saveExecutionEvidence(UUID.randomUUID(), UUID.randomUUID(), List.of());
		assertThat(repository.findExecutionEvidence(Set.of())).isEmpty();
		verifyNoInteractions(evidence, jdbc);
	}

	@Test @DisplayName("공개 실행 연결은 버전·이벤트·확인 ID를 그대로 저장한다")
	void savesExecutionIdentity() {
		// given
		UUID version = UUID.randomUUID(), event = UUID.randomUUID();
		DrawPublicationExecution execution = new DrawPublicationExecution(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW, List.of());
		doAnswer(invocation -> {
			List<DrawPublicationExecutionEntity> rows = invocation.getArgument(0);
			assertThat(rows).singleElement().satisfies(row -> {
				assertThat(row.getId()).isEqualTo(new DrawPublicationExecutionId(version, execution.runId()));
				assertThat(row.getEventId()).isEqualTo(event);
				assertThat(row.getConfirmationId()).isEqualTo(execution.confirmationId());
			});
			return rows;
		}).when(evidence).saveAllAndFlush(anyList());
		// when
		repository.saveExecutionEvidence(version, event, List.of(execution));
		// then
		verify(evidence).saveAllAndFlush(anyList());
	}

	@Test @DisplayName("버전의 실행별 확인·취소 근거를 일괄 연결하고 불변 맵과 목록으로 반환한다")
	void readsBatchedExecutionEvidence() throws Exception {
		// given
		UUID version = UUID.randomUUID(), missing = UUID.randomUUID(), runA = UUID.randomUUID(), runB = UUID.randomUUID();
		UUID checkA = UUID.randomUUID(), checkB = UUID.randomUUID(), actor = UUID.randomUUID();
		ResultSet first = evidenceRow(version, runA, checkA, actor), second = evidenceRow(version, runB, checkB, actor);
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
			RowMapper<?> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(first, 0), mapper.mapRow(second, 1));
		});
		UUID cancelA = UUID.randomUUID(), cancelB = UUID.randomUUID(), resultA = UUID.randomUUID(), resultB = UUID.randomUUID();
		ResultSet causeA = cancellationRow(runA, cancelA, resultA, "첫 사유"), causeB = cancellationRow(runA, cancelB, resultB, "다음 사유");
		doAnswer(invocation -> {
			RowCallbackHandler handler = invocation.getArgument(1);
			handler.processRow(causeA);
			handler.processRow(causeB);
			return null;
		}).when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
		// when
		java.util.Map<UUID, List<DrawPublicationExecution>> loaded = repository.findExecutionEvidence(Set.of(version, missing));
		// then
		assertThat(loaded).doesNotContainKey(missing);
		assertThat(loaded.get(version)).extracting(DrawPublicationExecution::runId).containsExactly(runA, runB);
		assertThat(loaded.get(version).getFirst().confirmationId()).isEqualTo(checkA);
		assertThat(loaded.get(version).getFirst().confirmedBy()).isEqualTo(actor);
		assertThat(loaded.get(version).getFirst().confirmedAt()).isEqualTo(NOW);
		assertThat(loaded.get(version).getFirst().cancellations()).usingRecursiveComparison().isEqualTo(List.of(
				new DrawPublicationCancellation(cancelA, resultA, runA, "첫 사유"), new DrawPublicationCancellation(cancelB, resultB, runA, "다음 사유")));
		assertThat(loaded.get(version).getLast().cancellations()).isEmpty();
		assertThatThrownBy(() -> loaded.put(missing, List.of())).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> loaded.get(version).clear()).isInstanceOf(UnsupportedOperationException.class);
		verify(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
		verify(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
	}

	private ResultSet evidenceRow(UUID version, UUID run, UUID confirmation, UUID actor) throws Exception {
		ResultSet row = mock(ResultSet.class);
		when(row.getBytes("publication_id")).thenReturn(toBytes(version));
		when(row.getBytes("draw_run_id")).thenReturn(toBytes(run));
		when(row.getBytes("confirmation_id")).thenReturn(toBytes(confirmation));
		when(row.getBytes("confirmed_by")).thenReturn(toBytes(actor));
		when(row.getObject("confirmed_at", LocalDateTime.class)).thenReturn(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
		return row;
	}
	private ResultSet cancellationRow(UUID run, UUID id, UUID result, String reason) throws Exception {
		ResultSet row = mock(ResultSet.class);
		when(row.getBytes("draw_run_id")).thenReturn(toBytes(run));
		when(row.getBytes("id")).thenReturn(toBytes(id));
		when(row.getBytes("draw_result_id")).thenReturn(toBytes(result));
		when(row.getString("reason")).thenReturn(reason);
		return row;
	}

	/** 갱신 저장은 유지 결과의 원본 실행과 기존 확인·새 결과의 새 확인을 그대로 연결한다는 시나리오를 검증한다. */
	@Test
	@DisplayName("갱신 저장은 유지 결과의 원본 실행과 기존 확인·새 결과의 새 확인을 그대로 연결한다")
	void preservesMixedSourcesAndConfirmationIds() {
		// given
		UUID event = UUID.randomUUID(), previousRun = UUID.randomUUID(), newRun = UUID.randomUUID();
		UUID publicationId = UUID.randomUUID(), previousCheck = UUID.randomUUID(), newCheck = UUID.randomUUID();
		DrawPublication.Entry retained = new DrawPublication.Entry(UUID.randomUUID(), previousRun, previousCheck);
		DrawPublication.Entry replacement = new DrawPublication.Entry(UUID.randomUUID(), newRun, newCheck);
		DrawResultConfirmation checked = new DrawResultConfirmation(newCheck, newRun, UUID.randomUUID(), NOW.minusSeconds(1));
		DrawPublicationEntity stored = new DrawPublicationEntity(new DrawPublication(publicationId, event, newRun, 2,
				DrawPublicationType.UPDATE, "연결 취소 사유", checked.confirmedBy(), checked.confirmedAt(), NOW,
				List.of(retained, replacement)));
		ReflectionTestUtils.setField(stored, "id", publicationId);
		when(versions.saveAndFlush(any())).thenReturn(stored);
		when(versions.findById(publicationId)).thenReturn(Optional.of(stored));
		when(entries.findByIdPublicationIdOrderByIdResultId(publicationId)).thenReturn(List.of(
				new DrawPublicationResultEntity(publicationId, event, retained),
				new DrawPublicationResultEntity(publicationId, event, replacement)));
		doAnswer(invocation -> {
			List<DrawPublicationResultEntity> saved = invocation.getArgument(0);
			assertThat(saved).extracting(DrawPublicationResultEntity::getEventId).containsOnly(event);
			assertThat(saved).extracting(DrawPublicationResultEntity::toDomain).containsExactly(retained, replacement);
			return saved;
		}).when(entries).saveAllAndFlush(anyList());
		// when
		DrawPublication published = repository.savePublication(event, newRun, 2, "연결 취소 사유", checked,
				List.of(retained, replacement), NOW);
		// then
		assertThat(published.entries()).containsExactly(retained, replacement);
		assertThat(published.sourceDrawId()).isEqualTo(newRun);
		assertThat(published.confirmedAt()).isEqualTo(checked.confirmedAt());
		verify(entityManager).detach(stored);
		verify(entityManager, never()).refresh(any());
	}

	/** 확인 저장 응답은 생성 ID와 실제 재조회한 확인자·시각을 반환한다는 시나리오를 검증한다. */
	@Test
	@DisplayName("확인 저장 응답은 생성 ID와 실제 재조회한 확인자·시각을 반환한다")
	void returnsReloadedConfirmation() {
		// given
		UUID run = UUID.randomUUID(), actor = UUID.randomUUID(), id = UUID.randomUUID();
		DrawResultConfirmationEntity stored = new DrawResultConfirmationEntity(run, actor, NOW);
		ReflectionTestUtils.setField(stored, "id", id);
		when(confirmations.saveAndFlush(any())).thenReturn(stored);
		when(confirmations.findById(id)).thenReturn(Optional.of(stored));
		// when
		DrawResultConfirmation confirmation = repository.saveConfirmation(run, actor, NOW);
		// then
		assertThat(confirmation).isEqualTo(new DrawResultConfirmation(id, run, actor, NOW));
		verify(entityManager).detach(stored);
		verify(entityManager, never()).refresh(any());
	}

	/** 페이지 조회는 최신 버전과 실제 포함 결과를 일괄 변환하고 전체 건수를 유지한다는 시나리오를 검증한다. */
	@Test
	@DisplayName("페이지 조회는 최신 버전과 실제 포함 결과를 일괄 변환하고 전체 건수를 유지한다")
	void loadsPageWithBatchedEntries() {
		// given
		UUID event = UUID.randomUUID(), run = UUID.randomUUID(), id = UUID.randomUUID();
		DrawPublicationEntity stored = new DrawPublicationEntity(event, run, NOW);
		ReflectionTestUtils.setField(stored, "id", id);
		DrawPublication.Entry entry = new DrawPublication.Entry(UUID.randomUUID(), run, null);
		when(versions.countByEventId(event)).thenReturn(4L);
		when(versions.findByEventIdOrderByRevisionDesc(event, PageRequest.of(1, 1))).thenReturn(List.of(stored));
		when(entries.findByIdPublicationIdIn(Set.of(id)))
				.thenReturn(List.of(new DrawPublicationResultEntity(id, event, entry)));
		// when
		PageResult<DrawPublication> page = repository.findHistory(event, new PageQuery(2, 1));
		// then
		assertThat(page.getTotalElements()).isEqualTo(4);
		assertThat(page.getPage()).isEqualTo(2);
		assertThat(page.getItems().getFirst().entries()).containsExactly(entry);
		verify(entries, never()).findByIdPublicationIdOrderByIdResultId(any());
	}

	/** 큰 페이지 번호는 오프셋 오버플로 없이 빈 페이지를 반환하고 목록을 조회하지 않는다는 시나리오를 검증한다. */
	@Test
	@DisplayName("큰 페이지 번호는 오프셋 오버플로 없이 빈 페이지를 반환하고 목록을 조회하지 않는다")
	void hugePageReturnsEmptyWithoutFetchingRows() {
		// given
		UUID event = UUID.randomUUID();
		when(versions.countByEventId(event)).thenReturn(4L);
		// when
		PageResult<DrawPublication> page = repository.findHistory(event, new PageQuery(Integer.MAX_VALUE, 100));
		// then
		assertThat(page.getItems()).isEmpty();
		assertThat(page.getTotalElements()).isEqualTo(4);
		verify(versions, never()).findByEventIdOrderByRevisionDesc(any(), any());
		verifyNoInteractions(entries);
	}

	/** 복합키 공개 포함 결과는 신규 저장에 persist를 선택하고 영속화 후 기존 행으로 판정한다는 시나리오를 검증한다. */
	@Test
	@DisplayName("복합키 공개 포함 결과는 신규 저장에 persist를 선택하고 영속화 후 기존 행으로 판정한다")
	void assignedCompositeKeyUsesPersistForNewRows() {
		// given
		DrawPublicationResultEntity entry = new DrawPublicationResultEntity(UUID.randomUUID(), UUID.randomUUID(),
				new DrawPublication.Entry(UUID.randomUUID(), UUID.randomUUID(), null));
		jakarta.persistence.metamodel.Metamodel metamodel = mock(jakarta.persistence.metamodel.Metamodel.class);
		jakarta.persistence.metamodel.EntityType<DrawPublicationResultEntity> entityType = mock(jakarta.persistence.metamodel.EntityType.class);
		doReturn(entityType).when(metamodel).managedType(DrawPublicationResultEntity.class);
		when(entityManager.getEntityManagerFactory()).thenReturn(mock(jakarta.persistence.EntityManagerFactory.class));
		org.springframework.data.jpa.repository.support.JpaEntityInformation<DrawPublicationResultEntity, ?> information =
				new org.springframework.data.jpa.repository.support.JpaPersistableEntityInformation<>(DrawPublicationResultEntity.class,
						metamodel, mock(jakarta.persistence.PersistenceUnitUtil.class));
		org.springframework.data.jpa.repository.support.SimpleJpaRepository<DrawPublicationResultEntity, ?> jpa =
				new org.springframework.data.jpa.repository.support.SimpleJpaRepository<>(information, entityManager);
		// when
		jpa.save(entry);
		// then
		assertThat(entry.isNew()).isTrue();
		verify(entityManager).persist(entry);
		verify(entityManager, never()).merge(any());
		ReflectionTestUtils.invokeMethod(entry, "markPersisted");
		assertThat(information.isNew(entry)).isFalse();
	}

	/** 공개 Entity는 ID 발급 전 필수 메타데이터를 검증하고 실제 ID를 임의로 만들지 않는다는 시나리오를 검증한다. */
	@Test
	@DisplayName("공개 Entity는 ID 발급 전 필수 메타데이터를 검증하고 실제 ID를 임의로 만들지 않는다")
	void validatesMetadataBeforeIdGeneration() {
		// given
		UUID event = UUID.randomUUID(), run = UUID.randomUUID();
		// when / then
		assertThatThrownBy(() -> new DrawPublicationEntity(null, run, NOW)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationEntity(event, null, NOW)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationEntity(event, run, null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationEntity(event, run, 2, "사유", null, null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
		DrawPublicationEntity entity = new DrawPublicationEntity(event, run, NOW);
		assertThat(entity.getId()).isNull();
		assertThat(entity.getSourceDrawId()).isEqualTo(run);
		assertThat(entity.getType()).isEqualTo(DrawPublicationType.INITIAL);
	}

	/** 공개 목록 중복은 ID 발급 전 거절하며 저장소에 부분 버전을 쓰지 않는다는 시나리오를 검증한다. */
	@Test
	@DisplayName("공개 목록 중복은 ID 발급 전 거절하며 저장소에 부분 버전을 쓰지 않는다")
	void rejectsDuplicateEntriesBeforeSavingVersion() {
		// given
		UUID run = UUID.randomUUID();
		DrawPublication.Entry entry = new DrawPublication.Entry(UUID.randomUUID(), run, null);
		// when / then
		assertThatThrownBy(() -> repository.savePublication(UUID.randomUUID(), run, 1, null, null,
				List.of(entry, entry), NOW)).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(versions, entries);
	}

	/** 여러 재추첨의 결과와 확인을 실행별 추가 쿼리 없이 배치로 연결하고 미충원을 보존한다는 시나리오를 검증한다. */
	@Test
	@DisplayName("여러 재추첨의 결과와 확인을 실행별 추가 쿼리 없이 배치로 연결하고 미충원을 보존한다")
	void batchesReplacementResultsAndConfirmations() throws Exception {
		// given
		UUID event = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
		UUID selectedId = UUID.randomUUID(), unfilledId = UUID.randomUUID();
		ResultSet firstRun = runRow(first, event, 1), secondRun = runRow(second, event, 2);
		when(jdbc.query(anyString(), any(RowMapper.class), any(byte[].class))).thenAnswer(invocation -> {
			RowMapper<?> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(firstRun, 0), mapper.mapRow(secondRun, 1));
		});
		ResultSet selected = resultRow(first, selectedId, event, true), unfilled = resultRow(second, unfilledId, event, false);
		doAnswer(invocation -> {
			RowCallbackHandler mapper = invocation.getArgument(1);
			mapper.processRow(selected);
			mapper.processRow(unfilled);
			return null;
		}).when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
		DrawResultConfirmationEntity check = new DrawResultConfirmationEntity(first, UUID.randomUUID(), NOW);
		ReflectionTestUtils.setField(check, "id", UUID.randomUUID());
		when(confirmations.findByRunIdIn(Set.of(first, second))).thenReturn(List.of(check));
		// when
		List<DrawPublicationRun> runs = repository.findReplacementRuns(event);
		// then
		assertThat(runs).extracting(DrawPublicationRun::runId).containsExactly(first, second);
		assertThat(runs.getFirst().confirmation()).isEqualTo(check.toDomain());
		assertThat(runs.getFirst().results()).extracting(DrawResult::getId).containsExactly(selectedId);
		assertThat(runs.getLast().confirmation()).isNull();
		assertThat(runs.getLast().results().getFirst().getSelection().getResultType()).isEqualTo(DrawSelectionResultType.UNFILLED);
		assertThat(runs.getLast().results().getFirst().getSelection().getUserId()).isNull();
		verify(confirmations).findByRunIdIn(Set.of(first, second));
		verify(confirmations, never()).findByRunId(any());
		verify(jdbc, times(1)).query(anyString(), any(RowMapper.class), any(byte[].class));
		verify(jdbc, times(1)).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
	}

	/** 재추첨이 없으면 결과와 확인의 빈 IN 조회를 실행하지 않는다는 시나리오를 검증한다. */
	@Test
	@DisplayName("재추첨이 없으면 결과와 확인의 빈 IN 조회를 실행하지 않는다")
	void skipsBatchReadsForNoReplacementRuns() {
		// given
		when(jdbc.query(anyString(), any(RowMapper.class), any(byte[].class))).thenReturn(List.of());
		// when / then
		assertThat(repository.findReplacementRuns(UUID.randomUUID())).isEmpty();
		verify(jdbc, never()).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
		verifyNoInteractions(confirmations);
	}

	/** 일괄 조회에서도 다른 이벤트의 경품을 가진 결과는 공개 모델로 변환하지 않는다는 시나리오를 검증한다. */
	@Test
	@DisplayName("일괄 조회에서도 다른 이벤트의 경품을 가진 결과는 공개 모델로 변환하지 않는다")
	void rejectsForeignEventInBatchedResults() throws Exception {
		// given
		UUID run = UUID.randomUUID(), event = UUID.randomUUID();
		ResultSet metadata = runRow(run, event, 1), foreign = resultRow(run, UUID.randomUUID(), UUID.randomUUID(), true);
		when(jdbc.query(anyString(), any(RowMapper.class), any(byte[].class))).thenAnswer(invocation -> {
			RowMapper<?> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(metadata, 0));
		});
		doAnswer(invocation -> {
			RowCallbackHandler mapper = invocation.getArgument(1);
			mapper.processRow(foreign);
			return null;
		}).when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
		// when / then
		assertThatThrownBy(() -> repository.findReplacementRuns(event)).isInstanceOf(com.getddo.core.common.exception.BusinessException.class);
		verifyNoInteractions(confirmations);
	}

	/** 배치 실행 조회 변환을 검증할 시험용 JDBC 실행 행을 구성한다. */
	private ResultSet runRow(UUID id, UUID eventId, int number) throws Exception {
		ResultSet row = mock(ResultSet.class);
		when(row.getBytes("id")).thenReturn(toBytes(id));
		when(row.getBytes("event_id")).thenReturn(toBytes(eventId));
		when(row.getInt("run_number")).thenReturn(number);
		when(row.getString("status")).thenReturn("CONFIRMED");
		when(row.getObject("confirmed_at", LocalDateTime.class)).thenReturn(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
		return row;
	}

	/** 배치 결과 조회 변환을 검증할 선정 또는 미충원 JDBC 행을 구성한다. */
	private ResultSet resultRow(UUID runId, UUID id, UUID eventId, boolean selected) throws Exception {
		ResultSet row = mock(ResultSet.class);
		when(row.getBytes("draw_run_id")).thenReturn(toBytes(runId));
		when(row.getBytes("id")).thenReturn(toBytes(id));
		when(row.getBytes("event_prize_id")).thenReturn(toBytes(UUID.randomUUID()));
		when(row.getBytes("prize_event")).thenReturn(toBytes(eventId));
		when(row.getInt("prize_rank")).thenReturn(1);
		when(row.getInt("slot_number")).thenReturn(1);
		when(row.getString("result_type")).thenReturn(selected ? "SELECTED" : "UNFILLED");
		when(row.getObject("created_at", LocalDateTime.class)).thenReturn(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
		if (selected) {
			byte[] candidate = toBytes(UUID.randomUUID());
			when(row.getBytes("candidate_id")).thenReturn(candidate);
			when(row.getBytes("linked_candidate")).thenReturn(candidate);
			when(row.getBytes("candidate_event")).thenReturn(toBytes(eventId));
			when(row.getBytes("participant_event")).thenReturn(toBytes(eventId));
			when(row.getBytes("user_id")).thenReturn(toBytes(UUID.randomUUID()));
			when(row.getObject("selection_order", Integer.class)).thenReturn(1);
		}
		return row;
	}
}
