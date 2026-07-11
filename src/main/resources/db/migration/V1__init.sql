-- =============================================================
-- V1: 초기 스키마 (4단계 ERD 반영)
-- 근거 문서: 4단계_시스템설계.md §4, ADR-002/004/005/007
-- 설계 노트:
--   * enum은 PG enum 타입 대신 varchar + CHECK — 값 추가 시 마이그레이션 부담 최소화
--   * name_chosung은 애플리케이션이 추출해 저장 (ADR-004: "애플리케이션 초성 추출 함수로 생성·저장")
--   * 소프트 삭제 없음 (collections는 행 삭제 — 3단계 M3 범위 제한)
-- =============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- -------------------------------------------------------------
-- users: 소셜 로그인 1종 (ADR-007)
-- -------------------------------------------------------------
CREATE TABLE users (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider     VARCHAR(20)  NOT NULL CHECK (provider IN ('KAKAO')),
    provider_id  VARCHAR(255) NOT NULL,
    nickname     VARCHAR(50)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_provider UNIQUE (provider, provider_id)
);

-- -------------------------------------------------------------
-- refresh_tokens: DB 저장 + 회전 (ADR-007 — 강제 로그아웃/탈취 대응)
-- -------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- -------------------------------------------------------------
-- games: BGG 온디맨드 캐시 + 유저 직접 등록 (ADR-003)
-- -------------------------------------------------------------
CREATE TABLE games (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bgg_id             BIGINT,                          -- custom 게임은 NULL
    source             VARCHAR(10) NOT NULL CHECK (source IN ('BGG', 'CUSTOM')),
    created_by_user_id BIGINT REFERENCES users (id),    -- custom 게임의 소유자 스코프
    name_ko            VARCHAR(200),
    name_en            VARCHAR(200),
    name_chosung       VARCHAR(200),                    -- 앱에서 name_ko로부터 추출·저장
    thumbnail_url      VARCHAR(500),
    min_players        SMALLINT,
    max_players        SMALLINT,
    playtime_minutes   SMALLINT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_games_bgg_id UNIQUE (bgg_id),
    CONSTRAINT chk_games_has_name CHECK (name_ko IS NOT NULL OR name_en IS NOT NULL),
    CONSTRAINT chk_games_custom_owner CHECK (source <> 'CUSTOM' OR created_by_user_id IS NOT NULL)
);
-- 한글/영문/초성 검색 (ADR-004: pg_trgm)
CREATE INDEX idx_games_name_ko_trgm      ON games USING gin (name_ko gin_trgm_ops);
CREATE INDEX idx_games_name_en_trgm      ON games USING gin (name_en gin_trgm_ops);
CREATE INDEX idx_games_name_chosung_trgm ON games USING gin (name_chosung gin_trgm_ops);

-- -------------------------------------------------------------
-- collections: 보유/위시 2상태만 (3단계 M3)
-- -------------------------------------------------------------
CREATE TABLE collections (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    game_id    BIGINT      NOT NULL REFERENCES games (id),
    status     VARCHAR(10) NOT NULL CHECK (status IN ('OWNED', 'WISHED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_collections_user_game UNIQUE (user_id, game_id)
);
CREATE INDEX idx_collections_user_status ON collections (user_id, status);

-- -------------------------------------------------------------
-- plays: 점수·플레이어 없이 존재 가능 (10초 기록의 데이터 표현)
-- -------------------------------------------------------------
CREATE TABLE plays (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    game_id         BIGINT      NOT NULL REFERENCES games (id),
    played_at       DATE        NOT NULL DEFAULT CURRENT_DATE,
    note            TEXT,
    rating          SMALLINT CHECK (rating BETWEEN 1 AND 5),
    photo_key       VARCHAR(300),
    idempotency_key UUID,       -- 클라 생성, 재시도 중복 방지 (API 스펙 §5)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_plays_user_played_at ON plays (user_id, played_at DESC);
CREATE INDEX idx_plays_user_game      ON plays (user_id, game_id);
CREATE UNIQUE INDEX uq_plays_idempotency ON plays (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- -------------------------------------------------------------
-- players: 유저 스코프 로컬 명부 — 계정과 무연결 (소셜 그래프 금지, Won't: 커뮤니티)
-- -------------------------------------------------------------
CREATE TABLE players (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_players_user_name UNIQUE (user_id, name)
);

-- -------------------------------------------------------------
-- play_players: 기록-플레이어 N:M + 점수
-- -------------------------------------------------------------
CREATE TABLE play_players (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    play_id   BIGINT  NOT NULL REFERENCES plays (id) ON DELETE CASCADE,
    player_id BIGINT  NOT NULL REFERENCES players (id),
    score     INTEGER,
    is_winner BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_play_players UNIQUE (play_id, player_id)
);
CREATE INDEX idx_play_players_player ON play_players (player_id);

-- -------------------------------------------------------------
-- import_jobs: 인프로세스 비동기 잡, 상태는 DB가 원장 (ADR-005)
-- -------------------------------------------------------------
CREATE TABLE import_jobs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source         VARCHAR(10) NOT NULL CHECK (source IN ('BGSTATS', 'BGG')),
    status         VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    raw_payload    JSONB,       -- 원본 보존 (재처리 가능성). 완료 90일 후 NULL 처리 배치 예정
    result_summary JSONB,
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ
);
-- 유저당 동시 1잡 제한 (409 IMPORT_ALREADY_RUNNING의 DB 레벨 보장)
CREATE UNIQUE INDEX uq_import_jobs_active ON import_jobs (user_id)
    WHERE status IN ('PENDING', 'RUNNING');
