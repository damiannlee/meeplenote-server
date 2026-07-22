# CLAUDE.md — meeplenote-server

> 1~5단계 산출물(문제정의/시장조사/기능명세/시스템설계/ADR/스캐폴드)을 구현 단계에서 즉시 참조 가능하도록 압축한 파일. 전체 근거는 `docs/`(`problem_definition.md`, `02_market_research_competitor_analysis.md`, `03_mvp_feature_spec.md`, `04_system_design.md`, `docs/adr/`) 참조 — **여기 없는 내용을 원본 문서에서 찾아야 한다면 이 파일이 불완전한 것, 발견 시 추가.**
>
> **모듈별 구현 상세는 이 파일이 아니라 `src/main/kotlin/com/meeplenote/{module}/CLAUDE.md`에** (auth/game/play/collection/stats/dataimport/export). 이 루트 파일은 전역 컨벤션·로드맵·ADR·현재 위치 요약만 유지. 모듈 `CLAUDE.md`를 쓸 때: 완결된 PR/커밋의 서사(버그 발견 경위, 구현 디테일)는 재서술하지 말고 PR 번호·커밋 해시로 가리키기만 할 것 — 실제로 남길 가치가 있는 건 **재발 위험 있는 활성 규칙**, **아직 열린 이슈/한계**, **다른 모듈이 참조하는 공개 인터페이스 요약**뿐.
>
> **CLAUDE.md류 문서 작성 규칙**: (1) 여러 파일에 공통되는 문장은 복붙하지 말고 상위 파일에 한 번만 쓸 것. (2) 기존 문서 내용을 옮기거나 재구성할 때는 그 내용이 지금도 사실인지 코드/git으로 재검증할 것 — 옛 문서를 그대로 베끼지 말 것. (3) PR/커밋에 이미 있는 서사는 포인터(PR 번호·커밋 해시)로 대체하고, 커밋에 없는 새 판단·활성 규칙만 남길 것. (4) 본문은 개조식(완전한 문장 대신 명사형 종결)으로 작성.

## 0. 지금 상태 (2026-07-18 기준)

- 5단계(스캐폴드) 완료, 로컬 첫 빌드 검증 완료(2026-07-11, `./gradlew test` BUILD SUCCESSFUL). Testcontainers 의존성명이 Spring Boot 4.1.0의 Testcontainers 2.x 아티팩트명 변경으로 `org.testcontainers:*` → `testcontainers-*` 로 수정됨(`build.gradle.kts`).
- 서비스명 확정: 미플수첩 (meeplenote).
- 페르소나 B 5~10명 인터뷰 **미실시** (n=1 자가응답만 근거) — 개발과 별개로 진행 필요.
- 아래 표는 로드맵(§2) 대비 현재 위치 요약. 모듈별 상세는 "상세" 열의 `CLAUDE.md` 참조.

