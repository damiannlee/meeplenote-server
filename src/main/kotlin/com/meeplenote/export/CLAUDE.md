# export 모듈 — 구현 노트

## 상태

- **US-4.2 완료** (커밋 `2874236`, PR #9): `GET /api/v1/exports`. JSON 전용, 스트리밍 아닌 일반 응답으로 확정(기능명세 "JSON/CSV"와 설계문서 "JSON 스트리밍" 불일치 → 사용자 확인 후 결정, ADR-002 정신과 일치 — 트래픽 규모에서 스트리밍은 과설계).
- 신규 모듈로 분리한 이유: ADR-001의 6개 모듈 목록은 모듈 개수 고정 조건이 아니라 "MSA 재검토" 트리거일 뿐이라 ADR 뒤집기 아님. `dataimport`(비동기 잡 처리)와 성격이 달라 별도 모듈로 뺌.
- `export → play.api/collection.api/game.api/auth.api` 단방향 의존만.

## 제외 범위

CSV 포맷(별도 스토리로 남김).
