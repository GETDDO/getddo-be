# Parameter Store 설정 조회·배포·복구

- 상태: 기존 내용 사용자 검토 완료 · Redis 추가 내용 검토 대기 · Codex 작성
- 작성일: 2026-10-15
- 관련 이슈: GD-112, GD-111
- 적용 범위: 서울 개발 환경의 API EC2 한 대. 여러 서버 순차 배포는 포함하지 않는다.

## 2026-10-16 Redis 설정 추가

사용자가 Redis 환경변수 이름을 선택해 4개를 추가 등록했다. 현재 로컬 조회 코드는 기존 9개와 Redis 4개를 합친 **13개**를 요구한다. 아래 과거 9개 등록·검증 기록은 당시 수행 결과이며 현재 개수와 구분한다.

| 이름 | 설명 | 유형 | 값 |
| --- | --- | --- | --- |
| `/getddo/dev/app/SPRING_DATA_REDIS_HOST` | GETDDO 앱의 ElastiCache Redis 원본 연결 호스트 | String | `master.getddo-dev-redis.lkiced.apn2.cache.amazonaws.com` |
| `/getddo/dev/app/SPRING_DATA_REDIS_PORT` | GETDDO 앱의 ElastiCache Redis 연결 포트 | String | `6379` |
| `/getddo/dev/app/SPRING_DATA_REDIS_PASSWORD` | GETDDO 앱의 ElastiCache Redis 인증 비밀번호 | SecureString | Redis 생성·PONG 확인에 사용한 비밀번호. 값은 문서에 기록하지 않음 |
| `/getddo/dev/app/SPRING_DATA_REDIS_SSL_ENABLED` | GETDDO 앱의 Redis TLS 암호화 연결 사용 여부 | String | `true` |

기존과 동일하게 Standard 계층·text 데이터 형식을 사용하고, Redis 비밀번호는 `alias/aws/ssm`으로 암호화한다. 기존 운영자·EC2 정책은 이 경로를 포함하므로 IAM 추가 변경은 하지 않았다.

- [fetch-app-env.py](../../infra/scripts/fetch-app-env.py)의 `KEYS`에 4개를 추가했다. `GetParameters`의 최대 10개 제한에 맞춰 10개·3개로 조회한 뒤 응답을 합쳐 검증한다. 두 번째 조회가 실패해도 일부 설정만 파일로 쓰지 않는다.
- DB·Redis 비밀번호는 모두 SecureString이어야 한다. Redis는 ElastiCache DNS·6379·TLS true와 Terraform에 맞는 32~64자 영문·숫자 비밀번호를 검사한다. 잘못된 값은 출력하지 않고 앱 교체 전 중단한다.
- [application.yaml](../../api/src/main/resources/application.yaml)의 `spring.data.redis`에 네 환경변수 참조를 추가했다. 로컬 기본값은 localhost·6379·빈 비밀번호·TLS false이며 배포 스크립트는 TLS true를 요구한다. 기존 이미지·CORS 설정은 유지한다.
- Compose의 기존 `env_file`과 배포 묶음의 기존 조회 스크립트 경로를 그대로 사용하므로 Compose·Actions YAML을 추가 수정할 필요는 없다.
- 현재 코드에는 Redis 클라이언트 의존성과 요청 제한 연동이 없다. 설정 전달 준비와 실제 기능 연동 완료를 구분한다.

검증: 변경 전 코드에서 회귀 검증 실패를 재현한 뒤 수정 코드의 임시 대역 테스트 11개가 통과했다. 두 번의 조회·잘못된 응답·설정 누락/중복·타입·Redis 값·파일 권한·비밀값 출력 방지를 확인했다. 실제 AWS 설정 13개 복호화 조회·검증과 권한 600의 임시 env 생성도 통과했다. 실제 값은 출력하지 않았고 임시 파일을 정리했다. 이는 작업용 프로필로 수행한 로컬 검증이며 EC2 역할의 새 코드 실행이나 앱 연결 검증은 아니다. 새 코드의 서버 배포는 PR 병합 후 별도로 진행한다.

