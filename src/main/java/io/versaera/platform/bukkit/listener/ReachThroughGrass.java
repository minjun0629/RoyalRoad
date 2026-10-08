package io.versaera.platform.bukkit.listener;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 평타가 풀에 막히지 않게: 좌클릭이 풀 · 꽃 · 덩굴처럼 지나갈 수 있는 블록에 걸렸는데 그 너머(손이 닿는 거리)에 몬스터가 있으면,
 * 풀을 부수지 않고 그 몬스터를 친다. 바닐라는 십자선에 먼저 닿은 풀을 쳐서, 들판에서 싸우면 자꾸 잔디만 베였다.
 */
public final class ReachThroughGrass implements Listener {
    private static final double REACH = 3.2;
    /** 방금 풀 너머를 친 사람 → 그 시각 (같은 틱에 오는 풀 부수기를 막는다) */
    private final Map<UUID, Long> swung = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwing(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || !e.getClickedBlock().isPassable()) return;
        LivingEntity target = behind(p);
        if (target == null) return;
        e.setCancelled(true);
        swung.put(p.getUniqueId(), System.currentTimeMillis());
        p.attack(target);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Long t = swung.get(e.getPlayer().getUniqueId());
        if (t != null && System.currentTimeMillis() - t < 150 && e.getBlock().isPassable()) e.setCancelled(true);
    }

    /** 눈에서 바라보는 쪽으로 손이 닿는 거리 안의 살아 있는 상대 (단단한 블록에 가리면 없음 · 지나갈 수 있는 블록은 무시) */
    private static LivingEntity behind(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = p.getWorld().rayTraceEntities(eye, dir, REACH, 0.2,
                x -> x instanceof LivingEntity && !(x instanceof ArmorStand) && x != p && !x.isDead());
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity le)) return null;
        double dist = hit.getHitPosition().distance(eye.toVector());
        RayTraceResult wall = p.getWorld().rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true);
        return wall != null && wall.getHitBlock() != null ? null : le;
    }
}
