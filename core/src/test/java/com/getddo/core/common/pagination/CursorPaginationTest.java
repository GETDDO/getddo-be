package com.getddo.core.common.pagination;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CursorPaginationTest {

	@Test
	void acceptsFirstQueryWithoutCursorAndFollowingQueryWithCursor() {
		CursorQuery first = new CursorQuery(null, 20);
		CursorQuery following = new CursorQuery("saved-cursor", 20);

		assertThat(first.cursor()).isNull();
		assertThat(first.size()).isEqualTo(20);
		assertThat(following.cursor()).isEqualTo("saved-cursor");
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
		assertThat(result.items()).containsExactly("first");
		assertThat(result.nextCursor()).isEqualTo("next-cursor");
		assertThat(result.totalElements()).isEqualTo(83);
		assertThat(result.hasNext()).isTrue();
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> result.items().add("third"));
	}

	@Test
	void acceptsLastResultAndRejectsInvalidResult() {
		CursorResult<String> last = new CursorResult<>(List.of(), null, 0);

		assertThat(last.items()).isEmpty();
		assertThat(last.hasNext()).isFalse();
		assertThatNullPointerException()
				.isThrownBy(() -> new CursorResult<String>(null, null, 0));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new CursorResult<>(List.of("first"), " ", 1));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new CursorResult<>(List.of("first"), null, -1));
	}
}
