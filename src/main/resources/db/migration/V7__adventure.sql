-- VersaEra V7: 칭호 · 펫 · 탈것 · 여행 · 길드 창고/의뢰 · 레이드 · 대형 조각. 기존 데이터는 건드리지 않는다 (새 테이블만).
-- 업적 · 얻은 칭호는 기존 discovery 표에 kind = 'achievement' · 'title' 로 남는다.

-- 달고 다니는 칭호 (하나)
CREATE TABLE player_title (
    uuid      TEXT PRIMARY KEY,
    title     TEXT NOT NULL,
    set_at    INTEGER NOT NULL
);

-- 길들인 동물
CREATE TABLE pet (
    id          TEXT PRIMARY KEY,
    owner       TEXT NOT NULL,
    species     TEXT NOT NULL,
    name        TEXT NOT NULL,
    level       INTEGER NOT NULL DEFAULT 1,
    xp          INTEGER NOT NULL DEFAULT 0,
    loyalty     INTEGER NOT NULL DEFAULT 50,
    fed_at      INTEGER NOT NULL,
    fainted_until INTEGER NOT NULL DEFAULT 0,
    released    INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL
);
CREATE INDEX idx_pet_owner ON pet (owner, released);

-- 탈것 (마구간에서 사거나 길들임)
CREATE TABLE mount (
    id          TEXT PRIMARY KEY,
    owner       TEXT NOT NULL,
    kind        TEXT NOT NULL,
    name        TEXT NOT NULL,
    created_at  INTEGER NOT NULL
);
CREATE INDEX idx_mount_owner ON mount (owner);

-- 마차 · 배 여행 중 (서버가 꺼져도 도착 시각이 지나면 도착지에서 깨어난다)
CREATE TABLE travel_journey (
    uuid        TEXT PRIMARY KEY,
    route       TEXT NOT NULL,
    dest        TEXT NOT NULL,
    depart_at   INTEGER NOT NULL,
    arrive_at   INTEGER NOT NULL
);

-- 길드 창고 (묶음 재료) · 기록
CREATE TABLE guild_storage (
    guild_id    TEXT NOT NULL,
    type_id     TEXT NOT NULL,
    quality     INTEGER NOT NULL,
    amount      INTEGER NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (guild_id, type_id, quality)
);
CREATE TABLE guild_storage_log (
    request_key TEXT PRIMARY KEY,
    guild_id    TEXT NOT NULL,
    uuid        TEXT NOT NULL,
    type_id     TEXT NOT NULL,
    quality     INTEGER NOT NULL,
    delta       INTEGER NOT NULL,
    day         INTEGER NOT NULL,
    at          INTEGER NOT NULL
);
CREATE INDEX idx_guild_storage_log ON guild_storage_log (guild_id, uuid, day);

-- 길드 주간 의뢰 진행 · 기여
CREATE TABLE guild_quest (
    guild_id    TEXT NOT NULL,
    week        INTEGER NOT NULL,
    quest_id    TEXT NOT NULL,
    progress    INTEGER NOT NULL DEFAULT 0,
    done_at     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, week, quest_id)
);
CREATE TABLE guild_quest_contrib (
    guild_id    TEXT NOT NULL,
    week        INTEGER NOT NULL,
    quest_id    TEXT NOT NULL,
    uuid        TEXT NOT NULL,
    amount      INTEGER NOT NULL,
    PRIMARY KEY (guild_id, week, quest_id, uuid)
);

-- 레이드: 주간 귀속 · 공략 기록
CREATE TABLE raid_lockout (
    uuid        TEXT NOT NULL,
    raid_id     TEXT NOT NULL,
    week        INTEGER NOT NULL,
    PRIMARY KEY (uuid, raid_id, week)
);
CREATE TABLE raid_clear (
    run_id      TEXT PRIMARY KEY,
    raid_id     TEXT NOT NULL,
    leader      TEXT NOT NULL,
    members     TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    at          INTEGER NOT NULL
);
CREATE INDEX idx_raid_clear ON raid_clear (raid_id, duration_ms);

-- 대형 조각 작품 (여러 재료) · 하루 한 번 감상
CREATE TABLE artwork (
    id          TEXT PRIMARY KEY,
    owner       TEXT NOT NULL,
    kind        TEXT NOT NULL,
    title       TEXT NOT NULL,
    world       TEXT NOT NULL,
    x           INTEGER NOT NULL,
    y           INTEGER NOT NULL,
    z           INTEGER NOT NULL,
    yaw         INTEGER NOT NULL,
    quality     INTEGER NOT NULL,
    materials   TEXT NOT NULL,
    views       INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL
);
CREATE INDEX idx_artwork_world ON artwork (world, x, z);
CREATE TABLE artwork_view (
    artwork_id  TEXT NOT NULL,
    uuid        TEXT NOT NULL,
    day         INTEGER NOT NULL,
    PRIMARY KEY (artwork_id, uuid, day)
);
