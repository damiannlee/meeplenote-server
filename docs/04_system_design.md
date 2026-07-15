# 보드게임 기록 서비스 — 4단계 시스템 설계

> 목적: 3단계 MVP 명세(Must 8개)를 구현 가능한 설계로 변환. 모든 결정의 "왜"는 ADR 모음(별도 문서)에 기록.
> 원칙: **포트폴리오는 규모가 아니라 판단을 보여준다.** 트래픽에 맞지 않는 과잉 설계(MSA, Kafka, Elasticsearch)를 의도적으로 배제하고, 그 배제의 근거를 남기는 것이 이 문서의 핵심 가치다.

---

## 1. 요구사항 요약과 트래픽 가정

### 기능 요구사항 (3단계에서 확정)
10초 기록, 한글 검색(초성 포함), 컬렉션(보유/위시), 기록↔컬렉션 자동 연동, 기본 통계 4종, BG Stats/BGG 임포트, 계정·백업, 데이터 내보내기.

### 비기능 요구사항
- 기록 저장 API p95 < 300ms (10초 UX의 서버 몫)
- 게임 검색 p95 < 500ms (타이핑 중 검색이므로 체감 중요)
- 데이터 유실 제로 지향 (이 서비스에서 데이터 유실 = 신뢰 붕괴 = 사망)
- 운영자 1인 (개발자 본인). 운영 부담 최소화가 아키텍처 최우선 제약

### 트래픽 가정 (명시적 수치)

| 항목 | 가정 | 근거 |
|---|---|---|
| MAU | 1,000 | 보드라이프 활동 유저 중 기록 의욕층. 니치의 니치 |
| DAU | 100~150 | 저빈도 취미. 주말 편중 |
| 쓰기 (기록 생성) | ~300건/일, 피크 주말 저녁 분당 5~10건 | 유저당 주 1~3플레이 |
| 읽기 | 쓰기의 20~30배 | 통계·컬렉션 조회 중심 |
| 피크 RPS | < 10 | 어떤 계산으로도 단일 서버 여유 |
| 저장량 | 기록 1건 ≈ 1KB → 연 10만 건 ≈ 100MB + 사진 | 사진이 저장 비용의 전부 |

**결론**: 이 수치에서 분산 시스템은 해악이다. 설계 목표는 "확장 가능성"이 아니라 **"1인이 운영 가능하면서, 커지면 어디를 자를지 미리 그어둔 모놀리스"**다.

## 2. 시스템 아키텍처

### 구성 (다이어그램은 대화 참조)

```
[클라이언트: Flutter 앱 (iOS/Android)]
        │ HTTPS / REST + JWT
        ▼
[Spring Boot 모놀리스 (Kotlin)] ── 모듈: auth / game / collection / play / stats / import
        │                                │
        ▼                                ▼
[PostgreSQL 15]                  [오브젝트 스토리지 (사진)]
        ▲
        │ 온디맨드 조회 + 캐시 적재
[BGG XML API2] (외부, 신뢰 불가 의존)
```

- **클라이언트는 Flutter 앱** (ADR-006, ADR-008). 클로드 코드가 클라이언트 코드를 전담 작성하는 개발 방식을 전제로, 단일 툴체인과 AI 코드 생성 신뢰도를 기준으로 Compose Multiplatform·React Native 대비 확정. 임시저장은 로컬 DB(sqflite/drift)에 영속, 사진은 디바이스에서 캐시 후 presigned URL로 스토리지 직행. 보드라이프 등 웹 유입 경로는 별도 정적 랜딩 페이지(앱과 무관, 스토어 링크 안내용)로 보완.
- **모듈러 모놀리스**: 단일 배포 단위, 내부는 도메인 모듈로 격리 (ADR-001). 모듈 간 호출은 인터페이스를 통해서만 — 나중에 분리할 절단선을 코드 구조로 미리 표시.
- **PostgreSQL 단일 DB**: 관계형 코어 + `jsonb`(임포트 원본 보관) + `pg_trgm`(검색) 겸용 (ADR-002, ADR-004).
- **BGG는 프록시+캐시**로만 접촉: 전체 DB 복제 금지, 요청 시 조회 후 로컬 `games` 테이블에 적재 (ADR-003). BGG 장애 시에도 이미 캐시된 게임으로 기록은 계속 가능해야 함 — 외부 의존이 핵심 루프를 막으면 안 된다.
- **임포트는 비동기 잡**: 업로드 즉시 202 반환, 처리는 인프로세스 큐 (ADR-005). 외부 메시지 큐 없음.
- **사진은 오브젝트 스토리지 직행**: presigned URL로 클라이언트가 직접 업로드, DB에는 키만 저장. 앱 서버가 바이너리를 중계하지 않음.

