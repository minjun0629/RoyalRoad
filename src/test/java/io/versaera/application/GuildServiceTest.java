package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.guild.GuildRules;
import io.versaera.domain.guild.GuildRules.Rank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuildServiceTest {
    private static String rich(TestWorld w) {
        String p = TestWorld.player();
        w.s.economy.deposit(p, 20_000 * io.versaera.domain.economy.Money.SILVER, "test", "seed:" + p);
        return p;
    }

    @Test
    void createCostsMoneyAndNamesAreUnique() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = rich(w), b = rich(w), poor = TestWorld.player();
            assertEquals("money.insufficient", assertThrows(DomainException.class, () -> w.s.guilds.create(poor, "가난한 길드", "PO", "r0")).code());
            var g = w.s.guilds.create(a, "새벽 원정대", "DAWN", "r1");
            assertEquals(20_000 * io.versaera.domain.economy.Money.SILVER - GuildRules.CREATE_COST, w.s.economy.balance(a));
            assertEquals("guild.taken", assertThrows(DomainException.class, () -> w.s.guilds.create(b, "새벽 원정대", "XX", "r2")).code());
            assertEquals("guild.taken", assertThrows(DomainException.class, () -> w.s.guilds.create(b, "다른 이름", "dawn".toUpperCase(), "r3")).code());
            assertEquals("guild.bad_name", assertThrows(DomainException.class, () -> w.s.guilds.create(b, "<b>x</b>", "AB", "r4")).code());
            assertEquals("LEADER", w.s.guilds.membership(a).orElseThrow().rank());
            assertEquals(g.id(), w.s.guilds.guildOf(a).orElseThrow().id());
        }
    }

    @Test
    void invitesRanksAndKicks() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String lead = rich(w), b = TestWorld.player(), c = TestWorld.player();
            var g = w.s.guilds.create(lead, "망치 동맹", "HAM", "r1");
            assertEquals("guild.no_invite", assertThrows(DomainException.class, () -> w.s.guilds.accept(b, g.id())).code());
            w.s.guilds.invite(lead, b);
            w.s.guilds.accept(b, g.id());
            assertEquals("guild.rank", assertThrows(DomainException.class, () -> w.s.guilds.invite(b, c)).code(), "일반 길드원은 초대 불가");
            w.s.guilds.setRank(lead, b, Rank.OFFICER);
            w.s.guilds.invite(b, c);
            w.now.addAndGet(GuildRules.INVITE_TTL_MS + 1);
            assertThrows(DomainException.class, () -> w.s.guilds.accept(c, g.id()), "초대는 만료된다");
            w.s.guilds.invite(b, c);
            w.s.guilds.accept(c, g.id());
            assertEquals("guild.rank", assertThrows(DomainException.class, () -> w.s.guilds.kick(c, b)).code());
            w.s.guilds.kick(b, c);
            assertTrue(w.s.guilds.membership(c).isEmpty());
            assertEquals("guild.leader_leave", assertThrows(DomainException.class, () -> w.s.guilds.leave(lead)).code());
            w.s.guilds.transferLeader(lead, b);
            w.s.guilds.leave(lead);
            assertEquals(b, w.s.guilds.find(g.id()).orElseThrow().leader());
        }
    }

    @Test
    void treasuryMovesAreLedgeredAndIdempotent() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String lead = rich(w), m = rich(w);
            var g = w.s.guilds.create(lead, "바다 상단", "SEA", "r1");
            w.s.guilds.invite(lead, m);
            w.s.guilds.accept(m, g.id());
            w.s.guilds.deposit(m, 3000, "d1");
            w.s.guilds.deposit(m, 3000, "d1");
            assertEquals(3000, w.s.guilds.treasury(g.id()), "같은 요청은 한 번만");
            assertEquals(20_000 * io.versaera.domain.economy.Money.SILVER - 3000, w.s.economy.balance(m));
            assertEquals(300, w.s.guilds.membership(m).orElseThrow().contribution());
            assertEquals("guild.rank", assertThrows(DomainException.class, () -> w.s.guilds.withdraw(m, 100, "w1")).code());
            assertEquals("money.insufficient", assertThrows(DomainException.class, () -> w.s.guilds.withdraw(lead, 5000, "w2")).code());
            w.s.guilds.withdraw(lead, 1000, "w3");
            assertEquals(2000, w.s.guilds.treasury(g.id()));
            long before = w.s.economy.balance(lead);
            w.s.guilds.kick(lead, m);
            w.s.guilds.disband(lead);
            assertEquals(before + 2000, w.s.economy.balance(lead), "해산하면 금고가 길드장에게");
            assertTrue(w.s.guilds.find(g.id()).isEmpty());
        }
    }

    @Test
    void levelRaisesMemberCap() {
        assertEquals(1, GuildRules.levelOf(999));
        assertEquals(2, GuildRules.levelOf(1000));
        assertTrue(GuildRules.maxMembers(5) > GuildRules.maxMembers(1));
        assertEquals(GuildRules.MAX_LEVEL, GuildRules.levelOf(Long.MAX_VALUE / 4));
    }
}
