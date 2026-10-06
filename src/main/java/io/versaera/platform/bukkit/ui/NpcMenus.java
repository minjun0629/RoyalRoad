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

    private record Home(NpcDefinition npc, int affinity, List<QuestDefinition> available, List<QuestService.Active> active, boolean shop,
                        Map<String, Long> forecast) {}

    public void open(Player p, String npcId) {
        String id = p.getUniqueId().toString();
        PlayerFacts f = facts.apply(p);
        async.run("npc-home", () -> {
            NpcDefinition n = s.relations.npc(npcId);
            List<QuestService.Active> mine = s.quests.active(id).stream().filter(a -> npcId.equals(a.def().giver())).toList();
            Map<String, Long> fc = new LinkedHashMap<>();
            s.worldEvents.forecastBy(npcId).forEach((d, t) -> fc.put(d.name(), t));
            return new Home(n, s.relations.affinity(id, npcId), s.quests.available(id, npcId, f), mine,
                    s.market.catalog().shops().containsKey(npcId), fc);
        }, h -> home(p, h), p);
    }

    private void home(Player p, Home h) {
        Menu m = new Menu(3, "&8" + h.npc().name());
        m.set(4, Menu.icon(Material.PLAYER_HEAD, "&f" + h.npc().name() + " &7" + h.npc().job(),
                List.of("&7" + Relation.tierName(h.affinity()) + " " + h.affinity())), null);
        int slot = 9;
        for (QuestService.Active a : h.active()) {
            if (slot > 17) break;
            m.set(slot++, questIcon(a.def(), a), e -> complete(p, a.def(), null));
        }
        for (QuestDefinition q : h.available()) {
            if (slot > 17) break;
            m.set(slot++, questIcon(q, null), e -> accept(p, q));
        }
        if (h.shop()) m.set(22, Menu.icon(Material.EMERALD, "&a상점", List.of()), e -> shop(p, h.npc().id()));
        if (!h.forecast().isEmpty()) {
            List<String> lines = new ArrayList<>();
            long now = System.currentTimeMillis();
            h.forecast().forEach((name, t) -> lines.add("&f" + name + " &7" + Math.max(1, Duration.ofMillis(t - now).toMinutes()) + "분 뒤"));
            m.set(20, Menu.icon(Material.CLOCK, "&e소식", lines), null);
        }
        m.set(24, Menu.icon(Material.POPPY, "&d선물", List.of("&7손에 든 재료 1개")), e -> gift(p, h.npc().id()));
        m.open(p);
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
        return Menu.icon(a == null ? Material.BOOK : Material.WRITABLE_BOOK, (a == null ? "&f" : "&a") + q.title(), lines);
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
        async.run("gift", () -> s.relations.gift(id, npcId, tags, q), gain -> {
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
                var qt = s.market.quote(id, sh.market(), o.typeId(), o.quality());
                prices.add(new long[]{qt.buy()});
            }
            return new Object[]{sh, prices, s.economy.balance(id)};
        }, r -> {
            MarketCatalog.Shop sh = (MarketCatalog.Shop) r[0];
            @SuppressWarnings("unchecked") List<long[]> prices = (List<long[]>) r[1];
            Menu m = new Menu(3, "&8" + s.market.catalog().market(sh.market()).name());
            m.set(4, Menu.icon(Material.GOLD_INGOT, "&e" + r[2], List.of()), null);
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
            m.set(22, Menu.icon(Material.HOPPER, "&a손에 든 것 팔기", List.of("&7" + String.join(" · ", sh.buys()))), e -> sell(p, npcId));
            m.open(p);
        }, p);
    }

    private void buy(Player p, String npcId, int idx, int amount) {
        String id = p.getUniqueId().toString(), req = UUID.randomUUID().toString();
        async.run("shop-buy", () -> s.market.buy(id, npcId, idx, amount, req), paid -> {
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
            async.run("shop-sell-u", () -> s.market.sellUnique(id, npcId, unique, req), got -> {
                p.sendMessage(Ui.info("+" + got + " 골드"));
                shop(p, npcId);
            }, err -> p.getInventory().addItem(hand), p);   // 실패: 서버 상태는 그대로(PLAYER) → 그 아이템을 다시 쥐여 준다
            return;
        }
        String type = codec.typeId(hand);
        if (type == null) { p.sendMessage(Ui.error("팔 물건을 손에 드세요")); return; }
        int q = codec.bulkQuality(hand), n = hand.getAmount();
        p.getInventory().setItemInMainHand(null);
        async.run("shop-sell", () -> s.market.sellBulk(id, npcId, type, q, n, req), got -> {
            p.sendMessage(Ui.info("+" + got + " 골드"));
            shop(p, npcId);
        }, err -> deliver.accept(p), p);   // 실패하면 서비스가 배달함으로 돌려준다
    }
}
