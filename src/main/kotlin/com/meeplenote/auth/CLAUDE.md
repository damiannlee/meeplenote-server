# auth 모듈 — 구현 노트

## 상태

- **M7 완료** (커밋 `5a41925`): 카카오 로그인 + JWT. 상세는 ADR-007.
- **`auth.api.CurrentUserProvider`** 도입 완료(커밋 `29b5ea5`, M2 시점) — 유저 스코프 필요한 모듈은 `SecurityContext` 직접 접근 대신 이걸 주입받을 것(루트 `CLAUDE.md` §8). 현재 collection/dataimport/export/game/play/stats 컨트롤러가 전부 사용 중.
