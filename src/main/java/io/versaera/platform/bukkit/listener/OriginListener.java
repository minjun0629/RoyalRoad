package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.application.OriginService;
import io.versaera.domain.origin.Gender;
import io.versaera.domain.origin.Race;
import io.versaera.domain.origin.StartCity;
import io.versaera.domain.world.Region;
import io.versaera.persistence.DbExecutor;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.ui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 캐릭터 만들기 · 초보 기간 (CHR-01 · BEG-01).
 * <ul>
 *   <li>처음 들어오면 움직일 수 없고, 종족 → 성별 → 시작 도시 창이 뜬다. 고르면 그 도시 광장으로 소환되고 그곳이 부활 지점이 된다</li>
 *   <li>초보 기간(게임 30일)에는 시작 도시 밖으로 나갈 수 없다 (RegionTracker.confine)</li>
 *   <li>종족 특성: 오크 최대 체력 +4 · 조인족 낙하 피해 없음 · 엘프 밤눈</li>
 * </ul>
 */
public final class OriginListener implements Listener {
    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final DbExecutor exec;
    private final Map<UUID, OriginService.Character> chars = new ConcurrentHashMap<>();
    private final Set<UUID> creating = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String[]> picks = new ConcurrentHashMap<>();   // [종족, 성별]

