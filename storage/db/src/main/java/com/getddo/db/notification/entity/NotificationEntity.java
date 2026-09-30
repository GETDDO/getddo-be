package com.getddo.db.notification.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.db.common.entity.BaseEntity;

/**
 * 기존 {@code notifications} 행의 조회·읽음 처리용 부분 매핑이다.
 *
 * <p>식별자·생성 시각은 채택된 공통 {@link BaseEntity} 매핑을 사용한다.
 * 알림 생성에 필요한 전체 컬럼을 매핑한 객체는 아니므로 신규 저장에 사용하지 않는다.
 * 읽음 갱신은 저장소의 조건부 벌크 UPDATE가 수행한다.</p>
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEntity extends BaseEntity {
	@Column(name = "user_id", nullable = false, length = 16)
	private UUID userId;

	@Column(name = "event_id", length = 16)
	private UUID eventId;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "body", nullable = false, columnDefinition = "text")
	private String body;

	@Column(name = "link_url", length = 500)
	private String linkUrl;

	@Column(name = "is_read", nullable = false)
	private boolean isRead;

}
