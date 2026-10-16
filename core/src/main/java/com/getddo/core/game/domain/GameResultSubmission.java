package com.getddo.core.game.domain;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** JSON 표현 대신 순서·색·정수 시간을 고정 길이로 해시해 동일 입력 재시도를 식별한다. */
@Getter
public final class GameResultSubmission {
	private final UUID gameId;
	private final UUID playId;
	private final String playToken;
	private final List<ColorWordAnswer> answers;
	private final String resultHash;

	public GameResultSubmission(UUID gameId, UUID playId, String playToken, List<ColorWordAnswer> answers) {
		this.gameId = Objects.requireNonNull(gameId, "gameId");
		this.playId = Objects.requireNonNull(playId, "playId");
		this.playToken = Objects.requireNonNull(playToken, "playToken");
		this.answers = List.copyOf(answers);
		this.resultHash = hash(this.answers);
	}

	private static String hash(List<ColorWordAnswer> answers) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (ColorWordAnswer answer : answers) {
				digest.update(ByteBuffer.allocate(16).putInt(answer.getSequence())
						.putInt(answer.getAnswer() == null ? -1 : answer.getAnswer().ordinal())
						.putLong(answer.getElapsedMs()).array());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", failure);
		}
	}
}
