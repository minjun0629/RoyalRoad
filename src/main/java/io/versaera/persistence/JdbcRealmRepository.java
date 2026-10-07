package io.versaera.persistence;

import io.versaera.application.port.RealmRepository;

import java.util.List;
import java.util.Optional;

public final class JdbcRealmRepository implements RealmRepository {
    private final Jdbc j;

    public JdbcRealmRepository(Database db) {
        this.j = new Jdbc(db);
    }

    private static final String PLOT = "SELECT world, cx, cz, owner, price, bought_at FROM land_plot";

    private static Plot plot(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Plot(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getLong(5), rs.getLong(6));
    }

    @Override
    public Optional<Plot> plot(String world, int cx, int cz) {
        return Optional.ofNullable(j.one(PLOT + " WHERE world = ? AND cx = ? AND cz = ?", ps -> {
            ps.setString(1, world);
            ps.setInt(2, cx);
            ps.setInt(3, cz);
        }, JdbcRealmRepository::plot, null));
    }

    @Override
    public List<Plot> allPlots() {
        return j.query(PLOT, ps -> { }, JdbcRealmRepository::plot);
    }

    @Override
    public List<Plot> plotsOf(String owner) {
        return j.query(PLOT + " WHERE owner = ?", ps -> ps.setString(1, owner), JdbcRealmRepository::plot);
    }

