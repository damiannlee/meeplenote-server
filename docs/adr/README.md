# ADR 인덱스

> 각 ADR은 "면접에서 '왜 이렇게 설계했나요'에 대한 답"이 되도록, 기각한 대안과 기각 이유, 그리고 결정을 뒤집을 트리거 조건까지 기록한다.
> 공통 제약: MAU 1,000 가정, 운영자 1인, 기록 저장 p95 300ms, 데이터 유실 제로 지향. (상세: [04_system_design.md](../04_system_design.md) 1장)

| ADR | 결정 | 상태 |
|---|---|---|
| [ADR-001](ADR-001_modular_monolith_adoption.md) | 모듈러 모놀리스 채택 (마이크로서비스 기각) | Accepted |
| [ADR-002](ADR-002_postgresql_single_db.md) | PostgreSQL 단일 DB (Redis·별도 문서 DB 기각) | Accepted |
| [ADR-003](ADR-003_bgg_on_demand_proxy_cache.md) | BGG 온디맨드 프록시 + 로컬 캐시 | Accepted |
| [ADR-004](ADR-004_korean_search_pg_trgm_chosung_column.md) | 한글 검색 — pg_trgm + 초성 생성 컬럼 | Accepted |
| [ADR-005](ADR-005_import_in_process_async_job.md) | 임포트 처리 — 인프로세스 비동기 잡 | Accepted |
| [ADR-006](ADR-006_client_native_app_adoption.md) | 클라이언트 — 네이티브 모바일 앱 채택 | Accepted |
| [ADR-007](ADR-007_auth_social_login_jwt.md) | 인증 — 소셜 로그인 1종 + 자체 JWT | Accepted |
| [ADR-008](ADR-008_client_stack_flutter_finalized.md) | 클라이언트 스택 — Flutter 확정 | Accepted |
| [ADR-009](ADR-009_module_boundary_enforcement.md) | 모듈 경계 — 패키지 규약 + ArchUnit | Accepted |
