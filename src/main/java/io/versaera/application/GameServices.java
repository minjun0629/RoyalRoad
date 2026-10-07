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
    public final GateService gates;
    public final OriginService origins;
    public final ReputationService reputation;
    public final SecretArtService arts;
    public final TrialService trials;
    public final RealmService realm;
    public final GearService gear;
    public final LifeSkillService life;
    public final FieldBossService fieldBosses;
    public final NpcWorldService npcWorld;
    public final io.versaera.application.port.AdventureRepository adventure;
    public final WeatherService weather;
    public final AchievementService achievements;
    public final GuildVaultService guildVault;
    public final PetService pets;
    public final TravelService travel;
    public final RaidService raids;
    public final ArtworkService artworks;
    public final ResetService reset;
    /** 파티 (접속 중에만 · 메인 스레드 전용) */
    public final io.versaera.domain.party.Parties parties = new io.versaera.domain.party.Parties();
    private volatile ServerRules rules = ServerRules.CANON;
    public final GatheringService gathering;
    public final MapService maps;
    public final BossService bosses;
    public final SkillBook skills;
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
        ItemTypeRegistry types = new ItemTypeRegistry(content.items(), content.sets());
        this.items = new ItemService(tx, itemRepo, new JdbcDeliveryRepository(db), types, audit, bus, clock);
        this.economy = new EconomyService(tx, wallets, audit, bus, clock);
        this.trades = new TradeService(tx, new JdbcTradeRepository(db), itemRepo, types, wallets, economy, audit, bus, clock);
        this.growth = new GrowthService(tx, progress, content.disciplines(), content.stats(), bus);
        this.crafting = new CraftingService(tx, content.recipes(), items, growth, progress, audit, bus);
        this.regions = new RegionIndex(content.regions());
        var ex = content.expansion();
        this.weather = new WeatherService(ex.weatherKinds(), ex.climates(), ex.weatherWindowMs(), ex.weatherCell(), regions, clock);
        this.exploration = new ExplorationService(tx, progress, regions, growth, bus, clock);
        this.relations = new RelationService(tx, progress, content.npcs(), growth, bus, clock, zone);
        this.profiles = new ProfileService(tx, new JdbcProfileRepository(db), clock);
        this.zone = zone;
        JobRepository jobRepo = new JdbcJobRepository(db);
        this.jobs = new JobService(tx, jobRepo, content.jobs(), bus, clock);
        this.deaths = new DeathService(tx, progress, jobRepo, items, growth, clock, this::rules);
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
        this.gates = new GateService(content.gates(), regions, growth::level);
        this.origins = new OriginService(tx, new JdbcOriginRepository(db), content.origins(), regions, items, clock, this::rules);
        this.reputation = new ReputationService(tx, progress, economy, regions, content.gods(), content.temples(), clock);
        deaths.attach(origins, reputation);
        this.arts = new SecretArtService(tx, progress, content.arts(), this, clock);
        this.trials = new TrialService(tx, progress, this);
        java.util.List<int[]> npcSpots = new java.util.ArrayList<>();
        content.places().values().forEach(m -> m.values().forEach(p -> npcSpots.add(new int[]{(int) Math.floor(p.x()), (int) Math.floor(p.z())})));
        this.realm = new RealmService(tx, new JdbcRealmRepository(db), this, regions, clock, npcSpots);
        this.gear = new GearService(tx, itemRepo, this, clock);
        this.life = new LifeSkillService(tx, this);
        growth.xpBonus(origins::xpMult);   // 종족 숙련 보너스
        this.maps = new MapService(tx, new JdbcMapRepository(db));
        this.skills = new SkillBook(this, content.skills(), content.combos());
        this.bosses = new BossService(tx, new JdbcBossRepository(db), content.bosses(), this, bus, clock);
        market.regionDiscount(worldEvents::shopDiscount);
        this.fieldBosses = new FieldBossService(tx, progress, content.fieldBosses(), this, clock);
        this.npcWorld = new NpcWorldService(tx, progress, new io.versaera.persistence.JdbcWorldStateRepository(db), this, content.npcProfiles(),
                content.archetypes(), clock, zone, bus);
        market.npcDiscount(npcWorld::shopDiscount);
        this.dungeons = new DungeonService(tx, new JdbcDungeonRepository(db), progress, content.dungeons(), this, bus, clock);
        // 모험 확장 (V7)
        this.adventure = new io.versaera.persistence.JdbcAdventureRepository(db);
        this.achievements = new AchievementService(tx, progress, adventure, this, ex.achievements(), ex.titles(), bus, clock);
        growth.onCounter(achievements::counterChanged);
        this.guildVault = new GuildVaultService(tx, adventure, this, ex.guildQuests(), ex.guildWithdraw(), ex.guildMaxKinds(), bus, clock, zone);
        this.pets = new PetService(tx, adventure, this, ex.species(), bus, clock);
        this.travel = new TravelService(tx, adventure, this, ex.mounts(), ex.travel(), clock);
        this.raids = new RaidService(tx, adventure, this, ex.raids(), bus, clock, zone);
        this.artworks = new ArtworkService(tx, adventure, this, ex.artworks(), clock, zone);
        this.reset = new ResetService(tx, new io.versaera.persistence.JdbcResetRepository(db), this);
        // 퀘스트 진행: 발견 · 제작은 도메인 이벤트로 (같은 DB 스레드에서 동기 처리)
        bus.subscribe(io.versaera.domain.event.GameEvents.PlayerDiscovered.class,
                e -> quests.record(e.uuid(), io.versaera.domain.quest.QuestDefinition.Type.DISCOVER, e.kind() + ":" + e.ref(), 1, 0));
        bus.subscribe(io.versaera.domain.event.GameEvents.PlayerCrafted.class,
                e -> quests.record(e.uuid(), io.versaera.domain.quest.QuestDefinition.Type.CRAFT, e.recipeId(), 1, e.quality()));
        for (var r : content.resources()) {
            growth.discipline(r.discipline());
            types.get(r.yield());
        }
    }

    /** 서버 규칙 (config.yml — 시간 비율 · 사망 방식) */
    public ServerRules rules() {
        return rules;
    }

    public void rules(ServerRules r) {
        rules = java.util.Objects.requireNonNull(r);
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
