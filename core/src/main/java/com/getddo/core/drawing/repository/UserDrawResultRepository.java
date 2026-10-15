package com.getddo.core.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.drawing.domain.UserDrawResult.MaskedWinner;

/** 기존 응모와 실제 공개에 포함된 결과만 읽는다. */
public interface UserDrawResultRepository {
	/** 접수된 ACCEPTED 응모 이력이 있는지 확인한다. 거절된 요청만 있는 사용자는 미응모자로 구분한다. */
	boolean hasEntered(UUID eventId, UUID userId);

	/** 최초 추첨이 응모자 없이 종료된 근거를 확인해 결과 대기 상태와 구분한다. */
	boolean hasNoEntriesRun(UUID eventId);

	/**
	 * 지정한 공개 버전에 포함된 당첨자만 읽고 개인정보는 가린 값으로 반환한다.
	 * 최신 내부 결과나 이후 취소 여부로 이미 공개된 버전의 명단을 바꾸지 않는다.
	 *
	 * @param publicationId 이벤트 식별자가 아닌 실제 공개 버전의 식별자
	 * @return 경품 연결 정보와 마스킹된 이름·전화번호를 가진 당첨자 목록
	 */
	List<MaskedWinner> findWinners(UUID publicationId);

	/**
	 * 지정한 공개 버전에서 해당 사용자에게 배정된 경품을 찾는다.
	 *
	 * @param publicationId 본인 결과를 판단할 실제 공개 버전의 식별자
	 * @param userId 소유권을 확인할 사용자 식별자
	 * @return 공개 버전에 본인 당첨 결과가 없으면 빈 값
	 */
	Optional<UUID> findWonPrize(UUID publicationId, UUID userId);
}
