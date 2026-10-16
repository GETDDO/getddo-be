# Redis 연결 설정과 검증

- 상태: 검토 대기 · Codex 작성 · 담당자 확인 전
- 기준일: 2026-10-16
- 관련 이슈: GD-111, GD-112, GD-208
- 범위: API 한 대의 Redis 설정 조회·전달 준비. Redis 인프라 Terraform·IAM과 요청 제한 구현 코드는 이번 PR에 포함하지 않는다.

## 현재 확인한 상태

사용자가 Terraform apply를 수행한 뒤 AWS 조회에서 `getddo-dev-redis`의 available 상태, 원본 1대, AUTH 인증, 필수 TLS와 저장 암호화를 확인했다. 사용자는 기존 API EC2에서 redis6-cli로 PONG 응답을 확인했다고 전달했다. 이 결과는 앱의 Redis 연동이나 API 두 대의 공유 카운터 검증 완료를 뜻하지 않는다.

| 연결 항목 | 값 |
| --- | --- |
| 엔진·사양 | ElastiCache Redis OSS 7.1, cache.t4g.micro |
| 노드 | 원본 1대·복제본 0개. 자동 장애 전환 없음 |
| 호스트 | master.getddo-dev-redis.lkiced.apn2.cache.amazonaws.com |
| 포트 | 6379 |
| TLS | 필수, 클라이언트에서 서버 인증서 검증 유지 |
| 인증 | Redis 생성 때 사용한 전용 비밀번호. 문서·Git에 원문을 기록하지 않음 |

## 변경 전·후와 확인할 파일

| 파일 | 변경 전 | 변경 후 |
| --- | --- | --- |
| [fetch-app-env.py](../../infra/scripts/fetch-app-env.py) | 기존 설정 9개를 한 번 조회 | Redis 4개를 포함한 13개를 10개·3개로 조회하고 합쳐 검증 |
| 같은 파일의 validate | DB 비밀번호 타입과 기존 설정 검사 | Redis 비밀번호도 SecureString 요구. ElastiCache DNS·6379·TLS true·비밀번호 형식 검사 추가 |
| [application.yaml](../../api/src/main/resources/application.yaml) | Redis 설정 없음 | spring.data.redis에 호스트·포트·비밀번호·TLS 환경변수 참조 추가 |
| [Parameter Store 안내](parameter-store.md#2026-10-16-redis-설정-추가) | 기존 9개 등록·조회 절차 | Redis 4개의 이름·설명·타입·등록 값과 검증 결과 추가 |

Compose는 기존 env_file을 통해 추가 설정도 전달하고 Actions는 기존 배포 묶음에 수정된 조회 스크립트를 포함한다. 새 Secret이나 별도 Compose·Actions 변경은 필요하지 않다. 설정 교체·실패 복구는 [기존 CD 흐름](container-cd.md)을 유지한다.

## 앱 설정

사용자가 Spring 표준 환경변수 이름을 선택하고 `/getddo/dev/app/` 아래에 등록했다.

| 환경변수 | 타입 | 의미 |
| --- | --- | --- |
| SPRING_DATA_REDIS_HOST | String | Redis 원본 DNS |
| SPRING_DATA_REDIS_PORT | String | 6379 |
| SPRING_DATA_REDIS_PASSWORD | SecureString | Redis AUTH 비밀번호, alias/aws/ssm으로 암호화 |
| SPRING_DATA_REDIS_SSL_ENABLED | String | true |

YAML의 로컬 기본값은 localhost·6379·빈 비밀번호·TLS false다. 실제 배포 스크립트는 등록된 네 설정을 모두 요구하고 TLS false를 거부한다. 조회나 검증이 실패하면 기존 app.env와 앱을 교체하지 않는다. 성공해도 커밋·push·main 배포 전에는 실행 중인 앱에 새 설정이 반영되지 않는다.

## 담당자 구현과 연결

기준 dev에는 Redis 클라이언트 의존성과 RedisRateLimiter가 아직 없다. GD-208의 [PR #107](https://github.com/Uplus-GETDDO/getddo-be/pull/107)은 같은 spring.data.redis 블록을 REDIS_* 환경변수로 선언하고 RATE_LIMIT_STORE로 저장소를 선택한다. 병합 시 YAML 블록을 하나로 합치고 담당자의 연결·명령 시간 제한을 보존해야 한다. 이번 PR은 RATE_LIMIT_STORE를 새로 등록하거나 요청 제한 구현을 변경하지 않는다.

설정 전달과 실제 Redis 사용을 구분한다. 담당자 코드 반영 후 저장소 선택·TLS·인증과 API 두 대의 횟수 공유를 검증한다. 장애 동작은 공용 [저장소 장애 정책](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/02-domain/entry.md#요청-제한-저장소-장애)을 따른다.

## 검증 범위

- 원본 작업 폴더에서 변경 전 회귀 실패를 재현했고, 수정 코드의 임시 대역 검증 11개가 통과했다. 두 번의 조회·설정 누락/중복·타입·TLS·포트·비밀값 출력 방지·파일 권한을 확인했다.
- 실제 AWS의 13개를 작업용 프로필로 복호화 조회·검증하고 권한 600의 임시 파일을 생성한 뒤 정리했다. 실제 값은 출력하지 않았다. EC2 역할의 새 코드 실행이나 앱 연결 검증은 아니다.
- 최신 dev 기반 chore/GD-111 브랜치에서 Java 일반 테스트 1,212개(API 337·core 825·storage 50)가 모두 통과했다. API 통합 테스트 224개는 Docker 부재로 실패해 전체 빌드는 실패했다. 테스트를 삭제하거나 비활성화하지 않았다.
