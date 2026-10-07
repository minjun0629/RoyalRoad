package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.crafting.CraftPlan;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.crafting.MaterialSlot;
import io.versaera.domain.crafting.Recipe;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.skill.Mastery;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.ui.Menu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 제작대: 월드의 블록을 우클릭해 그 분야의 제작 창을 연다 (명령어 없이). 고르면 제작대 곁에서 시간을 들여 손으로 만든다 —
 * 모루는 망치질 불똥, 화덕은 지글지글 연기, 베틀은 북 소리, 양조기는 끓는 거품. 떠나면 멈추고 재료는 돌려받는다.
 * 명품 이상이 나오면 큰 제목, 걸작은 서버 전체에 알려진다.
 * 모루=대장 · 베틀=재봉 · 제작대=가죽 · 훈연기=요리 · 양조기=연금 · 석재 절단기=조각 · 대장장이 작업대=수리.
 * 재료는 인벤토리에서 품질이 높은 것부터 골라 <b>먼저 빼고</b> 서버에 제작을 요청한다. 실패하면 재료는 배달함으로 돌아온다.
 */
public final class StationListener implements Listener {
    private static final Map<Material, String> STATIONS = Map.of(
            Material.ANVIL, "smithing", Material.LOOM, "tailoring", Material.CRAFTING_TABLE, "leatherwork",
            Material.SMOKER, "cooking", Material.BREWING_STAND, "alchemy", Material.STONECUTTER, "sculpting",
            Material.SMITHING_TABLE, "repair");

    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final SessionListener sessions;
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Work> works = new HashMap<>();
    private final Random rng = new Random();

    /** 만드는 중: 제작대 자리 · 진행 */
    private static final class Work {
        Recipe recipe;
        String discipline;
        Location station, anchor;
        List<MaterialInput> used;
        int ticks, total;
    }

