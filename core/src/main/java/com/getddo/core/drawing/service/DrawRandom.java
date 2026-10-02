package com.getddo.core.drawing.service;

import java.math.BigInteger;

/** 0 이상 upperExclusive 미만의 정수를 편향 없이 뽑는 난수 공급자다. */
@FunctionalInterface
public interface DrawRandom {

	BigInteger nextBelow(BigInteger upperExclusive);
}
