package io.versaera.platform.bukkit.command;

import io.versaera.application.GameServices;
import io.versaera.application.GuildService;
import io.versaera.application.RealmService;
import io.versaera.application.port.RealmRepository;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.world.RealmRuntime;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** /땅 · /상점 · /성 · /국가 · /황제 (LND-01 · SHP-01 · CST-01 · NAT-01) */
public final class RealmCommands implements CommandExecutor {
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final RealmRuntime realm;

    public RealmCommands(GameServices s, Async async, ItemCodec codec, RealmRuntime realm) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.realm = realm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("플레이어만");
            return true;
        }
        String sub = a.length == 0 ? "" : a[0];
        switch (cmd.getName()) {
            case "land" -> land(p, sub, a);
            case "pshop" -> shop(p, sub, a);
            case "castle" -> castle(p, sub, a);
            case "nation" -> nation(p, sub, a);
            case "emperor" -> async.run("emperor", () -> new Object[]{s.realm.emperor(), s.realm.capitals()}, r -> {
                @SuppressWarnings("unchecked") var e = (Optional<RealmRepository.Emperor>) r[0];
                if (e.isPresent()) {
                    OfflinePlayer o = Bukkit.getOfflinePlayer(java.util.UUID.fromString(e.get().leader()));
                    p.sendMessage(Ui.c("&6베르사의 첫 황제: &f" + (o.getName() == null ? "?" : o.getName())));
                } else p.sendMessage(Ui.c("&7아직 황제가 없습니다 — 수도 6곳(" + String.join(", ", names(s.realm.capitals())) + ")을 모두 가진 나라가 처음 나오면 황제가 됩니다"));
            }, p);
            default -> {
                return false;
            }
        }
        return true;
    }

    private java.util.List<String> names(java.util.Collection<String> regions) {
        return regions.stream().map(id -> s.regions.byId(id).name()).toList();
    }

    private Region here(Player p) {
        return s.regions.at(p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
    }

    private static String key(String what, Player p) {
        return what + ":" + p.getUniqueId() + ":" + System.nanoTime();
    }

    // ------------------------------------------------------------------ 땅
    private void land(Player p, String sub, String[] a) {
        String id = p.getUniqueId().toString(), world = p.getWorld().getName();
        int cx = p.getLocation().getBlockX() >> 4, cz = p.getLocation().getBlockZ() >> 4;
        switch (sub) {
            case "구입", "buy" -> async.run("land-buy", () -> s.realm.buyPlot(id, world, cx, cz, key("land", p)), plot -> {
                realm.plotChanged(world, cx, cz);
                p.sendMessage(Ui.info("땅을 샀다 (" + io.versaera.domain.economy.Money.format(plot.price()) + ") — 이 청크(16×16)는 이제 당신과 허가받은 사람만 고칠 수 있다"));
            }, p);
            case "팔기", "sell" -> async.run("land-sell", () -> s.realm.sellPlot(id, world, cx, cz, key("land-sell", p)), refund -> {
                realm.plotChanged(world, cx, cz);
                p.sendMessage(Ui.info("땅을 팔았다 (+" + io.versaera.domain.economy.Money.format(refund) + ")"));
            }, p);
            case "허가", "trust", "금지", "untrust" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) { p.sendMessage(Ui.error("/땅 허가|금지 <접속 중인 이름>")); return; }
                boolean add = sub.equals("허가") || sub.equals("trust");
                String tid = t.getUniqueId().toString();
                async.run("land-trust", () -> { s.realm.trust(id, world, cx, cz, tid, add); return true; }, ok -> {
                    realm.plotChanged(world, cx, cz);
                    p.sendMessage(Ui.info(t.getName() + (add ? " 님에게 이 땅을 허가했다" : " 님의 허가를 거뒀다")));
                }, p);
            }
            case "목록", "list" -> async.run("land-list", () -> s.realm.plotsOf(id), list -> {
                p.sendMessage(Ui.c("&6내 땅 " + list.size() + "칸"));
                for (var pl : list) p.sendMessage(Ui.c("&7- " + pl.world() + " 청크 (" + pl.cx() + ", " + pl.cz() + ") · 블록 (" + pl.cx() * 16 + ", " + pl.cz() * 16 + ")"));
            }, p);
            default -> async.run("land-info", () -> new Object[]{s.realm.plot(world, cx, cz), s.realm.plotPrice(world, cx, cz)}, r -> {
                @SuppressWarnings("unchecked") var plot = (Optional<RealmRepository.Plot>) r[0];
                long price = (Long) r[1];
                if (plot.isPresent()) {
                    OfflinePlayer o = Bukkit.getOfflinePlayer(java.util.UUID.fromString(plot.get().owner()));
                    p.sendMessage(Ui.c("&6이 땅의 주인: &f" + (o.getName() == null ? "?" : o.getName())));
                } else if (price < 0) p.sendMessage(Ui.c("&7여기는 살 수 없는 땅입니다"));
                else p.sendMessage(Ui.c("&7주인 없는 땅 — &e" + io.versaera.domain.economy.Money.format(price) + "&7 · /땅 구입 · 팔기 · 허가 <이름> · 금지 <이름> · 목록"));
            }, p);
        }
    }

    // ------------------------------------------------------------------ 개인 상점
    private void shop(Player p, String sub, String[] a) {
        String id = p.getUniqueId().toString();
        Block b = p.getTargetBlockExact(5);
        switch (sub) {
            case "개설", "open" -> {
                if (b == null || a.length < 2) { p.sendMessage(Ui.error("상점으로 쓸 블록(상자 등)을 보며 /상점 개설 <이름>")); return; }
                String name = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
                String world = b.getWorld().getName();
                int x = b.getX(), y = b.getY(), z = b.getZ();
                async.run("shop-open", () -> s.realm.openShop(id, world, x, y, z, name), sh -> {
                    realm.shopOpened(sh);
                    p.sendMessage(Ui.info("「" + name + "」 개설 — 손에 물건을 들고 이 블록을 보며 /상점 등록 <가격> [수량]"));
                }, p);
            }
            case "등록", "sell" -> {
                Optional<String> shop = b == null ? Optional.empty() : realm.shopAt(b);
                if (shop.isEmpty() || a.length < 2) { p.sendMessage(Ui.error("내 상점 블록을 보며 /상점 등록 <가격> [수량]")); return; }
                long price;
                price = io.versaera.domain.economy.Money.parse(a[1]);
                if (price <= 0) { p.sendMessage(Ui.error("가격: 3골드20실버 · 50실버 · 30쿠퍼 (숫자만 쓰면 쿠퍼, 띄어 쓰지 말 것)")); return; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                String iid = codec.instanceId(hand), type = codec.typeId(hand);
                if (type == null) { p.sendMessage(Ui.error("이 게임의 아이템만 팔 수 있습니다")); return; }
                if (iid != null) {
                    async.run("shop-stock", () -> s.realm.stockUnique(id, shop.get(), iid, price), st -> {
                        if (iid.equals(codec.instanceId(p.getInventory().getItemInMainHand()))) p.getInventory().setItemInMainHand(null);
                        p.sendMessage(Ui.info("올렸다 — " + io.versaera.domain.economy.Money.format(price)));
                    }, p);
                    return;
                }
                int amount = Math.min(hand.getAmount(), a.length > 2 ? Math.max(1, parse(a[2])) : hand.getAmount());
                int quality = codec.bulkQuality(hand);
                // 묶음: 먼저 손에서 빼고 서버에 올린다. 실패하면 배달함으로 돌려준다
                hand.setAmount(hand.getAmount() - amount);
                p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
                async.run("shop-stock-bulk", () -> {
                    try {
                        return s.realm.stockBulk(id, shop.get(), type, quality, amount, price);
                    } catch (RuntimeException e) {
                        s.items.deliverBulk(id, type, quality, amount, "shop_refund");
                        throw e;
                    }
                }, st -> p.sendMessage(Ui.info(amount + "개를 올렸다 — 개당 " + io.versaera.domain.economy.Money.format(price))), p);
            }
            case "닫기", "close" -> {
                Optional<String> shop = b == null ? Optional.empty() : realm.shopAt(b);
                if (shop.isEmpty()) { p.sendMessage(Ui.error("내 상점 블록을 보며 /상점 닫기")); return; }
                String world = b.getWorld().getName();
                int x = b.getX(), y = b.getY(), z = b.getZ();
                async.run("shop-close", () -> { s.realm.closeShop(id, shop.get()); return true; }, ok -> {
                    realm.shopClosed(world, x, y, z);
                    p.sendMessage(Ui.info("상점을 닫았다"));
                }, p);
            }
            default -> p.sendMessage(Ui.c("&7/상점 개설 <이름> · 등록 <가격> [수량] · 닫기 (블록을 보며). 상점 블록 우클릭 = 상점 창"));
        }
    }

    private static int parse(String v) {
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 1; }
    }

    // ------------------------------------------------------------------ 성
    private void castle(Player p, String sub, String[] a) {
        String id = p.getUniqueId().toString();
        Region r = here(p);
        Optional<String> castle = r == null ? Optional.empty() : s.realm.castleRegionAt(r.id());
        switch (sub) {
            case "목록", "list" -> async.run("castle-list", () -> {
                java.util.List<String> lines = new java.util.ArrayList<>();
                for (var c : s.realm.ownedCastles())
                    lines.add("&f" + s.regions.byId(c.region()).name() + " &7— " + s.guilds.find(c.guildId()).map(g -> g.name()).orElse("?") + " · 세금 " + c.taxPct() + "%");
                return lines;
            }, lines -> {
                p.sendMessage(Ui.c("&6주인 있는 성 " + lines.size() + "곳"));
                lines.forEach(l -> p.sendMessage(Ui.c(l)));
            }, p);
            case "구입", "buy" -> {
                if (castle.isEmpty()) { p.sendMessage(Ui.error("성(도시) 안에서 하세요")); return; }
                async.run("castle-buy", () -> s.realm.buyCastle(id, castle.get(), key("castle", p)), crown -> {
                    Bukkit.broadcastMessage(Ui.info(p.getName() + " 님의 길드가 「" + s.regions.byId(castle.get()).name() + "」의 주인이 되었습니다"));
                    crown.ifPresent(realm::announceEmperor);
                }, p);
            }
            case "세금", "tax" -> {
                if (castle.isEmpty() || a.length < 2) { p.sendMessage(Ui.error("성 안에서 /성 세금 <0~20>")); return; }
                int pct = parse(a[1]);
                async.run("castle-tax", () -> { s.realm.setTax(id, castle.get(), pct); return true; }, ok -> p.sendMessage(Ui.info("세율 " + pct + "%")), p);
            }
            case "공성", "siege" -> {
                if (castle.isEmpty()) { p.sendMessage(Ui.error("빼앗을 성 안에서 /성 공성")); return; }
                async.run("siege", () -> s.realm.declareSiege(id, castle.get(), key("siege", p)), realm::siegeStarted, p);
            }
            default -> {
                if (castle.isEmpty()) { p.sendMessage(Ui.c("&7/성 목록 · (성 안에서) 정보 · 구입 · 세금 <%> · 공성")); return; }
                String c = castle.get();
                async.run("castle-info", () -> new Object[]{s.realm.castle(c), s.realm.castlePrice(c)}, res -> {
                    @SuppressWarnings("unchecked") var own = (Optional<RealmRepository.Castle>) res[0];
                    String name = s.regions.byId(c).name() + (s.realm.capitals().contains(c) ? " &e(수도)" : "");
                    if (own.isEmpty()) p.sendMessage(Ui.c("&6" + name + " &7— 주인 없음 · 길드 금고 " + io.versaera.domain.economy.Money.format((long) res[1]) + "로 /성 구입"));
                    else async.run("castle-owner", () -> s.guilds.find(own.get().guildId()).map(g -> g.name()).orElse("?"),
                            g -> p.sendMessage(Ui.c("&6" + name + " &7— " + g + " 길드 · 세금 " + own.get().taxPct() + "% · /성 공성 으로 도전")), p);
                }, p);
            }
        }
    }

    // ------------------------------------------------------------------ 국가
    private void nation(Player p, String sub, String[] a) {
        String id = p.getUniqueId().toString();
        switch (sub) {
            case "건국", "found" -> {
                if (a.length < 2) { p.sendMessage(Ui.error("/국가 건국 <이름> (성을 가진 길드의 길드장)")); return; }
                String name = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
                async.run("nation", () -> new Object[]{s.realm.foundNation(id, name), s.realm.checkEmperor()}, r -> {
                    Bukkit.broadcastMessage(Ui.info("새 나라 「" + name + "」 건국"));
                    @SuppressWarnings("unchecked") var crown = (Optional<RealmService.Crowning>) r[1];
                    crown.ifPresent(realm::announceEmperor);
                }, p);
            }
            default -> async.run("nations", () -> {
                java.util.List<String> lines = new java.util.ArrayList<>();
                for (var n : s.realm.nations()) {
                    long castles = s.realm.ownedCastles().stream().filter(c -> c.guildId().equals(n.guildId())).count();
                    long capitals = s.realm.ownedCastles().stream().filter(c -> c.guildId().equals(n.guildId()) && s.realm.capitals().contains(c.region())).count();
                    lines.add("&f" + n.name() + " &7— 성 " + castles + " · 수도 " + capitals + "/" + s.realm.capitals().size() + " · 금고 "
                            + io.versaera.domain.economy.Money.format(s.economy.balance(GuildService.wallet(n.guildId()))));
                }
                return lines;
            }, lines -> {
                p.sendMessage(Ui.c("&6나라 " + lines.size() + "곳 &7(/국가 건국 <이름>)"));
                lines.forEach(l -> p.sendMessage(Ui.c(l)));
            }, p);
        }
    }
}
