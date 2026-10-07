package io.versaera.platform.bukkit.pack;

import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

/**
 * 접속한 사람에게 리소스팩 주소 + SHA-1 을 보낸다. SHA-1 이 같으면 클라이언트는 다시 받지 않는다.
 * 주소 · SHA-1 은 준비되는 대로 set 으로 바뀐다 (외부 주소 확인은 시간이 걸리므로).
 */
public final class PackSender implements Listener {
    private final Plugin plugin;
    private volatile String url;
    private volatile byte[] sha1;
    private volatile String prompt = "";
    private volatile boolean required;

    public PackSender(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 받기 창 문구 · 필수 여부 (필수면 거절한 사람은 접속이 끊긴다 — 클라이언트 규칙) */
    public void prompt(String prompt, boolean required) {
        this.prompt = prompt == null ? "" : prompt;
        this.required = required;
    }

    private void send(Player p) {
        String u = url;
        if (u == null) return;
        if (prompt.isEmpty() && !required) p.setResourcePack(u, sha1);
        else p.setResourcePack(u, sha1, Ui.c(prompt), required);
    }

    /** 새 팩 주소. 이미 접속한 사람에게도 바로 보낸다 */
    public void set(String url, byte[] sha1) {
        this.url = url;
        this.sha1 = sha1;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) send(p);
        });
    }

    public String url() {
        return url;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) send(p);
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            e.getPlayer().sendMessage(Ui.error("리소스팩을 받지 못했습니다"));
            plugin.getLogger().warning(e.getPlayer().getName() + " 리소스팩 받기 실패: " + url);
        }
    }
}
