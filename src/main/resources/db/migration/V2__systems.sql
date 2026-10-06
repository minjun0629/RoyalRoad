-- VersaEra V2: 직업 · 퀘스트 · 평판 · 길드 · 경매장 · 시세 · 던전 · 월드 이벤트 · 사망 기록

CREATE TABLE player_job (
    uuid        TEXT NOT NULL,
    slot        TEXT NOT NULL CHECK (slot IN ('COMBAT', 'LIFE')),
    job_id      TEXT NOT NULL,
    since       INTEGER NOT NULL,
    PRIMARY KEY (uuid, slot)
);

CREATE TABLE quest_progress (
    uuid          TEXT NOT NULL,
    quest_id      TEXT NOT NULL,
    state         TEXT NOT NULL CHECK (state IN ('ACTIVE', 'COMPLETED')),
    progress      TEXT NOT NULL DEFAULT '',
    choice        TEXT,
    times         INTEGER NOT NULL DEFAULT 0,
    accepted_at   INTEGER NOT NULL,
    completed_at  INTEGER,
    PRIMARY KEY (uuid, quest_id)
);

CREATE TABLE reputation (
    uuid     TEXT NOT NULL,
    faction  TEXT NOT NULL,
    value    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (uuid, faction)
);

CREATE TABLE guild (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    tag         TEXT NOT NULL UNIQUE,
    leader      TEXT NOT NULL,
    level       INTEGER NOT NULL DEFAULT 1,
    xp          INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL
);

CREATE TABLE guild_member (
    uuid          TEXT PRIMARY KEY,
    guild_id      TEXT NOT NULL REFERENCES guild (id),
    rank          TEXT NOT NULL CHECK (rank IN ('LEADER', 'OFFICER', 'MEMBER')),
    contribution  INTEGER NOT NULL DEFAULT 0,
    joined_at     INTEGER NOT NULL
);
CREATE INDEX idx_guild_member_guild ON guild_member (guild_id);

CREATE TABLE auction_listing (
    id          TEXT PRIMARY KEY,
    seller      TEXT NOT NULL,
    market      TEXT NOT NULL,
    kind        TEXT NOT NULL CHECK (kind IN ('UNIQUE', 'BULK')),
    item_id     TEXT,
    type_id     TEXT NOT NULL,
    quality     INTEGER NOT NULL,
    amount      INTEGER NOT NULL CHECK (amount > 0),
    price       INTEGER NOT NULL CHECK (price > 0),
    state       TEXT NOT NULL CHECK (state IN ('OPEN', 'SOLD', 'CANCELLED', 'EXPIRED')),
    buyer       TEXT,
    created_at  INTEGER NOT NULL,
    expires_at  INTEGER NOT NULL,
    closed_at   INTEGER
);
CREATE INDEX idx_auction_open ON auction_listing (state, market, type_id);

CREATE TABLE market_supply (
    market      TEXT NOT NULL,
    type_id     TEXT NOT NULL,
    supply      INTEGER NOT NULL DEFAULT 0,
    updated_at  INTEGER NOT NULL,
    PRIMARY KEY (market, type_id)
);

CREATE TABLE dungeon_run (
    id           TEXT PRIMARY KEY,
    dungeon_id   TEXT NOT NULL,
    seed         INTEGER NOT NULL,
    state        TEXT NOT NULL CHECK (state IN ('ACTIVE', 'CLEARED', 'FAILED')),
    members      TEXT NOT NULL,
    started_at   INTEGER NOT NULL,
    ended_at     INTEGER
);

CREATE TABLE world_event_state (
    event_id    TEXT PRIMARY KEY,
    active      INTEGER NOT NULL DEFAULT 0,
    started_at  INTEGER NOT NULL DEFAULT 0,
    next_at     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE death_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid        TEXT NOT NULL,
    region      TEXT,
    danger      INTEGER NOT NULL,
    xp_lost     INTEGER NOT NULL,
    created_at  INTEGER NOT NULL
);
