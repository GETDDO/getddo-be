package com.getddo.core.ticket.domain;

/**
 * 응모권 지급의 근거가 되는 청구의 종류.
 *
 * <p>종류마다 원장({@code ticket_ledger})에서 채우는 청구 참조 컬럼이 다르다.
 * MISSION은 {@code mission_reward_claim_id}, ATTENDANCE는 {@code attendance_reward_claim_id},
 * GAME은 {@code game_reward_claim_id}를 채운다.</p>
 */
public enum GrantSourceType {
	MISSION,
	ATTENDANCE,
	GAME
}
