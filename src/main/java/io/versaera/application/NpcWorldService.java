package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.application.port.WorldStateRepository;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.market.MarketCatalog;
import io.versaera.domain.npc.Archetype;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.NpcProfile;
import io.versaera.domain.npc.Relation;
import io.versaera.domain.world.Region;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * 살아 있는 주민 (NPC-04 · WLD-04). NPC 를 상점 · 퀘스트 지급기가 아니라 세계의 일부로 만든다.
 * <ul>
 *   <li>관계 단계 (Relation.Stage): 단계마다 할인 · 소문 · 지도 · 숨은 의뢰가 열린다</li>
 *   <li>기억: NPC 는 플레이어가 한 일(부탁 · 선물 · 지역의 큰일)을 기억하고 대사에 꺼낸다 (DB, 재시작해도 남는다)</li>
 *   <li>관계가 퍼진다: 한 NPC 와 가까워지면 가족 · 거래처 · 스승도 조금 가까워지고, 경쟁자는 멀어진다</li>
 *   <li>일: 소문 · 숙련 지도 · 쉼 · 치유 · 노래 · 수리 · 연대기 — 모두 실제로 효과가 있다 (몸에 걸리는 효과는 플랫폼)</li>
 *   <li>지역 번영: 의뢰 · 거래 · 보스 처치로 오르고 1주 반감기로 0 쪽으로 돌아간다. 번영하면 값이 내리고 귀한 상인이 들어오며,
 *       쇠퇴하면 귀한 상인이 떠난다</li>
 * </ul>
 */
public final class NpcWorldService {
    public enum Tier {
        DECLINE("쇠퇴"), NORMAL("평범"), THRIVING("번성"), FLOURISHING("융성");
        public final String label;

        Tier(String label) { this.label = label; }
    }

    public record Talk(NpcDefinition npc, Relation.Stage stage, int affinity, int gain, String line, String memoryLine, Tier regionTier,
                       List<String> unlocked) {}

    public record Rumor(String regionId, String regionName, String direction, int distance) {}

    public record Lesson(String discipline, long xp, long cost) {}

    /** 떠돌이가 도시 하나에 머무는 시간 (실제 시간) */
    public static final long WANDER_STAY_MS = 30 * 60_000L;

    /** 귀한 물건을 다루는 상인 — 지역이 쇠퇴하면 떠난다 */
    public static final Set<String> LUXURY = Set.of("jeweler", "antique", "mage_merchant", "auctioneer", "weaponsmith");

    private final TxRunner tx;
    private final ProgressRepository progress;
    private final WorldStateRepository world;
    private final GameServices s;
    private final GameClock clock;
    private final ZoneId zone;
    private final Map<String, NpcProfile> profiles = new HashMap<>();
    private final Map<String, Archetype> archetypes;
    private final Map<String, String> nearestMarket = new HashMap<>();
    private final Map<String, List<io.versaera.domain.npc.NpcSchedule.Point>> wanderStops = new java.util.concurrent.ConcurrentHashMap<>();
    /** 지금 세계를 떠나 있는 NPC (쇠퇴한 지역의 귀한 물건 상인) — refreshPresence() 가 DB 스레드에서 갈아 끼운다 */
    private volatile Set<String> absent = Set.of();

