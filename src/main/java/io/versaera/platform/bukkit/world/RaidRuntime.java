package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.application.RaidService;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.boss.BossRuntime;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Function;

/**
 * 레이드 (RAID-01 · RAID-02): 공격대(여러 파티)가 레이드 지역 가운데에 거대 보스를 불러 도전한다.
 * 시작 조건(인원 · 지역 · 숙련 · 귀속)은 서비스가, 보스 전투는 BossRuntime 이 맡는다. 5초마다 시간 초과를 확인해 보스를 물린다.
 */
public final class RaidRuntime {
    private final GameServices s;
    private final Async async;
    private final BossRuntime bosses;
    private final Function<UUID, String> regionOf;
    private final Map<String, UUID> hitboxOf = new HashMap<>();   // runId → 보스 판정 상자

    public RaidRuntime(Plugin plugin, GameServices s, Async async, BossRuntime bosses, Function<UUID, String> regionOf) {
        this.s = s;
        this.async = async;
        this.bosses = bosses;
        this.regionOf = regionOf;
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 100L, 100L);
    }

    /** 공격대장이 시작 (공격대 = 묶인 파티 전원, 접속한 사람만) */
    public void start(Player leader, String raidId) {
        String lid = leader.getUniqueId().toString();
        if (!s.parties.raidHead(lid).equals(lid)) {
            leader.sendMessage(Ui.error("공격대장만 시작할 수 있습니다"));
            return;
        }
        List<String> members = new ArrayList<>();
        Map<String, String> where = new HashMap<>(), names = new HashMap<>();
        for (String m : s.parties.raidMembers(lid)) {
            Player o = Bukkit.getPlayer(UUID.fromString(m));
            if (o == null) continue;
            members.add(m);
            names.put(m, o.getName());
            String r = regionOf.apply(o.getUniqueId());
            if (r != null) where.put(m, r);
        }
        async.run("raid-start", () -> s.raids.start(raidId, lid, members, where, names), run -> spawn(leader, run), leader);
    }

    private void spawn(Player leader, RaidService.Run run) {
        Region r = s.regions.byId(run.def().region());
        World w = Bukkit.getWorld(r.world());
        if (w == null) {
            async.fire("raid-fail", () -> { s.raids.failed(run.runId()); return null; });
            leader.sendMessage(Ui.error("레이드 세계가 없습니다"));
            return;
        }
        int x = (r.minX() + r.maxX()) / 2, z = (r.minZ() + r.maxZ()) / 2;
        Location at = new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
        tell(run, "&5레이드 &f" + run.def().name() + " &7— " + run.def().desc() + " &8(" + run.def().timeLimitMs() / 60_000 + "분)");
        bosses.spawn(run.def().boss(), at, leader, hitbox -> hitboxOf.put(run.runId(), hitbox), () -> {
            hitboxOf.remove(run.runId());
            async.run("raid-clear", () -> s.raids.cleared(run.runId()), best -> {
                long sec = (System.currentTimeMillis() - run.startedAt()) / 1000;
                Bukkit.broadcastMessage(Ui.info("레이드 &f" + run.def().name() + " &a공략 &7(" + sec / 60 + "분 " + sec % 60 + "초" + (best ? " · &6서버 최고 기록" : "") + "&7)"));
            }, null);
        });
    }

    private void expire() {
        async.run("raid-expire", () -> s.raids.expired(), runs -> {
            for (RaidService.Run run : runs) {
                UUID hb = hitboxOf.remove(run.runId());
                if (hb != null) bosses.stop(hb);
                tell(run, "&c레이드 실패 &7— 시간이 다 되어 " + run.def().name() + " 의 주인이 물러났다");
            }
        }, null);
    }

    private static void tell(RaidService.Run run, String msg) {
        for (String m : run.members()) {
            Player o = Bukkit.getPlayer(UUID.fromString(m));
            if (o != null) o.sendMessage(Ui.c(msg));
        }
    }
}
