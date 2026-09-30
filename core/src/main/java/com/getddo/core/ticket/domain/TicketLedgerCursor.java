package com.getddo.core.ticket.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.ticket.exception.TicketErrorCode;

/**
 * 응모권 이력 커서. 마지막으로 받은 이력의 정렬 키 {@code (createdAt, id)}다.
 *
 * <p>이력은 {@code createdAt DESC, id DESC}로 고정 정렬하며, 다음 조회는 이 키보다 뒤의 행부터 가져온다.
 * 클라이언트에는 내용을 해석할 수 없는 문자열로 전달한다.</p>
 */
public final class TicketLedgerCursor {

	private static final String SEPARATOR = "|";

	private final Instant createdAt;
	private final UUID id;

	public TicketLedgerCursor(Instant createdAt, UUID id) {
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.id = Objects.requireNonNull(id, "id");
	}

	/**
	 * 클라이언트가 보낸 커서 문자열을 해석한다.
	 *
	 * @param encoded {@link #encode()}로 만든 문자열
	 * @return 커서
	 * @throws BusinessException 형식이 올바르지 않은 경우 {@code TICKET_INVALID_LEDGER_QUERY}
	 */
	public static TicketLedgerCursor decode(String encoded) {
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			int separator = decoded.indexOf(SEPARATOR);
			if (separator < 0) {
				throw invalid();
			}
			return new TicketLedgerCursor(
					Instant.parse(decoded.substring(0, separator)),
					UUID.fromString(decoded.substring(separator + 1)));
		} catch (IllegalArgumentException | DateTimeParseException e) {
			throw invalid();
		}
	}

	/** 클라이언트에 전달할 문자열로 만든다. */
	public String encode() {
		String raw = createdAt + SEPARATOR + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public UUID getId() {
		return id;
	}

	private static BusinessException invalid() {
		return new BusinessException(TicketErrorCode.TICKET_INVALID_LEDGER_QUERY);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TicketLedgerCursor that)) {
			return false;
		}
		return createdAt.equals(that.createdAt) && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(createdAt, id);
	}

	@Override
	public String toString() {
		return "TicketLedgerCursor[createdAt=" + createdAt + ", id=" + id + "]";
	}
}
