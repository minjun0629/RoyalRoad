package io.versaera.application;

import io.versaera.application.port.AuditLog;
import io.versaera.application.port.GuildRepository;
import io.versaera.application.port.GuildRepository.Guild;
import io.versaera.application.port.GuildRepository.Member;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.guild.GuildRules;
import io.versaera.domain.guild.GuildRules.Rank;

import java.util.*;

/**
 * 길드 (GLD-01). 금고는 지갑 "guild:&lt;id&gt;" — 넣고 빼는 것이 모두 원장 · 감사 로그가 남는 돈 이동이다.
 * 초대는 메모리에만 두고 5분 뒤 사라진다 (DB 스레드에서만 접근).
 */
public final class GuildService {
    private final TxRunner tx;
    private final GuildRepository repo;
    private final EconomyService economy;
    private final AuditLog audit;
    private final EventBus bus;
    private final GameClock clock;
    /** 초대받은 사람 → (길드 id → 만료 시각) */
    private final Map<String, Map<String, Long>> invites = new HashMap<>();

    public GuildService(TxRunner tx, GuildRepository repo, EconomyService economy, AuditLog audit, EventBus bus, GameClock clock) {
        this.tx = tx;
        this.repo = repo;
        this.economy = economy;
        this.audit = audit;
        this.bus = bus;
        this.clock = clock;
    }

    public static String wallet(String guildId) {
        return "guild:" + guildId;
    }

    public Optional<Member> membership(String uuid) {
        return repo.member(uuid);
    }

    public Optional<Guild> guildOf(String uuid) {
        return repo.member(uuid).flatMap(m -> repo.find(m.guildId()));
    }

    public Optional<Guild> find(String id) {
        return repo.find(id);
    }

    public Optional<Guild> byName(String name) {
        return repo.byName(name);
    }

    public List<Member> members(String guildId) {
        return repo.members(guildId);
    }

    public long treasury(String guildId) {
        return economy.balance(wallet(guildId));
    }

    private Member need(String uuid) {
        return repo.member(uuid).orElseThrow(() -> DomainException.of("guild.none", "길드에 속해 있지 않습니다"));
    }

    private static Rank rank(Member m) {
        return Rank.valueOf(m.rank());
    }

    public Guild create(String uuid, String name, String tag, String requestId) {
        GuildRules.validate(name, tag);
        AfterCommit after = new AfterCommit();
        Guild g = tx.inTx(() -> {
            DomainException.require(repo.member(uuid).isEmpty(), "guild.already", "이미 길드에 속해 있습니다");
            DomainException.require(!repo.nameOrTagTaken(name, tag), "guild.taken", "같은 이름이나 태그의 길드가 있습니다");
            String id = UUID.randomUUID().toString();
            String key = "guild_create:" + requestId;
            long b = economy.balance(uuid);
            DomainException.require(b >= GuildRules.CREATE_COST, "money.insufficient", "길드를 만들려면 " + io.versaera.domain.economy.Money.format(GuildRules.CREATE_COST) + " 필요합니다");
            // 창설비는 사라지는 돈(싱크) — 시스템 지갑으로 보낸다
            economy.transferInTx(uuid, "system:sink", GuildRules.CREATE_COST, "guild_create", key, after);
            Guild ng = new Guild(id, name, tag, uuid, 1, 0, clock.nowMillis());
            repo.create(ng);
            repo.addMember(new Member(uuid, id, Rank.LEADER.name(), 0, clock.nowMillis()));
            audit.record("GUILD_CREATED", uuid, id, name + " [" + tag + "]", key);
            return ng;
        });
        after.publish(bus);
        bus.publish(new GameEvents.GuildChanged(g.id(), "created"));
        return g;
    }

