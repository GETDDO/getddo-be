# 기본 개발 흐름

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

