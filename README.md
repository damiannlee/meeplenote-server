# meeplenote-server (미플노트)

> 서비스명 확정: **미플노트** (영문: meeplenote), 2026-07-11 미플수첩 → 2026-07-22 미플노트로 개칭.


한국 보드게이머의 흩어진 플레이 기록과 컬렉션을 한 곳에서 — 10초 기록 × 컬렉션 자동 연동.

- 문제 정의 → `docs/` 상위 프로젝트 문서 (problem_definition.md)
- MVP 범위 → 3단계 기능 명세 (Must 8개)
- 설계 근거 → 4단계 시스템 설계 + ADR-001~009

## 스택

| 영역 | 선택 | 근거 |
|---|---|---|
| 서버 | Kotlin / Spring Boot 4.1, Java 21 | ADR-001 (모듈러 모놀리스) |
| DB | PostgreSQL 15 + pg_trgm + jsonb | ADR-002, ADR-004 |
| 마이그레이션 | Flyway (스키마 원장, `ddl-auto: validate`) | docs/CONVENTIONS.md §3 |
| 모듈 경계 | 패키지 규약 + ArchUnit | ADR-009 |
| 클라이언트 | Flutter (별도 리포) | ADR-006, ADR-008 |

## 실행 (로컬)

```bash
# 1. PostgreSQL 기동
docker compose up -d

# 2. 서버 실행 (기본 프로파일: local)
./gradlew bootRun

# 3. 확인
curl http://localhost:8080/actuator/health
```

테스트 (Testcontainers — Docker 필요):
```bash
./gradlew test
```

## 구조

```
src/main/kotlin/com/meeplenote/
├── MeeplenoteApplication.kt
├── auth/          # 카카오 로그인, JWT (ADR-007)
├── game/          # 게임 마스터, BGG 온디맨드 캐시, 한글/초성 검색 (ADR-003, 004)
├── play/          # 플레이 기록 — 핵심 루프 (M1)
├── collection/    # 보유/위시, 노플 배지 (M3, M4)
├── stats/         # 기본 통계 4종 (M5)
├── dataimport/    # BG Stats/BGG 임포트, 비동기 잡 (M6, ADR-005)
└── common/        # 공통 설정, 에러 응답 포맷

각 모듈: api/ (공개 계약) + internal/ (구현) — 경계는 ModuleBoundaryTest가 CI에서 강제
```

## 컨벤션

브랜치 전략(GitHub Flow), 커밋 규칙(Conventional Commits), 마이그레이션 규칙은 [docs/CONVENTIONS.md](docs/CONVENTIONS.md).

## 5단계 시점의 가정·미결 사항

| 항목 | 상태 |
|---|---|
| 프로젝트/서비스명 | 확정 — 미플노트 (meeplenote) |
| ADR-009 (모듈 경계 강제 방식) | **Proposed** — 개발자 승인 대기 |
| Spring Security + JWT 의존성 | 의도적 미포함 — US-4.2 구현 시 추가 (빈 프로젝트에서 넣으면 전 엔드포인트 401로 스캐폴드 검증 방해) |
| CD (배포 자동화) | 의도적 미포함 — 배포 인프라(OCI vs 물리 서버) 미결정. 결정 후 ci.yml에 deploy 잡 추가 |
| 오브젝트 스토리지 (사진) | 미설정 — 인프라 결정과 함께 (presigned URL 방식, 설계 §5) |
| 라이브러리 버전 (Boot 4.1.0, Kotlin 2.2.20, Gradle 8.14.3) | 2026-07 웹 확인 기준. **최초 로컬 빌드에서 검증 필요** — 스캐폴드 생성 환경이 Maven Central 차단이라 빌드 미검증. 버전 충돌 시 start.spring.io 기본값과 대조 |
| Flutter 클라이언트 | 별도 리포. `flutter create`는 Claude Code에서 실행 (수작업 스캐폴드보다 표준 구조 보장) |
