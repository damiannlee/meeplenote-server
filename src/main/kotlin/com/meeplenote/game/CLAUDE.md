# game 모듈 — 구현 노트

## 상태

- **M2 완료** (커밋 `29b5ea5`, PR #2): 한글 게임 검색(초성/한글/영문, ADR-004) — 로컬 DB 대상 + 커스텀 게임 등록.
- **M8 보류**: BGG XML API2가 Cloudflare 봇 차단으로 401 반환, 무인증 온디맨드 조회 불가. 상세·재개 시 확장할 캐시 필드 범위는 **ADR-003을 단일 출처로 참조** (여기서 재서술하지 않음). 코드는 `feat/m8-bgg-cache` 브랜치에 있으나 `main`(M2가 이후 별도 재구현되며 갈라짐)에서 크게 뒤처져 재개 시 리베이스 필요.

## `game.api.GameLookup` (공개 인터페이스 — 시그니처 변경 시 소비처 많으니 영향 범위 확인)

게임 존재 검증(M1), `getSummaries()`(배치, N+1 방지 — M4/M5/M6/export), `GameSummary.thumbnailUrl`, `findByBggId`/`findCandidatesByName`(M6 임포트 매칭).
