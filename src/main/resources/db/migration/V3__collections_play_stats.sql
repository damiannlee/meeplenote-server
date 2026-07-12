-- M4: 컬렉션 화면에 플레이 횟수·최근 플레이일을 표시하기 위한 캐시 컬럼.
-- play 모듈이 기록 저장 시 갱신한다 (collection→play 역방향 조회는 모듈 순환 의존을 만들기 때문에 피함).
ALTER TABLE collections
    ADD COLUMN play_count     INT  NOT NULL DEFAULT 0,
    ADD COLUMN last_played_at DATE;