YAML 파싱·Python 문법·배포 Bash 문법 검사도 통과했다. 최신 dev 기반 chore/GD-111 브랜치에서 `./gradlew test build --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel`을 실행해 Java 일반 테스트 1,212개(API 337·core 825·storage 50)가 모두 통과했다. API 통합 테스트 224개는 Docker 환경을 찾지 못해 실패해 전체 빌드는 실패했다. 실패한 테스트는 삭제하거나 비활성화하지 않았다.

## 변경 전·후와 볼 파일

| 항목 | 변경 전 | 변경 후 |
| --- | --- | --- |
| 설정 준비 | 서버의 app.env를 직접 편집 | 배포마다 Parameter Store의 필수 13개를 10개·3개로 조회·복호화 |
| 설정 오류 | 조회·검증 단계 없음 | 누락·타입·형식 오류면 기존 앱과 설정을 유지하고 중단 |
| 반영 | 수동 수정 후 컨테이너 재생성 | 임시 설정 사전 검증 후 app.env를 원자적으로 교체하고 강제 재생성 |
| 배포 실패 | 이전 이미지·Compose 복구 | 이전 env를 복원한 뒤 이전 이미지·Compose로 앱 재생성 |
| S3 이미지 설정 | YAML에 환경변수 참조가 이미 존재 | 기존 환경변수 참조와 이미지 설정 설명 유지 |

- [fetch-app-env.py](../../infra/scripts/fetch-app-env.py): 이름이 고정된 13개를 AWS CLI로 나눠 조회·검증하고 새 파일을 권한 600으로 생성한다.
- [deploy-container.sh](../../infra/scripts/deploy-container.sh): 배포 잠금, 설정 교체, 앱 상태 확인과 실패 복구를 담당한다.
- [deploy-ssm.py](../../infra/scripts/deploy-ssm.py): 공개 스크립트·Compose·이미지만 배포 묶음으로 전달한다. 실제 설정값은 Actions로 가져오지 않는다.
- [compose.deploy.yaml](../../compose.deploy.yaml): APP_ENV_FILE로 임시 파일을 사전 검증하고 실제 실행 시 /etc/getddo/app.env를 읽는다. raw 형식으로 달러 기호·따옴표를 보존한다.
- [application.yaml](../../api/src/main/resources/application.yaml): 공통 설정과 환경변수 참조만 관리한다. 이미지 업로드 기능 자체를 추가한 것은 아니다.
- [build.yml](../../.github/workflows/build.yml): 기존 PR 제목 검사와 Java 빌드를 실행한다. 이번에 추가했던 배포 테스트 단계는 사용자 요청으로 제거했다.

## 등록할 설정

AWS 콘솔의 서울 리전 → Systems Manager → Parameter Store → 내 파라미터에 Standard 유형으로 등록한다. 각 이름 앞에는 `/getddo/dev/app/`를 붙인다. GD-112 구현 전 대화에서 사용자가 콘솔 등록 후 이름·유형·버전 목록 9개를 전달했고, EC2에서 get-parameters-by-path의 --with-decryption 조회 결과 개수 9를 확인했다고 전달했다. 정확한 실행 시각과 실제 값은 기록하지 않았다. 이는 사용자의 별도 수동 확인이며 아래의 로컬 대역 검증이나 새 배포 스크립트의 실제 실행 결과는 아니다. 실제 값은 이 문서에 기록하지 않는다.

