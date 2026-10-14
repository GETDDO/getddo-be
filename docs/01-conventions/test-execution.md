# 테스트 실행 자원 제한

Docker가 필요한 전체 검증은 서로 다른 Spring 컨텍스트의 MySQL 컨테이너를 실행한다. 로컬 메모리가 제한된 환경에서는 저장소의 init script로 테스트 fork 1개, Spring 컨텍스트 캐시 1개, JUnit 병렬 실행 비활성을 적용할 수 있다. 테스트 범위와 검증 조건은 그대로 유지한다.

```bash
./gradlew test build --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
```

동일 호스트에서 여러 워크트리를 검증할 경우 참여 작업들이 같은 잠금을 사용한다.

```bash
flock /tmp/getddo-docker-tests.lock ./gradlew test build \
  --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
```

새 컨텍스트 로딩 중 이전 컨텍스트가 잠시 함께 존재할 수 있으므로 컨테이너 수와 가용 메모리도 확인한다. 다른 작업의 컨테이너나 프로세스를 임의 정리하지 않는다. Docker 연결·JDK 준비·통합 테스트 범위는 [루트 README](../../README.md#테스트와-ci)를 따른다.