    NpcWorldService(TxRunner tx, ProgressRepository progress, WorldStateRepository world, GameServices s, Collection<NpcProfile> profiles,
                    Map<String, Archetype> archetypes, GameClock clock, ZoneId zone, EventBus bus) {
        this.tx = tx;
        this.progress = progress;
        this.world = world;
        this.s = s;
        this.clock = clock;
        this.zone = zone;
        this.archetypes = Map.copyOf(archetypes);
        for (NpcProfile p : profiles) {
            DomainException.require(this.profiles.putIfAbsent(p.id(), p) == null, "npc.dup_profile", "NPC 프로필 중복: " + p.id());
            s.relations.npc(p.id());
            DomainException.require(archetypes.containsKey(p.archetype()), "npc.bad_arch", "없는 직업 틀: " + p.archetype());
            if (p.trains() != null) s.growth.discipline(p.trains());
        }
        // 일과 연결: 의뢰를 끝내면 맡긴 NPC 가 기억하고 지역이 번영한다 · 필드 보스를 쓰러뜨리면 그 지역 사람들이 기억한다
        bus.subscribe(GameEvents.QuestCompleted.class, e -> {
            var q = s.quests.quest(e.questId());
            if (q.giver() == null) return;
            remember(e.uuid(), q.giver(), "QUEST", q.title(), 2);
            contribute(s.relations.npc(q.giver()).region(), 8, "quest:" + e.uuid() + ":" + e.questId() + ":" + clock.nowMillis() / 86_400_000L);
        });
        bus.subscribe(GameEvents.NpcRelationChanged.class, e -> unlockByStage(e.uuid(), e.npcId(), e.affinity()));
    }

    public Optional<NpcProfile> profile(String npcId) {
        return Optional.ofNullable(profiles.get(npcId));
    }

    public Collection<NpcProfile> profiles() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    public Archetype archetypeOf(String npcId) {
        NpcProfile p = profiles.get(npcId);
        return p == null ? null : archetypes.get(p.archetype());
    }

    public boolean offers(String npcId, String service) {
        Archetype a = archetypeOf(npcId);
        if (a != null) return a.offers(service) || ("TRAIN".equals(service) && profiles.get(npcId).trains() != null && profiles.get(npcId).rare() != null);
        return "QUEST".equals(service) || "SHOP".equals(service) && s.market.catalog().shops().containsKey(npcId);
    }

    public Relation.Stage stage(String uuid, String npcId) {
        ProgressRepository.RelationRow row = progress.relation(uuid, npcId);
        return Relation.stage(row.affinity(), row.lastTalk() > 0 || row.affinity() != 0);
    }

    // ------------------------------------------------------------------ 기억
    public void remember(String uuid, String npcId, String kind, String detail, int weight) {
        tx.inTx(() -> {
            world.remember(uuid, npcId, kind, detail, weight, clock.nowMillis());
            return null;
        });
    }

    public List<WorldStateRepository.Memory> memories(String uuid, String npcId, int limit) {
        return world.memories(uuid, npcId, limit);
    }

