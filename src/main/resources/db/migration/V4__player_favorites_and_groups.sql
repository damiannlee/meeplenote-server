-- 플레이어 관리: 즐겨찾기, 그룹(N:M). players는 여전히 계정과 무연결인 유저 스코프 로컬 명부(Won't: 커뮤니티).
ALTER TABLE players
    ADD COLUMN is_favorite BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE player_groups (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_player_groups_user_name UNIQUE (user_id, name)
);

CREATE TABLE player_group_members (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id  BIGINT NOT NULL REFERENCES player_groups (id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    CONSTRAINT uq_player_group_members UNIQUE (group_id, player_id)
);
CREATE INDEX idx_player_group_members_player ON player_group_members (player_id);
