package io.versaera.platform.bukkit;

import io.versaera.application.GameServices;
import io.versaera.content.ContentBundle;
import io.versaera.content.ContentLoader;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.hidden.HiddenRule;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.persistence.Database;
import io.versaera.persistence.DbExecutor;
import io.versaera.persistence.Migrator;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.boss.BossRuntime;
import io.versaera.platform.bukkit.command.AdminCommand;
import io.versaera.platform.bukkit.command.PlayerCommand;
import io.versaera.platform.bukkit.listener.*;
import io.versaera.platform.bukkit.ui.MenuListener;
import io.versaera.platform.bukkit.ui.NpcMenus;
import io.versaera.platform.bukkit.combat.DeathListener;
import io.versaera.platform.bukkit.combat.SkillListener;
import io.versaera.platform.bukkit.command.GameCommands;
import io.versaera.platform.bukkit.dungeon.DungeonRuntime;
import io.versaera.platform.bukkit.map.MapRuntime;
import io.versaera.platform.bukkit.pack.ExternalPack;
import io.versaera.platform.bukkit.pack.PackSender;
import io.versaera.platform.bukkit.pack.PackServer;
import io.versaera.platform.bukkit.world.VersaChunkGenerator;
import io.versaera.platform.bukkit.world.WorldEventRuntime;
import io.versaera.security.Sealer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * VersaEra — 「베르사 새벽기」. RpgCraft 와 코드를 공유하지 않는 독립 플러그인.
 * 시작 순서: DB 열기 → 마이그레이션 → 콘텐츠 → 서비스 → 지난 거래 복구 → 히든 봉인 열기 → 리스너 · 명령.
 * 어느 단계든 실패하면 플러그인을 끄고 이유를 로그에 남긴다 (반쯤 켜진 상태로 돌지 않게).
 */
public final class VersaEraPlugin extends JavaPlugin {
    private Database db;
    private DbExecutor exec;
    private GameServices services;
    private GatherListener gather;
    private BossRuntime bosses;
    private io.versaera.platform.bukkit.world.FieldBossRuntime fieldBosses;
    /** 게임 시각(0 ~ 23). 메인 스레드가 5초마다 갱신하고, DB 스레드의 히든 판정은 이 값만 읽는다 */
    private volatile int gameHour = 12;
    public static final String REALMS = "versa_realms";
    private DungeonRuntime dungeons;
    private WorldEventRuntime events;
    private NpcRuntime npcRuntime;
    private io.versaera.platform.bukkit.world.PetRuntime petRuntime;
    private io.versaera.platform.bukkit.world.TravelRuntime travelRuntime;
    private io.versaera.platform.bukkit.world.ArtworkRuntime artworkRuntime;
    private PackServer pack;

    @Override
    public void onLoad() {
        installDatapack();
    }