    /** 기억을 한 줄로 (가장 최근 것 하나, 없으면 그 지역 사람들이 함께 기억하는 일) */
    String memoryLine(String uuid, NpcDefinition n) {
        List<WorldStateRepository.Memory> m = world.memories(uuid, n.id(), 1);
        if (m.isEmpty()) m = world.memories(uuid, "region:" + n.region(), 1);
        if (m.isEmpty()) return null;
        WorldStateRepository.Memory x = m.get(0);
        return switch (x.kind()) {
            case "QUEST" -> "지난번 '" + x.detail() + "' 일은 고마웠네.";
            case "GIFT_LIKED" -> "그때 준 " + x.detail() + ", 아직 아껴 쓰고 있지.";
            case "GIFT_DISLIKED" -> x.detail() + "은(는)... 다시는 가져오지 말게.";
            case "BOSS" -> x.detail() + "을(를) 쓰러뜨린 사람이 자네라며? 동네가 다 그 얘기야.";
            case "FAMILY" -> x.detail() + "에게서 자네 얘길 들었네.";
            case "RIVAL" -> x.detail() + " 쪽 사람이라지? 흥.";
            case "TRADE" -> "단골이 되어 주어 고맙네.";
            case "LESSON" -> x.detail() + " 연습은 잘 하고 있나?";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ 대화
    /** 우클릭 대화: 하루 첫 대화면 호감 +, 단계 · 기억 · 지역 상태에 맞는 대사 */
    public Talk talk(String uuid, String npcId) {
        NpcDefinition n = s.relations.npc(npcId);
        int gain = s.relations.talk(uuid, npcId);
        int aff = s.relations.affinity(uuid, npcId);
        Relation.Stage st = Relation.stage(aff, true);
        NpcProfile p = profiles.get(npcId);
        Archetype a = archetypeOf(npcId);
        String line = p != null && p.line() != null ? p.line() : a != null ? a.lines().get(st.lineGroup()) : null;
        Tier tier = tier(n.region());
        List<String> unlocked = new ArrayList<>();
        if (st.atLeast(Relation.Stage.INTEREST) && (offers(npcId, "RUMOR") || offers(npcId, "LORE"))) unlocked.add("소문");
        if (st.atLeast(Relation.Stage.FRIENDLY) && offers(npcId, "TRAIN")) unlocked.add("지도");
        if (st.discount() > 0 && s.market.catalog().shops().containsKey(npcId)) unlocked.add("할인 " + Math.round(st.discount() * 100) + "%");
        return new Talk(n, st, aff, gain, line, memoryLine(uuid, n), tier, unlocked);
    }

    /** 관계가 신뢰에 이르면 그 NPC 의 숨은 의뢰가 열린다 (한 번) */
    private void unlockByStage(String uuid, String npcId, int affinity) {
        NpcProfile p = profiles.get(npcId);
        if (p == null || p.hiddenQuests().isEmpty() || !Relation.stage(affinity, true).atLeast(Relation.Stage.TRUST)) return;
        for (String q : p.hiddenQuests()) progress.discover(uuid, "quest", q, clock.nowMillis());
    }

    /**
     * 관계가 퍼진다: npcId 와 delta 만큼 가까워지면 가족 · 거래처 · 스승도 조금, 경쟁자는 거꾸로. 상대에게 기억이 남는다.
     * 호감이 5 이상 오를 때만 (매일 대화 같은 작은 변화는 퍼지지 않는다).
     */
    public void spread(String uuid, String npcId, int delta) {
        NpcProfile p = profiles.get(npcId);
        if (p == null || delta < 5) return;
        String name = s.relations.npc(npcId).name();
        for (NpcProfile.Link l : p.links()) {
            int d = (int) Math.round(delta * Relation.spread(l.type()));
            if (d == 0) continue;
            tx.inTx(() -> {
                ProgressRepository.RelationRow row = progress.relation(uuid, l.npc());
                progress.setRelation(uuid, l.npc(), Relation.clamp((long) row.affinity() + d), row.lastTalk());
                world.remember(uuid, l.npc(), d > 0 ? "FAMILY" : "RIVAL", name, 1, clock.nowMillis());
                return null;
            });
        }
    }

    // ------------------------------------------------------------------ 일
    private void need(String uuid, String npcId, String service, Relation.Stage min) {
        DomainException.require(offers(npcId, service), "npc.no_service", "이 사람은 그 일을 하지 않습니다");
        Relation.Stage st = stage(uuid, npcId);
        DomainException.require(st != Relation.Stage.HOSTILE, "npc.hostile", "상대해 주지 않습니다");
        DomainException.require(st.atLeast(min), "npc.stage", "관계가 '" + min.label + "' 이상이어야 합니다 (지금 " + st.label + ")");
    }

    /** 소문: 아직 가 보지 않은 유적 · 던전 하나의 방향과 거리 (관심 이상). 친밀 이상이면 더 먼 곳도 */
    public Rumor rumor(String uuid, String npcId) {
        need(uuid, npcId, offers(npcId, "RUMOR") ? "RUMOR" : "LORE", Relation.Stage.INTEREST);
        NpcDefinition n = s.relations.npc(npcId);
        NpcProfile p = profiles.get(npcId);
        List<String> pool = new ArrayList<>(p == null ? List.of() : p.rumors());
        if (stage(uuid, npcId).atLeast(Relation.Stage.CLOSE)) pool.addAll(farSecrets(n.region()));
        Region here = s.regions.byId(n.region());
        for (String rid : pool) {
            if (s.exploration.discovered(uuid, "region", rid)) continue;
            Region r = s.regions.byId(rid);
            if (r == null || here == null) continue;
            tx.inTx(() -> progress.discover(uuid, "rumor", rid, clock.nowMillis()));
            double dx = (r.minX() + r.maxX()) / 2.0 - (here.minX() + here.maxX()) / 2.0, dz = (r.minZ() + r.maxZ()) / 2.0 - (here.minZ() + here.maxZ()) / 2.0;
            return new Rumor(rid, r.name(), direction(dx, dz), (int) Math.round(Math.hypot(dx, dz) / 100) * 100);
        }
        return null;
    }

    /** 친밀한 사이에만 털어놓는 먼 곳 (숨은 · 봉인된 · 깊은 지역) */
    private List<String> farSecrets(String regionId) {
        Region here = s.regions.byId(regionId);
        if (here == null) return List.of();
        List<Region> l = new ArrayList<>();
        for (Region r : s.regions.all())
            if (r.world().equals(here.world()) && !Collections.disjoint(r.tags(), Set.of("sealed", "hole", "mist", "deep", "crater", "volcano"))) l.add(r);
        l.sort(Comparator.comparingDouble(r -> Math.hypot(r.minX() - here.minX(), r.minZ() - here.minZ())));
        return l.stream().limit(3).map(Region::id).toList();
    }

    static String direction(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));   // 0 = 북
        String[] d = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        return d[(int) Math.floorMod(Math.round(a / 45), 8)];
    }

