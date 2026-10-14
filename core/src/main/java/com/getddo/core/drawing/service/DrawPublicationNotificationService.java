package com.getddo.core.drawing.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublicationType;
import com.getddo.core.drawing.repository.DrawPublicationNotificationRepository;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.service.NotificationJobService;

import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.INVALID_PUBLICATION;

/** 실제 공개된 명단에서 알림 대상자를 정하고 기존 알림 서비스에 작업을 등록한다. 직접 생성·발송하지 않는다. */
@Service
@RequiredArgsConstructor
public class DrawPublicationNotificationService {
	private final DrawPublicationRepository publications;
	private final DrawPublicationNotificationRepository recipients;
	private final NotificationJobService jobs;

	/** 새로운 버전에만 호출하며 커밋 뒤 기존 처리기가 내구성 있는 작업을 조회한다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void register(DrawPublication publication) {
		boolean initial = publication.getType() == DrawPublicationType.INITIAL;
		List<UUID> users = initial ? recipients.findParticipants(publication.getEventId()) : changedUsers(publication);
		jobs.register(new NotificationJobRequest("draw-publication:" + publication.getId(),
				initial ? NotificationType.RESULT_PUBLISHED : NotificationType.RESULT_CHANGED,
				publication.getEventId(), publication.getId(), initial ? "추첨 결과 발표" : "추첨 결과 변경",
				initial ? "응모한 이벤트의 추첨 결과가 발표되었습니다." : "응모한 이벤트의 당첨 결과가 변경되었습니다.",
				"/events/" + publication.getEventId() + "/results", publication.getPublishedAt(), users));
	}

	/** 실제 직전 공개와 비교해 교체된 자리의 이전·새 사용자만 중복 없이 반환한다. */
	private List<UUID> changedUsers(DrawPublication publication) {
		DrawPublication previous = publications.findPrevious(publication.getEventId(), publication.getRevision())
				.orElseThrow(() -> new BusinessException(INVALID_PUBLICATION));
		Map<Slot, DrawPublication.Slot> before = index(publications.findPublishedSlots(previous.getId()));
		Map<Slot, DrawPublication.Slot> after = index(publications.findPublishedSlots(publication.getId()));
		Set<Slot> slots = new LinkedHashSet<>(before.keySet());
		slots.addAll(after.keySet());
		Set<UUID> changed = new LinkedHashSet<>();
		for (Slot slot : slots) {
			DrawPublication.Slot old = before.get(slot), updated = after.get(slot);
			if (java.util.Objects.equals(old, updated)) continue;
			if (old != null) changed.add(old.getUserId());
			if (updated != null) changed.add(updated.getUserId());
		}
		return List.copyOf(changed);
	}

	/** 경품과 자리 번호로 명단을 대응시켜 유지 당첨자를 변경 대상에서 제외한다. */
	private static Map<Slot, DrawPublication.Slot> index(List<DrawPublication.Slot> rows) {
		Map<Slot, DrawPublication.Slot> indexed = new LinkedHashMap<>();
		for (DrawPublication.Slot row : rows) indexed.put(new Slot(row.getPrizeId(), row.getSlotNumber()), row);
		return indexed;
	}
	private record Slot(UUID prizeId, int number) {
	}
}
