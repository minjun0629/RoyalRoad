package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.combat.DamageCalculator;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.skill.Mastery;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * 전투 (CMB-01 기본형). 무기 · 방어구는 고유 아이템 값으로 계산하고, 결과는 매 타격 DB 에 쓰지 않고 모아서 쓴다.
 * <ul>
 *   <li>공격: 손에 든 고유 무기의 공격력 · 품질 · 무기 숙련 → 피해. 등 뒤(약점)는 1.5배.</li>
 *   <li>방어: 입은 고유 방어구의 방어력 합 → 피해 감소. 방패로 막으면 크게 감소.</li>
 *   <li>기록: 맞은 횟수(인내) · 무기 숙련 경험치 · 무기 내구도 마모를 5초마다 한 번에 저장.</li>
 *   <li>장비 능력 (ITM-02): 속성 피해 · 상대별 추가 피해 · 치명 · 방어 무시 · 흡수 · 둔화 · 기절 · 세트 · 피해 감소 · 반사 ·
 *       재생 · 저주 · 이동 속도 · 최대 체력. 착용 조건을 못 채우면 무기는 20% 힘만, 방어구는 0</li>
 *   <li>손질 버프 (검 갈기 · 방어구 닦기 · 다림질) · 조각 파괴술 · 다른 하나의 검 · 일점 공격 (SKL-05)</li>
 * </ul>
 * 스킬 · 콤보 · 회피 · 상태 이상은 SkillListener (CMB-02).
 */
public final class CombatListener implements Listener {
    /** 메인 스레드에서 쓰는 캐시: 아이템 id → (종류, 품질). DB 에서 한 번 읽어 둔다. */
    private record Cached(String typeId, int quality) {}

    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Map<String, Cached> items = new ConcurrentHashMap<>();
    private final Map<String, Integer> weaponMastery = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingHits = new ConcurrentHashMap<>(), pendingXp = new ConcurrentHashMap<>();
    private final Map<String, String> pendingXpDiscipline = new ConcurrentHashMap<>();
    private final Map<String, Integer> pendingWear = new ConcurrentHashMap<>();
    private final Map<String, String> wearOwner = new ConcurrentHashMap<>();
    private final RandomGenerator rng = new java.util.Random();   // getDefault() 는 Paper 의 플러그인 환경에서 구현(jdk.random)을 못 찾는다
    /** 직업 효과 캐시: uuid → {공격 %, 방어 %, 치명 확률 가산} */
    private final Map<String, double[]> perks = new ConcurrentHashMap<>();
    private static final double[] NO_PERKS = {0, 0, 0};
    /** 착용 조건 값 (GearService.context) — 접속 때 · 1분마다 */
    private final Map<String, Map<String, Long>> gearCtx = new ConcurrentHashMap<>();
    /** 입은 장비 + 든 무기의 늘 붙는 능력 합 (세트 포함) — 2초마다 */
    private final Map<UUID, Map<String, Integer>> worn = new ConcurrentHashMap<>();
    private final Map<UUID, Double> itemHealth = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warnedAt = new ConcurrentHashMap<>();
    /** 손질 · 비기 버프: uuid → [끝나는 시각, %] */
    private final Map<UUID, double[]> whet = new ConcurrentHashMap<>(), polish = new ConcurrentHashMap<>(), destruction = new ConcurrentHashMap<>();
    private final Map<UUID, Long> twin = new ConcurrentHashMap<>();
    /** 일점 공격: uuid → [대상 해시, 쌓임, 마지막 시각] */
    private final Map<UUID, long[]> focus = new ConcurrentHashMap<>();
    private java.util.function.Consumer<Player> healthChanged = p -> { };
    private int ticks;

    private java.util.function.Function<UUID, String> regionOf = u -> null;

    /** 지역 (날씨 · 파티 분배) — RegionTracker 가 채운다 */
    public void regionOf(java.util.function.Function<UUID, String> f) {
        this.regionOf = f;
    }