| # | 기능 | 상태 | 상세 |
|---|---|---|---|
| M1 | 10초 플레이 기록 | 완료 (PR #3) | `play/CLAUDE.md` |
| M2 | 한글 게임 검색 | 완료 | `game/CLAUDE.md` |
| M3 | 컬렉션 관리 (보유/위시) | 완료 (PR #4) | `collection/CLAUDE.md` |
| M4 | 기록↔컬렉션 자동 연동 | 완료 (PR #5, #6) | `collection/CLAUDE.md` |
| M5 | 기본 통계 4종 | 완료 (PR #7) | `stats/CLAUDE.md` |
| M6 | 데이터 임포트 | BG Stats만 완료 (PR #8), BGG 소스는 보류 | `dataimport/CLAUDE.md` |
| M7 | 계정·백업 (카카오+JWT) | 완료 | `auth/CLAUDE.md` |
| M8 | 게임 DB (BGG 온디맨드 캐시) | **보류** — BGG XML API2가 Cloudflare 봇 차단으로 401. 정식 API 사용 신청 준비 중(2026-07-18) | `game/CLAUDE.md`, ADR-003 |
| US-4.2 | 데이터 내보내기 | 완료 (PR #9), JSON만(CSV 제외) | `export/CLAUDE.md` |
| S4 | 컬렉션 필터 (인원수·플레이시간) | 완료 (PR #10/#11) — Should 항목이지만 BGG 미의존이라 먼저 처리 | `collection/CLAUDE.md` |
| S6 | 내 플레이 캘린더 (월별 뷰) | 완료 — Should 항목이지만 BGG 미의존이라 먼저 처리 | `play/CLAUDE.md` |

**남은 Must 항목은 BGG 의존(M6의 BGG 소스 임포트, M8) 해제 대기뿐.**

## 1. 이 프로젝트가 아닌 것 (매 기능 제안마다 먼저 대조할 것)

**Won't (3단계에서 확정, 경계 재확인 없이 재논의 금지):**
룰 요약/AI Q&A, 모임 매칭, 커뮤니티(피드·댓글), 취향 추천 엔진, BGG 양방향 동기화, 공개 평점 시스템, 가격 정책 확정(인터뷰 전까지 완전 무료).

기능이 하나라도 다음 중 "아니오"면 Should 이하로 강등 (3단계 설계 원칙):
1. 기록 루프(플레이 종료→10초 기록→컬렉션/통계 자동 갱신)에 기여하는가
2. BG Stats/BGG/메모앱에서 넘어오는 전환 장벽을 낮추는가
3. 다른 유저가 0명이어도 혼자서 가치가 있는가 (콜드스타트 회피)

새 기능 제안 시 위 3원칙 통과 여부와 Won't 목록 저촉 여부 먼저 판정, 애매하면 구현 전 사용자(민석) 확인 — **로드맵이 요구하는 "쳐내는 역할"은 6단계에서도 유지.**

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

구현 순서 권장: **M7(인증) → M2/M8(게임 검색·캐시) → M1(기록) → M3/M4(컬렉션 연동) → M5(통계) → M6(임포트)**. 근거: M1은 게임 선택 전제라 검색 먼저, 인증 없이는 API가 유저 스코프 못 가짐. M6은 가장 복잡(비동기 잡, 매칭)하고 타 모듈 의존이라 마지막.

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
| 011 | 컴퓨트 인프라 = AWS EC2 단일 인스턴스(t4g.small, 앱+Postgres 동거), RDS 기각 | 앱/DB 중 하나만 리소스 포화 → DB 분리. 최대 인스턴스로도 부족·무중단 요구 발생 → ALB+수평 확장 |
| 012 | 사진 오브젝트 스토리지 = Cloudflare R2 (S3 기각) | R2 가용성 이슈 실측, 또는 AWS 생태계 통합 이득이 egress 절감보다 커짐 |

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

> meetjyou-backend(동일 스택: Kotlin + Spring Boot + JPA)에서 이식. 코드베이스가 작을 때(당시 auth 모듈만 구현) 규칙화 — M1~M6 진행 중 어긋남 방지 목적.

- **Null safety**: `?: throw` Elvis 연산자만 사용. `!!`, `requireNotNull()`, `if (x == null) throw` 금지.
- **DTO 변환**: 서비스가 companion `.of()` 팩토리로 DTO 리턴. 컨트롤러는 `.of()` 직접 호출 금지.
- **메서드 길이**: 함수 바디 30줄 하드캡. 넘으면 `validateXxx`/`buildXxx`/`resolveXxx` 이름의 private 헬퍼로 추출.
- **N+1 방지**: 반복문 안에서 레포지토리 호출 금지. `findAllByXxxIn(ids)` + `groupBy`/`associateBy`로 배치 로딩. (M5 통계, M6 임포트에서 특히 중요)
- **`@Transactional`**: 읽기 전용 메서드는 `@Transactional(readOnly = true)`, 쓰기 메서드는 `@Transactional`.
- **스코프 함수**: `.apply{}.also{}.let{}` 3단 체이닝 금지. 명시적 statement로 풀어 쓸 것.
- **문자열 결합**: `+` 연산자 대신 템플릿 리터럴만.
- **import**: wildcard import 금지.
- **주석/로그**: 코드 내 주석과 로그 메시지는 영어. (`docs/`, ADR, 이 파일 등 문서는 한글 유지 — 이 규칙은 코드에만 적용)
- **현재 유저 접근**: 유저 스코프가 필요한 모듈에서 `SecurityContext`를 직접 파고들지 말 것. `auth.api.CurrentUserProvider`(M2 시점에 도입 완료, 전 모듈 컨트롤러가 이미 사용 중)를 주입받아 쓸 것.

## 9. 작업 사이클 (로드맵 6단계 그대로 적용)

0. 작업 시작 전 `git branch --show-current`로 현재 브랜치 확인 후 사용자에게 알릴 것. 예상 브랜치가 아니면 진행 전 확인받을 것.
1. 유저 스토리 하나 선택 → **구현 계획 먼저 제시**, 승인 후 구현 (US 번호는 03_mvp_feature_spec.md §3 참조)
2. 구현 후 자체 리뷰: N+1, 트랜잭션 경계, 보안(입력 검증, 타 유저 자원 접근 403)
3. 단위+통합 테스트 작성 (ArchUnit 모듈 경계 테스트는 이미 있음 — 깨지면 절단선을 넘은 것, 우회 금지). 최종 반영 전 `./gradlew test`로 전체 테스트 통과 확인.
4. 브랜치/커밋은 docs/CONVENTIONS.md 규칙 (GitHub Flow, squash merge, Conventional Commits)
5. 기술적으로 의미 있는 고민(트레이드오프, 막혔다가 뚫린 문제)이 생기면 "노션에 보내줘"로 기록 → 포트폴리오 재료 축적

## 10. 페르소나 (기억할 것 — 기능 판단의 최종 기준)

**페르소나 B "기록 덕후" 이서연 (28)**가 1차 타겟. 컬렉션 40종, BG Stats를 영어·유료·한글검색 약함으로 방치 중. 전환 조건은 "무료+한글+10초 기록". **리스크: 초반 3주 이탈** — 이 문서의 여러 설계(첫 기록 유도 화면, 임포트로 통계 즉시 채우기) 전부 이 리스크 대응. 새 기능은 이 사람의 이탈 리스크 증감 여부로 판단할 것.

## 11. Compact Instructions

컨텍스트 압축 시 보존할 것:
- 현재 작업 목표와 미완료 TODO
- 이번 세션에서 건드린 파일 경로/클래스명
- 테스트 결과 (통과/실패 개수, 실패한 테스트 이름)
- 주요 결정사항 (선택한 접근법, 기각한 대안과 이유)
- 아직 해결 안 된 에러 메시지

버릴 것: 이미 저장된 파일 내용 전문, 반복된 툴 호출 결과, 해결된 에러.
