package io.versaera.application;

import io.versaera.application.port.BossRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.boss.BossDefinition;
import io.versaera.domain.boss.BossRewards;
import io.versaera.domain.boss.BossRewards.Contribution;
import io.versaera.domain.boss.BossRewards.Tier;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.quest.QuestDefinition;

import java.util.*;

/**
 * 보스 전투 기록 · 보상 (BOS-02). 기여도는 전투 중 메모리에 모으고, 끝날 때 한 트랜잭션으로 저장 + 보상.
 * 보상 key: "boss:&lt;fight&gt;:&lt;uuid&gt;" — 같은 전투에서 두 번 받지 않는다. 처음 잡은 사람은 world_first 에 남는다.
 */
public final class BossService {
    public record Outcome(Map<String, Tier> tiers, boolean worldFirst) {}

    private final TxRunner tx;
    private final BossRepository repo;
    private final Map<String, BossDefinition> defs = new LinkedHashMap<>();
    private final Map<String, Map<String, Contribution>> live = new HashMap<>();
    private final Map<String, String> bossOf = new HashMap<>();
    private final GameServices s;
    private final EventBus bus;
    private final GameClock clock;

    BossService(TxRunner tx, BossRepository repo, Collection<BossDefinition> list, GameServices s, EventBus bus, GameClock clock) {
        this.tx = tx;
        this.repo = repo;
        this.s = s;
        this.bus = bus;
        this.clock = clock;
        for (BossDefinition d : list) {
            defs.put(d.id(), d);
            QuestService.checkReward(d.reward(), d.id(), s);
        }
    }

    public BossDefinition boss(String id) {
        BossDefinition d = defs.get(id);
        if (d == null) throw DomainException.of("boss.unknown", "없는 보스: " + id);
        return d;
    }

    public int recover() {
        return tx.inTx(() -> repo.failAllActive(clock.nowMillis()));
    }

    public String start(String bossId) {
        boss(bossId);
        String id = UUID.randomUUID().toString();
        tx.inTx(() -> {
            repo.start(id, bossId, clock.nowMillis());
            return null;
        });
        live.put(id, new LinkedHashMap<>());
        bossOf.put(id, bossId);
        return id;
    }

    public void contribute(String fightId, String uuid, long damage, long mitigated, long support) {
        Map<String, Contribution> m = live.get(fightId);
        if (m == null) return;   // 이미 끝난 전투 (늦게 온 타격)
        m.merge(uuid, new Contribution(Math.max(0, damage), Math.max(0, mitigated), Math.max(0, support)), Contribution::plus);
    }

    public Map<String, Contribution> contributions(String fightId) {
        return Map.copyOf(live.getOrDefault(fightId, Map.of()));
    }

    public void fail(String fightId) {
        Map<String, Contribution> m = live.remove(fightId);
        bossOf.remove(fightId);
        tx.inTx(() -> {
            repo.finish(fightId, "FAILED", clock.nowMillis());
            if (m != null) m.forEach((u, c) -> repo.saveContribution(fightId, u, c));
            return null;
        });
    }

    public Outcome defeated(String fightId, Map<String, String> names) {
        Map<String, Contribution> m = live.remove(fightId);
        String bossId = bossOf.remove(fightId);
        DomainException.require(m != null, "boss.not_active", "끝난 전투입니다");
        BossDefinition d = boss(bossId);
        Map<String, Tier> tiers = BossRewards.tiers(m);
        AfterCommit after = new AfterCommit();
        boolean[] first = {false};
        boolean ok = tx.inTx(() -> {
            if (!repo.finish(fightId, "DEFEATED", clock.nowMillis())) return false;
            m.forEach((u, c) -> repo.saveContribution(fightId, u, c));
            for (Map.Entry<String, Tier> e : tiers.entrySet()) {
                if (e.getValue() == Tier.NONE) continue;
                String key = "boss:" + fightId + ":" + e.getKey();
                s.quests.pay(e.getKey(), names.getOrDefault(e.getKey(), "?"), d.reward(), key, after);
                if (e.getValue() == Tier.MVP && d.reward().money() > 0)
                    s.economy.depositInTx(e.getKey(), d.reward().money() / 5, "boss_mvp", key + ":mvp", after);
            }
            String top = m.entrySet().stream().max(Comparator.comparingDouble(x -> x.getValue().score())).map(Map.Entry::getKey).orElse(null);
            if (top != null) first[0] = s.progress.claimWorldFirst("boss", bossId, top, names.getOrDefault(top, "?"), clock.nowMillis());
            s.audit.record("BOSS_DEFEATED", top, fightId, bossId + " " + tiers, null);
            return true;
        });
        DomainException.require(ok, "boss.not_active", "끝난 전투입니다");
        after.publish(bus);
        for (Map.Entry<String, Tier> e : tiers.entrySet()) {
            if (e.getValue() == Tier.NONE) continue;
            s.quests.grantAfterCommit(e.getKey(), d.reward());
            s.growth.record(e.getKey(), "boss.defeated", 1);
            s.quests.record(e.getKey(), QuestDefinition.Type.BOSS, bossId, 1, 0);
            s.guilds.activity(e.getKey(), 100);
        }
        bus.publish(new GameEvents.BossDefeated(bossId, List.copyOf(tiers.keySet())));
        return new Outcome(tiers, first[0]);
    }
}