    public CombatListener(Plugin plugin, GameServices s, Async async, ItemCodec codec) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.codec = codec;
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::gearTick, 40L, 40L);
    }

    // ------------------------------------------------------------------ 장비 능력 (ITM-02)

    /** 아이템 체력이 바뀌면 최대 체력을 다시 계산하게 (SkillListener.reload) */
    public void onHealthChanged(java.util.function.Consumer<Player> f) {
        healthChanged = f;
    }

    public double itemHealth(UUID u) {
        return itemHealth.getOrDefault(u, 0.0);
    }

    public void buffWeapon(UUID u, double pct, int minutes) {
        whet.put(u, new double[]{System.currentTimeMillis() + minutes * 60_000L, pct});
    }

    public void buffArmor(UUID u, double pct, int minutes) {
        polish.put(u, new double[]{System.currentTimeMillis() + minutes * 60_000L, pct});
    }

    public void buffDestruction(UUID u, double pct, int minutes) {
        destruction.put(u, new double[]{System.currentTimeMillis() + minutes * 60_000L, pct});
    }

    public void twin(UUID u, int seconds) {
        twin.put(u, System.currentTimeMillis() + seconds * 1000L);
    }

    private static double active(Map<UUID, double[]> m, UUID u) {
        double[] b = m.get(u);
        if (b == null) return 0;
        if (b[0] < System.currentTimeMillis()) { m.remove(u); return 0; }
        return b[1];
    }

    /** 이 사람이 이 장비를 쓸 수 있나 (조건 값을 아직 못 읽었으면 쓸 수 있다고 본다) */
    private boolean usable(String owner, ItemType t) {
        if (t.requires().isEmpty()) return true;
        Map<String, Long> c = gearCtx.get(owner);
        return c == null || io.versaera.application.GearService.unmet(t, c).isEmpty();
    }

    private void warn(Player p, ItemType t) {
        long now = System.currentTimeMillis();
        Long last = warnedAt.get(p.getUniqueId());
        if (last != null && now - last < 30_000) return;
        warnedAt.put(p.getUniqueId(), now);
        p.sendMessage(io.versaera.platform.bukkit.Ui.error(t.name() + ": 착용 조건을 채우지 못해 힘을 쓰지 못한다"));
    }

    private static boolean raw;

    /** 비기 · 생활 스킬처럼 피해를 이미 정한 타격: 무기 계산을 건너뛰고(방어는 그대로) 처치 기록은 남긴다. 메인 스레드 전용 */
    public static void rawDamage(LivingEntity target, double amount, Player source) {
        raw = true;
        try {
            target.damage(amount, source);
        } finally {
            raw = false;
        }
    }

    /** 상대의 종류 — 바닐라 몸 + 필드 보스 표시(scoreboard tag "versa_kind_UNDEAD" …) */
    public static Set<io.versaera.domain.item.ItemOptions.Kind> kinds(LivingEntity e) {
        Set<io.versaera.domain.item.ItemOptions.Kind> k = EnumSet.noneOf(io.versaera.domain.item.ItemOptions.Kind.class);
        switch (e.getType().name()) {
            case "ZOMBIE", "SKELETON", "WITHER_SKELETON", "DROWNED", "HUSK", "STRAY", "PHANTOM", "ZOMBIFIED_PIGLIN", "ZOMBIE_VILLAGER",
                 "SKELETON_HORSE", "ZOMBIE_HORSE", "ZOGLIN", "WITHER" -> k.add(io.versaera.domain.item.ItemOptions.Kind.UNDEAD);
            case "BLAZE", "GHAST", "VEX", "ENDERMAN", "PIGLIN_BRUTE", "EVOKER" -> k.add(io.versaera.domain.item.ItemOptions.Kind.DEMON);
            case "ENDER_DRAGON" -> { k.add(io.versaera.domain.item.ItemOptions.Kind.DRAGON); k.add(io.versaera.domain.item.ItemOptions.Kind.LARGE); }
            case "PLAYER" -> k.add(io.versaera.domain.item.ItemOptions.Kind.HUMAN);
            default -> { }
        }
        var hp = e.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (hp != null && hp.getValue() >= 80) k.add(io.versaera.domain.item.ItemOptions.Kind.LARGE);
        for (String tag : e.getScoreboardTags())
            if (tag.startsWith("versa_kind_")) {
                try { k.add(io.versaera.domain.item.ItemOptions.Kind.valueOf(tag.substring(11))); } catch (IllegalArgumentException ignored) { }
            }
        return k;
    }

    /** 2초마다: 입은 장비 능력 합 · 10초마다: 재생 · 저주 · 이동 속도 · 최대 체력 */
    private void gearTick() {
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String owner = p.getUniqueId().toString();
            List<ItemType> list = new ArrayList<>();
            for (ItemStack it : p.getInventory().getArmorContents()) {
                String iid = codec.instanceId(it);
                Cached c = iid == null ? null : lookup(iid, owner);
                if (c == null) continue;
                ItemType t = codec.types().get(c.typeId());
                if (usable(owner, t)) list.add(t);
            }
            String hand = codec.instanceId(p.getInventory().getItemInMainHand());
            Cached hc = hand == null ? null : lookup(hand, owner);
            Map<String, Integer> total = new HashMap<>(io.versaera.domain.item.ItemOptions.total(list, codec.types().sets()));
            if (hc != null) {   // 든 무기는 '늘 붙는' 능력만 (체력 · 속도 · 재생 · 저주)
                ItemType t = codec.types().get(hc.typeId());
                if (usable(owner, t)) for (String k : List.of("health", "speed", "regen", "drain")) {
                    Integer v = t.stats().get(k);
                    if (v != null) total.merge(k, v, Integer::sum);
                }
            }
            worn.put(p.getUniqueId(), total);
            var pas = io.versaera.domain.item.ItemOptions.passive(total);
            float speed = (float) (0.2 * (1 + pas.speedPct() / 100.0));
            if (Math.abs(p.getWalkSpeed() - speed) > 0.001f) p.setWalkSpeed(speed);
            Double before = itemHealth.put(p.getUniqueId(), pas.health());
            if (before == null ? pas.health() != 0 : before != pas.health()) healthChanged.accept(p);
            if (ticks % 5 == 0 && !p.isDead()) {
                var max = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                double m = max == null ? 20 : max.getValue();
                if (pas.regen() > 0) p.setHealth(Math.min(m, p.getHealth() + pas.regen()));
                if (pas.drain() > 0 && p.getHealth() > 1) {
                    p.setHealth(Math.max(1, p.getHealth() - pas.drain()));
                    p.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_NORMAL, p.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.01);
                }
            }
            if (ticks % 30 == 0) async.fire("gear-ctx", () -> { gearCtx.put(owner, s.gear.context(owner)); return null; });
        }
    }

    private static String disciplineOf(ItemType t) {
        if (t.hasTag("sword") || t.hasTag("dagger")) return "swordsmanship";
        if (t.hasTag("spear")) return "spearmanship";
        if (t.hasTag("bow")) return "archery";
        return null;
    }

    /** 처음 보는 아이템은 이번 타격에는 기본값으로 계산하고, DB 에서 읽어 다음부터 정확히 */
    private Cached lookup(String itemId, String owner) {
        Cached c = items.get(itemId);
        if (c != null) return c;
        async.fire("combat-cache", () -> {
            s.items.find(itemId).filter(it -> it.custody().ownedBy(owner)).ifPresent(it -> items.put(itemId, new Cached(it.typeId(), it.quality())));
            return null;
        });
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        double damage = e.getDamage();
        double pierce = 0;
        if (e.getDamager() instanceof Player attacker && !raw) {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            String iid = codec.instanceId(hand), owner = attacker.getUniqueId().toString();
            Cached w = iid == null ? null : lookup(iid, owner);
            if (w != null) {
                ItemType t = codec.types().get(w.typeId());
                String d = disciplineOf(t);
                int lv = d == null ? 1 : weaponMastery.getOrDefault(owner + ":" + d, 1);
                boolean back = victim.getLocation().getDirection().setY(0).normalize()
                        .dot(attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0).normalize()) < -0.5;
                double attack = t.stats().getOrDefault("attack", 0) * Math.max(0.2, attacker.getAttackCooldown());
                double[] pk = perks.getOrDefault(owner, NO_PERKS);
                double melee = d != null && !d.equals("archery") && pk.length > 3 ? pk[3] : 0;
                io.versaera.domain.item.ItemOptions.Hit opt = io.versaera.domain.item.ItemOptions.Hit.NONE;
                if (usable(owner, t)) opt = io.versaera.domain.item.ItemOptions.onHit(t.stats(), io.versaera.domain.item.Quality.statMultiplier(w.quality()), kinds(victim));
                else { attack *= 0.2; warn(attacker, t); }
                UUID au = attacker.getUniqueId();
                double buff = 1 + (active(whet, au) + active(destruction, au)) / 100.0;
                damage = DamageCalculator.compute(new DamageCalculator.Attack(attack * (1 + pk[0] + melee) * buff, w.quality(), lv, 0.05 + pk[2] + opt.critBonus(), 1.5, back),
                        new DamageCalculator.Defense(0, false, false), rng).damage();
                damage = damage * opt.mult() + opt.bonus();
                // 일점 공격 (검술 10+): 같은 상대를 2초 안에 이어 치면 쌓인다
                if ("swordsmanship".equals(d) && lv >= io.versaera.domain.skill.LifeSkills.FOCUS_MIN_LEVEL) {
                    long now = System.currentTimeMillis(), target = victim.getUniqueId().getMostSignificantBits();
                    long[] f = focus.get(au);
                    int stack = f != null && f[0] == target && now - f[2] <= io.versaera.domain.skill.LifeSkills.FOCUS_WINDOW_MS ? (int) f[1] + 1 : 0;
                    focus.put(au, new long[]{target, stack, now});
                    damage *= io.versaera.domain.skill.LifeSkills.focusMult(stack);
                    if (stack >= io.versaera.domain.skill.LifeSkills.FOCUS_MAX_STACK)
                        victim.getWorld().spawnParticle(org.bukkit.Particle.CRIT_MAGIC, victim.getLocation().add(0, 1, 0), 10, 0.2, 0.3, 0.2, 0.1);
                }
                pierce = opt.pierce();
                applyOptions(attacker, victim, opt, damage);
                Long tw = twin.get(au);
                if (tw != null && tw > System.currentTimeMillis()) {   // 다른 하나의 검: 한 번 더 (절반)
                    double extra = damage * 0.5;
                    Bukkit.getScheduler().runTask(plugin, () -> { if (victim.isValid() && !victim.isDead()) victim.damage(extra); });
                }
                if (d != null) {
                    pendingXp.merge(owner + ":" + d, 1L, Long::sum);
                    pendingXpDiscipline.put(owner + ":" + d, d);
                }
                pendingWear.merge(iid, 1, Integer::sum);
                wearOwner.put(iid, owner);
            }
        }
        if (victim instanceof Player defender) {
            String owner = defender.getUniqueId().toString();
            double armor = 0, raw = 0;
            for (ItemStack it : defender.getInventory().getArmorContents()) {
                String iid = codec.instanceId(it);
                Cached c = iid == null ? null : lookup(iid, owner);
                if (c == null) continue;
                ItemType t = codec.types().get(c.typeId());
                if (!usable(owner, t)) { warn(defender, t); continue; }
                int def = t.stats().getOrDefault("defense", 0);
                raw += def;
                armor += def * io.versaera.domain.item.Quality.statMultiplier(c.quality());
            }
            Map<String, Integer> tot = worn.getOrDefault(defender.getUniqueId(), Map.of());
            armor += Math.max(0, tot.getOrDefault("defense", 0) - raw);   // 세트 보너스 방어력
            armor *= (1 + active(polish, defender.getUniqueId()) / 100.0) * (1 - pierce);
            var pas = io.versaera.domain.item.ItemOptions.passive(tot);
            damage = damage * 100.0 / (100.0 + armor * 4) * (1 - Math.min(0.5, perks.getOrDefault(owner, NO_PERKS)[1])) * (1 - pas.resistPct() / 100.0);
            if (pas.thornsPct() > 0 && e.getDamager() instanceof LivingEntity src && src != defender) {
                double back = damage * pas.thornsPct() / 100.0;
                Bukkit.getScheduler().runTask(plugin, () -> { if (src.isValid() && !src.isDead()) src.damage(back); });
            }
            if (defender.isBlocking()) damage *= 0.4;
            pendingHits.merge(owner, 1L, Long::sum);
        }
        // 날씨 (WTH-01): 근접 · 활 피해 배율 (공격한 플레이어가 있는 지역)
        Player wp = e.getDamager() instanceof Player pl ? pl : e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player sh ? sh : null;
        if (wp != null) {
            String region = regionOf.apply(wp.getUniqueId());
            if (region != null) damage *= s.weather.combat(region, e.getDamager() instanceof Player
                    ? io.versaera.application.WeatherService.Attack.MELEE : io.versaera.application.WeatherService.Attack.RANGED);
        }
        e.setDamage(Math.max(0.5, damage));
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        String id = e.getPlayer().getUniqueId().toString();
        async.fire("warm", () -> { warm(id); return null; });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        String id = k.getUniqueId().toString();
        String type = e.getEntityType().name().toLowerCase(java.util.Locale.ROOT);
        // 파티 (PTY-02): 40 블록 안의 파티원 — 처치 경험을 나누고, 의뢰 처치도 함께 센다
        List<String> near = new ArrayList<>();
        for (String m : s.parties.members(id)) {
            Player o = Bukkit.getPlayer(UUID.fromString(m));
            if (o != null && o.getWorld().equals(k.getWorld()) && o.getLocation().distance(e.getEntity().getLocation()) <= 40) near.add(m);
        }
        if (!near.contains(id)) near.add(0, id);
        var maxHp = e.getEntity().getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        long base = Math.max(4, Math.round(maxHp == null ? 10 : maxHp.getValue()));
        long share = io.versaera.domain.party.Parties.share(base, near.size());
        // 전리품 (PTY-02): 차례 · 무작위면 떨어진 물건에 주인을 정한다 (30초 동안 그 사람만 줍는다 — 바닐라 Item 주인)
        String looter = near.size() > 1 ? s.parties.looter(id, near, rng.nextDouble()) : null;
        if (looter != null) {
            UUID lu = UUID.fromString(looter);
            org.bukkit.Location at = e.getEntity().getLocation();
            List<ItemStack> drops = new ArrayList<>(e.getDrops());
            e.getDrops().clear();
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (ItemStack it : drops) at.getWorld().dropItemNaturally(at, it).setOwner(lu);
            });
            Player lp = Bukkit.getPlayer(lu);
            if (lp != null && !drops.isEmpty()) Ui.bar(lp, "&e전리품 &7(" + s.parties.loot(id).label.split(" ")[0] + ")");
        }
        async.fire("kill", () -> {
            s.growth.record(id, "kill.monster", 1);
            for (String m : near) {
                s.quests.record(m, io.versaera.domain.quest.QuestDefinition.Type.KILL, type, 1, 0);
                if (near.size() > 1) {   // 함께 싸운 몫: 각자 가장 높은 전투 숙련으로
                    String best = "swordsmanship";
                    int lv = -1;
                    for (String d : io.versaera.application.RaidService.COMBAT) {
                        int l = s.growth.level(m, d);
                        if (l > lv) { lv = l; best = d; }
                    }
                    s.growth.addXp(m, best, share, 1);
                }
            }
            return null;
        });
    }

    /** 5초마다 모아서 저장 (전투 중 매 타격 DB 쓰기를 피함) */
    private void flush() {
        Map<String, Long> hits = drain(pendingHits), xp = drain(pendingXp);
        Map<String, Integer> wear = drain(pendingWear);
        Map<String, String> xpDisc = new HashMap<>(pendingXpDiscipline), owners = new HashMap<>(wearOwner);
        if (hits.isEmpty() && xp.isEmpty() && wear.isEmpty()) return;
        async.fire("combat-flush", () -> {
            for (var h : hits.entrySet()) s.growth.record(h.getKey(), "hit_taken", h.getValue());
            for (var x : xp.entrySet()) {
                String uuid = x.getKey().substring(0, x.getKey().indexOf(':'));
                String d = xpDisc.get(x.getKey());
                var r = s.growth.addXp(uuid, d, 3 * x.getValue(), 1);
                weaponMastery.put(x.getKey(), r.after());
            }
            for (var w : wear.entrySet()) {
                String owner = owners.get(w.getKey());
                try {
                    ItemInstance it = s.items.wear(w.getKey(), owner, (w.getValue() + 9) / 10, false);   // 10번 칠 때마다 1
                    if (it.broken()) items.remove(w.getKey());
                } catch (io.versaera.domain.common.DomainException ex) {
                    items.remove(w.getKey());   // 그 사이 거래 · 파괴됨 → 캐시에서 지움 (다음 검사에서 InventoryGuard 가 처리)
                }
            }
            return null;
        });
    }

    /** 흡수 · 둔화 · 기절 · 속성 연출 */
    private void applyOptions(Player attacker, LivingEntity victim, io.versaera.domain.item.ItemOptions.Hit opt, double damage) {
        if (opt == io.versaera.domain.item.ItemOptions.Hit.NONE) return;
        if (opt.lifesteal() > 0) {
            var max = attacker.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            attacker.setHealth(Math.min(max == null ? 20 : max.getValue(), attacker.getHealth() + damage * opt.lifesteal()));
        }
        if (opt.slowChance() > 0 && rng.nextDouble() < opt.slowChance())
            victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 40, 1));
        if (opt.stunChance() > 0 && rng.nextDouble() < opt.stunChance())
            victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 20, 6));
        for (String el : opt.elements()) {
            switch (el) {
                case "fire" -> victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
                case "ice" -> victim.setFreezeTicks(Math.min(victim.getMaxFreezeTicks() + 40, victim.getFreezeTicks() + 60));
                case "poison" -> victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.POISON, 60, 0));
                case "dark" -> victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER, 40, 0));
                case "lightning" -> victim.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.1);
                case "holy" -> victim.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, victim.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
                default -> { }
            }
        }
    }

    private static <V> Map<String, V> drain(Map<String, V> m) {
        Map<String, V> out = new HashMap<>();
        for (String k : new ArrayList<>(m.keySet())) {
            V v = m.remove(k);
            if (v != null) out.put(k, v);
        }
        return out;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String prefix = e.getPlayer().getUniqueId() + ":";
        weaponMastery.keySet().removeIf(k -> k.startsWith(prefix));
        UUID u = e.getPlayer().getUniqueId();
        gearCtx.remove(u.toString());
        for (Map<UUID, ?> m : List.of(worn, itemHealth, warnedAt, whet, polish, destruction, twin, focus)) m.remove(u);
    }

    /** 처음 접속 시 무기 숙련 캐시 (DB 스레드에서 부름) */
    public void warm(String uuid) {
        gearCtx.put(uuid, s.gear.context(uuid));
        for (String d : List.of("swordsmanship", "spearmanship", "archery")) weaponMastery.put(uuid + ":" + d, Mastery.levelOf(s.growth.xp(uuid, d)));
        Map<String, Double> p = s.jobs.perks(uuid);
        // [3] = 힘 스탯 (수련관 허수아비 치기) → 근접 피해
        perks.put(uuid, new double[]{p.getOrDefault("attack_pct", 0.0), p.getOrDefault("defense_pct", 0.0), p.getOrDefault("crit", 0.0),
                io.versaera.domain.skill.StatEffects.of(s.growth.statPoints(uuid)).meleeDamagePct()});
    }

    /** 직업이 바뀌면 다시 읽는다 (DB 스레드) */
    public void forget(String uuid) {
        perks.remove(uuid);
    }
}
