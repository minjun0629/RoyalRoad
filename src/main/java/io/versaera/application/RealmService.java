package io.versaera.application;

import io.versaera.application.port.GuildRepository;
import io.versaera.application.port.RealmRepository;
import io.versaera.application.port.RealmRepository.*;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.realm.RealmRules;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 땅 · 개인 상점 · 성 · 공성 · 국가 · 황제 (LND-01 · SHP-01 · CST-01 · NAT-01).
 * 원작(나무위키 「로열 로드」 §6.2): 유저는 땅을 사고 상점을 열고 성을 짓고 국가를 세울 수 있다. 대륙을 처음 통일한 황제에게 상금.
 * <ul>
 *   <li>돈은 모두 원장(EconomyService)으로, 아이템은 서버가 쥔다 — 상점의 고유 아이템은 ESCROW, 산 물건은 배달함으로</li>
 *   <li>공성 진행(누가 성 한가운데 있나)은 플랫폼이 세고, 판정 · 소유 이전은 여기서</li>
 *   <li>황제: 한 국가(길드)가 시작 도시 6곳(왕국의 수도)을 모두 가지면 — 서버에서 처음 한 번만</li>
 * </ul>
 */
public final class RealmService {
    public record Siege(String region, String attacker, String defender, long endsAt) {}

    public record Crowning(Nation nation, GuildRepository.Guild guild, long reward) {}

    private static final long DAY = 86_400_000L;
    private final TxRunner tx;
    private final RealmRepository repo;
    private final GameServices s;
    private final RegionIndex regions;
    private final GameClock clock;
    private final List<int[]> npcSpots;
    private final Set<String> capitals = new LinkedHashSet<>();
    private final Map<String, Siege> sieges = new ConcurrentHashMap<>();
    private volatile long emperorReward = 100 * io.versaera.domain.economy.Money.GOLD;

    RealmService(TxRunner tx, RealmRepository repo, GameServices s, RegionIndex regions, GameClock clock, List<int[]> npcSpots) {
        this.tx = tx;
        this.repo = repo;
        this.s = s;
        this.regions = regions;
        this.clock = clock;
        this.npcSpots = List.copyOf(npcSpots);
        for (var c : s.content.origins().cities()) capitals.add(c.region());
        // 서버가 꺼졌다 켜져도 공성은 이어진다 (끝나는 시각은 실제 시각 — 꺼진 동안에도 흐른다)
        s.state.loadAll(SIEGE).forEach((region, d) -> sieges.put(region, new Siege(region, d.get("attacker"), d.get("defender"), Long.parseLong(d.get("ends")))));
    }

    static final String SIEGE = "siege";

    private void saveSiege(Siege sg, int held) {
        s.state.save(SIEGE, sg.region(), Map.of("attacker", sg.attacker(), "defender", sg.defender(), "ends", Long.toString(sg.endsAt()),
                "held", Integer.toString(held)), sg.endsAt());
    }

    /** 공격 측이 성 한가운데를 지킨 초 (런타임이 몇 초마다 저장 · 켤 때 읽는다) */
    public void saveCaptureProgress(String region, int seconds) {
        Siege sg = sieges.get(region);
        if (sg != null) saveSiege(sg, seconds);
    }

    public int captureProgress(String region) {
        return s.state.load(SIEGE, region).map(d -> Integer.parseInt(d.getOrDefault("held", "0"))).orElse(0);
    }

    /** @param gold 골드 단위 (config emperor.reward_gold) */
    public void emperorReward(long gold) {
        emperorReward = Math.max(0, gold) * io.versaera.domain.economy.Money.GOLD;
    }

    public Set<String> capitals() {
        return Collections.unmodifiableSet(capitals);
    }

    private Region centerRegion(String world, int cx, int cz) {
        return regions.at(world, cx * 16 + 8, 70, cz * 16 + 8);
    }

    // ================================================================== 땅
    public Optional<Plot> plot(String world, int cx, int cz) {
        return repo.plot(world, cx, cz);
    }

