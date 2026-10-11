package com.getddo.db.drawing.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.DrawResult;
import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSelectionResultType;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.db.drawing.entity.DrawResultEntity;

import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;

/** 변경 가능한 현재 경품 정보 대신 저장된 규칙·후보에서 결과를 복원한다. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DrawResultMapper {
	/**
	 * 결과 행을 확정 당시 규칙·후보와 결합한다. 현재 경품 정보 대신 스냅샷 등수·사용자를 사용한다.
	 * DB 조회나 현재 자격 판단 없이 주어진 불변 입력에서 필수 변환 값을 찾는다.
	 *
	 * @param entity 저장된 선정·미충원 결과 행
	 * @param snapshot 확정 당시의 불변 후보·경품 입력
	 * @return 확정 당시 등수·사용자와 저장된 식별자·시각을 보존하는 결과
	 * @throws BusinessException 결과의 경품 또는 선정 후보가 주어진 스냅샷에 없어 복원할 수 없는 경우
	 */
	default DrawResult toDomain(DrawResultEntity entity, DrawSnapshot snapshot) {
		int rank = findPrizeRank(snapshot, entity.getPrizeId());
		UUID userId = null;
		if (entity.getResultType() == DrawSelectionResultType.SELECTED) {
			userId = findCandidateUserId(snapshot, entity.getCandidateId());
		}
		DrawSelection selection = toSelection(entity, rank, userId);
		return toResult(entity, selection);
	}

	/** 저장 필드와 스냅샷에서 복원한 등수·사용자를 선정 모델의 생성자에 매핑한다. */
	@Mapping(target = "prizeId", source = "entity.prizeId")
	@Mapping(target = "rank", source = "rank")
	@Mapping(target = "slotNumber", source = "entity.slotNumber")
	@Mapping(target = "selectionOrder", source = "entity.selectionOrder")
	@Mapping(target = "candidateId", source = "entity.candidateId")
	@Mapping(target = "userId", source = "userId")
	@Mapping(target = "resultType", source = "entity.resultType")
	DrawSelection toSelection(DrawResultEntity entity, int rank, UUID userId);

	/** DB에서 생성한 식별자·시각과 복원된 선정 내용을 불변 결과 모델로 매핑한다. */
	@Mapping(target = "id", source = "entity.id")
	@Mapping(target = "selection", source = "selection")
	@Mapping(target = "createdAt", source = "entity.createdAt")
	DrawResult toResult(DrawResultEntity entity, DrawSelection selection);

	/** 확정 당시 경품 등수를 찾으며 스냅샷에 없는 경품은 잘못된 실행으로 거절한다. */
	private static int findPrizeRank(DrawSnapshot snapshot, UUID prizeId) {
		for (DrawSnapshotSource.Prize prize : snapshot.rules().prizes()) {
			if (prize.id().equals(prizeId)) {
				return prize.rank();
			}
		}
		throw new BusinessException(INVALID_RUN);
	}

	/** 확정 당시 후보의 사용자를 찾으며 스냅샷에 없는 후보는 잘못된 실행으로 거절한다. */
	private static UUID findCandidateUserId(DrawSnapshot snapshot, UUID candidateId) {
		for (DrawSnapshot.Candidate candidate : snapshot.candidates()) {
			if (candidate.id().equals(candidateId)) {
				return candidate.userId();
			}
		}
		throw new BusinessException(INVALID_RUN);
	}
}
