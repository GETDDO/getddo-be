package com.getddo.core.drawing.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Objects;

/** 운영 추첨용 보안 난수 공급자다. 범위 밖의 비트 패턴은 다시 뽑는다. */
final class SecureDrawRandom implements DrawRandom {

	private final SecureRandom random = new SecureRandom();

	@Override
	public BigInteger nextBelow(BigInteger upperExclusive) {
		Objects.requireNonNull(upperExclusive, "upperExclusive");
		if (upperExclusive.signum() <= 0) {
			throw new IllegalArgumentException("upperExclusive must be positive");
		}

		BigInteger value;
		do {
			value = new BigInteger(upperExclusive.bitLength(), random);
		} while (value.compareTo(upperExclusive) >= 0);
		return value;
	}
}
