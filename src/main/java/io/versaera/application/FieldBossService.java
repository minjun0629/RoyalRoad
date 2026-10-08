package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.fieldboss.FieldBoss;
import io.versaera.domain.quest.QuestDefinition;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * 필드 보스 (BOS-02). 몸 · 패턴은 플랫폼(FieldBossRuntime), 여기서는 다시 나타날 때 · 보상 · 드롭을 정한다.
 * 서버 공용 값은 progress counter 의 가짜 주인 {@link #WORLD} 에 둔다: fboss.&lt;id&gt;.next (다시 나타나는 시각) · fboss.&lt;id&gt;.gen (몇 번째 출현).
 * 보상은 출현 번호(gen)로 한 번만 — 같은 처치가 두 번 보고돼도 두 번 주지 않는다.
 */
public final class FieldBossService {
    public static final String WORLD = "world";

    /** @param drops 가장 많이 때린 사람이 얻은 아이템 이름 */
    public record Defeat(String bossId, String top, Set<String> rewarded, List<String> drops, boolean worldFirst, long nextSpawnAt) {}

    private final TxRunner tx;
    private final ProgressRepository progress;
    private final GameServices s;
    private final GameClock clock;
    private final Map<String, FieldBoss> bosses = new LinkedHashMap<>();

    FieldBossService(TxRunner tx, ProgressRepository progress, Collection<FieldBoss> defs, GameServices s, GameClock clock) {
        this.tx = tx;
        this.progress = progress;
        this.s = s;
        this.clock = clock;
        for (FieldBoss b : defs) {
            DomainException.require(bosses.putIfAbsent(b.id(), b) == null, "fboss.dup", "필드 보스 id 중복: " + b.id());
            DomainException.require(s.regions.byId(b.region()) != null, "fboss.region", "없는 둥지 지역: " + b.region() + " (" + b.id() + ")");
            for (FieldBoss.Drop d : b.drops()) s.items.types().get(d.item());
            for (String it : b.reward().items()) s.items.types().get(it.split(":")[0]);
            for (String d : b.reward().xp().keySet()) s.growth.discipline(d);
        }
    }

    public Collection<FieldBoss> all() {
        return bosses.values();
    }

    public FieldBoss boss(String id) {
        FieldBoss b = bosses.get(id);
        if (b == null) throw DomainException.of("fboss.unknown", "없는 필드 보스: " + id);
        return b;
    }

    public long nextSpawnAt(String id) {
        return progress.counter(WORLD, "fboss." + id + ".next");
    }

    /** 지금까지 몇 번 쓰러졌나 (= 지금 출현 번호) */
    public long generation(String id) {
        return progress.counter(WORLD, "fboss." + id + ".gen");
    }

    public boolean ready(String id) {
        return nextSpawnAt(id) <= clock.nowMillis();
    }

    /**
     * 쓰러뜨림. 플랫폼은 나타날 때 읽은 generation 을 그대로 넘긴다 — 이미 처리된 출현이면 예외.
     *
     * @param damage 사람 → 준 피해 (서버가 센 값)
     */
    /** 보스 보상: 숙련 경험은 Progression.BOSS_XP 배 (레벨이 맞으면 한 마리가 사냥 30 ~ 60 분어치) */
    static QuestDefinition.Reward reward(FieldBoss b) {
        QuestDefinition.Reward r = b.reward();
        Map<String, Integer> xp = new java.util.LinkedHashMap<>();
        r.xp().forEach((d, v) -> xp.put(d, (int) Math.round(v * io.versaera.domain.balance.Progression.BOSS_XP)));
        return new QuestDefinition.Reward(r.money(), r.items(), xp, r.affinity(), r.reputation(), r.fame(), r.unlocks());
    }

    public Defeat defeated(String id, long generation, Map<String, Double> damage, Map<String, String> names, RandomGenerator rng) {
        FieldBoss b = boss(id);
        DomainException.require(!damage.isEmpty(), "fboss.no_one", "보상 받을 사람이 없습니다");
        double total = damage.values().stream().mapToDouble(Double::doubleValue).sum();
        String top = damage.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        Set<String> rewarded = new LinkedHashSet<>();
        for (var e : damage.entrySet()) if (e.getKey().equals(top) || e.getValue() >= total * FieldBoss.SHARE) rewarded.add(e.getKey());
        List<String> dropped = new ArrayList<>();
        for (FieldBoss.Drop d : b.drops()) if (rng.nextDouble() < d.chance()) dropped.add(d.item() + ":" + d.quality() + ":1");
        AfterCommit after = new AfterCommit();
        long now = clock.nowMillis(), next = now + b.respawnMinutes() * 60_000L;
        boolean[] first = {false};
        tx.inTx(() -> {
            long gen = progress.counter(WORLD, "fboss." + id + ".gen");
            DomainException.require(gen == generation, "fboss.done", "이미 처리된 처치입니다");
            progress.addCounter(WORLD, "fboss." + id + ".gen", 1);
            progress.addCounter(WORLD, "fboss." + id + ".next", next - progress.counter(WORLD, "fboss." + id + ".next"));
            String key = "fboss:" + id + ":" + generation;
            for (String u : rewarded) s.quests.pay(u, names.getOrDefault(u, "?"), reward(b), key + ":" + u, after);
            if (!dropped.isEmpty())
                s.quests.pay(top, names.getOrDefault(top, "?"), new QuestDefinition.Reward(0, dropped, null, null, null, 0, null), key + ":drop", after);
            first[0] = progress.claimWorldFirst("field_boss", id, top, names.getOrDefault(top, "?"), now);
            s.audit.record("FIELD_BOSS_DEFEATED", top, id, "gen=" + generation + " drops=" + dropped + " to=" + rewarded, key);
            return null;
        });
        after.publish(s.bus);
        for (String u : rewarded) {
            s.quests.grantAfterCommit(u, reward(b));
            s.growth.record(u, "boss.field", 1);
            s.quests.record(u, QuestDefinition.Type.BOSS, id, 1, 0);
        }
        s.npcWorld.bossSlain(b.name(), b.region(), rewarded, "fboss:" + id + ":" + generation);   // 그 지역 사람들이 기억하고 번영한다
        List<String> names2 = new ArrayList<>();
        for (String d : dropped) names2.add(s.items.types().get(d.split(":")[0]).name());
        return new Defeat(id, top, rewarded, names2, first[0], next);
    }
}
