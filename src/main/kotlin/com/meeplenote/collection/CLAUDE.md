# collection 모듈 — 구현 노트

## 상태

- **M3 완료** (커밋 `258870b`, PR #4): `PUT/DELETE /api/v1/collections/{gameId}` — 보유/위시 2상태 추가·제거뿐.
- **M4 완료** (PR #5, #6): `GET /api/v1/collections?status=&sort=`, 응답에 `playCount`/`lastPlayedAt`/`isNoPlay`.
- **S4 완료** (PR #10/#11): `?players=&maxPlaytime=` 필터, `games.min/max_players/playtime` 활용. 값 없는 커스텀 게임은 필터 활성화 시 결과에서 제외.

## 활성 규칙 (되돌린 이력 있음 — 다시 바꾸지 말 것)

- `recordPlay`는 **상태 무관하게 항상** `playCount`/`lastPlayedAt`을 갱신한다. `status == OWNED` 게이팅은 PR #5에서 한 번 들어갔다가 PR #6에서 사용자 피드백으로 되돌려졌다 — 위시 게임도 플레이될 수 있고 그 통계가 목록 응답에 노출돼야 한다는 제품 판단. **`isNoPlay`(노플 배지)만 여전히 `OWNED` 한정**으로 계산.
- `collection`은 `play.api`를 참조할 수 없다(역참조 시 `ModuleBoundaryTest.noCyclesBetweenModules` 위반, play→collection 방향이 이미 존재). 플레이 통계는 `collections.play_count`/`last_played_at` 캐시 컬럼(V3)에 `play` 모듈이 `CollectionPlayTracker.recordPlay()`로 밀어넣는 단방향 구조 — collection 쪽에서 캐시를 없애고 직접 조회하는 방향으로 "리팩토링"하지 말 것.

## 알려진 한계 (의도적으로 남김)

컬렉션 추가 이전에 이미 기록된 플레이는 `playCount`에 소급 반영되지 않는다 — 위 순환 의존 제약상 백필 구조를 만들 수 없고, US-2.2 스펙도 "보유 시점 이후" 플레이만 대상이라 현재 동작(추가 시 0부터 시작)이 스펙과 일치한다고 판단해 별도 이슈로 안 남김.
