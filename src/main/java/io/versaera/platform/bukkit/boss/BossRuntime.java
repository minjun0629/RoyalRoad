package io.versaera.platform.bukkit.boss;

import io.versaera.application.GameServices;
import io.versaera.domain.boss.BossDefinition;
import io.versaera.domain.boss.BossFight;
import io.versaera.domain.boss.BossMotion;
import io.versaera.domain.boss.BossRewards;
import io.versaera.domain.boss.Shape;
import io.versaera.domain.boss.Vec;
import io.versaera.domain.pack.PackIds;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Consumer;

/**
 * 거대 보스 실행부 (BOS-02). 엔티티는 <b>판정 상자 1개(Interaction) + 모델 1개(ItemDisplay)</b>만 쓴다.
 * 공격 범위는 BossFight 가 수학으로 판정하고, 예고는 범위 외곽선 파티클(최대 48개)로만 보여 준다.
 * 이동은 BossMotion (예고 중에는 멈춤 · 전투 공간 밖으로 나가지 않음), 모델은 리소스팩의 CustomModelData 모델.
 * 기여도(준 피해 · 방패로 막은 피해)는 BossService 로 보내고, 처치 보상은 거기서 한 번씩만 지급된다.
 */
public final class BossRuntime implements Listener {
    private final class Live {
        final BossDefinition def;
        final BossFight fight;
        final Interaction hitbox;
        final ItemDisplay model;
        final BossBar bar;
        final Location home;
        final String fightId;
        double hp;
        BossMotion.Pose pose;
        final Map<UUID, String> names = new HashMap<>();
        final List<Object[]> telegraphs = new ArrayList<>();   // [pattern, origin, yaw, resolveAt]
        Runnable onDefeat;

