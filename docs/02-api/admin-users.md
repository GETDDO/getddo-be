# 관리자 사용자 조회 구현·연동 안내

공용 계약 원본은 [spec AO01/AO02](https://github.com/Uplus-GETDDO/getddo-spec/blob/22730ba/05-api/user.md)다. 이 문서는 GD-206의 구현 구조·검증·연결 방법을 설명한다.

## 구현 흐름

`AdminUserController → AdminUserQueryService → AdminUserQueryRepositoryImpl / 기존 UserRepository`

- 공통 CurrentUser가 헤더를 DB와 대조한다. 선택 멤버십도 대조하도록 `membershipRequired=false`를 사용하고, 활성 관리자 여부는 서비스에서 검사한다.
- 목록은 JPA Criteria API로 선택 조건을 조합한다. count와 목록에 같은 조건을 적용하고 기존 Entity·UserMapper를 재사용한다. JPA 오프셋 범위를 넘는 페이지는 PageOffsets로 처리한다.
- 상세는 기존 사용자 저장소를 재사용한다. 호출자 확인 실패와 상세 대상 부재의 오류를 구분한다.
- 서비스의 읽기 전용 트랜잭션 안에서 조회하고, API에서는 AdminUserResponse와 PageResult.map으로 공개 필드만 변환한다.
- 사용자 모델·인증 동작·저장 테이블·마이그레이션·기존 가상 사용자 선택 API는 변경하지 않는다. UserErrorCode에는 채택한 관리자 조회 오류만 추가했다.

## FE 연결

관리자 화면에서 기존 공통 사용자 문맥 헤더와 `/api/v1/admin/users` 목록·상세를 호출한다. 실제 경로에 `/api`가 이미 있으므로 FE baseURL에 따른 접두사 중복 여부를 확인한다. 멤버십은 소문자로 표시·전달한다. 기존 `/virtual-users` 시연 계정 선택은 별도 기능이다.

검색과 페이지 변경 시 쿼리를 함께 갱신하고, 호출자 자격 오류와 없는 상세를 구분한다. 정확한 필드·기본값·코드·메시지는 위 공용 원본을 따른다. FE 담당자 구현·실제 브라우저 연결은 별도 확인이 필요하다.

## 검증

- 단위: 관리자 자격, 상세 저장소 재사용, 부재 오류, 페이지/검색어 생성자 검증.
- 실제 MySQL·MVC: 헤더/권한, AND 필터, 특수문자 검색, 안정 정렬, 전체 건수, 기본·범위 밖·큰 페이지, 공개 필드·UTC 정밀도, 조회 전후 사용자 행 비교.
- 실행 결과는 PR과 당일 공용 워크로그에 실제 완료 후 기록한다.

```bash
./gradlew :core:test --tests '*AdminUserQueryServiceTest' \
  :api:integrationTest --tests '*AdminUserIntegrationTest' \
  --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
./gradlew test build --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
```
