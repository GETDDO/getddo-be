package com.getddo.core.common.pagination;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PaginationTest {

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