    /**
     * 입은 갑옷 모습용 데이터팩 (갑옷 장식 무늬)을 기본 세계의 datapacks 폴더에 쓴다. 세계보다 먼저 읽혀야 하므로
     * 처음 설치하거나 무늬가 바뀐 뒤에는 서버를 한 번 더 켜야 적용된다.
     */
    private void installDatapack() {
        try {
            java.util.Properties props = new java.util.Properties();
            java.io.File sp = new java.io.File(getServer().getWorldContainer(), "server.properties");
            if (!sp.exists()) sp = new java.io.File("server.properties");
            if (sp.exists()) try (var in = new java.io.FileInputStream(sp)) { props.load(in); }
            java.io.File root = new java.io.File(getServer().getWorldContainer(), props.getProperty("level-name", "world") + "/datapacks/versaera");
            var files = io.versaera.pack.ArmorLooks.datapack(io.versaera.content.ContentBundle.fromClasspath(getClass().getClassLoader()));
            boolean changed = false;
            for (var e : files.entrySet()) {
                java.io.File f = new java.io.File(root, e.getKey());
                if (f.exists() && java.util.Arrays.equals(java.nio.file.Files.readAllBytes(f.toPath()), e.getValue())) continue;
                f.getParentFile().mkdirs();
                java.nio.file.Files.write(f.toPath(), e.getValue());
                changed = true;
            }
            if (changed) getLogger().warning("갑옷 모습 데이터팩을 " + root + " 에 설치했습니다 — 서버를 한 번 다시 켜야 입은 갑옷 모습이 보입니다");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "갑옷 모습 데이터팩 설치 실패 (갑옷은 바닐라 모습으로 보입니다)", e);
        }
    }

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            for (String f : ContentBundle.FILES) if (!new File(getDataFolder(), "content/" + f).exists()) saveResource("content/" + f, false);
            db = Database.open("jdbc:sqlite:" + new File(getDataFolder(), "versaera.db").getAbsolutePath());
            int applied = new Migrator(db).migrate(Migrator.fromClasspath(getClassLoader()));
            getLogger().info("DB 마이그레이션 " + applied + "개 적용");
            ContentBundle content = ContentBundle.load(f -> open(new File(getDataFolder(), "content/" + f)));
            ZoneId zone = ZoneId.of(getConfig().getString("timezone", "Asia/Seoul"));
            services = new GameServices(db, content, GameClock.SYSTEM, zone, getLogger());
            // 원작 규칙 (config.yml): 시간 4배 · 원작식 사망 (숙련 · 스탯 하락 · 아이템 드롭)
            services.rules(new io.versaera.application.ServerRules(getConfig().getString("death.mode", "canon"),
                    new io.versaera.domain.time.GameTime(getConfig().getInt("time.ratio", 4))));
            exec = new DbExecutor(getLogger());
            int recovered = exec.submit("recover", services.trades::recover).join();
            if (recovered > 0) getLogger().warning("지난 실행에서 끝나지 않은 거래 " + recovered + "건을 취소하고 아이템을 주인에게 돌려보냈습니다");
            int lostRuns = exec.submit("recover-dungeons", services.dungeons::recover).join();
            int lostFights = exec.submit("recover-bosses", services.bosses::recover).join();
            if (lostRuns + lostFights > 0) getLogger().warning("지난 실행의 던전 " + lostRuns + "판 · 보스 전투 " + lostFights + "건을 실패로 정리했습니다 (보상 없음, 손실 없음)");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "VersaEra 시작 실패 — 플러그인을 끕니다", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Async async = new Async(this, exec);
        io.versaera.platform.bukkit.world.TimeRuntime clock = new io.versaera.platform.bukkit.world.TimeRuntime(this, services);
        Bukkit.getScheduler().runTaskTimer(this, () -> gameHour = clock.hour(), 2L, 20L);
        ItemCodec codec = new ItemCodec(this, services.items.types());
        byte[] key = serverKey();
        Sealer sealer = new Sealer(key);
        services.worldEvents.reseed(java.nio.ByteBuffer.wrap(sha256(key)).getLong());   // 서버마다 다른 이벤트 시간표
        services.weather.reseed(java.nio.ByteBuffer.wrap(sha256(key)).getLong() ^ 0x57EA7E5L);   // 서버마다 다른 날씨 시간표
        SessionListener sessions = new SessionListener(this, services, async, codec);
        RegionTracker regions = new RegionTracker(services, async);
        loadHidden(sealer, regions);
        java.util.Set<String> gated = new java.util.HashSet<>();
        for (var d : services.worldEvents.all()) {
            String rv = d.effects().get("reveal");
            if (rv != null && rv.startsWith("region:")) gated.add(rv.substring(7));
        }
        // 메타페이아: 신기루 도시라 한낮(11~13시)에만 들어갈 수 있다 (ORIGINAL 규칙 — 원작의 등장 조건은 옮기지 않음)
        java.util.Set<String> noonOnly = java.util.Set.of("metapeia", "flame_sanctuary");
        regions.gate(id -> (gated.contains(id) && !services.worldEvents.revealed().contains("region:" + id))
                || (noonOnly.contains(id) && (gameHour < 11 || gameHour > 13)));
        io.versaera.platform.bukkit.listener.OriginListener originL = new io.versaera.platform.bukkit.listener.OriginListener(this, services, async, exec);
        regions.confine(originL::confine);   // 초보 기간: 시작 도시 밖으로 못 나감
        io.versaera.platform.bukkit.listener.ReputationListener repL = new io.versaera.platform.bukkit.listener.ReputationListener(this, services, async);
        NpcListener npcs = new NpcListener(this, services, async);
        gather = new GatherListener(this, services, async, codec, sessions);
        bosses = new BossRuntime(this, services, async);
        CombatListener combat = new CombatListener(this, services, async, codec);
        SkillListener skills = new SkillListener(this, services, async, codec, bosses);
        skills.extraHealth(u -> originL.extraHealth(u) + combat.itemHealth(u));   // 종족 + 장비 체력
        combat.onHealthChanged(skills::reload);
        dungeons = new DungeonRuntime(this, services, async, bosses);
        dungeons.hints(skills::seesHints);
        Bukkit.getScheduler().runTask(this, dungeons::prepareWorld);
        // 다른 차원(거인계 · 신계 · 정령계 · 요정계 · 마계 · 악마계 · 지옥 · 토둠) — 문(gates.yml)으로만 오가는 별도 세계
        if (getConfig().getBoolean("realms.enabled", true))
            Bukkit.getScheduler().runTask(this, () -> new org.bukkit.WorldCreator(REALMS)
                    .generator(getDefaultWorldGenerator(REALMS, null)).generateStructures(false).createWorld());   // load: STARTUP 이라 기본 세계가 생긴 뒤에 만든다
        MapRuntime maps = new MapRuntime(this, services, async);
        events = new WorldEventRuntime(this, services, async);
        npcRuntime = new NpcRuntime(this, services, npcs, () -> gameHour);
        NpcMenus menus = new NpcMenus(services, async, codec, sessions::deliver, p -> facts(p.getUniqueId().toString(), regions));
        npcs.onOpen(menus::open);
        DeathListener deathL = new DeathListener(this, services, async, codec);
        deathL.caches(u -> originL.character(u).map(c -> c.beginner(System.currentTimeMillis())).orElse(false), repL::standing);
        io.versaera.platform.bukkit.command.CanonCommands canonCmd = new io.versaera.platform.bukkit.command.CanonCommands(services, async, originL, repL);
        io.versaera.platform.bukkit.combat.SecretArtRuntime artsR = new io.versaera.platform.bukkit.combat.SecretArtRuntime(this, services, async, codec);
        io.versaera.platform.bukkit.world.IronMenTrial trialR = new io.versaera.platform.bukkit.world.IronMenTrial(this, services, async);
        canonCmd.attach(artsR, trialR);
        artsR.combat(combat);
        // 필드 보스 · 생활 스킬 · 감정 (BOS-02 · SKL-05 · ITM-02)
        fieldBosses = new io.versaera.platform.bukkit.world.FieldBossRuntime(this, services, async, sessions::deliver);
        io.versaera.platform.bukkit.command.LifeCommands lifeCmd = new io.versaera.platform.bukkit.command.LifeCommands(services, async, codec, combat, fieldBosses, sessions::deliver);
        getCommand("menu").setExecutor(new io.versaera.platform.bukkit.ui.MainMenu());
        for (String c : List.of("appraise", "bandage", "whet", "polish", "iron", "roar", "shatter", "fieldboss")) getCommand(c).setExecutor(lifeCmd);
        codec.requirementNames(k -> k.startsWith("mastery.") ? services.growth.discipline(k.substring(8)).name()
                : k.startsWith("stat.") ? services.growth.stats().stream().filter(st -> st.id().equals(k.substring(5))).map(st -> st.name()).findFirst().orElse(k)
                : k.equals("fame") ? "명성" : k);
        services.realm.emperorReward(getConfig().getLong("emperor.reward_gold", 1_000_000));
        io.versaera.platform.bukkit.world.RealmRuntime realmR = new io.versaera.platform.bukkit.world.RealmRuntime(this, services, async, exec);
        io.versaera.platform.bukkit.command.RealmCommands realmCmd = new io.versaera.platform.bukkit.command.RealmCommands(services, async, codec, realmR);
        for (String c : List.of("land", "pshop", "castle", "nation", "emperor")) getCommand(c).setExecutor(realmCmd);
        for (String c : List.of("party", "donate", "gods", "history", "fame", "arts", "trial")) getCommand(c).setExecutor(canonCmd);
        // 모험 확장 (V7): 업적 · 칭호 · 기록 · 펫 · 탈것 · 마차/배 · 날씨 · 레이드 · 길드 창고/의뢰 · 대형 조각
        java.util.function.Function<UUID, java.util.Set<String>> tagsOf = u -> {
            String rid = regions.regionOf(u);
            var r = rid == null ? null : services.regions.byId(rid);
            return r == null ? java.util.Set.of() : r.tags();
        };
        combat.regionOf(regions::regionOf);
        petRuntime = new io.versaera.platform.bukkit.world.PetRuntime(this, services, async, codec, regions::regionOf, tagsOf);
        gather.petSkill(petRuntime::hasSkill);
        travelRuntime = new io.versaera.platform.bukkit.world.TravelRuntime(this, services, async);
        io.versaera.platform.bukkit.world.WeatherRuntime weatherR = new io.versaera.platform.bukkit.world.WeatherRuntime(this, services, regions::regionOf);
        io.versaera.platform.bukkit.world.RaidRuntime raidR = new io.versaera.platform.bukkit.world.RaidRuntime(this, services, async, bosses, regions::regionOf);
        artworkRuntime = new io.versaera.platform.bukkit.world.ArtworkRuntime(this, services, async, codec, regions::regionOf, sessions::deliver);
        io.versaera.platform.bukkit.command.AdventureCommands advCmd = new io.versaera.platform.bukkit.command.AdventureCommands(services, async, codec,
                sessions::deliver, petRuntime, travelRuntime, raidR, weatherR, artworkRuntime);
        for (String c : List.of("achievements", "title", "record", "pet", "mount", "raid", "weather", "gstorage", "gquest", "sculpt")) {
            getCommand(c).setExecutor(advCmd);
            getCommand(c).setTabCompleter(advCmd);
        }
        menus.adventure(advCmd, regions::regionOf);
        io.versaera.platform.bukkit.listener.AdventureListener advL = new io.versaera.platform.bukkit.listener.AdventureListener(this, services, async);
        for (var l : List.<org.bukkit.event.Listener>of(petRuntime, travelRuntime, weatherR, artworkRuntime, advL)) Bukkit.getPluginManager().registerEvents(l, this);
                InventoryGuard guard = new InventoryGuard(this, services, async, codec);
        for (var l : List.of(sessions, guard, new CustodyGuard(this, codec, guard), regions, npcs, gather, combat, bosses, skills,
                deathL, dungeons, maps, originL, repL, artsR, trialR, realmR, fieldBosses, lifeCmd, new io.versaera.platform.bukkit.listener.HeadGear(codec), new io.versaera.platform.bukkit.listener.PotionListener(services, async, codec),
                new io.versaera.platform.bukkit.world.TrainingDummies(this, services, async),
                new StationListener(services, async, codec, sessions), new MenuListener()))
            Bukkit.getPluginManager().registerEvents(l, this);
        startPack();
        // 직업이 바뀌면 전투 효과 · 스킬 목록을 다시 읽는다 (이벤트는 DB 스레드에서 옴)
        services.bus.subscribe(io.versaera.domain.event.GameEvents.JobChanged.class, ev -> {
            combat.warm(ev.uuid());
            Bukkit.getScheduler().runTask(this, () -> {
                Player p = Bukkit.getPlayer(UUID.fromString(ev.uuid()));
                if (p != null) skills.reload(p);
            });
        });
        // NPC 세계 (NPC-05): 5분마다 떠나 있는 상인 다시 셈, 1시간마다 지역 경제 (생산 · 소비가 시장 공급을 움직임) — 모두 DB 스레드
        Bukkit.getScheduler().runTaskTimer(this, () -> async.fire("npc-presence", services.npcWorld::refreshPresence), 20L, 6000L);
        Bukkit.getScheduler().runTaskTimer(this, () -> async.fire("npc-economy", services.npcWorld::economyTick), 2400L, 72000L);
        // 경매 만료: 10분마다 50건씩 (물건은 판매자 배달함으로)
        Bukkit.getScheduler().runTaskTimer(this, () -> async.fire("auction-expire", () -> services.auctions.expire(50)), 1200L, 12000L);
        PlayerCommand pc = new PlayerCommand(services, async, codec, sessions::deliver, maps::give);
        getCommand("versa").setExecutor(pc);
        getCommand("trade").setExecutor(pc);
        GameCommands gc = new GameCommands(services, async, codec, sessions::deliver, p -> facts(p.getUniqueId().toString(), regions), dungeons);
        for (String c : List.of("job", "quest", "guild", "auction", "dungeon")) getCommand(c).setExecutor(gc);
        AdminCommand ac = new AdminCommand(services, async, codec, npcs, bosses, getDataFolder(), sealer, sessions::deliver);
        getCommand("versaadmin").setExecutor(ac);
        getCommand("versaadmin").setTabCompleter(ac);
        for (Player p : Bukkit.getOnlinePlayers()) {   // /reload 대비
            String id = p.getUniqueId().toString();
            async.fire("warm", () -> { combat.warm(id); return null; });
            sessions.deliver(p);
            skills.reload(p);
        }
        getLogger().info("VersaEra 시작 — 지역 " + services.regions.all().size() + " · 레시피 " + services.crafting.all().size()
                + " · 보스 " + services.content.bosses().size() + " · NPC " + services.relations.all().size());
    }

    private static byte[] sha256(byte[] b) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(b);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 리소스팩 배포 (config pack.*).
     * 1) pack.urls (GitHub raw 주소 등) 를 차례로 받아 보고, 처음 받아지는 주소를 쓴다 — SHA-1 은 서버가 직접 계산
     * 2) 모두 실패하거나 비어 있으면 코드로 만든 팩을 내장 HTTP 서버(pack.port)로 내려 준다
     */
    private void startPack() {
        if (!getConfig().getBoolean("pack.enabled", true)) return;
        io.versaera.pack.ResourcePackBuilder.Pack built;
        try {
            built = io.versaera.pack.ResourcePackBuilder.build(services.content);
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "리소스팩을 만들지 못했습니다 — 팩 없이 계속합니다", e);
            return;
        }
        PackSender sender = new PackSender(this);
        Bukkit.getPluginManager().registerEvents(sender, this);
        io.versaera.platform.bukkit.ui.Menu.background = getConfig().getBoolean("pack.menu-background", true);
        sender.prompt(getConfig().getString("pack.prompt", "VersaEra 전용 리소스팩 (아이템 · 보스 · 메뉴 그림)"), getConfig().getBoolean("pack.required", false));
        List<String> urls = getConfig().getBoolean("pack.github", true) ? ExternalPack.withDefaults(getConfig().getStringList("pack.urls")) : List.of();
        if (urls.isEmpty()) {
            hostPack(sender, built);
            return;
        }
        Thread t = new Thread(() -> {
            var found = ExternalPack.resolve(urls, getLogger());
            if (found.isPresent()) {
                var f = found.get();
                sender.set(f.url(), f.sha1());
                getLogger().info("리소스팩 (외부) " + f.size() / 1024 + "KB · sha1 " + ExternalPack.hex(f.sha1()) + " · " + f.url());
                if (!java.util.Arrays.equals(f.sha1(), built.sha1()))
                    getLogger().warning("외부 리소스팩이 이 플러그인이 만든 팩(sha1 " + built.sha1Hex() + ")과 다릅니다. "
                            + "콘텐츠를 바꿨다면 gradle buildPack 으로 VersaEra-ResourcePack.zip 을 다시 만들어 저장소 맨 위에 올리세요.");
            } else {
                getLogger().warning("pack.urls 의 주소를 모두 받지 못했습니다 — 내장 서버로 내려 줍니다");
                Bukkit.getScheduler().runTask(this, () -> hostPack(sender, built));
            }
        }, "versa-pack-check");
        t.setDaemon(true);
        t.start();
    }

    private void hostPack(PackSender sender, io.versaera.pack.ResourcePackBuilder.Pack built) {
        try {
            pack = new PackServer(this, built, getConfig().getInt("pack.port", 8173), getConfig().getString("pack.public-url", ""), Bukkit.getIp());
            sender.set(pack.url(), built.sha1());
            getLogger().info("리소스팩 (내장 서버) " + built.zip().length / 1024 + "KB · sha1 " + built.sha1Hex() + " · " + pack.url());
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.WARNING, "리소스팩 서버를 열지 못했습니다 — 팩 없이 계속합니다", e);
        }
    }

    /** bukkit.yml 의 worlds.<이름>.generator: VersaEra 로 쓰는 지형 생성기 (onEnable 전에 불릴 수 있어 콘텐츠를 따로 읽는다) */
    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        File dir = new File(getDataFolder(), "content");
        ContentBundle c = new File(dir, "regions.yml").exists()
                ? ContentBundle.load(f -> {
                    InputStream in = open(new File(dir, f));
                    return in != null ? in : getClassLoader().getResourceAsStream("content/" + f);
                })
                : ContentBundle.fromClasspath(getClassLoader());
        List<int[]> npcSpots = new java.util.ArrayList<>();
        c.places().values().forEach(m -> m.values().forEach(p -> npcSpots.add(new int[]{(int) Math.floor(p.x()), (int) Math.floor(p.z())})));
        return new VersaChunkGenerator(new io.versaera.domain.world.RegionIndex(c.regions()), npcSpots);
    }

    private static InputStream open(File f) {
        try {
            return new FileInputStream(f);
        } catch (IOException e) {
            return null;
        }
    }

    /** 서버별 봉인 키 (처음 실행 때 생성, 소유자만 읽기) */
    private byte[] serverKey() {
        File f = new File(getDataFolder(), "secret.key");
        try {
            if (f.exists()) return java.util.Base64.getDecoder().decode(Files.readString(f.toPath()).strip());
            byte[] k = Sealer.newKey();
            Files.writeString(f.toPath(), java.util.Base64.getEncoder().encodeToString(k));
            try {
                Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                getLogger().info("이 OS 는 파일 권한을 설정할 수 없습니다: secret.key 를 직접 보호하세요");
            }
            return k;
        } catch (IOException e) {
            throw new IllegalStateException("secret.key 를 읽거나 만들 수 없습니다", e);
        }
    }

    private void loadHidden(Sealer sealer, RegionTracker regions) {
        File f = new File(getDataFolder(), "hidden.sealed");
        if (!f.exists()) {
            getLogger().info("히든 콘텐츠 봉인 파일 없음 (hidden.sealed) — 히든 콘텐츠 없이 시작");
            return;
        }
        try {
            String plain = sealer.open(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            List<HiddenRule> rules = ContentLoader.hidden(ContentLoader.parse(plain, "hidden.sealed"), "hidden.sealed");
            services.attachHidden(rules, uuid -> facts(uuid, regions));
            getLogger().info("히든 규칙 " + rules.size() + "개 (내용은 로그에 남기지 않음)");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "hidden.sealed 를 열 수 없습니다 (키가 다르거나 변조됨) — 히든 콘텐츠 없이 시작", e);
        }
    }

    /** DB 스레드에서 불림 — Bukkit API 는 부르지 않고 메인 스레드가 미리 적어 둔 값만 읽는다 */
    private PlayerFacts facts(String uuid, RegionTracker regions) {
        int hour = gameHour;
        String region = regions.regionOf(UUID.fromString(uuid));
        return new PlayerFacts() {
            public long counter(String key) { return services.growth.counter(uuid, key); }
            public int mastery(String d) { return services.growth.level(uuid, d); }
            public int affinity(String npc) { return services.relations.affinity(uuid, npc); }
            public String region() { return region; }
            public boolean discovered(String kind, String ref) { return services.exploration.discovered(uuid, kind, ref); }
            public int hour() { return hour; }
        };
    }

    @Override
    public void onDisable() {
        if (bosses != null) bosses.stopAll(false);
        if (fieldBosses != null) fieldBosses.shutdown();
        if (dungeons != null) dungeons.stopAll();
        if (events != null) events.stop();
        if (npcRuntime != null) npcRuntime.removeAll();
        if (petRuntime != null) petRuntime.dismissAll();
        if (travelRuntime != null) travelRuntime.shutdown();
        if (artworkRuntime != null) artworkRuntime.shutdown();
        if (pack != null) pack.stop();
        if (gather != null) gather.restoreAll();
        if (exec != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String id = p.getUniqueId().toString();
                exec.submit("shutdown-trade", () -> { services.trades.cancelFor(id, "server stop"); return null; });
            }
            exec.close();   // 남은 DB 작업을 끝까지 기다림
        }
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "DB 닫기 실패", e);
            }
        }
    }
}
