package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.application.ReputationService;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 명성 · 악명 · 살인자 (REP-01) · 파티 (PTY-01) 의 서버 쪽.
 * <ul>
 *   <li>사람이 사람을 죽이면 서버의 사망 이벤트로 판정해 악명 +100 · 살인자. 살인자는 이름이 붉게 보이고(스코어보드 팀), 살인자를 죽인 사람은 아무 페널티가 없다</li>
 *   <li>악명 · 살인자는 몬스터를 잡을 때마다 조금씩 씻긴다 (신전 기부는 /기부)</li>
 *   <li>같은 파티끼리는 서로 해칠 수 없다</li>
 * </ul>
 */
public final class ReputationListener implements Listener {
    private static final String TEAM = "versa_murderer";
    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final Map<UUID, ReputationService.Standing> cache = new ConcurrentHashMap<>();

    public ReputationListener(Plugin plugin, GameServices s, Async async) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        // 살인자 기간이 끝났는지 1분마다 다시 본다
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ReputationService.Standing st = cache.get(p.getUniqueId());
                if (st != null && st.murderer() && st.murdererUntil() <= System.currentTimeMillis()) refresh(p);
            }
        }, 1200L, 1200L);
    }

    public ReputationService.Standing standing(UUID u) {
        return cache.getOrDefault(u, new ReputationService.Standing(0, 0, 0, false));
    }

    public void refresh(Player p) {
        String id = p.getUniqueId().toString();
        async.run("standing", () -> s.reputation.standing(id), st -> {
            cache.put(p.getUniqueId(), st);
            if (p.isOnline()) paint(p, st.murderer());
        }, null);
    }

    private Team team() {
        Scoreboard b = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = b.getTeam(TEAM);
        if (t == null) {
            t = b.registerNewTeam(TEAM);
            t.setColor(ChatColor.RED);
            t.setPrefix(ChatColor.RED + "[살인자] ");
        }
        return t;
    }

    private void paint(Player p, boolean murderer) {
        Team t = team();
        if (murderer) t.addEntry(p.getName());
        else t.removeEntry(p.getName());
        p.setPlayerListName(murderer ? ChatColor.RED + p.getName() : p.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        refresh(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cache.remove(e.getPlayer().getUniqueId());
        s.parties.leave(e.getPlayer().getUniqueId().toString());
    }

    /** 같은 파티는 서로 해치지 않는다 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = e.getDamager() instanceof Player a ? a
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player a2 ? a2 : null;
        if (attacker != null && attacker != victim && s.parties.together(attacker.getUniqueId().toString(), victim.getUniqueId().toString()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity(), killer = victim.getKiller();
        if (killer == null || killer == victim) return;
        String k = killer.getUniqueId().toString(), v = victim.getUniqueId().toString();
        async.run("pk", () -> s.reputation.playerKilled(k, v), kill -> {
            if (kill.notorietyGain() > 0) {
                killer.sendMessage(Ui.error("사람을 죽였다 — 악명 +" + kill.notorietyGain() + " · 살인자 (붉은 이름). 몬스터 사냥 · 신전 기부로 씻을 수 있다"));
            } else killer.sendMessage(Ui.info("살인자를 처단했다 — 아무 페널티가 없다"));
            refresh(killer);
        }, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonsterDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Monster) || e.getEntity().getKiller() == null) return;
        Player p = e.getEntity().getKiller();
        ReputationService.Standing st = standing(p.getUniqueId());
        if (st.notoriety() <= 0 && !st.murderer()) return;
        String id = p.getUniqueId().toString();
        async.run("cleanse", () -> {
            s.reputation.monsterKilled(id);
            return s.reputation.standing(id);
        }, after -> {
            cache.put(p.getUniqueId(), after);
            if (st.murderer() != after.murderer() && p.isOnline()) {
                paint(p, after.murderer());
                p.sendMessage(Ui.info("살인자 상태가 풀렸다"));
            }
        }, null);
    }
}
