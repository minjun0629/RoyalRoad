package io.versaera.application;

import io.versaera.application.port.AdventureRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.raid.RaidDefinition;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * 레이드 (RAID-01): 시작 조건 확인 · 주간 귀속 · 공략 보상 · 공략 기록(가장 빠른 순).
 * 보스 전투 자체는 BossService/BossRuntime 이 맡고(보스 보상도 따로), 여기서는 공격대 단위의 일만 한다.
 * 스레드: DB 스레드에서만.
 */
public final class RaidService {
    public record Run(String runId, RaidDefinition def, String leader, List<String> members, long startedAt) {
        public long deadline() {
            return startedAt + def.timeLimitMs();
        }
    }

    public static final List<String> COMBAT = List.of("swordsmanship", "spearmanship", "archery", "spellcraft");

    private final TxRunner tx;
    private final AdventureRepository repo;
    private final GameServices s;
    private final EventBus bus;
    private final GameClock clock;
    private final ZoneId zone;
    private final Map<String, RaidDefinition> defs = new LinkedHashMap<>();
    private final Map<String, Run> live = new LinkedHashMap<>();

    RaidService(TxRunner tx, AdventureRepository repo, GameServices s, List<RaidDefinition> list, EventBus bus, GameClock clock, ZoneId zone) {
        this.tx = tx;
        this.repo = repo;
        this.s = s;
        this.bus = bus;
        this.clock = clock;
        this.zone = zone;
        for (RaidDefinition d : list) {
            DomainException.require(defs.putIfAbsent(d.id(), d) == null, "raid.dup", "레이드 중복: " + d.id());
            s.bosses.boss(d.boss());
            DomainException.require(s.regions.byId(d.region()) != null, "raid.region", "레이드 지역이 없습니다: " + d.region());
        }
    }

    public long week() {
        return Math.floorDiv(Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate().toEpochDay() + 3, 7);
    }

    public Collection<RaidDefinition> all() {
        return defs.values();
    }

    public RaidDefinition raid(String id) {
        RaidDefinition d = defs.get(id);
        DomainException.require(d != null, "raid.unknown", "없는 레이드: " + id);
        return d;
    }

    public boolean lockedOut(String uuid, String raidId) {
        return repo.lockedOut(uuid, raidId, week());
    }

    public static int combatLevel(GameServices s, String uuid) {
        int best = 0;
        for (String d : COMBAT) best = Math.max(best, s.growth.level(uuid, d));
        return best;
    }

    /**
     * 시작. where = 공격대원 → 지금 지역 (플랫폼이 확인). 모두 레이드 지역 안 · 인원 · 숙련 · 이번 주 귀속을 본다.
     */
    public Run start(String raidId, String leader, List<String> members, Map<String, String> where, Map<String, String> names) {
        RaidDefinition d = raid(raidId);
        DomainException.require(live.values().stream().noneMatch(r -> r.def().id().equals(raidId)), "raid.busy", "다른 공격대가 이미 도전 중입니다");
        DomainException.require(members.contains(leader), "raid.leader", "공격대장이 공격대에 없습니다");
        DomainException.require(members.size() >= d.minPlayers() && members.size() <= d.maxPlayers(), "raid.size",
                d.name() + " 은(는) " + d.minPlayers() + "~" + d.maxPlayers() + "명 (지금 " + members.size() + "명)");
        long w = week();
        for (String m : members) {
            String n = names.getOrDefault(m, "?");
            DomainException.require(d.region().equals(where.get(m)), "raid.away", n + " 이(가) " + s.regions.byId(d.region()).name() + " 안에 없습니다");
            DomainException.require(!repo.lockedOut(m, raidId, w), "raid.locked", n + " 은(는) 이번 주에 이미 공략했습니다");
            DomainException.require(combatLevel(s, m) >= d.mastery(), "raid.weak", n + " 의 전투 숙련이 " + d.mastery() + " 보다 낮습니다");
        }
        Run r = new Run(UUID.randomUUID().toString(), d, leader, List.copyOf(members), clock.nowMillis());
        live.put(r.runId(), r);
        s.audit.record("RAID_STARTED", leader, r.runId(), raidId + " " + members.size() + "명", null);
        return r;
    }

    public Optional<Run> run(String runId) {
        return Optional.ofNullable(live.get(runId));
    }

    /** 시간이 다 된 도전 (플랫폼이 보스를 물린다) */
    public List<Run> expired() {
        long now = clock.nowMillis();
        List<Run> out = new ArrayList<>();
        for (Run r : List.copyOf(live.values())) if (now > r.deadline()) { live.remove(r.runId()); out.add(r); }
        return out;
    }

    public void failed(String runId) {
        Run r = live.remove(runId);
        if (r != null) s.audit.record("RAID_FAILED", r.leader(), runId, r.def().id(), null);
    }

    /** 보스를 쓰러뜨렸다: 귀속 · 보상 · 기록. @return 서버 최고 기록이면 true */
    public boolean cleared(String runId) {
        Run r = live.remove(runId);
        DomainException.require(r != null, "raid.not_active", "끝난 레이드입니다");
        RaidDefinition d = r.def();
        long now = clock.nowMillis(), dur = now - r.startedAt(), w = week();
        DomainException.require(now <= r.deadline(), "raid.late", "시간이 지났습니다");
        AfterCommit after = new AfterCommit();
        QuestDefinition.Reward reward = new QuestDefinition.Reward(d.money(), d.items(), Map.of(), Map.of(), Map.of(), d.fame(),
                d.title() == null ? List.of() : List.of("title:" + d.title()));
        boolean[] best = {false};
        List<String> paid = new ArrayList<>();
        tx.inTx(() -> {
            var top = repo.bestClears(d.id(), 1);
            best[0] = top.isEmpty() || dur < top.get(0).durationMs();
            repo.insertClear(new AdventureRepository.RaidClear(runId, d.id(), r.leader(), String.join(",", r.members()), dur, now));
            for (String m : r.members()) {
                if (!repo.addLockout(m, d.id(), w)) continue;   // 같은 주 두 번은 보상 없음
                s.quests.pay(m, m, reward, "raid:" + runId + ":" + m, after);
                paid.add(m);
            }
            s.audit.record("RAID_CLEARED", r.leader(), runId, d.id() + " " + dur + "ms " + paid.size() + "명", null);
            return null;
        });
        after.publish(bus);
        for (String m : paid) {
            s.growth.record(m, "raid.cleared", 1);
            s.guilds.activity(m, 200);
        }
        bus.publish(new GameEvents.RaidCleared(d.id(), runId, r.members(), dur, best[0]));
        return best[0];
    }

    public List<AdventureRepository.RaidClear> best(String raidId, int limit) {
        return repo.bestClears(raidId, limit);
    }
}