    public OriginListener(Plugin plugin, GameServices s, Async async, DbExecutor exec) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.exec = exec;
    }

    public Optional<OriginService.Character> character(UUID u) {
        return Optional.ofNullable(chars.get(u));
    }

    /** 초보는 시작 도시 밖으로 못 나간다 — RegionTracker 가 지역이 바뀔 때 부른다 */
    public String confine(Player p, String regionId, Location to) {
        if (creating.contains(p.getUniqueId())) return "먼저 캐릭터를 만들어야 한다";
        // 관리자 (versaera.admin) · config beginner.confine: false 면 어디든 간다 (초보 보호 — 죽어도 손실 없음 — 는 그대로)
        if (p.hasPermission("versaera.admin") || !plugin.getConfig().getBoolean("beginner.confine", true)) return null;
        OriginService.Character c = chars.get(p.getUniqueId());
        if (c == null || !c.beginner(System.currentTimeMillis()) || inside(c, regionId, to)) return null;
        long left = c.beginnerUntil() - System.currentTimeMillis();
        return "초보 기간 — 아직 " + c.city().name() + " 둘레의 사냥터 밖으로 나갈 수 없다 (현실 " + hours(left) + " 남음)";
    }

    /** 초보 사냥터 폭: 성벽 밖으로 이만큼 (토끼 · 여우가 나오는 들판) */
    public static final int HUNTING_RING = 96;

    /** 초보가 다닐 수 있는 곳: 시작 도시 지역 안, 또는 그 도시 성벽에서 HUNTING_RING 블록 안의 들판 */
    private boolean inside(OriginService.Character c, String regionId, Location at) {
        if (s.origins.insideCity(c, regionId)) return true;
        Region city = s.regions.byId(c.city().region());
        if (city == null || at == null || !io.versaera.domain.terrain.SettlementPlanner.isTown(city) || !city.world().equals(at.getWorld().getName())) return false;
        int[] g = io.versaera.domain.terrain.SettlementPlanner.townGrid(city);
        return Math.max(Math.abs(at.getBlockX() - g[0]), Math.abs(at.getBlockZ() - g[1])) <= g[2] + 4 + HUNTING_RING;
    }

    public double extraHealth(UUID u) {
        OriginService.Character c = chars.get(u);
        return c != null && "max_health".equals(c.race().perk()) ? 4 : 0;
    }

    private static String hours(long ms) {
        long h = Math.max(0, ms) / 3_600_000L, m = Math.max(0, ms) / 60_000L % 60;
        return h > 0 ? h + "시간 " + m + "분" : m + "분";
    }

    // ------------------------------------------------------------------ 들어옴 · 나감
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String id = p.getUniqueId().toString();
        async.run("origin", () -> s.origins.character(id), c -> {
            if (!p.isOnline()) return;
            if (c.isEmpty()) {
                creating.add(p.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> raceMenu(p), 20L);
                return;
            }
            chars.put(p.getUniqueId(), c.get());
            applyPerks(p, c.get());
            if (c.get().beginner(System.currentTimeMillis())) {
                Region here = s.regions.at(p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
                if (!inside(c.get(), here == null ? null : here.id(), p.getLocation())) spawnIn(p, c.get().city());
            }
        }, p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        chars.remove(e.getPlayer().getUniqueId());
        creating.remove(e.getPlayer().getUniqueId());
        picks.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!creating.contains(e.getPlayer().getUniqueId()) || e.getTo() == null) return;
        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ() || e.getFrom().getY() < e.getTo().getY())
            e.setTo(e.getFrom().setDirection(e.getTo().getDirection()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        OriginService.Character c = chars.get(p.getUniqueId());
        if (c != null && "no_fall_damage".equals(c.race().perk())) e.setCancelled(true);   // 조인족
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (creating.contains(p.getUniqueId())) {   // 만드는 중에 어떻게든 쓰러졌다면 다시 창부터
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && creating.contains(p.getUniqueId())) reopen(p); }, 5L);
            return;
        }
        OriginService.Character c = chars.get(p.getUniqueId());
        if (c != null) Bukkit.getScheduler().runTaskLater(plugin, () -> applyPerks(p, c), 5L);
    }

    /** 캐릭터를 만드는 동안은 다치지 않는다 (창이 열린 채 맞아 죽으면 창이 사라졌다) */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHurtWhileCreating(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && creating.contains(p.getUniqueId())) e.setCancelled(true);
    }

    /** … 몬스터도 노리지 않는다 */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTargetWhileCreating(org.bukkit.event.entity.EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p && creating.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHungerWhileCreating(org.bukkit.event.entity.FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && creating.contains(p.getUniqueId())) e.setCancelled(true);
    }

    private void applyPerks(Player p, OriginService.Character c) {
        if ("night_vision".equals(c.race().perk())) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
    }

    // ------------------------------------------------------------------ 캐릭터 만들기 창
    private final class Step extends Menu {
        Step(int rows, String title) {
            super(rows, title);
        }

        @Override
        public void closed(Player p) {
            // 다 만들기 전에는 창을 닫아도 다시 연다
            if (creating.contains(p.getUniqueId()))
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && creating.contains(p.getUniqueId()) && !(p.getOpenInventory().getTopInventory().getHolder() instanceof Step)) reopen(p);
                }, 2L);
        }
    }

    private void reopen(Player p) {
        String[] k = picks.get(p.getUniqueId());
        if (k == null) raceMenu(p);
        else if (k[1] == null) genderMenu(p);
        else cityMenu(p);
    }

    private static Material raceIcon(String id) {
        return switch (id) {
            case "elf" -> Material.BOW;
            case "dwarf" -> Material.IRON_PICKAXE;
            case "orc" -> Material.IRON_AXE;
            case "birdfolk" -> Material.FEATHER;
            default -> Material.PLAYER_HEAD;
        };
    }

    private void raceMenu(Player p) {
        if (!p.isOnline()) return;
        picks.remove(p.getUniqueId());
        Step m = new Step(3, "&8종족 선택");
        List<Race> races = s.origins.options().races();
        for (int i = 0; i < races.size(); i++) {
            Race r = races.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + r.note());
            r.xpBonus().forEach((d, v) -> lore.add("&a" + s.growth.discipline(d).name() + " 숙련 +" + Math.round(v * 100) + "%"));
            switch (r.perk()) {
                case "max_health" -> lore.add("&a최대 체력 +4");
                case "no_fall_damage" -> lore.add("&a떨어져도 다치지 않음");
                case "night_vision" -> lore.add("&a밤눈");
                default -> { }
            }
            m.set(11 + i, Menu.icon(raceIcon(r.id()), "&6" + r.name(), lore), ev -> {
                picks.put(p.getUniqueId(), new String[]{r.id(), null});
                genderMenu(p);
            });
        }
        m.open(p);
        p.sendTitle(Ui.c("&6로열 로드"), "", 10, 60, 20);
    }

    private void genderMenu(Player p) {
        Step m = new Step(3, "&8성별 선택");
        Gender[] gs = Gender.values();
        for (int i = 0; i < gs.length; i++) {
            Gender g = gs[i];
            m.set(11 + i * 2, Menu.icon(g == Gender.MALE ? Material.IRON_HELMET : g == Gender.FEMALE ? Material.GOLDEN_HELMET : Material.LEATHER_HELMET,
                    "&6" + g.label, List.of("&7능력 차이는 없습니다")), ev -> {
                String[] k = picks.get(p.getUniqueId());
                if (k == null) {
                    raceMenu(p);
                    return;
                }
                k[1] = g.name();
                cityMenu(p);
            });
        }
        m.set(18, Menu.icon(Material.ARROW, "&7뒤로", List.of()), ev -> raceMenu(p));
        m.open(p);
    }

    private void cityMenu(Player p) {
        Step m = new Step(3, "&8시작 도시 선택");
        List<StartCity> cities = s.origins.options().cities();
        for (int i = 0; i < cities.size(); i++) {
            StartCity c = cities.get(i);
            m.set(10 + i, Menu.icon(Material.LODESTONE, "&6" + c.name(), List.of("&e" + c.kingdom(), "&7" + c.note(),
                    "&8처음 게임 30일(현실 " + hours(s.rules().time().realMillisFor(s.origins.options().beginnerGameDays())) + ")은 이 도시 밖으로 못 나갑니다")),
                    ev -> create(p, c));
        }
        m.set(18, Menu.icon(Material.ARROW, "&7뒤로", List.of()), ev -> genderMenu(p));
        m.open(p);
    }

    private void create(Player p, StartCity city) {
        String[] k = picks.get(p.getUniqueId());
        if (k == null || k[1] == null) {
            raceMenu(p);
            return;
        }
        String id = p.getUniqueId().toString();
        String race = k[0];
        Gender g = Gender.valueOf(k[1]);
        async.run("origin-create", () -> s.origins.create(id, race, g, city.id()), c -> {
            creating.remove(p.getUniqueId());
            picks.remove(p.getUniqueId());
            chars.put(p.getUniqueId(), c);
            if (!p.isOnline()) return;
            p.closeInventory();
            spawnIn(p, city);
            applyPerks(p, c);
            p.sendTitle(Ui.c("&6" + city.name()), Ui.c("&7" + c.race().name() + " · " + g.label), 10, 70, 20);
            p.sendMessage(Ui.info("보리빵 10개가 배달함으로 왔습니다"));
        }, p);
    }

    /** 도시 광장 (분수 남쪽 큰길 위, 분수를 바라본다). 그 자리가 막혀 있으면 가까운 빈 땅을 찾는다. 그곳을 부활 지점으로 */
    private void spawnIn(Player p, StartCity city) {
        Region r = s.regions.byId(city.region());
        World w = r == null ? null : Bukkit.getWorld(r.world());
        if (w == null) return;
        int[] sp = io.versaera.domain.terrain.SettlementPlanner.spawnPoint(r);
        Location l = safe(w, sp[0], sp[1]);
        l.setYaw(sp[2]);
        p.teleport(l);
        p.setBedSpawnLocation(l, true);
    }

    /** (x, z) 에서 나선으로 넓혀 가며: 발밑이 단단하고 (물 · 용암 아님) 발 · 머리 칸이 비어 있는 첫 자리 */
    static Location safe(World w, int x0, int z0) {
        for (int rad = 0; rad <= 12; rad++)
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rad) continue;
                    int x = x0 + dx, z = z0 + dz, y = w.getHighestBlockYAt(x, z);
                    Material ground = w.getBlockAt(x, y, z).getType();
                    if (!ground.isSolid() || ground == Material.LAVA || ground == Material.WATER) continue;
                    if (!w.getBlockAt(x, y + 1, z).getType().isAir() || !w.getBlockAt(x, y + 2, z).getType().isAir()) continue;
                    return new Location(w, x + 0.5, y + 1, z + 0.5);
                }
        return new Location(w, x0 + 0.5, w.getHighestBlockYAt(x0, z0) + 1, z0 + 0.5);
    }
}
