package io.versaera.application;

import io.versaera.application.port.AdventureRepository;
import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.achievement.Achievement;
import io.versaera.domain.achievement.Title;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.hidden.Condition;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.domain.quest.QuestDefinition;

import java.util.*;

/**
 * 업적 · 칭호 · 모험가 기록 (ACH-01 ~ 03).
 * <ul>
 *   <li>행동 기록이 바뀌면 그 기록을 쓰는 업적만 다시 판정 (GrowthService.onCounter). 숙련 · 발견 · 관계 업적은 그 이벤트 때, 접속 때는 전부</li>
 *   <li>얻으면 한 트랜잭션에서: 기록(discovery 'achievement') · 돈 · 명성 · 칭호 → 서버 최초면 기록</li>
 *   <li>칭호는 얻은 것 중 하나를 달고 다닌다 (player_title)</li>
 * </ul>
 * 스레드: DB 스레드에서만.
 */
public final class AchievementService {
    public record Row(Achievement achievement, boolean earned) {}

    /** 모험가 기록 한 장 */
    public record Record(String uuid, long fame, long notoriety, int achievements, int achievementTotal, int points, int worldFirsts,
                         Map<String, Integer> discoveries, int regionsTotal, Map<String, Long> counters, Map<String, Integer> topMastery,
                         List<Title> titles, Title equipped, int pets, int mounts, int raidClears, Map<String, int[]> byCategory) {}

    /** 모험가 기록에 보이는 행동 기록 (키 → 이름) */
    public static final Map<String, String> SHOWN = linked(
            "kill.monster", "쓰러뜨린 몬스터", "boss.field", "필드 보스", "dungeon.cleared", "던전", "raid.cleared", "레이드",
            "quest.completed", "의뢰", "talk.npc", "NPC 와 이야기", "gift.npc", "선물", "death", "쓰러짐",
            "craft.smithing", "대장 제작", "craft.cooking", "요리", "gather.mining", "채광", "gather.logging", "벌목", "gather.fishing", "낚시",
            "art.created", "대형 조각", "pet.tamed", "길들인 동물", "ride.distance", "말 타고 (100블록)", "travel.carriage", "마차 여행",
            "travel.ship", "배 여행", "guild.quest", "길드 의뢰");

    private static Map<String, String> linked(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return Collections.unmodifiableMap(m);
    }

    private final TxRunner tx;
    private final ProgressRepository progress;
    private final AdventureRepository repo;
    private final GameServices s;
    private final EventBus bus;
    private final GameClock clock;
    private final Map<String, Achievement> byId = new LinkedHashMap<>();
    private final Map<String, List<Achievement>> byCounter = new HashMap<>();
    private final List<Achievement> other = new ArrayList<>();
    private final Map<String, Title> titles;

    AchievementService(TxRunner tx, ProgressRepository progress, AdventureRepository repo, GameServices s, List<Achievement> achievements,
                       Map<String, Title> titles, EventBus bus, GameClock clock) {
        this.tx = tx;
        this.progress = progress;
        this.repo = repo;
        this.s = s;
        this.bus = bus;
        this.clock = clock;
        this.titles = Map.copyOf(titles);
        for (Achievement a : achievements) {
            DomainException.require(byId.putIfAbsent(a.id(), a) == null, "ach.dup", "업적 중복: " + a.id());
            Set<String> keys = new HashSet<>();
            counters(a.when(), keys);
            for (String k : keys) byCounter.computeIfAbsent(k, x -> new ArrayList<>()).add(a);
            if (!onlyCounters(a.when())) other.add(a);
        }
        // 숙련 · 발견 · 관계가 바뀌면 그런 조건의 업적만
        bus.subscribe(GameEvents.MasteryLevelUp.class, e -> check(e.uuid(), other));
        bus.subscribe(GameEvents.PlayerDiscovered.class, e -> check(e.uuid(), other));
        bus.subscribe(GameEvents.NpcRelationChanged.class, e -> { if (e.delta() >= 5) check(e.uuid(), other); });
    }

    private static void counters(Condition c, Set<String> out) {
        switch (c) {
            case Condition.Counter k -> out.add(k.key());
            case Condition.All a -> a.parts().forEach(p -> counters(p, out));
            case Condition.Any a -> a.parts().forEach(p -> counters(p, out));
            default -> { }
        }
    }

    private static boolean onlyCounters(Condition c) {
        return switch (c) {
            case Condition.Counter k -> true;
            case Condition.All a -> a.parts().stream().allMatch(AchievementService::onlyCounters);
            case Condition.Any a -> a.parts().stream().allMatch(AchievementService::onlyCounters);
            default -> false;
        };
    }

    /** GrowthService.onCounter 에 연결 */
    public void counterChanged(String uuid, String key, long value) {
        List<Achievement> l = byCounter.get(key);
        if (l != null) check(uuid, l);
    }

