-- VersaEra V3: 탐험 지도(안개) · 보스 전투 기록 · 보스 보상

CREATE TABLE map_explored (
    uuid  TEXT NOT NULL,
    cx    INTEGER NOT NULL,
    cz    INTEGER NOT NULL,
    PRIMARY KEY (uuid, cx, cz)
);

CREATE TABLE boss_fight (
    id          TEXT PRIMARY KEY,
    boss_id     TEXT NOT NULL,
    state       TEXT NOT NULL CHECK (state IN ('ACTIVE', 'DEFEATED', 'FAILED')),
    started_at  INTEGER NOT NULL,
    ended_at    INTEGER
);

CREATE TABLE boss_contribution (
    fight_id  TEXT NOT NULL REFERENCES boss_fight (id),
    uuid      TEXT NOT NULL,
    damage    INTEGER NOT NULL DEFAULT 0,
    mitigated INTEGER NOT NULL DEFAULT 0,
    support   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (fight_id, uuid)
);
