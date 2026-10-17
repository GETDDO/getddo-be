# 이벤트·경품 이미지 업로드 (GD-227)

- 상태: 검토 대기 — 구현 설명, 담당자 확인 전

사용자가 확정한 BE 수신 방식과 JPG·PNG, **5MB = 5,000,000바이트 이하** 기준을 구현한다. 공용 spec과 DB 스키마는 변경하지 않는다.

## 호출 순서

1. 관리자 문맥 헤더로 `POST /api/v1/admin/events/images?target=EVENT`를 호출한다. 경품은 `target=PRIZE`다.
2. 요청은 `multipart/form-data`이고 `file` 파트에 이미지 한 장을 보낸다. JPG(`.jpg`·`.jpeg`, `image/jpeg`) 또는 PNG(`.png`, `image/png`)만 허용한다. 확장자·MIME·ImageIO가 읽은 실제 형식과 이미지 크기 헤더를 대조한다. 전체 픽셀 디코딩·재인코딩은 하지 않는다.
3. 201 공통 응답의 `data.imageKey`를 기존 이벤트·경품 등록 또는 수정 요청의 `imageKey`에 넣는다. `data.imageUrl`은 미리보기에 사용한다. 이미지가 없는 기존 요청도 계속 허용한다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "성공했습니다.",
  "data": {
    "imageKey": "events/550e8400-e29b-41d4-a716-446655440000.png",
    "imageUrl": "https://cdn.example.com/images/events/550e8400-e29b-41d4-a716-446655440000.png"
  }
}
```

위 JSON의 공통 성공 필드 값은 실제 `ResponseEnvelope.success`를 따른다. `imageKey`는 상대 키이며 S3 객체 키는 `images/` + `imageKey`다. 원본 파일명은 저장 키에 사용하지 않는다. 사용자 목록·상세와 관리자 등록·수정·목록·상세의 이벤트 및 경품 `imageUrl`도 같은 기준으로 조합한다. 공개 주소가 미설정된 기존 조회는 `imageUrl=null`을 유지하고, 신규 업로드는 거절한다.

| 조건 | 응답 |
| --- | --- |
| 빈 파일·지원하지 않는 형식·형식 위장·잘못된 헤더 | 400 `EVENT-018` |
| 컨테이너 multipart 파서의 파일/요청 크기 초과 | 413 `HTTP-413` |
| 서비스 직접 호출에서 파일 크기 초과 | 400 `EVENT-018` |
| 관리자 아님·비활성 사용자 | 403 `EVENT-006` |
| 사용자 문맥 누락·불일치 | 기존 공통 사용자 문맥 오류 |
| S3·공개 주소 미설정 또는 S3 저장 실패 | 503 `EVENT-019` |

기존 `@CurrentUser`는 시연용 사용자 문맥이며 로그인 인증을 새로 제공하는 API가 아니다. 이미지 저장에는 이벤트 ID가 필요하지 않으며, 등록 전 업로드 후 등록을 포기한 객체의 자동 정리나 기존 이미지 객체 삭제는 이번 변경에 포함하지 않는다.

## 설정·구조

- `GETDDO_IMAGES_BUCKET`: 비공개 이미지 버킷 이름.
- `GETDDO_IMAGES_BASE_URL`: `/images/`로 끝나는 공개 CloudFront 주소. 공개 URL에 S3 접근 자격 증명을 넣지 않는다.
- `AWS_REGION`: 실제 버킷 리전. SDK의 기본 자격 증명 체인으로 배포 환경의 IAM 권한을 사용한다. 코드에 AWS 키를 넣지 않는다.
- `GETDDO_IMAGES_ENDPOINT`: S3Mock에서만 명시적으로 지정하는 HTTP 엔드포인트. 실제 AWS에서는 비워 둔다. 지정 시 path-style을 사용한다.
- `spring.servlet.multipart.max-file-size=5000000B`, `max-request-size=5100000B`. 요청 전체 상한에는 multipart 헤더 공간을 포함한다.

`core/event/service/EventImageUploadService`는 권한·종류·용량·키 생성을 맡고 `EventImageStorage`에 저장을 위임한다. `storage:s3`가 AWS SDK v2 `S3Client`로 PUT한다. 네트워크 호출 동안 DB 트랜잭션을 잡지 않으며 DB에 업로드 이력을 새로 만들지 않는다. S3 설정이 없는 로컬 실행에서도 기존 기능을 기동할 수 있도록 클라이언트는 지연 생성한다.

## 로컬 S3Mock

```bash
docker run --rm --name getddo-image-test -p 9090:9090 \
  -e COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS=getddo-images-local \
  adobe/s3mock@sha256:ab01a6946750f451ca215a47e91030695b260e4003b8a5a6201d25029b8fca92
```

로컬 BE 프로세스에 다음을 주입한다. 아래 키는 S3Mock 전용 더미값이며 실제 AWS에 사용하지 않는다.

```text
AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=s3mock
AWS_SECRET_ACCESS_KEY=s3mock
GETDDO_IMAGES_ENDPOINT=http://localhost:9090
GETDDO_IMAGES_BUCKET=getddo-images-local
GETDDO_IMAGES_BASE_URL=http://localhost:9090/getddo-images-local/images/
```

기존 9090 포트의 S3Mock이 있으면 새 컨테이너 대신 기존 버킷·포트를 사용한다. BE가 Docker 안에서 실행되면 `localhost`는 BE 컨테이너 자신이므로 접근 가능한 S3Mock 호스트로 지정한다.

```bash
curl -X POST 'http://localhost:8080/api/v1/admin/events/images?target=EVENT' \
  -H 'X-User-ID: DB에-존재하는-관리자-UUID' -H 'X-User-Role: ADMIN' \
  -F 'file=@small.png;type=image/png'
```

자동 통합 테스트는 전용 S3Mock·MySQL 컨테이너와 실제 HTTP 서버를 띄운다. 작은 PNG/JPG 업로드, URL에서 원본 바이트·Content-Type 조회, 이벤트·경품 등록과 목록·상세 URL 연결, 정확한 5MB 경계 및 권한·형식 오류를 검증한다.

```bash
./gradlew :core:test --tests '*EventImageUploadServiceTest' \
  :api:test --tests '*EventImageUrlsTest' --tests '*EventResponseContractTest' \
  :api:integrationTest --tests '*EventImageUploadIntegrationTest' \
  --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
```

2026-10-17 KST에 위 대상 검증과 `:storage:s3:test`의 총 27개 테스트가 통과했다. 작은 PNG·JPG를 BE API로 전송해 201 응답을 받고, 반환 URL의 GET 200·Content-Type·원본 바이트 일치를 확인했다. 5,000,000바이트는 업로드되고 5,000,001바이트는 HTTP 413으로 거절됐다.

같은 날 18:38 KST에 `test build --offline --no-daemon --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel`도 17분 8초로 성공했다. JUnit 보고서 합계 2,423개(일반 1,371 / 통합 1,052), 실패·오류·건너뜀은 0개다. 새로 추가한 테스트는 10개다.

S3Mock 성공은 실제 AWS IAM·버킷 정책·CloudFront 배포 검증을 대신하지 않는다. 배포 환경에서는 같은 API로 업로드한 실제 키를 CloudFront URL에서 확인해야 한다.

참고: [AWS S3 SDK](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html), [기본 자격 증명 체인](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html), [Adobe S3Mock](https://github.com/adobe/S3Mock).
