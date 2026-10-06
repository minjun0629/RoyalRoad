package io.versaera.platform.bukkit.dungeon;

import io.versaera.application.DungeonService;
import io.versaera.application.GameServices;
import io.versaera.domain.dungeon.DungeonDefinition;
import io.versaera.domain.dungeon.DungeonLayout;
import io.versaera.domain.dungeon.DungeonLayout.Kind;
import io.versaera.domain.dungeon.DungeonRun;
import io.versaera.domain.dungeon.LeverPuzzle;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.boss.BossRuntime;
import io.versaera.platform.bukkit.command.GameCommands;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 던전 실행부 (DUN-01). 전용 빈 세계(versa_dungeons)에 판마다 새로 짓는다. 상태 판단은 DungeonService/DungeonRun 이 한다.
 * <ul>
 *   <li>방 17×17 · 높이 7, 방 사이 통로. 문은 들어갈 수 있을 때만 열린다 (쇠창살)</li>
 *   <li>전투방: 처음 들어가면 몬스터가 나오고, 다 잡으면 정리 → 이웃 문이 열린다</li>
 *   <li>퍼즐방: 레버마다 문양 표지판, 맞은편 벽에 순서를 새긴 표지판 — 보고 맞는 순서로 당긴다</li>
 *   <li>숨은 방: 금 간 벽돌 벽 뒤. 이어진 방을 정리한 뒤 그 벽을 우클릭하면 열린다</li>
 *   <li>보스방: 길 위의 방과 퍼즐을 다 끝내면 열린다. 강화 몬스터는 방에서, 거대 보스는 크기에 맞는 원형 전투장으로 옮겨 싸운다</li>
 * </ul>
 * 세계는 서버를 켤 때마다 지운다 (판이 끝나면 그 자리는 다시 쓰지 않음, 재시작 때 정리).
 */
public final class DungeonRuntime implements Listener, GameCommands.DungeonStarter {
    public static final String WORLD = "versa_dungeons";
    private static final int ROOM = 17, PITCH = 24, HEIGHT = 7, BASE_Y = 80, SLOT_SPACING = 2000;
    private static final String[] SYMBOLS = {"☀", "☾", "★", "♦", "♣", "♠", "♥", "♪"};

    /** 빈 세계 생성기 (아무것도 만들지 않음) */
    public static final class VoidGenerator extends ChunkGenerator {
    }

    private final class Live {
        final DungeonService.Handle h;
        final int ox, oz;
        final Map<UUID, Location> returnTo = new HashMap<>();
        final Map<Integer, Set<UUID>> mobs = new HashMap<>();
        final Set<Integer> spawned = new HashSet<>();
        final Map<Location, Integer> levers = new HashMap<>();
        final List<Location> secretWall = new ArrayList<>();
        UUID bossEntity;
        boolean bossSpawned, finished;

        Live(DungeonService.Handle h, int slot) {
            this.h = h;
            this.ox = slot * SLOT_SPACING;
            this.oz = 0;
        }

        Location center(int room) {
            DungeonLayout.Room r = h.run().layout().rooms().get(room);
            return new Location(world, ox + r.gx() * PITCH + 0.5, BASE_Y + 1, oz + r.gz() * PITCH + 0.5);
        }

        int roomAt(Location l) {
            for (DungeonLayout.Room r : h.run().layout().rooms()) {
                double cx = ox + r.gx() * PITCH, cz = oz + r.gz() * PITCH;
                if (Math.abs(l.getX() - cx) <= ROOM / 2.0 && Math.abs(l.getZ() - cz) <= ROOM / 2.0 && Math.abs(l.getY() - BASE_Y) < HEIGHT + 2) return r.id();
            }
            return -1;
        }
    }

    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final BossRuntime bosses;
    private final Map<String, Live> runs = new HashMap<>();
    private final Map<UUID, String> playerRun = new HashMap<>();
    private World world;
    private int nextSlot;
    private java.util.function.Predicate<Player> seesHints = p -> false;

