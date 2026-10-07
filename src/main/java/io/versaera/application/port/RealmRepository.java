package io.versaera.application.port;

import java.util.List;
import java.util.Optional;

/** 땅 · 개인 상점 · 성 · 국가 · 황제 */
public interface RealmRepository {
    record Plot(String world, int cx, int cz, String owner, long price, long boughtAt) {}

    record Shop(String id, String owner, String world, int x, int y, int z, String name, long createdAt) {}

    record Stock(long id, String shopId, String typeId, int quality, int amount, String itemId, long price) {}

    record Castle(String region, String guildId, int taxPct, long since, long lastIncome) {}

    record Nation(String id, String guildId, String name, long foundedAt) {}

    record Emperor(String nationId, String guildId, String leader, long crownedAt) {}

    // 땅
    Optional<Plot> plot(String world, int cx, int cz);

    List<Plot> allPlots();

    List<Plot> plotsOf(String owner);

    boolean insertPlot(Plot p);

    void deletePlot(String world, int cx, int cz);

    List<String> members(String world, int cx, int cz);

    void addMember(String world, int cx, int cz, String uuid);

    void removeMember(String world, int cx, int cz, String uuid);

    // 상점
    void insertShop(Shop s);

    Optional<Shop> shop(String id);

    Optional<Shop> shopAt(String world, int x, int y, int z);

    List<Shop> shopsOf(String owner);

    List<Shop> allShops();

    void deleteShop(String id);

    long insertStock(Stock s);

    Optional<Stock> stock(long id);

    List<Stock> stockOf(String shopId);

    void setStockAmount(long id, int amount);

    void deleteStock(long id);

    // 성 · 국가 · 황제
    Optional<Castle> castle(String region);

    List<Castle> castles();

    void upsertCastle(Castle c);

    Optional<Nation> nationOfGuild(String guildId);

    List<Nation> nations();

    boolean insertNation(Nation n);

    Optional<Emperor> emperor();

    boolean crown(Emperor e);
}
