package com.getddo.core.common.pagination;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CursorPaginationTest {

	@Test
	@DisplayName("항목을 다른 타입으로 변환해도 순서·커서 정보·원본과 결과의 불변성을 유지한다")
	void mapsItemsAndPreservesCursor() {
		// given
		CursorResult<String> source = new CursorResult<>(List.of("first", "next"), "next-cursor", 83);
		Function<CharSequence, Integer> mapper = CharSequence::length;
		// when
		CursorResult<Number> mapped = source.map(mapper);
		// then
		assertThat(mapped.getItems()).containsExactly(5, 4);
		assertThat(mapped.getNextCursor()).isEqualTo("next-cursor");
		assertThat(mapped.getTotalElements()).isEqualTo(83);
		assertThat(mapped.hasNext()).isTrue();
		assertThat(source.getItems()).containsExactly("first", "next");
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> mapped.getItems().add(3));
	}

	@Test
	@DisplayName("빈 마지막 목록은 변환 함수를 호출하지 않고 종료 커서를 유지한다")
	void mapsEmptyLastResultWithoutCallingMapper() {
		// given
		CursorResult<String> source = new CursorResult<>(List.of(), null, 0);
		// when
		CursorResult<Integer> mapped = source.map(item -> {
			throw new AssertionError("빈 목록에서 변환 함수가 호출됨");
		});
		// then
		assertThat(mapped.getItems()).isEmpty();
		assertThat(mapped.getNextCursor()).isNull();
		assertThat(mapped.getTotalElements()).isZero();
		assertThat(mapped.hasNext()).isFalse();
	}

	@Test
	@DisplayName("빈 커서 목록에서도 null 변환 함수를 거절하고 null 변환 결과도 거절한다")
	void rejectsNullMapperAndMappedItem() {
		// given
		CursorResult<String> empty = new CursorResult<>(List.of(), null, 0);
		CursorResult<String> source = new CursorResult<>(List.of("first"), null, 1);
		// when / then
		assertThatNullPointerException().isThrownBy(() -> empty.map(null));
		assertThatNullPointerException().isThrownBy(() -> source.map(item -> null));
	}

	@Test
	void acceptsFirstQueryWithoutCursorAndFollowingQueryWithCursor() {
		CursorQuery first = new CursorQuery(null, 20);
		CursorQuery following = new CursorQuery("saved-cursor", 20);

		assertThat(first.getCursor()).isNull();
		assertThat(first.getSize()).isEqualTo(20);
		assertThat(following.getCursor()).isEqualTo("saved-cursor");
	}

	@Test
	void rejectsBlankCursorAndInvalidSize() {
		assertThatIllegalArgumentException().isThrownBy(() -> new CursorQuery("", 20));
		assertThatIllegalArgumentException().isThrownBy(() -> new CursorQuery("  ", 20));
		assertThatIllegalArgumentException().isThrownBy(() -> new CursorQuery(null, 0));
	}

	@Test
	void keepsNextCursorAndCopiesItems() {
		List<String> source = new ArrayList<>(List.of("first"));
		CursorResult<String> result = new CursorResult<>(source, "next-cursor", 83);

		source.add("second");
		assertThat(result.getItems()).containsExactly("first");
		assertThat(result.getNextCursor()).isEqualTo("next-cursor");
		assertThat(result.getTotalElements()).isEqualTo(83);
		assertThat(result.hasNext()).isTrue();
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> result.getItems().add("third"));
	}

	@Test
	void acceptsLastResultAndRejectsInvalidResult() {
		CursorResult<String> last = new CursorResult<>(List.of(), null, 0);

		assertThat(last.getItems()).isEmpty();
		assertThat(last.hasNext()).isFalse();
		assertThatNullPointerException()
				.isThrownBy(() -> new CursorResult<String>(null, null, 0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new CursorResult<>(List.of("first"), " ", 1));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new CursorResult<>(List.of("first"), null, -1));
	}
}
