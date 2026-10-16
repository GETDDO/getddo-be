# GETDDO Backend

GETDDO는 응모권을 모아 경품 이벤트에 참여하고 추첨 결과를 확인하는 **이벤트 응모 및 추첨 플랫폼**입니다. 이 README는 멘토링과 발표를 위해 프로젝트 개요부터 수행 경과·자체 평가까지 같은 순서로 구성했습니다.

> 구현 현황·검증 기준: 2026-10-16 KST, BE dev `15f2d95c` / spec `8666b4e`. 작업 브랜치의 진행 상황과 원격 CI 결과는 별도로 확인합니다. 세부 기술 설명과 코드·테스트 근거는 [Wiki 멘토링 자료](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring)에서 확인합니다.

### 목차

1. [프로젝트 개요](#01-프로젝트-개요)
2. [프로젝트 팀 구성 및 역할](#02-프로젝트-팀-구성-및-역할)
3. [프로젝트 수행 절차 및 방법](#03-프로젝트-수행-절차-및-방법)
4. [프로젝트 수행 경과](#04-프로젝트-수행-경과)
5. [자체 평가 의견](#05-자체-평가-의견)

## 01. 프로젝트 개요

### 서비스 소개와 기획 배경

커머스의 경품 이벤트는 사용자의 참여를 유도하는 동시에 응모권 지급·사용 이력과 추첨 결과에 대한 신뢰가 필요한 기능입니다. GETDDO는 이 과제를 주제로 출석·미션·게임에서 이벤트 참여와 운영자용 백오피스까지 이어지는 서비스를 개발합니다.

사용자가 보는 참여 화면뿐 아니라 중복 요청, 동시 응모, 응모권 반환, 추첨 근거 보존과 실패 복구를 함께 다룹니다. 기획의 기준은 [공용 요구사항](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/00-requirements/README.md)입니다.

### 프로젝트 목표

| 목표 | 확인할 내용 |
| --- | --- |
| 참여형 이벤트 경험 | 활동으로 응모권을 얻고 이벤트 조회·응모·결과 확인으로 이어지는 사용자 흐름 |
| 응모권의 일관성 | 지급·차감·반환·만료 이력과 현재 응모권 상태의 일치 |
| 신뢰할 수 있는 응모·추첨 | 중복 접수·중복 당첨 방지와 후보·가중치·조건·확정 결과 보존 |
| 운영 가능한 서비스 | 관리자 이벤트·경품 관리, 취소 처리, 알림과 실패 재처리 |

### 주요 서비스 흐름

```mermaid
flowchart LR
    Earn["출석·미션·게임으로 응모권 획득"] --> Browse["이벤트 조회"]
    Browse --> Enter["이벤트 응모·응모권 사용"]
    Enter --> Close["모집 마감"]
    Close --> Draw["마감 5분 후 자동 추첨"]
    Draw --> Review["관리자 확인·수동 공개"]
    Review --> Result["사용자 결과 확인·알림"]
```

이 그림은 목표 서비스 흐름입니다. 게임 결과·보상과 최초 수동 공개의 남은 연결은 [현재 구현 현황](#현재-구현-현황과-남은-연동-과제)에 구분했습니다. 응모권 미사용 이벤트도 있으며, 모든 이벤트가 응모권을 차감하는 것은 아닙니다. 추첨 실행과 사용자에게 결과를 공개하는 시점은 분리합니다.

### 개발 범위와 시연 환경

- 가상 사용자와 더미 데이터로 사용자·관리자 흐름을 시연합니다. 실제 회원가입·로그인 대신 시연용 사용자 문맥을 사용합니다.
- 사용자 기능과 백오피스 API, 업무 규칙, 저장·동시성 처리와 비동기 작업을 개발합니다. 이 저장소는 백엔드이며 프론트엔드는 별도 저장소에서 개발합니다.
- 알림은 사이트 내 목록·읽음 기능과 외부 발송의 모의 처리를 대상으로 합니다. 실제 문자·이메일·외부 푸시는 제외합니다.
- 로컬은 Java 21·Docker Compose·MySQL 8.4, DB 통합 테스트는 Testcontainers MySQL 8.4를 사용합니다.
- S3Mock은 이미지 객체 입출력의 로컬 확인 용도입니다. 실제 AWS 권한·CloudFront·프론트 이미지 표시와 배포 완료 여부는 별도로 검증합니다.

상세 포함·제외 범위는 [공용 구현 범위](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/00-requirements/scope.md)를 따릅니다.

## 02. 프로젝트 팀 구성 및 역할

### 팀원 소개와 담당 영역

| 팀원 | 담당 영역 | 주요 기능 |
| --- | --- | --- |
| 이경주 | 인프라·공통 기반·사용자 | 실행·배포 환경, 공통 응답·오류·시간·감사 기록, 사용자·역할·멤버십 |
| 석종수 | 어뷰징 차단 기록·추첨 | 지급 전 차단 근거 기록, 후보·가중치 스냅샷, 추첨·당첨 취소·재추첨·결과 공개 |
| 전민규 | 백오피스·알림·게임 | 관리자 운영 기능, 배너·정책 관리, 알림 생성·발송·복구, 게임 검증·보상 자격 |
| 윤태형 | 응모권·미션·출석·응모 | 응모권 지급·차감·반환·만료, 출석·미션 보상, 응모 접수·내역 |
| 박지훈 | 이벤트·경품 관리·이벤트 취소 | 사용자·관리자 이벤트 조회, 이벤트·경품 CRUD, 시작·마감 전환, 취소 연동 |

담당 영역은 구현 책임을 나타내며 완료 현황을 의미하지 않습니다. 상세 경계는 [백엔드 기능 담당자](docs/01-conventions/backend-feature-assignments.md)를 확인합니다.

### 기능 간 연동과 협업 방식

| 연동 흐름 | 처리 방식 |
| --- | --- |
| 출석·미션·게임 → 응모권 지급 | 각 활동에서 보상 자격을 판정한 뒤 공통 응모권 지급 서비스를 호출 |
| 이벤트 설정 → 사용자 응모 | 이벤트에 저장된 기간·멤버십·응모 조건을 접수 시 검증하고, 응모 기록과 응모권 차감을 함께 처리 |
| 응모 마감 → 추첨 → 결과 공개·알림 | 모집 마감 후 자동 추첨을 실행하고, 관리자 확인·수동 공개 후 결과 조회와 알림에 반영 |
| 이벤트 취소 → 반환·알림·감사 | 취소 상태 변경, 응모권 반환, 취소 알림 작업 등록과 감사 기록을 하나의 트랜잭션으로 처리 |

공용 API·정책을 먼저 맞추고 담당 기능에서 필요한 서비스를 호출합니다. 공동 경계의 변경은 담당자와 합의하며, Jira 태스크별 브랜치와 GitHub PR에서 변경 범위·테스트 결과를 공유합니다.

## 03. 프로젝트 수행 절차 및 방법

### 요구사항 분석과 서비스 정책 수립

참여·보상·추첨 흐름에서 발생할 수 있는 중복, 기간 경계, 취소와 실패 상황을 요구사항으로 정리하고 도메인 규칙·ADR에 결정 이유를 남깁니다. 확정 정책과 미결정 사항을 구분하며 공용 정책은 [getddo-spec](https://github.com/Uplus-GETDDO/getddo-spec)에서 관리합니다.

### 사용자 흐름 및 API 설계

사용자 화면과 관리자 운영 흐름을 기준으로 조회·명령 API, 요청·응답 DTO, 오류 코드와 페이지 형식을 정합니다. HTTP 입력 형식은 API 계층에서 받고, 자격·기간·상태와 같은 업무 판단과 트랜잭션은 서비스에서 처리합니다.

[공용 API 명세](https://github.com/Uplus-GETDDO/getddo-spec/tree/main/05-api)와 백엔드 [API 구현 문서](docs/02-api/README.md)를 구분하며 Swagger/OpenAPI로 실제 제공 경로와 스키마를 확인합니다.

### 전체 아키텍처

단일 Spring Boot 애플리케이션으로 실행하는 멀티모듈 구조이며, 각 모듈 안을 기능별 패키지로 나눕니다.

```mermaid
flowchart LR
    Client["사용자·관리자 프론트"] --> API["api: HTTP 요청·응답"]
    API --> Core["core: 업무 규칙·Service·Repository 계약"]
    API --> DB["storage:db: JPA·JDBC 구현"]
    DB --> Core
    DB --> MySQL[("MySQL 8.4")]
    Worker["같은 앱의 예약 워커"] --> Core
```

#### 모듈 의존 관계

api → core, api → storage:db, storage:db → core는 빌드 의존 방향입니다. 실제 업무 서비스는 Repository 인터페이스를 통해 주입된 저장 구현을 호출합니다. 모듈을 별도 서버로 배포하는 구조는 아닙니다.

| 구분 | 역할·사용 이유 |
| --- | --- |
| api | HTTP DTO·형식 검증·공통 사용자 문맥·응답 변환을 업무 규칙과 분리 |
| core | API와 워커가 업무 규칙·트랜잭션을 재사용. Spring Context/Transaction 의존성은 허용 |
| storage:db | Entity·JPA/JDBC·잠금·쿼리·마이그레이션을 저장 책임에 모음 |

이벤트 시작·마감과 응모권 만료는 각각 전용 스레드를 사용합니다. 알림·추첨은 DB 작업 상태·소유권 만료·재시도로 중복 처리와 장애 복구를 관리합니다. [Wiki 전체 아키텍처](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Architecture)에 호출 흐름·트랜잭션과 워커 경계를 설명했습니다.

#### 클라우드 설계도

[![GETDDO AWS 클라우드 아키텍처](docs/assets/cloud-architecture.png)](docs/assets/cloud-architecture.drawio)

[draw.io 원본](docs/assets/cloud-architecture.drawio). 이 그림은 인프라 설계 자료이며 실제 2대 EC2 운영·Redis 캐시·S3 이미지 연동 완료를 뜻하지 않습니다. 현재 요청 제한은 인메모리 구현이고, 실제 배포 검증 상태는 [배포 문서](docs/06-runbook/container-cd.md)를 확인합니다.

### 기술 스택

| 구분 | 기술 |
| --- | --- |
| 언어 | ![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) |
| 프레임워크 | ![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white) ![Spring MVC](https://img.shields.io/badge/Spring_MVC-6DB33F?style=for-the-badge&logo=spring&logoColor=white) |
| 빌드 | ![Gradle 9.7.1](https://img.shields.io/badge/Gradle-9.7.1-02303A?style=for-the-badge&logo=gradle&logoColor=white) |
| 데이터베이스 | ![MySQL 8.4](https://img.shields.io/badge/MySQL-8.4-4479A1?style=for-the-badge&logo=mysql&logoColor=white) |
| 캐시 · 도입 예정 | ![Redis](https://img.shields.io/badge/Redis-FF4438?style=for-the-badge&logo=redis&logoColor=white) |
| 영속성·마이그레이션 | ![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white) ![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=for-the-badge&logo=flyway&logoColor=white) |
| 객체 매핑 | ![MapStruct 1.6.3](https://img.shields.io/badge/MapStruct-1.6.3-455A64?style=for-the-badge) ![Lombok](https://img.shields.io/badge/Lombok-BC4521?style=for-the-badge) |
| API 문서 | ![springdoc-openapi 3.1.1](https://img.shields.io/badge/springdoc--openapi-3.1.1-85EA2D?style=for-the-badge&logo=swagger&logoColor=black) ![Swagger UI](https://img.shields.io/badge/Swagger_UI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black) |
| 테스트 | ![JUnit](https://img.shields.io/badge/JUnit-25A162?style=for-the-badge&logo=junit5&logoColor=white) ![Spring Boot Test](https://img.shields.io/badge/Spring_Boot_Test-6DB33F?style=for-the-badge&logo=springboot&logoColor=white) ![Testcontainers](https://img.shields.io/badge/Testcontainers-2496ED?style=for-the-badge) |
| 실행 환경 | ![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white) ![Docker Compose](https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white) |
| CI | ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white) |

### 데이터베이스 설계와 도메인별 책임 분리

현재 테이블·컬럼·제약의 실행 기준은 [Flyway SQL](storage/db/src/main/resources/db/migration)입니다. [DB 문서](docs/03-database/README.md)와 [API·DB 대응](docs/03-database/api-schema-mapping.md)에서 저장 책임과 기능 연결을 확인합니다.

사용자, 이벤트·경품, 응모자·접수 이력, 응모권·처리 이력, 추첨 실행·스냅샷·공개 결과를 역할별로 나눕니다. 현재 상태와 처리 이력, 내부 추첨 결과와 공개 명단을 구분해 변경 이후에도 처리 근거를 확인하도록 합니다. 함께 확정해야 하는 데이터는 서비스 트랜잭션으로 묶습니다.

#### ERD

> 기존 ERD 이미지는 2026-10-02 스냅샷입니다. 수정한 이미지는 추후 교체할 예정이며 이번 문서 작업에서는 보존합니다. 아래 도메인 표는 기존 이미지 안내이며 현재 DB 개수 집계가 아닙니다. 현재 스키마는 전체 Flyway 적용 결과를 기준으로 확인합니다.

[![GETDDO 전체 ERD](https://raw.githubusercontent.com/wiki/Uplus-GETDDO/getddo-be/assets/erd-2026-10-02/full.png)](https://raw.githubusercontent.com/wiki/Uplus-GETDDO/getddo-be/assets/erd-2026-10-02/full.png)

[ERDCloud에서 확대·이동하며 보기](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9) · [원본 이미지](https://raw.githubusercontent.com/wiki/Uplus-GETDDO/getddo-be/assets/erd-2026-10-02/full.png)

##### 도메인별 ERD

도메인을 선택하면 GitHub Wiki에서 해당 도메인의 전체 ERD와 테이블별 컬럼 표를 확인할 수 있습니다. 이미지를 누르면 원본 크기로 열립니다. [전체 Wiki 목록](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD)에서도 도메인별로 이동할 수 있습니다.

| 도메인 | 테이블 수 | 내용 |
| --- | ---: | --- |
| [사용자](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-user) | 1 | 사용자 정보·역할·멤버십 |
| [이벤트·응모](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-event) | 5 | 이벤트·경품·응모자·응모 요청·배너 |
| [출석](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-attendance) | 5 | 일일·연속 출석과 보상 기록 |
| [미션](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-mission) | 10 | 퀴즈·설문·제출·보상 기록 |
| [게임](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-game) | 4 | 게임·플레이·통계·보상 기록 |
| [보상 정책](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-reward) | 1 | 출석·미션·게임의 보상 정책 |
| [응모권](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-ticket) | 2 | 응모권 한 장의 현재 상태와 처리 이력 |
| [어뷰징 검토](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-abuse) | 1 | 의심 행위 탐지와 관리자 검토 |
| [추첨·발표](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-drawing) | 6 | 추첨 실행·후보·결과·당첨·발표 |
| [알림](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-notification) | 2 | 알림 생성 작업과 사용자 알림 |
| [감사 로그](https://github.com/Uplus-GETDDO/getddo-be/wiki/ERD-audit) | 1 | 변경 행위와 변경 전후 데이터 |

### Jira·GitHub 기반 이슈 관리와 코드 리뷰

프론트엔드와 백엔드는 별도 저장소에서 개발하고, 공용 요구사항·도메인 정책·협업 규칙은 [getddo-spec](https://github.com/Uplus-GETDDO/getddo-spec)에서 관리합니다. 백엔드의 기능별 담당 영역과 공동 작업 경계는 [백엔드 기능 담당자](docs/01-conventions/backend-feature-assignments.md)를 따릅니다.

개발은 Jira 이슈 생성 → `dev` 기준 작업 브랜치 → 구현·테스트 → Pull Request → CI·코드 리뷰 → 병합 순서로 진행합니다. 상세 규칙은 다음 원본 문서를 확인합니다.

- [브랜치 전략](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/01-conventions/branch.md)
- [커밋 규칙](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/01-conventions/commit.md)
- [개발 흐름](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/01-conventions/workflow.md)
- [Pull Request 규칙](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/01-conventions/pull-request.md)
- [백엔드 코드 스타일](docs/01-conventions/code-style.md)
- [백엔드 문서와 작업 기록](docs/README.md)
- [이 저장소의 PR 템플릿](.github/pull_request_template.md)

작업 브랜치는 태스크 단위로 만들고 `dev`에 직접 PR을 보냅니다. 리뷰에서는 정책 일치, 책임 배치, 트랜잭션·동시성·오류 경계와 테스트를 확인합니다. 날짜별 워크로그는 기능 PR과 분리해 `docs/YYYY-MM-DD` 공용 브랜치에서 공유합니다.

### 테스트와 CI

단위·MVC 테스트와 실제 MySQL 통합 테스트를 분리합니다. 업무 규칙뿐 아니라 중복 요청, 잠금 대기, 마감 경계, 실패 시 롤백·재시도와 데이터 제약을 검증합니다. 시간 경계 검증에는 고정 또는 제어 가능한 시계를 사용하는 기준을 둡니다.

[Build 워크플로](.github/workflows/build.yml)는 PR과 main/dev push에서 빌드·테스트를 수행하고, 성공하면 [컨테이너 이미지 워크플로](.github/workflows/image.yml)를 호출합니다. 실패 보고서는 Actions 아티팩트로 남깁니다.

### CI/CD 구성

main의 성공한 CI 이미지에 대해 전달 파일·체크섬을 보관하고 [CD 워크플로](.github/workflows/cd.yml)에서 AWS S3·SSM 기반 서버 교체와 실패 복구를 수행하도록 구성했습니다. **구성 코드와 실제 배포 성공은 구분**하며, 현재 배포 준비·검증 상태는 [컨테이너 CD 런북](docs/06-runbook/container-cd.md)을 따릅니다.

### 개발 환경 참고

<details>
<summary>패키지 구조와 기능 추가 시 배치</summary>

#### 패키지 구조

하나의 Spring Boot 애플리케이션으로 실행하는 멀티모듈 구조입니다. 아래는 주요 디렉터리와 기능 추가 시 패키지 배치 기준입니다. `<도메인>`에는 `user`, `event`, `ticket` 등 기능별 패키지 이름이 들어갑니다.

```text
getddo-be/
├── api/                                         # HTTP 요청·응답과 애플리케이션 실행
│   ├── build.gradle                             # 웹·검증·API 문서 의존성
│   └── src/
│       ├── main/
│       │   ├── java/com/getddo/api/
│       │   │   ├── GetddoBeApplication.java     # Spring Boot 진입점
│       │   │   ├── common/                      # API 공통 처리
│       │   │   │   ├── config/                  # MVC·CORS·OpenAPI 설정
│       │   │   │   ├── context/                 # 사용자 문맥 어노테이션·ArgumentResolver
│       │   │   │   ├── exception/               # 공통 API 오류 코드·전역 예외 처리
│       │   │   │   └── response/                # 공통 응답 형식 ResponseEnvelope
│       │   │   └── <도메인>/
│       │   │       ├── controller/              # 요청을 받아 Service에 위임
│       │   │       ├── dto/request/             # 요청 DTO와 입력 검증
│       │   │       └── dto/response/            # 응답 DTO·from(domain) 변환
│       │   └── resources/                       # application.yaml 등 실행 설정
│       ├── test/                                # DB 없는 API·설정 테스트
│       └── integrationTest/                     # 앱 기동·API·DB 연동 테스트
├── core/                                        # 도메인 모델과 비즈니스 규칙
│   ├── build.gradle                             # Spring Context·트랜잭션 의존성
│   └── src/
│       ├── main/java/com/getddo/core/
│       │   ├── common/                          # 공통 예외·페이지 조회 모델·시각 처리
│       │   └── <도메인>/
│       │       ├── domain/                      # 도메인 객체·상태·비즈니스 규칙
│       │       ├── service/                     # 업무 흐름과 트랜잭션
│       │       ├── repository/                  # 조회·저장 인터페이스
│       │       └── exception/                   # 도메인 오류 코드(ErrorCode 구현)
│       └── test/                                # 도메인 규칙·Service 테스트
├── storage/                                     # 영속성 모듈의 경로 구분
│   └── db/                                      # MySQL 영속성 구현
│       ├── build.gradle                         # JPA·Flyway·MySQL 드라이버 의존성
│       └── src/
│           ├── main/
│           │   ├── java/com/getddo/db/
│           │   │   ├── common/                  # 공통 Entity·JPA Auditing 설정
│           │   │   └── <도메인>/
│           │   │       ├── entity/              # DB 테이블에 대응하는 JPA Entity
│           │   │       ├── repository/          # JpaRepository·core Repository 구현
│           │   │       └── mapper/              # Entity ↔ 도메인 객체 변환
│           │   └── resources/db/migration/      # 도메인별 Flyway SQL
│           ├── test/                            # DB 없는 영속성 관련 테스트
│           ├── integrationTest/                 # 실제 MySQL·마이그레이션 검증
│           └── testFixtures/                    # 공유 Testcontainers 설정
├── .github/                                     # GitHub 협업·자동화 설정
│   ├── pull_request_template.md                 # PR 작성 양식
│   └── workflows/                               # 빌드·테스트·PR 제목 검사
├── docs/                                        # 백엔드 API·DB·결정·작업 기록
├── gradle/wrapper/                              # Gradle 실행 버전 고정
├── compose.yaml                                 # 로컬 앱·MySQL 컨테이너 구성
├── Dockerfile                                   # 애플리케이션 이미지 빌드
├── .env.example                                 # 로컬 환경변수 예시
├── build.gradle                                 # 모듈 공통 빌드·테스트 설정
├── settings.gradle                              # 프로젝트 이름·모듈 등록
├── gradlew                                      # macOS·Linux·WSL Gradle 실행
└── gradlew.bat                                  # Windows Gradle 실행
```

#### 기능 추가 시 패키지 배치

새 기능의 HTTP DTO·Controller는 `api`, 업무 서비스·모델·Repository 계약은 `core`, Entity·저장 구현·Mapper는 `storage:db`의 같은 기능 패키지에 둡니다. 도메인 오류 코드는 `core/<도메인>/exception`에 배치하고 기존 공통 응답·페이지·시간 기능을 재사용합니다.

의존 방향은 `api → core`, `api → storage:db`, `storage:db → core`입니다. `api`만 실행 가능한 Spring Boot JAR을 만들고, 나머지 두 모듈은 라이브러리 JAR을 만듭니다. `core`는 API DTO나 JPA Entity에 의존하지 않습니다.

</details>

<details>
<summary>로컬 실행·테스트 명령과 환경 준비</summary>

#### 실행 방법

Docker Compose 실행에는 Docker가 필요합니다. 터미널에서 애플리케이션이나 Gradle 작업을 실행하려면 JDK 21도 준비합니다. Gradle은 저장소의 Wrapper를 사용합니다.

#### Docker Compose

저장소 루트에서 `.env.example`을 `.env`로 복사하고 `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`에 각각 로컬 비밀번호를 입력합니다. DB 이름과 일반 계정은 예시의 `getddo`를 사용합니다. `.env`는 Git과 Docker 빌드 컨텍스트에서 제외됩니다.

```bash
docker compose up --build -d
```

앱은 `http://localhost:8080`, MySQL 8.4는 `localhost:3306`에서 접근할 수 있습니다. Compose는 `local` Spring 프로필을 활성화하고, `application-local.yaml`에 정의된 MySQL JDBC 연결에 DB 환경변수를 전달합니다. MySQL 데이터는 Compose 볼륨에 유지됩니다. 종료할 때는 `docker compose down`을 사용합니다.

앱 시작 시 Flyway가 `storage:db/src/main/resources/db/migration/<도메인>/`의 SQL을 버전 순서대로 적용합니다. 마이그레이션이 추가될 때마다 버전 번호와 개수가 바뀌어 PR끼리 이 문단에서 충돌하므로 여기에는 적지 않습니다. 현재 목록은 해당 디렉터리를, 각 버전이 추가한 테이블·인덱스와 API 대응은 [API·DB 대응](docs/03-database/api-schema-mapping.md)을 확인합니다. 초기 스키마(44개 테이블)는 가장 앞의 마이그레이션 묶음이 생성하며, 이벤트의 논리 삭제 시각, 출석 기준일·사용자별 하루 1회 UNIQUE와 외래 키는 각 테이블의 초기 생성 정의에 포함되어 있습니다. Docker Desktop을 WSL에서 사용하는 경우 WSL 연동을 활성화해야 합니다.

#### 터미널

JDK 21을 준비하고 저장소 루트에서 실행합니다. 앱을 터미널에서 실행하려면 앞의 `.env` 준비 후 `docker compose up -d db`로 개발용 MySQL만 시작합니다. `bootRun`은 `.env`를 자동으로 읽지 않으므로 같은 DB 접속 정보를 환경변수로 지정합니다.

macOS / Linux / WSL:

```bash
export SPRING_PROFILES_ACTIVE=local
export DB_HOST=localhost DB_PORT=3306
export DB_NAME=getddo DB_USERNAME=getddo
export DB_PASSWORD='<.env에 설정한 DB_PASSWORD 값>'
./gradlew :api:bootRun
```

Windows PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_NAME = 'getddo'
$env:DB_USERNAME = 'getddo'
$env:DB_PASSWORD = '<.env에 설정한 DB_PASSWORD 값>'
.\gradlew.bat :api:bootRun
```

DB 이름이나 계정을 바꿨다면 환경변수도 `.env`와 맞춥니다.

#### 테스트 실행 방법

DB가 필요한 테스트는 Testcontainers가 실행하는 임시 MySQL 8.4를 사용합니다. 개발용 DB·`.env` 설정 없이 실행하며, 임의의 호스트 포트로 연결하고 테스트 종료 시 컨테이너를 정리합니다. H2는 사용하지 않습니다.

공통 설정은 `storage/db/src/testFixtures/java/com/getddo/db/support`에 있습니다. `MySqlTestContainers`는 호출마다 새 컨테이너를 만들고, `MySqlTestConfiguration`은 `@ServiceConnection`으로 Spring의 JDBC·Flyway 연결을 자동 설정합니다. Spring 테스트는 `test` 프로필을 사용하며 컨테이너 시작·종료는 Spring이 관리합니다. 이 설정은 통합 테스트에서만 사용하고 운영 실행 JAR에는 포함하지 않습니다.

API의 앱 기동·Swagger 테스트는 `api/src/integrationTest/java/com/getddo/api/support/ApiIntegrationTest.java`의 `@ApiIntegrationTest`를 사용해 동일한 Spring 컨텍스트와 MySQL 하나를 공유합니다. 공통 Entity 테스트는 테이블을 생성·삭제하는 별도 DB를 사용하고, 초기 SQL 테스트는 JUnit이 관리하는 별도 컨테이너의 빈 DB에서 실행합니다. 다른 모듈이나 서로 다른 Spring 컨텍스트까지 강제로 공유하지 않습니다.

통합 테스트 전에 Docker Desktop의 Linux 컨테이너 엔진을 실행합니다. WSL에서는 Settings → Resources → WSL Integration에서 사용하는 배포판을 켜고, `docker version`에 Client와 Server가 모두 표시되는지 확인합니다. 첫 실행에는 MySQL 이미지 다운로드가 필요합니다.

| 작업 | macOS / Linux / WSL | Windows PowerShell | Docker |
| --- | --- | --- | --- |
| DB 없는 테스트 | `./gradlew test` | `.\gradlew.bat test` | 불필요 |
| MySQL 통합 테스트 | `./gradlew integrationTest` | `.\gradlew.bat integrationTest` | 필요 |
| 초기 SQL·공통 Entity 검증 | `./gradlew :storage:db:integrationTest` | `.\gradlew.bat :storage:db:integrationTest` | 필요 |
| 앱 기동·Swagger 검증 | `./gradlew :api:integrationTest` | `.\gradlew.bat :api:integrationTest` | 필요 |
| 전체 빌드·테스트 | `./gradlew build` | `.\gradlew.bat build` | 필요 |
| 실행 JAR 패키징 | `./gradlew :api:bootJar` | `.\gradlew.bat :api:bootJar` | 불필요 |

초기 SQL 검증은 빈 MySQL에 Flyway 마이그레이션을 적용하고, 재실행 시 추가 적용이 없는지 확인합니다. Docker에 연결할 수 없으면 통합 테스트는 실패합니다.

GitHub Actions의 `빌드·테스트`는 러너의 Docker에서 같은 `./gradlew build`를 실행합니다. 별도 DB 비밀값 설정은 필요하지 않습니다. 실패한 테스트 보고서는 `test-reports` 아티팩트에서 확인할 수 있습니다.

`PR 제목 검사`는 [공용 PR 제목 규칙](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/01-conventions/pull-request.md)에 맞는지 별도로 확인합니다. PR 생성·수정·커밋 추가 시 실행하며, 제목·본문만 수정하면 Gradle 빌드는 실행하지 않습니다. 제목 오류로 병합을 막으려면 main/dev Ruleset의 필수 상태 검사에 `PR 제목 검사`를 등록합니다.

</details>

## 04. 프로젝트 수행 경과

### 주요 기능 구현과 서비스 시연

아래 순서로 화면과 API를 함께 확인합니다. 시연 준비 항목이며 프론트엔드 전체 흐름의 실행 완료 기록은 아닙니다.

| 시연 순서 | 사용자·관리자 행동 | 확인할 결과 |
| --- | --- | --- |
| 1. 출석·미션으로 응모권 획득 | 가상 사용자를 선택하고 출석·퀴즈·설문 수행 | 보상 지급·이력, 동일 요청의 중복 보상 방지 |
| 2. 이벤트 조회·응모·사용 내역 | 조건에 맞는 이벤트 선택 후 응모 | 접수 결과, 차감 수량·잔여 응모권·사용 이력의 일치 |
| 3. 관리자 이벤트·경품 관리 | 이벤트·경품 등록, 시작 전 수정·삭제, 별도 이벤트 취소 | 기간·상태 제한, 경품 ID 유지, 취소에 따른 반환·알림·감사 |
| 4. 가중치 추첨·결과 확인·공개 | 마감된 이벤트 자동 추첨 후 관리자 명단 확인·수동 공개 | 후보·가중치·확정 결과와 사용자 마스킹 결과. 최초 공개 연결은 후속 확인 |
| 5. 사이트 내 알림·운영 | 알림 목록·읽음 확인, 실패 작업 재처리 | 중복 생성 방지, 모의 발송 결과와 재처리 상태 |

#### 기능별 구현 방식과 사용 이유

| 기능 | 구현 방식·사용 이유 | 상세·테스트 근거 |
| --- | --- | --- |
| 사용자 문맥·관리자 사용자 조회 | DB 역할·멤버십 대조와 공통 사용자 문맥, 관리자 조회. 시연용 사용자 선택과 실제 인증을 구분 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#user) |
| 이벤트·경품 CRUD·취소·상태 전환 | 이벤트·경품 원자적 CRUD, 취소·반환·감사 연결, 시작/마감 전환과 실패 격리 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#event) |
| 출석·연속 출석·보상 | KST 출석·연속 단계·보상 청구·지급을 함께 확정해 중복 보상 방지 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#attendance) |
| 퀴즈·설문 조회·제출 | 퀴즈 진행·설문·제출 해시·완료 청구로 재전송과 중복 보상 제어 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#mission) |
| 게임 조회·시작·결과 검증 기반 | 조회·시작·규칙/토큰/seed 저장·검증기 기반. 결과 제출·보상 전체 연결은 후속 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#game) |
| 응모권 지급·사용·반환·만료·운영 조회 | 한 장의 현재 상태와 이력을 분리하고 원본 연결·잠금으로 중복 사용/반환 방지 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#ticket) |
| 응모 접수·거절 이력·현황 | 응모 PK 멱등·등급별 차감·마감 최종 검사·거절 이력으로 재전송과 경합 처리 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#entry) |
| 자동 추첨·재추첨·공개 기록·결과 조회 | 후보 스냅샷·동일 실행·공개 버전 분리로 추첨 근거와 공개 명단 보존. 최초 수동 공개 연결은 후속 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#drawing) |
| 알림 생성·모의 발송·읽음·복구 | 발생 키·사용자별 중복 방지, 생성/모의 발송 재시도와 읽음 분리 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#notification) |
| 공통 응답·시간·감사·요청 제한 | 공통 응답·시간·감사와 429 요청 제한. 공통 감사 조회 API는 제외 | [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#common) |

코드가 없는 후속 연결과 제외 범위는 [Wiki 구현 범위](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#remaining)에서 구분합니다. 공용 정책은 getddo-spec이 원본이며 이 문서에서 새로 확정하지 않습니다.

### 핵심 기술 과제와 해결 과정

| 기술 과제 | 대응한 방식과 이유 | 검증 근거 |
| --- | --- | --- |
| 중복 요청과 동시 처리 | 요청 식별자·DB UNIQUE·행 잠금을 조합하고 재시도 범위를 제한해 중복 접수·차감을 방지 | 응모 멱등성·동시 접수·마감 최종 검사 [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#entry) |
| 지급·차감·반환의 일관성 | 업무 기록과 응모권 변경·이력을 하나의 트랜잭션으로 확정. 반환은 원본 사용 이력과 연결 | 중복 지급·반환, 초과 차감, 실패 시 롤백 [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#ticket) |
| 추첨 후보·가중치·결과 보존 | 실행 시점 스냅샷과 확정 결과·공개 버전을 분리해 처리 근거와 과거 공개 명단을 보관 | 스냅샷·중복 선정·기존 당첨 유지·공개 결과 [Wiki](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#drawing) |
| 비동기 작업 재시도·실패 복구 | DB 선점·처리 소유권 만료·재시도를 관리하고 이벤트 상태 전환은 건별 트랜잭션으로 실패 격리 | 워커 경합·재시도·감사 실패 롤백 [알림](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#notification) · [이벤트](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Features#event) |

### 테스트 결과

2026-10-16 KST, dev `15f2d95c` 기준으로 전체 `test build`를 실행해 **BUILD SUCCESSFUL**을 확인했습니다.

| 구분 | 실행 수 | 결과 |
| --- | ---: | --- |
| DB 없는 단위·MVC·설정 검증 | 1,195 | 실패·오류·건너뜀 0 |
| Testcontainers MySQL 통합 검증 | 1,009 | 실패·오류·건너뜀 0 |
| **합계** | **2,204** | **성공** |

실제 잠금·중복 처리·마감 경계·롤백·공개 버전과 API 계약을 검증합니다. [Wiki 테스트 결과](https://github.com/Uplus-GETDDO/getddo-be/wiki/Mentoring-Tests)에 실행 명령·모듈/기능별 결과·원본 JSON/CSV를 정리했습니다. 같은 날 S3Mock에서 확인한 객체 입출력 17개는 이 집계와 별도입니다.

이 결과는 로컬 기능 검증이며 부하 처리량·실제 AWS 배포·FE 전체 흐름·S3/CloudFront 연동 완료를 뜻하지 않습니다. 후속 작업의 원격 CI 상태와 실제 연동 검증은 별도로 확인합니다.

### 현재 구현 현황과 남은 연동 과제

아래는 기준 dev에 병합된 코드로 확인한 현황입니다. 다른 작업 브랜치의 완료 여부까지 판단한 목록은 아닙니다.

| 영역 | 현재 확인한 내용 | 남은 연결·검증 |
| --- | --- | --- |
| 이벤트·경품 | CRUD·취소·시작/마감 전환·반환·감사 연동 | S3 업로드와 imageKey/imageUrl·프론트 이미지 표시 |
| 출석·미션·응모권·응모 | 주요 업무 서비스·API와 MySQL 검증 | 프론트 흐름을 연결한 종합 시연·운영 데이터 확인 |
| 추첨·발표 | 자동 추첨·재추첨·공개 버전 보존·사용자 결과 조회 | GD-199 최초 관리자 확인·수동 공개·PUBLISHED 연결 |
| 게임 | 조회·시작 API·결과 검증기 기반 | 결과 제출·보상 지급·통계의 전체 흐름 |
| 알림 | 생성·모의 발송·읽음·실패 복구 | 기능 간 알림과 프론트 표시의 종합 확인 |
| 배너·관리자 정책·지급 전 차단 | 공용 계약·저장 구조 | 담당 API·서비스 구현과 연동 현황 확인 |
| 인프라·이미지 | CI/CD 구성, 로컬 MySQL 및 S3Mock 입출력 확인 | 실제 AWS 권한·배포·CloudFront와 부하 검증 |
| ERD 자료 | 기존 이미지와 도메인별 페이지 보존 | 수정된 ERD 이미지로 교체 |

## 05. 자체 평가 의견

> 아래는 확인된 구현·검증을 바탕으로 한 평가 초안입니다. 최종 발표 전 팀의 의견과 실제 종합 시연 결과를 반영합니다.

### 프로젝트 목표 대비 달성도

| 목표 | 현재 평가 근거 | 추가 확인 |
| --- | --- | --- |
| 참여형 이벤트 흐름 | 출석·미션 보상, 응모·차감, 이벤트 관리와 결과 조회의 기반 구현·테스트 | 게임·최초 공개·이미지 연동과 프론트 전체 시연 |
| 응모·응모권의 일관성 | 중복·동시성·롤백·원본 이력 연결을 실제 MySQL에서 검증 | 운영 규모 데이터와 부하 상황 |
| 추첨 신뢰성 | 후보·가중치·조건·결과·공개 버전을 저장하고 관련 회귀 테스트 수행 | 실제 관리자 확인·공개 흐름의 종합 검증 |
| 운영·복구 | 알림·추첨 재시도, 이벤트별 실패 격리와 감사 기록 | 실제 배포 환경의 장애·복구 시나리오 |

기능 테스트 성공만으로 전체 서비스가 완성됐다고 평가하지 않고, 남은 연동을 별도로 관리합니다.

### 설계·구현·협업에서 잘한 점

- 현재 상태와 처리 이력, 추첨 실행과 결과 공개를 분리해 데이터의 근거를 보존했습니다.
- 멀티모듈의 의존 방향과 기능별 패키지·담당 경계를 두고 공통 응모권·알림·감사 서비스를 재사용했습니다.
- 코드 리뷰에서 발견한 시간 경계·잠금·중복 검증 문제를 회귀 테스트와 함께 보완했습니다. MySQL을 사용해 실제 제약·트랜잭션 동작을 확인했습니다.
- Jira 태스크, PR·CI, 공용 정책과 워크로그를 연결해 변경 이유와 검증 결과를 공유했습니다.

### 개발 과정에서의 어려움과 아쉬운 점

- 정책·ERD 변경에 맞춰 API·코드·테스트·문서를 함께 정리해야 했고, 과거 상태값과 담당 경계가 남아 있으면 판단이 어려웠습니다.
- 여러 기능이 같은 이벤트·응모권·알림을 다루면서 잠금 순서, 트랜잭션 범위와 실패 복구 계약을 조율해야 했습니다.
- 기능별 테스트가 있어도 프론트·이미지·실제 배포까지 이어지는 검증은 별개였습니다. 로컬 검증과 서비스 완료 상태를 구분할 필요가 있습니다.
- 테스트가 늘어나면서 실행 시간과 중복 검증의 관리가 필요해졌습니다. 핵심 동시성·롤백 검증을 보존하면서 정리할 범위를 검토합니다.

### 개선 방향과 향후 확장 계획

| 우선순위 | 방향 |
| --- | --- |
| 현재 범위 마무리 | 이미지·게임·최초 공개·관리자 기능의 남은 연결을 확인하고 전체 시연 수행 |
| 검증·운영 보완 | 실제 AWS 배포·권한·복구·부하를 확인하고 측정 결과에 따라 인덱스·워커·테스트 실행 비용 개선 |
| 자료 정합성 | 최신 ERD 이미지 교체와 API·정책·구현 문서의 기준 일치 |
| 향후 검토 | 실제 인증·외부 알림·분산 요청 제한 등은 별도 요구사항과 운영 규모를 정한 뒤 검토 |

향후 검토 항목은 이번 구현 범위에 자동으로 추가하지 않습니다.

### 팀원별 배운 점

개인의 경험은 각 팀원이 직접 확인해 작성합니다. 아래 질문은 발표 준비를 위한 작성 안내입니다.

| 팀원 | 돌아볼 주제 | 배운 점 |
| --- | --- | --- |
| 이경주 | 공통 기반·실행 환경·CI/CD에서 가장 중요했던 선택 | 팀원 작성 예정 |
| 석종수 | 후보·가중치·결과 보존과 재시도에서 배운 점 | 팀원 작성 예정 |
| 전민규 | 비동기 알림·게임·운영 기능을 연결하며 배운 점 | 팀원 작성 예정 |
| 윤태형 | 보상·응모권·응모의 중복·동시성·롤백을 다루며 배운 점 | 팀원 작성 예정 |
| 박지훈 | 이벤트 생명주기와 다른 기능의 트랜잭션을 연결하며 배운 점 | 팀원 작성 예정 |
