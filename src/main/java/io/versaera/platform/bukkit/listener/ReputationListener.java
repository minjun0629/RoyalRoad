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
 *   <li>몬스터는 살인자를 끝까지 쫓는다 (먼저 노리고, 놓치지 않음)</li>
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
        // 몬스터는 살인자를 끝까지 쫓는다 (원작): 2초마다 48 블록 안 몬스터가 살인자를 노리고, 추적 거리를 늘린다
        Bukkit.getScheduler().runTaskTimer(plugin, this::hunt, 40L, 40L);
    }

    private void hunt() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!standing(p.getUniqueId()).murderer() || p.isDead()) continue;
            for (org.bukkit.entity.Entity e : p.getNearbyEntities(48, 24, 48)) {
                if (!(e instanceof Monster m)) continue;
                var range = m.getAttribute(org.bukkit.attribute.Attribute.GENERIC_FOLLOW_RANGE);
                if (range != null && range.getBaseValue() < 64) range.setBaseValue(64);
                if (m.getTarget() == null || !(m.getTarget() instanceof Player)) m.setTarget(p);
            }
        }
    }

    /** 살인자를 노리던 몬스터는 놓치지 않는다 (잊기 · 거리 초과로 표적을 버리지 않음) */
    @EventHandler(ignoreCancelled = true)
    public void onLoseTarget(org.bukkit.event.entity.EntityTargetEvent e) {
        if (!(e.getEntity() instanceof Monster m) || !(m.getTarget() instanceof Player cur)) return;
        if (e.getTarget() == null && standing(cur.getUniqueId()).murderer() && !cur.isDead() && cur.getWorld() == m.getWorld()
                && cur.getLocation().distanceSquared(m.getLocation()) < 96 * 96)
            e.setCancelled(true);
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
        org.bukkit.Location at = victim.getLocation();
        io.versaera.domain.world.Region where = s.regions.at(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
        String region = where == null ? null : where.id();
        async.run("pk", () -> {
            // 공성 중인 두 길드가 그 성 안에서 싸운 것은 전쟁 — 악명 없음 (CST-01)
            String kg = s.guilds.guildOf(k).map(g -> g.id()).orElse(null), vg = s.guilds.guildOf(v).map(g -> g.id()).orElse(null);
            if (s.realm.atWar(kg, vg, region)) return new io.versaera.domain.reputation.Reputation.Kill(0, -1);
            return s.reputation.playerKilled(k, v);
        }, kill -> {
            if (kill.murderMs() < 0) {
                killer.sendMessage(Ui.info("공성전의 적을 쓰러뜨렸다"));
                return;
            }
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
