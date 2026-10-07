package io.versaera.platform.bukkit.ui;

import io.versaera.application.GameServices;
import io.versaera.application.QuestService;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.domain.market.MarketCatalog;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.Relation;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.InventoryOps;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * NPC 창: 의뢰 · 상점 · 예보 · 선물. 글은 짧게 — 아이콘 + 이름 + 숫자.
 * 모든 판단은 서비스(DB 스레드)가 하고, 이 창은 결과만 보여 준다.
 */
public final class NpcMenus {
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Consumer<Player> deliver;
    private final Function<Player, PlayerFacts> facts;

    public NpcMenus(GameServices s, Async async, ItemCodec codec, Consumer<Player> deliver, Function<Player, PlayerFacts> facts) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.deliver = deliver;
        this.facts = facts;
    }

    private io.versaera.platform.bukkit.command.AdventureCommands adventure;
    private java.util.function.Function<UUID, String> regionOf = u -> null;

    /** 마구간 · 마차 · 배 창 (V7) */
    public void adventure(io.versaera.platform.bukkit.command.AdventureCommands a, java.util.function.Function<UUID, String> regionOf) {
        this.adventure = a;
        this.regionOf = regionOf;
    }

    private record Home(NpcDefinition npc, io.versaera.domain.npc.Relation.Stage stage, int affinity, List<QuestDefinition> available,
                        List<QuestService.Active> active, boolean shop, Map<String, Long> forecast, Set<String> services, List<String> info,
                        List<String> people) {}

    private static final Map<String, String> LINK = Map.ofEntries(Map.entry("SPOUSE", "배우자"), Map.entry("PARENT", "부모"),
            Map.entry("CHILD", "자식"), Map.entry("SIBLING", "형제"), Map.entry("PARTNER", "거래처"), Map.entry("MASTER", "스승"),
            Map.entry("APPRENTICE", "제자"), Map.entry("RIVAL", "경쟁자"), Map.entry("LORD", "주군"), Map.entry("VASSAL", "가신"));

    public void open(Player p, String npcId) {
        String id = p.getUniqueId().toString();
        PlayerFacts f = facts.apply(p);
        async.run("npc-home", () -> {
            NpcDefinition n = s.relations.npc(npcId);
            // 악명 · 살인자 (REP-01): 보통 NPC 는 상대하지 않고, 악한 NPC 는 악명 높은 사람만 상대한다
            var st = s.reputation.standing(id);
            if (io.versaera.domain.reputation.Reputation.npcRefuses(n.evil(), st.notoriety(), st.murderer()))
                throw io.versaera.domain.common.DomainException.of("npc.refuses", n.evil()
                        ? n.name() + ": 모르는 얼굴과는 거래하지 않는다"
                        : n.name() + ": " + (st.murderer() ? "살인자와는 상대하지 않는다" : "악명 높은 사람과는 거래하지 않는다"));
            List<QuestService.Active> mine = s.quests.active(id).stream().filter(a -> npcId.equals(a.def().giver())).toList();
            Map<String, Long> fc = new LinkedHashMap<>();
            s.worldEvents.forecastBy(npcId).forEach((d, t) -> fc.put(d.name(), t));
            var stage = s.npcWorld.stage(id, npcId);
            var arch = s.npcWorld.archetypeOf(npcId);
            Set<String> services = arch == null ? Set.of() : Set.copyOf(arch.services());
            List<String> info = new ArrayList<>();
            List<String> people = new ArrayList<>();
            s.npcWorld.profile(npcId).ifPresent(pr -> {
                info.add("&7Lv." + pr.level() + (pr.family() != null ? " &8· &7" + pr.family() + "씨 집안" : ""));
                if (pr.wanderer()) {
                    String town = s.npcWorld.wandererTown(npcId);
                    info.add("&7떠돌이 &8· &7" + (town == null ? "길 위" : s.regions.byId(town).name()));
                }
                for (var l : pr.links()) {
                    NpcDefinition o = s.relations.npc(l.npc());
                    people.add("&7" + LINK.getOrDefault(l.type(), l.type()) + " &f" + o.name() + " &8" + o.job()
                            + (o.region().equals(n.region()) ? "" : " · " + s.regions.byId(o.region()).name()));
                }
            });
            info.add("&7마을 " + s.npcWorld.tier(n.region()).label);
            for (var mem : s.npcWorld.memories(id, npcId, 3)) info.add("&8기억: " + mem.kind().toLowerCase(Locale.ROOT) + " " + mem.detail());
            return new Home(n, stage, s.relations.affinity(id, npcId), s.quests.available(id, npcId, f), mine,
                    s.market.catalog().shops().containsKey(npcId), fc, services, info, people);
        }, h -> home(p, h), p);
    }

    private void home(Player p, Home h) {
        Menu m = new Menu(3, "&8" + h.npc().name());
        List<String> head = new ArrayList<>();
        head.add("&7" + h.stage().label + " " + h.affinity());
        head.addAll(h.info());
        m.set(4, Menu.icon(Material.PLAYER_HEAD, "&f" + h.npc().name() + " &7" + h.npc().job(), head), null);
        int slot = 9;
        for (QuestService.Active a : h.active()) {
            if (slot > 17) break;
            m.set(slot++, questIcon(a.def(), a), e -> complete(p, a.def(), null));
        }
        for (QuestDefinition q : h.available()) {
            if (slot > 17) break;
            m.set(slot++, questIcon(q, null), e -> accept(p, q));
        }
        String npc = h.npc().id();
        Set<String> sv = h.services();
        if (sv.contains("RUMOR") || sv.contains("LORE"))
            m.set(18, Menu.ui("rumor", Material.MAP, "&b소문", List.of(need(h, Relation.Stage.INTEREST), "&7가 보지 못한 곳 하나")), e -> rumor(p, npc));
        if (sv.contains("TRAIN"))
            m.set(19, Menu.ui("train", Material.ENCHANTED_BOOK, "&e지도", List.of(need(h, Relation.Stage.FRIENDLY), "&7하루 한 번 · 숙련 경험")), e -> train(p, npc));
        if (!h.forecast().isEmpty()) {
            List<String> lines = new ArrayList<>();
            long now = System.currentTimeMillis();
            h.forecast().forEach((name, t) -> lines.add("&f" + name + " &7" + Math.max(1, Duration.ofMillis(t - now).toMinutes()) + "분 뒤"));
            m.set(20, Menu.ui("news", Material.CLOCK, "&e소식", lines), null);
        }
        if (sv.contains("INN")) m.set(21, Menu.ui("inn", Material.RED_BED, "&f쉼", List.of("&7체력 · 배고픔 회복 · 휴식")), e -> paid(p, npc, "INN"));
        else if (sv.contains("HEAL")) m.set(21, Menu.ui("heal", Material.POTION, "&f치유", List.of("&7체력 회복 · 해로운 효과 제거")), e -> paid(p, npc, "HEAL"));
        if (h.shop()) m.set(22, Menu.ui("shop", Material.EMERALD, "&a상점",
                h.stage().discount() > 0 ? List.of("&e할인 " + Math.round(h.stage().discount() * 100) + "%") : List.of()), e -> shop(p, npc));
        if (sv.contains("REPAIR")) m.set(23, Menu.ui("repair", Material.ANVIL, "&f수리", List.of("&7손에 든 장비")), e -> repair(p, npc));
        m.set(24, Menu.ui("gift", Material.POPPY, "&d선물", List.of("&7손에 든 재료 1개")), e -> gift(p, npc));
        if (sv.contains("SONG")) m.set(25, Menu.ui("song", Material.NOTE_BLOCK, "&d노래", List.of("&7하루 한 번")), e -> song(p, npc));
        if (adventure != null) {
            if (sv.contains("STABLE")) m.set(0, Menu.ui("mount", Material.SADDLE, "&6마구간", List.of("&7탈것 사기")), e -> adventure.stable(p, npc));
            if (sv.contains("CARRIAGE")) m.set(8, Menu.ui("carriage", Material.MINECART, "&f마차", List.of("&7이 도시에서 떠나는 마차")),
                    e -> adventure.routes(p, npc, regionOf.apply(p.getUniqueId()), false));
            if (sv.contains("SHIP")) m.set(8, Menu.ui("ship", Material.OAK_BOAT, "&b배", List.of("&7이 항구에서 떠나는 배 · 폭풍엔 뜨지 않음")),
                    e -> adventure.routes(p, npc, regionOf.apply(p.getUniqueId()), true));
        }
        if (!h.people().isEmpty()) m.set(26, Menu.ui("people", Material.BOOK, "&f아는 사람들", h.people().subList(0, Math.min(12, h.people().size()))), null);
        m.open(p);
    }

    private static String need(Home h, Relation.Stage min) {
        return h.stage().atLeast(min) ? "&a열림" : "&c'" + min.label + "' 이상";
    }

    // ------------------------------------------------------------------ NPC 의 일
    private void rumor(Player p, String npcId) {
        String id = p.getUniqueId().toString();
        async.run("npc-rumor", () -> s.npcWorld.rumor(id, npcId), r -> {
            p.closeInventory();
            p.sendMessage(Ui.c("&b소문 &7" + r.regionName() + " &8— &7" + r.direction() + " 쪽 " + r.distance() + " 블록"));
        }, p);
    }

    private void train(Player p, String npcId) {
        String id = p.getUniqueId().toString();
        async.run("npc-train", () -> s.npcWorld.train(id, npcId), l -> {
            p.closeInventory();
            p.sendMessage(Ui.c("&e지도 &7" + s.growth.discipline(l.discipline()).name() + " &a+" + l.xp() + " &8(-" + l.cost() + " 골드)"));
        }, p);
    }

    private void paid(Player p, String npcId, String service) {
        String id = p.getUniqueId().toString(), req = UUID.randomUUID().toString();
        async.run("npc-" + service, () -> s.npcWorld.paidService(id, npcId, service, req), cost -> {
            p.closeInventory();
            var max = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            p.setHealth(max == null ? 20 : max.getValue());
            if (service.equals("INN")) {
                p.setFoodLevel(20);
                p.setSaturation(10);
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 20 * 60, 0));
            } else {
                for (var t : List.of(org.bukkit.potion.PotionEffectType.POISON, org.bukkit.potion.PotionEffectType.WITHER,
                        org.bukkit.potion.PotionEffectType.WEAKNESS, org.bukkit.potion.PotionEffectType.SLOW, org.bukkit.potion.PotionEffectType.BLINDNESS,
                        org.bukkit.potion.PotionEffectType.HUNGER, org.bukkit.potion.PotionEffectType.CONFUSION))
                    p.removePotionEffect(t);
            }
            p.sendMessage(Ui.info((service.equals("INN") ? "푹 쉬었다" : "상처가 아물었다") + " (-" + cost + " 골드)"));
        }, p);
    }

    private void song(Player p, String npcId) {
        String id = p.getUniqueId().toString();
        async.run("npc-song", () -> s.npcWorld.song(id, npcId), first -> {
            p.closeInventory();
            if (!first) { p.sendMessage(Ui.c("&7오늘은 이미 들었다")); return; }
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.LUCK, 20 * 600, 0));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 20 * 600, 0));
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 1.2f);
            p.sendMessage(Ui.c("&d노래 &7발걸음이 가벼워졌다 (10분)"));
        }, p);
    }

    private void repair(Player p, String npcId) {
        String iid = codec.instanceId(p.getInventory().getItemInMainHand());
        if (iid == null) { p.sendMessage(Ui.error("고칠 장비를 손에 드세요")); return; }
        String id = p.getUniqueId().toString(), req = UUID.randomUUID().toString();
        async.run("npc-repair", () -> {
            var r = s.npcWorld.repair(id, npcId, iid, req);
            return new Object[]{r, s.items.find(iid).orElseThrow()};
        }, res -> {
            var r = (io.versaera.domain.item.Repair.Result) res[0];
            var it = (io.versaera.domain.item.ItemInstance) res[1];
            int slot = p.getInventory().getHeldItemSlot();
            if (iid.equals(codec.instanceId(p.getInventory().getItem(slot)))) p.getInventory().setItem(slot, codec.unique(it));
            p.sendMessage(Ui.info("수리 " + it.durability() + "/" + it.maxDurability() + (r.maxAfter() < r.maxBefore() ? "  &c최대 -" + (r.maxBefore() - r.maxAfter()) : "")));
        }, p);
    }

    private ItemStack questIcon(QuestDefinition q, QuestService.Active a) {
        List<String> lines = new ArrayList<>();
        lines.add("&7" + switch (q.grade()) {
            case DAILY -> "일상";
            case SKILLED -> "숙련";
            case PERILOUS -> "험로";
            case LEGEND -> "전설";
        });
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestDefinition.Objective o = q.objectives().get(i);
            String prog = a == null ? "" : o.type() == QuestDefinition.Type.DELIVER ? "" : " &f" + Math.min(o.amount(), a.progress().get(i)) + "/" + o.amount();
            lines.add("&8· &7" + o.label() + prog);
        }
        if (q.reward().money() > 0) lines.add("&e" + q.reward().money() + " 골드");
        return Menu.ui(a == null ? "quest" : "quest_active", a == null ? Material.BOOK : Material.WRITABLE_BOOK, (a == null ? "&f" : "&a") + q.title(), lines);
    }

    private void accept(Player p, QuestDefinition q) {
        String id = p.getUniqueId().toString();
        PlayerFacts f = facts.apply(p);
        async.run("quest-accept", () -> { s.quests.accept(id, q.id(), f); return null; }, v -> {
            p.closeInventory();
            p.sendMessage(Ui.info("의뢰: " + q.title()));
        }, p);
    }

    private void complete(Player p, QuestDefinition q, String choice) {
        if (!q.choices().isEmpty() && choice == null) {
            Menu m = new Menu(1, "&8" + q.title());
            int slot = 0;
            for (QuestDefinition.Choice c : q.choices())
                m.set(slot++ * 2 + 2, Menu.icon(Material.PAPER, "&f" + c.label(), List.of()), e -> complete(p, q, c.id()));
            m.open(p);
            return;
        }
        // 납품: 먼저 인벤토리에서 뺀다 (모자라면 아무것도 빼지 않음). 실패하면 서비스가 배달함으로 돌려준다
        List<MaterialInput> delivered = new ArrayList<>();
        for (QuestDefinition.Objective o : q.objectives()) {
            if (o.type() != QuestDefinition.Type.DELIVER) continue;
            if (InventoryOps.count(p, codec, o.target(), o.minQuality()) < o.amount()) {
                InventoryOps.give(p, codec, delivered);
                p.sendMessage(Ui.error("납품할 물건이 모자랍니다: " + o.label()));
                return;
            }
            delivered.addAll(InventoryOps.take(p, codec, o.target(), o.amount(), o.minQuality()));
        }
        String id = p.getUniqueId().toString(), name = p.getName();
        async.run("quest-complete", () -> s.quests.complete(id, name, q.id(), choice, delivered), r -> {
            p.closeInventory();
            p.sendTitle(Ui.c("&6" + q.title()), Ui.c("&7완료"), 5, 40, 10);
            deliver.accept(p);
        }, err -> deliver.accept(p), p);
    }

    private void gift(Player p, String npcId) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        String type = codec.typeId(hand);
        if (type == null || codec.instanceId(hand) != null) { p.sendMessage(Ui.error("재료를 손에 들어야 합니다")); return; }
        int q = codec.bulkQuality(hand);
        List<MaterialInput> taken = InventoryOps.take(p, codec, type, 1, 0);
        if (taken == null) return;
        String id = p.getUniqueId().toString();
        Set<String> tags = codec.types().get(type).tags();
        String name = codec.types().get(type).name();
        async.run("gift", () -> s.npcWorld.gift(id, npcId, tags, q, name), gain -> {
            p.closeInventory();
            p.sendMessage(Ui.c(gain > 0 ? "&a호감 +" + gain : gain < 0 ? "&c호감 " + gain : "&7반응 없음"));
        }, err -> InventoryOps.give(p, codec, taken), p);
    }

    // ------------------------------------------------------------------ 상점
    public void shop(Player p, String npcId) {
        String id = p.getUniqueId().toString();
        async.run("shop", () -> {
            MarketCatalog.Shop sh = s.market.shop(npcId);
            List<long[]> prices = new ArrayList<>();
            for (MarketCatalog.Offer o : sh.sells()) {
                var qt = s.market.quoteAt(id, npcId, o.typeId(), o.quality());
                prices.add(new long[]{qt.buy()});
            }
            return new Object[]{sh, prices, s.economy.balance(id)};
        }, r -> {
            MarketCatalog.Shop sh = (MarketCatalog.Shop) r[0];
            @SuppressWarnings("unchecked") List<long[]> prices = (List<long[]>) r[1];
            Menu m = new Menu(3, "&8" + s.market.catalog().market(sh.market()).name());
            m.set(4, Menu.ui("money", Material.GOLD_INGOT, "&e" + r[2], List.of()), null);
            for (int i = 0; i < sh.sells().size() && i < 9; i++) {
                MarketCatalog.Offer o = sh.sells().get(i);
                int idx = i;
                boolean unique = codec.types().get(o.typeId()).category().unique();
                ItemStack icon = codec.bulk(o.typeId(), o.quality(), 1);
                var meta = icon.getItemMeta();
                meta.setLore(List.of(Ui.c("&e" + prices.get(i)[0]), Ui.c(unique ? "&8클릭: 1개" : "&8클릭: 1개 · 쉬프트: 16개")));
                icon.setItemMeta(meta);
                m.set(9 + i, icon, e -> buy(p, npcId, idx, !unique && e.isShiftClick() ? 16 : 1));
            }
            m.set(22, Menu.ui("sell", Material.HOPPER, "&a손에 든 것 팔기", List.of("&7" + String.join(" · ", sh.buys()))), e -> sell(p, npcId));
            m.open(p);
        }, p);
    }

    /** 거래가 끝난 뒤 (DB 스레드): 단골 기억 · 지역 번영. 이미 끝난 거래는 되돌리지 않는다 */
    private long traded(String uuid, String npcId, long amount, String req) {
        try {
            s.npcWorld.traded(uuid, npcId, amount, req);
        } catch (RuntimeException ex) {
            java.util.logging.Logger.getLogger("VersaEra").warning("npc trade memory: " + ex);
        }
        return amount;
    }

    private void buy(Player p, String npcId, int idx, int amount) {
        String id = p.getUniqueId().toString(), req = UUID.randomUUID().toString();
        async.run("shop-buy", () -> traded(id, npcId, s.market.buy(id, npcId, idx, amount, req), req), paid -> {
            p.sendMessage(Ui.info("-" + paid + " 골드"));
            deliver.accept(p);
            shop(p, npcId);
        }, p);
    }

    private void sell(Player p, String npcId) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        String id = p.getUniqueId().toString(), req = UUID.randomUUID().toString();
        String unique = codec.instanceId(hand);
        if (unique != null) {
            p.getInventory().setItemInMainHand(null);
            async.run("shop-sell-u", () -> traded(id, npcId, s.market.sellUnique(id, npcId, unique, req), req), got -> {
                p.sendMessage(Ui.info("+" + got + " 골드"));
                shop(p, npcId);
            }, err -> p.getInventory().addItem(hand), p);   // 실패: 서버 상태는 그대로(PLAYER) → 그 아이템을 다시 쥐여 준다
            return;
        }
        String type = codec.typeId(hand);
        if (type == null) { p.sendMessage(Ui.error("팔 물건을 손에 드세요")); return; }
        int q = codec.bulkQuality(hand), n = hand.getAmount();
        p.getInventory().setItemInMainHand(null);
        async.run("shop-sell", () -> traded(id, npcId, s.market.sellBulk(id, npcId, type, q, n, req), req), got -> {
            p.sendMessage(Ui.info("+" + got + " 골드"));
            shop(p, npcId);
        }, err -> deliver.accept(p), p);   // 실패하면 서비스가 배달함으로 돌려준다
    }
}
