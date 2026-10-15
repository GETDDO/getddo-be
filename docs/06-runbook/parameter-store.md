# Parameter Store 설정 조회·배포·복구

- 상태: 사용자 검토 완료 · Codex 작성 · 2026-10-15 사용자 요청으로 상태 갱신
- 작성일: 2026-10-15
- 관련 이슈: GD-112
- 적용 범위: 서울 개발 환경의 API EC2 한 대. 여러 서버 순차 배포는 포함하지 않는다.

## 변경 전·후와 볼 파일

| 항목 | 변경 전 | 변경 후 |
| --- | --- | --- |
| 설정 준비 | 서버의 app.env를 직접 편집 | 배포마다 Parameter Store의 필수 9개 조회·복호화 |
| 설정 오류 | 조회·검증 단계 없음 | 누락·타입·형식 오류면 기존 앱과 설정을 유지하고 중단 |
| 반영 | 수동 수정 후 컨테이너 재생성 | 임시 설정 사전 검증 후 app.env를 원자적으로 교체하고 강제 재생성 |
| 배포 실패 | 이전 이미지·Compose 복구 | 이전 env를 복원한 뒤 이전 이미지·Compose로 앱 재생성 |
| S3 이미지 설정 | YAML에 환경변수 참조가 이미 존재 | 기존 환경변수 참조와 이미지 설정 설명 유지 |

- [fetch-app-env.py](../../infra/scripts/fetch-app-env.py): 이름이 고정된 9개를 AWS CLI로 조회·검증하고 새 파일을 권한 600으로 생성한다.
- [deploy-container.sh](../../infra/scripts/deploy-container.sh): 배포 잠금, 설정 교체, 앱 상태 확인과 실패 복구를 담당한다.
- [deploy-ssm.py](../../infra/scripts/deploy-ssm.py): 공개 스크립트·Compose·이미지만 배포 묶음으로 전달한다. 실제 설정값은 Actions로 가져오지 않는다.
- [compose.deploy.yaml](../../compose.deploy.yaml): APP_ENV_FILE로 임시 파일을 사전 검증하고 실제 실행 시 /etc/getddo/app.env를 읽는다. raw 형식으로 달러 기호·따옴표를 보존한다.
- [application.yaml](../../api/src/main/resources/application.yaml): 공통 설정과 환경변수 참조만 관리한다. 이미지 업로드 기능 자체를 추가한 것은 아니다.
- [build.yml](../../.github/workflows/build.yml): 기존 PR 제목 검사와 Java 빌드를 실행한다. 이번에 추가했던 배포 테스트 단계는 사용자 요청으로 제거했다.

## 등록할 설정

AWS 콘솔의 서울 리전 → Systems Manager → Parameter Store → 내 파라미터에 Standard 유형으로 등록한다. 각 이름 앞에는 `/getddo/dev/app/`를 붙인다. 현재 사용자가 9개 등록과 EC2 복호화 조회 개수 9를 확인한 상태다. 실제 값은 이 문서에 기록하지 않는다.

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

모든 값은 비어 있지 않은 한 줄이어야 한다. 새 변수를 도입하면 조회 목록·검증·문서를 함께 수정한다. 목록에 없는 기존 app.env 항목은 새 파일에 보존하지 않으므로 전환 전에 추가 설정의 유무를 확인한다.

## 서버 준비와 권한

EC2에 AWS CLI, Python 3.8 이상, Docker, Compose 2.30 이상과 `/etc/getddo`가 필요하다. 설정 디렉터리는 root 소유·700으로 관리한다. 기존 app.env가 있으면 root 소유·600인 일반 파일이어야 하며 심볼릭 링크는 허용하지 않는다. 공개 RDS 인증서 저장소 `/etc/getddo/rds-truststore.p12`도 준비한다.

EC2 역할은 해당 앱 경로의 `ssm:GetParameters`와 SecureString 복호화에 필요한 `kms:Decrypt` 권한을 가져야 한다. 사용자가 등록·조회할 운영자 정책과 EC2의 `read-app-parameters` 인라인 정책은 앞선 작업에서 연결한 기록이 있다. 이 PR은 IAM을 적용하거나 Terraform State를 변경하지 않는다. 기존 직접 연결 정책의 Terraform 편입은 별도 확인한다.