    /** 접속 때 · 주기적으로 (명성처럼 행동 기록 경로 밖에서 오르는 값) */
    public List<Achievement> checkAll(String uuid) {
        return check(uuid, byId.values());
    }

    private List<Achievement> check(String uuid, Collection<Achievement> list) {
        PlayerFacts f = null;
        List<Achievement> got = new ArrayList<>();
        for (Achievement a : list) {
            if (progress.discovered(uuid, "achievement", a.id())) continue;
            if (f == null) f = s.facts(uuid, null, 12);
            if (a.when().test(f) && grant(uuid, a)) got.add(a);
        }
        return got;
    }

    private boolean grant(String uuid, Achievement a) {
        long now = clock.nowMillis();
        AfterCommit after = new AfterCommit();
        boolean[] first = {false};
        QuestDefinition.Reward r = new QuestDefinition.Reward(a.money(), List.of(), Map.of(), Map.of(), Map.of(), a.fame(),
                a.title() == null ? List.of() : List.of("title:" + a.title()));
        boolean fresh = tx.inTx(() -> {
            if (!progress.discover(uuid, "achievement", a.id(), now)) return false;
            first[0] = progress.claimWorldFirst("achievement", a.id(), uuid, uuid, now);
            s.quests.pay(uuid, uuid, r, "ach:" + uuid + ":" + a.id(), after);
            return true;
        });
        if (!fresh) return false;
        after.publish(bus);
        bus.publish(new GameEvents.AchievementEarned(uuid, a.id(), a.name(), first[0], a.title()));
        s.growth.record(uuid, "achievement.count", 1);   // 업적을 모으는 업적
        return true;
    }

    public Achievement achievement(String id) {
        Achievement a = byId.get(id);
        DomainException.require(a != null, "ach.unknown", "없는 업적: " + id);
        return a;
    }

    public Collection<Achievement> all() {
        return byId.values();
    }

    /** 목록 (숨은 업적은 얻기 전엔 이름을 가린다 — 화면이 earned 로 판단) */
    public List<Row> list(String uuid) {
        Set<String> earned = new HashSet<>(progress.discoveries(uuid, "achievement"));
        List<Row> out = new ArrayList<>();
        for (Achievement a : byId.values()) out.add(new Row(a, earned.contains(a.id())));
        return out;
    }

    // ------------------------------------------------------------------ 칭호
    public Title title(String id) {
        Title t = titles.get(id);
        if (t != null) return t;
        // 히든 규칙이 주는 칭호처럼 목록에 없는 것: 이름 = id
        return new Title(id.matches("[a-z0-9_]+") ? id : "custom", id.replace('_', ' '), "&f", "");
    }

    public List<Title> titles(String uuid) {
        List<Title> out = new ArrayList<>();
        for (String id : progress.discoveries(uuid, "title")) out.add(title(id));
        return out;
    }

    public Optional<Title> equipped(String uuid) {
        return repo.title(uuid).filter(t -> progress.discovered(uuid, "title", t)).map(this::title);
    }

    /** 칭호 달기 (null = 떼기). 얻은 칭호만 */
    public void equip(String uuid, String titleId) {
        if (titleId == null) {
            tx.inTx(() -> { repo.clearTitle(uuid); return null; });
        } else {
            DomainException.require(progress.discovered(uuid, "title", titleId), "title.not_owned", "얻지 않은 칭호입니다");
            tx.inTx(() -> { repo.setTitle(uuid, titleId, clock.nowMillis()); return null; });
        }
        bus.publish(new GameEvents.TitleChanged(uuid, titleId));
    }

    // ------------------------------------------------------------------ 모험가 기록
    public Record record(String uuid) {
        Set<String> earned = new HashSet<>(progress.discoveries(uuid, "achievement"));
        int points = 0;
        Map<String, int[]> cat = new LinkedHashMap<>();
        for (Achievement a : byId.values()) {
            int[] c = cat.computeIfAbsent(a.category(), k -> new int[2]);
            c[1]++;
            if (earned.contains(a.id())) {
                c[0]++;
                points += a.points();
            }
        }
        Map<String, Long> all = progress.allCounters(uuid);
        Map<String, Long> shown = new LinkedHashMap<>();
        SHOWN.keySet().forEach(k -> shown.put(k, all.getOrDefault(k, 0L)));
        Map<String, Integer> mastery = new LinkedHashMap<>();
        s.growth.levels(uuid).entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(6)
                .forEach(e -> mastery.put(e.getKey(), e.getValue()));
        var st = s.reputation.standing(uuid);
        return new Record(uuid, st.fame(), st.notoriety(), (int) byId.keySet().stream().filter(earned::contains).count(), byId.size(), points,
                progress.worldFirsts(uuid), progress.discoveryCounts(uuid), s.regions.all().size(), shown, mastery, titles(uuid),
                equipped(uuid).orElse(null), repo.pets(uuid).size(), repo.mounts(uuid).size(), repo.clearsOf(uuid), cat);
    }
}
