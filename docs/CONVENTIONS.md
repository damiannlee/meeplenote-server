# 개발 컨벤션 — 브랜치 전략 & 커밋 규칙

> 판단 기준: 1인 개발 + Claude Code 협업. 팀 규모용 프로세스(GitFlow의 develop/release 브랜치, 리뷰어 지정 등)는 의도적으로 배제한다. 프로세스도 아키텍처와 같은 원칙 — 규모에 맞게.

## 1. 브랜치 전략: GitHub Flow (GitFlow 기각)

```
main ────●────●────●────●──▶   (항상 배포 가능 상태, 보호 브랜치)
          \        /
           feat/us-1.1-quick-log ──▶ PR ──▶ CI 통과 ──▶ squash merge
```

규칙:
- `main`은 항상 빌드·테스트가 통과하는 상태. 직접 푸시 금지 (브랜치 보호 설정).
- 작업 단위 = 유저 스토리 또는 그 하위 태스크. 브랜치 하나 = PR 하나 = 스토리 하나.
- 브랜치 이름: `feat/us-1.1-quick-log`, `fix/import-duplicate`, `chore/ci-cache` 형식.
- 머지는 **squash merge** — 1인 개발에서 중간 커밋(WIP, 오타 수정)은 히스토리 가치가 없고, main 히스토리가 "스토리 단위"로 남는 것이 포트폴리오 가독성에 유리.
- PR은 셀프 머지하되, 머지 전 게이트 2개: ① CI 통과, ② Claude Code 코드 리뷰 (로드맵 6단계 사이클의 "이 코드 리뷰해줘" 단계를 PR 시점에 수행).

GitFlow 기각 이유: develop/release/hotfix 브랜치는 "여러 명이 동시에 여러 릴리스를 준비"할 때의 도구다. 1인 + 지속 배포 상황에서는 관리 비용만 남는다.

## 2. 커밋 컨벤션: Conventional Commits

형식: `type(scope): 요약` — 요약은 한글 허용, 50자 이내 지향.

| type | 용도 |
|---|---|
| `feat` | 기능 추가 (유저 스토리 구현) |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 구조 개선 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 (ADR, README 등) |
| `chore` | 빌드, 의존성, 설정 |
| `ci` | CI/CD 파이프라인 |
| `perf` | 성능 개선 (측정 근거를 본문에 남길 것) |

scope는 모듈명을 사용: `auth`, `game`, `collection`, `play`, `stats`, `dataimport`, `infra`, `db`

예시:
```
feat(play): 10초 기록 저장 API 구현 (US-1.1)
fix(dataimport): BG Stats 중복 병합 시 플레이어 집합 비교 누락 수정
docs(adr): ADR-009 모듈 경계 강제 방식 추가
chore(db): V2 마이그레이션 — games 인기도 컬럼 추가
```

본문 규칙 (선택이지만 권장):
- **왜**를 적는다. 무엇을 했는지는 diff가 말해준다.
- 유저 스토리 번호(US-x.x)나 ADR 번호를 참조해 문서와 커밋을 연결한다 — 면접에서 "이 결정이 코드 어디에 반영됐나"를 추적 가능하게.
- **개조식으로 작성한다** — 완전한 문장(~다/~했다) 대신 명사형 종결의 항목 나열. **PR 본문도 동일하게 개조식으로 작성한다.**

## 3. 마이그레이션 규칙 (Flyway)

- 파일명: `V{N}__{설명}.sql` (예: `V2__add_game_popularity.sql`)
- **한 번 main에 머지된 마이그레이션은 수정 금지** — 수정이 필요하면 다음 버전으로 새 마이그레이션.
- 스키마의 원장은 Flyway. JPA `ddl-auto`는 영원히 `validate`.
- 파괴적 변경(컬럼 삭제 등)은 2단계로: N에서 사용 중단 → N+1(다음 배포 이후)에서 삭제. 롤백 여지 확보.
