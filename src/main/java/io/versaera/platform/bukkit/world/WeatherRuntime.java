package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.domain.weather.WeatherKind;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Function;

/**
 * 날씨 보이기 (WTH-01): 플레이어마다 지금 지역의 날씨로 하늘(비 · 눈)을 바꾸고, 안개 · 모래폭풍 입자를 그 사람 주위에만 뿌린다.
 * 날씨가 바뀌면 효과를 한 줄로 알린다. 날씨 계산은 시드 · 시각뿐이라 DB 를 쓰지 않는다 (메인 스레드에서 바로).
 * 효과(채집 · 전투 · NPC 일과)는 서비스가 같은 값으로 계산한다.
 */
public final class WeatherRuntime implements Listener {
    private final GameServices s;
    private final Function<UUID, String> regionOf;
    private final Map<UUID, String> shown = new HashMap<>();

    public WeatherRuntime(Plugin plugin, GameServices s, Function<UUID, String> regionOf) {
        this.s = s;
        this.regionOf = regionOf;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, 40L);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String region = regionOf.apply(p.getUniqueId());
            if (region == null) continue;
            WeatherKind k = s.weather.at(region);
            String before = shown.put(p.getUniqueId(), k.id());
            if (!k.id().equals(before)) {
                p.setPlayerWeather(k.downfall() ? WeatherType.DOWNFALL : WeatherType.CLEAR);
                if (before != null) Ui.bar(p, "&b" + k.name());
            }
            if (k.particle() != null) {
                try {
                    Particle pt = Particle.valueOf(k.particle());
                    Location l = p.getLocation().add(0, 1.2, 0);
                    p.spawnParticle(pt, l, 40, 6, 2.5, 6, 0.01);
                } catch (IllegalArgumentException ignored) {
                    // 이 서버 버전에 없는 입자
                }
            }
        }
    }

    /** /날씨: 지금 · 다음 날씨와 효과 */
    public List<String> report(Player p) {
        String region = regionOf.apply(p.getUniqueId());
        if (region == null) return List.of("&7지역 밖입니다");
        WeatherKind k = s.weather.at(region);
        var next = s.weather.next(region);
        List<String> out = new ArrayList<>();
        out.add("&b" + s.regions.byId(region).name() + " &f" + k.name() + " &7— " + k.desc());
        List<String> fx = new ArrayList<>();
        k.gather().forEach((d, m) -> fx.add(s.growth.discipline(d).name() + (m >= 1 ? " &a+" : " &c") + Math.round((m - 1) * 100) + "%&7"));
        if (k.melee() != 1) fx.add("근접 " + pct(k.melee()));
        if (k.ranged() != 1) fx.add("활 " + pct(k.ranged()));
        if (k.spell() != 1) fx.add("마법 " + pct(k.spell()));
        if (k.indoor()) fx.add("&e사람들이 집에 머문다 · 배가 뜨지 않는다&7");
        if (!fx.isEmpty()) out.add("&7" + String.join(" · ", fx));
        long min = Math.max(1, (next.getValue() - System.currentTimeMillis()) / 60_000);
        out.add("&8예보: " + min + "분 뒤 " + next.getKey().name());
        return out;
    }

    private static String pct(double m) {
        long v = Math.round((m - 1) * 100);
        return (v >= 0 ? "&a+" : "&c") + v + "%&7";
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        shown.remove(e.getPlayer().getUniqueId());
    }
}
