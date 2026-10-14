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

class PaginationTest {

	@Test
	@DisplayName("항목을 다른 타입으로 변환해도 순서·페이지 정보·원본과 결과의 불변성을 유지한다")
	void mapsItemsAndPreservesPagination() {
		// given
		PageResult<String> source = new PageResult<>(List.of("first", "next"), 2, 2, 9);
		Function<CharSequence, Integer> mapper = CharSequence::length;
		// when
		PageResult<Number> mapped = source.map(mapper);
		// then
		assertThat(mapped.getItems()).containsExactly(5, 4);
		assertThat(mapped.getPage()).isEqualTo(2);
		assertThat(mapped.getSize()).isEqualTo(2);
		assertThat(mapped.getTotalElements()).isEqualTo(9);
		assertThat(mapped.totalPages()).isEqualTo(source.totalPages());
		assertThat(mapped.hasNext()).isEqualTo(source.hasNext());
		assertThat(source.getItems()).containsExactly("first", "next");
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> mapped.getItems().add(3));
	}

	@Test
	@DisplayName("빈 페이지는 변환 함수를 호출하지 않고 페이지 정보를 유지한다")
	void mapsEmptyPageWithoutCallingMapper() {
		// given
		PageResult<String> source = new PageResult<>(List.of(), 3, 20, 21);
		// when
		PageResult<Integer> mapped = source.map(item -> {
			throw new AssertionError("빈 목록에서 변환 함수가 호출됨");
		});
		// then
		assertThat(mapped.getItems()).isEmpty();
		assertThat(mapped.getPage()).isEqualTo(3);
		assertThat(mapped.getSize()).isEqualTo(20);
		assertThat(mapped.getTotalElements()).isEqualTo(21);
		assertThat(mapped.hasNext()).isFalse();
	}

	@Test
	@DisplayName("빈 페이지에서도 null 변환 함수를 거절하고 null 변환 결과도 거절한다")
	void rejectsNullMapperAndMappedItem() {
		// given
		PageResult<String> empty = new PageResult<>(List.of(), 1, 20, 0);
		PageResult<String> source = new PageResult<>(List.of("first"), 1, 20, 1);
		// when / then
		assertThatNullPointerException().isThrownBy(() -> empty.map(null));
		assertThatNullPointerException().isThrownBy(() -> source.map(item -> null));
	}

	@Test
	void calculatesOffsetFromOneBasedPageNumber() {
		PageQuery first = new PageQuery(1, 20);
		PageQuery second = new PageQuery(2, 20);

		assertThat(first.getPage()).isEqualTo(1);
		assertThat(first.getSize()).isEqualTo(20);
		assertThat(first.offset()).isZero();
		assertThat(second.offset()).isEqualTo(20);
		assertThat(new PageQuery(Integer.MAX_VALUE, Integer.MAX_VALUE).offset())
				.isGreaterThan(Integer.MAX_VALUE);
	}

	@Test
	void rejectsInvalidPageAndSize() {
		assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(0, 20));
		assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(1, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(1, -1));
	}

	@Test
	void calculatesTotalPagesAndNextPageFromOverallCount() {
		PageResult<String> first = new PageResult<>(List.of("first"), 1, 20, 83);
		PageResult<String> last = new PageResult<>(List.of("last"), 5, 20, 83);
		PageResult<String> empty = new PageResult<>(List.of(), 1, 20, 0);

		assertThat(first.getTotalElements()).isEqualTo(83);
		assertThat(first.totalPages()).isEqualTo(5);
		assertThat(first.hasNext()).isTrue();
		assertThat(last.hasNext()).isFalse();
		assertThat(empty.totalPages()).isZero();
		assertThat(empty.hasNext()).isFalse();
	}

	@Test
	void copiesItemsSoResultsCannotChangeAfterCreation() {
		List<String> source = new ArrayList<>(List.of("first"));
		PageResult<String> result = new PageResult<>(source, 1, 20, 1);

		source.add("second");
		assertThat(result.getItems()).containsExactly("first");
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> result.getItems().add("third"));
	}

	@Test
	void rejectsInvalidResults() {
		assertThatNullPointerException()
				.isThrownBy(() -> new PageResult<String>(null, 1, 20, 0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PageResult<>(List.of("first"), 0, 20, 1));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PageResult<>(List.of("first"), 1, 0, 1));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PageResult<>(List.of("first"), 1, 20, -1));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PageResult<>(List.of("first", "second"), 1, 1, 2));
	}
}
