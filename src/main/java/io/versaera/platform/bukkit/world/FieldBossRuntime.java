package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.domain.fieldboss.FieldBoss;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * 필드 보스의 몸 (BOS-02). 둥지 64 블록 안에 사람이 있고 다시 나타날 때가 됐으면 둥지 가운데에 나타난다 (청크가 이미 로드돼 있을 때만).
 * 아무도 96 블록 안에 없으면 사라졌다가 (보상 · 대기 없음) 다음에 다시 나타난다. 피해는 서버가 세고, 쓰러지면 FieldBossService 가 보상한다.
 * 패턴: REGEN · MINIONS · FEAR · VESSEL · ENRAGE · BREATH · FLIGHT (field_bosses.yml 설명 참고).
 * 리소스팩을 쓰면 바닐라 몸을 숨기고 부위별 모델(ItemDisplay)을 태워, 관절을 보간으로 돌려 걷기 · 날갯짓 · 공격 몸짓을 보여 준다.
 */
public final class FieldBossRuntime implements Listener {
    private static final String TAG = "versa_fboss", MINION = "versa_fboss_minion", VESSEL = "versa_fboss_vessel";
    private static final int VESSEL_HITS = 8;

    private record PartView(io.versaera.pack.ModelKit.Part part, ItemDisplay display) {}

    private static final class Live {
        final FieldBoss def;
        final LivingEntity body;
        final long gen;
        final Map<UUID, Double> damage = new HashMap<>();
        final List<Entity> minions = new ArrayList<>();
        ArmorStand vessel;
        /** 부위별 모델 (리소스팩): 관절을 돌려 걷기 · 날갯짓 · 휘두르기 */
        final List<PartView> parts = new ArrayList<>();
        Location last;
        double phase;
        int attackT;
        org.bukkit.boss.BossBar bar;
        int vesselHits;
        boolean revived, enraged;
        long sinceSeen = System.currentTimeMillis();

        Live(FieldBoss def, LivingEntity body, long gen) {
            this.def = def;
            this.body = body;
            this.gen = gen;
        }
    }

    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final Consumer<Player> deliver;
    private final Map<String, Live> live = new ConcurrentHashMap<>();
    private final Map<UUID, Live> byBody = new ConcurrentHashMap<>();
    /** 보스 id → [다시 나타나는 시각, 출현 번호] (DB 에서 읽어 둔 값) */
    private final Map<String, long[]> schedule = new ConcurrentHashMap<>();
    private final RandomGenerator rng = new java.util.Random();   // getDefault() 는 Paper 의 플러그인 환경에서 구현(jdk.random)을 못 찾는다
    private int tick;