| 이름 | 유형 | 값의 조건 |
| --- | --- | --- |
| SPRING_DATASOURCE_URL | String | 전체 jdbc:mysql URL, DB 경로, sslMode=VERIFY_IDENTITY와 기존 공개 인증서 저장소 설정 |
| SPRING_DATASOURCE_USERNAME | String | 현재 앱용 DB 계정 |
| SPRING_DATASOURCE_PASSWORD | SecureString | 앱 계정의 실제 비밀번호. 기본 alias/aws/ssm 사용 |
| SPRING_JPA_HIBERNATE_DDL_AUTO | String | validate |
| SPRING_FLYWAY_ENABLED | String | true 또는 false |
| CORS_ALLOWED_ORIGINS | String | 경로 없는 HTTPS origin. 여러 개면 쉼표로 구분 |
| AWS_REGION | String | ap-northeast-2 |
| GETDDO_IMAGES_BUCKET | String | 실제 비공개 이미지 버킷 이름 |
| GETDDO_IMAGES_BASE_URL | String | /images/로 끝나는 HTTPS CloudFront 주소 |
| SPRING_DATA_REDIS_HOST | String | 프로토콜·포트 없는 ElastiCache DNS |
| SPRING_DATA_REDIS_PORT | String | 6379 |
| SPRING_DATA_REDIS_PASSWORD | SecureString | Terraform과 동일한 32~64자 영문·숫자 비밀번호. 기본 alias/aws/ssm 사용 |
| SPRING_DATA_REDIS_SSL_ENABLED | String | true |

이미지 버킷·기본 주소 두 값은 인프라 담당 사용자가 현재 개발 환경에 등록하고 이 배포에 사용하기로 한 설정이다. 공용 이미지 업로드 API 계약이나 타 기능 담당자의 이미지 정책 확정을 의미하지 않는다. 현재 조회 스크립트에서는 Redis 설정을 포함한 13개 모두 필수이므로 이 두 값이 없으면 배포 전에 중단된다.

모든 값은 비어 있지 않은 한 줄이어야 한다. 새 변수를 도입하면 조회 목록·검증·문서를 함께 수정한다. 목록에 없는 기존 app.env 항목은 새 파일에 보존하지 않으므로 전환 전에 추가 설정의 유무를 확인한다.

## 서버 준비와 권한

EC2에 AWS CLI, Python 3.8 이상, Docker, Compose 2.30 이상과 `/etc/getddo`가 필요하다. 설정 디렉터리는 root 소유·700으로 관리한다. 기존 app.env가 있으면 root 소유·600인 일반 파일이어야 하며 심볼릭 링크는 허용하지 않는다. 공개 RDS 인증서 저장소 `/etc/getddo/rds-truststore.p12`도 준비한다.

EC2 역할은 해당 앱 경로의 `ssm:GetParameters`와 SecureString 복호화에 필요한 `kms:Decrypt` 권한을 가져야 한다. 사용자가 등록·조회할 운영자 정책과 EC2의 `read-app-parameters` 인라인 정책은 앞선 작업에서 연결한 기록이 있다. 이 PR은 IAM을 적용하거나 Terraform State를 변경하지 않는다. 기존 직접 연결 정책의 Terraform 편입은 별도 확인한다.

기존 AmazonSSMManagedInstanceCore에는 전체 리소스 GetParameter·GetParameters 허용이 있다. 앱 경로 제한 Allow를 추가해도 기존 유효 권한이 축소되지는 않는다. 환경별 엄격한 격리는 기존 관리 정책과 KMS 키 정책을 함께 검토해야 한다.

## 반영 순서

1. 파라미터 이름·유형·값을 확인하고 현재 app.env의 추가 항목이 없는지 확인한다.
2. GD-112 변경을 dev PR로 검증·리뷰한 뒤 main에 반영한다. dev 병합만으로 서버 CD가 실행되지는 않는다.
3. main Build 성공 후 기존 Container CD가 실행된다. 서버는 배포 잠금을 얻고 필수 설정 13개를 10개·3개로 나눠 조회한다.
4. root 전용 `/etc/getddo/.deploy-env.*/next.env`로 Compose를 검증하고 이미지 체크섬·ID·커밋을 검사한다.
5. 기존 설정을 같은 비공개 임시 디렉터리에 백업하고 root 전용 진행 기록 `/opt/getddo/deploy-in-progress/`와 백업을 디스크에 반영한 뒤 새 설정으로 원자적 교체한다. 컨테이너는 같은 이미지여도 강제 재생성한다.
6. `/v3/api-docs`의 HTTP 200과 실행 이미지 일치가 확인되면 current 링크를 갱신한다. 이는 업무 API·DB 기능 전체 검증은 아니다.

