# CLAUDE.md — meeplenote-server

> 이 파일은 1~5단계 산출물(문제정의/시장조사/기능명세/시스템설계/ADR/스캐폴드)을 구현 단계에서 즉시 참조 가능하도록 압축한 것이다. 전체 근거는 `docs/`(`problem_definition.md`, `02_market_research_competitor_analysis.md`, `03_mvp_feature_spec.md`, `04_system_design.md`, `docs/adr/`)를 참조하되, **여기 없는 내용을 원본 문서에서 찾아야 한다면 이 파일이 불완전한 것 — 발견 시 이 파일에 추가할 것.**

## 0. 지금 상태 (2026-07-12 기준)

- 5단계(스캐폴드) 완료: 모듈 패키지 구조, Flyway V1, CI, ArchUnit 경계 테스트까지 작성됨. **로컬 첫 빌드 검증 완료 (2026-07-11)** — `./gradlew test` BUILD SUCCESSFUL. 단, `build.gradle.kts`의 Testcontainers 의존성명이 `org.testcontainers:postgresql`/`junit-jupiter`에서 `testcontainers-postgresql`/`testcontainers-junit-jupiter`로 수정됨 (Spring Boot 4.1.0이 끌어오는 Testcontainers 2.x가 아티팩트명을 변경했기 때문, 1.x 라인은 1.21.3에서 종료).
- ADR-009 (모듈 경계 강제 방식)는 Status: Accepted (2026-07-11 승인).
- 서비스명 확정: 미플수첩 (meeplenote), 2026-07-11.
- 페르소나 B 5~10명 인터뷰 **미실시** (n=1 자가응답만 근거). 개발과 별개로 진행 필요.
- **6단계(구현) 진행 상황 (2026-07-12)**: M7(카카오 로그인+JWT) 완료·머지. M2(한글 게임 검색 — 초성/한글/영문, 로컬 DB 대상 + 커스텀 게임 등록) 완료·머지. **M8(BGG 온디맨드 캐시)는 보류** — 실제 BGG XML API2가 현재 Cloudflare 봇 차단으로 401을 반환해 무인증 온디맨드 조회가 성립하지 않음(`docs/adr/ADR-003` 구현 노트, `feat/m8-bgg-cache` 브랜치에 코드 보존). BGG 접근 문제 해결 전까지 게임 검색은 로컬 DB(직접 등록 게임)만 대상. **M1(`POST /api/v1/plays`) 완료·머지 (PR #3)** — 범위는 생성 API 하나(GET 목록/PATCH/DELETE는 다음 스토리). 구현 중 `game.api.GameLookup`(게임 존재 검증용 최소 공개 인터페이스) 신설. **버그 발견 및 수정**: `build.gradle.kts`의 `com.fasterxml.jackson.module:jackson-module-kotlin`(Jackson 2)이 Spring Boot 4.1의 기본 Jackson 3(`tools.jackson.*`)과 맞지 않아 Kotlin data class의 디폴트 파라미터(예: `players: List<PlayerInput> = emptyList()`)가 요청 바디에 없을 때 역직렬화가 깨지는 잠재 버그였음 — `tools.jackson.module:jackson-module-kotlin`으로 교체. 이전까지 이 버그가 안 드러난 이유는 기존 엔드포인트(M2/M7)가 전부 필수 필드만 썼기 때문. **M3(`PUT/DELETE /api/v1/collections/{gameId}`, `feat/m3-collection-management` 브랜치) 구현 완료, 머지 대기** — 범위는 보유/위시 추가·제거 두 엔드포인트뿐(`GET /api/v1/collections` 목록·정렬·카운트는 M4로 분리). `collection.api.CollectionLookup`(`isOwned`) 신설, `collection.internal.CollectionLookupService`가 구현하지만 아직 `play` 모듈에 배선하지 않음 — `play.internal.CollectionOwnershipChecker`는 여전히 `collections` 테이블을 native query로 직접 조회 중(주석을 TODO(M4)로 갱신, M4에서 이 클래스를 삭제하고 `PlayService`가 `CollectionLookup`을 직접 주입받도록 교체 예정). ADR-010(엔티티 식별자 BIGINT IDENTITY + FK 유지) 승인·기록 완료. **M4(기록↔컬렉션 자동 연동, 노플 배지, `feat/m4-play-collection-sync` 브랜치) 구현 완료** — `play.internal.CollectionOwnershipChecker`(native query 임시 구현)를 삭제하고 `PlayService`가 `collection.api.CollectionLookup`을 직접 주입받도록 교체(순환 의존 없이 play→collection 단방향 유지). 신규 `GET /api/v1/collections?status=&sort=`(정렬: name/recent_play/play_count) 구현, 응답에 `playCount`/`lastPlayedAt`/`isNoPlay` 포함. **설계 포인트**: `collection` 모듈이 `play.api`를 역참조하면 `ModuleBoundaryTest.noCyclesBetweenModules`가 깨지므로(play→collection 방향이 이미 존재), 플레이 횟수·최근 플레이일은 `collections` 테이블에 캐시 컬럼(`play_count`, `last_played_at`, V3 마이그레이션)으로 저장하고 `play` 모듈이 기록 저장 성공 시(멱등 재시도 제외) `collection.api.CollectionPlayTracker.recordPlay()`를 호출해 갱신하는 방식으로 구현 — collection 모듈은 자기 테이블만 읽는다. `game.api.GameLookup`에 `getSummaries`(배치 조회, N+1 방지)와 `GameSummary.thumbnailUrl` 추가. **코드 리뷰 후 수정 완료 (2026-07-13, PR #5 머지)**: (1) `CollectionLookupService.recordPlay`가 상태 무관하게 통계를 갱신하던 것을 최초엔 `status == OWNED`로 게이팅했으나, **사용자 피드백으로 다시 되돌림** — 위시리스트 게임도 (남의 것을 플레이해보는 등) 기록될 수 있어야 하고 `GET /api/v1/collections` 응답에 그 통계가 노출돼야 한다는 제품 판단에 따라 `recordPlay`는 상태 무관하게 항상 `playCount`/`lastPlayedAt`을 갱신한다. `isNoPlay`(노플 배지)만 여전히 `status == OWNED`일 때만 계산 — "보유 후 안 한 게임"이라는 배지 자체의 의미는 OWNED에 한정되지만, 플레이 통계 집계 자체는 소유 여부와 무관. (2) `PlayService.recordPlay`에서 `collectionPlayTracker.recordPlay()` 호출을 멱등성 재시도용 `catch (DataIntegrityViolationException)` 블록 밖으로 이동 — 컬렉션 쪽 쓰기 실패가 "중복 기록"으로 오인되어 조용히 삼켜지는 것을 방지. (3) `sort` 쿼리 파라미터를 서비스 레이어의 수동 문자열 파싱(`CollectionSort.fromQueryParam`, 삭제됨) 대신 `CollectionSortConverter`(Spring `Converter<String, CollectionSort>`) 기반 타입 바인딩으로 교체 — `status`와 동일한 검증 경로(`GlobalExceptionHandler.handleTypeMismatch`) 재사용. (4) `sort=name` 정렬이 `nameKo`가 없는 커스텀 게임(영문 전용)을 항상 맨 앞으로 보내던 버그 수정 — `nameEn` 폴백 추가. (5) `CollectionLookupServiceTest`/`CollectionSortConverterTest` 신규 작성. **알려진 한계 (의도적으로 남김)**: 컬렉션에 추가하기 전에 이미 기록한 플레이 이력은 `playCount`에 소급 반영되지 않는다 — `collection` 모듈이 `play.api`를 조회하면 `ModuleBoundaryTest.noCyclesBetweenModules`가 깨지므로(play→collection 방향이 이미 존재) 역방향 조회로 백필하는 구조를 만들 수 없다. US-2.2 원문("보유 후 한 번도 기록이 없는 게임")도 "보유 시점 이후" 플레이만을 대상으로 하므로 현재 동작(추가/재추가 시 0부터 시작)이 스펙과 일치한다고 판단, 별도 이슈로 남기지 않음. 다음 순서는 M5(통계) — 통계 집계 시 이번에 만든 `collections.play_count`/`last_played_at` 캐시를 재사용할 수 있는지 먼저 검토할 것.

## 1. 이 프로젝트가 아닌 것 (매 기능 제안마다 먼저 대조할 것)

**Won't (3단계에서 확정, 경계 재확인 없이 재논의 금지):**
룰 요약/AI Q&A, 모임 매칭, 커뮤니티(피드·댓글), 취향 추천 엔진, BGG 양방향 동기화, 공개 평점 시스템, 가격 정책 확정(인터뷰 전까지 완전 무료).

기능이 하나라도 다음 중 "아니오"면 Should 이하로 강등한다 (3단계 설계 원칙):
1. 기록 루프(플레이 종료→10초 기록→컬렉션/통계 자동 갱신)에 기여하는가
2. BG Stats/BGG/메모앱에서 넘어오는 전환 장벽을 낮추는가
3. 다른 유저가 0명이어도 혼자서 가치가 있는가 (콜드스타트 회피)

새 기능 제안이 들어오면 위 3원칙 통과 여부와 Won't 목록 저촉 여부를 먼저 판정하고, 애매하면 구현 전에 사용자(민석)에게 확인한다 — **로드맵이 요구하는 "쳐내는 역할"은 6단계에서도 유지.**

## 2. MVP 범위 (3단계 MoSCoW — Must 8개, 이번 릴리스 전부)

| # | 기능 | 핵심 API/모듈 |
|---|---|---|
| M1 | 10초 플레이 기록 | `play` — `POST /api/v1/plays`, gameId 외 전부 옵션 |
| M2 | 한글 게임 검색 (한글/영문/초성) | `game` — `GET /api/v1/games?q=` |
| M3 | 컬렉션 관리 (보유/위시 2상태만) | `collection` — `PUT/DELETE /api/v1/collections/{gameId}` |
| M4 | 기록↔컬렉션 자동 연동 (노플 배지) | `collection` + `play` 응답의 `suggestAddToCollection` |
| M5 | 기본 통계 4종 | `stats` — `GET /api/v1/stats/summary` |
| M6 | 데이터 임포트 (BG Stats/BGG, 단방향) | `dataimport` — `POST /api/v1/imports` (비동기 202) |
| M7 | 계정·백업 (소셜 로그인 1종) | `auth` — 카카오, JWT |
| M8 | 게임 DB (BGG 온디맨드 캐시) | `game` |

구현 순서 권장: **M7(인증) → M2/M8(게임 검색·캐시) → M1(기록) → M3/M4(컬렉션 연동) → M5(통계) → M6(임포트)**. 근거: M1이 게임 선택을 전제하므로 검색이 먼저, 인증 없이는 어떤 API도 유저 스코프를 못 가짐. M6은 가장 복잡하고(비동기 잡, 매칭) 다른 모듈에 의존하므로 마지막.

## 3. 비기능 요구사항 (설계 §1)

- 기록 저장 API p95 < 300ms, 검색 p95 < 500ms
- 데이터 유실 제로 지향 — 삭제는 소프트 우선 검토, 백업 대상은 PostgreSQL 하나
- 트래픽: MAU 1,000 / 피크 RPS < 10 → **이 규모에 안 맞는 최적화(캐시 선제 도입, 배치 집계 테이블 등)를 하지 말 것.** 문제가 관측된 후 도입 (ADR-002).

## 4. 확정 기술 결정 (ADR — 뒤집으려면 반드시 트리거 조건 재확인 후 사용자 승인)

| ADR | 결정 | 뒤집는 트리거 |
|---|---|---|
| 001 | 모듈러 모놀리스 (auth/game/collection/play/stats/dataimport) | import가 API 레이턴시에 영향 |
| 002 | PostgreSQL 단일 DB (Redis/Mongo 없음) | 통계 조회 반복 폭주 → HTTP 캐시 헤더부터, 그다음 Redis |
| 003 | BGG 온디맨드 프록시+캐시 (전체 미러링 안 함), 지연 한도 1.5s | — |
| 004 | pg_trgm + 초성 생성 컬럼 (ES 없음) | 검색 대상이 설명/리뷰로 확장 |
| 005 | 임포트 = 인프로세스 비동기 잡 (외부 큐 없음) | API 레이턴시 영향 시 워커 분리 → DB 폴링(SKIP LOCKED)부터 |
| 006/008 | 클라이언트 = Flutter (PWA/RN/CMP 기각) | Claude Code의 Flutter 코드 생성 품질 실측 미달 시 RN 재검토 |
| 007 | 카카오 로그인 1종 + 자체 JWT (access 30분 + refresh DB 저장/회전) | 카카오 없는 유저 항의 실측 시 2번째 프로바이더 |
| 009 | 모듈 경계 = 패키지 규약(`api`/`internal`) + ArchUnit | 이벤트 기반 통신 전환 시 Spring Modulith 재검토 |
| 010 | 엔티티 식별자 = BIGINT IDENTITY + 모듈 간 FK 유지 (UUID PK·FK 제거 기각) | ADR-001/009 트리거(물리적 서비스 분리) 발동 시 FK 제거·PK 전략 재검토 |

## 5. 클라이언트/서버 책임 경계 (설계 §3 — 정합성 규칙은 항상 서버가 최종)

| 관심사 | 클라(Flutter) | 서버 |
|---|---|---|
| 기록 임시저장 | 로컬 DB(sqflite/drift) | — |
| 승자 자동판정 | 즉시 반영 | 저장 시 재검증 |
| 초성/한글 검색 로직 | 디바운스 300ms만 | 전체 |
| 통계 집계 | 렌더링만 | 전체 |
| 노플 배지 | — | 컬렉션 응답에 포함 |
| 중복 기록 방지 | 버튼 비활성화 | Idempotency-Key (최종 방어선) |

## 6. API 계약 핵심 (설계 §5 발췌 — 전체는 04_system_design.md)

- 에러 포맷 통일: `{ "error": { "code", "message", "detail" } }`
- 목록 조회는 **커서 기반** (오프셋 금지) — `plays` 등
- `POST /api/v1/plays`는 `Idempotency-Key` 헤더 필수, 응답에 `totalPlayCountForGame`·`suggestAddToCollection` 포함해 클라 왕복 제거 ("기록 1회 = API 1콜" 원칙)
- 게임 검색 결과 0건은 200 + 빈 배열 (에러 아님 — "직접 등록" CTA)
- 임포트는 202 → 폴링 → `POST .../resolve`로 매칭 실패 수동 해결

## 7. DB 스키마 원장

`src/main/resources/db/migration/V1__init.sql`. 신규 마이그레이션은 **머지된 버전 수정 금지, 새 버전 추가만** (docs/CONVENTIONS.md §3). ERD 엔티티별 향후 병목 지점과 대비책은 04_system_design.md §4 "이 설계에서 나중에 병목이 될 지점" 참조 — 특히 `play_players` 성장과 `import_jobs.raw_payload` 정리는 Could 기능 도입 시점에 다시 볼 것.

## 8. 코드 스타일 & 컨벤션 (Kotlin)

> meetjyou-backend(동일 스택: Kotlin + Spring Boot + JPA)에서 이식. 코드베이스가 아직 작을 때(현재 auth 모듈만 구현) 규칙화해서 M1~M6 진행 중 어긋나지 않게 한다.

- **Null safety**: `?: throw` Elvis 연산자만 사용. `!!`, `requireNotNull()`, `if (x == null) throw` 금지.
- **DTO 변환**: 서비스가 companion `.of()` 팩토리로 DTO를 리턴한다. 컨트롤러는 `.of()`를 직접 호출하지 않는다.
- **메서드 길이**: 함수 바디 30줄 하드캡. 넘으면 `validateXxx`/`buildXxx`/`resolveXxx` 이름의 private 헬퍼로 추출.
- **N+1 방지**: 반복문 안에서 레포지토리 호출 금지. `findAllByXxxIn(ids)` + `groupBy`/`associateBy`로 배치 로딩. (M5 통계, M6 임포트에서 특히 중요)
- **`@Transactional`**: 읽기 전용 메서드는 `@Transactional(readOnly = true)`, 쓰기 메서드는 `@Transactional`.
- **스코프 함수**: `.apply{}.also{}.let{}` 3단 체이닝 금지. 명시적 statement로 풀어 쓸 것.
- **문자열 결합**: `+` 연산자 대신 템플릿 리터럴만.
- **import**: wildcard import 금지.
- **주석/로그**: 코드 내 주석과 로그 메시지는 영어. (`docs/`, ADR, 이 파일 등 문서는 한글 유지 — 이 규칙은 코드에만 적용)
- **현재 유저 접근**: M1(기록)/M3(컬렉션)/M5(통계)처럼 유저 스코프가 필요한 모듈에서 `SecurityContext`를 직접 파고들지 말 것. auth 모듈에 `CurrentUserProvider` 같은 단일 접근점을 만들어 재사용 (아직 미구현 — 두 번째로 유저 스코프가 필요한 모듈을 만들 때 도입할 것).

## 9. 작업 사이클 (로드맵 6단계 그대로 적용)

0. 작업 시작 전 `git branch --show-current`로 현재 브랜치 확인 후 사용자에게 알릴 것. 예상 브랜치가 아니면 진행 전 확인받을 것.
1. 유저 스토리 하나 선택 → **구현 계획 먼저 제시**, 승인 후 구현 (US 번호는 03_mvp_feature_spec.md §3 참조)
2. 구현 후 자체 리뷰: N+1, 트랜잭션 경계, 보안(입력 검증, 타 유저 자원 접근 403)
3. 단위+통합 테스트 작성 (ArchUnit 모듈 경계 테스트는 이미 있음 — 깨지면 절단선을 넘은 것, 우회 금지). 최종 반영 전 `./gradlew test`로 전체 테스트 통과 확인.
4. 브랜치/커밋은 docs/CONVENTIONS.md 규칙 (GitHub Flow, squash merge, Conventional Commits)
5. 기술적으로 의미 있는 고민(트레이드오프, 막혔다가 뚫린 문제)이 생기면 "노션에 보내줘"로 기록 → 포트폴리오 재료 축적

## 10. 페르소나 (기억할 것 — 기능 판단의 최종 기준)

**페르소나 B "기록 덕후" 이서연 (28)**가 1차 타겟. 컬렉션 40종, BG Stats를 영어·유료·한글검색 약함으로 방치 중. 전환 조건은 "무료+한글+10초 기록". **리스크: 초반 3주 이탈** — 이 문서에서 나오는 여러 설계(첫 기록 유도 화면, 임포트로 통계 즉시 채우기)가 전부 이 리스크 대응이다. 새 기능이 이 사람의 이탈 리스크를 줄이는지 늘리는지로 판단할 것.

## 11. Compact Instructions

컨텍스트 압축 시 보존할 것:
- 현재 작업 목표와 미완료 TODO
- 이번 세션에서 건드린 파일 경로/클래스명
- 테스트 결과 (통과/실패 개수, 실패한 테스트 이름)
- 주요 결정사항 (선택한 접근법, 기각한 대안과 이유)
- 아직 해결 안 된 에러 메시지

버릴 것: 이미 저장된 파일 내용 전문, 반복된 툴 호출 결과, 해결된 에러.
