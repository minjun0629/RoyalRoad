package io.versaera.application;

import io.versaera.application.port.*;
import io.versaera.content.ContentBundle;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.hidden.HiddenRule;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.domain.skill.StatEffects;
import io.versaera.domain.world.RegionIndex;
import io.versaera.persistence.*;

import java.time.ZoneId;
import java.util.Collection;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * 조립 지점 (플러그인과 테스트가 같은 방식으로 서비스를 만든다).
 * 모든 서비스 호출은 DbExecutor 의 한 스레드에서 해야 한다.
 */
public final class GameServices {
    public final GameClock clock;
    public final EventBus bus;
    public final ContentBundle content;
    public final TxRunner tx;
    public final ItemRepository itemRepo;
    public final ProgressRepository progress;
    public final AuditLog audit;
    public final ItemService items;
    public final EconomyService economy;
    public final TradeService trades;
    public final GrowthService growth;
    public final CraftingService crafting;
    public final ExplorationService exploration;
    public final RelationService relations;
    public final ProfileService profiles;
    public final RegionIndex regions;
    public final JobService jobs;
    public final DeathService deaths;
    public final QuestService quests;
    public final GuildService guilds;
    public final MarketService market;
    public final AuctionService auctions;
    public final DungeonService dungeons;
    public final WorldEventService worldEvents;
    public final GatheringService gathering;
    public final MapService maps;
    public final BossService bosses;
    private final ZoneId zone;
    private HiddenService hidden;

    public GameServices(Database db, ContentBundle content, GameClock clock, ZoneId zone, Logger log) {
        this.clock = clock;
        this.bus = new EventBus(log);
        this.content = content;
        this.tx = new SqlTxRunner(db);
        this.itemRepo = new JdbcItemRepository(db);
        this.progress = new JdbcProgressRepository(db);
        this.audit = new JdbcAuditLog(db, clock);
        WalletRepository wallets = new JdbcWalletRepository(db);
        ItemTypeRegistry types = new ItemTypeRegistry(content.items());
        this.items = new ItemService(tx, itemRepo, new JdbcDeliveryRepository(db), types, audit, bus, clock);
        this.economy = new EconomyService(tx, wallets, audit, bus, clock);
        this.trades = new TradeService(tx, new JdbcTradeRepository(db), itemRepo, types, wallets, economy, audit, bus, clock);
        this.growth = new GrowthService(tx, progress, content.disciplines(), content.stats(), bus);
        this.crafting = new CraftingService(tx, content.recipes(), items, growth, progress, audit, bus);
        this.regions = new RegionIndex(content.regions());
        this.exploration = new ExplorationService(tx, progress, regions, growth, bus, clock);
        this.relations = new RelationService(tx, progress, content.npcs(), growth, bus, clock, zone);
        this.profiles = new ProfileService(tx, new JdbcProfileRepository(db), clock);
        this.zone = zone;
        JobRepository jobRepo = new JdbcJobRepository(db);
        this.jobs = new JobService(tx, jobRepo, content.jobs(), bus, clock);
        this.deaths = new DeathService(tx, progress, jobRepo, items, growth, clock);
        // 제작 보정: 생활 직업 효과(craft_quality.<분야>) + 정밀 스탯(흔들림 감소)
        crafting.modifiers((uuid, discipline) -> new double[]{
                jobs.perks(uuid).getOrDefault("craft_quality." + discipline, 0.0),
                StatEffects.of(growth.statPoints(uuid)).craftVarianceMult()});
        this.guilds = new GuildService(tx, new JdbcGuildRepository(db), economy, audit, bus, clock);
        MarketRepository marketRepo = new JdbcMarketRepository(db);
        this.market = new MarketService(tx, marketRepo, itemRepo, items, economy, content.market(), audit, bus, clock, this::priceDiscount);
        this.auctions = new AuctionService(tx, marketRepo, itemRepo, items, economy, content.market(), audit, bus, clock,
                uuid -> jobs.perks(uuid).getOrDefault("auction_fee_cut", 0.0));
        this.quests = new QuestService(tx, new JdbcQuestRepository(db), progress, content.quests(), this, bus, clock, zone);
        this.worldEvents = new WorldEventService(tx, new JdbcWorldEventRepository(db), content.worldEvents(), regions, bus, clock, 0L);
        this.gathering = new GatheringService(this, content.resources());
        this.maps = new MapService(tx, new JdbcMapRepository(db));
        this.bosses = new BossService(tx, new JdbcBossRepository(db), content.bosses(), this, bus, clock);
        market.regionDiscount(worldEvents::shopDiscount);
        this.dungeons = new DungeonService(tx, new JdbcDungeonRepository(db), progress, content.dungeons(), this, bus, clock);
        for (var r : content.resources()) {
            growth.discipline(r.discipline());
            types.get(r.yield());
        }
    }

    /** 봉인을 연 히든 규칙을 붙인다 (없으면 히든 콘텐츠 없이 동작) */
    public HiddenService attachHidden(Collection<HiddenRule> rules, Function<String, PlayerFacts> facts) {
        hidden = new HiddenService(tx, progress, rules, facts, bus, clock);
        growth.onCounter(hidden::counterChanged);
        return hidden;
    }

    /** NPC 상점 할인: 상인 직업 효과 + 매력 스탯 (MarketPricing 이 최대 25% 로 자른다) */
    public double priceDiscount(String uuid) {
        return jobs.perks(uuid).getOrDefault("price_discount", 0.0) + StatEffects.of(growth.statPoints(uuid)).priceDiscount();
    }

    /** 조건 판정용 사실 (DB 스레드에서만) */
    public PlayerFacts facts(String uuid, String region, int hour) {
        return new Facts(this, uuid, region, hour);
    }

    public HiddenService hidden() {
        return hidden;
    }
}
