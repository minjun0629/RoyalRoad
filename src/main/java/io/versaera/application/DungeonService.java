package io.versaera.application;

import io.versaera.application.port.DungeonRepository;
import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.dungeon.DungeonDefinition;
import io.versaera.domain.dungeon.DungeonLayout;
import io.versaera.domain.dungeon.DungeonRun;
import io.versaera.domain.dungeon.LeverPuzzle;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.quest.QuestDefinition;

import java.util.*;

/**
 * 던전 (DUN-01). 진행 상태는 메모리(DungeonRun), 시작 · 끝은 DB(dungeon_run)에 남는다.
 * 보상은 사람마다 idempotency key "dungeon:&lt;run&gt;:&lt;uuid&gt;" 로 한 번만, 그리고 던전마다 재사용 대기(cooldown)가 있다.
 * 서버가 꺼지면 진행 중이던 판은 실패로 처리된다 (보상 없음, 아이템 손실 없음).
 */
public final class DungeonService {
    public record Handle(String runId, DungeonDefinition def, DungeonRun run, List<String> members) {}

    private final TxRunner tx;
    private final DungeonRepository repo;
    private final ProgressRepository progress;
    private final Map<String, DungeonDefinition> defs = new LinkedHashMap<>();
    private final Map<String, Handle> runs = new HashMap<>();
    private final Map<String, String> runOf = new HashMap<>();
    private final GameServices s;
    private final EventBus bus;
    private final GameClock clock;

    DungeonService(TxRunner tx, DungeonRepository repo, ProgressRepository progress, Collection<DungeonDefinition> list, GameServices s, EventBus bus,
                   GameClock clock) {
        this.tx = tx;
        this.repo = repo;
        this.progress = progress;
        this.s = s;
        this.bus = bus;
        this.clock = clock;
        Set<String> bosses = new HashSet<>();
        s.content.bosses().forEach(b -> bosses.add(b.id()));
        for (DungeonDefinition d : list) {
            if (defs.putIfAbsent(d.id(), d) != null) throw new IllegalArgumentException("던전 id 중복: " + d.id());
            if (s.regions.byId(d.region()) == null) throw new IllegalArgumentException(d.id() + ": unknown region " + d.region());
            if (d.boss() != null && !bosses.contains(d.boss())) throw new IllegalArgumentException(d.id() + ": 없는 보스 " + d.boss());
            if (d.boss() == null && d.bossMob() == null) throw new IllegalArgumentException(d.id() + ": boss 또는 boss_mob 필요");
            QuestService.checkReward(d.reward(), d.id(), s);
            QuestService.checkReward(d.hiddenReward(), d.id(), s);
        }
    }

    public Collection<DungeonDefinition> all() {
        return Collections.unmodifiableCollection(defs.values());
    }

    public DungeonDefinition dungeon(String id) {
        DungeonDefinition d = defs.get(id);
        if (d == null) throw DomainException.of("dungeon.unknown", "없는 던전: " + id);
        return d;
    }

    public int recover() {
        return tx.inTx(() -> repo.failAllActive(clock.nowMillis()));
    }

    public Optional<Handle> runOf(String uuid) {
        String id = runOf.get(uuid);
        return id == null ? Optional.empty() : Optional.ofNullable(runs.get(id));
    }

    public Handle handle(String runId) {
        Handle h = runs.get(runId);
        if (h == null) throw DomainException.of("dungeon.no_run", "진행 중인 던전이 아닙니다");
        return h;
    }

    private static String cooldownKey(String dungeonId) {
        return "dungeon." + dungeonId + ".rewarded_at";
    }

    /** 보상을 다시 받을 수 있기까지 남은 시간 (0 이면 가능) */
    public long cooldownLeft(String uuid, String dungeonId) {
        long last = progress.counter(uuid, cooldownKey(dungeonId));
        return last == 0 ? 0 : Math.max(0, last + dungeon(dungeonId).cooldownMs() - clock.nowMillis());
    }