    @Override
    public boolean insertPlot(Plot p) {
        return j.update("INSERT OR IGNORE INTO land_plot (world, cx, cz, owner, price, bought_at) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, p.world());
            ps.setInt(2, p.cx());
            ps.setInt(3, p.cz());
            ps.setString(4, p.owner());
            ps.setLong(5, p.price());
            ps.setLong(6, p.boughtAt());
        }) == 1;
    }

    @Override
    public void deletePlot(String world, int cx, int cz) {
        Jdbc.Binder b = ps -> {
            ps.setString(1, world);
            ps.setInt(2, cx);
            ps.setInt(3, cz);
        };
        j.update("DELETE FROM plot_member WHERE world = ? AND cx = ? AND cz = ?", b);
        j.update("DELETE FROM land_plot WHERE world = ? AND cx = ? AND cz = ?", b);
    }

    @Override
    public List<String> members(String world, int cx, int cz) {
        return j.query("SELECT uuid FROM plot_member WHERE world = ? AND cx = ? AND cz = ?", ps -> {
            ps.setString(1, world);
            ps.setInt(2, cx);
            ps.setInt(3, cz);
        }, rs -> rs.getString(1));
    }

    @Override
    public void addMember(String world, int cx, int cz, String uuid) {
        j.update("INSERT OR IGNORE INTO plot_member (world, cx, cz, uuid) VALUES (?, ?, ?, ?)", ps -> {
            ps.setString(1, world);
            ps.setInt(2, cx);
            ps.setInt(3, cz);
            ps.setString(4, uuid);
        });
    }

    @Override
    public void removeMember(String world, int cx, int cz, String uuid) {
        j.update("DELETE FROM plot_member WHERE world = ? AND cx = ? AND cz = ? AND uuid = ?", ps -> {
            ps.setString(1, world);
            ps.setInt(2, cx);
            ps.setInt(3, cz);
            ps.setString(4, uuid);
        });
    }

    private static final String SHOP = "SELECT id, owner, world, x, y, z, name, created_at FROM player_shop";

    private static Shop shop(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Shop(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getLong(8));
    }

    @Override
    public void insertShop(Shop s) {
        j.update("INSERT INTO player_shop (id, owner, world, x, y, z, name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, s.id());
            ps.setString(2, s.owner());
            ps.setString(3, s.world());
            ps.setInt(4, s.x());
            ps.setInt(5, s.y());
            ps.setInt(6, s.z());
            ps.setString(7, s.name());
            ps.setLong(8, s.createdAt());
        });
    }

    @Override
    public Optional<Shop> shop(String id) {
        return Optional.ofNullable(j.one(SHOP + " WHERE id = ?", ps -> ps.setString(1, id), JdbcRealmRepository::shop, null));
    }

    @Override
    public Optional<Shop> shopAt(String world, int x, int y, int z) {
        return Optional.ofNullable(j.one(SHOP + " WHERE world = ? AND x = ? AND y = ? AND z = ?", ps -> {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
        }, JdbcRealmRepository::shop, null));
    }

    @Override
    public List<Shop> shopsOf(String owner) {
        return j.query(SHOP + " WHERE owner = ?", ps -> ps.setString(1, owner), JdbcRealmRepository::shop);
    }

    @Override
    public List<Shop> allShops() {
        return j.query(SHOP, ps -> { }, JdbcRealmRepository::shop);
    }

    @Override
    public void deleteShop(String id) {
        j.update("DELETE FROM shop_stock WHERE shop_id = ?", ps -> ps.setString(1, id));
        j.update("DELETE FROM player_shop WHERE id = ?", ps -> ps.setString(1, id));
    }

    private static final String STOCK = "SELECT id, shop_id, type_id, quality, amount, item_id, price FROM shop_stock";

    private static Stock stock(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Stock(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getString(6), rs.getLong(7));
    }

    @Override
    public long insertStock(Stock s) {
        return j.insertKey("INSERT INTO shop_stock (shop_id, type_id, quality, amount, item_id, price) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, s.shopId());
            ps.setString(2, s.typeId());
            ps.setInt(3, s.quality());
            ps.setInt(4, s.amount());
            ps.setString(5, s.itemId());
            ps.setLong(6, s.price());
        });
    }

    @Override
    public Optional<Stock> stock(long id) {
        return Optional.ofNullable(j.one(STOCK + " WHERE id = ?", ps -> ps.setLong(1, id), JdbcRealmRepository::stock, null));
    }

    @Override
    public List<Stock> stockOf(String shopId) {
        return j.query(STOCK + " WHERE shop_id = ? ORDER BY id", ps -> ps.setString(1, shopId), JdbcRealmRepository::stock);
    }

    @Override
    public void setStockAmount(long id, int amount) {
        j.update("UPDATE shop_stock SET amount = ? WHERE id = ?", ps -> {
            ps.setInt(1, amount);
            ps.setLong(2, id);
        });
    }

    @Override
    public void deleteStock(long id) {
        j.update("DELETE FROM shop_stock WHERE id = ?", ps -> ps.setLong(1, id));
    }

    private static final String CASTLE = "SELECT region, guild_id, tax_pct, since, last_income FROM castle";

    private static Castle castle(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Castle(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getLong(4), rs.getLong(5));
    }

    @Override
    public Optional<Castle> castle(String region) {
        return Optional.ofNullable(j.one(CASTLE + " WHERE region = ?", ps -> ps.setString(1, region), JdbcRealmRepository::castle, null));
    }

    @Override
    public List<Castle> castles() {
        return j.query(CASTLE, ps -> { }, JdbcRealmRepository::castle);
    }

    @Override
    public void upsertCastle(Castle c) {
        j.update("""
                INSERT INTO castle (region, guild_id, tax_pct, since, last_income) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (region) DO UPDATE SET guild_id = excluded.guild_id, tax_pct = excluded.tax_pct, since = excluded.since,
                    last_income = excluded.last_income""", ps -> {
            ps.setString(1, c.region());
            ps.setString(2, c.guildId());
            ps.setInt(3, c.taxPct());
            ps.setLong(4, c.since());
            ps.setLong(5, c.lastIncome());
        });
    }

    private static Nation nation(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Nation(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4));
    }

    @Override
    public Optional<Nation> nationOfGuild(String guildId) {
        return Optional.ofNullable(j.one("SELECT id, guild_id, name, founded_at FROM nation WHERE guild_id = ?", ps -> ps.setString(1, guildId),
                JdbcRealmRepository::nation, null));
    }

    @Override
    public List<Nation> nations() {
        return j.query("SELECT id, guild_id, name, founded_at FROM nation", ps -> { }, JdbcRealmRepository::nation);
    }

    @Override
    public boolean insertNation(Nation n) {
        return j.update("INSERT OR IGNORE INTO nation (id, guild_id, name, founded_at) VALUES (?, ?, ?, ?)", ps -> {
            ps.setString(1, n.id());
            ps.setString(2, n.guildId());
            ps.setString(3, n.name());
            ps.setLong(4, n.foundedAt());
        }) == 1;
    }

    @Override
    public Optional<Emperor> emperor() {
        return Optional.ofNullable(j.one("SELECT nation_id, guild_id, leader, crowned_at FROM emperor WHERE id = 1", ps -> { },
                rs -> new Emperor(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)), null));
    }

    @Override
    public boolean crown(Emperor e) {
        return j.update("INSERT OR IGNORE INTO emperor (id, nation_id, guild_id, leader, crowned_at) VALUES (1, ?, ?, ?, ?)", ps -> {
            ps.setString(1, e.nationId());
            ps.setString(2, e.guildId());
            ps.setString(3, e.leader());
            ps.setLong(4, e.crownedAt());
        }) == 1;
    }
}
