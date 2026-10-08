package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 배고픔 (원작 「로열 로드」: 시간이 지나면 배가 고프고, 굶으면 힘이 빠진다).
 * <ul>
 *   <li>가만히 있어도 줄어든다: 배부른 상태(20)가 게임 시간 hours_per_meal 시간이면 바닥난다 — 포만감(saturation)이 먼저, 그다음 배고픔 칸</li>
 *   <li>움직이고 싸우고 회복하며 쓰는 바닐라 소모(exhaustion)는 activity_scale 배만 (원작은 굶주림이 하루 단위)</li>
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

    /**
     * 움직이고 · 싸우고 · 체력이 차오를 때의 바닐라 소모(exhaustion)를 activity_scale 배로.
     * 예전에는 배고픔 칸이 줄 때만 일부를 막았는데, 포만감은 그 전에 바닐라 속도로 다 타 버려 배고픔이 쭉쭉 닳았다.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExhaust(org.bukkit.event.entity.EntityExhaustionEvent e) {
        e.setExhaustion((float) (e.getExhaustion() * activityScale));
    }
}
