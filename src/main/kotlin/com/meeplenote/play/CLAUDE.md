# play 모듈 — 구현 노트

## 상태

- **M1 완료** (커밋 `094d029`, PR #3): `POST /api/v1/plays` — gameId만 필수. 범위는 생성 API 하나뿐(목록/PATCH/DELETE 없음).
- **목록 조회 추가**: `GET /api/v1/plays?cursor=&limit=` — `(played_at, id)` 복합 키셋 커서, opaque Base64 인코딩(`PlayService.encodeCursor`/`decodeCursor`). S2(공유 카드) 히스토리 공유 요구로 `04_system_design.md`에 있었지만 M1에서 미룬 스코프를 이번에 구현. `gameId`/`from`/`to` 필터·PATCH·DELETE는 여전히 스코프 밖. 응답 아이템에 `gameName`/`thumbnailUrl`을 `GameLookup.getSummaries()` 배치 조회로 포함 — 클라이언트가 이 응답만으로 공유 카드를 그릴 수 있음(별도 카드 API 없음).
- **S6 캘린더**: `GET /api/v1/plays/calendar?yearMonth=yyyy-MM` — 목록 API와 별도 엔드포인트로 분리(캘린더는 "그 달 전체"가 필요해 커서 페이지네이션 개념과 안 맞음). 커서 없이 해당 월 전체를 `playedAt ASC, id ASC`로 반환, 응답은 `nextCursor` 없는 `PlayCalendarResponse`. `gameId`/`from`/`to` 임의 범위 필터는 여전히 스코프 밖 — 월 단위로만.
- **M4 이후**: `CollectionOwnershipChecker`(임시 native query) 삭제, `collection.api.CollectionLookup` 직접 주입으로 교체. 순환 의존 회피 구조는 `collection/CLAUDE.md` 참조.
- **M5**: `PlayStatsProvider` 신설 — `plays` 테이블 직접 GROUP BY 집계(이유는 `stats/CLAUDE.md`).
- **M6**: `PlayerNameResolver`(플레이어 이름 해석, import와 공유), `PlayBulkImporter`(배치 임포트 전용 진입점, Idempotency-Key 경로와 별개).
- **export**: `PlayExportProvider` 신설(유저 전체 플레이+플레이어 배치 조회).
- **플레이어 관리(즐겨찾기·그룹·최근 플레이어)**: `PlayerService`/`PlayerController`/`PlayerGroupController` 신설(V4 마이그레이션). `GET /api/v1/players`(즐겨찾기 우선 정렬), `PATCH /api/v1/players/{id}/favorite`, `GET /api/v1/players/recent?limit=`(US-1.2 AC였던 "최근 함께한 플레이어 제안"의 서버 구현), `POST/GET /api/v1/player-groups`, `PUT/DELETE /api/v1/player-groups/{groupId}/players/{playerId}`, `DELETE /api/v1/player-groups/{groupId}`. 그룹-플레이어는 N:M(`player_group_members` 조인 테이블). **스코프는 유저 개인 로컬 명부 태깅에 한정** — `players` 테이블의 기존 원칙(계정 무연결, 소셜 그래프 금지, Won't: 커뮤니티) 그대로 유지, 그룹에 공유/초대/타 유저 참조 없음. 모임·커뮤니티로의 확장은 이번 설계에 포함하지 않음 — 실제로 필요해지면 `player_id` 기반이 아닌 `user_id` 기반의 별도 도메인/모듈로 재설계(별도 ADR 필요).

## 주의 (재발 이력 있음 — 코드 리뷰 시 확인)

- `PlayService.recordPlay`의 `collectionPlayTracker.recordPlay()` 호출은 idempotency 재시도용 `catch (DataIntegrityViolationException)` **바깥**에 있어야 함(PR #5 코드 리뷰에서 수정) — 안에 두면 컬렉션 쪽 쓰기 실패가 "중복 기록"으로 오인되어 조용히 삼켜짐.
- Kotlin data class에 디폴트 파라미터 있는 요청 바디 추가 시 Jackson 2/3 계열 확인 — 과거 `jackson-module-kotlin`(Jackson 2)이 Spring Boot 4.1 Jackson 3과 안 맞아 역직렬화가 깨진 이력 있음(PR #3에서 `tools.jackson.module`로 교체 완료, `build.gradle.kts`).