        Live(BossDefinition def, Location at, String fightId) {
            this.def = def;
            this.home = at.clone();
            this.fightId = fightId;
            this.hp = def.maxHp();
            this.pose = new BossMotion.Pose(at.getX(), at.getZ(), at.getYaw());
            this.fight = new BossFight(def, System.currentTimeMillis());
            float w = (float) (def.hitRadius() * def.scale() * 2), h = (float) (def.scale() * 2);
            hitbox = at.getWorld().spawn(at, Interaction.class, x -> {
                x.setInteractionWidth(w);
                x.setInteractionHeight(h);
                x.setResponsive(true);
                x.setPersistent(false);
            });
            model = at.getWorld().spawn(at, ItemDisplay.class, x -> {
                x.setItemStack(modelItem(def));
                float sc = (float) def.scale();
                x.setTransformation(new Transformation(new Vector3f(0, sc, 0), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
                x.setPersistent(false);
                x.setViewRange(4f);
            });
            bar = Bukkit.createBossBar(Ui.c("&6" + def.name()), BarColor.RED, BarStyle.SEGMENTED_10);
        }
    }

    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final Map<UUID, Live> byHitbox = new HashMap<>();

    public BossRuntime(Plugin plugin, GameServices s, Async async) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    /** 리소스팩 모델 (팩이 없으면 클라이언트는 종이를 크게 보여 준다 — 팩 적용은 RP-01) */
    static ItemStack modelItem(BossDefinition def) {
        ItemStack it = new ItemStack(Material.PAPER);
        if (def.model() != null) {
            ItemMeta m = it.getItemMeta();
            m.setCustomModelData(PackIds.modelData(def.model()));
            it.setItemMeta(m);
        }
        return it;
    }

    public BossDefinition def(String id) {
        return s.content.bosses().stream().filter(b -> b.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("없는 보스: " + id));
    }

    /** 보스를 세운다. 전투 기록이 DB 에 생긴 뒤에 엔티티가 나타난다. onSpawn: 판정 상자 uuid */
    public void spawn(String id, Location at, org.bukkit.command.CommandSender notify, Consumer<UUID> onSpawn) {
        spawn(id, at, notify, onSpawn, null);
    }

    /** onDefeat: 처치 직후 메인 스레드에서 (던전 보스방 등) */
    public void spawn(String id, Location at, org.bukkit.command.CommandSender notify, Consumer<UUID> onSpawn, Runnable onDefeat) {
        BossDefinition d = def(id);
        async.run("boss_start", () -> s.bosses.start(id), fightId -> {
            Live l = new Live(d, at, fightId);
            l.onDefeat = onDefeat;
            byHitbox.put(l.hitbox.getUniqueId(), l);
            at.getWorld().playSound(at, Sound.ENTITY_WITHER_SPAWN, 2f, 0.6f);
            if (onSpawn != null) onSpawn.accept(l.hitbox.getUniqueId());
        }, notify);
    }

    public void spawn(String id, Location at) {
        spawn(id, at, null, null);
    }

    /** 보스 하나를 물린다 (레이드 시간 초과) — 전투는 실패로 기록 */
    public boolean stop(UUID hitbox) {
        Live l = byHitbox.remove(hitbox);
        if (l == null) return false;
        remove(l);
        String id = l.fightId;
        async.fire("boss_fail", () -> { s.bosses.fail(id); return null; });
        return true;
    }

    /** @param record 전투 실패를 DB 에 남길지 (서버 종료 때는 false — 다음 시작 때 recover 가 정리) */
    public int stopAll(boolean record) {
        int n = byHitbox.size();
        for (Live l : byHitbox.values()) {
            remove(l);
            String id = l.fightId;
            if (record) async.fire("boss_fail", () -> { s.bosses.fail(id); return null; });
        }
        byHitbox.clear();
        return n;
    }

    private void remove(Live l) {
        l.hitbox.remove();
        l.model.remove();
        l.bar.removeAll();
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Live l = byHitbox.get(e.getEntity().getUniqueId());
        if (l == null) return;
        e.setCancelled(true);
        Player p = e.getDamager() instanceof Player pl ? pl
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null;
        if (p == null) return;
        Location c = l.hitbox.getLocation();
        boolean weak = l.fight.weakPoint(new Vec(c.getX(), c.getY(), c.getZ()), l.pose.yaw(),
                new Vec(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()));
        double dmg = e.getDamage() * 5 * (weak ? 1.5 : 1);
        l.hp = Math.max(0, l.hp - dmg);
        l.names.put(p.getUniqueId(), p.getName());
        String uuid = p.getUniqueId().toString(), fight = l.fightId;
        long d = Math.round(dmg);
        async.fire("boss_hit", () -> { s.bosses.contribute(fight, uuid, d, 0, 0); return null; });
        if (weak) p.spawnParticle(Particle.CRIT, p.getEyeLocation().add(p.getLocation().getDirection()), 6);
        if (l.hp <= 0) defeat(l);
    }

    private void defeat(Live l) {
        Location c = l.hitbox.getLocation();
        c.getWorld().playSound(c, Sound.ENTITY_ENDER_DRAGON_DEATH, 2f, 0.8f);
        byHitbox.remove(l.hitbox.getUniqueId());
        remove(l);
        if (l.onDefeat != null) l.onDefeat.run();
        Map<String, String> names = new HashMap<>();
        l.names.forEach((u, n) -> names.put(u.toString(), n));
        async.run("boss_defeat", () -> s.bosses.defeated(l.fightId, names), out -> {
            Bukkit.broadcastMessage(Ui.info(l.def.name() + " 토벌" + (out.worldFirst() ? " &6(서버 최초)" : "")));
            out.tiers().forEach((u, t) -> {
                Player p = Bukkit.getPlayer(UUID.fromString(u));
                if (p == null) return;
                p.sendMessage(t == BossRewards.Tier.NONE ? Ui.error("기여가 모자라 보상이 없습니다")
                        : Ui.info(t == BossRewards.Tier.MVP ? "최고 기여 — 보상 + 추가 보상" : "보상이 배달함에 들어왔습니다"));
            });
        }, null);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Live l : new ArrayList<>(byHitbox.values())) {
            if (!l.hitbox.isValid()) {
                byHitbox.remove(l.hitbox.getUniqueId());
                remove(l);
                String id = l.fightId;
                async.fire("boss_fail", () -> { s.bosses.fail(id); return null; });
                continue;
            }
            Location c = l.hitbox.getLocation();
            double arena = l.def.arenaRadius();
            Map<UUID, Vec> targets = new HashMap<>();
            Player nearest = null;
            double best = Double.MAX_VALUE;
            for (Player p : c.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE || p.isDead()) continue;
                double d = p.getLocation().distance(l.home);
                if (d > arena) { l.bar.removePlayer(p); continue; }
                l.bar.addPlayer(p);
                targets.put(p.getUniqueId(), new Vec(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()));
                double dc = p.getLocation().distance(c);
                if (dc < best) { best = dc; nearest = p; }
            }
            l.bar.setProgress(Math.max(0, Math.min(1, l.hp / l.def.maxHp())));
            move(l, nearest, now);
            c = l.hitbox.getLocation();
            for (BossFight.Action a : l.fight.update(now, l.hp / l.def.maxHp(), new Vec(c.getX(), c.getY(), c.getZ()), l.pose.yaw(), targets)) {
                switch (a) {
                    case BossFight.Action.PhaseChanged pc -> {
                        if (pc.announce() != null && !pc.announce().isBlank())
                            for (UUID u : targets.keySet()) { Player p = Bukkit.getPlayer(u); if (p != null) p.sendTitle("", Ui.c("&6" + pc.announce()), 5, 50, 10); }
                    }
                    case BossFight.Action.Telegraph tg -> l.telegraphs.add(new Object[]{l.def.patterns().get(tg.pattern()), tg.origin(), tg.yaw(), tg.resolveAt()});
                    case BossFight.Action.Resolve r -> resolve(l, r);
                    case BossFight.Action.Enraged en -> l.bar.setColor(BarColor.PURPLE);
                }
            }
            l.telegraphs.removeIf(t -> (long) t[3] <= now);
            for (Object[] t : l.telegraphs) outline(c.getWorld(), (BossDefinition.Pattern) t[0], (Vec) t[1], (double) t[2], l.def.scale());
        }
    }

