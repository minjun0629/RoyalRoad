package io.versaera.domain.terrain;

import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * 중세 판타지 건축 설계도 (WLD-03, ORIGINAL · 순수 계산). 상자 집이 아니라:
 * <ul>
 *   <li>목조 골조 집: 돌 기초 · 돌 1층 · 통나무 골조와 회벽의 위층 · 층마다 처마 받침(뒤집은 계단) · 가파른 박공지붕 · 굴뚝 · 덧문 · 꽃상자 · 문 위 랜턴</li>
 *   <li>여관(큰 3층 · 술통) · 대장간(앞이 트인 돌 작업장 · 용광로 · 모루 · 용암 가마솥) · 성당(종탑과 첨탑 · 스테인드글라스 · 버팀벽)</li>
 *   <li>사막: 평지붕 사암 집 · 차양. 지역 성격으로 재질이 바뀐다 (항구 = 흰 회벽 · 청록 지붕, 산 = 돌 · 심층암 지붕 …)</li>
 * </ul>
 * 모든 설계도는 문이 북쪽(z = 0 쪽)을 보게 그리고, SettlementPlanner 가 길 쪽으로 돌려 세운다. y = 0 은 바닥(지면 높이).
 */
public final class Medieval {
    private Medieval() {
    }

    /** 지역 성격별 재질 */
    public record Palette(String foundation, List<String> stone, String frameWood, String frameLog, List<String> plaster, String roof, String floor,
                          String door, String trapdoor, String glass, List<String> road, boolean flat, String accent) {
        String roofStairs() { return roof + "_stairs"; }

        String roofSlab() { return roof + "_slab"; }

        String roofFull() {
            return switch (roof) {
                case "deepslate_tile", "deepslate_brick", "mud_brick", "stone_brick", "nether_brick", "prismarine_brick" -> roof + "s";
                case "brick" -> "bricks";
                case "cut_sandstone", "smooth_sandstone", "smooth_quartz", "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper" -> roof;
                default -> roof + "_planks";
            };
        }

        String log(String axis) {
            return frameLog + "[axis=" + axis + "]";
        }

        String woodStairs() {
            return frameWood + "_stairs";
        }

        public static Palette of(Region r, RegionIndex idx) {
            Set<String> t = new HashSet<>(r.tags());
            for (Region p = r.parent() == null ? null : idx.byId(r.parent()); p != null; p = p.parent() == null ? null : idx.byId(p.parent())) t.addAll(p.tags());
            List<String> cobble = List.of("cobblestone", "cobblestone", "stone_bricks", "mossy_cobblestone", "andesite");
            if (t.contains("desert"))
                return new Palette("cut_sandstone", List.of("sandstone", "smooth_sandstone", "sandstone"), "jungle", "stripped_jungle_log",
                        List.of("smooth_sandstone", "sandstone", "white_terracotta"), "smooth_sandstone", "smooth_sandstone", "jungle_door", "jungle_trapdoor",
                        "glass_pane", List.of("smooth_sandstone", "sandstone", "cut_sandstone", "sand"), true, "orange_wool");
            if (t.contains("coast") || t.contains("port"))
                return new Palette("stone_bricks", List.of("stone_bricks", "cobblestone", "andesite"), "spruce", "stripped_spruce_log",
                        List.of("white_terracotta", "calcite", "mushroom_stem"), "warped", "spruce_planks", "spruce_door", "spruce_trapdoor",
                        "glass_pane", cobble, false, "light_blue_wool");
            if (t.contains("frozen") || t.contains("mountain") || t.contains("deep"))
                return new Palette("cobbled_deepslate", List.of("stone_bricks", "cracked_stone_bricks", "cobblestone"), "spruce", "spruce_log",
                        List.of("stone_bricks", "polished_andesite", "spruce_planks"), "deepslate_tile", "spruce_planks", "spruce_door", "spruce_trapdoor",
                        "glass_pane", List.of("cobbled_deepslate", "cobblestone", "stone", "gravel"), false, "gray_wool");
            if (t.contains("scholar"))
                return new Palette("stone_bricks", List.of("stone_bricks", "polished_andesite"), "dark_oak", "stripped_dark_oak_log",
                        List.of("calcite", "white_terracotta", "smooth_quartz"), "deepslate_tile", "dark_oak_planks", "dark_oak_door", "dark_oak_trapdoor",
                        "glass_pane", List.of("stone_bricks", "polished_andesite", "andesite", "cobblestone"), false, "purple_wool");
            if (t.contains("fortress") || t.contains("outpost"))
                return new Palette("cobblestone", List.of("stone_bricks", "cobblestone", "cracked_stone_bricks", "mossy_stone_bricks"), "dark_oak",
                        "stripped_dark_oak_log", List.of("stone_bricks", "mushroom_stem", "calcite"), "deepslate_tile", "spruce_planks", "dark_oak_door",
                        "dark_oak_trapdoor", "glass_pane", List.of("cobblestone", "gravel", "stone_bricks", "andesite"), false, "red_wool");
            if (t.contains("forest") || t.contains("highland") || t.contains("artisan"))
                return new Palette("cobblestone", List.of("cobblestone", "mossy_cobblestone", "stone_bricks"), "spruce", "spruce_log",
                        List.of("mushroom_stem", "white_terracotta", "stripped_birch_wood"), "spruce", "spruce_planks", "spruce_door", "spruce_trapdoor",
                        "glass_pane", cobble, false, "green_wool");
            return new Palette("cobblestone", List.of("cobblestone", "stone_bricks", "cobblestone", "andesite"), "dark_oak", "stripped_dark_oak_log",
                    List.of("mushroom_stem", "white_terracotta", "calcite"), "dark_oak", "spruce_planks", "dark_oak_door", "spruce_trapdoor", "glass_pane",
                    cobble, false, "red_wool");
        }
    }

