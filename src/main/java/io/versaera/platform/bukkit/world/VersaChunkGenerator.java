package io.versaera.platform.bukkit.world;

import io.versaera.domain.terrain.SettlementPlanner;
import io.versaera.domain.terrain.TerrainModel;
import io.versaera.domain.world.RegionIndex;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지역 데이터로 땅을 만드는 세계 생성기 (WLD-02). bukkit.yml 에서 지정: worlds.&lt;이름&gt;.generator: VersaEra
 * 높이 · 표면은 TerrainModel(순수 계산)이 정하고, 동굴 · 광석 · 나무는 바닐라 단계가 이어서 만든다 (바이옴을 지형에 맞춤).
 * 유적 지역에는 부서진 기둥, 도시 지역에는 광장 · 길 · 건물 · 성벽, 지역마다 랜드마크를 세운다 (SettlementPlanner). 원작 지도를 베끼지 않는다 — regions.yml 의 영역 · 성격만 쓴다.
 * 생성은 여러 스레드에서 동시에 불리므로 TerrainModel 은 불변 객체만 쓴다.
 */
public final class VersaChunkGenerator extends ChunkGenerator {
    private final RegionIndex regions;
    private final List<int[]> keepClear;
    private final Map<String, TerrainModel> models = new ConcurrentHashMap<>();
    private final Map<String, SettlementPlanner> plans = new ConcurrentHashMap<>();
    private final Map<String, BlockData> blocks = new ConcurrentHashMap<>();

    /** @param keepClear NPC 일과 장소 {x, z} — 건물을 짓지 않을 자리 */
    public VersaChunkGenerator(RegionIndex regions, List<int[]> keepClear) {
        this.regions = regions;
        this.keepClear = List.copyOf(keepClear);
    }

    private SettlementPlanner plan(WorldInfo w) {
        return plans.computeIfAbsent(w.getName(), n -> SettlementPlanner.plan(regions, n, w.getSeed(), keepClear));
    }

    /** "STONE_BRICKS" · "oak_stairs[facing=north,half=bottom]" → 블록 상태 (한 번 읽고 기억) */
    private BlockData block(String name) {
        return blocks.computeIfAbsent(name, n -> {
            try {
                return Bukkit.createBlockData("minecraft:" + n.toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return Material.STONE.createBlockData();
            }
        });
    }

    private TerrainModel model(WorldInfo w) {
        return models.computeIfAbsent(w.getName(), n -> new TerrainModel(regions, n, w.getSeed()));
    }

    private static Material top(TerrainModel.Surface s) {
        return switch (s) {
            case GRASS -> Material.GRASS_BLOCK;
            case SAND -> Material.SAND;
            case RED_SAND -> Material.RED_SAND;
            case SNOW -> Material.SNOW_BLOCK;
            case STONE -> Material.STONE;
            case PODZOL -> Material.PODZOL;
            case MUD -> Material.MUD;
            case GRAVEL -> Material.GRAVEL;
            case DIRT_PATH -> Material.COARSE_DIRT;
            case BASALT -> Material.BASALT;
        };
    }

    private static Material under(TerrainModel.Surface s) {
        return switch (s) {
            case SAND, RED_SAND -> Material.SANDSTONE;
            case GRAVEL, STONE, BASALT -> Material.STONE;
            default -> Material.DIRT;
        };
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        TerrainModel t = model(info);
        int min = data.getMinHeight(), max = data.getMaxHeight() - 1;
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkX * 16 + dx, z = chunkZ * 16 + dz;
                int h = Math.max(min + 1, Math.min(max - 1, t.height(x, z)));
                TerrainModel.Surface s = t.surface(x, z, h);
                data.setBlock(dx, min, dz, Material.BEDROCK);
                for (int y = min + 1; y <= h; y++) {
                    Material m = y == h ? top(s) : y >= h - 3 ? under(s) : y < 0 ? Material.DEEPSLATE : Material.STONE;
                    data.setBlock(dx, y, dz, m);
                }
                if (!t.dry(x, z)) for (int y = h + 1; y <= TerrainModel.SEA_LEVEL; y++) data.setBlock(dx, y, dz, s == TerrainModel.Surface.SNOW ? Material.ICE : Material.WATER);
            }
    }

    @Override
    public boolean shouldGenerateCaves() {
        return true;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo w, int x, int y, int z) {
                TerrainModel t = model(w);
                int h = t.height(x, z);
                if (h < TerrainModel.SEA_LEVEL - 8) return Biome.OCEAN;
                return switch (t.surface(x, z, h)) {
                    case SAND -> h <= TerrainModel.SEA_LEVEL + 2 ? Biome.BEACH : Biome.DESERT;
                    case RED_SAND -> Biome.BADLANDS;
                    case SNOW -> Biome.SNOWY_PLAINS;
                    case STONE -> Biome.STONY_PEAKS;
                    case PODZOL -> Biome.OLD_GROWTH_SPRUCE_TAIGA;
                    case BASALT -> Biome.BADLANDS;
                    case GRAVEL -> Biome.WINDSWEPT_GRAVELLY_HILLS;
                    default -> Biome.PLAINS;
                };
            }

            @Override
            public List<Biome> getBiomes(WorldInfo w) {
                return List.of(Biome.OCEAN, Biome.BEACH, Biome.DESERT, Biome.BADLANDS, Biome.SNOWY_PLAINS, Biome.STONY_PEAKS, Biome.OLD_GROWTH_SPRUCE_TAIGA,
                        Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.PLAINS);
            }
        };
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(org.bukkit.World world) {
        return List.of(new BlockPopulator() {
            @Override
            public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
                TerrainModel t = model(info);
                int x0 = chunkX * 16, z0 = chunkZ * 16;
                // 유적 기둥
                int[] p = t.ruinPillar(Math.floorDiv(x0, 64), Math.floorDiv(z0, 64));
                if (p != null && Math.floorDiv(p[0], 16) == chunkX && Math.floorDiv(p[1], 16) == chunkZ) {
                    int base = t.height(p[0], p[1]);
                    for (int y = 1; y <= p[2]; y++)
                        if (region.isInRegion(p[0], base + y, p[1]))
                            region.setType(p[0], base + y, p[1], y == p[2] ? Material.CHISELED_STONE_BRICKS : y % 3 == 0 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS);
                }
                // 도시 · 랜드마크: 이 청크에 걸친 구조물의 열만 그린다
                SettlementPlanner.Sink sink = (x, y, z, m) -> {
                    if (region.isInRegion(x, y, z)) region.setBlockData(x, y, z, block(m));
                };
                for (SettlementPlanner.Structure st : plan(info).in(x0, z0, x0 + 15, z0 + 15))
                    for (int x = Math.max(x0, st.minX); x <= Math.min(x0 + 15, st.maxX); x++)
                        for (int z = Math.max(z0, st.minZ); z <= Math.min(z0 + 15, st.maxZ); z++)
                            if (st.covers(x, z)) st.column(x, z, t::height, sink);
            }
        });
    }
}
