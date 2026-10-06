package io.versaera.platform.bukkit.pack;

import com.sun.net.httpserver.HttpServer;
import io.versaera.pack.ResourcePackBuilder;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * 리소스팩 배포 (RP-01). 서버를 켤 때 ResourcePackBuilder 로 팩을 만들고, 내장 HTTP 서버로 내려 준다.
 * 접속하면 SHA-1 과 함께 보내므로 클라이언트는 바뀐 경우에만 다시 받는다.
 * 설정: pack.enabled · pack.port · pack.public-url (비우면 http://&lt;server-ip&gt;:&lt;port&gt;/versaera.zip)
 */
public final class PackServer implements Listener {
    private final Plugin plugin;
    private final ResourcePackBuilder.Pack pack;
    private final String url;
    private final HttpServer http;

    public PackServer(Plugin plugin, ResourcePackBuilder.Pack pack, int port, String publicUrl, String serverIp) throws IOException {
        this.plugin = plugin;
        this.pack = pack;
        this.http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/versaera.zip", ex -> {
            try (ex) {
                if (!"GET".equals(ex.getRequestMethod())) {
                    ex.sendResponseHeaders(405, -1);
                    return;
                }
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, pack.zip().length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(pack.zip());
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.FINE, "리소스팩 전송 중단", e);
            }
        });
        http.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "versa-pack");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        String host = serverIp == null || serverIp.isBlank() ? "localhost" : serverIp;
        this.url = publicUrl == null || publicUrl.isBlank() ? "http://" + host + ":" + port + "/versaera.zip" : publicUrl;
        plugin.getLogger().info("리소스팩 " + pack.zip().length / 1024 + "KB · sha1 " + pack.sha1Hex() + " · " + url);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.setResourcePack(url, pack.sha1());
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED || e.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD)
            e.getPlayer().sendMessage(Ui.error("리소스팩이 없으면 보스 모델 · 메뉴 배경이 기본 그림으로 보입니다"));
    }

    public void stop() {
        http.stop(0);
    }
}
