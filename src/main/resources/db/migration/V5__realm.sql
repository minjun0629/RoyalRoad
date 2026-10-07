-- VersaEra V5: 땅 · 개인 상점 · 성 · 국가 · 황제 (원작: 땅을 사고 상점을 열고 성을 짓고 국가를 세운다)

CREATE TABLE land_plot (
    world      TEXT NOT NULL,
    cx         INTEGER NOT NULL,
    cz         INTEGER NOT NULL,
    owner      TEXT NOT NULL,
    price      INTEGER NOT NULL,
    bought_at  INTEGER NOT NULL,
    PRIMARY KEY (world, cx, cz)
);
CREATE INDEX idx_plot_owner ON land_plot (owner);

CREATE TABLE plot_member (
    world  TEXT NOT NULL,
    cx     INTEGER NOT NULL,
    cz     INTEGER NOT NULL,
    uuid   TEXT NOT NULL,
    PRIMARY KEY (world, cx, cz, uuid)
);

CREATE TABLE player_shop (
    id          TEXT PRIMARY KEY,
    owner       TEXT NOT NULL,
    world       TEXT NOT NULL,
    x           INTEGER NOT NULL,
    y           INTEGER NOT NULL,
    z           INTEGER NOT NULL,
    name        TEXT NOT NULL,
    created_at  INTEGER NOT NULL,
    UNIQUE (world, x, y, z)
);

-- 상점 물건은 서버가 쥔다: 고유 아이템은 item_instance.custody = ESCROW("shop:<stock id>"), 묶음은 수량만
CREATE TABLE shop_stock (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id  TEXT NOT NULL REFERENCES player_shop (id),
    type_id  TEXT NOT NULL,
    quality  INTEGER NOT NULL,
    amount   INTEGER NOT NULL CHECK (amount >= 0),
    item_id  TEXT,
    price    INTEGER NOT NULL CHECK (price > 0)
);
CREATE INDEX idx_stock_shop ON shop_stock (shop_id);

CREATE TABLE castle (
    region       TEXT PRIMARY KEY,
    guild_id     TEXT NOT NULL,
    tax_pct      INTEGER NOT NULL DEFAULT 5 CHECK (tax_pct BETWEEN 0 AND 20),
    since        INTEGER NOT NULL,
    last_income  INTEGER NOT NULL
);

CREATE TABLE nation (
    id          TEXT PRIMARY KEY,
    guild_id    TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL UNIQUE,
    founded_at  INTEGER NOT NULL
);

CREATE TABLE emperor (
    id          INTEGER PRIMARY KEY CHECK (id = 1),
    nation_id   TEXT NOT NULL,
    guild_id    TEXT NOT NULL,
    leader      TEXT NOT NULL,
    crowned_at  INTEGER NOT NULL
);