    private long day() {
        return Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate().toEpochDay();
    }

    /** 숙련 지도 (우호 이상, NPC 마다 하루 한 번). NPC 레벨의 절반까지만 가르칠 수 있다. 동료 이상이면 두 배 */
    public Lesson train(String uuid, String npcId) {
        need(uuid, npcId, "TRAIN", Relation.Stage.FRIENDLY);
        NpcProfile p = profiles.get(npcId);
        String d = p.trains();
        int cap = Math.max(5, p.level() / 2), lv = s.growth.level(uuid, d);
        DomainException.require(lv < cap, "npc.train_cap", "더 가르칠 게 없다고 한다 (" + s.growth.discipline(d).name() + " " + cap + " 까지)");
        long cost = (50 + p.level() * 10L);
        String key = "train:" + uuid + ":" + npcId + ":" + day();
        DomainException.require(s.economy.withdraw(uuid, cost, "npc_train", key), "npc.trained_today", "오늘은 이미 배웠습니다");
        long xp = (60 + p.level() * 4L) * (stage(uuid, npcId).atLeast(Relation.Stage.COMRADE) ? 2 : 1);
        s.growth.addXp(uuid, d, xp, Math.min(31, p.level() / 2));
        remember(uuid, npcId, "LESSON", s.growth.discipline(d).name(), 1);
        return new Lesson(d, xp, cost);
    }

    /**
     * 처음 온 사람에게 주는 물건 (훈련 교관의 수련용 목검 …). 직업 틀마다 한 사람에 한 번 — 다른 도시의 같은 교관에게 또 받을 수 없다.
     * @return 플랫폼이 직접 줄 바닐라 물건 ("minecraft:arrow:32") — 게임 아이템은 배달함으로 간다
     */
    public List<String> receiveGift(String uuid, String npcId) {
        need(uuid, npcId, "GIFT", Relation.Stage.STRANGER);
        Archetype a = archetypeOf(npcId);
        boolean first = tx.inTx(() -> s.progress.discover(uuid, "npc_gift", a.id(), clock.nowMillis()));
        DomainException.require(first, "npc.gift_taken", "이미 받았습니다");
        List<String> vanilla = new ArrayList<>();
        for (String g : a.gifts()) {
            String[] p = g.split(":");
            if (p[0].equals("minecraft")) { vanilla.add(g); continue; }
            int q = Integer.parseInt(p[1]), n = Integer.parseInt(p[2]);
            if (s.items.types().get(p[0]).category().unique())
                for (int i = 0; i < n; i++) s.items.create(p[0], q, null, s.relations.npc(npcId).name(), "npc_gift", Map.of(), uuid, "gift:" + uuid + ":" + a.id() + ":" + i);
            else s.items.deliverBulk(uuid, p[0], q, n, "npc_gift");
        }
        remember(uuid, npcId, "GIFT", a.job(), 1);
        return vanilla;
    }

