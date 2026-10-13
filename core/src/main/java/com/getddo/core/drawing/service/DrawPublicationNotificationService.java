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
import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.INVALID_PUBLICATION;
import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublicationType;
import com.getddo.core.drawing.domain.DrawPublishedSlot;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.drawing.repository.DrawPublicationNotificationRepository;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.service.NotificationJobService;

/** 공개와 같은 트랜잭션에서 기존 알림 작업을 등록한다. 생성·발송·재시도는 기존 처리기가 맡는다. */
@Service
@RequiredArgsConstructor
public class DrawPublicationNotificationService {
	private final DrawPublicationRepository publications;
	private final DrawPublicationNotificationRepository recipients;
	private final NotificationJobService jobs;

	/** 새로운 버전에만 호출하며 커밋 뒤 기존 처리기가 내구성 있는 작업을 조회한다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void register(DrawPublication publication) {
		boolean initial = publication.type() == DrawPublicationType.INITIAL;
		List<UUID> users = initial ? recipients.findParticipants(publication.eventId()) : changedUsers(publication);
		jobs.register(new NotificationJobRequest("draw-publication:" + publication.id(),
				initial ? NotificationType.RESULT_PUBLISHED : NotificationType.RESULT_CHANGED,
				publication.eventId(), publication.id(), initial ? "추첨 결과 발표" : "추첨 결과 변경",
				initial ? "응모한 이벤트의 추첨 결과가 발표되었습니다." : "응모한 이벤트의 당첨 결과가 변경되었습니다.",
				"/events/" + publication.eventId() + "/results", publication.publishedAt(), users));
	}

	/** 실제 직전 공개와 비교해 교체된 자리의 이전·새 사용자만 중복 없이 반환한다. */
	private List<UUID> changedUsers(DrawPublication publication) {
		DrawPublication previous = publications.findPrevious(publication.eventId(), publication.revision())
				.orElseThrow(() -> new BusinessException(INVALID_PUBLICATION));
		Map<Slot, DrawPublishedSlot> before = index(publications.findPublishedSlots(previous.id()));
		Map<Slot, DrawPublishedSlot> after = index(publications.findPublishedSlots(publication.id()));
		Set<Slot> slots = new LinkedHashSet<>(before.keySet());
		slots.addAll(after.keySet());
		Set<UUID> changed = new LinkedHashSet<>();
		for (Slot slot : slots) {
			DrawPublishedSlot old = before.get(slot), updated = after.get(slot);
			if (java.util.Objects.equals(old, updated)) continue;
			if (old != null) changed.add(old.userId());
			if (updated != null) changed.add(updated.userId());
		}
		return List.copyOf(changed);
	}

	/** 경품과 자리 번호로 명단을 대응시켜 유지 당첨자를 변경 대상에서 제외한다. */
	private static Map<Slot, DrawPublishedSlot> index(List<DrawPublishedSlot> rows) {
		Map<Slot, DrawPublishedSlot> indexed = new LinkedHashMap<>();
		for (DrawPublishedSlot row : rows) indexed.put(new Slot(row.prizeId(), row.slotNumber()), row);
		return indexed;
	}
	private record Slot(UUID prizeId, int number) { }
}