    public List<Plot> allPlots() {
        return repo.allPlots();
    }

    public List<Plot> plotsOf(String uuid) {
        return repo.plotsOf(uuid);
    }

    public List<String> members(String world, int cx, int cz) {
        return repo.members(world, cx, cz);
    }

    public long plotPrice(String world, int cx, int cz) {
        return RealmRules.plotPrice(centerRegion(world, cx, cz));
    }

    public Plot buyPlot(String uuid, String world, int cx, int cz, String key) {
        Region r = centerRegion(world, cx, cz);
        long price = RealmRules.plotPrice(r);
        DomainException.require(price > 0, "land.forbidden", "여기는 살 수 없는 땅입니다 (금역 · 던전 · 명소 · 바다)");
        for (int[] p : npcSpots)
            DomainException.require(!(Math.floorDiv(p[0], 16) == cx && Math.floorDiv(p[1], 16) == cz) || !"world".equals(world), "land.npc", "주민이 일하는 자리라 살 수 없습니다");
        Plot plot = new Plot(world, cx, cz, uuid, price, clock.nowMillis());
        tx.inTx(() -> {
            DomainException.require(repo.plotsOf(uuid).size() < RealmRules.MAX_PLOTS, "land.limit", "땅은 " + RealmRules.MAX_PLOTS + "칸까지 가질 수 있습니다");
            DomainException.require(repo.insertPlot(plot), "land.taken", "이미 주인이 있는 땅입니다");
            if (!s.economy.withdraw(uuid, price, "land", key)) throw DomainException.of("land.duplicate", "이미 처리한 구입입니다");
            payCastleTax(regionId(r), price, key);
            return null;
        });
        return plot;
    }

    public long sellPlot(String uuid, String world, int cx, int cz, String key) {
        return tx.inTx(() -> {
            Plot p = repo.plot(world, cx, cz).orElseThrow(() -> DomainException.of("land.none", "주인 없는 땅입니다"));
            DomainException.require(p.owner().equals(uuid), "land.not_owner", "내 땅이 아닙니다");
            for (Shop sh : repo.shopsOf(uuid))
                DomainException.require(!(sh.world().equals(world) && Math.floorDiv(sh.x(), 16) == cx && Math.floorDiv(sh.z(), 16) == cz), "land.shop", "이 땅의 상점을 먼저 닫으세요");
            repo.deletePlot(world, cx, cz);
            long refund = RealmRules.refund(p.price());
            if (refund > 0) s.economy.deposit(uuid, refund, "land_sell", key);
            return refund;
        });
    }

    public void trust(String owner, String world, int cx, int cz, String member, boolean add) {
        tx.inTx(() -> {
            Plot p = repo.plot(world, cx, cz).orElseThrow(() -> DomainException.of("land.none", "주인 없는 땅입니다"));
            DomainException.require(p.owner().equals(owner), "land.not_owner", "내 땅이 아닙니다");
            if (add) repo.addMember(world, cx, cz, member);
            else repo.removeMember(world, cx, cz, member);
            return null;
        });
    }

    // ================================================================== 개인 상점
    public Shop openShop(String uuid, String world, int x, int y, int z, String name) {
        DomainException.require(name != null && !name.isBlank() && name.length() <= 24, "shop.name", "상점 이름은 1 ~ 24자");
        return tx.inTx(() -> {
            Plot p = repo.plot(world, Math.floorDiv(x, 16), Math.floorDiv(z, 16)).orElse(null);
            DomainException.require(p != null && p.owner().equals(uuid), "shop.not_my_land", "내 땅에서만 상점을 열 수 있습니다");
            DomainException.require(repo.shopsOf(uuid).size() < RealmRules.MAX_SHOPS, "shop.limit", "상점은 " + RealmRules.MAX_SHOPS + "개까지");
            DomainException.require(repo.shopAt(world, x, y, z).isEmpty(), "shop.taken", "이미 상점인 자리입니다");
            Shop sh = new Shop(UUID.randomUUID().toString(), uuid, world, x, y, z, name, clock.nowMillis());
            repo.insertShop(sh);
            return sh;
        });
    }

