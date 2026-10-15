package com.getddo.core.game.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 플레이 발급 값. 토큰 원문은 시작 응답에만 사용하고 저장에는 해시를 전달한다. */
@Getter
public final class GamePlayCredentials {

	private final String playToken;
	private final String playTokenHash;
	private final long generationSeed;

	private GamePlayCredentials(String playToken, String playTokenHash, long generationSeed) {
		this.playToken = playToken;
		this.playTokenHash = playTokenHash;
		this.generationSeed = generationSeed;
	}

	/** 256-bit 불투명 토큰과 xorshift32용 0이 아닌 unsigned seed를 각각 발급한다. */
	public static GamePlayCredentials issue(SecureRandom random) {
		Objects.requireNonNull(random, "random");
		byte[] tokenBytes = new byte[32];
		random.nextBytes(tokenBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		long seed;
		do {
			seed = Integer.toUnsignedLong(random.nextInt());
		} while (seed == 0);
		return new GamePlayCredentials(token, sha256(token), seed);
	}

	/**
	 * 신뢰된 사용자·게임 ID와 저장된 플레이의 토큰 연결을 확인한다. 사용자 신원 확인을 대신하지 않는다.
	 * 공개 오류 변환과 만료 기준은 결과 API 계약에서 적용한다.
	 */
	public static boolean matches(GamePlay play, UUID userId, UUID gameId, String token) {
		if (play == null || userId == null || gameId == null || token == null
				|| play.getId() == null || play.getStartedAt() == null
				|| !play.getUserId().equals(userId) || !play.getGameId().equals(gameId)
				|| !play.hasVerificationContext() || token.length() != 43 || !token.matches("[A-Za-z0-9_-]{43}")) {
			return false;
		}
		return MessageDigest.isEqual(play.getPlayTokenHash().getBytes(StandardCharsets.US_ASCII),
				sha256(token).getBytes(StandardCharsets.US_ASCII));
	}

	private static String sha256(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			// SHA-256은 Java 플랫폼 필수 알고리즘이다. 발급 실패 시 토큰을 반환하지 않는다.
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}
}
