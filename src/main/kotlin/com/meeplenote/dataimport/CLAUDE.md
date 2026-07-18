# dataimport 모듈 — 구현 노트

## 상태

- **M6 완료** (커밋 `ea4d4a2`, PR #8): BG Stats 파일 업로드만(BGG 소스는 M8과 동일 사유로 제외 — M8 해제 후 별도 스토리). `POST /api/v1/imports`, `GET /api/v1/imports/{jobId}`, `POST /api/v1/imports/{jobId}/resolve`. 잡 처리는 `@Async`(ADR-005).
- **BG Stats 스키마**: 공식 문서 비공개라 공개 파서(`TalitaJames/bgStats-dataVisualiser`) 역추적으로 확정 — **실물 샘플로 검증한 것은 아니라 첫 실제 파일 유입 시 재검증 필요.**

## 주의 (재발 이력 있음)

async 처리에서 `@Transactional` 메서드를 `this.xxx(...)`로 self-invocation하면 Spring AOP 프록시가 가로채지 못해 트랜잭션이 조용히 무시된다 — 이 모듈이 겪은 실제 버그(초기 구현의 `process`→`this.runImport`). 현재는 `@Transactional` 없이 개별 `save()` 호출이 각자 커밋되는 구조로 우회돼 있음. 새 비동기 처리 추가 시 같은 패턴 확인할 것.

## 제외 범위

BGG 소스 임포트(M8 해제 후), `import_jobs.raw_payload` 90일 정리 배치잡(Could).