    /** 통찰 스탯이 있으면 숨은 벽에 실마리 파티클이 보인다 */
    public void hints(java.util.function.Predicate<Player> seesHints) {
        this.seesHints = seesHints;
    }

    public DungeonRuntime(Plugin plugin, GameServices s, Async async, BossRuntime bosses) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.bosses = bosses;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** 서버 시작 때: 지난 실행의 던전 세계를 지우고 새로 만든다 */
    public void prepareWorld() {
        World old = Bukkit.getWorld(WORLD);
        if (old != null) Bukkit.unloadWorld(old, false);
        Path dir = new File(Bukkit.getWorldContainer(), WORLD).toPath();
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                plugin.getLogger().warning("던전 세계를 지우지 못했습니다: " + e.getMessage());
            }
        }
        world = new WorldCreator(WORLD).generator(new VoidGenerator()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (world != null) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setTime(18000);
        }
    }

    // ------------------------------------------------------------------ 입장
    @Override
    public void enter(Player leader, String dungeonId) {
        if (world == null) { leader.sendMessage(Ui.error("던전 세계가 준비되지 않았습니다")); return; }
        DungeonDefinition d;
        try {
            d = s.dungeons.dungeon(dungeonId);
        } catch (RuntimeException e) {
            leader.sendMessage(Ui.error("없는 던전입니다"));
            return;
        }
        Location l = leader.getLocation();
        boolean inside = false;
        for (Region r = s.regions.at(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); r != null;
             r = r.parent() == null ? null : s.regions.byId(r.parent()))
            if (r.id().equals(d.region())) inside = true;
        if (!inside) { leader.sendMessage(Ui.error(s.regions.byId(d.region()).name() + "에서 들어갈 수 있습니다")); return; }
        // 일행: 6 블록 안의 사람 (최대 인원까지)
        List<Player> party = new ArrayList<>(List.of(leader));
        for (Player o : leader.getWorld().getPlayers())
            if (o != leader && party.size() < d.maxParty() && o.getLocation().distance(l) <= 6 && !playerRun.containsKey(o.getUniqueId())) party.add(o);
        List<String> ids = party.stream().map(p -> p.getUniqueId().toString()).toList();
        long seed = new Random().nextLong();
        async.run("dungeon-start", () -> s.dungeons.start(dungeonId, ids, seed), h -> {
            Live run = new Live(h, nextSlot++);
            runs.put(h.runId(), run);
            for (Player p : party) {
                run.returnTo.put(p.getUniqueId(), p.getLocation());
                playerRun.put(p.getUniqueId(), h.runId());
                p.sendMessage(Ui.info(d.name() + " · " + party.size() + "명"));
            }
            build(run, party);
        }, leader);
    }

    private Material mat(DungeonDefinition d, int i, Material fallback) {
        if (i >= d.palette().size()) return fallback;
        Material m = Material.matchMaterial(d.palette().get(i));
        return m == null ? fallback : m;
    }

    /** 방 하나씩 틱마다 짓는다 (한 번에 수만 블록을 놓지 않음) */
    private void build(Live run, List<Player> party) {
        DungeonLayout layout = run.h.run().layout();
        Iterator<DungeonLayout.Room> it = layout.rooms().iterator();
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (it.hasNext()) {
                buildRoom(run, it.next());
                return;
            }
            task.cancel();
            for (DungeonLayout.Link link : layout.links()) buildLink(run, link);
            refreshDoors(run);
            Location start = run.center(layout.room(Kind.START).id());
            for (Player p : party) if (p.isOnline()) p.teleport(start);
        }, 1L, 1L);
    }

    private void buildRoom(Live run, DungeonLayout.Room r) {
        DungeonDefinition d = run.h.def();
        Material floor = mat(d, 0, Material.STONE_BRICKS), wall = mat(d, 1, Material.STONE_BRICKS), decor = mat(d, 2, Material.CHAIN),
                light = mat(d, 3, Material.LANTERN);
        if (r.kind() == Kind.HIDDEN) wall = Material.CRACKED_STONE_BRICKS;
        int cx = run.ox + r.gx() * PITCH, cz = run.oz + r.gz() * PITCH, h = ROOM / 2;
        for (int x = -h; x <= h; x++)
            for (int z = -h; z <= h; z++) {
                world.getBlockAt(cx + x, BASE_Y, cz + z).setType(floor, false);
                world.getBlockAt(cx + x, BASE_Y + HEIGHT + 1, cz + z).setType(wall, false);
                boolean edge = Math.abs(x) == h || Math.abs(z) == h;
                for (int y = 1; y <= HEIGHT; y++) world.getBlockAt(cx + x, BASE_Y + y, cz + z).setType(edge ? wall : Material.AIR, false);
            }
        for (int[] c : new int[][]{{-h + 2, -h + 2}, {h - 2, -h + 2}, {-h + 2, h - 2}, {h - 2, h - 2}}) {
            world.getBlockAt(cx + c[0], BASE_Y + HEIGHT, cz + c[1]).setType(decor, false);
            world.getBlockAt(cx + c[0], BASE_Y + HEIGHT - 1, cz + c[1]).setType(light, false);
        }
        if (r.kind() == Kind.PUZZLE) buildPuzzle(run, r, cx, cz);
        if (r.kind() == Kind.BOSS) world.getBlockAt(cx, BASE_Y, cz).setType(Material.CHISELED_STONE_BRICKS, false);
    }

    /** 레버(북쪽 벽 안쪽)와 문양 표지판, 남쪽 벽에 순서 표지판 */
    private void buildPuzzle(Live run, DungeonLayout.Room r, int cx, int cz) {
        LeverPuzzle pz = run.h.run().puzzle();
        int n = pz.size(), h = ROOM / 2;
        for (int i = 0; i < n; i++) {
            int x = cx - (n - 1) + i * 2, z = cz - h + 1;
            Block lever = world.getBlockAt(x, BASE_Y + 2, z);
            lever.setType(Material.LEVER, false);
            if (lever.getBlockData() instanceof Switch sw) {
                sw.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
                sw.setFacing(BlockFace.SOUTH);
                sw.setPowered(false);
                lever.setBlockData(sw, false);
            }
            run.levers.put(lever.getLocation(), i);
            sign(world.getBlockAt(x, BASE_Y + 3, z), BlockFace.SOUTH, SYMBOLS[i]);
        }
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < n; i++) order.append(SYMBOLS[pz.clue(i)]).append(i < n - 1 ? " " : "");
        sign(world.getBlockAt(cx, BASE_Y + 3, cz + h - 1), BlockFace.NORTH, order.toString());
    }

    private void sign(Block b, BlockFace facing, String text) {
        b.setType(Material.OAK_WALL_SIGN, false);
        if (b.getBlockData() instanceof org.bukkit.block.data.type.WallSign ws) {
            ws.setFacing(facing);
            b.setBlockData(ws, false);
        }
        if (b.getState() instanceof Sign sg) {
            sg.setLine(1, text);
            sg.update(true, false);
        }
    }

    private int[] doorway(Live run, DungeonLayout.Link link) {
        DungeonLayout.Room a = run.h.run().layout().rooms().get(link.a()), b = run.h.run().layout().rooms().get(link.b());
        return new int[]{b.gx() - a.gx(), b.gz() - a.gz(), run.ox + a.gx() * PITCH, run.oz + a.gz() * PITCH};
    }

    /** 통로: 두 방 사이 7 블록. 숨은 연결은 금 간 벽으로 막아 둔다 */
    private void buildLink(Live run, DungeonLayout.Link link) {
        DungeonDefinition d = run.h.def();
        Material floor = mat(d, 0, Material.STONE_BRICKS), wall = mat(d, 1, Material.STONE_BRICKS);
        int[] dw = doorway(run, link);
        int dx = dw[0], dz = dw[1], ax = dw[2], az = dw[3], h = ROOM / 2;
        for (int step = h; step <= PITCH - h; step++)
            for (int side = -2; side <= 2; side++) {
                int x = ax + dx * step + (dz != 0 ? side : 0), z = az + dz * step + (dx != 0 ? side : 0);
                boolean edge = Math.abs(side) == 2;
                world.getBlockAt(x, BASE_Y, z).setType(floor, false);
                world.getBlockAt(x, BASE_Y + 4, z).setType(wall, false);
                for (int y = 1; y <= 3; y++) {
                    Block b = world.getBlockAt(x, BASE_Y + y, z);
                    if (edge) b.setType(wall, false);
                    else if (link.secret() && step == h) {
                        b.setType(Material.CRACKED_STONE_BRICKS, false);
                        run.secretWall.add(b.getLocation());
                    } else b.setType(Material.AIR, false);
                }
            }
    }

    /** 문 상태 맞추기: 양쪽 다 들어갈 수 있으면 열림, 아니면 쇠창살 */
    private void refreshDoors(Live run) {
        DungeonRun dr = run.h.run();
        int h = ROOM / 2;
        for (DungeonLayout.Link link : dr.layout().links()) {
            if (link.secret()) continue;
            boolean open = dr.canEnter(link.a()) && dr.canEnter(link.b());
            int[] dw = doorway(run, link);
            for (int end : new int[]{h, PITCH - h})
                for (int side = -1; side <= 1; side++)
                    for (int y = 1; y <= 3; y++) {
                        int x = dw[2] + dw[0] * end + (dw[1] != 0 ? side : 0), z = dw[3] + dw[1] * end + (dw[0] != 0 ? side : 0);
                        world.getBlockAt(x, BASE_Y + y, z).setType(open ? Material.AIR : Material.IRON_BARS, false);
                    }
        }
    }

    // ------------------------------------------------------------------ 진행
    private void tick() {
        for (Live run : new ArrayList<>(runs.values())) {
            if (run.finished) continue;
            String rid = run.h.runId();
            async.run("dungeon-tick", () -> s.dungeons.tick(rid), failed -> {
                if (failed) finish(run, false);
            }, null);
            for (UUID u : run.returnTo.keySet()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null || p.getWorld() != world) continue;
                if (seesHints.test(p) && !run.h.run().hiddenFound())
                    for (Location wl : run.secretWall)
                        if (wl.distance(p.getLocation()) < 10) p.spawnParticle(Particle.ENCHANTMENT_TABLE, wl.clone().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3, 0.2);
                int room = run.roomAt(p.getLocation());
                if (room < 0 || run.spawned.contains(room)) continue;
                DungeonLayout.Room r = run.h.run().layout().rooms().get(room);
                if ((r.kind() == Kind.COMBAT || r.kind() == Kind.TREASURE || r.kind() == Kind.HIDDEN) && !run.h.run().isCleared(room)) spawnRoom(run, room);
                else if (r.kind() == Kind.BOSS && !run.bossSpawned) spawnBoss(run, room);
                run.spawned.add(room);
            }
        }
    }

    private void spawnRoom(Live run, int room) {
        DungeonDefinition d = run.h.def();
        SplittableRandom rng = new SplittableRandom(run.h.run().layout().seed() * 31 + room);
        Location c = run.center(room);
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < d.monstersPerRoom(); i++) {
            EntityType type;
            try {
                type = EntityType.valueOf(d.monsters().get(rng.nextInt(d.monsters().size())));
            } catch (IllegalArgumentException e) {
                type = EntityType.ZOMBIE;
            }
            Location at = c.clone().add(rng.nextInt(-5, 6), 0, rng.nextInt(-5, 6));
            Entity e = world.spawnEntity(at, type);
            if (e instanceof LivingEntity le) scale(le, d.monsterHealth());
            ids.add(e.getUniqueId());
        }
        run.mobs.put(room, ids);
    }

    private static void scale(LivingEntity le, double mult) {
        AttributeInstance a = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (a == null) return;
        a.setBaseValue(Math.min(1024, a.getBaseValue() * mult));
        le.setHealth(a.getValue());
        le.setRemoveWhenFarAway(false);
    }

    private void spawnBoss(Live run, int room) {
        run.bossSpawned = true;
        DungeonDefinition d = run.h.def();
        Location c = run.center(room);
        if (d.boss() != null) {
            // 거대 보스는 방에 들어가지 않는다: 보스 크기에 맞는 열린 전투장을 따로 짓고 일행을 옮긴다
            Location arena = buildArena(run, bosses.def(d.boss()).arenaRadius());
            int r = (int) Math.min(64, bosses.def(d.boss()).arenaRadius());
            for (UUID u : run.returnTo.keySet()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.getWorld() == world) p.teleport(arena.clone().add(0, 0, r - 6));
            }
            bosses.spawn(d.boss(), arena, null, uuid -> run.bossEntity = uuid, () -> bossDown(run));
            return;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(d.bossMob());
        } catch (IllegalArgumentException e) {
            type = EntityType.RAVAGER;
        }
        Entity e = world.spawnEntity(c, type);
        if (e instanceof LivingEntity le) {
            scale(le, d.bossHealth());
            le.setCustomName(Ui.c("&6" + d.name()));
            le.setCustomNameVisible(true);
        }
        run.bossEntity = e.getUniqueId();
    }

    /** 지붕 없는 원형 전투장 (반지름 = 보스 전투 공간, 최대 64). 판의 자리와 다음 판 자리 사이 */
    private Location buildArena(Live run, double radius) {
        DungeonDefinition d = run.h.def();
        Material floor = mat(d, 0, Material.STONE_BRICKS), wall = mat(d, 1, Material.STONE_BRICKS), light = mat(d, 3, Material.LANTERN);
        int r = (int) Math.min(64, radius), cx = run.ox + SLOT_SPACING / 2, cz = run.oz;   // 방 격자(최대 ±40칸 × 24)와 겹치지 않는 자리
        for (int x = -r - 1; x <= r + 1; x++)
            for (int z = -r - 1; z <= r + 1; z++) {
                double dist = Math.hypot(x, z);
                if (dist > r + 1) continue;
                world.getBlockAt(cx + x, BASE_Y, cz + z).setType(floor, false);
                if (dist > r) for (int y = 1; y <= 6; y++) world.getBlockAt(cx + x, BASE_Y + y, cz + z).setType(wall, false);
            }
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            world.getBlockAt(cx + (int) Math.round(Math.cos(a) * (r - 1)), BASE_Y + 1, cz + (int) Math.round(Math.sin(a) * (r - 1))).setType(light, false);
        }
        return new Location(world, cx + 0.5, BASE_Y + 1, cz + 0.5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity().getWorld() != world) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        UUID dead = e.getEntity().getUniqueId();
        for (Live run : runs.values()) {
            if (dead.equals(run.bossEntity) && run.h.def().boss() == null) { bossDown(run); return; }
            for (Map.Entry<Integer, Set<UUID>> m : run.mobs.entrySet()) {
                if (!m.getValue().remove(dead) || !m.getValue().isEmpty()) continue;
                int room = m.getKey();
                String rid = run.h.runId();
                async.run("dungeon-room", () -> { s.dungeons.roomCleared(rid, room); return null; }, v -> {
                    refreshDoors(run);
                    for (UUID u : run.returnTo.keySet()) { Player p = Bukkit.getPlayer(u); if (p != null) Ui.bar(p, "&a방 정리"); }
                }, null);
                return;
            }
        }
    }

    private void bossDown(Live run) {
        Map<String, String> names = new HashMap<>();
        for (UUID u : run.returnTo.keySet()) { Player p = Bukkit.getPlayer(u); if (p != null) names.put(u.toString(), p.getName()); }
        String rid = run.h.runId();
        async.run("dungeon-clear", () -> s.dungeons.bossDefeated(rid, names), rewarded -> {
            for (UUID u : run.returnTo.keySet()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) continue;
                p.sendTitle(Ui.c("&6" + run.h.def().name()), Ui.c(rewarded.contains(u.toString()) ? "&7정복 · 보상은 배달함" : "&7정복 · 재사용 대기 중"), 5, 60, 10);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> finish(run, true), 200L);
        }, null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || e.getClickedBlock().getWorld() != world || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Live run = runs.get(playerRun.get(p.getUniqueId()));
        if (run == null) return;
        Block b = e.getClickedBlock();
        Integer lever = run.levers.get(b.getLocation());
        if (lever != null && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            String rid = run.h.runId();
            int puzzleRoom = run.h.run().layout().room(Kind.PUZZLE).id();
            async.run("dungeon-lever", () -> {
                LeverPuzzle.Result r = s.dungeons.pull(rid, lever);
                if (r == LeverPuzzle.Result.SOLVED) s.dungeons.roomCleared(rid, puzzleRoom);
                return r;
            }, r -> {
                setLever(b, r != LeverPuzzle.Result.WRONG);
                if (r == LeverPuzzle.Result.WRONG) {
                    for (Location l : run.levers.keySet()) setLever(l.getBlock(), false);
                    world.playSound(b.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
                } else if (r == LeverPuzzle.Result.SOLVED) {
                    world.playSound(b.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
                    refreshDoors(run);
                }
            }, p);
            return;
        }
        if (b.getType() == Material.CRACKED_STONE_BRICKS && run.secretWall.contains(b.getLocation()) && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            int from = run.roomAt(p.getLocation());
            String rid = run.h.runId();
            async.run("dungeon-secret", () -> s.dungeons.revealHidden(rid, from), ok -> {
                if (!ok) { Ui.bar(p, "&8벽 너머에서 바람 소리가 난다"); return; }
                for (Location l : run.secretWall) l.getBlock().setType(Material.AIR, false);
                world.playSound(b.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
                Ui.bar(p, "&6숨은 방");
            }, p);
        }
    }

    private static void setLever(Block b, boolean on) {
        if (b.getBlockData() instanceof Switch sw) {
            sw.setPowered(on);
            b.setBlockData(sw, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld() == world && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getWorld() == world && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        String rid = playerRun.remove(e.getPlayer().getUniqueId());
        Live run = rid == null ? null : runs.get(rid);
        if (run == null) return;
        Location back = run.returnTo.remove(e.getPlayer().getUniqueId());
        if (back != null) e.setRespawnLocation(back);
        if (run.returnTo.isEmpty()) finish(run, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        String rid = playerRun.remove(p.getUniqueId());
        Live run = rid == null ? null : runs.get(rid);
        if (run == null) return;
        Location back = run.returnTo.remove(p.getUniqueId());
        if (back != null) p.teleport(back);
        String id = p.getUniqueId().toString();
        async.fire("dungeon-leave", () -> { s.dungeons.leave(id); return null; });
        if (run.returnTo.isEmpty()) finish(run, false);
    }

    private void finish(Live run, boolean cleared) {
        if (run.finished) return;
        run.finished = true;
        runs.remove(run.h.runId());
        for (Set<UUID> ids : run.mobs.values()) for (UUID u : ids) { Entity e = Bukkit.getEntity(u); if (e != null) e.remove(); }
        if (run.bossEntity != null) { Entity e = Bukkit.getEntity(run.bossEntity); if (e != null && run.h.def().boss() == null) e.remove(); }
        for (Map.Entry<UUID, Location> m : run.returnTo.entrySet()) {
            playerRun.remove(m.getKey());
            Player p = Bukkit.getPlayer(m.getKey());
            if (p == null) continue;
            p.teleport(m.getValue());
            if (!cleared) p.sendMessage(Ui.error(run.h.def().name() + " 실패"));
        }
        if (!cleared) {
            String rid = run.h.runId();
            for (UUID u : run.returnTo.keySet()) {
                String id = u.toString();
                async.fire("dungeon-leave", () -> { s.dungeons.leave(id); return null; });
            }
            async.fire("dungeon-end", () -> { try { s.dungeons.tick(rid); } catch (RuntimeException ignored) { } return null; });
        }
        run.returnTo.clear();
    }

    public void stopAll() {
        for (Live run : new ArrayList<>(runs.values())) {
            for (Map.Entry<UUID, Location> m : run.returnTo.entrySet()) {
                Player p = Bukkit.getPlayer(m.getKey());
                if (p != null) p.teleport(m.getValue());
            }
        }
        runs.clear();
        playerRun.clear();
    }
}
