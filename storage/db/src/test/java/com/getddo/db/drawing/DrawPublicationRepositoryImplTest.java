package com.getddo.db.drawing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** JPA 대역으로 공개·확인 변환을 검증한다. 실제 DB 제약·잠금·시각 정밀도 검증은 아니다. */
class DrawPublicationRepositoryImplTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	private final DrawPublicationJpaRepository versions = mock(DrawPublicationJpaRepository.class);
	private final DrawPublicationResultJpaRepository entries = mock(DrawPublicationResultJpaRepository.class);
	private final DrawResultConfirmationJpaRepository confirmations = mock(DrawResultConfirmationJpaRepository.class);
	private final EntityManager entityManager = mock(EntityManager.class);
	private final DrawPublicationRepositoryImpl repository = new DrawPublicationRepositoryImpl(versions, entries,
			entityManager, mock(JdbcTemplate.class), confirmations);

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

	@Test
	@DisplayName("페이지 조회는 최신 버전과 실제 포함 결과를 일괄 변환하고 전체 건수를 유지한다")
	void loadsPageWithBatchedEntries() {
		UUID event = UUID.randomUUID(), run = UUID.randomUUID(), id = UUID.randomUUID();
		DrawPublicationEntity stored = new DrawPublicationEntity(event, run, NOW);
		ReflectionTestUtils.setField(stored, "id", id);
		DrawPublication.Entry entry = new DrawPublication.Entry(UUID.randomUUID(), run, null);
		when(versions.countByEventId(event)).thenReturn(4L);
		when(versions.findByEventIdOrderByRevisionDesc(event, PageRequest.of(1, 1))).thenReturn(List.of(stored));
		when(entries.findByIdPublicationIdIn(Set.of(id)))
				.thenReturn(List.of(new DrawPublicationResultEntity(id, event, entry)));
		PageResult<DrawPublication> page = repository.findHistory(event, new PageQuery(2, 1));
		assertThat(page.getTotalElements()).isEqualTo(4);
		assertThat(page.getPage()).isEqualTo(2);
		assertThat(page.getItems().getFirst().entries()).containsExactly(entry);
		verify(entries, never()).findByIdPublicationIdOrderByIdResultId(any());
	}

	@Test
	@DisplayName("큰 페이지 번호는 오프셋 오버플로 없이 빈 페이지를 반환하고 목록을 조회하지 않는다")
	void hugePageReturnsEmptyWithoutFetchingRows() {
		UUID event = UUID.randomUUID();
		when(versions.countByEventId(event)).thenReturn(4L);
		PageResult<DrawPublication> page = repository.findHistory(event, new PageQuery(Integer.MAX_VALUE, 100));
		assertThat(page.getItems()).isEmpty();
		assertThat(page.getTotalElements()).isEqualTo(4);
		verify(versions, never()).findByEventIdOrderByRevisionDesc(any(), any());
		verifyNoInteractions(entries);
	}

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
}