    /** 이 사람에게서 이미 선물을 받았나 */
    public boolean giftTaken(String uuid, String npcId) {
        Archetype a = archetypeOf(npcId);
        return a == null || s.progress.discovered(uuid, "npc_gift", a.id());
    }

    /** 돈을 받고 하는 일 (쉼 · 치유) — 같은 요청 key 는 한 번 */
    public long paidService(String uuid, String npcId, String service, String requestId) {
        need(uuid, npcId, service, Relation.Stage.STRANGER);
        NpcDefinition n = s.relations.npc(npcId);
        long base = switch (service) { case "INN" -> 30; case "HEAL" -> 40; default -> throw DomainException.of("npc.no_service", "없는 일"); };
        long cost = Math.max(5, Math.round(base * (1 - stage(uuid, npcId).discount()) * (tier(n.region()) == Tier.DECLINE ? 1.5 : 1)));
        s.economy.withdraw(uuid, cost, "npc_" + service.toLowerCase(Locale.ROOT), "npc:" + service + ":" + requestId);
        return cost;
    }

    /** 노래 (음유시인): NPC 마다 하루 한 번 공짜 */
    public boolean song(String uuid, String npcId) {
        need(uuid, npcId, "SONG", Relation.Stage.STRANGER);
        return tx.inTx(() -> progress.discover(uuid, "song", npcId + ":" + day(), clock.nowMillis()));
    }

    /** 수리: 수리 숙련 대신 NPC 레벨로 (대장장이 · 갑옷장이). 닳은 만큼 값 */
    public io.versaera.domain.item.Repair.Result repair(String uuid, String npcId, String itemId, String requestId) {
        need(uuid, npcId, "REPAIR", Relation.Stage.STRANGER);
        var it = s.items.find(itemId).filter(x -> x.custody().ownedBy(uuid)).orElseThrow(() -> DomainException.of("item.not_owner", "내 장비를 손에 들어야 합니다"));
        int missing = it.maxDurability() - it.durability();
        DomainException.require(missing > 0 || it.ruined(), "npc.no_repair", "고칠 데가 없습니다");
        long cost = Math.max(10, Math.round(missing * 0.6 * (1 - stage(uuid, npcId).discount())));
        s.economy.withdraw(uuid, cost, "npc_repair", "npc_repair:" + requestId);
        int level = profiles.containsKey(npcId) ? Math.min(31, profiles.get(npcId).level() / 2) : 10;
        return s.items.repair(itemId, uuid, npcId, level, "npc_repair_item:" + requestId);
    }

    static final String GIFTS = "gifts";

    /** 선물: 호감 + 기억(좋아함 · 싫어함) + 가족 · 거래처에게 퍼짐 */
    public int gift(String uuid, String npcId, Set<String> itemTags, int quality, String itemName) {
        // 오늘 이 사람에게 몇 번째 선물인가 (runtime_state, 다음 날이면 0 부터) — 하루 GIFTS_PER_DAY 번까지
        long today = s.relations.today();
        String key = uuid + ":" + npcId;
        Map<String, String> st = s.state.load(GIFTS, key).orElse(Map.of());
        int given = Long.toString(today).equals(st.get("day")) ? Integer.parseInt(st.getOrDefault("n", "0")) : 0;
        DomainException.require(given < Relation.GIFTS_PER_DAY, "npc.gift_enough", "오늘은 선물을 충분히 받았습니다 — 내일 다시 오세요");
        int gain = s.relations.gift(uuid, npcId, itemTags, quality, given + 1);
        s.state.save(GIFTS, key, Map.of("day", Long.toString(today), "n", Integer.toString(given + 1)), clock.nowMillis() + 2 * 86_400_000L);
        if (gain >= 10) remember(uuid, npcId, "GIFT_LIKED", itemName, 1);
        else if (gain < 0) remember(uuid, npcId, "GIFT_DISLIKED", itemName, 1);
        spread(uuid, npcId, gain);
        return gain;
    }