    public void invite(String inviter, String target) {
        Member m = need(inviter);
        DomainException.require(rank(m).atLeast(Rank.OFFICER), "guild.rank", "부길드장 이상만 초대할 수 있습니다");
        DomainException.require(repo.member(target).isEmpty(), "guild.target_has_guild", "상대는 이미 길드가 있습니다");
        Guild g = repo.find(m.guildId()).orElseThrow();
        DomainException.require(repo.members(g.id()).size() < GuildRules.maxMembers(g.level()), "guild.full", "길드 인원이 가득 찼습니다");
        invites.computeIfAbsent(target, k -> new HashMap<>()).put(g.id(), clock.nowMillis() + GuildRules.INVITE_TTL_MS);
    }

    public List<String> invitesFor(String uuid) {
        Map<String, Long> m = invites.getOrDefault(uuid, Map.of());
        long now = clock.nowMillis();
        return m.entrySet().stream().filter(e -> e.getValue() > now).map(Map.Entry::getKey).toList();
    }

    public Guild accept(String uuid, String guildId) {
        Long exp = invites.getOrDefault(uuid, Map.of()).get(guildId);
        DomainException.require(exp != null && exp > clock.nowMillis(), "guild.no_invite", "초대가 없거나 만료되었습니다");
        Guild g = tx.inTx(() -> {
            DomainException.require(repo.member(uuid).isEmpty(), "guild.already", "이미 길드에 속해 있습니다");
            Guild x = repo.find(guildId).orElseThrow(() -> DomainException.of("guild.gone", "길드가 사라졌습니다"));
            DomainException.require(repo.members(x.id()).size() < GuildRules.maxMembers(x.level()), "guild.full", "길드 인원이 가득 찼습니다");
            repo.addMember(new Member(uuid, x.id(), Rank.MEMBER.name(), 0, clock.nowMillis()));
            audit.record("GUILD_JOINED", uuid, x.id(), null, null);
            return x;
        });
        invites.remove(uuid);
        bus.publish(new GameEvents.GuildChanged(g.id(), "joined:" + uuid));
        return g;
    }

    public void leave(String uuid) {
        String gid = tx.inTx(() -> {
            Member m = need(uuid);
            DomainException.require(rank(m) != Rank.LEADER || repo.members(m.guildId()).size() == 1, "guild.leader_leave",
                    "길드장은 먼저 길드장을 넘기거나 길드를 해산해야 합니다");
            if (rank(m) == Rank.LEADER) disbandInTx(m.guildId(), uuid);
            else repo.removeMember(uuid);
            return m.guildId();
        });
        bus.publish(new GameEvents.GuildChanged(gid, "left:" + uuid));
    }

    public void kick(String actor, String target) {
        String gid = tx.inTx(() -> {
            Member a = need(actor), t = need(target);
            DomainException.require(a.guildId().equals(t.guildId()), "guild.not_same", "같은 길드원이 아닙니다");
            DomainException.require(rank(a).ordinal() < rank(t).ordinal(), "guild.rank", "자기보다 낮은 직급만 내보낼 수 있습니다");
            repo.removeMember(target);
            audit.record("GUILD_KICK", actor, target, a.guildId(), null);
            return a.guildId();
        });
        bus.publish(new GameEvents.GuildChanged(gid, "kicked:" + target));
    }

    public void setRank(String actor, String target, Rank r) {
        DomainException.require(r != Rank.LEADER, "guild.rank", "길드장은 넘기기로만 바꿀 수 있습니다");
        String gid = tx.inTx(() -> {
            Member a = need(actor), t = need(target);
            DomainException.require(a.guildId().equals(t.guildId()), "guild.not_same", "같은 길드원이 아닙니다");
            DomainException.require(rank(a) == Rank.LEADER && !actor.equals(target), "guild.rank", "길드장만 직급을 바꿀 수 있습니다");
            if (r == Rank.OFFICER) {
                Guild g = repo.find(a.guildId()).orElseThrow();
                long officers = repo.members(g.id()).stream().filter(m -> m.rank().equals("OFFICER")).count();
                DomainException.require(officers < GuildRules.maxOfficers(g.level()), "guild.officers_full", "부길드장 자리가 없습니다");
            }
            repo.setRank(target, r.name());
            return a.guildId();
        });
        bus.publish(new GameEvents.GuildChanged(gid, "rank:" + target));
    }

