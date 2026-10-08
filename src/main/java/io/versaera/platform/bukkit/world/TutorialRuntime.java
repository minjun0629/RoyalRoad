package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.application.TutorialService;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 튜토리얼 화면 (TutorialService): 위쪽 막대에 지금 단계 · 진행, 단계를 열 때 할 일 안내와 필요한 물건(연습용 목검 · 베틀),
 * 끝낼 때 보상 알림. 1 초마다 살핀다. /튜토리얼 · /튜토리얼 건너뛰기.
 */
public final class TutorialRuntime implements Listener, CommandExecutor {
    /** 단계를 열 때 보이는 안내 */
    private static final Map<String, List<String>> HINTS = Map.of(
            "tutorial.1_training", List.of("&e[튜토리얼] &f베르사 대륙에 온 것을 환영합니다!",
                    "&7마을 광장 &f북서쪽 훈련장&7의 허수아비를 쳐 보세요 — 칠수록 &f힘&7이 붙고 검술 숙련이 오릅니다.",
                    "&7연습용 목검을 배달함으로 보냈습니다 (가방에 자리가 있으면 바로 들어옵니다). 모든 기능은 &f/메뉴"),
            "tutorial.2_first_hunt", List.of("&e[튜토리얼] &f성문 밖 들판에 토끼 · 여우가 삽니다. 다섯 마리를 잡아 보세요.",
                    "&7몬스터는 레벨에 맞는 상대일수록 경험치가 많고, 너무 약한 상대는 거의 주지 않습니다."),
            "tutorial.3_flax", List.of("&e[튜토리얼] &f들판의 &a풀&f을 부수면 아마 섬유가 나옵니다 (채집 숙련).",
                    "&7나무 · 돌 · 광석도 같은 방식으로 캡니다 — 알맞은 도구가 있으면 더 많이."),
            "tutorial.4_weave", List.of("&e[튜토리얼] &f베틀을 드렸습니다. 땅에 놓고 &f우클릭&7 → &f리넨 짜기&7 (아마 섬유 3개).",
                    "&7작업대마다 분야가 다릅니다: 모루=대장 · 베틀=재봉 · 훈연기=요리 · 양조기=연금 · 화살 작업대=목공 · 숫돌=보석 세공 …"),
            "tutorial.5_people", List.of("&e[튜토리얼] &f마을 사람(NPC)을 &f우클릭&7해 이야기해 보세요. 셋이면 됩니다.",
                    "&7사람마다 하는 일 · 좋아하는 것이 있고, 친해지면 의뢰 · 할인 · 비밀을 알려 줍니다."),
            "tutorial.6_skill", List.of("&e[튜토리얼] &f무기를 들고 &fF&7(손 바꾸기 키) = 기술 1 · &f웅크리고 우클릭&7 = 기술 2 · &f웅크리고 F&7 = 회피.",
                    "&7좌클릭 · 웅크리고 좌클릭을 이어 누르면 콤보 마무리가 나갑니다. 기술을 세 번 써 보세요."),
            "tutorial.7_graduation", List.of("&e[튜토리얼] &f마지막 — 몬스터 스무 마리.",
                    "&7초보 기간(게임 30일) 동안은 시작 도시 안에서만 다니고, 죽어도 숙련 · 아이템을 잃지 않습니다.",
                    "&7그 뒤에는 성 밖 넓은 대륙으로 — 숙련 · 직업 · 의뢰 · 필드 보스 · 던전이 기다립니다."));

    private final GameServices s;
    private final Async async;
    private final Consumer<Player> deliver;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> busy = new ConcurrentHashMap<>();

