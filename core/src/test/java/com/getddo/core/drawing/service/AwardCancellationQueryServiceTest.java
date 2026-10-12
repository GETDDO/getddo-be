package com.getddo.core.drawing.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;
import com.getddo.core.user.domain.*;
import com.getddo.core.user.repository.UserRepository;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;
import static com.getddo.core.drawing.exception.AwardCancellationErrorCode.NOT_FOUND;
import static com.getddo.core.drawing.exception.AwardCancellationErrorCode.NOT_CANCELABLE;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwardCancellationQueryServiceTest {
	private final UUID actor = UUID.randomUUID(), id = UUID.randomUUID(), run = UUID.randomUUID();
	private final AwardCancellationRepository cancellations = mock(AwardCancellationRepository.class);
	private final DrawSnapshotRepository snapshots = mock(DrawSnapshotRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final AwardCancellationQueryService query = new AwardCancellationQueryService(cancellations, snapshots, users);

	@BeforeEach void setUp() {
		when(users.findById(actor)).thenReturn(Optional.of(user(UserRole.ADMIN, UserStatus.ACTIVE)));
		when(cancellations.findById(id)).thenReturn(Optional.of(new AwardCancellation(id, UUID.randomUUID(), run, actor, "사유", Instant.EPOCH)));
	}

	@Test @DisplayName("AD09는 이미 연결된 재추첨 실행만 조회한다")
	void readsConnectedReplacement() {
		// given
		DrawSnapshot stored = snapshot(DrawType.REPLACEMENT);
		when(snapshots.findRegistered(run)).thenReturn(Optional.of(stored));
		// when / then
		assertThat(query.findConnected(actor, id)).isSameAs(stored);
		verify(snapshots).findRegistered(run);
	}

	@Test @DisplayName("권한 없는 사용자는 취소 기록을 조회하지 않는다")
	void rejectsInactiveAndNormalUsers() {
		// given / when / then
		when(users.findById(actor)).thenReturn(Optional.of(user(UserRole.USER, UserStatus.ACTIVE)));
		assertCode(() -> query.findConnected(actor, id), ADMIN_REQUIRED);
		when(users.findById(actor)).thenReturn(Optional.of(user(UserRole.ADMIN, UserStatus.INACTIVE)));
		assertCode(() -> query.findConnected(actor, id), ADMIN_REQUIRED);
		verifyNoInteractions(cancellations, snapshots);
	}

	@Test @DisplayName("없는 취소와 최초 실행으로 잘못 연결된 취소는 거절한다")
	void rejectsMissingCancellationAndInitialLink() {
		// given / when / then
		when(cancellations.findById(id)).thenReturn(Optional.empty());
		assertCode(() -> query.findConnected(actor, id), NOT_FOUND);
		when(cancellations.findById(id)).thenReturn(Optional.of(new AwardCancellation(id, UUID.randomUUID(), run, actor, "사유", Instant.EPOCH)));
		when(snapshots.findRegistered(run)).thenReturn(Optional.of(snapshot(DrawType.INITIAL)));
		assertCode(() -> query.findConnected(actor, id), INVALID_RUN);
	}

	private User user(UserRole role, UserStatus status) {
		return new User(actor, "관리자", role, status, Membership.VIP, null, null, null, null, Instant.EPOCH, Instant.EPOCH);
	}

	@Test @DisplayName("과거 미연결 취소는 새 실행을 추정하지 않고 서비스에서 거절한다")
	void rejectsUnlinkedHistoryWithoutLookingUpRun() {
		when(cancellations.findById(id)).thenReturn(Optional.of(new AwardCancellation(id, UUID.randomUUID(), null,
				actor, "과거 사유", Instant.EPOCH)));
		assertCode(() -> query.findConnected(actor, id), NOT_CANCELABLE);
		verifyNoInteractions(snapshots);
	}
	private DrawSnapshot snapshot(DrawType type) { return new DrawSnapshot(run, UUID.randomUUID(), DrawRunStatus.PREPARING, null, null, null, List.of(), type); }
	private static void assertCode(Runnable operation, com.getddo.core.common.exception.ErrorCode code) {
		assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
	}
}