Parameter Store 수정만으로 실행 중인 앱은 바뀌지 않는다. 다음 배포가 실행될 때 최신 값을 반영한다. 설정 파일을 셸의 source 명령으로 실행하거나 비밀값을 명령 인자에 넣지 않는다.

운영자가 값 없이 목록만 확인하는 명령:

```bash
aws ssm get-parameters-by-path \
  --path /getddo/dev/app/ \
  --profile getddo-dev --region ap-northeast-2 \
  --query 'Parameters[].{Name:Name,Type:Type,Version:Version}' \
  --output table --no-cli-pager
```

EC2에서 파일 권한·변수 이름만 확인하는 명령:

```bash
sudo stat -c '%a %U:%G %n' /etc/getddo/app.env
sudo awk -F= '/^[A-Z_]+=/ {print $1}' /etc/getddo/app.env
```

## 실패 시 복구와 한계

조회·검증 실패는 실행 중인 앱과 설정을 바꾸지 않는다. 설정 교체 후 실패하면 배포 직전 파일을 복원하고 이전 이미지로 재생성한다. 이전 앱이 없는 첫 배포는 실패한 앱을 중지하고 기존 파일 상태를 복원한다.

정상 배포 또는 정상 복구가 끝나면 설정·current 링크를 디스크에 반영한 뒤 진행 기록을 제거하고 마지막 동기화 성공 여부를 별도로 확인한다. 정리 완료가 확인된 경우에만 임시 설정·백업을 제거한다. 이전 설정은 백업의 복사본으로 원자적으로 복원하므로 기록 정리 전까지 원본 previous.env가 남는다. 복구나 진행 기록 정리가 실패하면 진행 기록·남은 임시 설정을 보존하고 DEPLOY_INCOMPLETE를 표시해 다음 배포도 차단한다. 파일 복원 자체가 실패하면 권한 700 디렉터리 안의 600 백업을 보존하고 ROLLBACK_FAILED도 표시한다. 운영자가 서버에서 복구한 뒤 해당 비공개 백업을 정리한다. 비밀값은 로그에 출력하지 않는다.

복구 대상은 해당 서버의 배포 직전 app.env와 이미지다. Parameter Store 원격 값과 DB 마이그레이션은 되돌리지 않는다. SIGINT·SIGTERM은 복구하지만 SIGKILL·호스트 장애에는 자동 복구를 보장하지 않는다.

app.env 교체 후 current 링크 확정 전에 강제 종료되면 기존 배포 기록과 새 설정이 섞여 남을 수 있다. 이제 다음 실행은 설정 조회·백업·교체 전에 진행 기록의 존재를 검사하고 DEPLOY_INCOMPLETE로 중단한다. 따라서 중단된 새 설정을 이전 설정으로 잘못 백업하는 다음 배포 경로를 차단한다. 이 변경은 자동 복구가 아니라 운영자 확인 전 배포 중단이다.

운영자는 SSM 원격 명령 종료 여부를 확인하고 서버의 deploy.lock을 확보한 뒤 current 배포·실제 실행 이미지·app.env 조합을 확인한다. root 전용 진행 기록의 context에는 해당 배포와 임시 설정 디렉터리 경로 두 줄만 있으며 비밀값은 없다. 같은 context를 `/etc/getddo/.deploy-env.*/context`에도 보존한다. 마커가 없더라도 이 복구 기록이 남아 있으면 다음 배포를 차단하므로, 마커 삭제나 마지막 동기화 실패 때도 백업과 복구 경로를 잃지 않는다. 남은 previous.env 백업이 검증된 이전 이미지에 해당하면 두 파일을 짝지어 복구하고 앱 상태를 확인한다. 조합을 확인할 수 없다면 배포를 재개하지 않는다. 확인·복구가 끝난 뒤에만 설정과 배포 기록을 디스크에 반영하고 마커와 비공개 임시 디렉터리 양쪽의 context·보존한 임시 파일을 정리한 다음 새 배포를 시작한다. 단순 재시도를 위해 진행 기록부터 삭제하지 않는다. 설정값을 댓글·로그에 출력하지 않는다. 실제 호스트 장애와 AWS 서버에서의 수동 복구 시험은 아직 수행하지 않았다.

