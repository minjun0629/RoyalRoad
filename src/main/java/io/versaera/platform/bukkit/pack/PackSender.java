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

    public PackSender(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 새 팩 주소. 이미 접속한 사람에게도 바로 보낸다 */
    public void set(String url, byte[] sha1) {
        this.url = url;
        this.sha1 = sha1;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) p.setResourcePack(url, sha1);
        });
    }

    public String url() {
        return url;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String u = url;
            if (p.isOnline() && u != null) p.setResourcePack(u, sha1);
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED || e.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD)
            e.getPlayer().sendMessage(Ui.error("리소스팩이 없으면 보스 모델 · 메뉴 배경이 기본 그림으로 보입니다"));
    }
}
