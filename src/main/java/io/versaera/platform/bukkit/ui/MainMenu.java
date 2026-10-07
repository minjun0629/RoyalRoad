package io.versaera.platform.bukkit.ui;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 메인 메뉴 (/메뉴, UI-01). 리소스팩 아이콘 · 배경을 쓰는 단추판 — 누르면 해당 명령을 대신 친다.
 * 명령을 외우지 않아도 모든 기능에 닿게 하려는 것 (설명 문장 없이 아이콘 + 짧은 이름).
 */
public final class MainMenu implements CommandExecutor {
    private record Button(int slot, String icon, Material fallback, String name, String command) {}

    private static final List<Button> BUTTONS = List.of(
            new Button(10, "character", Material.PLAYER_HEAD, "&f캐릭터", "versa"),
            new Button(11, "stat", Material.NETHER_STAR, "&f명성 · 출신", "fame"),
            new Button(12, "quest", Material.WRITABLE_BOOK, "&f의뢰", "quest"),
            new Button(13, "arts", Material.ENCHANTED_BOOK, "&d비기", "arts"),
            new Button(14, "map_known", Material.FILLED_MAP, "&f지도책", "versa 지도책"),
            new Button(15, "fieldboss", Material.WITHER_SKELETON_SKULL, "&c필드 보스", "fieldboss"),
            new Button(16, "trial", Material.IRON_SWORD, "&f철인 100명", "trial"),
            new Button(19, "party", Material.CAKE, "&a파티", "party"),
            new Button(20, "guild", Material.WHITE_BANNER, "&b길드", "guild"),
            new Button(21, "auction", Material.GOLD_INGOT, "&e경매", "auction"),
            new Button(22, "appraise", Material.SPYGLASS, "&b감정 (손에 든 것)", "appraise"),
            new Button(23, "bandage", Material.PAPER, "&a붕대 감기", "bandage"),
            new Button(24, "gods", Material.SUNFLOWER, "&e신 · 신전", "gods"),
            new Button(25, "history", Material.BOOK, "&f연대기", "history"),
            new Button(29, "land", Material.GRASS_BLOCK, "&a땅", "land"),
            new Button(30, "castle", Material.STONE_BRICKS, "&7성", "castle 목록"),
            new Button(31, "nation", Material.GOLDEN_HELMET, "&6국가", "nation"),
            new Button(32, "reputation", Material.SHIELD, "&6황제", "emperor"),
            new Button(33, "job", Material.CRAFTING_TABLE, "&f직업", "job"));

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) return true;
        open(p);
        return true;
    }

    public static void open(Player p) {
        Menu m = new Menu(6, "&8베르사");   // 6줄 = 팩 배경(menu6)과 크기가 맞는다
        for (Button b : BUTTONS)
            m.set(b.slot(), Menu.ui(b.icon(), b.fallback(), b.name(), List.of("&8/" + b.command())), e -> {
                p.closeInventory();
                p.performCommand(b.command());
            });
        m.set(49, Menu.ui("close", Material.BARRIER, "&c닫기", List.of()), e -> p.closeInventory());
        m.open(p);
    }
}