    private static String pick(List<String> l, SplittableRandom r) {
        return l.get(r.nextInt(l.size()));
    }

    private static String stairs(String id, String facing, boolean top) {
        return id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=straight]";
    }

    private static String pane(String glass, boolean alongX) {
        return glass + (alongX ? "[east=true,west=true]" : "[north=true,south=true]");
    }

    // ------------------------------------------------------------------ 목조 골조 집
    /**
     * @param fw 바닥 너비 (x, 앞면) · fd 깊이 (z) · floors 층 수
     */
    public static Blueprint house(Palette p, int fw, int fd, int floors, SplittableRandom rng) {
        if (p.flat()) return desertHouse(p, fw, fd, Math.min(2, floors), rng);
        int W = fw, D = fd, F = floors;
        int roofRows = (D + 3) / 2 + 1;
        Blueprint b = new Blueprint(W + 2, 4 * F + roofRows + 4, D + 2);
        String plaster = pick(p.plaster(), rng);
        int x1 = 1, x2 = W, z1 = 1, z2 = D, door = (x1 + x2) / 2;
        // 바닥 · 기초
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) b.set(x, 0, z, x == x1 || x == x2 || z == z1 || z == z2 ? p.foundation() : p.floor());
        for (int k = 0; k < F; k++) {
            int y0 = 4 * k;
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    boolean edgeX = x == x1 || x == x2, edgeZ = z == z1 || z == z2;
                    if (!edgeX && !edgeZ) {
                        b.fill(x, y0 + 1, z, x, y0 + 3, z, "air");
                        b.set(x, y0 + 4, z, k == F - 1 ? "air" : p.floor());   // 위층 바닥 (맨 위는 다락)
                        continue;
                    }
                    boolean corner = edgeX && edgeZ;
                    int along = edgeZ ? x - x1 : z - z1;
                    boolean post = corner || (k > 0 && along % 3 == 0);
                    for (int y = y0 + 1; y <= y0 + 3; y++) {
                        String m;
                        if (post) m = p.log("y");
                        else if (k == 0) m = pick(p.stone(), rng);
                        else m = plaster;
                        b.set(x, y, z, m);
                    }
                    // 층 사이 들보 (가로 통나무)
                    b.set(x, y0 + 4, z, corner ? p.log("y") : p.log(edgeZ ? "x" : "z"));
                }
            // 창: 위층은 기둥 사이 칸(3 칸 중 가운데 둘)마다 하나 걸러 2×2 창 + 기둥 위 덧문, 1층은 작은 창 + 아래 꽃덤불
            for (int side : new int[]{z1, z2}) {
                int out = side == z1 ? side - 1 : side + 1;
                String face = side == z1 ? "north" : "south";
                for (int x = x1 + 1; x < x2; x++) {
                    int m = (x - x1) % 3, bay = (x - x1) / 3;
                    if (k > 0) {
                        if (m != 1 || bay % 2 == (side == z1 ? 0 : 1) || x + 1 >= x2) continue;
                        for (int dx = 0; dx <= 1; dx++) for (int y = y0 + 2; y <= y0 + 3; y++) b.set(x + dx, y, side, pane(p.glass(), true));
                        for (int sx : new int[]{x - 1, x + 2})
                            for (int y = y0 + 2; y <= y0 + 3; y++) b.soft(sx, y, out, p.trapdoor() + "[facing=" + face + ",open=true,half=bottom]");
                    } else {
                        if (m != 2 || (side == z1 && Math.abs(x - door) <= 1)) continue;
                        b.set(x, y0 + 2, side, pane(p.glass(), true));
                        b.soft(x, y0 + 1, out, "flowering_azalea_leaves[persistent=true]");
                    }
                }
            }
            for (int z = z1 + 2; z < z2; z += 3)
                for (int side : new int[]{x1, x2}) {
                    if (b.get(side, y0 + 2, z) == null || b.get(side, y0 + 2, z).startsWith(p.frameLog())) continue;
                    b.set(side, y0 + 2, z, pane(p.glass(), false));
                    if (k > 0) b.set(side, y0 + 3, z, pane(p.glass(), false));
                }
            // 처마 받침: 층 들보 아래 앞 · 뒤로 뒤집은 계단
            if (k > 0)
                for (int x = x1; x <= x2; x++) {
                    b.soft(x, y0, z1 - 1, stairs(p.woodStairs(), "south", true));
                    b.soft(x, y0, z2 + 1, stairs(p.woodStairs(), "north", true));
                }
            // 천장 랜턴
            b.set(door, y0 + 3, (z1 + z2) / 2, "lantern[hanging=true]");
        }
        // 문 + 문 위 차양 · 랜턴
        b.set(door, 1, z1, p.door() + "[facing=north,half=lower,hinge=left,open=false]");
        b.set(door, 2, z1, p.door() + "[facing=north,half=upper,hinge=left,open=false]");
        b.set(door, 0, z1 - 1, stairs("cobblestone_stairs", "south", false));
        for (int x = door - 1; x <= door + 1; x++) b.set(x, 3, z1 - 1, p.frameWood() + "_slab[type=top]");
        b.set(door + 1, 2, z1 - 1, "lantern[hanging=true]");
        // 박공지붕: 앞뒤로 한 칸씩 처마, 좌우로 한 칸 박공 처마
        int top = 4 * F;
        for (int i = 0; ; i++) {
            int zn = z1 - 1 + i, zs = z2 + 1 - i, y = top + i;
            if (zn > zs) break;
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                if (zn == zs) {
                    b.set(x, y, zn, p.roofFull());
                    b.set(x, y + 1, zn, p.roofSlab() + "[type=bottom]");
                } else {
                    b.set(x, y, zn, stairs(p.roofStairs(), "south", false));
                    b.set(x, y, zs, stairs(p.roofStairs(), "north", false));
                    if (i > 0 && (x == x1 - 1 || x == x2 + 1)) {   // 박공 처마 밑면
                        b.set(x, y - 1, zn, stairs(p.roofStairs(), "north", true));
                        b.set(x, y - 1, zs, stairs(p.roofStairs(), "south", true));
                    }
                }
            }
            // 박공벽 (지붕 아래 삼각형)
            for (int x : new int[]{x1, x2})
                for (int z = Math.max(z1, zn + 1); z <= Math.min(z2, zs - 1); z++) b.set(x, y, z, z == (z1 + z2) / 2 && i == 2 ? pane(p.glass(), false) : plaster);
            if (zn == zs || zn + 1 == zs) {
                if (zn + 1 == zs) for (int x = x1 - 1; x <= x2 + 1; x++) {
                    b.set(x, y + 1, zn, p.roofSlab() + "[type=bottom]");
                    b.set(x, y + 1, zs, p.roofSlab() + "[type=bottom]");
                }
                // 굴뚝
                int cx = x1 + 1, cz = z2 - 1;
                for (int yy = 1; yy <= y + 2; yy++) b.set(cx, yy, cz, yy <= 3 ? "bricks" : "bricks");
                b.set(cx, y + 3, cz, "campfire[lit=true,signal_fire=false,facing=north]");
                b.set(cx, 1, cz - 1, "furnace[facing=north,lit=false]");
                break;
            }
        }
        // 살림: 1층 귀퉁이 통 · 작업대
        b.set(x2 - 1, 1, z2 - 1, "barrel[facing=up]");
        b.set(x2 - 1, 1, z2 - 2, "crafting_table");
        return b;
    }

    /** 사막: 두꺼운 사암 벽 · 평지붕 + 낮은 난간 · 문 위 천 차양 */
    static Blueprint desertHouse(Palette p, int fw, int fd, int floors, SplittableRandom rng) {
        int W = fw, D = fd, F = floors;
        Blueprint b = new Blueprint(W + 2, 4 * F + 3, D + 2);
        int x1 = 1, x2 = W, z1 = 1, z2 = D, door = (x1 + x2) / 2;
        String wall = pick(p.plaster(), rng);
        for (int k = 0; k < F; k++) {
            int y0 = 4 * k;
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    b.set(x, y0, z, k == 0 ? (edge ? p.foundation() : p.floor()) : p.floor());
                    for (int y = y0 + 1; y <= y0 + 3; y++) b.set(x, y, z, edge ? ((x == x1 || x == x2) && (z == z1 || z == z2) ? "cut_sandstone" : wall) : "air");
                    if (edge && k > 0 && y0 % 4 == 0) b.set(x, y0, z, "cut_sandstone");
                }
            for (int x = x1 + 2; x < x2; x += 3) for (int side : new int[]{z1, z2}) if (!(k == 0 && side == z1 && Math.abs(x - door) <= 1)) b.set(x, y0 + 2, side, "jungle_trapdoor[facing=" + (side == z1 ? "north" : "south") + ",open=true,half=bottom]");
            for (int z = z1 + 2; z < z2; z += 3) for (int side : new int[]{x1, x2}) b.set(side, y0 + 2, z, "jungle_trapdoor[facing=" + (side == x1 ? "west" : "east") + ",open=true,half=bottom]");
            b.set(door, y0 + 3, (z1 + z2) / 2, "lantern[hanging=true]");
        }
        int top = 4 * F;
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
            b.set(x, top, z, "smooth_sandstone");
            if (edge) b.set(x, top + 1, z, (x + z) % 2 == 0 ? "sandstone_wall" : "cut_sandstone_slab[type=bottom]");
        }
        b.set(door, 1, z1, p.door() + "[facing=north,half=lower,hinge=left,open=false]");
        b.set(door, 2, z1, p.door() + "[facing=north,half=upper,hinge=left,open=false]");
        for (int x = door - 1; x <= door + 1; x++) b.set(x, 3, z1 - 1, p.accent());   // 천 차양
        b.set(door - 1, 1, z1 - 1, "decorated_pot");
        return b;
    }

    // ------------------------------------------------------------------ 여관 · 대장간
    /** 여관: 큰 3층 집 + 앞에 쌓은 술통 · 간판 기둥 */
    public static Blueprint tavern(Palette p, SplittableRandom rng) {
        Blueprint b = house(p, 11, 9, p.flat() ? 2 : 3, rng);
        int z = 0;
        b.set(1, 1, z, "barrel[facing=up]");
        b.set(2, 1, z, "barrel[facing=up]");
        b.set(1, 2, z, "barrel[facing=north]");
        b.set(11, 1, z, p.frameWood() + "_fence");
        b.set(11, 2, z, p.frameWood() + "_fence");
        b.set(11, 3, z, "lantern[hanging=false]");
        return b;
    }

    /** 대장간: 앞이 트인 돌 작업장 · 용광로 · 모루 · 용암 가마솥 · 숫돌 · 큰 굴뚝 */
    public static Blueprint smithy(Palette p, SplittableRandom rng) {
        int W = 9, D = 7;
        Blueprint b = new Blueprint(W + 2, 12, D + 2);
        int x1 = 1, x2 = W, z1 = 1, z2 = D;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edgeX = x == x1 || x == x2, back = z == z2, front = z == z1;
                b.set(x, 0, z, "stone_bricks");
                for (int y = 1; y <= 4; y++) {
                    String m = "air";
                    if ((edgeX || back) && !(front && !edgeX)) m = (edgeX && (front || back)) ? p.log("y") : pick(List.of("stone_bricks", "cobblestone", "cracked_stone_bricks"), rng);
                    if (front && edgeX) m = p.log("y");
                    b.set(x, y, z, m);
                }
                b.set(x, 5, z, edgeX || front || back ? p.log(front || back ? "x" : "z") : p.floor());
            }
        // 낮은 박공지붕
        for (int i = 0; i <= (D + 2) / 2; i++) {
            int zn = i, zs = D + 1 - i, y = 5 + i;
            if (zn > zs) break;
            for (int x = 0; x <= W + 1; x++) {
                if (zn == zs) b.set(x, y, zn, p.roofSlab() + "[type=bottom]");
                else {
                    b.set(x, y, zn, stairs(p.roofStairs(), "south", false));
                    b.set(x, y, zs, stairs(p.roofStairs(), "north", false));
                }
            }
        }
        // 대장간 살림
        b.set(2, 1, 6, "blast_furnace[facing=south,lit=true]");
        b.set(3, 1, 6, "lava_cauldron");
        b.set(5, 1, 4, "anvil[facing=east]");
        b.set(7, 1, 6, "smithing_table");
        b.set(8, 1, 3, "grindstone[face=floor,facing=north]");
        b.set(8, 1, 6, "barrel[facing=up]");
        b.set(5, 4, 3, "lantern[hanging=true]");
        for (int y = 1; y <= 11; y++) b.set(1, y, 7, y >= 9 ? "bricks" : "cobblestone");
        b.set(1, 12 - 1, 7, "campfire[lit=true,signal_fire=true,facing=north]");
        return b;
    }

    // ------------------------------------------------------------------ 성당
    /** 성당: 긴 본당 · 가파른 지붕 · 뾰족 창 스테인드글라스 · 버팀벽 · 앞쪽 종탑과 첨탑 */
    public static Blueprint chapel(Palette p, SplittableRandom rng) {
        int W = 11, D = 21;
        String[] colors = {"red", "blue", "yellow", "purple", "light_blue", "orange"};
        Blueprint b = new Blueprint(W + 2, 40, D + 2);
        int x1 = 1, x2 = W, z1 = 1, z2 = D, mid = (x1 + x2) / 2, wallTop = 10;
        List<String> stone = List.of("stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                b.set(x, 0, z, edge ? "stone_bricks" : ((x + z) % 2 == 0 ? "polished_andesite" : "stone_bricks"));
                for (int y = 1; y <= wallTop; y++) b.set(x, y, z, edge ? pick(stone, rng) : "air");
            }
        // 옆벽 뾰족 창 + 버팀벽
        for (int z = z1 + 3; z < z2 - 1; z += 3) {
            String glass = colors[(z / 3) % colors.length] + "_stained_glass_pane[north=true,south=true]";
            for (int side : new int[]{x1, x2}) {
                for (int y = 3; y <= 7; y++) b.set(side, y, z, glass);
                int out = side == x1 ? side - 1 : side + 1;
                String face = side == x1 ? "east" : "west";
                for (int y = 1; y <= 5; y++) b.set(out, y, z + 1, "stone_bricks");
                b.set(out, 6, z + 1, stairs("stone_brick_stairs", face, false));
            }
        }
        // 정문 (두 짝) + 장미창
        for (int dx = 0; dx <= 1; dx++) {
            String hinge = dx == 0 ? "left" : "right";
            b.set(mid + dx - 0, 1, z1, "dark_oak_door[facing=north,half=lower,hinge=" + hinge + ",open=false]");
            b.set(mid + dx - 0, 2, z1, "dark_oak_door[facing=north,half=upper,hinge=" + hinge + ",open=false]");
        }
        // 종탑: 앞 가운데 5×5, 높이 22 + 첨탑
        int tx1 = mid - 2, tx2 = mid + 2, tz1 = z1, tz2 = z1 + 4, towerTop = 22;
        for (int x = tx1; x <= tx2; x++)
            for (int z = tz1; z <= tz2; z++) {
                boolean edge = x == tx1 || x == tx2 || z == tz1 || z == tz2;
                for (int y = wallTop + 1; y <= towerTop; y++) {
                    boolean belfry = y >= 17 && y <= 19 && edge && !(x == tx1 || x == tx2) == !(z == tz1 || z == tz2) && x != tx1 && x != tx2 || (y >= 17 && y <= 19 && edge && (z == tz1 || z == tz2) && x != tx1 && x != tx2) || (y >= 17 && y <= 19 && edge && (x == tx1 || x == tx2) && z != tz1 && z != tz2);
                    b.set(x, y, z, edge ? (belfry ? "air" : pick(stone, rng)) : (y == 16 ? "spruce_planks" : "air"));
                }
                b.set(x, towerTop + 1, z, edge ? "stone_brick_slab[type=bottom]" : "stone_bricks");
            }
        b.set(mid, 18, tz1 + 2, "bell[attachment=ceiling,facing=north]");
        b.set(mid, 19, tz1 + 2, "stone_bricks");
        // 장미창 (종탑 앞면)
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (Math.abs(dx) + Math.abs(dy) <= 1)
            b.set(mid + dx, 13 + dy, z1, (dx == 0 && dy == 0 ? "yellow" : "red") + "_stained_glass_pane[east=true,west=true]");
        // 첨탑: 층마다 한 칸씩 좁아지는 피라미드 (지붕 재질)
        for (int i = 0; i <= 3; i++) {
            int y = towerTop + 2 + i * 2;
            for (int x = tx1 + i; x <= tx2 - i; x++)
                for (int z = tz1 + i; z <= tz2 - i; z++) {
                    boolean edge = x == tx1 + i || x == tx2 - i || z == tz1 + i || z == tz2 - i;
                    String face = z == tz1 + i ? "south" : z == tz2 - i ? "north" : x == tx1 + i ? "east" : "west";
                    b.set(x, y, z, edge ? stairs(p.roofStairs(), face, false) : p.roofFull());
                    b.set(x, y + 1, z, edge && x != tx1 + i && x != tx2 - i && z != tz1 + i && z != tz2 - i ? "air" : p.roofFull());
                    if (edge) b.set(x, y + 1, z, x == tx1 + i || x == tx2 - i || z == tz1 + i || z == tz2 - i ? stairs(p.roofStairs(), face, false) : p.roofFull());
                }
        }
        b.set(mid, towerTop + 10, tz1 + 2, p.roofFull());
        b.set(mid, towerTop + 11, tz1 + 2, "lightning_rod[facing=up]");
        // 본당 지붕 (가파름: 한 칸 들어갈 때 한 칸씩)
        for (int i = 0; ; i++) {
            int xa = x1 - 1 + i, xb = x2 + 1 - i, y = wallTop + 1 + i;
            if (xa > xb) break;
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                if (z >= tz1 && z <= tz2 && xa >= tx1 && xb <= tx2) continue;   // 종탑이 지붕을 뚫고 선다
                if (xa == xb) { b.set(xa, y, z, p.roofFull()); continue; }
                boolean tower = z >= tz1 && z <= tz2;
                if (!(tower && xa >= tx1 && xa <= tx2)) b.set(xa, y, z, stairs(p.roofStairs(), "east", false));
                if (!(tower && xb >= tx1 && xb <= tx2)) b.set(xb, y, z, stairs(p.roofStairs(), "west", false));
            }
            for (int x = Math.max(x1, xa + 1); x <= Math.min(x2, xb - 1); x++) {
                b.set(x, y, z2, pick(stone, rng));   // 뒤 박공벽
                if (x < tx1 || x > tx2) b.set(x, y, z1, pick(stone, rng));   // 앞 박공벽 (종탑 양옆)
            }
            if (xa == xb) break;
        }
        // 제단 · 의자 · 샹들리에
        b.set(mid, 1, z2 - 2, "chiseled_stone_bricks");
        b.set(mid, 2, z2 - 2, "candle[candles=3,lit=true]");
        for (int z = tz2 + 2; z < z2 - 3; z += 2) for (int x : new int[]{mid - 3, mid - 2, mid + 2, mid + 3}) b.set(x, 1, z, "dark_oak_stairs[facing=south,half=bottom,shape=straight]");
        for (int z = tz2 + 3; z < z2 - 1; z += 5) b.set(mid, wallTop, z, "lantern[hanging=true]");
        return b;
    }

    // ------------------------------------------------------------------ 시장 노점 · 분수 · 가로등
    /** 노점: 울타리 기둥 네 개 + 줄무늬 천 지붕 + 상품 */
    public static Blueprint stall(Palette p, SplittableRandom rng) {
        Blueprint b = new Blueprint(5, 5, 4);
        String[] cloth = {p.accent(), "white_wool"};
        for (int x = 0; x <= 4; x += 4) for (int z = 0; z <= 3; z += 3) b.fill(x, 1, z, x, 3, z, p.frameWood() + "_fence");
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 3; z++) b.set(x, 4, z, cloth[x % 2]);
        String[] goods = {"hay_block", "melon", "pumpkin", "barrel[facing=up]", "composter", "bee_nest[facing=north,honey_level=0]", "chest[facing=north]"};
        for (int x = 1; x <= 3; x++) b.set(x, 1, 1, goods[rng.nextInt(goods.length)]);
        b.set(2, 3, 2, "lantern[hanging=true]");
        return b;
    }

    /** 광장 분수: 둥근 돌 수반 + 가운데 기둥에서 물이 흘러내린다 (반지름 4) */
    public static Blueprint fountain() {
        Blueprint b = new Blueprint(11, 7, 11);
        int c = 5;
        for (int x = 0; x < 11; x++)
            for (int z = 0; z < 11; z++) {
                double d = Math.hypot(x - c, z - c);
                if (d > 5.2) continue;
                if (d > 4.2) { b.set(x, 0, z, "stone_bricks"); b.set(x, 1, z, "stone_brick_wall"); }
                else { b.set(x, 0, z, "water"); }
            }
        b.fill(c, 0, c, c, 3, c, "chiseled_stone_bricks");
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.set(c + o[0], 3, c + o[1], stairs("stone_brick_stairs", o[0] == 1 ? "west" : o[0] == -1 ? "east" : o[1] == 1 ? "north" : "south", true));
        b.set(c, 4, c, "water");
        return b;
    }

    /** 가로등: 돌 담장 기둥 + 울타리 + 랜턴 */
    public static Blueprint lamp(Palette p) {
        Blueprint b = new Blueprint(1, 5, 1);
        b.set(0, 1, 0, "cobblestone_wall");
        b.set(0, 2, 0, p.frameWood() + "_fence");
        b.set(0, 3, 0, p.frameWood() + "_fence");
        b.set(0, 4, 0, "lantern[hanging=false]");
        return b;
    }
}
