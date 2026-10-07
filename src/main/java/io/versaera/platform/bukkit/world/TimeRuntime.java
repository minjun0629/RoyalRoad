package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.domain.time.GameTime;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * 게임 시간 (TIME-01, CANON: 로열 로드의 시간은 현실보다 4배 빠르다). 세계 시계를 멈추고 1초마다 현실 시각 × 비율로 맞춘다.
 * 현실 하루 = 게임 4일 = 낮밤 네 번 (한 낮밤이 현실 6시간). 비율 0 이면 마인크래프트 기본 낮밤을 그대로 둔다.
 */
public final class TimeRuntime {
    private final GameServices s;
    private volatile int hour = 12;

    public TimeRuntime(Plugin plugin, GameServices s) {
        this.s = s;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 20L);
    }

    public int hour() {
        return hour;
    }

    private void tick() {
        GameTime t = s.rules().time();
        long now = System.currentTimeMillis();
        if (!t.enabled()) {
            World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (w != null) hour = (int) ((w.getTime() / 1000 + 6) % 24);
            return;
        }
        hour = t.hour(now);
        long mc = t.minecraftTime(now);
        for (World w : Bukkit.getWorlds()) {
            if (!Boolean.FALSE.equals(w.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE))) w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setTime(mc);
        }
    }
}
