# GD-157 색깔 게임 서버 판정

- 상태: 검토 대기 — Codex 작성, 담당자 확인 전
- 선행: 병합된 GD-170 `GamePlay`, 이번 PR로 분리한 공용 `ColorWordRules`.
- 기준: [채택 색깔 계약](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/05-api/game-color-word-draft.md), [게임 규칙](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/02-domain/game.md).
- 2026-10-14 BE/spec fetch 확인. 미커밋 변경은 보존하고 pull하지 않았다.

## 구현과 적용 기준 대조

| 코드 | 이유·적용 기준 | 검증 준비 |
| --- | --- | --- |
| ColorWordColor | 채택된 red/blue/yellow/green 추출 순서 | 고정 CSV |
| ColorWordRound/Generator | 단어와 정답색 분리, xorshift32 unsigned 연산, FE의 난수 소비 순서와 10회 재추출·최종 대체 | 공유 예제·상위 비트 seed·반복/분기 테스트 |
| ColorWordAnswer | 번호·색(null=시간초과)·활성 경과시간만 받음 | 입력 순서·시간 테스트 |
| ColorWordValidationResult | 유효 0점과 무효 null 점수 구분 | 0/40/50점 테스트 |
| ColorWordValidator | 발급 당시 스냅샷·seed로 판정. DB/지급/HTTP 의존 없음 | 제한 시간·자연 종료·상태 비변경 테스트 |

판정기는 문제 번호의 누락/중복/역순, 음수 시간, 세 번째 실패 뒤 추가 기록, 목숨이 남은 불완전 종료를 무효로 본다. 정답/오답 클릭은 `0 <= elapsedMs < limit`이며, timeout은 null 색과 정확한 해당 제한 시간으로 정규화된 기록만 인정한다. 정상 오답/timeout은 목숨만 줄이고 정상 기록으로 취급한다. 정답 수가 늘 때만 제한 시간을 줄이며 최소값과 배점은 저장된 규칙에서 읽는다. long 산술로 정답 수 증가에 따른 int overflow를 피한다.

손상된 저장 규칙·미지원 버전·잘못된 저장 seed는 예외를 전파한다. 서버 설정/장애를 사용자 INVALID로 바꿔 저장하지 않는다. 매 호출마다 새 생성기를 만들어 플레이 간 상태가 섞이지 않는다.

## GD-159 호출 경계와 미결정 사항

`ColorWordValidator.validate(play, answers)` 호출 전 본인/게임/토큰을 확인하고 같은 입력의 기존 완료 결과를 먼저 반환해야 한다. JSON 필수 필드·숫자·enum 형식, answer 속성 생략과 명시적 null의 구분은 GD-159 HTTP 경계가 담당한다. ColorWordColor는 core enum이며 공개 DTO가 아니다. API는 소문자 색을 명시적으로 변환하고 알 수 없는 값을 400으로 처리해야 한다.

중간에 나가기는 제출하지 않고 미완료 플레이로 남긴다. 판정기 호출만으로 결과 API·멱등 저장·통계·보상·접근 제어가 완성된 것은 아니다. 이 코드는 보상 여부를 판정하거나 지급하지 않는다. 토큰 수명/요청 한도/전체 활성 시간 대비 서버 경과 허용 오차는 공용 후속이며 임의 숫자를 추가하지 않았다. 공개 seed로 실제 사람이 플레이했는지 증명하지 못한다는 채택 계약의 한계도 유지한다.

## 검증 상태와 사용자 실행

회귀 테스트와 Java/JavaScript 공용 CSV 예제를 작성했다. 사용자가 GD-157 단위 테스트 두 클래스와 Node 공유 예제 검사의 성공을 확인했다. 파서 테스트·전체 테스트·전체 빌드의 최종 성공은 확인 전이다. 최신 dev로 분리한 브랜치는 GitHub CI로 별도 확인한다. 실제 FE 앱 반영·연동과 Java/FE의 실행 결과 일치 확인을 완료로 표시하지 않는다. 공용 원문이나 다른 담당 서비스는 수정하지 않았다.

```bash
./gradlew :core:test --tests 'com.getddo.core.game.domain.ColorWordRoundGeneratorTest' --tests 'com.getddo.core.game.service.ColorWordValidatorTest'
node core/src/test/resources/game/check-color-word-vectors.mjs
./gradlew test
./gradlew build
```

Node 검사는 명세가 참조한 FE 순수 생성 함수와 xorshift32로 같은 CSV를 검증한다. 최신 FE 앱에 seed/시간 수집이 반영됐다는 증거는 아니다.

실제 수행 검증: 최초 격리 javac에서 생성기 블록 닫힘 누락을 수정한 뒤, 신규 운영/테스트와 기존 의존 소스의 격리 javac 컴파일 exit 0을 확인했다. 캐시 의존성을 사용한 소스 수준 확인이며 Gradle 전체 컴파일/테스트/빌드 성공을 대신하지 않는다. 새 파일 11개의 공백·복사 일치와 문서 코드블록을 확인했다.

GD-157 독립 PR에는 GD-156의 규칙 파서·파서 테스트·기존 BOM의 Jackson 의존성만 선행 기반으로 포함한다. G03 Controller·시작 서비스·토큰 발급 API는 포함하지 않는다. 원래 GD-156 작업 폴더는 보존했으므로 이후 GD-156 PR 준비 시 중복 파일을 dev 기준으로 정리한다.

PR 준비 검증: 최신 dev 3e51a2e에서 분리한 feat/GD-157의 `./gradlew :core:compileTestJava --offline`이 BUILD SUCCESSFUL(11초)로 통과했다. 테스트/Node 검사는 사용자가 기존 게임 폴더에서 성공을 확인했으며 전체 test/build와 분리 브랜치의 CI는 별도 확인 대상이다.
