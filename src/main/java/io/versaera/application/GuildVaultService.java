package io.versaera.application;

import io.versaera.application.port.AdventureRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.guild.GuildQuestDef;
import io.versaera.domain.item.ItemType;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * 길드 창고 · 길드 주간 의뢰 (GLD-02 · GLD-03).
 * <ul>
 *   <li>창고: 묶음 재료만 (종류 · 품질 · 수). 넣기는 누구나, 꺼내기는 계급별 하루 한도. 모든 넣고 뺌은 요청 key 로 한 번만 · 기록이 남는다</li>
 *   <li>꺼낸 물건은 배달함으로 (인벤토리가 가득 차도 잃지 않는다)</li>
 *   <li>주간 의뢰: 길드 id · 주 번호로 결정적으로 3개. 길드원의 행동 기록 증가를 합쳐 채우고, 다 채우면 금고 · 활동 점수 · 기여자 명성</li>
 * </ul>
 * 스레드: DB 스레드에서만.
 */
public final class GuildVaultService {
    public record QuestState(GuildQuestDef def, long progress, boolean done, List<AdventureRepository.Contribution> top) {}

    public static final int QUESTS_PER_WEEK = 3;

    private final TxRunner tx;
    private final AdventureRepository repo;
    private final GameServices s;
    private final EventBus bus;
    private final GameClock clock;
    private final ZoneId zone;
    private final List<GuildQuestDef> defs;
    private final Map<String, Integer> withdrawLimit;
    private final int maxKinds;

    GuildVaultService(TxRunner tx, AdventureRepository repo, GameServices s, List<GuildQuestDef> defs, Map<String, Integer> withdrawLimit, int maxKinds,
                      EventBus bus, GameClock clock, ZoneId zone) {
        this.tx = tx;
        this.repo = repo;
        this.s = s;
        this.defs = List.copyOf(defs);
        this.withdrawLimit = Map.copyOf(withdrawLimit);
        this.maxKinds = maxKinds;
        this.bus = bus;
        this.clock = clock;
        this.zone = zone;
        DomainException.require(defs.size() >= QUESTS_PER_WEEK, "gq.too_few", "길드 의뢰가 " + QUESTS_PER_WEEK + "개보다 적습니다");
        s.growth.onCounterDelta(this::counterAdded);
    }

    private long day() {
        return Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate().toEpochDay();
    }

    /** 월요일에 바뀌는 주 번호 (1970-01-01 은 목요일) */
    public long week() {
        return Math.floorDiv(day() + 3, 7);
    }

    private io.versaera.application.port.GuildRepository.Member member(String uuid) {
        return s.guilds.membership(uuid).orElseThrow(() -> DomainException.of("guild.none", "길드에 들어 있지 않습니다"));
    }

    // ------------------------------------------------------------------ 창고
    public List<AdventureRepository.Stored> storage(String uuid) {
        return repo.storage(member(uuid).guildId());
    }

    /** 오늘 더 꺼낼 수 있는 양 (-1 = 제한 없음) */
    public long withdrawLeft(String uuid) {
        var m = member(uuid);
        int limit = withdrawLimit.getOrDefault(m.rank(), 0);
        if (limit < 0) return -1;
        return Math.max(0, limit - repo.withdrawnOn(m.guildId(), uuid, day()));
    }

    /**
     * 넣기: 플랫폼이 인벤토리에서 먼저 빼서 넘긴다. 실패하면 배달함으로 돌려준다.
     * @return 넣은 뒤 창고의 그 재료 수
     */
    public long deposit(String uuid, String typeId, int quality, int amount, String requestId) {
        boolean[] done = {false};
        try {
            long v = doDeposit(uuid, typeId, quality, amount, requestId);
            done[0] = true;
            return v;
        } finally {
            if (!done[0] && amount > 0 && amount <= 64 * 36) s.items.deliverBulk(uuid, typeId, quality, amount, "guild_storage_refund");
        }
    }

    private long doDeposit(String uuid, String typeId, int quality, int amount, String requestId) {
        ItemType t = s.items.types().get(typeId);
        DomainException.require(!t.category().unique(), "gstore.unique", "장비는 길드 창고에 넣을 수 없습니다 (재료만)");
        DomainException.require(amount > 0 && amount <= 64 * 36, "gstore.bad_amount", "수량이 잘못되었습니다");
        var m = member(uuid);
        long now = clock.nowMillis();
        long after = tx.inTx(() -> {
            long cur = repo.stored(m.guildId(), typeId, quality);
            if (cur == 0) DomainException.require(repo.storage(m.guildId()).size() < maxKinds, "gstore.full", "창고 칸이 가득 찼습니다 (" + maxKinds + "종류)");
            DomainException.require(repo.logStorage("gs:" + requestId, m.guildId(), uuid, typeId, quality, amount, day(), now), "gstore.dup", "이미 처리한 요청입니다");
            repo.setStored(m.guildId(), typeId, quality, cur + amount);
            s.audit.record("GUILD_STORE_IN", uuid, m.guildId(), typeId + ":" + quality + " x" + amount, "gs:" + requestId);
            return cur + amount;
        });
        // 창고 비축 의뢰: 재료 태그가 맞으면 넣은 만큼
        for (GuildQuestDef d : quests(m.guildId())) if (d.deposit() && t.tags().contains(d.counter())) progress(m.guildId(), uuid, d, amount);
        return after;
    }

