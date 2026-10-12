# API 명세 — 응모권 · 일회성 미션 · 게임

담당: 윤태형. 공통 응답 포맷(`ResponseEnvelope`), 헤더(`X-Virtual-User-Id`/`X-Operator-Id`), 오류 코드 체계는 공통 규약 문서를 따른다(별도 작성 필요 — 이 문서는 해당 규약이 이미 있다고 가정한다).

## 응모권

| Method | Path | 설명 | 주요 오류 |
|---|---|---|---|
| GET | `/api/me/tickets/balance` | 보유 응모권(전역 단일 잔액) 조회 | - |
| GET | `/api/me/tickets/ledger?cursor=&size=&eventId=` | 적립/사용 내역(커서 페이지네이션). `eventId`는 조회 필터일 뿐 잔액 자체는 항상 하나 | - |
| GET | `/api/me/tickets/summary` | 총적립/총사용 요약 | - |
| GET | `/api/admin/audit/ledger-consistency` | 전체 유저 3중 대조 결과(운영자용) | - |

```
GET /api/me/tickets/balance
X-Virtual-User-Id: 7

200 { "data": { "balance": 24 } }
```

```
GET /api/me/tickets/ledger?size=20
X-Virtual-User-Id: 7

200 { "data": { "items": [
        { "id": 2482, "delta": 1, "balanceAfter": 24,
          "reasonCode": "MISSION", "refType": "MISSION", "refId": 5,
          "createdAt": "2026-09-10T09:00:00+09:00" }
      ], "nextCursor": "eyJpZCI6MjQ3MH0=", "hasNext": true } }
```

## 미션 (출석·게임 제외 5종)

| Method | Path | 설명 | 주요 오류 |
|---|---|---|---|
| GET | `/api/missions?eventId=` | 플랫폼 전체 미션 + 지정 시 해당 이벤트 소속 미션 목록(내 수행 상태 포함) | - |
| POST | `/api/missions/{missionId}/complete` | 행동/설문/기업연동 수행 → 전역 응모권 적립. `QUIZ`는 `answerIndex` 필수, `SURVEY`는 `responses` 필수, `EXTERNAL_VERIFY`는 body 없이 즉시 Mock 조회 | `409 MISSION_ALREADY_COMPLETED`, `400 QUIZ_ANSWER_INCORRECT`, `400 EXTERNAL_ACHIEVEMENT_NOT_MET` |
| GET | `/api/admin/missions` | 미션 목록(운영자) | - |
| POST | `/api/admin/missions` | 미션 등록 | - |
| PATCH | `/api/admin/missions/{id}` | 미션 수정/활성화 토글 | - |
| GET | `/api/admin/missions/{id}/completions?page=` | 미션별 지급 내역 | - |

```
GET /api/missions?eventId=12
X-Virtual-User-Id: 7

200 { "data": { "myTicketBalance": 16,
      "platformMissions": [
        { "missionId": 21, "eventId": null, "code": "COMPLETE_PROFILE", "title": "프로필 완성",
          "missionType": "ACTION", "repeatCycle": "ONCE", "rewardTickets": 5, "completed": true }
      ],
      "eventMissions": [
        { "missionId": 5, "eventId": 12, "code": "BRAND_QUIZ", "title": "브랜드 퀴즈",
          "missionType": "QUIZ", "repeatCycle": "DAILY", "rewardTickets": 3, "completed": false,
          "quizQuestion": "이 상품의 원산지는?", "quizChoices": ["국내산", "미국산", "호주산"] }
      ] } }
```

```
POST /api/missions/5/complete
X-Virtual-User-Id: 7
{ "answerIndex": 0 }

200 { "data": { "missionId": 5, "missionTitle": "브랜드 퀴즈", "eventId": 12,
                "grantedAmount": 3, "ticketBalanceAfter": 16,
                "businessDate": "2026-09-10", "ticketLedgerId": 2483 } }

400 { "success": false, "code": "MISSION-003",
      "message": "정답이 아니에요. 다시 시도해보세요.", "data": null }   // 완료 이력 미생성 → 재시도 가능
```

> 출석·응모(Entry 제출)는 이 API 목록에 없다 — 각각 다른 팀원 담당 API를 쓴다.

## 게임

| Method | Path | 설명 | 주요 오류 |
|---|---|---|---|
| GET | `/api/games` | 전체 게임 목록(순환 없이 항상 전체 공개) + 내 최고점·누적점수 | - |
| POST | `/api/games/{gameId}/play` | 게임 플레이 제출. 점수·성공 여부는 서버가 판정 — [추가 검토 필요] 게임별 요청 바디 형식·성공 기준은 게임 종류 확정 후 정의 | `404 GAME_NOT_FOUND` |
| GET | `/api/me/games/{gameId}/stat` | 내 최고점·누적 점수·플레이 횟수 | - |

```
GET /api/games
X-Virtual-User-Id: 7

200 { "data": [
        { "gameId": 1, "code": "TAKOYAKI", "title": "타코야끼 만들기",   // 후보 예시, 게임 종류 미확정
          "myBestScore": 8200, "myCumulativeScore": 41300, "myPlayCount": 12 }
      ] }
```

```
POST /api/games/1/play
X-Virtual-User-Id: 7
{ }   // 게임별 원시 플레이 데이터 형식 미정

200 { "data": { "gameId": 1, "score": 8500, "success": true,
                "bestScore": 8500, "cumulativeScore": 49800, "playCount": 13,
                "ticketGranted": true } }   // success=true였고 오늘 아직 못 받았으므로 지급

200 { "data": { "gameId": 1, "score": 3200, "success": false,
                "bestScore": 8500, "cumulativeScore": 53000, "playCount": 14,
                "ticketGranted": false } }   // success=false라서 지급 시도 자체를 안 함
```

> `ticketGranted`는 boolean이지 수량이 아니다 — 성공해도 항상 1장, 실패하면 0장뿐이다(차등 지급 없음).

## 오류 코드 (이 도메인)

| 코드 | 상태 | 설명 |
|---|---|---|
| `MISSION-001` | 404 | 참여할 수 없는 미션입니다 |
| `MISSION-002` | 409 | 오늘은 이미 참여했어요 |
| `MISSION-003` | 400 | 정답이 아니에요. 다시 시도해보세요 |
| `MISSION-004` | 400 | 아직 조건을 달성하지 못했어요 |
| `GAME_NOT_FOUND` | 404 | 존재하지 않는 게임입니다 |