    public void transferLeader(String actor, String target) {
        String gid = tx.inTx(() -> {
            Member a = need(actor), t = need(target);
            DomainException.require(a.guildId().equals(t.guildId()) && rank(a) == Rank.LEADER && !actor.equals(target), "guild.rank", "길드장만 넘길 수 있습니다");
            repo.setRank(actor, Rank.OFFICER.name());
            repo.setRank(target, Rank.LEADER.name());
            Guild g = repo.find(a.guildId()).orElseThrow();
            repo.update(new Guild(g.id(), g.name(), g.tag(), target, g.level(), g.xp(), g.createdAt()));
            audit.record("GUILD_LEADER", actor, target, g.id(), null);
            return g.id();
        });
        bus.publish(new GameEvents.GuildChanged(gid, "leader:" + target));
    }

    /** 해산 — 금고에 남은 돈은 길드장에게 돌아간다 */
    public void disband(String actor) {
        String gid = tx.inTx(() -> {
            Member a = need(actor);
            DomainException.require(rank(a) == Rank.LEADER, "guild.rank", "길드장만 해산할 수 있습니다");
            disbandInTx(a.guildId(), actor);
            return a.guildId();
        });
        bus.publish(new GameEvents.GuildChanged(gid, "disbanded"));
    }

    private void disbandInTx(String guildId, String leader) {
        long left = economy.balance(wallet(guildId));
        AfterCommit ignore = new AfterCommit();
        if (left > 0) economy.transferInTx(wallet(guildId), leader, left, "guild_disband", "guild_disband:" + guildId, ignore);
        repo.delete(guildId);
        audit.record("GUILD_DISBANDED", leader, guildId, String.valueOf(left), null);
    }

    /** 금고에 넣기 — 넣은 돈의 1/10 이 공헌도 · 길드 경험치가 된다 */
    public long deposit(String uuid, long amount, String requestId) {
        AfterCommit after = new AfterCommit();
        long bal = tx.inTx(() -> {
            Member m = need(uuid);
            if (economy.transferInTx(uuid, wallet(m.guildId()), amount, "guild_deposit", "guild_dep:" + requestId, after)) {
                repo.addContribution(uuid, amount / 10);
                addXpInTx(m.guildId(), amount / 10, after);
            }
            return economy.balance(wallet(m.guildId()));
        });
        after.publish(bus);
        return bal;
    }

    /** 금고에서 빼기 — 길드장 · 부길드장만 */
    public long withdraw(String uuid, long amount, String requestId) {
        AfterCommit after = new AfterCommit();
        long bal = tx.inTx(() -> {
            Member m = need(uuid);
            DomainException.require(rank(m).atLeast(Rank.OFFICER), "guild.rank", "부길드장 이상만 금고에서 꺼낼 수 있습니다");
            economy.transferInTx(wallet(m.guildId()), uuid, amount, "guild_withdraw", "guild_wd:" + requestId, after);
            return economy.balance(wallet(m.guildId()));
        });
        after.publish(bus);
        return bal;
    }

    /** 함께 한 활동(던전 · 보스 · 퀘스트)으로 공헌 · 길드 경험치 */
    public void activity(String uuid, long points) {
        if (points <= 0) return;
        AfterCommit after = new AfterCommit();
        tx.inTx(() -> {
            repo.member(uuid).ifPresent(m -> {
                repo.addContribution(uuid, points);
                addXpInTx(m.guildId(), points, after);
            });
            return null;
        });
        after.publish(bus);
    }

    private void addXpInTx(String guildId, long xp, AfterCommit after) {
        if (xp <= 0) return;
        Guild g = repo.find(guildId).orElseThrow();
        long nx = g.xp() + xp;
        int lv = GuildRules.levelOf(nx);
        repo.update(new Guild(g.id(), g.name(), g.tag(), g.leader(), lv, nx, g.createdAt()));
        if (lv > g.level()) after.add(new GameEvents.GuildChanged(g.id(), "level:" + lv));
    }
}