    /** NPC 상점 할인 (관계 단계) — 적대면 거래하지 않는다 */
    public double shopDiscount(String uuid, String npcId) {
        Relation.Stage st = stage(uuid, npcId);
        DomainException.require(st != Relation.Stage.HOSTILE, "npc.hostile", "이 사람은 당신과 거래하지 않습니다");
        double d = st.discount();
        NpcDefinition n = s.relations.npc(npcId);
        return d + switch (tier(n.region())) { case THRIVING -> 0.03; case FLOURISHING -> 0.06; default -> 0; };
    }

    /** 거래가 끝난 뒤: 단골 기억 · 지역 번영 (5실버마다 +1) */
    public void traded(String uuid, String npcId, long amount, String requestId) {
        if (amount <= 0) return;
        NpcDefinition n = s.relations.npc(npcId);
        if (world.count(uuid, npcId, "TRADE") < 3) remember(uuid, npcId, "TRADE", Long.toString(amount), 1);
        contribute(n.region(), (int) Math.max(1, amount / (500)), "trade:" + requestId);
    }

    /** NPC 가 일하는 시장 (가장 가까운 시장 — 경매인의 경매장) */
    public String marketOf(String npcId) {
        return nearestMarket.computeIfAbsent(npcId, id -> {
            MarketCatalog.Shop shop = s.market.catalog().shops().get(id);
            if (shop != null) return shop.market();
            Region here = s.regions.byId(s.relations.npc(id).region());
            return s.market.catalog().markets().values().stream().min(Comparator.comparingDouble(m -> {
                Region mr = s.regions.byId(m.region());
                return mr == null || here == null ? Double.MAX_VALUE : Math.hypot(mr.minX() - here.minX(), mr.minZ() - here.minZ());
            })).map(MarketCatalog.Market::id).orElse(null);
        });
    }

    // ------------------------------------------------------------------ 지역 번영
    /** 1주 반감기로 0 쪽으로 돌아간 지금 값 */
    public int prosperity(String region) {
        WorldStateRepository.Prosperity p = world.prosperity(region);
        if (p.updatedAt() == 0) return 0;
        double weeks = Math.max(0, clock.nowMillis() - p.updatedAt()) / (7 * 86_400_000.0);
        return (int) Math.round(p.value() * Math.pow(0.5, weeks));
    }

    public Tier tier(String region) {
        int v = prosperity(region);
        return v < -200 ? Tier.DECLINE : v >= 600 ? Tier.FLOURISHING : v >= 200 ? Tier.THRIVING : Tier.NORMAL;
    }

    /** 번영을 더한다 (같은 key 는 한 번). 음수 = 피해 (몬스터 습격 등) */
    public int contribute(String region, int amount, String key) {
        if (region == null || amount == 0) return prosperity(region);
        return tx.inTx(() -> {
            if (!world.contribute(key, region, amount, clock.nowMillis())) return prosperity(region);
            int v = Math.max(-1000, Math.min(1000, prosperity(region) + amount));
            world.setProsperity(region, v, clock.nowMillis());
            return v;
        });
    }

    /** 지금 이 NPC 가 세계에 있는가: 귀한 물건 상인은 쇠퇴한 지역을 떠난다 */
    public boolean present(String npcId) {
        NpcProfile p = profiles.get(npcId);
        if (p == null || !LUXURY.contains(p.archetype())) return true;
        return tier(s.relations.npc(npcId).region()) != Tier.DECLINE;
    }

    /** DB 스레드에서: 떠나 있는 NPC 목록을 다시 셈 (지역마다 한 번만 읽는다) */
    public Set<String> refreshPresence() {
        Map<String, Tier> tiers = new HashMap<>();
        Set<String> out = new HashSet<>();
        for (NpcProfile p : profiles.values()) {
            if (!LUXURY.contains(p.archetype())) continue;
            String region = s.relations.npc(p.id()).region();
            if (tiers.computeIfAbsent(region, this::tier) == Tier.DECLINE) out.add(p.id());
        }
        absent = Set.copyOf(out);
        return absent;
    }

