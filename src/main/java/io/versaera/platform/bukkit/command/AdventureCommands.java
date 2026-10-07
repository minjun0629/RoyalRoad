package io.versaera.platform.bukkit.command;

import io.versaera.application.AchievementService;
import io.versaera.application.GameServices;
import io.versaera.application.GuildVaultService;
import io.versaera.application.RaidService;
import io.versaera.application.port.AdventureRepository;
import io.versaera.domain.achievement.Achievement;
import io.versaera.domain.achievement.Title;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.pet.PetRules;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.InventoryOps;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.ui.Menu;
import io.versaera.platform.bukkit.world.ArtworkRuntime;
import io.versaera.platform.bukkit.world.PetRuntime;
import io.versaera.platform.bukkit.world.RaidRuntime;
import io.versaera.platform.bukkit.world.TravelRuntime;
import io.versaera.platform.bukkit.world.WeatherRuntime;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;

/**
 * 모험 확장 명령 · 창 (V7): /업적 · /칭호 · /기록 · /펫 · /탈것 · /레이드 · /날씨 · /길드창고 · /길드의뢰 · /조각.
 * 모든 판단은 서비스(DB 스레드)가, 이 클래스는 창을 그리고 결과를 보여 준다.
 */
public final class AdventureCommands implements CommandExecutor, TabCompleter {
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Consumer<Player> deliver;
    private final PetRuntime pets;
    private final TravelRuntime travel;
    private final RaidRuntime raids;
    private final WeatherRuntime weather;
    private final ArtworkRuntime art;

