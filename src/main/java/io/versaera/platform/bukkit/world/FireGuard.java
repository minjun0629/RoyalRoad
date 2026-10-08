package io.versaera.platform.bukkit.world;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;

/**
 * 불이 세계를 태우지 않게: 용암 · 번개 · 번지는 불로 붙는 불은 막고, 불이 블록을 태워 없애는 것도 막는다.
 * 지형 생성기의 용암 웅덩이가 도시 · 마을 근처에 생기면 나무 집이 통째로 타 버렸다.
 * 사람이 부싯돌 · 화염구로 직접 붙인 불은 그대로 (캠프파이어 · 전투), 다만 번지지는 않는다.
 */
public final class FireGuard implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        switch (e.getCause()) {
            case LAVA, SPREAD, LIGHTNING -> e.setCancelled(true);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        e.setCancelled(true);
    }
}
