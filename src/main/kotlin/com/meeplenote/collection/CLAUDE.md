# collection 모듈 — 구현 노트

## 상태

- **M3 완료** (커밋 `258870b`, PR #4): `PUT/DELETE /api/v1/collections/{gameId}` — 보유/위시 2상태 추가·제거뿐.
- **M4 완료** (PR #5, #6): `GET /api/v1/collections?status=&sort=`, 응답에 `playCount`/`lastPlayedAt`/`isNoPlay`.
- **S4 완료** (PR #10/#11): `?players=&maxPlaytime=` 필터, `games.min/max_players/playtime` 활용. 값 없는 커스텀 게임은 필터 활성화 시 결과에서 제외.

## 활성 규칙 (되돌린 이력 있음 — 다시 바꾸지 말 것)

- `recordPlay`는 상태 무관 항상 `playCount`/`lastPlayedAt` 갱신. `status == OWNED` 게이팅은 PR #5에서 도입 후 PR #6에서 사용자 피드백으로 원복 — 위시 게임도 플레이 가능, 그 통계가 목록 응답에 노출 필요하다는 제품 판단. `isNoPlay`(노플 배지)만 여전히 `OWNED` 한정 계산.
- `collection`은 `play.api` 참조 불가(역참조 시 `ModuleBoundaryTest.noCyclesBetweenModules` 위반, play→collection 방향 기존). 플레이 통계는 `collections.play_count`/`last_played_at` 캐시 컬럼(V3)에 `play` 모듈이 `CollectionPlayTracker.recordPlay()`로 반영하는 단방향 구조 — 캐시 없애고 직접 조회하는 방향 리팩토링 금지.

## 알려진 한계 (의도적으로 남김)

컬렉션 추가 이전 플레이 이력은 `playCount`에 소급 미반영 — 순환 의존 제약상 백필 구조 불가. US-2.2 스펙도 "보유 시점 이후" 플레이만 대상이라 현재 동작(추가 시 0부터 시작)이 스펙과 일치, 별도 이슈로 미등록.