기존 AmazonSSMManagedInstanceCore에는 전체 리소스 GetParameter·GetParameters 허용이 있다. 앱 경로 제한 Allow를 추가해도 기존 유효 권한이 축소되지는 않는다. 환경별 엄격한 격리는 기존 관리 정책과 KMS 키 정책을 함께 검토해야 한다.

## 반영 순서

1. 파라미터 이름·유형·값을 확인하고 현재 app.env의 추가 항목이 없는지 확인한다.
2. GD-112 변경을 dev PR로 검증·리뷰한 뒤 main에 반영한다. dev 병합만으로 서버 CD가 실행되지는 않는다.
3. main Build 성공 후 기존 Container CD가 실행된다. 서버는 배포 잠금을 얻고 9개 값을 조회한다.
4. root 전용 `/etc/getddo/.deploy-env.*/next.env`로 Compose를 검증하고 이미지 체크섬·ID·커밋을 검사한다.
5. 기존 설정을 같은 비공개 임시 디렉터리에 백업하고 새 설정으로 원자적 교체한다. 컨테이너는 같은 이미지여도 강제 재생성한다.
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

정상 종료·복구 시 임시 파일과 백업을 제거한다. 파일 복원 자체가 실패하면 권한 700 디렉터리 안의 600 백업을 보존하고 ROLLBACK_FAILED와 경로를 표시한다. 운영자가 서버에서 복구한 뒤 해당 비공개 백업을 정리한다. 비밀값은 로그에 출력하지 않는다.

복구 대상은 해당 서버의 배포 직전 app.env와 이미지다. Parameter Store 원격 값과 DB 마이그레이션은 되돌리지 않는다. SIGINT·SIGTERM은 복구하지만 SIGKILL·호스트 장애에는 자동 복구를 보장하지 않는다.

특히 app.env 교체 후 current 링크 확정 전에 강제 종료되면 기존 배포 기록과 새 설정이 섞여 남을 수 있다. 이후 자동 재배포는 그 새 설정을 이전 이미지와 함께 복구할 수 있으므로, 단순 재시도로 정상 조합이 보장되지 않는다. 이 경우 자동 재배포를 진행하지 말고 current가 가리키는 배포·실제 실행 이미지·설정 파일을 서버에서 확인한다. 검증된 이전 설정 백업이 있다면 해당 이미지와 짝을 맞춰 수동 복구하고, 조합을 확인할 수 없다면 배포를 재개하지 않는다. 설정값을 댓글·로그에 출력하지 않는다. 영속적인 배포 기록으로 중단 상태를 자동 정리하는 기능과 실제 강제 종료 복구 시험은 아직 구현·검증하지 않았다.

## 검증 상태

삭제 전 AWS CLI·Docker 응답을 대역으로 제공하는 로컬 테스트 34개가 통과한 기록이 있다. 이후 사용자 요청으로 이번에 추가한 테스트 파일 3개와 CI의 배포 테스트·Compose 설정 검증 단계를 제거했다. 현재 PR에는 해당 테스트가 포함되지 않는다. 실제 AWS 조회·컨테이너 실행 검증을 뜻하는 결과는 아니다.

새 코드의 실제 서버 배포·설정 반영·롤백 검증은 아직 수행하지 않았다. 로컬 Docker Desktop WSL 연동이 없어 실제 Compose 검증과 Docker 기반 통합 검증에는 제약이 있다. 커밋·PR에는 실제 실행한 검증 결과를 별도로 기록한다.

테스트 파일 제거 후 현재 구성에서 Bash 문법·YAML 파싱·문서 링크 검사를 통과했다. 위 34개 통과는 제거 전 기록이며 현재 배포 회귀 테스트가 남아 있다는 뜻이 아니다. `./gradlew test build --no-daemon --max-workers=1 --no-parallel` 실행에서 일반 테스트 1,195개는 통과했으나 Docker 연결 불가로 API 통합 테스트 216개가 실패해 전체 빌드는 실패했다. 기존 GitHub CI의 Java 테스트·빌드는 유지하며 CI 결과는 PR에서 확인한다.
