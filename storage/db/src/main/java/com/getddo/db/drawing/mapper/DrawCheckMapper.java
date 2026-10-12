package com.getddo.db.drawing.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import com.getddo.core.drawing.domain.DrawCheckItem;
import com.getddo.core.drawing.domain.DrawVerification;
import com.getddo.db.drawing.entity.DrawCheckEntity;

/** 검증 버전과 항목별 결과를 JSON에 보존한다. 예외 원문은 포함하지 않는다. */
@Component
public class DrawCheckMapper {
	private final JsonMapper json = JsonMapper.builder().build();
	public String snapshot(List<DrawCheckItem> items) {
		return json.writeValueAsString(new Snapshot(1, items.stream()
				.map(item -> new Item(item.getCode(), item.isPassed(), item.getMessage())).toList()));
	}
	public DrawVerification toDomain(DrawCheckEntity entity) {
		Snapshot stored = json.readValue(entity.getChecksSnapshot(), Snapshot.class);
		if (stored.version() != 1) throw new IllegalStateException("지원하지 않는 검증 이력 버전입니다.");
		return new DrawVerification(entity.getId(), entity.getDrawRun().getId(), entity.isPassed(),
				stored.checks().stream().map(item -> new DrawCheckItem(item.code(), item.passed(), item.message())).toList(),
				entity.getCheckedAt(), entity.getCheckedBy());
	}
	private record Snapshot(int version, List<Item> checks) { }
	private record Item(String code, boolean passed, String message) { }
}
