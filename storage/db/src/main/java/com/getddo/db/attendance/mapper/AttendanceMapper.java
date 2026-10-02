package com.getddo.db.attendance.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.getddo.core.attendance.domain.Attendance;
import com.getddo.core.attendance.domain.AttendanceRewardClaim;
import com.getddo.core.attendance.domain.AttendanceStreak;
import com.getddo.db.attendance.entity.AttendanceEntity;
import com.getddo.db.attendance.entity.AttendanceRewardClaimEntity;
import com.getddo.db.attendance.entity.AttendanceStreakEntity;

/** 출석 Entity와 도메인 객체 변환. ID와 생성 시각은 영속화 때 채워지므로 Entity로는 옮기지 않는다. */
@Mapper(componentModel = "spring")
public interface AttendanceMapper {

	AttendanceEntity toEntity(Attendance attendance);

	Attendance toDomain(AttendanceEntity entity);

	AttendanceStreakEntity toEntity(AttendanceStreak streak);

	/** {@code attend(LocalDate)}는 새 현황을 돌려주는 업무 메서드라 속성이 아니다. */
	@Mapping(target = "attend", ignore = true)
	AttendanceStreak toDomain(AttendanceStreakEntity entity);

	@Mapping(target = "attendanceStreakPolicyId", source = "streakPolicyId")
	AttendanceRewardClaimEntity toEntity(AttendanceRewardClaim claim);

	@Mapping(target = "streakPolicyId", source = "attendanceStreakPolicyId")
	AttendanceRewardClaim toDomain(AttendanceRewardClaimEntity entity);
}
