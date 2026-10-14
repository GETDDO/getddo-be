package com.getddo.core.game.domain;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorWordRoundGeneratorTest {

	@Test
	@DisplayName("채택 계약의 공유 CSV와 같은 문제열을 재현한다")
	void matchesSharedContractVector() throws Exception {
		// given
		ColorWordRoundGenerator generator = new ColorWordRoundGenerator(123456789);
		InputStream resource = getClass().getResourceAsStream("/game/color-word-generator-v1.csv");
		assertThat(resource).as("공유 문제열 CSV 리소스").isNotNull();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
			// when / then
			assertThat(reader.readLine()).isEqualTo("seed,sequence,word,ink");
			List<String> lines = reader.lines().toList();
			assertThat(lines).as("채택 계약의 공유 문제열").hasSizeGreaterThanOrEqualTo(4);
			for (int index = 0; index < lines.size(); index++) {
				String[] fields = lines.get(index).split(",", -1);
				assertThat(fields).hasSize(4);
				assertThat(Long.parseLong(fields[0])).isEqualTo(123456789L);
				assertThat(Integer.parseInt(fields[1])).isEqualTo(index + 1);
				ColorWordRound round = generator.next();
				assertThat(round.getWord().name().toLowerCase(Locale.ROOT)).isEqualTo(fields[2]);
				assertThat(round.getInk().name().toLowerCase(Locale.ROOT)).isEqualTo(fields[3]);
			}
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {1, 123456789, 2147483648L, 4294967295L})
	@DisplayName("새 플레이는 이전 인스턴스와 독립적이고 같은 unsigned seed의 문제열이 같다")
	void sameSeedStartsAnIndependentSequence(long seed) {
		// given
		ColorWordRoundGenerator first = new ColorWordRoundGenerator(seed);
		ColorWordRoundGenerator second = new ColorWordRoundGenerator(seed);
		ColorWordRound previous = null;
		// when / then
		for (int index = 0; index < 100; index++) {
			ColorWordRound left = first.next();
			ColorWordRound right = second.next();
			assertThat(left.getWord()).isEqualTo(right.getWord());
			assertThat(left.getInk()).isEqualTo(right.getInk());
			if (previous != null) {
				assertThat(left.getWord() != previous.getWord() || left.getInk() != previous.getInk()).isTrue();
			}
			previous = left;
		}
	}

	@Test
	@DisplayName("동일 문제 10회 재추출 뒤 FE와 같은 대체색을 고르고 추가 난수를 쓰지 않는다")
	void fallsBackAfterTenIdenticalDraws() {
		// given
		AtomicInteger draws = new AtomicInteger();
		ColorWordRoundGenerator generator = new ColorWordRoundGenerator(() -> {
			draws.incrementAndGet();
			return 0;
		});
		// when
		ColorWordRound first = generator.next();
		ColorWordRound fallback = generator.next();
		// then
		assertThat(first.getWord()).isEqualTo(ColorWordColor.RED);
		assertThat(first.getInk()).isEqualTo(ColorWordColor.BLUE);
		assertThat(fallback.getWord()).isEqualTo(ColorWordColor.RED);
		assertThat(fallback.getInk()).isEqualTo(ColorWordColor.RED);
		assertThat(draws.get()).isEqualTo(33);
	}

	@Test
	@DisplayName("일치 분기에서는 선택지 추출 난수를 소비하지 않는다")
	void matchingBranchConsumesOnlyWordAndBranchDraws() {
		// given
		AtomicInteger draws = new AtomicInteger();
		ColorWordRoundGenerator generator = new ColorWordRoundGenerator(() -> {
			draws.incrementAndGet();
			return 0.9;
		});
		// when
		ColorWordRound first = generator.next();
		ColorWordRound fallback = generator.next();
		// then
		assertThat(first.getWord()).isEqualTo(ColorWordColor.GREEN);
		assertThat(first.getInk()).isEqualTo(ColorWordColor.GREEN);
		assertThat(fallback.getWord()).isEqualTo(ColorWordColor.GREEN);
		assertThat(fallback.getInk()).isEqualTo(ColorWordColor.RED);
		assertThat(draws.get()).isEqualTo(22);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1, 4294967296L, Long.MAX_VALUE})
	@DisplayName("0과 unsigned 32-bit 범위 밖의 seed는 거절한다")
	void rejectsInvalidSeed(long seed) {
		// given / when / then
		assertThatThrownBy(() -> new ColorWordRoundGenerator(seed)).isInstanceOf(IllegalArgumentException.class);
	}
}
