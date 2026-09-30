package com.getddo.db.ticket.repository;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * 네이티브 SQL에서 {@code BINARY(16)} UUID 컬럼을 다루기 위한 변환.
 *
 * <p>Hibernate가 Entity의 UUID를 저장하는 형식(상위 64비트 → 하위 64비트, big-endian)과 같다.</p>
 */
final class UuidBinary {

	private static final int UUID_BYTES = 16;

	private UuidBinary() {
	}

	static byte[] toBytes(UUID value) {
		return ByteBuffer.allocate(UUID_BYTES)
				.putLong(value.getMostSignificantBits())
				.putLong(value.getLeastSignificantBits())
				.array();
	}

	static UUID fromBytes(byte[] value) {
		if (value.length != UUID_BYTES) {
			throw new IllegalArgumentException("UUID 컬럼은 16바이트여야 한다.");
		}
		ByteBuffer buffer = ByteBuffer.wrap(value);
		return new UUID(buffer.getLong(), buffer.getLong());
	}
}
