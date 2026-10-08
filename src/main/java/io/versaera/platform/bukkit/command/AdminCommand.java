package io.versaera.platform.bukkit.command;

import io.versaera.application.GameServices;
import io.versaera.domain.item.ItemInstance;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.boss.BossRuntime;
import io.versaera.platform.bukkit.listener.NpcListener;
import io.versaera.security.Sealer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/**
 * 관리자 전용 /versaadmin (/va) — 일반 플레이어 명령과 분리. 권한 versaera.admin.
 * inspect · item · audit · give · money · npc spawn · boss spawn|stop · seal · hidden generate · event · perf
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final NpcListener npcs;
    private final BossRuntime bosses;
    private final File dataFolder;
    private final Sealer sealer;
    private final Consumer<Player> deliver;

    private Consumer<Player> townReport = p -> { };

    /** /va 마을 진단 (플러그인이 생성기를 알고 있어 거기서 넘겨준다) */
    public void townReport(Consumer<Player> f) {
        this.townReport = f;
    }

    public AdminCommand(GameServices s, Async async, ItemCodec codec, NpcListener npcs, BossRuntime bosses, File dataFolder, Sealer sealer,
                        Consumer<Player> deliver) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.npcs = npcs;
        this.bosses = bosses;
        this.dataFolder = dataFolder;
        this.sealer = sealer;
        this.deliver = deliver;
    }

    /**
     * 초기화. /va 초기화 <이름> 확인 = 그 사람의 진행 · 아이템을 지움 (접속 중이면 인벤토리를 비우고 내보냄)
     * /va 초기화 전체 확인 = 다음 시작 때 DB 와 세계를 지우고 처음부터 (세계는 읽히기 전에만 지울 수 있다)
     */
    private void reset(CommandSender sender, String[] a) {
        if (a.length < 3 || !a[2].equals("확인")) {
            sender.sendMessage(Ui.error("/va 초기화 <이름> 확인 · /va 초기화 전체 확인 — 되돌릴 수 없습니다"));
            return;
        }
        if (a[1].equals("전체") || a[1].equalsIgnoreCase("all")) {
            try {
                dataFolder.mkdirs();
                java.nio.file.Files.writeString(new File(dataFolder, "reset-all").toPath(), sender.getName() + " " + java.time.LocalDateTime.now());
            } catch (java.io.IOException e) {
                sender.sendMessage(Ui.error("초기화 예약 실패: " + e.getMessage()));
                return;
            }
            sender.sendMessage(Ui.info("전체 초기화를 예약했습니다 — 서버를 껐다 켜면 DB · 세계가 지워지고 처음부터 시작합니다 (취소: plugins/VersaEra/reset-all 삭제)"));
            return;
        }
        String id = uuidOf(a[1]);
        if (id == null) { sender.sendMessage(Ui.error("그런 플레이어가 없습니다: " + a[1])); return; }
        String who = sender instanceof Player p ? p.getUniqueId().toString() : "console";
        Player online = Bukkit.getPlayer(UUID.fromString(id));
        Runnable wipe = () -> async.run("reset", () -> s.reset.player(id, who), n -> {
            // 저장된 인벤토리 · 위치 (서버 파일)도 지운다 — 다시 들어오면 빈손으로 종족 고르기부터
            for (org.bukkit.World w : Bukkit.getWorlds()) new File(w.getWorldFolder(), "playerdata/" + id + ".dat").delete();
            sender.sendMessage(Ui.info(a[1] + " 초기화 (" + n + "건)"));
        }, sender);
        if (online != null) {
            online.getInventory().clear();
            online.getEnderChest().clear();
            online.setLevel(0);
            online.setExp(0);
            online.kickPlayer(Ui.c("&c초기화되었습니다"));
            Bukkit.getScheduler().runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(AdminCommand.class), wipe, 2L);
        } else {
            wipe.run();
        }
    }

    private static String uuidOf(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId().toString();
        OfflinePlayer o = Bukkit.getOfflinePlayer(name);
        return o.hasPlayedBefore() ? o.getUniqueId().toString() : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!sender.hasPermission("versaera.admin")) { sender.sendMessage(Ui.error("권한이 없습니다")); return true; }
        String sub = a.length == 0 ? "help" : KOREAN.getOrDefault(a[0], a[0].toLowerCase(Locale.ROOT));
        String req = "admin:" + (sender instanceof Player p ? p.getUniqueId() : "console") + ":" + UUID.randomUUID();
        try {
            run(sender, sub, a, req);
        } catch (NumberFormatException e) {
            sender.sendMessage(Ui.error("숫자가 잘못되었습니다: " + e.getMessage()));
        } catch (IllegalArgumentException | io.versaera.domain.common.DomainException e) {
            sender.sendMessage(Ui.error(e.getMessage()));
        }
        return true;
    }

    /** 한국어 하위 명령 → 영어 (둘 다 된다) */
    private static final Map<String, String> KOREAN = Map.ofEntries(Map.entry("조사", "inspect"), Map.entry("아이템", "item"), Map.entry("기록", "audit"),
            Map.entry("지급", "give"), Map.entry("돈", "money"), Map.entry("엔피시", "npc"), Map.entry("보스", "boss"), Map.entry("봉인", "seal"),
            Map.entry("히든", "hidden"), Map.entry("행사", "event"), Map.entry("성능", "perf"),
            Map.entry("소환", "spawn"), Map.entry("정보", "info"), Map.entry("제거", "stop"), Map.entry("생성", "generate"));
    private static final List<String> SUBS = List.of("지급", "돈", "조사", "아이템", "기록", "엔피시", "보스", "봉인", "히든", "행사", "성능", "초기화", "마을");

    /** 이름(띄어쓰기는 _) 이나 id → id. 못 찾으면 null */
    static <T> String resolve(java.util.Collection<T> all, java.util.function.Function<T, String> id, java.util.function.Function<T, String> name, String arg) {
        for (T t : all) if (id.apply(t).equals(arg)) return arg;
        for (T t : all) if (key(name.apply(t)).equals(arg)) return id.apply(t);
        for (T t : all) if (key(name.apply(t)).equalsIgnoreCase(arg) || name.apply(t).replace(" ", "").equals(arg)) return id.apply(t);
        return null;
    }

    static String key(String name) {
        return name == null ? "" : name.trim().replace(' ', '_');
    }

    private static String word(String[] a, int i) {
        return a.length > i ? KOREAN.getOrDefault(a[i], a[i]) : "";
    }

    private void run(CommandSender sender, String sub, String[] a, String req) {
        switch (sub) {
            case "초기화", "reset" -> reset(sender, a);
            case "마을", "town" -> { if (sender instanceof Player p) townReport.accept(p); }
            case "inspect" -> {
                String id = a.length > 1 ? uuidOf(a[1]) : null;
                if (id == null) { sender.sendMessage(Ui.error("/va inspect <이름>")); return; }
                async.run("inspect", () -> List.of("돈 " + s.economy.balance(id), "숙련 " + s.progress.allMastery(id), "스탯 " + s.growth.statPoints(id),
                        "기록 " + s.progress.allCounters(id).size() + "개", "보유 아이템 " + s.itemRepo.byCustody(io.versaera.domain.item.Custody.player(id)).size()),
                        lines -> lines.forEach(l -> sender.sendMessage(Ui.info(l))), sender);
            }
            case "item" -> {
                if (!(sender instanceof Player p)) return;
                String iid = codec.instanceId(p.getInventory().getItemInMainHand());
                if (iid == null) { sender.sendMessage(Ui.error("손에 든 고유 아이템이 없습니다")); return; }
                async.run("item-inspect", () -> {
                    ItemInstance it = s.items.find(iid).orElse(null);
                    return it == null ? List.of("DB 에 없음 (위조 의심)") : List.of(typeName(it.typeId()) + " (" + it.typeId() + ") 품질 " + it.quality() + " · 내구 " + it.durability() + "/" + it.maxDurability(),
                            "제작 " + it.creatorName() + " · " + it.method(), "보관 " + it.custody(), "속성 " + it.props(), "내력 " + s.itemRepo.historyOf(iid));
                }, lines -> lines.forEach(l -> sender.sendMessage(Ui.info(l))), sender);
            }
            case "audit" -> {
                String action = a.length > 1 ? a[1].toUpperCase(Locale.ROOT) : "TRADE_COMPLETED";
                async.run("audit", () -> s.audit.recent(action, 10), lines -> lines.forEach(l -> sender.sendMessage(Ui.c("&7" + l))), sender);
            }
            case "give" -> {   // /va 지급 <플레이어> <아이템 이름> [품질] [수량]
                if (a.length < 3) { sender.sendMessage(Ui.error("/va 지급 <플레이어> <아이템 이름(띄어쓰기는 _)> [품질] [수량]")); return; }
                String id = uuidOf(a[1]);
                if (id == null) { sender.sendMessage(Ui.error("플레이어 " + a[1] + " 을(를) 찾지 못했습니다")); return; }
                String type = resolve(codec.types().all(), t -> t.id(), t -> t.name(), a[2]);
                if (type == null) { sender.sendMessage(Ui.error("아이템 " + a[2] + " 을(를) 찾지 못했습니다 — Tab 으로 이름을 고르세요")); return; }
                int q = a.length > 3 ? Integer.parseInt(a[3]) : 500, n = a.length > 4 ? Integer.parseInt(a[4]) : 1;
                boolean unique = codec.types().get(type).category().unique();
                async.run("admin-give", () -> {
                    if (unique) for (int i = 0; i < Math.min(n, 36); i++) s.items.create(type, q, null, "관리자", "admin", Map.of(), id, req);
                    else s.items.deliverBulk(id, type, q, n, "admin");
                    return true;
                }, ok -> {
                    sender.sendMessage(Ui.info(a[1] + " 에게 " + typeName(type) + " ×" + n + " (품질 " + q + ") 지급 → 배달함"));
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t != null) deliver.accept(t);
                }, sender);
            }
            case "money" -> {   // /va money <이름> <+금액|-금액>
                if (a.length < 3) { sender.sendMessage(Ui.error("/va money <이름> <+금액|-금액>")); return; }
                String id = uuidOf(a[1]);
                long v = Long.parseLong(a[2]);
                if (id == null || v == 0) { sender.sendMessage(Ui.error("이름 또는 금액이 잘못되었습니다")); return; }
                async.run("admin-money", () -> v > 0 ? s.economy.deposit(id, v, "admin", req) : s.economy.withdraw(id, -v, "admin", req),
                        ok -> sender.sendMessage(Ui.info("잔액 반영")), sender);
            }
            case "npc" -> {
                String npc = a.length >= 3 ? resolve(s.relations.all(), n -> n.id(), n -> n.name(), a[2]) : null;
                if (a.length >= 3 && npc == null) { sender.sendMessage(Ui.error("NPC " + a[2] + " 을(를) 찾지 못했습니다")); return; }
                if (npc != null && word(a, 1).equals("info")) { npcInfo(sender, npc); return; }
                if (!(sender instanceof Player p) || npc == null || !word(a, 1).equals("spawn")) { sender.sendMessage(Ui.error("/va 엔피시 소환 <이름> · /va 엔피시 정보 <이름>")); return; }
                npcs.spawn(s.relations.npc(npc), p.getLocation());
                sender.sendMessage(Ui.info("NPC " + s.relations.npc(npc).name()));
            }
            case "boss" -> {
                if (word(a, 1).equals("stop")) { sender.sendMessage(Ui.info("보스 " + bosses.stopAll(true) + "마리 제거")); return; }
                String boss = a.length >= 3 ? resolve(s.content.bosses(), b -> b.id(), b -> b.name(), a[2]) : null;
                if (!(sender instanceof Player p) || boss == null || !word(a, 1).equals("spawn")) { sender.sendMessage(Ui.error("/va 보스 소환 <이름> · /va 보스 제거")); return; }
                bosses.spawn(boss, p.getLocation(), sender, null);
            }
            case "seal" -> seal(sender);
            case "hidden" -> {
                if (!word(a, 1).equals("generate")) { sender.sendMessage(Ui.error("/va 히든 생성 [개수]")); return; }
                int n = a.length >= 3 ? Integer.parseInt(a[2]) : 8;
                generateHidden(sender, Math.max(1, Math.min(40, n)));
            }
            case "event" -> {
                long now = System.currentTimeMillis();
                for (var d : s.worldEvents.all()) {
                    var on = s.worldEvents.clock().activeAt(d, now);
                    var next = s.worldEvents.clock().next(d, now);
                    sender.sendMessage(Ui.c((on != null ? "&a● " : "&8○ ") + "&f" + d.name() + " &7" + (on != null ? "진행 중"
                            : java.time.Duration.ofMillis(next.start() - now).toMinutes() + "분 뒤")));
                }
            }
            case "perf" -> {
                Runtime rt = Runtime.getRuntime();
                sender.sendMessage(Ui.info("NPC " + s.relations.all().size() + " (프로필 " + s.npcWorld.profiles().size() + " · 떠난 상인 " + s.npcWorld.absent().size() + ")"));
                sender.sendMessage(Ui.info("지역 " + s.regions.all().size() + " · 레시피 " + s.crafting.all().size() + " · 히든 " + (s.hidden() == null ? 0 : s.hidden().ruleCount())
                        + " · 메모리 " + (rt.totalMemory() - rt.freeMemory()) / 1048576 + "MB"));
            }
            default -> sender.sendMessage(Ui.info("지급 · 돈 · 조사 · 아이템 · 기록 · 엔피시 소환|정보 · 보스 소환|제거 · 봉인 · 히든 생성 · 행사 · 성능 · 초기화 · 마을 (영어 give · money … 도 됩니다)"));
        }
    }

    /** NPC 한 명: 직업 틀 · 레벨 · 관계 · 지금 자리 · 지역 번영 (번영은 DB 스레드에서 읽는다) */
    private void npcInfo(CommandSender sender, String id) {
        async.run("admin-npc", () -> {
            var n = s.relations.npc(id);
            List<String> out = new ArrayList<>();
            out.add(n.name() + " · " + n.job() + " · " + n.region() + (n.evil() ? " · 악" : ""));
            s.npcWorld.profile(id).ifPresent(p -> {
                out.add("틀 " + p.archetype() + " · Lv." + p.level() + (p.family() != null ? " · " + p.family() + " " + p.household() : "")
                        + (p.trains() != null ? " · 지도 " + p.trains() : ""));
                out.add("관계 " + p.links().size() + " · 소문 " + p.rumors().size() + " · 숨은 의뢰 " + p.hiddenQuests().size()
                        + (p.wanderer() ? " · 떠돌이 " + String.join(">", p.route()) + " (지금 " + (s.npcWorld.wandererTown(id) == null ? "길 위" : s.npcWorld.wandererTown(id)) + ")" : "")
                        + (p.rare() != null ? " · 희귀 " + p.rare().hourFrom() + "~" + p.rare().hourTo() + "시, " + p.rare().everyDays() + "일마다" : ""));
            });
            out.add("지역 번영 " + s.npcWorld.prosperity(n.region()) + " (" + s.npcWorld.tier(n.region()).label + ")" + (s.npcWorld.present(id) ? "" : " · 떠나 있음"));
            return out;
        }, out -> out.forEach(l -> sender.sendMessage(Ui.info(l))), sender);
    }

    /**
     * 서버 비밀 시드로 히든 규칙을 만들어 곧바로 봉인한다 (HID-02). 평문은 디스크에 쓰지 않는다.
     * 이미 봉인된 규칙이 있으면 덮어쓰니, 손으로 만든 규칙(hidden-src)은 그다음에 seal 로 다시 합쳐야 한다.
     */
    private void generateHidden(CommandSender sender, int count) {
        Map<String, String> counters = new LinkedHashMap<>();
        counters.put("gather.fishing", "물가에서 오래 낚싯대를 드리운");
        counters.put("gather.mining", "갱도에서 오래 곡괭이질한");
        counters.put("gather.logging", "숲에서 나무를 베어 온");
        counters.put("talk.npc", "사람들과 자주 이야기하는");
        counters.put("gift.npc", "선물을 아끼지 않는");
        counters.put("kill.monster", "싸움을 피하지 않는");
        counters.put("hit_taken", "끝까지 버티는");
        counters.put("art.experience", "예술에 마음을 쓰는");
        counters.put("quest.completed", "부탁을 마다하지 않는");
        counters.put("dungeon.cleared", "깊은 곳을 다녀온");
        Map<String, String> regions = new LinkedHashMap<>();
        for (var r : s.regions.all()) if (r.maxY() >= 64) regions.put(r.id(), r.name());
        Map<String, String> npcs = new LinkedHashMap<>();
        for (var n : s.relations.all()) npcs.put(n.id(), n.name());
        List<String> disc = s.growth.disciplines().stream().map(d -> d.id()).toList();
        List<Map<String, String>> rewards = List.of(Map.of("recipe", "craft_wind_chime", "label", "바람의 노래"),
                Map.of("quest", "hidden.wind_song", "label", "남겨진 악보"), Map.of("title", "길 없는 길의 개척자", "label", "개척자의 표식"),
                Map.of("title", "밤을 걷는 자", "label", "밤의 발자국"), Map.of("place", "forgotten_shrine", "label", "잊힌 사당"));
        long seed = new java.security.SecureRandom().nextLong();
        String yaml = io.versaera.domain.hidden.HiddenRuleGenerator.generate(seed, count,
                new io.versaera.domain.hidden.HiddenRuleGenerator.Pools(counters, regions, npcs, disc, rewards));
        try {
            io.versaera.content.ContentLoader.hidden(io.versaera.content.ContentLoader.parse(yaml, "generated"), "generated");   // 형식 검사
            Files.writeString(new File(dataFolder, "hidden.sealed").toPath(), sealer.seal(yaml), StandardCharsets.UTF_8);
            sender.sendMessage(Ui.info("히든 규칙 " + count + "개 생성 · 봉인 (내용은 보여 주지 않음) · 재시작 후 적용"));
        } catch (IOException | RuntimeException e) {
            sender.sendMessage(Ui.error("생성 실패: " + e.getMessage()));
        }
    }

    /** hidden-src/*.yml 을 합쳐 hidden.sealed 로 봉인 (다음 재시작부터 적용). 원본 폴더는 서버에서 치우라고 알려 준다. */
    private void seal(CommandSender sender) {
        File src = new File(dataFolder, "hidden-src");
        File[] files = src.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) { sender.sendMessage(Ui.error("hidden-src/*.yml 이 없습니다")); return; }
        StringBuilder all = new StringBuilder("hidden:\n");
        try {
            for (File f : files) {
                String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                Map<String, Object> root = io.versaera.content.ContentLoader.parse(text, f.getName());
                io.versaera.content.ContentLoader.hidden(root, f.getName());   // 형식 검사
                for (String line : text.split("\\R")) if (!line.startsWith("hidden:") && !line.isBlank()) all.append(line).append('\n');
            }
            Files.writeString(new File(dataFolder, "hidden.sealed").toPath(), sealer.seal(all.toString()), StandardCharsets.UTF_8);
            sender.sendMessage(Ui.info("봉인 완료 · " + files.length + "개 파일 · 재시작 후 적용"));
            sender.sendMessage(Ui.error("hidden-src 폴더를 서버에서 치우세요 (평문 조건)"));
        } catch (IOException | RuntimeException e) {
            sender.sendMessage(Ui.error("봉인 실패: " + e.getMessage()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] a) {
        if (!sender.hasPermission("versaera.admin")) return List.of();
        if (a.length == 1) return filter(SUBS, a[0]);
        String sub = KOREAN.getOrDefault(a[0], a[0]);
        if (a.length == 2 && sub.equals("npc")) return filter(List.of("소환", "정보"), a[1]);
        if (a.length == 2 && sub.equals("boss")) return filter(List.of("소환", "제거"), a[1]);
        if (a.length == 2 && sub.equals("hidden")) return filter(List.of("생성"), a[1]);
        if (a.length == 3 && sub.equals("give")) return filter(codec.types().all().stream().map(t -> key(t.name())).distinct().sorted().toList(), a[2]);
        if (a.length == 3 && sub.equals("boss")) return filter(s.content.bosses().stream().map(b -> key(b.name())).toList(), a[2]);
        if (a.length == 3 && sub.equals("npc")) return filter(s.relations.all().stream().map(n -> key(n.name())).toList(), a[2]);
        if (a.length == 4 && sub.equals("give")) return List.of("<품질 1~1000>");
        if (a.length == 5 && sub.equals("give")) return List.of("<수량>");
        return null;
    }

    private static List<String> filter(List<String> l, String p) {
        return l.stream().filter(x -> x.contains(p)).limit(200).toList();
    }

    private String typeName(String typeId) {
        return codec.types().has(typeId) ? codec.types().get(typeId).name() : typeId;
    }
}
