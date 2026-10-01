package com.getddo.db.attendance.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.db.common.entity.BaseEntity;

/** 출석 기록 Entity. 수정하지 않는다. 사용자 참조는 다른 도메인 Entity와 연관관계 없이 UUID로 둔다. */
@Getter
@Entity
@Table(name = "attendances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AttendanceEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	/** 출석 기준 KST 날짜. */
	@Column(name = "attendance_date", nullable = false, updatable = false)
	private LocalDate attendanceDate;
}
