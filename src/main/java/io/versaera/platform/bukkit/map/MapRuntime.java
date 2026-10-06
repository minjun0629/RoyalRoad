package io.versaera.platform.bukkit.map;

import io.versaera.application.GameServices;
import io.versaera.domain.map.FogMap;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 탐험 지도 (MAP-01). 걸어서 간 칸(32 블록)만 밝혀지고 나머지는 안개.
 * 지도 그림은 사람마다 다르다 (contextual renderer). 그리기는 2초에 한 번, 가 본 칸 목록은 DB 스레드에서 받아 둔 사본을 쓴다.
 */
public final class MapRuntime implements Listener {
    private static final Map<FogMap.Tone, Color> COLORS = new EnumMap<>(Map.ofEntries(
            Map.entry(FogMap.Tone.FOG, new Color(40, 40, 46)), Map.entry(FogMap.Tone.PLAINS, new Color(110, 160, 80)),
            Map.entry(FogMap.Tone.FOREST, new Color(60, 100, 50)), Map.entry(FogMap.Tone.CITY, new Color(180, 140, 90)),
            Map.entry(FogMap.Tone.DESERT, new Color(215, 195, 140)), Map.entry(FogMap.Tone.SEA, new Color(50, 80, 150)),
            Map.entry(FogMap.Tone.MOUNTAIN, new Color(130, 130, 130)), Map.entry(FogMap.Tone.FROZEN, new Color(230, 235, 245)),
            Map.entry(FogMap.Tone.RUINS, new Color(150, 140, 120)), Map.entry(FogMap.Tone.MIST, new Color(170, 170, 190)),
            Map.entry(FogMap.Tone.BORDER, new Color(200, 160, 40)), Map.entry(FogMap.Tone.YOU, new Color(220, 40, 40)),
            Map.entry(FogMap.Tone.CRATER, new Color(70, 60, 75)), Map.entry(FogMap.Tone.DEEP, new Color(80, 70, 60))));

    private final GameServices s;
    private final Async async;
    private final Map<UUID, Set<Long>> explored = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCell = new HashMap<>();
    private final Map<FogMap.Tone, Byte> palette = new EnumMap<>(FogMap.Tone.class);

    public MapRuntime(Plugin plugin, GameServices s, Async async) {
        this.s = s;
        this.async = async;
        for (var e : COLORS.entrySet()) palette.put(e.getKey(), MapPalette.matchColor(e.getValue()));
    }

    /** 새 칸으로 들어갈 때만 DB 에 기록 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        long cell = FogMap.pack(FogMap.cell(to.getBlockX()), FogMap.cell(to.getBlockZ()));
        Player p = e.getPlayer();
        if (Objects.equals(lastCell.put(p.getUniqueId(), cell), cell)) return;
        String id = p.getUniqueId().toString();
        int x = to.getBlockX(), z = to.getBlockZ();
        UUID u = p.getUniqueId();
        async.run("map-visit", () -> {
            s.maps.visit(id, x, z);
            return s.maps.snapshot(id);
        }, snap -> explored.put(u, snap), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        explored.remove(u);
        lastCell.remove(u);
        String id = u.toString();
        async.fire("map-forget", () -> { s.maps.forget(id); return null; });
    }

    public void give(Player p) {
        MapView view = Bukkit.createMap(p.getWorld());
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
        view.setScale(MapView.Scale.FAR);
        view.setTrackingPosition(false);
        view.addRenderer(new Renderer());
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) it.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName(Ui.c("&6탐험 지도"));
        it.setItemMeta(meta);
        for (ItemStack over : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), over);
    }

    private final class Renderer extends MapRenderer {
        private final Map<UUID, Long> drawnAt = new HashMap<>();

        Renderer() {
            super(true);
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            long now = System.currentTimeMillis();
            Long last = drawnAt.get(player.getUniqueId());
            if (last != null && now - last < 2000) return;
            drawnAt.put(player.getUniqueId(), now);
            Set<Long> known = explored.getOrDefault(player.getUniqueId(), Set.of());
            Location l = player.getLocation();
            String world = l.getWorld().getName();
            FogMap.Tone[] px = FogMap.render(l.getBlockX(), l.getBlockZ(), 8, (cx, cz) -> known.contains(FogMap.pack(cx, cz)),
                    (x, z) -> s.regions.at(world, x, 64, z), l.getBlockX(), l.getBlockZ());
            for (int z = 0; z < FogMap.SIZE; z++)
                for (int x = 0; x < FogMap.SIZE; x++) canvas.setPixel(x, z, palette.get(px[z * FogMap.SIZE + x]));
        }
    }
}
