package com.getddo.core.drawing.service;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SecureDrawRandomTest {

	@Test
	void returnsValuesInsideRequestedRange() {
		SecureDrawRandom random = new SecureDrawRandom();
		BigInteger bound = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);

		for (int attempt = 0; attempt < 100; attempt++) {
			BigInteger value = random.nextBelow(bound);
			assertThat(value).isGreaterThanOrEqualTo(BigInteger.ZERO).isLessThan(bound);
		}
		assertThat(random.nextBelow(BigInteger.ONE)).isZero();
	}

	@Test
	void rejectsEmptyRange() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SecureDrawRandom().nextBelow(BigInteger.ZERO));
	}
}
