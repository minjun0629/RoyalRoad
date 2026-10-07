package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지역 판정. 블록을 옮겼을 때만 청크 격자 인덱스를 한 번 본다 (매 틱 검사 없음).
 * 지역이 바뀌면 이름 · 위험도를 잠깐 보여 주고, 처음이면 발견 기록 (최초 발견자는 서버에 알림).
 */
public final class RegionTracker implements Listener {
    private final GameServices s;
    private final Async async;
    private final Map<UUID, String> current = new ConcurrentHashMap<>();

    public RegionTracker(GameServices s, Async async) {
        this.s = s;
        this.async = async;
    }

    /** 드러나야만 들어갈 수 있는 지역 (월드 이벤트 reveal) — 메인 스레드에서 판정하는 순수 계산 */
    private java.util.function.Predicate<String> blocked = id -> false;

    /** 사람마다 막는 지역 (초보 기간의 성문) — 막으면 이유, 아니면 null. 메인 스레드 · 캐시만 본다 */
    /** 못 나가게 막을 이유 (없으면 null) — 지역 id 와 가려는 자리 */
    public interface Confine {
        String check(Player p, String regionId, Location to);
    }

    private Confine confine = (p, id, to) -> null;

    public void confine(Confine f) {
        confine = f;
    }

    /** 관리자는 크리에이티브 · 관전 모드일 때만 막힘을 무시한다 (서바이벌로 시험할 때는 똑같이 막힌다) */
    private static boolean bypass(Player p) {
        return p.hasPermission("versaera.admin") && (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR);
    }

    public void gate(java.util.function.Predicate<String> blocked) {
        this.blocked = blocked;
    }

    public String regionOf(UUID player) {
        return current.get(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null || (e.getFrom().getBlockX() == to.getBlockX() && e.getFrom().getBlockY() == to.getBlockY() && e.getFrom().getBlockZ() == to.getBlockZ()))
            return;
        Player p = e.getPlayer();
        Region r = s.regions.at(to.getWorld().getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        String now = r == null ? null : r.id(), before = current.get(p.getUniqueId());
        // 초보 기간: 지역이 바뀌지 않아도 (도시 지역 안의 성벽 밖 들판) 매 블록 확인한다
        String held = bypass(p) ? null : confine.check(p, now, to);
        if (held != null) {
            e.setTo(e.getFrom());
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Ui.c("&7" + held)));
            return;
        }
        if (java.util.Objects.equals(now, before)) return;
        if (now != null && !bypass(p) && blocked.test(now)) {
            e.setTo(e.getFrom());
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Ui.c("&7아직 길이 드러나지 않았다")));
            return;
        }
        if (now == null) current.remove(p.getUniqueId());
        else current.put(p.getUniqueId(), now);
        if (r == null) return;
        s.gates.at(now).ifPresent(g -> cross(p, g));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Ui.c("&f" + r.name() + "  " + Ui.danger(r.danger()))));
        String id = p.getUniqueId().toString(), name = p.getName();
        async.run("enter-region", () -> {
            var d = s.exploration.enterRegion(id, name, r);
            if (s.hidden() != null) s.hidden().checkAll(id);
            return d;
        }, d -> {
            if (!d.isNew()) return;
            p.sendTitle(Ui.c("&6" + r.name()), Ui.c("&7새로운 지역 · " + Ui.danger(r.danger())), 10, 50, 15);
            if (d.worldFirst()) {
                org.bukkit.Bukkit.broadcastMessage(Ui.info(name + " 님이 「" + r.name() + "」을(를) 처음 발견했습니다"));
                async.fire("fame", () -> s.reputation.addFame(id, 20 + r.danger() * 10));   // 최초 발견은 명성 (REP-01)
            }
        }, null);
    }

    /** 문 (WLD-03): 탐험 숙련은 DB 스레드에서 읽고, 이동은 메인 스레드에서 */
    private void cross(Player p, io.versaera.domain.world.Gate g) {
        String id = p.getUniqueId().toString();
        async.run("gate", () -> {
            return s.gates.check(id, g);
        }, d -> {
            if (!p.isOnline()) return;
            if (!d.allowed() && !p.hasPermission("versaera.admin")) {
                p.sendMessage(Ui.c("&7" + d.reason()));
                return;
            }
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(g.toWorld());
            if (w == null) {
                p.sendMessage(Ui.c("&7「" + g.name() + "」 너머의 세계가 아직 열리지 않았다 (서버 설정 realms.enabled)"));
                return;
            }
            int y = w.getHighestBlockYAt(g.toX(), g.toZ()) + 1;
            p.teleport(new Location(w, g.toX() + 0.5, y, g.toZ() + 0.5));
            p.sendTitle(Ui.c("&5" + g.name()), Ui.c("&7다른 땅으로 건너왔다"), 10, 50, 15);
        }, p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        current.remove(e.getPlayer().getUniqueId());
    }
}
