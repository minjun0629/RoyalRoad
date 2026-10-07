-- VersaEra V4: 캐릭터 만들기 (종족 · 성별 · 시작 도시) · 접속 제한 (원작식 사망 페널티)

CREATE TABLE character_origin (
    uuid        TEXT PRIMARY KEY,
    race        TEXT NOT NULL,
    gender      TEXT NOT NULL CHECK (gender IN ('MALE', 'FEMALE', 'NEUTRAL')),
    city        TEXT NOT NULL,
    created_at  INTEGER NOT NULL
);

CREATE TABLE login_lock (
    uuid    TEXT PRIMARY KEY,
    until   INTEGER NOT NULL,
    reason  TEXT NOT NULL
);