### 확장 절단선 (미리 그어두는 선)
1. **stats 모듈** — 집계 쿼리가 무거워지는 첫 지점. 집계 테이블(materialized) 도입 → 그래도 부족하면 읽기 전용 리플리카.
2. **import 모듈** — CPU/IO 스파이크의 근원. 인프로세스 큐 → 별도 워커 프로세스로 분리가 첫 번째 물리적 분리 후보.
3. **games 검색** — pg_trgm 한계 도달 시(수십만 행 + 높은 QPS) 검색 엔진 도입. MAU 1,000에선 도달하지 않음.

## 3. 클라이언트/서버 책임 경계

| 관심사 | 클라이언트 (Flutter) | 서버 | 근거 |
|---|---|---|---|
| 기록 임시저장 (작성 중 이탈) | ✅ 로컬 DB(sqflite/drift) | — | 서버 왕복은 10초 UX에 불필요한 지연. 네이티브 로컬 저장은 PWA 대비 축출·용량 제약이 없어 미완성 데이터도 안정적으로 보관 |
| 승자 자동 판정 (점수 최고자) | ✅ 즉시 반영 | 저장 시 재검증 | UI 반응성은 클라, 데이터 정합성은 서버가 최종 책임 |
| 초성/한글 검색 | 입력 디바운스(300ms)만 | ✅ 검색 로직 전체 | 초성 변환·유사도는 DB 인덱스와 결합된 서버 책임. 클라에 게임 DB를 내리지 않음 |
| 통계 집계 | 차트 렌더링만 | ✅ 집계 쿼리 | 클라 집계는 전체 기록 다운로드를 강제 → 데이터 크면 파탄 |
| 노플 배지 판정 | — | ✅ 컬렉션 응답에 포함 | 컬렉션+기록 조인은 서버만 가능 |
| 사진 리사이즈 | ✅ 업로드 전 1280px 축소 | 원본 검증만 | 업로드 트래픽·저장 비용 절감. 서버 이미지 처리 파이프라인 회피 |
| 임포트 파일 파싱 | 파일 선택만 | ✅ 파싱·매칭·병합 | 매칭은 games DB 필요. 원본은 서버가 jsonb로 보관(재처리 가능) |
| 중복 기록 방지 | 저장 버튼 비활성화 | ✅ 멱등키(Idempotency-Key) | 이중 안전. 네트워크 재시도 시 중복 생성은 서버만 막을 수 있음 |

**원칙**: 클라이언트는 "빠른 느낌"을 책임지고, 서버는 "맞는 데이터"를 책임진다. 정합성 규칙이 클라이언트에만 존재하는 상태를 금지.

## 4. 데이터 모델 (ERD) — 다이어그램은 대화 참조

### 엔티티 요약

