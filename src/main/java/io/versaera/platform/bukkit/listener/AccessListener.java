package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.persistence.DbExecutor;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 캡슐 · 이용료 · 연령 제한 (ACC-01). 접속 단계에서 캡슐 등록 · 이용료를 확인하고, 미성년 표시된 사람은 싸우지 못하게 한다.
 */
public final class AccessListener implements Listener {
    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final DbExecutor exec;
    private final Set<UUID> minors = ConcurrentHashMap.newKeySet();

    public AccessListener(Plugin plugin, GameServices s, Async async, DbExecutor exec) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.exec = exec;
    }

    public boolean minor(UUID u) {
        return minors.contains(u);
    }

    public void setMinor(UUID u, boolean on) {
        if (on) minors.add(u);
        else minors.remove(u);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String id = e.getUniqueId().toString();
        try {
            String why = exec.submit("access", () -> s.access.admit(id)).join();
            if (why != null) e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Ui.c("&c" + why));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("접속 확인 실패 (들여보냄): " + ex.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String id = p.getUniqueId().toString();
        async.run("minor", () -> new Object[]{s.access.minor(id), s.access.paidUntil(id)}, r -> {
            setMinor(p.getUniqueId(), (Boolean) r[0]);
            if ((Boolean) r[0]) p.sendMessage(Ui.info("미성년 보호: 사냥 · 전투 · 던전 · 다른 차원 · 시련은 할 수 없습니다"));
            long paid = (Long) r[1];
            if (s.access.rules().subscriptionFee() > 0 && paid > 0)
                p.sendMessage(Ui.c("&7이용 기간: " + Math.max(0, (paid - System.currentTimeMillis()) / 86_400_000L) + "일 남음 (다음 결제 "
                        + s.access.rules().subscriptionFee() + " 골드, 자동)"));
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        minors.remove(e.getPlayer().getUniqueId());
    }

    /** 미성년은 공격할 수 없고, 다른 사람에게 공격받지도 않는다 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player attacker = e.getDamager() instanceof Player a ? a
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player a2 ? a2 : null;
        if (attacker != null && minors.contains(attacker.getUniqueId())) {
            e.setCancelled(true);
            Ui.bar(attacker, "&7미성년은 사냥 · 전투를 할 수 없습니다");
            return;
        }
        if (attacker != null && e.getEntity() instanceof Player v && minors.contains(v.getUniqueId())) e.setCancelled(true);
    }
}