## 검증 상태

삭제 전 AWS CLI·Docker 응답을 대역으로 제공하는 로컬 테스트 34개가 통과한 기록이 있다. 이후 사용자 요청으로 이번에 추가한 테스트 파일 3개와 CI의 배포 테스트·Compose 설정 검증 단계를 제거했다. 현재 PR에는 해당 테스트가 포함되지 않는다. 이 34개 대역 테스트 결과는 실제 AWS 조회·컨테이너 실행 검증을 뜻하지 않는다.

새 코드의 실제 서버 배포·설정 반영·롤백 검증은 아직 수행하지 않았다. 로컬 Docker Desktop WSL 연동이 없어 실제 Compose 검증과 Docker 기반 통합 검증에는 제약이 있다. 커밋·PR에는 실제 실행한 검증 결과를 별도로 기록한다.

테스트 파일 제거 후 현재 구성에서 Bash 문법·YAML 파싱·문서 링크 검사를 통과했다. 위 34개 통과는 제거 전 기록이며 현재 배포 회귀 테스트가 남아 있다는 뜻이 아니다. `./gradlew test build --no-daemon --max-workers=1 --no-parallel` 실행에서 일반 테스트 1,195개는 통과했으나 Docker 연결 불가로 API 통합 테스트 216개가 실패해 전체 빌드는 실패했다. 기존 GitHub CI의 Java 테스트·빌드는 유지하며 CI 결과는 PR에서 확인한다.

## 강제 종료 방어 변경의 검증

수정 전 코드에서 설정 교체 후 배포 프로세스를 SIGKILL로 종료하고 다음 배포를 실패시켜, 이전 이미지와 중단된 새 설정이 함께 복구되는 현상을 임시 환경에서 재현했다. 수정 후에는 미완료 진행 기록 때문에 다음 실행이 설정·앱을 건드리기 전에 중단됨을 확인했다. 정상 배포·정상 복구 시 기록 제거, 복구 실패 시 기록 보존 등 임시 검증 16개와 Bash 문법·문서 링크 검사를 통과했다. AWS·Docker는 대역이며 실제 디스크 전원 차단·서버 복구 시험은 아니다. 이전 사용자 요청에 따라 검증 파일과 CI 배포 테스트 단계는 PR에 추가하지 않았다.

이번 수정 후 전체 Gradle test·build도 재실행했다. 일반 테스트 1,195개는 통과했고, Docker 연결 불가로 API 통합 테스트 216개가 실패해 전체 빌드는 실패했다. 실제 서버 배포와 AWS 조회·전원 장애 시험은 수행하지 않았다.

## 2026-10-16 진행 기록 정리 실패 방어

정리 완료를 마커 존재 여부와 별도로 추적한다. 마커 context 삭제 후 폴더 삭제 또는 마지막 root 동기화가 실패해도 비공개 설정 디렉터리의 context·원본 previous.env를 보존한다. 다음 실행은 마커가 없어도 남은 context를 감지해 배포를 차단한다. 복구는 원본 백업의 복사본으로 수행해 정리 완료 전 원본 백업을 소모하지 않는다.

수정 전 코드에 폴더 삭제·마지막 root 동기화 실패를 주입해 보존 실패를 재현했다. 수정 후 정상 배포·롤백 양쪽의 실패 주입과 강제 종료·재배포 차단 등을 포함한 임시 검증 18개가 통과했다. Bash 문법·문서 링크·diff 검사도 통과했다. 전체 Gradle 재실행은 일반 1,195개 통과, Docker 연결 불가로 API 통합 216개 실패다. 테스트 파일·CI 배포 테스트 단계는 사용자 요청에 따라 PR에 추가하지 않았으며 실제 서버 시험은 수행하지 않았다.
