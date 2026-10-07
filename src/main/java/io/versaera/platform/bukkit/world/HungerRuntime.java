package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 배고픔 (원작 「로열 로드」: 시간이 지나면 배가 고프고, 굶으면 힘이 빠진다).
 * <ul>
 *   <li>가만히 있어도 줄어든다: 배부른 상태(20)가 게임 시간 hours_per_meal 시간이면 바닥난다 — 포만감(saturation)이 먼저, 그다음 배고픔 칸</li>
 *   <li>움직여서 줄어드는 바닐라 소모는 activity_scale 만큼만 (원작은 굶주림이 하루 단위)</li>
 *   <li>3칸 이하 = 허기 (힘 · 채굴 속도 저하), 0 = 기진 (걸음도 느려짐) · 바닐라 굶주림 피해</li>
 * </ul>
 * 게임 시간은 config time.ratio 를 따른다 (4 = 현실 하루에 게임 나흘 → 게임 1시간 = 현실 15분).
 */
public final class HungerRuntime implements Listener {
    private final double activityScale;
    private final long drainEveryMs;
    private long nextDrain;

    public HungerRuntime(Plugin plugin, GameServices s, double hoursPerMeal, double activityScale) {
        this.activityScale = Math.max(0, Math.min(1, activityScale));
        int ratio = s.rules().time().ratio();
        long gameHourMs = ratio > 0 ? 86_400_000L / ratio / 24 : 50_000L;   // 0 = 마인크래프트 기본 낮밤 (게임 1시간 = 50초)
        this.drainEveryMs = Math.max(5_000L, Math.round(Math.max(0.5, hoursPerMeal) * gameHourMs / 20.0));
        this.nextDrain = System.currentTimeMillis() + drainEveryMs;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean drain = now >= nextDrain;
        if (drain) nextDrain = now + drainEveryMs;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            if (drain) {
                if (p.getSaturation() >= 1) p.setSaturation(p.getSaturation() - 1);
                else if (p.getFoodLevel() > 0) p.setFoodLevel(p.getFoodLevel() - 1);
            }
            int food = p.getFoodLevel();
            if (food <= 3) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 140, 0, true, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 140, 0, true, false, true));
            }
            if (food == 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 140, 0, true, false, true));
        }
    }

    /** 움직여서 줄어드는 바닐라 소모를 줄인다 (먹어서 오르는 것은 그대로) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getFoodLevel() >= p.getFoodLevel() || e.getItem() != null) return;
        if (ThreadLocalRandom.current().nextDouble() >= activityScale) e.setCancelled(true);
    }
}