    public Optional<Shop> shopAt(String world, int x, int y, int z) {
        return repo.shopAt(world, x, y, z);
    }

    public Optional<Shop> shop(String id) {
        return repo.shop(id);
    }

    public List<Shop> allShops() {
        return repo.allShops();
    }

    public List<Stock> stock(String shopId) {
        return repo.stockOf(shopId);
    }

    public void closeShop(String uuid, String shopId) {
        tx.inTx(() -> {
            Shop sh = ownShop(uuid, shopId);
            DomainException.require(repo.stockOf(sh.id()).isEmpty(), "shop.not_empty", "물건을 모두 내린 뒤에 닫을 수 있습니다");
            repo.deleteShop(sh.id());
            return null;
        });
    }

    private Shop ownShop(String uuid, String shopId) {
        Shop sh = repo.shop(shopId).orElseThrow(() -> DomainException.of("shop.none", "없는 상점"));
        DomainException.require(sh.owner().equals(uuid), "shop.not_owner", "내 상점이 아닙니다");
        return sh;
    }

    /** 고유 아이템 올리기 — 아이템은 ESCROW("shop:<id>") 로 옮겨져 인벤토리 · 거래 · 경매에 동시에 있을 수 없다 */
    public Stock stockUnique(String uuid, String shopId, String itemId, long price) {
        DomainException.require(price > 0, "shop.price", "가격은 1 이상");
        return tx.inTx(() -> {
            ownShop(uuid, shopId);
            DomainException.require(repo.stockOf(shopId).size() < RealmRules.MAX_STOCK, "shop.full", "상점 칸이 가득 찼습니다 (" + RealmRules.MAX_STOCK + ")");
            var it = s.items.find(itemId).orElseThrow(() -> DomainException.of("item.unknown", "없는 아이템"));
            long id = repo.insertStock(new Stock(0, shopId, it.typeId(), it.quality(), 1, itemId, price));
            s.items.toEscrowInTx(itemId, uuid, "shop:" + id);
            return repo.stock(id).orElseThrow();
        });
    }

    /** 묶음 올리기 — 플랫폼이 인벤토리에서 먼저 뺀 뒤 부른다 (실패하면 플랫폼이 배달함으로 돌려줌) */
    public Stock stockBulk(String uuid, String shopId, String typeId, int quality, int amount, long price) {
        DomainException.require(price > 0 && amount > 0, "shop.price", "가격 · 수량은 1 이상");
        DomainException.require(!s.items.types().get(typeId).category().unique(), "shop.unique", "고유 아이템은 따로 올립니다");
        return tx.inTx(() -> {
            ownShop(uuid, shopId);
            DomainException.require(repo.stockOf(shopId).size() < RealmRules.MAX_STOCK, "shop.full", "상점 칸이 가득 찼습니다 (" + RealmRules.MAX_STOCK + ")");
            long id = repo.insertStock(new Stock(0, shopId, typeId, quality, amount, null, price));
            return repo.stock(id).orElseThrow();
        });
    }