    public StationListener(org.bukkit.plugin.Plugin plugin, GameServices s, Async async, ItemCodec codec, SessionListener sessions) {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 5L);
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        String d = STATIONS.get(e.getClickedBlock().getType());
        if (d == null || e.getPlayer().isSneaking()) return;
        e.setCancelled(true);
        if (d.equals("repair")) repair(e.getPlayer());
        else open(e.getPlayer(), d, e.getClickedBlock().getLocation().add(0.5, 1.0, 0.5));
    }

    private void open(Player p, String discipline, Location station) {
        String id = p.getUniqueId().toString();
        async.run("station", () -> {
            int lv = s.growth.level(id, discipline);
            List<Recipe> known = new ArrayList<>();
            for (Recipe r : s.crafting.all()) if (r.discipline().equals(discipline) && s.crafting.knows(id, r)) known.add(r);
            return new Object[]{lv, known};
        }, res -> {
            int lv = (int) res[0];
            @SuppressWarnings("unchecked") List<Recipe> known = (List<Recipe>) res[1];
            Menu m = new Menu(Math.max(1, Math.min(6, (known.size() + 9) / 9 + 1)), "&8" + s.growth.discipline(discipline).name() + " · " + Mastery.label(lv));
            int slot = 0;
            for (Recipe r : known) {
                ItemType out = codec.types().get(r.output());
                List<String> lines = new ArrayList<>();
                for (MaterialSlot ms : r.slots()) lines.add((ms.optional() ? "&8+ " : "&7· ") + slotLabel(ms) + " ×" + ms.count());
                lines.add(lv >= r.minLevel() ? "&f" + Mastery.label(r.minLevel()) : "&c" + Mastery.label(r.minLevel()));
                Material icon = Material.matchMaterial(out.material());
                m.set(slot++, Menu.icon(icon == null ? Material.PAPER : icon, "&f" + r.name(), lines), ev -> {
                    p.closeInventory();
                    craft(p, r, station, lv);
                });
            }
            m.open(p);
        }, p);
    }

    private String slotLabel(MaterialSlot ms) {
        if (ms.accepts().startsWith("type:")) return codec.types().get(ms.accepts().substring(5)).name();
        return switch (ms.accepts().substring(4)) {
            case "wood" -> "목재";
            case "metal" -> "금속";
            case "mineral" -> "광물";
            case "gem" -> "보석";
            case "dye" -> "염료";
            case "stone" -> "석재";
            case "marble" -> "대리석";
            case "cloth" -> "천";
            case "leather" -> "가죽";
            case "fiber" -> "섬유";
            case "fish" -> "생선";
            case "grain" -> "곡물";
            case "herb" -> "약초";
            case "seasoning" -> "양념";
            default -> ms.accepts().substring(4);
        };
    }

    /** 인벤토리의 묶음 재료를 MaterialInput 으로 (같은 종류 · 같은 품질끼리) */
    private Map<String, int[]> stock(Player p) {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (codec.instanceId(it) != null) continue;
            String t = codec.typeId(it);
            if (t == null) continue;
            int q = codec.bulkQuality(it);
            m.computeIfAbsent(t + "@" + q, k -> new int[]{q, 0})[1] += it.getAmount();
        }
        return m;
    }

    private String toolId(Player p, String tag) {
        if (tag == null) return null;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            String t = codec.typeId(it), iid = codec.instanceId(it);
            if (t != null && iid != null && codec.types().get(t).hasTag(tag)) return iid;
        }
        return null;
    }

    private void craft(Player p, Recipe r, Location station, int lv) {
        if (!busy.add(p.getUniqueId())) return;   // 연타 방지: 한 사람당 제작 하나씩
        List<MaterialInput> pool = new ArrayList<>();
        for (Map.Entry<String, int[]> e : stock(p).entrySet()) {
            String type = e.getKey().substring(0, e.getKey().indexOf('@'));
            pool.add(new MaterialInput(type, codec.types().get(type).tags(), e.getValue()[0], e.getValue()[1]));
        }
        List<CraftPlan.Assignment> plan;
        try {
            plan = CraftPlan.assign(r, pool);
        } catch (io.versaera.domain.common.DomainException ex) {
            busy.remove(p.getUniqueId());
            p.sendMessage(Ui.error(ex.getMessage()));
            return;
        }
        if (r.tool() != null && toolId(p, r.tool()) == null) {
            busy.remove(p.getUniqueId());
            p.sendMessage(Ui.error("도구가 필요합니다"));
            return;
        }
        // 재료를 먼저 뺀다 (실패하면 서비스가 배달함으로 돌려준다)
        List<MaterialInput> used = new ArrayList<>();
        for (CraftPlan.Assignment a : plan) {
            int need = a.slot().count();
            ItemStack[] c = p.getInventory().getStorageContents();
            for (int i = 0; i < c.length && need > 0; i++) {
                if (c[i] == null || codec.instanceId(c[i]) != null || !a.input().typeId().equals(codec.typeId(c[i])) || codec.bulkQuality(c[i]) != a.input().quality()) continue;
                int take = Math.min(need, c[i].getAmount());
                c[i].setAmount(c[i].getAmount() - take);
                if (c[i].getAmount() <= 0) c[i] = null;
                need -= take;
            }
            p.getInventory().setStorageContents(c);
            if (need > 0) {   // 그 사이 인벤토리가 바뀜 → 뺀 만큼 되돌리고 중단
                for (MaterialInput u : used) p.getInventory().addItem(codec.bulk(u.typeId(), u.quality(), u.count()));
                int got = a.slot().count() - need;
                if (got > 0) p.getInventory().addItem(codec.bulk(a.input().typeId(), a.input().quality(), got));
                busy.remove(p.getUniqueId());
                p.sendMessage(Ui.error("재료가 바뀌었습니다. 다시 시도하세요"));
                return;
            }
            used.add(new MaterialInput(a.input().typeId(), a.input().tags(), a.input().quality(), a.slot().count()));
        }
        // 손으로 만드는 시간: 제작대 곁에 머문다 (tick 이 끝나면 complete)
        Work w = new Work();
        w.recipe = r;
        w.discipline = r.discipline();
        w.station = station;
        w.anchor = p.getLocation();
        w.used = used;
        w.total = (int) Math.round(io.versaera.domain.craft.WorkTime.craft(r.minLevel(), lv) * 20);
        works.put(p.getUniqueId(), w);
    }

    // ------------------------------------------------------------------ 만드는 동안
    private void tick() {
        for (Iterator<Map.Entry<UUID, Work>> it = works.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Work w = en.getValue();
            Player p = org.bukkit.Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || p.isDead()) {   // 나감: 재료는 배달함으로
                it.remove();
                busy.remove(en.getKey());
                String owner = en.getKey().toString();
                for (MaterialInput u : w.used) async.fire("craft-refund", () -> { s.items.deliverBulk(owner, u.typeId(), u.quality(), u.count(), "craft_refund"); return null; });
                continue;
            }
            if (!p.getWorld().equals(w.station.getWorld()) || p.getLocation().distanceSquared(w.station) > 16 || p.getLocation().distanceSquared(w.anchor) > 4) {
                it.remove();
                busy.remove(en.getKey());
                for (MaterialInput u : w.used) p.getInventory().addItem(codec.bulk(u.typeId(), u.quality(), u.count()));
                Ui.bar(p, "&c손을 멈췄다 — 재료는 돌려받았다");
                continue;
            }
            w.ticks += 5;
            effects(w);
            double f = Math.min(1, w.ticks / (double) w.total);
            int n = (int) Math.round(f * 20);
            Ui.bar(p, "&e" + w.recipe.name() + " &e" + "|".repeat(n) + "&8" + "|".repeat(20 - n));
            if (w.ticks >= w.total) {
                it.remove();
                complete(p, w.recipe, w.used, w.station);
            }
        }
    }

    /** 분야마다 다른 손길: 소리 · 불똥 · 연기 · 거품 */
    private void effects(Work w) {
        org.bukkit.World world = w.station.getWorld();
        Location at = w.station;
        boolean beat = w.ticks % 10 == 0;
        switch (w.discipline) {
            case "smithing" -> {
                if (beat) world.playSound(at, org.bukkit.Sound.BLOCK_ANVIL_USE, 0.6f, 1.1f + rng.nextFloat() * 0.5f);
                world.spawnParticle(org.bukkit.Particle.LAVA, at, beat ? 2 : 0, 0.15, 0.05, 0.15, 0);
                world.spawnParticle(org.bukkit.Particle.CRIT, at, 6, 0.2, 0.1, 0.2, 0.25);
            }
            case "cooking" -> {
                if (beat) world.playSound(at, org.bukkit.Sound.BLOCK_CAMPFIRE_CRACKLE, 1f, 1f);
                world.spawnParticle(org.bukkit.Particle.SMOKE_NORMAL, at, 4, 0.2, 0.05, 0.2, 0.01);
                world.spawnParticle(org.bukkit.Particle.FLAME, at, 1, 0.15, 0.02, 0.15, 0);
            }
            case "tailoring" -> {
                if (beat) world.playSound(at, org.bukkit.Sound.UI_LOOM_SELECT_PATTERN, 0.8f, 0.9f + rng.nextFloat() * 0.3f);
                world.spawnParticle(org.bukkit.Particle.CLOUD, at, 1, 0.2, 0.05, 0.2, 0);
            }
            case "leatherwork" -> {
                if (beat) world.playSound(at, org.bukkit.Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 0.9f + rng.nextFloat() * 0.3f);
                world.spawnParticle(org.bukkit.Particle.CRIT, at, 3, 0.2, 0.05, 0.2, 0.05);
            }
            case "alchemy" -> {
                if (beat) world.playSound(at, org.bukkit.Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1f);
                world.spawnParticle(org.bukkit.Particle.SPELL_WITCH, at, 4, 0.2, 0.1, 0.2, 0);
                world.spawnParticle(org.bukkit.Particle.BUBBLE_POP, at, 2, 0.15, 0.05, 0.15, 0);
            }
            case "sculpting" -> {
                if (beat) world.playSound(at, org.bukkit.Sound.BLOCK_STONE_HIT, 0.8f, 0.8f + rng.nextFloat() * 0.4f);
                world.spawnParticle(org.bukkit.Particle.BLOCK_CRACK, at, 6, 0.2, 0.1, 0.2, Material.STONE.createBlockData());
            }
            default -> {
                if (beat) world.playSound(at, org.bukkit.Sound.BLOCK_WOOD_HIT, 0.8f, 1f);
                world.spawnParticle(org.bukkit.Particle.CRIT, at, 3, 0.2, 0.05, 0.2, 0.05);
            }
        }
    }

    /** 다 만듦: 서버에 제작 요청 → 품질에 따라 드러낸다 */
    private void complete(Player p, Recipe r, List<MaterialInput> used, Location station) {
        String id = p.getUniqueId().toString(), name = p.getName(), tool = toolId(p, r.tool());
        async.run("craft", () -> {
            int tq = -1;
            if (tool != null) {
                ItemInstance t = s.items.find(tool).filter(x -> x.custody().ownedBy(id)).orElse(null);
                if (t != null && !t.broken()) {
                    // 도구 능력 '제작 품질' (자하브의 조각칼 …) — 착용 조건을 채웠을 때만
                    var tt = s.items.types().get(t.typeId());
                    int bonus = io.versaera.application.GearService.unmet(tt, s.gear.context(id)).isEmpty() ? tt.stats().getOrDefault("craft", 0) * 10 : 0;
                    tq = Math.min(1000, t.quality() + bonus);
                    s.items.wear(tool, id, 1, false);
                }
            }
            return s.crafting.craft(id, name, r.id(), used, tq, null, new SplittableRandom(), null);
        }, res -> {
            busy.remove(p.getUniqueId());
            reveal(p, r, res.quality(), station);
            if (res.xp() > 0) Ui.bar(p, "&7" + s.growth.discipline(r.discipline()).name() + " +" + res.xp());
            sessions.deliver(p);
        }, err -> {
            busy.remove(p.getUniqueId());
            sessions.deliver(p);   // 실패 → 재료가 배달함으로 돌아왔으니 바로 돌려줌
        }, p);
    }

    /** 품질 등급에 따라: 보통은 짧게, 명품은 큰 제목, 걸작은 서버 전체에 */
    private void reveal(Player p, Recipe r, int quality, Location at) {
        int g = io.versaera.domain.item.Quality.grade(quality);
        String grade = io.versaera.domain.item.Quality.gradeName(quality);
        org.bukkit.World w = at.getWorld();
        if (g >= 5) {
            p.sendTitle(Ui.c("&6&l걸작!"), Ui.c("&f" + r.name()), 5, 80, 20);
            w.playSound(at, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.8f);
            w.spawnParticle(org.bukkit.Particle.END_ROD, at, 120, 0.6, 0.8, 0.6, 0.08);
            org.bukkit.Bukkit.broadcastMessage(Ui.c("&6[걸작] &f" + p.getName() + " &7— " + r.name()));
        } else if (g == 4) {
            p.sendTitle(Ui.c("&d&l명품!"), Ui.c("&f" + r.name()), 5, 60, 15);
            w.playSound(at, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            w.spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, at, 40, 0.4, 0.5, 0.4, 0.06);
        } else {
            w.playSound(at, g >= 2 ? org.bukkit.Sound.ENTITY_PLAYER_LEVELUP : org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            p.sendMessage(Ui.c(Ui.gradeColor(quality) + grade + " &f" + r.name() + " &7(" + quality / 10 + ")"));
        }
    }

    /** 서버 종료: 만들던 재료를 돌려준다 */
    public void shutdown() {
        for (var en : works.entrySet()) {
            Player p = org.bukkit.Bukkit.getPlayer(en.getKey());
            if (p != null) for (MaterialInput u : en.getValue().used) p.getInventory().addItem(codec.bulk(u.typeId(), u.quality(), u.count()));
        }
        works.clear();
    }

    private void repair(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        String iid = codec.instanceId(hand);
        if (iid == null) {
            p.sendMessage(Ui.error("고칠 장비를 손에 드세요"));
            return;
        }
        String id = p.getUniqueId().toString();
        async.run("repair", () -> {
            int lv = s.growth.level(id, "repair");
            var r = s.items.repair(iid, id, id, lv, null);
            s.growth.addXp(id, "repair", 8, Math.max(1, lv));
            s.growth.record(id, "repair.count", 1);
            return new Object[]{r, s.items.find(iid).orElseThrow()};
        }, res -> {
            var r = (io.versaera.domain.item.Repair.Result) res[0];
            ItemInstance it = (ItemInstance) res[1];
            int slot = p.getInventory().getHeldItemSlot();
            if (iid.equals(codec.instanceId(p.getInventory().getItem(slot)))) p.getInventory().setItem(slot, codec.unique(it));
            p.sendMessage(Ui.info("수리 " + it.durability() + "/" + it.maxDurability() + (r.maxAfter() < r.maxBefore() ? "  &c최대 -" + (r.maxBefore() - r.maxAfter()) : "")));
        }, p);
    }
}