| 테이블 | 역할 | 핵심 설계 포인트 |
|---|---|---|
| `users` | 계정 | 소셜 로그인 1종. provider + provider_id 유니크 |
| `games` | 게임 마스터 (BGG 캐시 + 유저 직접 등록) | `bgg_id` nullable(직접 등록 게임), `name_ko`, `name_en`, `name_chosung`(생성 컬럼), min/max_players, playtime. `source` enum(bgg/custom) |
| `collections` | 유저-게임 보유/위시 | (user_id, game_id) 유니크. `status` enum(owned/wished). 소프트 삭제 없음 — 행 삭제 |
| `plays` | 플레이 기록 | user_id, game_id, played_at(date), note, rating, photo_key. **점수·플레이어 없이 존재 가능** (10초 기록의 데이터 표현) |
| `players` | 유저의 로컬 플레이어 명부 | **계정과 무연결.** user_id 스코프의 이름 목록. 소셜 그래프를 만들지 않음 (Won't: 커뮤니티) |
| `play_players` | 기록-플레이어 N:M + 점수 | play_id, player_id, score(nullable), is_winner |
| `import_jobs` | 임포트 잡 | source(bgstats/bgg), status(pending/running/done/failed), `raw_payload jsonb`(원본 보존), result_summary jsonb |

### 이 설계에서 나중에 병목이 될 지점 (선제 지적)

1. **통계 집계 쿼리** — `plays` + `play_players` 조인 위 GROUP BY. 유저당 기록 수천 건까진 인덱스(`plays(user_id, played_at)`)로 충분하나, "전체 유저 연말 결산 일괄 생성" 같은 배치가 생기면 풀스캔. **대비**: 통계는 유저 요청 시 계산(온디맨드)으로 시작, 느려지면 유저별 월간 집계 테이블 추가. 처음부터 집계 테이블을 만들지 않는 이유: 정합성 관리 비용이 MVP 규모에선 손해.
2. **`games.name` trgm 검색** — 행 수가 적을 땐 문제없으나, BGG 캐시가 수만 행으로 자라면 `gin_trgm_ops` 인덱스 크기와 갱신 비용 증가. **대비**: 검색은 `name_ko`/`name_en`/`name_chosung` 3컬럼 한정, 인기도(기록 수) 정렬로 LIMIT 조기 종료.
3. **`play_players`의 성장** — 가장 빨리 커지는 테이블(기록당 평균 3~4행). 지금은 문제없지만 "플레이어별 전적 비교"(Could 기능)를 켜는 순간 이 테이블 중심의 무거운 쿼리가 생김. **대비**: Could 기능 도입 시점에 집계 테이블과 함께 설계. 지금 미리 만들지 않음.
4. **`import_jobs.raw_payload` (jsonb)** — BG Stats 원본이 수 MB일 수 있음. TOAST로 저장은 되나 백업 크기와 vacuum 비용 증가. **대비**: 처리 완료 90일 후 raw_payload null 처리하는 정리 잡. 원본이 필요한 재처리 시나리오는 90일이면 충분.
5. **사진 키와 고아 객체** — 기록 삭제 시 스토리지 객체 삭제 실패하면 고아 발생. **대비**: 삭제는 DB 먼저 + 주기적 고아 수거 배치(스토리지 목록 대조). 동기 삭제에 매달리지 않음.

## 5. REST API 스펙 (핵심 발췌)

공통: `Authorization: Bearer <JWT>`. 에러 응답 포맷 통일:
```json
{ "error": { "code": "GAME_NOT_FOUND", "message": "게임을 찾을 수 없습니다", "detail": {} } }
```
공통 에러: 401 `UNAUTHORIZED`, 403 `FORBIDDEN`(타 유저 자원), 422 `VALIDATION_FAILED`, 429 `RATE_LIMITED`.

### 인증
```
POST /api/v1/auth/social          { provider: "kakao", token: "..." } → 200 { accessToken, refreshToken, isNewUser }
POST /api/v1/auth/refresh         { refreshToken } → 200 { accessToken }   | 401 TOKEN_EXPIRED
```

### 게임 검색
```
GET /api/v1/games?q=ㅋㅌ&limit=10
→ 200 { items: [{ id, bggId, nameKo, nameEn, thumbnailUrl, minPlayers, maxPlayers, playtime, source }], hasMore }
```
- `q`는 한글/영문/초성 모두 허용. 서버가 판별.
- 로컬 캐시 미스 + BGG 조회 성공 시 결과에 병합 (응답 지연 허용 한도 내에서만, 초과 시 캐시 결과만 반환하고 백그라운드 적재).
- 400 `QUERY_TOO_SHORT` (q < 1자), 결과 0건은 200 + 빈 배열 (에러 아님 — 클라는 "직접 등록" CTA 노출).

```
POST /api/v1/games                { name: "우리집 자작 게임" } → 201 { id, source: "custom" }
```
- 유저 스코프 custom 게임. 422 (이름 공백/100자 초과).

### 기록 (핵심 루프)
```
POST /api/v1/plays
Headers: Idempotency-Key: <uuid>   ← 클라 생성, 재시도 중복 방지
{ gameId, playedAt?: "2026-07-09", players?: [{ playerId?, name?, score?, isWinner? }], note?, rating?, photoKey? }
→ 201 { id, gameId, playedAt, totalPlayCountForGame: 12, suggestAddToCollection: true }
```
- **gameId 외 전부 옵션** (US-1.1/1.2의 API 표현). playedAt 생략 시 오늘.
- players[].name만 오면 명부에 플레이어 자동 생성 후 연결 (기록 화면에서 명부 관리 화면으로 보내지 않음 — 마찰 제로).
- `totalPlayCountForGame`(토스트용)과 `suggestAddToCollection`(미보유 판정)을 응답에 포함 → 클라 추가 왕복 제거. **기록 1회 = API 1콜.**
- 404 `GAME_NOT_FOUND`, 409 `DUPLICATE_REQUEST`(멱등키 중복 — 최초 응답 재반환), 422 (미래 날짜, rating 범위 밖).

```
GET    /api/v1/plays?gameId=&from=&to=&cursor=&limit=20   → 200 { items, nextCursor }   ← 커서 기반 (오프셋 금지)
PATCH  /api/v1/plays/{id}     부분 수정 → 200
DELETE /api/v1/plays/{id}     → 204   | 404, 403
```

### 컬렉션
```
GET  /api/v1/collections?status=owned&sort=recent_play
→ 200 { items: [{ gameId, nameKo, thumbnailUrl, status, playCount, lastPlayedAt, isNoPlay }], counts: { owned: 42, wished: 7 } }
```
- `playCount`/`isNoPlay`는 서버 계산 (책임 경계 표 참조).
```
PUT    /api/v1/collections/{gameId}   { status: "owned" } → 200   ← 멱등 (있으면 상태 변경)
DELETE /api/v1/collections/{gameId}   → 204
```

### 통계
```
GET /api/v1/stats/summary?year=2026
→ 200 { totalPlays, playsThisMonth, monthlyTrend: [{month, count}×12], topGames: [{gameId, nameKo, count}×5], noPlayCount }
```

### 임포트
```
POST /api/v1/imports              multipart(file) 또는 { source: "bgg", bggUsername: "..." }
→ 202 { jobId, status: "pending" }
GET  /api/v1/imports/{jobId}
→ 200 { status: "done", summary: { playsImported: 312, gamesMatched: 87, unmatched: [{name, candidates: [...]}] } }
POST /api/v1/imports/{jobId}/resolve   { resolutions: [{ unmatchedName, gameId }] } → 200
```
- 422 `UNSUPPORTED_FILE_FORMAT`, 409 `IMPORT_ALREADY_RUNNING`(유저당 동시 1개), 실패 시 status: "failed" + reason.
- 중복 병합 규칙: (game, playedAt, 플레이어 집합) 동일 시 스킵 — 규칙은 summary에 명시 반환.

### 데이터 내보내기
```
GET /api/v1/exports → 200 (application/json, 전체 기록+컬렉션)
{
  exportedAt: "2026-07-15T00:00:00Z",
  plays: [{ id, gameId, gameName, playedAt, note, rating, players: [{name, score, isWinner}] }],
  collections: [{ gameId, gameName, status, playCount, lastPlayedAt, addedAt }]
}
```
- 구현 시점(2026-07) 확인: 트래픽 규모(MAU 1,000, 피크 RPS<10, 단일 유저 데이터만 조회)에서 청크 스트리밍(`StreamingResponseBody`)은 과설계로 판단해 일반 JSON 응답으로 구현(ADR-002 정신). 데이터 규모가 커지면 그때 스트리밍 재검토.
- CSV 포맷은 이번 슬라이스 범위 밖(향후 별도 스토리).

### 사진 업로드
```
POST /api/v1/photos/presign   { contentType: "image/jpeg" } → 200 { uploadUrl, photoKey, expiresIn: 600 }
```
- 클라가 uploadUrl로 직접 PUT → photoKey를 기록 생성에 첨부. 서버는 바이너리 미경유.

## 6. 비고 — 이 설계가 면접에서 방어되는 지점

1. "왜 MSA 안 했나" → 트래픽 수치 + 1인 운영 제약 + 모듈 절단선 (ADR-001)
2. "왜 Redis/Kafka/ES 없나" → 각각의 부재가 의도적 결정이며 도입 트리거 조건을 명시함 (ADR-002/004/005)
3. "외부 API 의존은 어떻게 다뤘나" → BGG 장애가 핵심 루프를 못 막는 캐시 구조 (ADR-003)
4. "데이터 정합성은" → 멱등키, 서버 최종 검증, 임포트 원본 보존(재처리 가능성)
5. "병목은 어디인가" → 4장에서 5개 지점을 선제 지적하고 각각 도입 트리거와 대비책을 적어둠
6. "왜 클라이언트를 Flutter로 했나" → 백엔드(Kotlin) 언어 통일이라는 직관적 선택지를 검토했으나, 개발 방식(클로드 코드 전담 개발)이라는 제약을 반영해 AI 코드 생성 신뢰도와 툴체인 단순성 기준으로 재판단한 사례 (ADR-006, ADR-008). "가장 유명한 스택"도 아니고 "가장 직관적인 언어 통일"도 아닌, 이 프로젝트의 개발 방식 자체를 결정 변수로 삼은 판단 과정을 보여줌
