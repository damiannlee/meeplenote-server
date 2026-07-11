# ADR 인덱스

> 각 ADR은 "면접에서 '왜 이렇게 설계했나요'에 대한 답"이 되도록, 기각한 대안과 기각 이유, 그리고 결정을 뒤집을 트리거 조건까지 기록한다.
> 공통 제약: MAU 1,000 가정, 운영자 1인, 기록 저장 p95 300ms, 데이터 유실 제로 지향. (상세: [4단계_시스템설계.md](../4단계_시스템설계.md) 1장)

| ADR | 결정 | 상태 |
|---|---|---|
| [ADR-001](ADR-001_모듈러모놀리스채택.md) | 모듈러 모놀리스 채택 (마이크로서비스 기각) | Accepted |
| [ADR-002](ADR-002_PostgreSQL단일DB.md) | PostgreSQL 단일 DB (Redis·별도 문서 DB 기각) | Accepted |
| [ADR-003](ADR-003_BGG온디맨드프록시캐시.md) | BGG 온디맨드 프록시 + 로컬 캐시 | Accepted |
| [ADR-004](ADR-004_한글검색pg_trgm초성컬럼.md) | 한글 검색 — pg_trgm + 초성 생성 컬럼 | Accepted |
| [ADR-005](ADR-005_임포트인프로세스비동기잡.md) | 임포트 처리 — 인프로세스 비동기 잡 | Accepted |
| [ADR-006](ADR-006_클라이언트네이티브앱채택.md) | 클라이언트 — 네이티브 모바일 앱 채택 | Accepted |
| [ADR-007](ADR-007_인증소셜로그인JWT.md) | 인증 — 소셜 로그인 1종 + 자체 JWT | Accepted |
| [ADR-008](ADR-008_클라이언트스택Flutter확정.md) | 클라이언트 스택 — Flutter 확정 | Accepted |
| [ADR-009](ADR-009_모듈경계강제.md) | 모듈 경계 — 패키지 규약 + ArchUnit | Accepted |
