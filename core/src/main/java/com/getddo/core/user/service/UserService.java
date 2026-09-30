package com.getddo.core.user.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.exception.UserErrorCode;
import com.getddo.core.user.exception.UserException;
import com.getddo.core.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	/** 확인된 활성 사용자의 내 정보를 반환한다. 상태 제한은 U01에만 적용하며 재조회하지 않는다. */
	public User getProfile(User currentUser) {
		if (currentUser.status() == UserStatus.INACTIVE) {
			throw new UserException(UserErrorCode.USER_INACTIVE);
		}
		return currentUser;
	}

	/**
	 * 필수 ID로 사용자를 조회한다. 상태와 nullable 필드는 저장된 값을 보존한다.
	 *
	 * @throws UserException ID가 null이거나 등록된 사용자가 없는 경우
	 */
	@Transactional(readOnly = true)
	public User findById(UUID userId) {
		if (userId == null) {
			throw new UserException(UserErrorCode.USER_ID_REQUIRED);
		}
		return userRepository.findById(userId)
				.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
	}
}
