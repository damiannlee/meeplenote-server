# stats 모듈 — 구현 노트

## 상태

- **M5 완료** (커밋 `d9455b9`, PR #7): `GET /api/v1/stats/summary` — 총 플레이 수, 월별 추이, 최다 플레이 top5, 노플 게임 수.

## 설계 결정 (여기가 단일 출처 — 재검토 없이 "캐시 재사용으로 리팩토링"하지 말 것)

- `collections.play_count`/`last_played_at` 캐시 재사용 안 함 — 컬렉션 미등록 게임의 플레이가 캐시에서 누락(`collection/CLAUDE.md`의 "알려진 한계"). 대신 `plays` 테이블 직접 GROUP BY 집계(`play.api.PlayStatsProvider`). `noPlayCount`만 M4 규칙(`OWNED && playCount==0`)을 `collection.api.CollectionLookup.countNoPlay()`로 재사용.
- `monthlyTrend`는 쿼리 파라미터 없이 항상 최근 12개월 롤링으로 확정 — 설계문서의 `?year=` 예시와 기능명세의 "최근 12개월" 인수조건 불일치, 사용자 확인 후 결정(2026-07-13).