    private void move(Live l, Player target, long now) {
        if (l.fight.casting(now)) return;
        double keep = l.def.hitRadius() * l.def.scale();
        BossMotion.Pose next = target == null
                ? BossMotion.returnHome(l.pose, l.home.getX(), l.home.getZ(), l.def.speed(), l.def.scale(), 0.25)
                : BossMotion.step(l.pose, target.getLocation().getX(), target.getLocation().getZ(), l.home.getX(), l.home.getZ(),
                l.def.arenaRadius(), keep, l.def.speed(), l.def.scale(), 0.25);
        if (next.equals(l.pose)) return;
        l.pose = next;
        World w = l.home.getWorld();
        int y = w.getHighestBlockYAt((int) Math.floor(next.x()), (int) Math.floor(next.z())) + 1;
        Location at = new Location(w, next.x(), Math.max(y, l.home.getY() - 4), next.z(), (float) next.yaw(), 0);
        l.hitbox.teleport(at);
        l.model.teleport(at);
    }

    /** 예고: 범위 외곽선만 (파티클 최대 48개) */
    private static void outline(World w, BossDefinition.Pattern p, Vec o, double yaw, double scale) {
        double r = p.radius() * scale;
        int n = 48;
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 70, 40), 1.6f);
        double[] f = Vec.facing(yaw);
        for (int i = 0; i < n; i++) {
            double x, z;
            if (p.shape() == Shape.LINE) {
                double along = r * i / n, side = (i % 2 == 0 ? 1 : -1) * p.widthOrAngle() * scale / 2;
                x = o.x() + f[0] * along + f[1] * side;
                z = o.z() + f[1] * along - f[0] * side;
            } else {
                double half = p.shape() == Shape.CONE ? Math.toRadians(p.widthOrAngle() / 2) : Math.PI;
                double base = Math.atan2(f[1], f[0]);
                double a = base - half + 2 * half * i / n;
                x = o.x() + Math.cos(a) * r;
                z = o.z() + Math.sin(a) * r;
            }
            w.spawnParticle(Particle.REDSTONE, x, o.y() + 0.2, z, 1, 0, 0, 0, 0, dust);
        }
    }

    private void resolve(Live l, BossFight.Action.Resolve r) {
        Location c = l.hitbox.getLocation();
        c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
        for (UUID u : r.hit()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            double dmg = r.damage() / 5.0;
            if (p.isBlocking()) {
                // 방패로 막으면 피해 60% 감소 — 막아 낸 만큼 기여도
                long saved = Math.round(r.damage() * 0.6);
                dmg *= 0.4;
                String uuid = u.toString(), fight = l.fightId;
                async.fire("boss_block", () -> { s.bosses.contribute(fight, uuid, 0, saved, 0); return null; });
            }
            p.damage(dmg);
            if (r.effect() == null) continue;
            switch (r.effect()) {
                case "knockback" -> p.setVelocity(p.getLocation().toVector().subtract(c.toVector()).setY(0).normalize().multiply(1.4).setY(0.5));
                case "knockup" -> p.setVelocity(new Vector(0, 1.1, 0));
                case "pull" -> p.setVelocity(c.toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(1.2).setY(0.3));
                case "slow", "freeze" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, r.effect().equals("freeze") ? 60 : 100, r.effect().equals("freeze") ? 4 : 1));
                case "blind" -> p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                case "stagger" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 60, 1));
                default -> plugin.getLogger().warning("모르는 보스 효과: " + r.effect());
            }
        }
    }

    /** 지원 기여 (아군 보호 스킬 등) — 스킬 실행부가 부른다 */
    public void support(Player p, long amount) {
        for (Live l : byHitbox.values()) {
            if (p.getWorld() != l.home.getWorld() || p.getLocation().distance(l.home) > l.def.arenaRadius()) continue;
            String uuid = p.getUniqueId().toString(), fight = l.fightId;
            l.names.put(p.getUniqueId(), p.getName());
            async.fire("boss_support", () -> { s.bosses.contribute(fight, uuid, 0, 0, amount); return null; });
        }
    }

    /** 플레이어가 보스 전투 공간 안에 있는가 (던전 보스 · 스킬 판정용) */
    public boolean inArena(Player p) {
        for (Live l : byHitbox.values())
            if (p.getWorld() == l.home.getWorld() && p.getLocation().distance(l.home) <= l.def.arenaRadius()) return true;
        return false;
    }
}