    /** 사기: 돈은 주인에게 (성 세금은 성 주인 길드에게), 물건은 산 사람의 배달함으로 */
    public Stock buy(String buyer, long stockId, int amount, String key) {
        DomainException.require(amount > 0, "shop.amount", "수량은 1 이상");
        return tx.inTx(() -> {
            Stock st = repo.stock(stockId).orElseThrow(() -> DomainException.of("shop.sold", "이미 팔린 물건입니다"));
            Shop sh = repo.shop(st.shopId()).orElseThrow();
            DomainException.require(!sh.owner().equals(buyer), "shop.self", "내 상점 물건은 '내리기'로 회수합니다");
            DomainException.require(amount <= st.amount(), "shop.amount", "남은 수량은 " + st.amount() + "개");
            long total = st.price() * amount;
            Region r = regions.at(sh.world(), sh.x(), sh.y(), sh.z());
            Castle c = castleOf(regionId(r)).orElse(null);
            long tax = c == null ? 0 : RealmRules.tax(total, c.taxPct());
            if (!s.economy.transfer(buyer, sh.owner(), total - tax, "shop:" + sh.id(), key + ":pay")) throw DomainException.of("shop.duplicate", "이미 처리한 구매");
            if (tax > 0) s.economy.transfer(buyer, GuildService.wallet(c.guildId()), tax, "castle_tax:" + c.region(), key + ":tax");
            if (st.itemId() != null) {
                s.items.fromEscrowInTx(st.itemId(), "shop:" + st.id(), buyer);
                repo.deleteStock(st.id());
            } else {
                s.items.deliverBulkInTx(buyer, st.typeId(), st.quality(), amount, "shop");
                if (st.amount() == amount) repo.deleteStock(st.id());
                else repo.setStockAmount(st.id(), st.amount() - amount);
            }
            s.audit.record("SHOP_SOLD", buyer, st.typeId() + "x" + amount, total + " (tax " + tax + ")", key);
            return st;
        });
    }

    /** 내 물건 내리기 → 내 배달함 */
    public void withdrawStock(String owner, long stockId) {
        tx.inTx(() -> {
            Stock st = repo.stock(stockId).orElseThrow(() -> DomainException.of("shop.sold", "이미 없는 물건입니다"));
            ownShop(owner, st.shopId());
            if (st.itemId() != null) s.items.fromEscrowInTx(st.itemId(), "shop:" + st.id(), owner);
            else s.items.deliverBulkInTx(owner, st.typeId(), st.quality(), st.amount(), "shop_withdraw");
            repo.deleteStock(st.id());
            return null;
        });
    }

    // ================================================================== 성
    private static String regionId(Region r) {
        return r == null ? null : r.id();
    }

    /** 성이 될 수 있는 지역 (도시 · 성 · 전초기지, 세계 world) */
    public List<Region> castleRegions() {
        List<Region> out = new ArrayList<>();
        for (Region r : regions.all()) if ("world".equals(r.world()) && RealmRules.town(r)) out.add(r);
        return out;
    }

    /** 이 지역(또는 감싼 지역)이 속한 성 지역 id */
    public Optional<String> castleRegionAt(String regionId) {
        for (Region r = regionId == null ? null : regions.byId(regionId); r != null; r = r.parent() == null ? null : regions.byId(r.parent()))
            if (RealmRules.town(r)) return Optional.of(r.id());
        return Optional.empty();
    }

    /** 이 지역이 속한 성의 주인 기록 (주인이 있을 때) */
    public Optional<Castle> castleOf(String regionId) {
        return castleRegionAt(regionId).flatMap(repo::castle);
    }

    public Optional<Castle> castle(String region) {
        return repo.castle(region);
    }

    public List<Castle> ownedCastles() {
        return repo.castles();
    }

    public long castlePrice(String region) {
        Region r = regions.byId(region);
        DomainException.require(r != null && RealmRules.town(r), "castle.none", "성이 아닌 곳입니다");
        return RealmRules.castlePrice(r, capitals.contains(region));
    }

    private GuildRepository.Guild leaderGuild(String uuid) {
        GuildRepository.Guild g = s.guilds.guildOf(uuid).orElseThrow(() -> DomainException.of("castle.no_guild", "길드에 들어 있어야 합니다"));
        DomainException.require(g.leader().equals(uuid), "castle.not_leader", "길드장만 할 수 있습니다");
        return g;
    }

    private void payCastleTax(String regionId, long amount, String key) {
        Castle c = castleOf(regionId).orElse(null);
        if (c == null) return;
        long tax = RealmRules.tax(amount, c.taxPct());
        if (tax > 0) s.economy.deposit(GuildService.wallet(c.guildId()), tax, "castle_tax:" + c.region(), key + ":tax");
    }

