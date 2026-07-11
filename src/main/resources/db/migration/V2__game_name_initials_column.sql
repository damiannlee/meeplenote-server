-- =============================================================
-- V2: games 검색 컬럼명 정정
-- 근거: docs/adr/ADR-004_한글검색pg_trgm초성컬럼.md 구현 노트
-- V1의 name_chosung(초성을 로마자로 그대로 옮긴 이름)을
-- 영문 의미 명칭 name_initials로 리네임. 머지된 V1은 직접 수정하지 않는다.
-- =============================================================

ALTER TABLE games RENAME COLUMN name_chosung TO name_initials;
ALTER INDEX idx_games_name_chosung_trgm RENAME TO idx_games_name_initials_trgm;
