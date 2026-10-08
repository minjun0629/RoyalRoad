package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 수련관 허수아비 (CANON: 위드처럼 허수아비를 쳐서 스탯을 쌓는다 — 허수아비가 스탯을 주는 게 아니라 반복 행동이 스탯을 만든다).
 * 수련관 지역 가운데에 허수아비(갑옷 거치대) 셋, 마을마다 훈련장 안에 셋. 칠 때마다 행동 기록 hit.training +1 → 힘 스탯 (action_stats.yml),
 * 훈련 교관의 의뢰(train:dummy)도 센다. 훈련장 과녁을 화살로 맞히면 궁술 경험(초보 단계까지) · 의뢰(train:target).
 * 허수아비는 저장하지 않는다 (청크를 읽을 때 세우고, 내릴 때 함께 사라짐) — 겹쳐 생기지 않게.
 */
public final class TrainingDummies implements Listener {
    public static final String TAG = "versa_dummy";
    private static final List<String> HALLS = List.of("basic_training_hall", "novice_training_hall");
    private final GameServices s;
    private final Async async;
    private final Map<UUID, Long> lastHit = new ConcurrentHashMap<>();
    private final Map<String, Long> pending = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTarget = new ConcurrentHashMap<>();
    /** 청크 → 그 안의 훈련장 허수아비 자리 {세계 이름, x, z} */
    private final Map<Long, List<Object[]>> yardSpots = new HashMap<>();

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public TrainingDummies(Plugin plugin, GameServices s, Async async) {
        this.s = s;
        this.async = async;
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickDrills, 20L, 2L);   // 타이밍 막대   // 1초마다 DB 에 (화면 숫자는 칠 때마다 바로)
        // 마을 훈련장 (SettlementPlanner: 광장 북서쪽, 너비 11 · 깊이 13, 허수아비 줄은 북쪽에서 셋째 줄) — 그 앞에 갑옷 거치대 허수아비
        for (Region r : s.regions.all()) {
            if (!io.versaera.domain.terrain.SettlementPlanner.isTown(r)) continue;
            int[] g = io.versaera.domain.terrain.SettlementPlanner.townGrid(r);
            int minX = g[0] - 15, minZ = g[1] - 27;
            for (int dx : new int[]{2, 5, 8}) {
                int x = minX + dx, z = minZ + 5;
                yardSpots.computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new ArrayList<>()).add(new Object[]{r.world(), x, z});
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String h : HALLS) {
                Region r = s.regions.byId(h);
                World w = r == null ? null : Bukkit.getWorld(r.world());
                if (w != null && w.isChunkLoaded(((r.minX() + r.maxX()) / 2) >> 4, ((r.minZ() + r.maxZ()) / 2) >> 4)) place(w, r);
            }
        });
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        List<Object[]> spots = yardSpots.get(chunkKey(e.getChunk().getX(), e.getChunk().getZ()));
        if (spots != null)
            for (Object[] sp : spots)
                if (sp[0].equals(e.getWorld().getName())) yardDummy(e.getWorld(), (int) sp[1], (int) sp[2]);
        for (String h : HALLS) {
            Region r = s.regions.byId(h);
            if (r == null || !r.world().equals(e.getWorld().getName())) continue;
            int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
            if (e.getChunk().getX() == cx >> 4 && e.getChunk().getZ() == cz >> 4) place(e.getWorld(), r);
        }
    }

    /** 훈련장 허수아비 하나: 그 자리가 정말 훈련장 바닥(거친 흙 · 자갈)일 때만, 이미 있으면 두지 않는다 */
    private void yardDummy(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        var floor = w.getBlockAt(x, y, z).getType();
        if (floor != org.bukkit.Material.COARSE_DIRT && floor != org.bukkit.Material.GRAVEL) return;
        Location l = new Location(w, x + 0.5, y + 1, z + 0.5, 180, 0);
        for (Entity e : w.getNearbyEntities(l, 0.8, 1.5, 0.8)) if (e.getScoreboardTags().contains(TAG)) return;
        dummy(w, l);
    }

    private static void dummy(World w, Location l) {
        ArmorStand a = w.spawn(l, ArmorStand.class);
        a.setPersistent(false);
        // setInvulnerable(true) 를 쓰면 바닐라가 피해 이벤트를 아예 보내지 않아 타격을 셀 수 없다 → 피해는 이벤트에서 모두 취소 (onAnyDamage)
        a.setGravity(false);
        a.setArms(true);
        a.setBasePlate(false);
        a.setCustomName("허수아비");
        a.setCustomNameVisible(true);
        a.addScoreboardTag(TAG);
    }

    /** 훈련장 과녁: 화살이 맞으면 센다 (1초에 한 번) */
    @EventHandler(ignoreCancelled = true)
    public void onArrow(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (e.getHitBlock() == null || e.getHitBlock().getType() != org.bukkit.Material.TARGET || !(e.getEntity().getShooter() instanceof Player p)) return;
        long now = System.currentTimeMillis();
        Long last = lastTarget.get(p.getUniqueId());
        if (last != null && now - last < 1000) return;
        lastTarget.put(p.getUniqueId(), now);
        pendingTarget.merge(p.getUniqueId().toString(), 1L, Long::sum);
        showTarget(p);
    }

    private void place(World w, Region r) {
        int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
        for (Entity e : w.getChunkAt(cx >> 4, cz >> 4).getEntities()) if (e.getScoreboardTags().contains(TAG)) return;
        for (int[] o : new int[][]{{-3, 0}, {0, -3}, {3, 0}}) {
            int x = cx + o[0], z = cz + o[1];
            dummy(w, new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5));
        }
    }

    /** 허수아비는 부서지지 않는다 (불 · 폭발 · 낙하 무엇이든) */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnyDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    /** 허수아비 앞에서 하는 타이밍 수련 (사람마다 하나) */
    private static final class Drill {
        long start, active, lockedUntil, noteUntil;
        double center;
        int combo;
        String note = "";
    }

    private final Map<UUID, Drill> drills = new ConcurrentHashMap<>();
    private final Random rng = new Random();

    /**
     * 허수아비 치기 = 타이밍 미니게임 (TimingBar): 화면 아래 막대 위를 표시가 왕복한다. 노란 칸에서 치면 1번, 가운데 초록에서 치면 2번 센다.
     * 칸 밖에서 치면 빗나감 — 연속이 끊기고 0.7초 동안 칠 수 없다. 연타로는 늘지 않는다.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);
        if (!(e.getDamager() instanceof Player p)) return;
        long now = System.currentTimeMillis();
        Long last = lastHit.get(p.getUniqueId());
        if (last != null && now - last < 120) return;   // 한 번 휘두름에 이벤트가 겹쳐 오는 것
        lastHit.put(p.getUniqueId(), now);
        Drill d = drills.computeIfAbsent(p.getUniqueId(), k -> {
            Drill n = new Drill();
            n.start = now;
            n.center = io.versaera.domain.combat.TimingBar.nextCenter(rng.nextDouble());
            return n;
        });
        boolean fresh = now - d.active > 5000;
        d.active = now;
        if (fresh) {   // 처음 (또는 한참 쉬었다가) 치면 막대부터 보여 준다
            d.start = now;
            d.combo = 0;
            note(d, now, "&7막대의 &e노란 칸&7에서 치세요 (가운데 &a초록&7 = 두 배)");
            show(p, "hit.training", 0);
            return;
        }
        if (now < d.lockedUntil) return;
        double pos = io.versaera.domain.combat.TimingBar.position(now - d.start, io.versaera.domain.combat.TimingBar.period(d.combo));
        var g = io.versaera.domain.combat.TimingBar.judge(pos, d.center, io.versaera.domain.combat.TimingBar.width(d.combo));
        switch (g) {
            case MISS -> {
                d.combo = 0;
                d.lockedUntil = now + io.versaera.domain.combat.TimingBar.MISS_LOCK_MS;
                note(d, now, "&c빗나감");
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            }
            case GOOD -> {
                d.combo++;
                note(d, now, "&e좋아 &7+1");
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 1.2f);
            }
            case PERFECT -> {
                d.combo++;
                note(d, now, "&a완벽 &7+2");
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f + Math.min(d.combo, 10) * 0.04f);
            }
        }
        if (g.hits > 0) {
            pending.merge(p.getUniqueId().toString(), (long) g.hits, Long::sum);
            show(p, "hit.training", g.hits);
            d.center = io.versaera.domain.combat.TimingBar.nextCenter(rng.nextDouble());   // 다음 칸은 다른 자리에
            d.start = now;
        }
        render(p, d, now);
    }

    private static void note(Drill d, long now, String text) {
        d.note = text;
        d.noteUntil = now + 900;
    }

    /** 0.1초마다: 수련 중인 사람의 막대를 다시 그린다 (5초 동안 치지 않으면 끝) */
    private void tickDrills() {
        long now = System.currentTimeMillis();
        for (var e : drills.entrySet()) {
            Drill d = e.getValue();
            if (now - d.active > 5000) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) render(p, d, now);
        }
    }

    private void render(Player p, Drill d, long now) {
        double pos = now < d.lockedUntil ? -1 : io.versaera.domain.combat.TimingBar.position(now - d.start, io.versaera.domain.combat.TimingBar.period(d.combo));
        Long total = totals.get(p.getUniqueId() + ":hit.training");
        String text = "&f허수아비 &e" + (total == null ? "…" : total) + "&7회  " + io.versaera.domain.combat.TimingBar.render(pos, d.center, io.versaera.domain.combat.TimingBar.width(d.combo))
                + "  &7연속 &f" + d.combo + (now < d.noteUntil ? "  " + d.note : "");
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(io.versaera.platform.bukkit.Ui.c(text)));
    }

    /** 지금까지 친 수 (DB 기록 + 아직 쓰지 않은 것) — 처음 칠 때 한 번 DB 에서 읽고, 그 뒤로는 칠 때마다 바로 올린다 */
    private final Map<String, Long> totals = new ConcurrentHashMap<>();
    private final java.util.Set<String> loading = ConcurrentHashMap.newKeySet();

    /** @param add 이번에 더한 수 (0 = 읽어 오기만) */
    private void show(Player p, String counter, int add) {
        String key = p.getUniqueId() + ":" + counter;
        if (totals.computeIfPresent(key, (k, v) -> v + add) != null) return;
        if (!loading.add(key)) return;
        String id = p.getUniqueId().toString();
        async.run("training-count", () -> s.growth.counter(id, counter), stored -> {
            loading.remove(key);
            // DB 값 + 아직 쓰지 않은 것 (방금 친 것 포함)
            totals.put(key, stored + (counter.equals("hit.training") ? pending : pendingTarget).getOrDefault(id, 0L));
        }, null);
    }

    private void showTarget(Player p) {
        show(p, "hit.archery_training", 1);
        Long n = totals.get(p.getUniqueId() + ":hit.archery_training");
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(io.versaera.platform.bukkit.Ui.c("&f과녁 &e" + (n == null ? "…" : n) + "&7회")));
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        String u = e.getPlayer().getUniqueId().toString();
        totals.keySet().removeIf(k -> k.startsWith(u));
        drills.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    private void flush() {
        Map<String, Long> batch = drain(pending), targets = drain(pendingTarget);
        if (batch.isEmpty() && targets.isEmpty()) return;
        async.fire("training", () -> {
            batch.forEach((u, n) -> {
                s.growth.record(u, "hit.training", n);
                s.quests.record(u, io.versaera.domain.quest.QuestDefinition.Type.TRAIN, "dummy", (int) Math.min(Integer.MAX_VALUE, n), 0);
            });
            targets.forEach((u, n) -> {
                s.growth.record(u, "hit.archery_training", n);
                s.growth.addXp(u, "archery", 3 * n, 5);   // 과녁으로는 초보 단계까지만 는다
                s.quests.record(u, io.versaera.domain.quest.QuestDefinition.Type.TRAIN, "target", (int) Math.min(Integer.MAX_VALUE, n), 0);
            });
            return null;
        });
    }

    private static Map<String, Long> drain(Map<String, Long> m) {
        Map<String, Long> batch = new HashMap<>(m);
        batch.forEach((k, v) -> m.merge(k, -v, (a, b) -> a + b == 0 ? null : a + b));
        return batch;
    }
}
