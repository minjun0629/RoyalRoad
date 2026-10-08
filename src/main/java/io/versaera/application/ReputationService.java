package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.faith.God;
import io.versaera.domain.faith.Temple;
import io.versaera.domain.reputation.Reputation;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 명성 · 악명 · 살인자 · 신전 기부 (REP-01 · GOD-01). 값은 행동 기록(counter)에 둔다: fame · notoriety · murderer_until.
 * 모두 서버가 정한다 — 누가 누구를 죽였는지는 서버의 사망 이벤트로만 판정.
 */
public final class ReputationService {
    public record Standing(long fame, long notoriety, long murdererUntil, boolean murderer) {
        public String fameName() {
            return Reputation.fameName(fame);
        }
    }

    public record Donation(Temple temple, God god, long paid, long cleansed, int blessingSeconds) {}

    private final TxRunner tx;
    private final ProgressRepository progress;
    private final EconomyService economy;
    private final RegionIndex regions;
    private final GameClock clock;
    private final Map<String, God> gods = new LinkedHashMap<>();
    private final Map<String, Temple> templeByRegion = new LinkedHashMap<>();

    public ReputationService(TxRunner tx, ProgressRepository progress, EconomyService economy, RegionIndex regions, Collection<God> gods,
                             Collection<Temple> temples, GameClock clock) {
        this.tx = tx;
        this.progress = progress;
        this.economy = economy;
        this.regions = regions;
        this.clock = clock;
        for (God g : gods) DomainException.require(this.gods.putIfAbsent(g.id(), g) == null, "god.dup", "신 id 중복: " + g.id());
        for (Temple t : temples) {
            God g = this.gods.get(t.god());
            DomainException.require(g != null, "temple.no_god", "신전의 신이 없습니다: " + t.id());
            DomainException.require(!g.evil(), "temple.evil", "악신의 신전은 기부를 받지 않습니다: " + t.id());
            DomainException.require(regions.byId(t.region()) != null, "temple.no_region", "신전 지역이 없습니다: " + t.id());
            DomainException.require(templeByRegion.putIfAbsent(t.region(), t) == null, "temple.dup", "한 지역에 신전 둘: " + t.region());
        }
    }

    public Collection<God> gods() {
        return gods.values();
    }

    public Standing standing(String uuid) {
        long until = progress.counter(uuid, "murderer_until");
        return new Standing(progress.counter(uuid, "fame"), progress.counter(uuid, "notoriety"), until, until > clock.nowMillis());
    }

    public long addFame(String uuid, long amount) {
        DomainException.require(amount > 0, "fame.bad", "명성은 더하기만");
        return tx.inTx(() -> progress.addCounter(uuid, "fame", amount));
    }

    /** 사람이 사람을 죽였다 (서버의 사망 이벤트에서만 부른다). 살인자를 죽이면 아무 일도 없다 */
    public Reputation.Kill playerKilled(String killer, String victim) {
        DomainException.require(!killer.equals(victim), "rep.self", "자기 자신");
        return tx.inTx(() -> {
            boolean victimMurderer = progress.counter(victim, "murderer_until") > clock.nowMillis();
            long before = progress.counter(killer, "notoriety");
            Reputation.Kill k = Reputation.onKill(victimMurderer, before);
            if (k.notorietyGain() > 0) {
                progress.addCounter(killer, "notoriety", k.notorietyGain());
                long now = clock.nowMillis(), cur = progress.counter(killer, "murderer_until");
                long until = Math.max(cur, now) + k.murderMs();
                progress.addCounter(killer, "murderer_until", until - cur);
            }
            return k;
        });
    }

    /** 몬스터 사냥: 악명 1 · 살인자 시간 1분이 씻긴다 */
    public void monsterKilled(String uuid) {
        tx.inTx(() -> {
            if (progress.counter(uuid, "notoriety") > 0) progress.addCounter(uuid, "notoriety", -1);
            long now = clock.nowMillis(), cur = progress.counter(uuid, "murderer_until");
            if (cur > now) progress.addCounter(uuid, "murderer_until", -Math.min(cur - now, Reputation.CLEANSE_MS_PER_KILL));
            return null;
        });
    }

    /** 이 지역(또는 감싼 지역)의 신전 */
    public Optional<Temple> templeAt(String regionId) {
        for (Region r = regionId == null ? null : regions.byId(regionId); r != null; r = r.parent() == null ? null : regions.byId(r.parent())) {
            Temple t = templeByRegion.get(r.id());
            if (t != null) return Optional.of(t);
        }
        return Optional.empty();
    }

    /**
     * 신전 기부: 돈을 내면 먼저 악명을 씻고(악명이 많을수록 1 포인트가 비싸다), 악명이 0 이 되면 살인자 상태도 풀린다.
     * 남은 돈 20쿠퍼마다 축복 1초 (최대 30분). 같은 key 는 한 번만 처리.
     */
    public Donation donate(String uuid, String regionId, long amount, String key) {
        Temple t = templeAt(regionId).orElseThrow(() -> DomainException.of("temple.none", "신전 안에서만 기부할 수 있습니다"));
        DomainException.require(amount >= 10, "temple.too_little", "10쿠퍼 이상 기부할 수 있습니다");
        long notoriety = progress.counter(uuid, "notoriety");
        long cleansed = Reputation.cleansedBy(amount, notoriety);
        long cost = 0;
        for (long n = notoriety, i = 0; i < cleansed; i++, n--) cost += Reputation.donationPerPoint(n);
        int blessing = (int) Math.min(1800, (amount - cost) / (20));
        tx.inTx(() -> {   // 돈과 악명을 한 트랜잭션에서 (중첩 트랜잭션)
            if (!economy.withdraw(uuid, amount, "temple:" + t.id(), key)) throw DomainException.of("temple.duplicate", "이미 처리한 기부입니다");
            if (cleansed > 0) progress.addCounter(uuid, "notoriety", -cleansed);
            if (progress.counter(uuid, "notoriety") <= 0) {
                long cur = progress.counter(uuid, "murderer_until");
                if (cur > 0) progress.addCounter(uuid, "murderer_until", -cur);
            }
            return null;
        });
        return new Donation(t, gods.get(t.god()), amount, cleansed, blessing);
    }
}
