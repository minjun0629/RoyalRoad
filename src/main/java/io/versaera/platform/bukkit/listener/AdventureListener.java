package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.achievement.Title;
import io.versaera.domain.event.GameEvents;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * 업적 · 칭호 (ACH-01 · ACH-02) 의 플레이어 쪽: 얻으면 제목 · 소리 · 서버 최초면 전체 알림, 단 칭호는 이름 앞(목록 · 채팅)에.
 * 접속할 때와 10분마다 모든 업적을 다시 확인한다 (명성처럼 행동 기록 밖에서 오르는 값).
 * 도메인 이벤트는 DB 스레드에서 오므로 메인 스레드로 넘겨 그린다.
 */
public final class AdventureListener implements Listener {
    private final Plugin plugin;
    private final GameServices s;
    private final Async async;

    public AdventureListener(Plugin plugin, GameServices s, Async async) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        s.bus.subscribe(GameEvents.AchievementEarned.class, e -> Bukkit.getScheduler().runTask(plugin, () -> earned(e)));
        s.bus.subscribe(GameEvents.TitleChanged.class, e -> refreshTitle(e.uuid()));
        s.bus.subscribe(GameEvents.GuildQuestDone.class, e -> Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers())
                async.run("gq-done", () -> s.guilds.membership(p.getUniqueId().toString()).map(m -> m.guildId().equals(e.guildId())).orElse(false),
                        mine -> { if (mine) p.sendMessage(Ui.info("길드 의뢰 완료: &f" + e.name() + " &7(금고 +" + e.money() + ")")); }, null);
        }));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String id = p.getUniqueId().toString();
                async.fire("ach-check", () -> s.achievements.checkAll(id));
            }
        }, 20L * 600, 20L * 600);
    }

    private void earned(GameEvents.AchievementEarned e) {
        Player p = Bukkit.getPlayer(UUID.fromString(e.uuid()));
        if (p == null) return;
        p.sendTitle(Ui.c("&6업적"), Ui.c("&f" + e.name()), 5, 50, 15);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        if (e.title() != null) p.sendMessage(Ui.info("새 칭호: " + s.achievements.title(e.title()).color() + s.achievements.title(e.title()).name()));
        if (e.worldFirst()) Bukkit.broadcastMessage(Ui.info(p.getName() + " 님이 서버에서 처음으로 업적 「" + e.name() + "」 달성"));
    }

    /** DB 스레드에서 칭호를 읽어 메인 스레드에서 이름에 붙인다 */
    private void refreshTitle(String uuid) {
        async.run("title-load", () -> s.achievements.equipped(uuid).orElse(null), t -> {
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));
            if (p != null) apply(p, t);
        }, null);
    }

    private static void apply(Player p, Title t) {
        String name = t == null ? p.getName() : Ui.c(t.color() + "[" + t.name() + "] &f") + p.getName();
        p.setPlayerListName(name);
        p.setDisplayName(name);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        String id = e.getPlayer().getUniqueId().toString();
        refreshTitle(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> async.fire("ach-join", () -> s.achievements.checkAll(id)), 60L);
    }
}
