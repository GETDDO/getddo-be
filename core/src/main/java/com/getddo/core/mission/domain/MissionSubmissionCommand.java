package com.getddo.core.mission.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import lombok.Getter;

/**
 * 미션 제출 요청.
 *
 * <p>제출 ID는 서버가 만들지 않고 클라이언트가 요청 전에 정한 UUID({@code Idempotency-Key})다. 같은 제출의 재시도는 같은 값을
 * 쓴다. 값의 유효성은 {@code MissionSubmissionService}가 검증한다.</p>
 *
 * <p>같은 제출 ID로 온 재전송이 원래 요청과 같은 본문인지 판정하려고 요청 해시를 함께 계산한다. 저장된 답변에는 이미 정답으로 완료해
 * 저장하지 않은 문항의 답변이 빠져 있어 저장된 답변과 비교하면 원래 요청을 알 수 없기 때문이다.</p>
 */
@Getter
public final class MissionSubmissionCommand {

	private final UUID userId;
	private final UUID missionId;
	private final UUID submissionId;
	private final List<MissionAnswerInput> answers;
	/** 사용자·미션·답변 구성의 SHA-256(64자리 16진수). 답변 목록이 없으면 null. 제출 ID와 답변 순서는 반영하지 않는다. */
	private final String requestHash;

	public MissionSubmissionCommand(UUID userId, UUID missionId, UUID submissionId, List<MissionAnswerInput> answers) {
		this.userId = userId;
		this.missionId = missionId;
		this.submissionId = submissionId;
		this.answers = answers == null ? null : List.copyOf(answers);
		this.requestHash = this.answers == null ? null : hash(userId, missionId, this.answers);
	}

	/**
	 * 요청 구성 문자열의 SHA-256을 구한다. 단답형 채점의 정규화(공백·대소문자·유니코드 조합 차이를 무시)와는 별개의 규칙이다.
	 * 답변은 문항 ID 순으로 정렬하고, 선택지가 없거나 입력 문자열이 {@code null}이거나 비었거나 공백뿐이면 {@code -}로 적는다.
	 * 그 밖의 입력 문자열은 원문 그대로 길이와 함께 포함하므로 {@code "겟또"}와 {@code "겟또 "}는 다른 요청이다(채점에서는 둘 다
	 * 같은 답이다). 길이를 앞에 붙여 다른 답변 구성과 경계가 섞이지 않게 한다.
	 */
	private static String hash(UUID userId, UUID missionId, List<MissionAnswerInput> answers) {
		StringBuilder canonical = new StringBuilder("mission=").append(missionId).append("|user=").append(userId);
		answers.stream()
				.sorted(Comparator.comparing(MissionAnswerInput::getQuestionId))
				.forEach(answer -> {
					String text = answer.getAnswerText() == null || answer.getAnswerText().isBlank() ? null
							: answer.getAnswerText();
					canonical.append("|q=").append(answer.getQuestionId())
							.append(",o=").append(answer.getSelectedOptionId() == null ? "-" : answer.getSelectedOptionId())
							.append(",t=").append(text == null ? "-" : text.length() + ":" + text);
				});
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			// 모든 자바 구현이 SHA-256을 제공한다.
			throw new IllegalStateException(e);
		}
	}
}
