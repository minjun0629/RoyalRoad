package io.versaera.platform.bukkit.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.logging.Logger;

/** 서버 목록 아이콘: jar 안의 server-icon.png (64 × 64). 서버 폴더에 따로 둔 server-icon.png 가 있으면 그쪽을 쓴다 */
public final class ServerIcon implements Listener {
    private CachedServerIcon icon;

    public ServerIcon(Logger log) {
        if (new java.io.File("server-icon.png").exists()) return;   // 관리자가 고른 아이콘이 먼저
        try (InputStream in = ServerIcon.class.getClassLoader().getResourceAsStream("server-icon.png")) {
            if (in == null) return;
            BufferedImage img = ImageIO.read(in);
            icon = Bukkit.loadServerIcon(img);
        } catch (Exception e) {
            log.warning("서버 아이콘을 읽지 못했습니다: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        if (icon != null) e.setServerIcon(icon);
    }
}