    public TutorialRuntime(Plugin plugin, GameServices s, Async async, Consumer<Player> deliver) {
        this.s = s;
        this.async = async;
        this.deliver = deliver;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);   // 1초마다 (허수아비 · 사냥 수가 바로 보이게)
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            if (busy.putIfAbsent(u, true) != null) continue;
            String id = u.toString(), name = p.getName();
            async.run("tutorial", () -> {
                try {
                    return s.tutorial.tick(id, name);
                } catch (RuntimeException ex) {   // 한 번 실패해도 다음 살핌은 계속 (막대가 멈추지 않게)
                    Bukkit.getLogger().warning("[VersaEra] 튜토리얼 " + name + ": " + ex.getMessage());
                    return null;
                }
            }, v -> {
                busy.remove(u);
                if (v != null && p.isOnline()) show(p, v);
            }, null);
        }
    }

    private void show(Player p, TutorialService.View v) {
        if (v.finished() != null) {
            p.sendMessage(Ui.c("&a✔ " + v.finished().title() + " &7완료" + reward(v.reward())));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            deliver.accept(p);
        }
        if (v.started() != null) {
            for (String line : HINTS.getOrDefault(v.started().id(), List.of())) p.sendMessage(Ui.c(line));
            give(p, v.started().id());
        }
        BossBar bar = bars.get(p.getUniqueId());
        if (v.done()) {
            if (bar != null) {
                bar.removeAll();
                bars.remove(p.getUniqueId());
                if (v.finished() != null) p.sendMessage(Ui.c("&6튜토리얼을 마쳤습니다! &7이제 베르사 대륙은 당신의 것 — &f/메뉴"));
            }
            return;
        }
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10);
            bar.addPlayer(p);
            bars.put(p.getUniqueId(), bar);
        }
        QuestDefinition q = v.current();
        StringBuilder sb = new StringBuilder(q.title());
        int have = 0, need = 0;
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestDefinition.Objective o = q.objectives().get(i);
            sb.append(" · ").append(o.label()).append(" ").append(v.progress()[i]).append("/").append(o.amount());
            have += v.progress()[i];
            need += o.amount();
        }
        bar.setTitle(Ui.c("&a" + sb));
        bar.setProgress(need == 0 ? 1 : Math.max(0, Math.min(1, have / (double) need)));
    }

    private static String reward(QuestDefinition.Reward r) {
        if (r == null) return "";
        StringBuilder sb = new StringBuilder();
        if (r.money() > 0) sb.append(" &e+").append(io.versaera.domain.economy.Money.format(r.money()));
        if (!r.items().isEmpty()) sb.append(" &d+ 물건 ").append(r.items().size()).append("가지 (배달함)");
        r.xp().forEach((d, x) -> sb.append(" &b+").append(x).append(" ").append(d));
        return sb.toString();
    }

    /** 단계에 필요한 물건 */
    private void give(Player p, String step) {
        switch (step) {
            case "tutorial.1_training" -> {
                String id = p.getUniqueId().toString(), name = p.getName();
                async.run("tutorial-sword", () -> s.items.create("practice_sword", 400, null, "훈련 교관", "tutorial", Map.of(), id, "tutorial:practice_sword:" + id),
                        it -> { if (p.isOnline()) deliver.accept(p); }, null);
            }
            case "tutorial.4_weave" -> {
                ItemStack loom = new ItemStack(Material.LOOM);
                var meta = loom.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Ui.c("&f베틀 &7(재봉 작업대)"));
                    meta.setLore(List.of(Ui.c("&7땅에 놓고 우클릭 → 리넨 짜기")));
                    loom.setItemMeta(meta);
                }
                var left = p.getInventory().addItem(loom);
                left.values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
            }
            default -> { }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BossBar b = bars.remove(e.getPlayer().getUniqueId());
        if (b != null) b.removeAll();
        busy.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        String id = p.getUniqueId().toString();
        if (args.length > 0 && (args[0].equals("건너뛰기") || args[0].equalsIgnoreCase("skip"))) {
            async.run("tutorial-skip", () -> { s.tutorial.skip(id); return null; }, v -> {
                BossBar b = bars.remove(p.getUniqueId());
                if (b != null) b.removeAll();
                p.sendMessage(Ui.c("&7튜토리얼을 건너뛰었습니다. 모든 기능은 &f/메뉴"));
            }, p);
            return true;
        }
        p.sendMessage(Ui.c("&e튜토리얼 &7— 위쪽 초록 막대가 지금 할 일입니다. 건너뛰려면 &f/튜토리얼 건너뛰기"));
        for (QuestDefinition q : s.tutorial.steps()) p.sendMessage(Ui.c("&8· &7" + q.title()));
        return true;
    }
}