    public FieldBossRuntime(Plugin plugin, GameServices s, Async async, Consumer<Player> deliver) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.deliver = deliver;
        async.run("fboss-load", () -> {
            Map<String, long[]> m = new HashMap<>();
            for (FieldBoss b : s.fieldBosses.all()) m.put(b.id(), new long[]{s.fieldBosses.nextSpawnAt(b.id()), s.fieldBosses.generation(b.id())});
            return m;
        }, schedule::putAll, null);
        Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::turn, 1L, 1L);
    }

    private int frame;

    /** 매 틱: 모델이 몸이 보는 쪽을 보게 (모델은 -z 를 앞으로 만들었다 → 180도 돌림). 2틱마다 관절 자세 */
    private void turn() {
        frame++;
        for (Live l : live.values()) {
            if (!l.body.isValid()) continue;
            float yaw = l.body.getLocation().getYaw() + 180f;
            for (PartView pv : l.parts) if (pv.display().isValid()) pv.display().setRotation(yaw, 0f);
            if (frame % 2 == 0 && !l.parts.isEmpty()) pose(l);
        }
    }

    /** 관절 자세: 걷는 속도만큼 다리 · 팔을 흔들고, 날개는 퍼덕이고, 꼬리 · 머리는 흔들리고, 공격 때 무기 팔(또는 머리)을 휘두른다 */
    private void pose(Live l) {
        Location now = l.body.getLocation();
        double speed = l.last == null || l.last.getWorld() != now.getWorld() ? 0 : Math.hypot(now.getX() - l.last.getX(), now.getZ() - l.last.getZ()) / 2.0;
        l.last = now;
        float s = (float) l.def.size();
        l.phase += speed * 9 / s + 0.0;
        double amp = Math.min(1, speed * 10), t = frame;
        boolean flyer = l.def.look().equals("FLYER") || l.def.mechanics().contains("FLIGHT");
        boolean bothArms = l.def.look().equals("GOLEM");
        double atk = l.attackT > 0 ? 1 - l.attackT / 10.0 : -1;
        if (l.attackT > 0) l.attackT -= 2;
        float lift = (float) (-l.body.getHeight() + 0.5 * s);
        for (PartView pv : l.parts) {
            var part = pv.part();
            double ax = 0, ay = 0, az = 0, ty = 0;
            switch (part.anim()) {
                case "BODY" -> ty = Math.sin(t * 0.08) * 0.015 * s + amp * Math.abs(Math.sin(l.phase)) * 0.03 * s;
                case "HEAD" -> {
                    ax = Math.toRadians(Math.sin(t * 0.06 + part.phase()) * 5);
                    ay = Math.toRadians(Math.sin(t * 0.03 + part.phase()) * 10);
                    if (atk >= 0 && !hasArms(l)) ax += Math.toRadians(-32 * Math.sin(atk * Math.PI));   // 물기 (머리를 내리찍음)
                }
                case "LEG_A" -> ax = Math.toRadians(Math.sin(l.phase) * 35 * amp);
                case "LEG_B" -> ax = Math.toRadians(-Math.sin(l.phase) * 35 * amp);
                case "ARM_B" -> {
                    ax = Math.toRadians(-Math.sin(l.phase) * 25 * amp + Math.sin(t * 0.07) * 3);
                    if (bothArms && atk >= 0) ax = swing(atk);
                }
                case "ARM_A" -> ax = atk >= 0 ? swing(atk) : Math.toRadians(Math.sin(l.phase) * 25 * amp + Math.sin(t * 0.07 + 1) * 3);
                case "WING_L", "WING_R" -> {
                    double flap = Math.sin(t * (flyer ? 0.35 : 0.12)) * (flyer ? 38 : 14) + (flyer ? 8 : -6);
                    az = Math.toRadians(part.anim().equals("WING_L") ? flap : -flap);
                }
                case "TAIL" -> ay = Math.toRadians(Math.sin(t * 0.12 + part.phase()) * 18);
                default -> { }
            }
            org.joml.Quaternionf q = new org.joml.Quaternionf().rotateY((float) ay).rotateX((float) ax).rotateZ((float) az);
            org.joml.Vector3f pivot = new org.joml.Vector3f((float) ((part.px() - 8) / 16 * s), (float) ((part.py() - 8) / 16 * s), (float) ((part.pz() - 8) / 16 * s));
            org.joml.Vector3f turned = q.transform(new org.joml.Vector3f(pivot));
            org.joml.Vector3f tr = new org.joml.Vector3f(0, lift + (float) ty, 0).add(pivot).sub(turned);
            ItemDisplay d = pv.display();
            if (!d.isValid()) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(3);
            d.setTransformation(new org.bukkit.util.Transformation(tr, q, new org.joml.Vector3f(s, s, s), new org.joml.Quaternionf()));
        }
    }

    private static boolean hasArms(Live l) {
        for (PartView pv : l.parts) if (pv.part().anim().equals("ARM_A")) return true;
        return false;
    }

    /** 무기 팔 휘두르기: 머리 위로 들었다가(0 ~ 35%) 앞 아래로 내리친다 */
    private static double swing(double p) {
        return Math.toRadians(p < 0.35 ? 150 * (p / 0.35) : 150 - 130 * ((p - 0.35) / 0.65));
    }

    /** /필드보스 — 마주친 적 있는 보스만: 이름 · 지역 · 지금 상태 (모르는 보스는 이름도 보이지 않는다) */
    public List<String> status(Set<String> known) {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (FieldBoss b : s.fieldBosses.all()) {
            if (!known.contains(b.id())) continue;
            Region r = s.regions.byId(b.region());
            long[] sc = schedule.get(b.id());
            String state = live.containsKey(b.id()) ? "&c출몰" : sc != null && sc[0] > now ? "&7" + ((sc[0] - now) / 60_000 + 1) + "분" : "&8고요";
            out.add("&f" + b.name() + " &8· " + (r == null ? b.region() : r.name()) + " &8· " + state);
        }
        return out;
    }

    private final Set<String> met = ConcurrentHashMap.newKeySet();   // "uuid:boss" — 이번 접속에서 이미 기록함

    /** 32 블록 안에 들어온 사람은 그 보스를 만난 것으로 기록 (탐험 발견 'field_boss') */
    private void meet(Player p, Live l) {
        String key = p.getUniqueId() + ":" + l.def.id();
        if (!met.add(key)) return;
        String id = p.getUniqueId().toString(), name = p.getName(), boss = l.def.id();
        async.fire("meet-fboss", () -> s.exploration.discover(id, name, "field_boss", boss));
    }

    private void second() {
        tick++;
        long now = System.currentTimeMillis();
        for (Live l : new ArrayList<>(live.values())) {
            if (!l.body.isValid() || l.body.isDead()) {   // 죽음 이벤트 없이 사라짐 (청크 내려감 등) → 보상 없이 정리
                cleanup(l, true);
                continue;
            }
            boolean near = false;
            for (Player p : l.body.getWorld().getPlayers()) {
                double d2 = p.getLocation().distanceSquared(l.body.getLocation());
                if (d2 < 32 * 32) meet(p, l);
                if (d2 < 96 * 96) near = true;
            }
            if (near) l.sinceSeen = now;
            if (l.bar != null) {
                var hp = l.body.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                l.bar.setProgress(Math.max(0, Math.min(1, l.body.getHealth() / (hp == null ? l.def.maxHp() : hp.getValue()))));
                for (Player p : new ArrayList<>(l.bar.getPlayers()))
                    if (!p.isOnline() || p.getWorld() != l.body.getWorld() || p.getLocation().distanceSquared(l.body.getLocation()) > 48 * 48) l.bar.removePlayer(p);
                for (Player p : l.body.getWorld().getPlayers())
                    if (p.getLocation().distanceSquared(l.body.getLocation()) <= 48 * 48 && !l.bar.getPlayers().contains(p)) l.bar.addPlayer(p);
            }
            else if (now - l.sinceSeen > 60_000) { cleanup(l, true); continue; }
            mechanics(l);
        }
        if (tick % 10 != 0 || schedule.isEmpty()) return;
        for (FieldBoss b : s.fieldBosses.all()) {
            if (live.containsKey(b.id())) continue;
            long[] sc = schedule.get(b.id());
            if (sc == null || sc[0] > now) continue;
            Region r = s.regions.byId(b.region());
            World w = r == null ? null : Bukkit.getWorld(r.world());
            if (w == null) continue;
            int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
            if (!w.isChunkLoaded(cx >> 4, cz >> 4)) continue;
            Player seen = null;
            for (Player p : w.getPlayers()) {
                double dx = p.getLocation().getX() - cx, dz = p.getLocation().getZ() - cz;
                if (dx * dx + dz * dz < 64 * 64 && p.getGameMode() != GameMode.SPECTATOR) { seen = p; break; }
            }
            if (seen == null) continue;
            Location at = ground(w, cx, cz, r);
            if (at != null) spawn(b, at, sc[1]);
        }
    }

    private static Location ground(World w, int x, int z, Region r) {
        int top = w.getHighestBlockYAt(x, z);
        if (top + 1 <= r.maxY()) return new Location(w, x + 0.5, top + 1, z + 0.5);
        for (int y = Math.min(r.maxY(), w.getMaxHeight() - 3); y > Math.max(r.minY(), w.getMinHeight()); y--)   // 동굴 둥지: 빈 곳을 찾는다
            if (w.getBlockAt(x, y - 1, z).getType().isSolid() && w.getBlockAt(x, y, z).getType().isAir() && w.getBlockAt(x, y + 1, z).getType().isAir())
                return new Location(w, x + 0.5, y, z + 0.5);
        return null;
    }

    private void spawn(FieldBoss b, Location at, long gen) {
        EntityType type;
        try {
            type = EntityType.valueOf(b.entity());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("필드 보스 " + b.id() + ": 없는 엔티티 " + b.entity());
            schedule.remove(b.id());
            return;
        }
        if (!(at.getWorld().spawnEntity(at, type) instanceof LivingEntity e)) return;
        e.addScoreboardTag(TAG);
        for (var k : b.kinds()) e.addScoreboardTag("versa_kind_" + k.name());
        e.setCustomName(Ui.c("&4&l" + b.name()));
        e.setCustomNameVisible(true);
        e.setRemoveWhenFarAway(false);
        e.setPersistent(false);
        var hp = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) hp.setBaseValue(b.maxHp());
        e.setHealth(b.maxHp());
        if (e instanceof Phantom ph) ph.setSize(6);
        if (e instanceof Hoglin h) h.setImmuneToZombification(true);           // 지상에서 좀비로 바뀌지 않게
        if (e instanceof PiglinAbstract pa) pa.setImmuneToZombification(true);
        if (e instanceof Slime sl) sl.setSize(6);
        e.setFireTicks(0);
        Live l = new Live(b, e, gen);
        if (io.versaera.platform.bukkit.ui.Menu.background) {   // 리소스팩 모델: 바닐라 몸은 숨기고 부위별 모델을 태운다 (판정은 바닐라 몸)
            e.setInvisible(true);
            if (e.getEquipment() != null) e.getEquipment().clear();
            for (var part : io.versaera.pack.ModelKit.rig(b.look())) {
                ItemStack it = new ItemStack(Material.PAPER);
                var meta = it.getItemMeta();
                meta.setCustomModelData(io.versaera.domain.pack.PackIds.modelData("fboss/" + b.id() + "/" + part.name()));
                it.setItemMeta(meta);
                ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, x -> {
                    x.setItemStack(it);
                    x.setPersistent(false);
                    x.setViewRange(3f);
                    x.setInterpolationDuration(3);
                });
                e.addPassenger(d);
                l.parts.add(new PartView(part, d));
            }
            l.last = e.getLocation();
            pose(l);
        }
        l.bar = Bukkit.createBossBar(Ui.c("&4" + b.name()), org.bukkit.boss.BarColor.RED, org.bukkit.boss.BarStyle.SEGMENTED_10);
        live.put(b.id(), l);
        byBody.put(e.getUniqueId(), l);
        if (b.mechanics().contains("VESSEL")) {
            Location v = at.clone().add(rng.nextInt(9) - 4, 0, 6);
            v.setY(at.getWorld().getHighestBlockYAt(v) + 1);
            ArmorStand st = at.getWorld().spawn(v, ArmorStand.class);
            st.setCustomName(Ui.c("&5생명의 그릇 &7(" + VESSEL_HITS + "번 쳐서 부순다)"));
            st.setCustomNameVisible(true);
            st.setPersistent(false);
            st.setGlowing(true);
            st.addScoreboardTag(VESSEL);
            l.vessel = st;
        }
        for (Player p : at.getWorld().getPlayers())
            if (p.getLocation().distanceSquared(at) < 80 * 80) p.sendTitle(Ui.c("&4" + b.name()), Ui.c("&7" + b.description()), 10, 60, 20);
        at.getWorld().playSound(at, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.8f);
    }

    private void mechanics(Live l) {
        LivingEntity b = l.body;
        Set<String> m = l.def.mechanics();
        var hpAttr = b.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = hpAttr == null ? l.def.maxHp() : hpAttr.getValue();
        List<Player> near = new ArrayList<>();
        for (Player p : b.getWorld().getPlayers())
            if (p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE && p.getLocation().distanceSquared(b.getLocation()) < 24 * 24) near.add(p);
        if (b instanceof Mob mob && (mob.getTarget() == null || !mob.getTarget().isValid()) && !near.isEmpty()) mob.setTarget(near.get(0));
        if (m.contains("REGEN") && b.getHealth() < max) b.setHealth(Math.min(max, b.getHealth() + max * 0.005));
        if (m.contains("ENRAGE") && !l.enraged && b.getHealth() < max * 0.3) {
            l.enraged = true;
            b.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20 * 600, 1));
            b.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 600, 1));
            for (Player p : near) p.sendMessage(Ui.c("&4" + l.def.name() + "이(가) 미쳐 날뛴다!"));
        }
        if (near.isEmpty()) return;
        if (m.contains("MINIONS") && tick % 20 == 0) {
            l.minions.removeIf(x -> !x.isValid());
            if (l.minions.size() < 4) {
                try {
                    EntityType mt = EntityType.valueOf(l.def.minion());
                    for (int i = 0; i < 2; i++) {
                        Entity x = b.getWorld().spawnEntity(b.getLocation().add(rng.nextInt(7) - 3, 0, rng.nextInt(7) - 3), mt);
                        x.addScoreboardTag(MINION);
                        x.setPersistent(false);
                        l.minions.add(x);
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (m.contains("FEAR") && tick % 12 == 0) {
            for (Player p : near) if (p.getLocation().distanceSquared(b.getLocation()) < 10 * 10) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
                Ui.bar(p, "&5" + l.def.name() + "의 공포에 몸이 굳는다");
            }
            b.getWorld().spawnParticle(Particle.SOUL, b.getLocation().add(0, 1, 0), 40, 3, 1, 3, 0.02);
        }
        if (m.contains("BREATH") && tick % 10 == 5) {
            Vector dir = b.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1e-4) dir = near.get(0).getLocation().toVector().subtract(b.getLocation().toVector()).setY(0);
            dir.normalize();
            for (int i = 1; i <= 8; i++)
                b.getWorld().spawnParticle(Particle.FLAME, b.getLocation().add(dir.clone().multiply(i)).add(0, 1.2, 0), 10, 0.4 + i * 0.08, 0.3, 0.4 + i * 0.08, 0.02);
            l.attackT = 10;
            for (Player p : near) {
                Vector to = p.getLocation().toVector().subtract(b.getLocation().toVector()).setY(0);
                if (to.length() > 8.5 || to.length() < 0.1 || to.normalize().dot(dir) < 0.5) continue;
                p.damage(l.def.damage() * 1.2, b);
                p.setFireTicks(60);
            }
        }
        if (m.contains("FLIGHT") && tick % 8 == 3) {
            Player t = near.get(rng.nextInt(near.size()));
            Vector v = t.getLocation().toVector().subtract(b.getLocation().toVector());
            if (v.lengthSquared() > 4) b.setVelocity(v.normalize().multiply(1.2).setY(0.7));
        }
    }

    private void cleanup(Live l, boolean remove) {
        live.remove(l.def.id());
        byBody.remove(l.body.getUniqueId());
        for (Entity x : l.minions) if (x.isValid()) x.remove();
        if (l.vessel != null && l.vessel.isValid()) l.vessel.remove();
        for (PartView pv : l.parts) if (pv.display().isValid()) pv.display().remove();
        if (l.bar != null) l.bar.removeAll();
        if (remove && l.body.isValid()) l.body.remove();
    }

    private static Player playerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    /** 보스의 기본 피해 (방어 계산은 CombatListener 가 그 뒤에) */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBossHits(EntityDamageByEntityEvent e) {
        Live l = byBody.get(e.getDamager().getUniqueId());
        if (l != null && e.getEntity() instanceof Player) {
            e.setDamage(l.def.damage());
            l.attackT = 10;   // 휘두르는 몸짓
        }
        if (e.getEntity() instanceof ArmorStand st && st.getScoreboardTags().contains(VESSEL)) {   // 생명의 그릇: 여러 번 쳐야 부서진다
            e.setCancelled(true);
            if (playerOf(e.getDamager()) == null) return;
            for (Live x : live.values()) if (x.vessel == st && ++x.vesselHits >= VESSEL_HITS) {
                st.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, st.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
                st.getWorld().playSound(st.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
                st.remove();
                for (Player p : st.getWorld().getPlayers()) if (p.getLocation().distanceSquared(st.getLocation()) < 48 * 48)
                    p.sendMessage(Ui.c("&d생명의 그릇이 부서졌다 — " + x.def.name() + "은(는) 이제 되살아나지 못한다"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent e) {
        Live l = byBody.get(e.getEntity().getUniqueId());
        Player p = l == null ? null : playerOf(e.getDamager());
        if (p != null) l.damage.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    /** 생명의 그릇이 남아 있으면 한 번 되살아난다 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        Live l = byBody.get(e.getEntity().getUniqueId());
        if (l == null || l.revived || l.vessel == null || !l.vessel.isValid() || e.getFinalDamage() < l.body.getHealth()) return;
        e.setCancelled(true);
        l.revived = true;
        var max = l.body.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        l.body.setHealth((max == null ? l.def.maxHp() : max.getValue()) * 0.5);
        l.body.getWorld().spawnParticle(Particle.TOTEM, l.body.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0.3);
        for (Player p : l.body.getWorld().getPlayers()) if (p.getLocation().distanceSquared(l.body.getLocation()) < 48 * 48)
            p.sendMessage(Ui.c("&5" + l.def.name() + ": 생명의 그릇이 남아 있는 한 나는 죽지 않는다!"));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity().getScoreboardTags().contains(MINION)) {
            e.getDrops().clear();
            return;
        }
        Live l = byBody.get(e.getEntity().getUniqueId());
        if (l == null) return;
        e.getDrops().clear();
        e.setDroppedExp(200);
        cleanup(l, false);
        if (l.damage.isEmpty()) return;   // 사람이 때리지 않음 → 보상 없이 다음 출현
        Map<String, Double> dmg = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        l.damage.forEach((u, d) -> {
            dmg.put(u.toString(), d);
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            names.put(u.toString(), op.getName() == null ? "?" : op.getName());
        });
        long seed = rng.nextLong();
        async.run("fboss-defeat", () -> s.fieldBosses.defeated(l.def.id(), l.gen, dmg, names, new SplittableRandom(seed)), d -> {
            schedule.put(l.def.id(), new long[]{d.nextSpawnAt(), l.gen + 1});
            Bukkit.broadcastMessage(Ui.c("&6필드 보스 &4" + l.def.name() + " &6쓰러짐 — &f" + names.get(d.top())
                    + (d.worldFirst() ? " &e(세계 최초)" : "") + (d.drops().isEmpty() ? "" : " &7· 전리품: " + String.join(", ", d.drops()))));
            for (String u : d.rewarded()) {
                Player p = Bukkit.getPlayer(UUID.fromString(u));
                if (p != null) deliver.accept(p);
            }
        }, null);
    }

    public void shutdown() {
        for (Live l : new ArrayList<>(live.values())) cleanup(l, true);
    }
}