    public Handle start(String dungeonId, List<String> members, long seed) {
        DungeonDefinition d = dungeon(dungeonId);
        List<String> party = List.copyOf(new LinkedHashSet<>(members));
        DomainException.require(party.size() >= d.minParty() && party.size() <= d.maxParty(), "dungeon.party",
                d.name() + " 은(는) " + d.minParty() + " ~ " + d.maxParty() + "명이 들어갈 수 있습니다");
        for (String m : party) DomainException.require(!runOf.containsKey(m), "dungeon.busy", "이미 던전에 들어가 있는 사람이 있습니다");
        if (s.access != null)   // 연령 제한 (ACC-01): 미성년은 전투 모험 불가
            for (String m : party) DomainException.require(!s.access.minor(m), "dungeon.minor", "미성년 보호 대상은 던전에 들어갈 수 없습니다");
        String id = UUID.randomUUID().toString();
        DungeonRun run = new DungeonRun(DungeonLayout.generate(seed, d.rooms()), party, clock.nowMillis(), d.timeLimitMs(), d.levers());
        tx.inTx(() -> {
            repo.insert(id, d.id(), seed, String.join(",", party), clock.nowMillis());
            return null;
        });
        Handle h = new Handle(id, d, run, party);
        runs.put(id, h);
        for (String m : party) runOf.put(m, id);
        return h;
    }

    public void roomCleared(String runId, int room) {
        Handle h = handle(runId);
        h.run().roomCleared(room, clock.nowMillis());
        checkFailed(h);
    }

    public LeverPuzzle.Result pull(String runId, int lever) {
        Handle h = handle(runId);
        LeverPuzzle.Result r = h.run().pull(lever, clock.nowMillis());
        checkFailed(h);
        return r;
    }

    public boolean revealHidden(String runId, int fromRoom) {
        return handle(runId).run().revealHidden(fromRoom, clock.nowMillis());
    }

    public void memberDown(String runId, String uuid) {
        Handle h = handle(runId);
        h.run().memberDown(uuid, clock.nowMillis());
        checkFailed(h);
    }

    /** 시간 초과 확인 (플랫폼이 주기적으로) */
    public boolean tick(String runId) {
        Handle h = handle(runId);
        h.run().tick(clock.nowMillis());
        return checkFailed(h);
    }

    private boolean checkFailed(Handle h) {
        if (h.run().state() != DungeonRun.State.FAILED) return false;
        close(h, "FAILED");
        return true;
    }

    public void leave(String uuid) {
        runOf(uuid).ifPresent(h -> memberDown(h.runId(), uuid));
        runOf.remove(uuid);
    }

    private void close(Handle h, String state) {
        tx.inTx(() -> {
            repo.setState(h.runId(), state, clock.nowMillis());
            return null;
        });
        runs.remove(h.runId());
        for (String m : h.members()) runOf.remove(m, h.runId());
    }

    /** 보스 처치 → 판 종료 + 보상. @return 보상을 받은 사람 */
    public List<String> bossDefeated(String runId, Map<String, String> names) {
        Handle h = handle(runId);
        h.run().bossDefeated(clock.nowMillis());
        List<String> rewarded = new ArrayList<>();
        AfterCommit after = new AfterCommit();
        long now = clock.nowMillis();
        tx.inTx(() -> {
            repo.setState(h.runId(), "CLEARED", now);
            for (String m : h.members()) {
                if (cooldownLeft(m, h.def().id()) > 0) continue;
                String key = "dungeon:" + h.runId() + ":" + m;
                s.quests.pay(m, names.getOrDefault(m, "?"), h.def().reward(), key, after);
                if (h.run().hiddenFound()) s.quests.pay(m, names.getOrDefault(m, "?"), h.def().hiddenReward(), key + ":hidden", after);
                long last = progress.counter(m, cooldownKey(h.def().id()));
                progress.addCounter(m, cooldownKey(h.def().id()), now - last);
                rewarded.add(m);
            }
            s.audit.record("DUNGEON_CLEARED", null, h.runId(), h.def().id() + " " + h.members(), null);
            return null;
        });
        runs.remove(h.runId());
        for (String m : h.members()) runOf.remove(m, h.runId());
        after.publish(bus);
        for (String m : rewarded) {
            s.quests.grantAfterCommit(m, h.def().reward());
            if (h.run().hiddenFound()) s.quests.grantAfterCommit(m, h.def().hiddenReward());
        }
        for (String m : h.members()) {
            s.growth.record(m, "dungeon.cleared", 1);
            s.quests.record(m, QuestDefinition.Type.DUNGEON, h.def().id(), 1, 0);
            s.guilds.activity(m, 20L * h.def().danger());
        }
        bus.publish(new GameEvents.DungeonCleared(h.runId(), h.def().id(), h.members()));
        return rewarded;
    }
}
