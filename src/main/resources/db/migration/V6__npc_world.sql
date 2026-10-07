-- VersaEra V6: NPC 기억 · 지역 번영 (NPC-04 · WLD-04). 기존 데이터는 건드리지 않는다.

-- NPC 가 플레이어에 대해 기억하는 일 (부탁을 들어줌 · 선물 · 적을 쓰러뜨림 …). npc_id 가 "region:<id>" 면 그 지역 사람들이 함께 기억하는 일
CREATE TABLE npc_memory (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid     TEXT NOT NULL,
    npc_id   TEXT NOT NULL,
    kind     TEXT NOT NULL,
    detail   TEXT NOT NULL,
    weight   INTEGER NOT NULL DEFAULT 1,
    at       INTEGER NOT NULL
);
CREATE INDEX idx_npc_memory ON npc_memory (uuid, npc_id, at);

-- 지역 번영: 플레이어의 도움(의뢰 · 거래 · 보스 처치)으로 오르고, 시간이 지나면 0 쪽으로 돌아간다
CREATE TABLE region_state (
    region      TEXT PRIMARY KEY,
    prosperity  INTEGER NOT NULL DEFAULT 0,
    updated_at  INTEGER NOT NULL
);

-- 같은 일로 번영이 두 번 오르지 않게
CREATE TABLE region_contribution (
    request_key TEXT PRIMARY KEY,
    region      TEXT NOT NULL,
    amount      INTEGER NOT NULL,
    at          INTEGER NOT NULL
);