    /** 어느 스레드에서나: 마지막으로 센 떠나 있는 NPC */
    public Set<String> absent() {
        return absent;
    }

    /** 희귀 NPC 가 지금(게임 시각 hour, 오늘) 나와 있는가. 희귀하지 않으면 늘 true */
    public boolean rareNow(String npcId, int hour) {
        NpcProfile p = profiles.get(npcId);
        return p == null || p.rare() == null || p.rare().present(day(), hour);
    }

    /** 떠돌이의 지금 자리 (떠돌이가 아니면 null). 도시 광장 옆 큰길에 머문다 */
    public io.versaera.domain.npc.Wandering.State wanderer(String npcId) {
        NpcProfile p = profiles.get(npcId);
        if (p == null || !p.wanderer()) return null;
        List<io.versaera.domain.npc.NpcSchedule.Point> stops = wanderStops.computeIfAbsent(npcId, id -> {
            List<io.versaera.domain.npc.NpcSchedule.Point> out = new ArrayList<>();
            int lane = Math.floorMod(id.hashCode(), 4) * 5;
            for (String r : p.route()) {
                int[] g = io.versaera.domain.terrain.SettlementPlanner.townGrid(s.regions.byId(r));
                out.add(new io.versaera.domain.npc.NpcSchedule.Point(g[0] + 16 + lane + 0.5, g[1] + 1.5));
            }
            return List.copyOf(out);
        });
        return io.versaera.domain.npc.Wandering.at(stops, p.speed(), WANDER_STAY_MS, clock.nowMillis(), Math.floorMod(npcId.hashCode(), 3_600_000L));
    }

    /** 떠돌이가 지금 머무는 도시 (길 위면 null) */
    public String wandererTown(String npcId) {
        var st = wanderer(npcId);
        return st == null || st.stop() < 0 ? null : profiles.get(npcId).route().get(st.stop());
    }

    /** 필드 보스가 쓰러지면: 그 둥지와 가까운 지역 사람들이 기억하고, 그 지역이 번영한다 */
    public void bossSlain(String bossName, String lairRegion, Collection<String> heroes, String key) {
        Region lair = s.regions.byId(lairRegion);
        if (lair == null) return;
        for (Region r : s.regions.all()) {
            if (!r.world().equals(lair.world()) || Math.hypot(r.minX() - lair.minX(), r.minZ() - lair.minZ()) > 2500) continue;
            if (s.relations.all().stream().noneMatch(n -> n.region().equals(r.id()))) continue;
            contribute(r.id(), 40, key + ":" + r.id());
            for (String u : heroes) remember(u, "region:" + r.id(), "BOSS", bossName, 3);
        }
    }

    /** 지역 경제 하루치: 생산자는 가까운 시장에 물건을 내놓고(공급 +), 소비자는 사 간다(공급 -) — 값이 지역마다 움직인다 */
    public int economyTick() {
        Map<String, Map<String, Integer>> delta = new HashMap<>();   // 시장 → 태그 → 변화
        for (NpcProfile p : profiles.values()) {
            Archetype a = archetypes.get(p.archetype());
            if (a.produces().isEmpty() && a.consumes().isEmpty()) continue;
            String m = marketOf(p.id());
            if (m == null) continue;
            for (String t : a.produces()) delta.computeIfAbsent(m, k -> new HashMap<>()).merge(t, 2, Integer::sum);
            for (String t : a.consumes()) delta.computeIfAbsent(m, k -> new HashMap<>()).merge(t, -1, Integer::sum);
        }
        int changed = 0;
        for (var me : delta.entrySet())
            for (var te : me.getValue().entrySet())
                for (var t : s.items.types().all())
                    if (t.hasTag(te.getKey()) && !t.category().unique() && te.getValue() != 0) {
                        s.market.adjustSupply(me.getKey(), t.id(), Math.max(-20, Math.min(20, te.getValue())));
                        changed++;
                    }
        return changed;
    }
}