    /** 주인 없는 성을 길드 금고로 산다 */
    public Optional<Crowning> buyCastle(String uuid, String region, String key) {
        long price = castlePrice(region);
        GuildRepository.Guild g = leaderGuild(uuid);
        tx.inTx(() -> {
            DomainException.require(repo.castle(region).isEmpty(), "castle.owned", "이미 주인이 있는 성입니다 — 공성으로 빼앗아야 합니다");
            if (!s.economy.withdraw(GuildService.wallet(g.id()), price, "castle:" + region, key)) throw DomainException.of("castle.duplicate", "이미 처리했습니다");
            long now = clock.nowMillis();
            repo.upsertCastle(new Castle(region, g.id(), 5, now, now));
            s.audit.record("CASTLE_BOUGHT", uuid, region, g.id() + " " + price, key);
            return null;
        });
        return checkEmperor();
    }

    public void setTax(String uuid, String region, int pct) {
        DomainException.require(pct >= 0 && pct <= RealmRules.MAX_TAX, "castle.tax", "세율은 0 ~ " + RealmRules.MAX_TAX + "%");
        GuildRepository.Guild g = leaderGuild(uuid);
        tx.inTx(() -> {
            Castle c = repo.castle(region).orElseThrow(() -> DomainException.of("castle.none", "주인 없는 성"));
            DomainException.require(c.guildId().equals(g.id()), "castle.not_owner", "우리 길드의 성이 아닙니다");
            repo.upsertCastle(new Castle(c.region(), c.guildId(), pct, c.since(), c.lastIncome()));
            return null;
        });
    }

    /** 하루가 지난 만큼 성 수입을 길드 금고로 (여러 번 불러도 같은 날을 두 번 주지 않는다) @return 준 총액 */
    public long collectIncome() {
        long now = clock.nowMillis(), total = 0;
        for (Castle c : repo.castles()) {
            long days = (now - c.lastIncome()) / DAY;
            if (days <= 0) continue;
            long amount = days * RealmRules.dailyIncome(castlePrice(c.region()));
            long upTo = c.lastIncome() + days * DAY;
            tx.inTx(() -> {
                Castle cur = repo.castle(c.region()).orElseThrow();
                if (cur.lastIncome() != c.lastIncome() || !cur.guildId().equals(c.guildId())) return null;
                repo.upsertCastle(new Castle(c.region(), c.guildId(), c.taxPct(), c.since(), upTo));
                s.economy.deposit(GuildService.wallet(c.guildId()), amount, "castle_income:" + c.region(), "castle_income:" + c.region() + ":" + upTo);
                return null;
            });
            total += amount;
        }
        return total;
    }

    // ================================================================== 공성
    public Collection<Siege> sieges() {
        sieges.values().removeIf(x -> x.endsAt() <= clock.nowMillis());   // 저장된 줄은 만료 시각에 runtime_state 가 버린다
        return Collections.unmodifiableCollection(sieges.values());
    }

    public Siege declareSiege(String uuid, String region, String key) {
        GuildRepository.Guild g = leaderGuild(uuid);
        Castle c = repo.castle(region).orElseThrow(() -> DomainException.of("castle.unowned", "주인 없는 성은 /성 구입 으로 삽니다"));
        DomainException.require(!c.guildId().equals(g.id()), "siege.own", "우리 성입니다");
        sieges();
        DomainException.require(!sieges.containsKey(region), "siege.busy", "이미 공성 중인 성입니다");
        if (!s.economy.withdraw(GuildService.wallet(g.id()), RealmRules.SIEGE_COST, "siege:" + region, key))
            throw DomainException.of("siege.duplicate", "이미 처리했습니다");
        Siege sg = new Siege(region, g.id(), c.guildId(), clock.nowMillis() + RealmRules.SIEGE_MS);
        sieges.put(region, sg);
        saveSiege(sg, 0);
        s.audit.record("SIEGE_DECLARED", uuid, region, g.id() + " vs " + c.guildId(), key);
        return sg;
    }

