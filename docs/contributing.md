# 협업 규칙

- 모든 작업은 **브랜치 기반으로 진행**하며 `main` 브랜치에 직접 push하지 않는다.
- 작업 시작 전 **최신 `dev` 브랜치를 pull**한다.
- 기능 단위로 작업을 나누고 **PR을 통해 코드 리뷰 후 병합**한다.
- 기술적 의견이 다를 경우 **근거 기반으로 논의 후 다수결로 결정**한다.

## 1. 브랜치 전략

프론트엔드 / 백엔드 Repository는 분리해서 관리하며, 각 Repository는 동일한 브랜치 전략을 따른다.

```text
main         배포/시연 가능한 안정 버전
 └ dev       개발 통합 브랜치
     ├ feat/*       기능 개발
     ├ fix/*        버그 수정
     ├ refactor/*   리팩터링
     ├ chore/*      빌드·설정·환경 작업
     └ docs/*       문서 작업
```

### 권장 문서 구조

```text
reward-project/
├── docs/
│   ├── requirements.md       요구사항·우선순위
│   ├── domain-rules.md       출석·응모권·추첨 규칙
│   ├── api/                  API 명세
│   ├── database/             ERD·설명
│   ├── decisions/            회의에서 결정한 내용과 이유
│   └── runbook.md            배포·장애 확인·복구 방법
├── .github/                  작업·PR 양식, 자동 검사
└── README.md                 실행 방법·주요 문서 링크
```

### 브랜치 역할

**`main`**

- 항상 실행 및 시연 가능한 안정 상태를 유지한다.
- 직접 push하지 않는다.
- `dev`에서 충분히 검증된 코드만 PR을 통해 merge한다.

**`dev`**

- 팀의 개발 결과물이 모이는 통합 브랜치이다.
- 직접 push하지 않는다.
- 모든 개발 작업은 작업 브랜치에서 수행한 뒤 PR을 통해 merge한다.

**작업 브랜치**

- 작업 하나를 기준으로 브랜치 하나를 생성한다.
- `dev`에서 분기한다.
- 개발 완료 후 `dev`를 대상으로 PR을 생성한다.

## 2. 브랜치 이름 규칙

Jira를 사용하므로 **Jira 이슈 키를 브랜치명에 포함한다.**

```text
<타입>/<Jira 이슈 키>
```

예시:

```text
feat/GD-21
feat/GD-32
fix/GD-45
refactor/GD-53
chore/GD-13
docs/GD-61
```

### 타입

| 타입 | 용도 |
| --- | --- |
| `feat` | 새로운 기능 개발 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변화 없는 코드 개선 |
| `chore` | 빌드, CI, Docker, 설정, 패키지 등 |
| `docs` | 문서 관련 작업 |

### 규칙

- 형식은 `타입/Jira키`이다.
- 브랜치 타입은 소문자를 사용한다.
- Jira 이슈 키는 대문자로 유지한다.
- 브랜치명에는 공백을 사용하지 않는다.

브랜치는 Jira 이슈의 **GitHub에서 브랜치 만들기** 기능을 이용하여 생성하는 것을 권장한다.

생성 시 다음 설정을 사용한다.

```text
Repository: 작업할 Repository
Branch from: dev
Branch name: 위 컨벤션에 맞는 이름
```

## 3. 기본 개발 흐름

모든 개발 작업은 다음 흐름을 따른다.

```text
Jira 이슈 생성
      ↓
Jira에서 GitHub 브랜치 생성 (dev 기준)
      ↓
로컬에서 작업
      ↓
Commit
      ↓
Push
      ↓
Pull Request → dev
      ↓
CI Build / Test
      ↓
Code Review
      ↓
Squash and Merge
      ↓
dev
```

예시:

```text
Jira: GD-21 쿠폰 발급 API 구현
      ↓
GitHub Branch: feat/GD-21
      ↓
Pull Request: feat/GD-21 → dev
      ↓
CI 성공
      ↓
1명 이상 Approve
      ↓
Squash and Merge
```

## 4. Code Style

코드 스타일 통일을 위해 다음 규칙을 따른다.

- **Prettier 사용**
- **ESLint 적용**
- 세미콜론 사용
- 싱글 쿼트 사용
- 들여쓰기 4칸

## 5. Commit

커밋 메시지는 아래 형식을 따른다.

```text
type: 작업 내용
```

### 타입 목록

| 타입 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 (코드 변경 없음) |
| `style` | 코드 포맷팅, 세미콜론 등 스타일 변경 (논리 변경 없음) |
| `refactor` | 리팩터링 (기능 변화 없음) |
| `test` | 테스트 관련 코드 추가/수정 |
| `chore` | 빌드, 패키지 매니저 설정 등 기타 작업 |
| `design` | CSS 등 사용자 UI 디자인 변경 |
| `comment` | 필요한 주석 추가 및 변경 |
| `rename` | 파일 혹은 폴더명을 수정하거나 옮기는 작업만 수행 |
| `remove` | 파일을 삭제하는 작업만 수행 |
| `!HOTFIX` | 급하게 치명적인 버그를 고치는 경우 |

## 6. Pull Request

### PR 생성 기준

- 기능 단위 작업 완료 시 PR을 생성한다.
- `dev` 브랜치를 대상으로 PR을 생성한다.

### PR 제목

```text
[타입] #이슈번호 제목
```

예시:

```text
[feat] #10 방 생성 API 구현
```

이슈 번호를 포함하면 자동 링크가 걸려 편리하다.

### PR 내용

```markdown
## 📌 관련 이슈
- close #이슈번호

## ✨ 작업 내용
<!-- 어떤 변경 사항이 있었는지 주요 내용을 적어주세요. -->
- Room 엔티티와 Participant 엔티티 간 1:N 관계 설정
- 방 생성 시 고유한 6자리 URL Slug 생성 로직 추가
- NestJS ValidationPipe를 이용한 입력값 검증 적용

## 📸 스크린샷 / 테스트 결과
<!-- API 응답 결과(Postman/Swagger)나 실행 결과 스크린샷을 첨부해주세요. -->
- `POST /rooms` 성공 응답 확인 완료

## 🔍 리뷰 포인트
<!-- 리뷰어가 집중해서 봐주었으면 하는 부분이 있다면 적어주세요. -->
- 슬러그 생성 알고리즘이 중복을 충분히 방지할 수 있을까요?
- Prisma 트랜잭션 처리가 적절하게 되었는지 확인 부탁드립니다.

## ✅ 체크리스트
- [ ] 커밋 메시지 컨벤션을 준수했는가?
- [ ] 로컬에서 빌드 및 테스트가 성공했는가?
- [ ] 불필요한 주석이나 console.log를 제거했는가?
```

### PR 리뷰 규칙

- 최소 **1명 이상 리뷰 후 merge**한다.
- 리뷰 코멘트를 반영한 후 merge한다.
