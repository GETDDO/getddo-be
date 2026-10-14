# 테스트 실행 자원 제한

Docker가 필요한 전체 검증은 서로 다른 Spring 컨텍스트의 MySQL 컨테이너를 실행한다. 로컬 메모리가 제한된 환경에서는 저장소의 init script로 테스트 fork 1개, Spring 컨텍스트 캐시 2개, JUnit 병렬 실행 비활성을 적용할 수 있다. 테스트 범위와 검증 조건은 그대로 유지한다.

```bash
./gradlew test build --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
```

캐시를 2개 보관해 서로 다른 설정을 오가는 테스트에서 기존 컨텍스트를 재사용할 기회를 늘린다. 설정이 같은 테스트만 컨텍스트를 공유하며, `@DirtiesContext`로 폐기한 컨텍스트는 재사용하지 않는다. 메모리 사용량은 캐시 1개일 때보다 늘어날 수 있고 실제 시간 절감치는 동일 환경에서 측정해야 한다.

메모리가 부족하면 Spring의 시스템 속성으로 캐시를 1개로 제한한다. 이 속성은 init script가 테스트 JVM에 전달한다.

```bash
./gradlew test build --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel \
  -Dspring.test.context.cache.maxSize=1
```

이 설정은 해당 init script를 지정한 실행에만 적용된다. 현재 GitHub Actions의 `빌드·테스트`는 init script 없이 `./gradlew build --no-daemon --stacktrace`를 실행하므로 이 캐시 조정의 직접 적용 대상이 아니다.

동일 호스트에서 여러 워크트리를 검증할 경우 참여 작업들이 같은 잠금을 사용한다.

```bash
flock /tmp/getddo-docker-tests.lock ./gradlew test build \
  --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
```

새 컨텍스트 로딩 중 이전 컨텍스트가 잠시 함께 존재할 수 있으므로 컨테이너 수와 가용 메모리도 확인한다. 다른 작업의 컨테이너나 프로세스를 임의 정리하지 않는다. Docker 연결·JDK 준비·통합 테스트 범위는 [루트 README](../../README.md#테스트와-ci)를 따른다.