    public AdventureCommands(GameServices s, Async async, ItemCodec codec, Consumer<Player> deliver, PetRuntime pets, TravelRuntime travel,
                             RaidRuntime raids, WeatherRuntime weather, ArtworkRuntime art) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.deliver = deliver;
        this.pets = pets;
        this.travel = travel;
        this.raids = raids;
        this.weather = weather;
        this.art = art;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) return true;
        try {
            switch (cmd.getName()) {
                case "achievements" -> achievements(p, a.length > 0 ? a[0] : null);
                case "title" -> titles(p);
                case "record" -> record(p);
                case "pet" -> pet(p, a);
                case "mount" -> mount(p, a);
                case "raid" -> raid(p, a);
                case "weather" -> weather.report(p).forEach(l -> p.sendMessage(Ui.c(l)));
                case "gstorage" -> storage(p);
                case "gquest" -> guildQuests(p);
                case "sculpt" -> sculpt(p, a);
                default -> { }
            }
        } catch (DomainException e) {
            p.sendMessage(Ui.error(e.getMessage()));
        }
        return true;
    }

    // ------------------------------------------------------------------ 업적
    private void achievements(Player p, String category) {
        String id = p.getUniqueId().toString();
        async.run("ach-list", () -> s.achievements.list(id), rows -> {
            List<String> cats = rows.stream().map(r -> r.achievement().category()).distinct().toList();
            String cat = category != null && cats.contains(category) ? category : cats.get(0);
            Menu m = new Menu(6, "&8업적 · " + cat);
            for (int i = 0; i < cats.size() && i < 9; i++) {
                String c = cats.get(i);
                long got = rows.stream().filter(r -> r.achievement().category().equals(c) && r.earned()).count();
                long all = rows.stream().filter(r -> r.achievement().category().equals(c)).count();
                m.set(i, Menu.icon(c.equals(cat) ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, "&f" + c, List.of("&7" + got + "/" + all)),
                        e -> achievements(p, c));
            }
            int slot = 9;
            for (AchievementService.Row r : rows) {
                if (!r.achievement().category().equals(cat) || slot >= 54) continue;
                Achievement x = r.achievement();
                boolean hide = x.hidden() && !r.earned();
                List<String> lore = new ArrayList<>();
                lore.add(hide ? "&8숨은 업적" : "&7" + x.desc());
                if (!hide) {
                    List<String> rw = new ArrayList<>();
                    if (x.money() > 0) rw.add("&e" + x.money() + " 골드");
                    if (x.fame() > 0) rw.add("&6명성 " + x.fame());
                    if (x.title() != null) rw.add("&d칭호 " + s.achievements.title(x.title()).name());
                    if (!rw.isEmpty()) lore.add(String.join(" &8· ", rw));
                }
                lore.add(r.earned() ? "&a달성" : "&8미달성");
                m.set(slot++, Menu.ui(r.earned() ? "achievement" : "achievement_locked", r.earned() ? Material.GOLD_BLOCK : Material.COAL_BLOCK,
                        (r.earned() ? "&a" : "&7") + (hide ? "???" : x.name()), lore), null);
            }
            m.open(p);
        }, p);
    }

    // ------------------------------------------------------------------ 칭호
    private void titles(Player p) {
        String id = p.getUniqueId().toString();
        async.run("title-list", () -> new Object[]{s.achievements.titles(id), s.achievements.equipped(id).orElse(null)}, r -> {
            @SuppressWarnings("unchecked") List<Title> list = (List<Title>) r[0];
            Title on = (Title) r[1];
            Menu m = new Menu(6, "&8칭호");
            int slot = 0;
            for (Title t : list) {
                if (slot >= 45) break;
                boolean worn = on != null && on.id().equals(t.id());
                m.set(slot++, Menu.ui("title", Material.NAME_TAG, t.color() + t.name(), List.of("&7" + t.desc(), worn ? "&a달고 있음" : "&8클릭: 달기")),
                        e -> async.run("title-set", () -> { s.achievements.equip(id, t.id()); return null; }, v -> {
                            p.closeInventory();
                            p.sendMessage(Ui.info("칭호: " + t.color() + t.name()));
                        }, p));
            }
            if (list.isEmpty()) m.set(22, Menu.icon(Material.PAPER, "&7얻은 칭호가 없습니다", List.of("&8업적 · 레이드 · 숨은 조건으로 얻는다")), null);
            m.set(49, Menu.ui("close", Material.BARRIER, "&c칭호 떼기", List.of()), e -> async.run("title-clear", () -> { s.achievements.equip(id, null); return null; },
                    v -> { p.closeInventory(); p.sendMessage(Ui.info("칭호를 뗐다")); }, p));
            m.open(p);
        }, p);
    }

    // ------------------------------------------------------------------ 모험가 기록
    private void record(Player p) {
        String id = p.getUniqueId().toString();
        async.run("record", () -> s.achievements.record(id), r -> {
            Menu m = new Menu(6, "&8모험가 기록 · " + p.getName());
            m.set(4, Menu.icon(Material.PLAYER_HEAD, "&f" + p.getName() + (r.equipped() == null ? "" : " " + r.equipped().color() + "[" + r.equipped().name() + "]"),
                    List.of("&6명성 " + r.fame() + (r.notoriety() > 0 ? " &c악명 " + r.notoriety() : ""), "&e업적 " + r.achievements() + "/" + r.achievementTotal()
                            + " &7· 점수 " + r.points(), "&b서버 최초 " + r.worldFirsts() + "번")), null);
            List<String> found = new ArrayList<>();
            found.add("&f지역 " + r.discoveries().getOrDefault("region", 0) + "/" + r.regionsTotal());
            found.add("&fNPC " + r.discoveries().getOrDefault("npc", 0) + "명");
            r.discoveries().forEach((k, v) -> { if (!List.of("region", "npc", "achievement", "title").contains(k)) found.add("&7" + k + " " + v); });
            m.set(19, Menu.ui("map_known", Material.FILLED_MAP, "&a발견", found), null);
            List<String> acts = new ArrayList<>();
            r.counters().forEach((k, v) -> { if (v > 0) acts.add("&7" + AchievementService.SHOWN.get(k) + " &f" + v); });
            m.set(21, Menu.ui("combat", Material.IRON_SWORD, "&c행적", acts.isEmpty() ? List.of("&8아직 없음") : acts), null);
            List<String> mas = new ArrayList<>();
            r.topMastery().forEach((d, lv) -> mas.add("&7" + s.growth.discipline(d).name() + " &f" + io.versaera.domain.skill.Mastery.label(lv)));
            m.set(23, Menu.ui("stat", Material.EXPERIENCE_BOTTLE, "&e숙련", mas.isEmpty() ? List.of("&8아직 없음") : mas), null);
            m.set(25, Menu.ui("pet", Material.BONE, "&a동료", List.of("&7펫 " + r.pets() + " · 탈것 " + r.mounts(), "&7레이드 공략 " + r.raidClears())), null);
            List<String> cats = new ArrayList<>();
            r.byCategory().forEach((c, v) -> cats.add("&7" + c + " &f" + v[0] + "/" + v[1]));
            m.set(39, Menu.ui("achievement", Material.GOLD_BLOCK, "&6업적", cats), e -> achievements(p, null));
            List<String> ts = new ArrayList<>();
            for (Title t : r.titles()) ts.add(t.color() + t.name());
            m.set(41, Menu.ui("title", Material.NAME_TAG, "&d칭호 " + r.titles().size(), ts.isEmpty() ? List.of("&8없음") : ts.subList(0, Math.min(15, ts.size()))), e -> titles(p));
            m.set(49, Menu.ui("close", Material.BARRIER, "&c닫기", List.of()), e -> p.closeInventory());
            m.open(p);
        }, p);
    }

    // ------------------------------------------------------------------ 펫
    private void pet(Player p, String[] a) {
        String id = p.getUniqueId().toString();
        String sub = a.length == 0 ? "" : a[0];
        switch (sub) {
            case "이름", "name" -> {
                String pid = pets.activePet(p.getUniqueId()).orElseThrow(() -> DomainException.of("pet.none", "불러낸 펫이 없습니다"));
                String name = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                async.run("pet-name", () -> s.pets.rename(id, pid, name), pt -> { p.sendMessage(Ui.info("이름: " + pt.name())); pets.summon(p, pid); }, p);
            }
            case "돌려보내기", "dismiss" -> pets.dismiss(p, true);
            case "놓아주기", "release" -> {
                String pid = pets.activePet(p.getUniqueId()).orElseThrow(() -> DomainException.of("pet.none", "놓아줄 펫을 먼저 불러내세요"));
                if (a.length < 2 || !a[1].equals("확인")) { p.sendMessage(Ui.c("&c정말 놓아주려면 &f/펫 놓아주기 확인 &c— 되돌릴 수 없습니다")); return; }
                pets.dismiss(p, false);
                async.run("pet-release", () -> { s.pets.release(id, pid); return null; }, v -> p.sendMessage(Ui.info("펫을 들로 돌려보냈다")), p);
            }
            default -> async.run("pet-list", () -> {
                List<Object[]> out = new ArrayList<>();
                for (var pt : s.pets.pets(id)) out.add(new Object[]{pt, s.pets.stats(pt)});
                return new Object[]{out, s.growth.level(id, "taming")};
            }, r -> {
                @SuppressWarnings("unchecked") List<Object[]> list = (List<Object[]>) r[0];
                int lv = (int) r[1];
                Menu m = new Menu(3, "&8펫 " + list.size() + "/" + PetRules.maxPets(lv));
                int slot = 9;
                String act = pets.activePet(p.getUniqueId()).orElse("");
                for (Object[] o : list) {
                    var pt = (AdventureRepository.Pet) o[0];
                    var st = (io.versaera.application.PetService.Stats) o[1];
                    boolean out = pt.id().equals(act), fainted = pt.faintedUntil() > System.currentTimeMillis();
                    List<String> lore = List.of("&7" + st.species().name() + " · Lv." + pt.level() + " · 충성 " + st.loyalty(),
                            "&7체력 " + st.maxHealth() + " · 공격 " + String.format("%.1f", st.attack()), "&7" + String.join(" · ", st.skills()),
                            fainted ? "&c쉬는 중" : out ? "&a곁에 있음 &8(클릭: 돌려보내기)" : "&8클릭: 부르기", "&8쉬프트 클릭: 손에 든 먹이 주기");
                    m.set(slot++, Menu.ui("pet", Material.BONE, "&a" + pt.name(), lore), e -> {
                        if (e.isShiftClick()) { feed(p, pt.id()); return; }
                        p.closeInventory();
                        if (out) pets.dismiss(p, true);
                        else pets.summon(p, pt.id());
                    });
                }
                if (list.isEmpty()) m.set(13, Menu.icon(Material.PAPER, "&7펫이 없습니다", List.of("&8쉬프트 + 먹이를 들고 들의 동물을 우클릭", "&8늑대: 고기 · 고양이: 생선 …")), null);
                m.open(p);
            }, p);
        }
    }

    private void feed(Player p, String petId) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        String type = codec.typeId(hand);
        if (type == null || codec.instanceId(hand) != null) { p.sendMessage(Ui.error("먹이(재료)를 손에 드세요")); return; }
        int q = codec.bulkQuality(hand);
        Set<String> tags = codec.types().get(type).tags();
        var taken = InventoryOps.take(p, codec, type, 1, 0);
        if (taken == null) return;
        String id = p.getUniqueId().toString();
        async.run("pet-feed", () -> s.pets.feed(id, petId, tags, q), loyalty -> {
            p.closeInventory();
            p.sendMessage(Ui.info("맛있게 먹었다 &7(충성 " + loyalty + ")"));
        }, err -> InventoryOps.give(p, codec, taken), p);
    }

    // ------------------------------------------------------------------ 탈것
    private void mount(Player p, String[] a) {
        if (a.length > 0 && (a[0].equals("내리기") || a[0].equals("dismiss"))) { travel.dismissMount(p); return; }
        String id = p.getUniqueId().toString();
        async.run("mount-list", () -> new Object[]{s.travel.mounts(id), s.growth.level(id, "riding")}, r -> {
            @SuppressWarnings("unchecked") List<AdventureRepository.Mount> list = (List<AdventureRepository.Mount>) r[0];
            int lv = (int) r[1];
            Menu m = new Menu(3, "&8탈것 · 승마 " + lv);
            int slot = 9;
            for (var mt : list) {
                var k = s.travel.kind(mt.kind());
                boolean ok = lv >= k.ridingLevel();
                m.set(slot++, Menu.ui("mount", Material.SADDLE, "&6" + mt.name(), List.of("&7속도 " + Math.round(k.speedFor(lv) / 0.225 * 100) + "% · 점프 " + k.jump(),
                        ok ? "&8클릭: 부르기" : "&c승마 " + k.ridingLevel() + " 필요")), e -> { p.closeInventory(); travel.summonMount(p, mt.id()); });
            }
            if (list.isEmpty()) m.set(13, Menu.icon(Material.PAPER, "&7탈것이 없습니다", List.of("&8도시의 마구간지기에게서 산다")), null);
            m.open(p);
        }, p);
    }

    /** 마구간지기 창 (NPC 창에서) */
    public void stable(Player p, String npcId) {
        Menu m = new Menu(3, "&8마구간");
        int slot = 9;
        for (var k : s.travel.mountKinds()) {
            m.set(slot++, Menu.ui("mount", Material.SADDLE, "&6" + k.name(), List.of("&e" + k.price() + " 골드 &8(관계 할인 따로)", "&7승마 " + k.ridingLevel() + " · 체력 " + k.health(),
                    "&8클릭: 사기")), e -> {
                String id = p.getUniqueId().toString(), req = UUID.randomUUID().toString();
                async.run("mount-buy", () -> s.travel.buy(id, npcId, k.id(), req), mt -> {
                    p.closeInventory();
                    p.sendMessage(Ui.info(mt.name() + " 을(를) 샀다 — /탈것"));
                }, p);
            });
        }
        m.open(p);
    }

    /** 마부 · 선장 창 (NPC 창에서): 이 도시에서 떠나는 노선 */
    public void routes(Player p, String npcId, String here, boolean ship) {
        if (here == null) { p.sendMessage(Ui.error("지역 밖입니다")); return; }
        var list = s.travel.routesFrom(here).stream().filter(r -> (r.kind() == io.versaera.domain.travel.Route.Kind.SHIP) == ship).toList();
        Menu m = new Menu(3, "&8" + (ship ? "배" : "마차") + " · " + s.regions.byId(here).name());
        int slot = 9;
        for (var r : list) {
            if (slot > 26) break;
            m.set(slot++, Menu.ui(ship ? "ship" : "carriage", ship ? Material.OAK_BOAT : Material.MINECART, "&f" + s.regions.byId(r.to()).name(),
                    List.of("&e" + r.fare() + " 골드 &7· " + r.distance() + " 블록 · " + Math.max(1, r.durationMs() / 1000) + "초", "&8클릭: 떠나기")), e -> {
                p.closeInventory();
                travel.depart(p, npcId, r, here);
            });
        }
        if (list.isEmpty()) m.set(13, Menu.icon(Material.PAPER, "&7이곳에서 떠나는 " + (ship ? "배가" : "마차가") + " 없습니다", List.of()), null);
        m.open(p);
    }

    // ------------------------------------------------------------------ 레이드
    private void raid(Player p, String[] a) {
        String id = p.getUniqueId().toString(), sub = a.length == 0 ? "" : a[0];
        switch (sub) {
            case "초대", "invite" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null) throw DomainException.of("raid.no_player", "접속 중인 파티장이 아닙니다");
                s.parties.raidInvite(id, t.getUniqueId().toString());
                p.sendMessage(Ui.info(t.getName() + " 파티를 공격대에 초대했다"));
                t.sendMessage(Ui.info(p.getName() + " 님이 공격대에 초대했다 — /레이드 수락"));
            }
            case "수락", "accept" -> {
                String head = s.parties.raidAccept(id);
                for (String m : s.parties.raidMembers(head)) {
                    Player o = Bukkit.getPlayer(UUID.fromString(m));
                    if (o != null) o.sendMessage(Ui.info(p.getName() + " 파티가 공격대에 들어왔다 (" + s.parties.raidMembers(head).size() + "명)"));
                }
            }
            case "나가기", "leave" -> {
                s.parties.leaveRaid(s.parties.leader(id).orElse(id));
                p.sendMessage(Ui.info("공격대를 떠났다"));
            }
            case "시작", "start" -> {
                if (a.length < 2) throw DomainException.of("raid.usage", "/레이드 시작 <id>");
                raids.start(p, a[1]);
            }
            default -> async.run("raid-list", () -> {
                List<Object[]> out = new ArrayList<>();
                for (var d : s.raids.all()) out.add(new Object[]{d, s.raids.lockedOut(id, d.id()), s.raids.best(d.id(), 1)});
                return out;
            }, rows -> {
                Menu m = new Menu(3, "&8레이드 · 공격대 " + s.parties.raidMembers(id).size() + "명");
                int slot = 9;
                for (Object[] o : rows) {
                    var d = (io.versaera.domain.raid.RaidDefinition) o[0];
                    boolean locked = (boolean) o[1];
                    @SuppressWarnings("unchecked") var best = (List<AdventureRepository.RaidClear>) o[2];
                    List<String> lore = new ArrayList<>(List.of("&7" + d.desc(), "&7" + s.regions.byId(d.region()).name() + " · " + d.minPlayers() + "~" + d.maxPlayers() + "명",
                            "&7전투 숙련 " + d.mastery() + " · " + d.timeLimitMs() / 60_000 + "분", "&e" + d.money() + " 골드 · 명성 " + d.fame()
                                    + (d.title() == null ? "" : " · 칭호 " + s.achievements.title(d.title()).name())));
                    if (!best.isEmpty()) lore.add("&b최고 기록 " + best.get(0).durationMs() / 1000 / 60 + "분 " + best.get(0).durationMs() / 1000 % 60 + "초");
                    lore.add(locked ? "&c이번 주 귀속" : "&8공격대장: 그 지역에서 /레이드 시작 " + d.id());
                    m.set(slot++, Menu.ui("raid", Material.DRAGON_HEAD, "&5" + d.name(), lore), null);
                }
                m.open(p);
            }, p);
        }
    }

    // ------------------------------------------------------------------ 길드 창고 · 의뢰
    private void storage(Player p) {
        String id = p.getUniqueId().toString();
        async.run("gstore", () -> new Object[]{s.guildVault.storage(id), s.guildVault.withdrawLeft(id)}, r -> {
            @SuppressWarnings("unchecked") List<AdventureRepository.Stored> list = (List<AdventureRepository.Stored>) r[0];
            long left = (long) r[1];
            Menu m = new Menu(6, "&8길드 창고 " + (left < 0 ? "" : "&7(오늘 " + left + "개 더)"));
            int slot = 0;
            for (var st : list) {
                if (slot >= 45) break;
                ItemStack icon = codec.bulk(st.typeId(), st.quality(), (int) Math.min(64, Math.max(1, st.amount())));
                var meta = icon.getItemMeta();
                meta.setLore(List.of(Ui.c("&7" + st.amount() + "개"), Ui.c("&8클릭: 64개 · 쉬프트: 8개 꺼내기 (배달함)")));
                icon.setItemMeta(meta);
                m.set(slot++, icon, e -> {
                    int n = (int) Math.min(st.amount(), e.isShiftClick() ? 8 : 64);
                    String req = UUID.randomUUID().toString();
                    async.run("gstore-out", () -> s.guildVault.withdraw(id, st.typeId(), st.quality(), n, req), v -> { deliver.accept(p); storage(p); }, p);
                });
            }
            m.set(49, Menu.ui("vault", Material.CHEST, "&a손에 든 재료 넣기", List.of("&8묶음 재료만")), e -> deposit(p));
            m.set(51, Menu.ui("gquest", Material.WRITABLE_BOOK, "&b길드 의뢰", List.of()), e -> guildQuests(p));
            m.open(p);
        }, p);
    }

    private void deposit(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        String type = codec.typeId(hand);
        if (type == null || codec.instanceId(hand) != null) { p.sendMessage(Ui.error("재료를 손에 드세요")); return; }
        int q = codec.bulkQuality(hand), n = hand.getAmount();
        p.getInventory().setItemInMainHand(null);
        String id = p.getUniqueId().toString(), req = UUID.randomUUID().toString();
        async.run("gstore-in", () -> s.guildVault.deposit(id, type, q, n, req), v -> storage(p), err -> deliver.accept(p), p);
    }

    private void guildQuests(Player p) {
        String id = p.getUniqueId().toString();
        async.run("gquest", () -> s.guildVault.questStates(id), list -> {
            Menu m = new Menu(3, "&8길드 의뢰 · 이번 주");
            int slot = 11;
            for (GuildVaultService.QuestState q : list) {
                List<String> lore = new ArrayList<>(List.of("&7" + q.def().desc(), "&f" + q.progress() + "/" + q.def().target(),
                        "&e금고 " + q.def().money() + " · 활동 " + q.def().activity()));
                for (var c : q.top()) {
                    var op = Bukkit.getOfflinePlayer(UUID.fromString(c.uuid()));
                    lore.add("&8" + (op.getName() == null ? "?" : op.getName()) + " " + c.amount());
                }
                lore.add(q.done() ? "&a완료" : "&8길드원 모두의 기록이 모인다");
                m.set(slot, Menu.ui("gquest", q.done() ? Material.ENCHANTED_BOOK : Material.WRITABLE_BOOK, (q.done() ? "&a" : "&b") + q.def().name(), lore), null);
                slot += 2;
            }
            m.open(p);
        }, p);
    }

    // ------------------------------------------------------------------ 대형 조각
    private void sculpt(Player p, String[] a) {
        String sub = a.length == 0 ? "" : a[0];
        switch (sub) {
            case "만들기", "make" -> {
                if (a.length < 3) throw DomainException.of("art.usage", "/조각 만들기 <종류> <작품 이름>");
                art.create(p, a[1], String.join(" ", Arrays.copyOfRange(a, 2, a.length)));
            }
            case "허물기", "remove" -> art.dismantle(p, p.hasPermission("versaera.admin") && a.length > 1 && a[1].equals("관리"));
            default -> {
                p.sendMessage(Ui.c("&f대형 조각 &7— /조각 만들기 <종류> <이름> · /조각 허물기"));
                for (var k : s.artworks.kinds()) {
                    List<String> parts = new ArrayList<>();
                    for (var part : k.parts()) parts.add(part.amount() + "×" + String.join("/", part.tags()));
                    p.sendMessage(Ui.c("&e" + k.id() + " &f" + k.name() + " &7조각 " + k.level() + " · " + String.join(" + ", parts) + " &8— " + k.desc()));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        return switch (cmd.getName()) {
            case "raid" -> a.length == 1 ? List.of("초대", "수락", "나가기", "시작") : a.length == 2 && a[0].equals("시작")
                    ? s.raids.all().stream().map(d -> d.id()).toList() : null;
            case "sculpt" -> a.length == 1 ? List.of("만들기", "허물기") : a.length == 2 && a[0].equals("만들기")
                    ? s.artworks.kinds().stream().map(k -> k.id()).toList() : List.of();
            case "pet" -> a.length == 1 ? List.of("이름", "돌려보내기", "놓아주기") : List.of();
            case "mount" -> a.length == 1 ? List.of("내리기") : List.of();
            default -> List.of();
        };
    }
}
