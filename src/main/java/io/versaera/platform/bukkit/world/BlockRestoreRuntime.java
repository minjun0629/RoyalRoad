package io.versaera.platform.bukkit.world;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Predicate;

/**
 * 세계 복구 (WLD-04). 도시 · 들판 · 유적은 모두의 것이라, 부서진 블록은 잠시 뒤 원래대로 돌아온다.
 * <ul>
 *   <li>부수기 · 폭발 · 불 · 잎 시듦 → 원래 블록이 돌아온다. 붙어 있던 등불 · 꽃 · 횃불 · 문 위아래도 함께</li>
 *   <li>땅 위에 놓은 블록 → 같은 시간 뒤 원래 자리(대개 공기)로 돌아간다 — 길을 막거나 남의 도시에 흔적을 남기지 못한다</li>
 *   <li>예외: 산 땅(청크 주인 · 허가받은 사람이 짓는 곳) · 자원 블록(채집이 따로 되살림) · 크리에이티브(관리자 건축)</li>
 *   <li>부순 블록은 떨어뜨리지 않는다 (복구되는 세계에서 무한 채집 방지). 사람이 서 있으면 비킬 때까지 미룬다</li>
 *   <li>서버를 끄면 남은 자리를 모두 바로 되돌린다</li>
 * </ul>
 */
public final class BlockRestoreRuntime implements Listener {
    private record Pending(Location at, BlockData original, long due) {}

    private final long delayMs;
    private final boolean drops;
    private final Predicate<Block> owned;
    private final Predicate<Material> node;
    private final Map<Location, Pending> pending = new LinkedHashMap<>();

    /**
     * @param owned 산 땅의 블록인가 (그대로 둔다)
     * @param node  자원 블록인가 (채집이 되살린다)
     */
    public BlockRestoreRuntime(Plugin plugin, long delaySeconds, boolean drops, Predicate<Block> owned, Predicate<Material> node) {
        this.delayMs = Math.max(5, delaySeconds) * 1000L;
        this.drops = drops;
        this.owned = owned;
        this.node = node;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private boolean exempt(Block b) {
        return owned.test(b);
    }

    /** 처음 모습만 기억한다 (부순 뒤 다시 놓아도 원래 블록으로) */
    private void remember(Block b, BlockData original) {
        Location at = b.getLocation();
        long due = System.currentTimeMillis() + delayMs;
        Pending old = pending.get(at);
        pending.put(at, new Pending(at, old == null ? original.clone() : old.original(), due));
    }

    /** 이 블록이 사라지면 함께 떨어질 것: 문 · 큰 꽃의 다른 반쪽, 위에 얹힌 것 · 옆과 아래에 매달린 것 (단단하지 않은 블록) */
    private void rememberWith(Block b) {
        remember(b, b.getBlockData());
        if (b.getBlockData() instanceof Bisected half) {
            Block other = b.getRelative(half.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
            if (other.getType() == b.getType()) remember(other, other.getBlockData());
        }
        for (BlockFace f : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block n = b.getRelative(f);
            Material m = n.getType();
            if (!m.isAir() && !m.isSolid() && m != Material.WATER && m != Material.LAVA && !exempt(n)) {
                remember(n, n.getBlockData());
                if (n.getBlockData() instanceof Bisected) {   // 매달린 문 · 큰 꽃의 나머지 반쪽
                    Block up = n.getRelative(BlockFace.UP);
                    if (up.getType() == m) remember(up, up.getBlockData());
                }
            }
        }
    }

    private static boolean creative(Player p) {
        return p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (creative(e.getPlayer()) || exempt(b) || node.test(b.getType())) return;
        rememberWith(b);
        if (!drops) {
            e.setDropItems(false);
            e.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (creative(e.getPlayer()) || exempt(b)) return;
        remember(b, e.getBlockReplacedState().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        exploded(e.blockList());
        if (!drops) e.setYield(0f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        exploded(e.blockList());
        if (!drops) e.setYield(0f);
    }

    private void exploded(List<Block> blocks) {
        for (Block b : blocks) if (!exempt(b) && !b.getType().isAir()) rememberWith(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!exempt(e.getBlock())) rememberWith(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (!exempt(e.getBlock())) remember(e.getBlock(), e.getBlock().getBlockData());
    }

    /** 1초마다: 때가 된 자리를 되돌린다. 청크가 안 읽혔거나 사람이 서 있으면 미룬다 */
    private void tick() {
        if (pending.isEmpty()) return;
        long now = System.currentTimeMillis();
        int budget = 400;   // 한 번에 너무 많이 되돌려 렉이 나지 않게
        Iterator<Pending> it = pending.values().iterator();
        List<Pending> later = new ArrayList<>();
        while (it.hasNext() && budget > 0) {
            Pending p = it.next();
            if (p.due() > now) continue;
            World w = p.at().getWorld();
            if (w == null) { it.remove(); continue; }
            if (!w.isChunkLoaded(p.at().getBlockX() >> 4, p.at().getBlockZ() >> 4)) continue;
            it.remove();
            if (occupied(p.at())) { later.add(new Pending(p.at(), p.original(), now + 5000)); continue; }
            p.at().getBlock().setBlockData(p.original(), false);
            budget--;
        }
        for (Pending p : later) pending.put(p.at(), p);
    }

    private static boolean occupied(Location at) {
        double cx = at.getBlockX() + 0.5, cz = at.getBlockZ() + 0.5;
        for (Player p : at.getWorld().getPlayers()) {
            Location l = p.getLocation();
            if (Math.abs(l.getX() - cx) < 0.9 && Math.abs(l.getZ() - cz) < 0.9 && l.getY() > at.getBlockY() - 2 && l.getY() < at.getBlockY() + 1) return true;
        }
        return false;
    }

    /** 서버 종료: 남은 자리를 모두 되돌린다 */
    public void restoreAll() {
        for (Pending p : pending.values()) if (p.at().getWorld() != null) p.at().getBlock().setBlockData(p.original(), false);
        pending.clear();
    }

    public int pendingCount() {
        return pending.size();
    }
}