    /** 꺼내기: 계급별 하루 한도. 배달함으로 간다 */
    public long withdraw(String uuid, String typeId, int quality, int amount, String requestId) {
        DomainException.require(amount > 0 && amount <= 64 * 9, "gstore.bad_amount", "한 번에 576개까지");
        var m = member(uuid);
        long now = clock.nowMillis();
        long left = tx.inTx(() -> {
            int limit = withdrawLimit.getOrDefault(m.rank(), 0);
            if (limit >= 0) DomainException.require(repo.withdrawnOn(m.guildId(), uuid, day()) + amount <= limit, "gstore.limit",
                    "오늘 꺼낼 수 있는 양을 넘었습니다 (" + limit + "개)");
            long cur = repo.stored(m.guildId(), typeId, quality);
            DomainException.require(cur >= amount, "gstore.short", "창고에 " + cur + "개뿐입니다");
            DomainException.require(repo.logStorage("gs:" + requestId, m.guildId(), uuid, typeId, quality, -amount, day(), now), "gstore.dup", "이미 처리한 요청입니다");
            repo.setStored(m.guildId(), typeId, quality, cur - amount);
            s.items.deliverBulk(uuid, typeId, quality, amount, "guild_storage");
            s.audit.record("GUILD_STORE_OUT", uuid, m.guildId(), typeId + ":" + quality + " x" + amount, "gs:" + requestId);
            return cur - amount;
        });
        return left;
    }

    // ------------------------------------------------------------------ 주간 의뢰
    /** 이 길드의 이번 주 의뢰 3개 (결정적) */
    public List<GuildQuestDef> quests(String guildId) {
        long w = week();
        return defs.stream().sorted(Comparator.comparingLong((GuildQuestDef d) -> mix(guildId.hashCode() * 31L + w * 1_000_003L + d.id().hashCode()))
                .thenComparing(GuildQuestDef::id)).limit(QUESTS_PER_WEEK).toList();
    }

    public List<QuestState> questStates(String uuid) {
        String g = member(uuid).guildId();
        long w = week();
        Map<String, AdventureRepository.GuildQuestRow> rows = new HashMap<>();
        for (var r : repo.guildQuests(g, w)) rows.put(r.questId(), r);
        List<QuestState> out = new ArrayList<>();
        for (GuildQuestDef d : quests(g)) {
            var r = rows.get(d.id());
            List<AdventureRepository.Contribution> top = repo.contributions(g, w, d.id());
            out.add(new QuestState(d, r == null ? 0 : Math.min(d.target(), r.progress()), r != null && r.doneAt() > 0, top.subList(0, Math.min(5, top.size()))));
        }
        return out;
    }

    private void counterAdded(String uuid, String key, long delta) {
        if (key.equals("guild.quest") || key.equals("achievement.count")) return;
        var m = s.guilds.membership(uuid);
        if (m.isEmpty()) return;
        for (GuildQuestDef d : quests(m.get().guildId())) if (!d.deposit() && d.counter().equals(key)) progress(m.get().guildId(), uuid, d, delta);
    }

    private void progress(String guildId, String uuid, GuildQuestDef d, long delta) {
        long w = week(), now = clock.nowMillis();
        AfterCommit after = new AfterCommit();
        List<String> contributors = new ArrayList<>();
        boolean finished = tx.inTx(() -> {
            var row = repo.guildQuests(guildId, w).stream().filter(r -> r.questId().equals(d.id())).findFirst();
            if (row.isPresent() && row.get().doneAt() > 0) return false;
            long p = repo.addGuildQuestProgress(guildId, w, d.id(), delta);
            repo.addContribution(guildId, w, d.id(), uuid, delta);
            if (p < d.target() || !repo.finishGuildQuest(guildId, w, d.id(), now)) return false;
            if (d.money() > 0) s.economy.depositInTx(GuildService.wallet(guildId), d.money(), "guild_quest", "gq:" + guildId + ":" + w + ":" + d.id(), after);
            for (var c : repo.contributions(guildId, w, d.id())) contributors.add(c.uuid());
            s.audit.record("GUILD_QUEST_DONE", uuid, guildId, d.id() + " w" + w, null);
            return true;
        });
        if (!finished) return;
        after.publish(bus);
        for (String c : contributors) {
            s.guilds.activity(c, Math.max(1, d.activity() / Math.max(1, contributors.size())));
            s.reputation.addFame(c, 10);
            s.growth.record(c, "guild.quest", 1);
        }
        bus.publish(new GameEvents.GuildQuestDone(guildId, d.id(), d.name(), d.money()));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