    /** 공성 중인 두 길드끼리는 성 안에서 싸워도 악명이 쌓이지 않는다 */
    public boolean atWar(String guildA, String guildB, String regionId) {
        if (guildA == null || guildB == null) return false;
        String castle = castleRegionAt(regionId).orElse(null);
        if (castle == null) return false;
        Siege sg = sieges().stream().filter(x -> x.region().equals(castle)).findFirst().orElse(null);
        return sg != null && (sg.attacker().equals(guildA) && sg.defender().equals(guildB) || sg.attacker().equals(guildB) && sg.defender().equals(guildA));
    }

    /** 공격 측이 성 한가운데를 다 지켰다 → 성이 넘어간다 */
    public Optional<Crowning> capture(String region) {
        Siege sg = sieges.remove(region);
        DomainException.require(sg != null, "siege.none", "공성 중이 아닙니다");
        s.state.delete(SIEGE, region);
        tx.inTx(() -> {
            long now = clock.nowMillis();
            Castle c = repo.castle(region).orElseThrow();
            repo.upsertCastle(new Castle(region, sg.attacker(), c.taxPct(), now, now));
            s.audit.record("CASTLE_CAPTURED", null, region, sg.defender() + " -> " + sg.attacker(), "capture:" + region + ":" + now);
            return null;
        });
        return checkEmperor();
    }

    public void endSiege(String region) {
        sieges.remove(region);
        s.state.delete(SIEGE, region);
    }

    // ================================================================== 국가 · 황제
    public Nation foundNation(String uuid, String name) {
        DomainException.require(name != null && !name.isBlank() && name.length() <= 20, "nation.name", "나라 이름은 1 ~ 20자");
        GuildRepository.Guild g = leaderGuild(uuid);
        Nation n = new Nation(UUID.randomUUID().toString(), g.id(), name, clock.nowMillis());
        tx.inTx(() -> {
            DomainException.require(repo.castles().stream().anyMatch(c -> c.guildId().equals(g.id())), "nation.no_castle", "성이 하나는 있어야 나라를 세울 수 있습니다");
            DomainException.require(repo.nationOfGuild(g.id()).isEmpty(), "nation.exists", "이미 나라를 세웠습니다");
            DomainException.require(repo.insertNation(n), "nation.name_taken", "이미 있는 나라 이름입니다");
            s.audit.record("NATION_FOUNDED", uuid, n.id(), name, "nation:" + g.id());
            return null;
        });
        checkEmperor();
        return n;
    }

    public Optional<Nation> nationOf(String guildId) {
        return repo.nationOfGuild(guildId);
    }

    public List<Nation> nations() {
        return repo.nations();
    }

    public Optional<Emperor> emperor() {
        return repo.emperor();
    }

    /** 수도 6곳을 모두 가진 나라가 처음 나오면 황제 (서버에서 한 번만): 길드 금고에 상금, 길드장에게 황제의 왕관 */
    public Optional<Crowning> checkEmperor() {
        if (repo.emperor().isPresent()) return Optional.empty();
        Map<String, Set<String>> owned = new HashMap<>();
        for (Castle c : repo.castles()) owned.computeIfAbsent(c.guildId(), k -> new HashSet<>()).add(c.region());
        for (Nation n : repo.nations()) {
            if (!owned.getOrDefault(n.guildId(), Set.of()).containsAll(capitals)) continue;
            GuildRepository.Guild g = s.guilds.find(n.guildId()).orElse(null);
            if (g == null) continue;
            long now = clock.nowMillis();
            boolean crowned = tx.inTx(() -> {
                if (!repo.crown(new Emperor(n.id(), g.id(), g.leader(), now))) return false;
                if (emperorReward > 0) s.economy.deposit(GuildService.wallet(g.id()), emperorReward, "emperor", "emperor:" + n.id());
                s.items.create("emperor_crown", 1000, null, "베르사", "emperor", Map.of(), g.leader(), "emperor_crown:" + n.id());
                s.audit.record("EMPEROR_CROWNED", g.leader(), n.id(), n.name(), "emperor:" + n.id());
                return true;
            });
            if (crowned) return Optional.of(new Crowning(n, g, emperorReward));
        }
        return Optional.empty();
    }
}
