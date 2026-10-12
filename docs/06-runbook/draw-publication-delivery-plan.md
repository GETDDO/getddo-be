# 추첨 공개 알림 연결·장애 확인

- 상태: 검토 대기
- 작성: Codex / 2026-10-13
- 관련 작업: [GD-169](https://ureca4.atlassian.net/browse/GD-169)

## 처리와 책임

공개 서비스는 새 공개 버전과 실제 명단·실행별 확인 근거를 저장하고 같은 트랜잭션에서 기존 알림 작업을 등록한다. 최초 공개에는 RESULT_PUBLISHED, 명단 갱신에는 RESULT_CHANGED를 사용한다. 발생 키는 `draw-publication:<publicationId>`이며 payload에 실제 공개 ID와 고정 수신 목록·문구·결과 확인 링크를 보존한다. 재요청은 기존 버전을 반환하고 새 작업을 만들지 않는다.

작업 등록 실패는 아직 커밋되지 않은 공개와 새 확인까지 롤백한다. 커밋 이후 기존 NotificationJobService 및 작업 처리기가 생성·모의 발송을 수행하며, 해당 실패는 공개를 되돌리거나 당첨자를 다시 선정하지 않는다. 운영 DB 적용·스케줄러 연결은 이 문서 검증 범위가 아니다.

## 장애 확인 순서

1. `draw_publications`에서 event_id·revision·id·published_at을 확인한다. 실행 CONFIRMED만으로 공개 완료를 판단하지 않는다.
2. `notification_jobs.occurrence_key`가 `draw-publication:<공개 UUID>`인 행을 조회한다. payload.publicationId와 고정 입력·예약 시각을 대조한다. 원자적으로 등록되는 새 공개에 작업 행이 없으면 구버전 공개 여부와 코드 적용 범위를 확인한다.
3. 작업 status·attempt_count·next_attempt_at·lease_until·last_error를 확인한다. `notifications.job_id`로 사용자별 생성·발송 상태를 확인한다. 새 공개를 만들거나 이력을 삭제하여 재전달하지 않는다.
4. 생성·발송 재시도와 관리자 재개는 [기존 알림 복구 절차](notification-worker.md)를 따른다. payload를 임의 변경하거나 새로운 수신 목록으로 같은 키를 재등록하지 않는다.
5. 관리자 추첨 상세의 최근 실패·예약·운영자 대기와 실패/실행 감사 페이지를 대조한다. 추첨 실패 재개와 알림 작업 재개는 별개다.

## 검증과 실행 제한

실제 MySQL 연결 테스트에서 공개 등록 실패 롤백, 중복 확인·공개 재요청, 다중 실행의 미충원 확인 근거, 생성/모의 발송 및 발송 실패 후 기존 작업 재시도를 검증한다. 2026-10-13 전체 test/build가 통과했다. 단위 816건, API 통합 114건, DB 통합 577건으로 전체 1507건이 성공했고 실패·오류·스킵은 0건이다. GD-169 실제 서비스 연결 클래스는 21건이다. 운영 DB와 실제 외부 발송에는 적용하지 않았다.

Docker 테스트는 공통 잠금과 Gradle 직렬 실행뿐 아니라 Spring 컨텍스트 캐시도 제한한다. 테스트 작업이 하나여도 기본 캐시는 이전 컨텍스트의 MySQL 컨테이너를 보관한다. GD-169 전체 검증 중 이 보관으로 메모리·스왑이 소진되어 해당 테스트를 중단한 뒤, 아래 세션용 init script를 적용해 재검증했다. 다른 작업의 컨테이너를 삭제하거나 일괄 정리하지 않는다.

`/tmp/gd169-test-limits.gradle`:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        maxParallelForks = 1
        systemProperty 'spring.test.context.cache.maxSize', '1'
        systemProperty 'junit.jupiter.execution.parallel.enabled', 'false'
    }
}
```

```bash
flock /tmp/getddo-docker-tests.lock ./gradlew test build \
  --init-script /tmp/gd169-test-limits.gradle --max-workers=1 --no-parallel
```

새 컨텍스트 로딩 중 이전 컨텍스트가 잠시 함께 존재할 수 있으므로 컨테이너 수와 가용 메모리도 확인한다. 같은 테스트 JVM에 MySQL 컨테이너가 계속 누적되는 경우 전체 검증 완료로 보고하지 않고 실행을 중단해 설정과 실제 JVM 인자를 확인한다.

## 이벤트 상태 경계

기존 취소 등록의 REDRAWING 갱신을 재사용한다. 최초 확정·공개 시 상태 변경은 재사용 가능한 기존 함수가 없어 미연결이다. 사용자 결정에 따라 신규 이벤트 상태 변경 코드를 만들지 않았다. 공개 여부는 실제 공개 버전으로 판단하고 이벤트 담당과 후속 연결을 확인한다.
