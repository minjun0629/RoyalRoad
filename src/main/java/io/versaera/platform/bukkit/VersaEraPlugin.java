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
    private io.versaera.platform.bukkit.world.BlockRestoreRuntime restore;
    private io.versaera.platform.bukkit.world.FieldMobRuntime fieldMobs;
    private io.versaera.platform.bukkit.world.SculptingRuntime sculpting;
    private StationListener stations;
    /** 세계 이름 → 이 플러그인이 만든 지형 생성기 (진단 /va 마을) */
    private final java.util.Map<String, VersaChunkGenerator> generators = new java.util.concurrent.ConcurrentHashMap<>();
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
        resetIfRequested();
        useOurGenerator();
        installDatapack();
    }

    /**
     * /va 초기화 전체 확인 으로 예약된 전체 초기화: 세계가 읽히기 전(onLoad · load: STARTUP)에 DB 와 세계 폴더를 지운다.
     * 설정 · 콘텐츠 · 봉인 키는 남긴다.
     */
    private void resetIfRequested() {
        java.io.File flag = new java.io.File(getDataFolder(), "reset-all");
        if (!flag.exists()) return;
        try {
            java.util.Properties props = new java.util.Properties();
            java.io.File sp = new java.io.File("server.properties");
            if (sp.exists()) try (var in = new java.io.FileInputStream(sp)) { props.load(in); }
            String level = props.getProperty("level-name", "world");
            List<java.io.File> gone = new java.util.ArrayList<>();
            for (String f : List.of("versaera.db", "versaera.db-wal", "versaera.db-shm", "startup-error.txt")) gone.add(new java.io.File(getDataFolder(), f));
            for (String w : List.of(level, level + "_nether", level + "_the_end", REALMS, "versa_dungeons")) gone.add(new java.io.File(w));
            for (java.io.File f : gone) deleteTree(f.toPath());
            java.nio.file.Files.delete(flag.toPath());
            getLogger().warning("전체 초기화 완료 — DB 와 세계 (" + level + " 외) 를 지웠습니다. 처음부터 시작합니다");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "전체 초기화 실패 — reset-all 을 남겨 두고 다음 시작 때 다시 시도합니다", e);
        }
    }

    private static void deleteTree(java.nio.file.Path p) throws java.io.IOException {
        if (!java.nio.file.Files.exists(p)) return;
        try (var walk = java.nio.file.Files.walk(p)) {
            for (java.nio.file.Path x : walk.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(x);
        }
    }

    /**
     * 기본 세계(server.properties 의 level-name)가 VersaEra 지형으로 만들어지게 bukkit.yml 의 worlds.<이름>.generator 를 채운다.
     * 플러그인은 load: STARTUP 이라 세계보다 먼저 읽힌다 → 서버가 이미 읽어 둔 bukkit.yml 설정(CraftServer.configuration)에도 바로 넣어
     * 같은 시작에서 적용되게 한다. 관리자가 다른 생성기를 적어 두었으면 건드리지 않는다. config: world.auto-generator: false 로 끈다.
     */
    private void useOurGenerator() {
        try {
            java.io.File cfg = new java.io.File(getDataFolder(), "config.yml");
            if (cfg.exists() && !org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfg).getBoolean("world.auto-generator", true)) return;
            java.util.Properties props = new java.util.Properties();
            java.io.File sp = new java.io.File("server.properties");
            if (sp.exists()) try (var in = new java.io.FileInputStream(sp)) { props.load(in); }
            String level = props.getProperty("level-name", "world");
            String key = "worlds." + level + ".generator";
            // 1) 서버가 읽어 둔 설정 (이번 시작)
            org.bukkit.configuration.file.YamlConfiguration live = null;
            try {
                java.lang.reflect.Field f = getServer().getClass().getDeclaredField("configuration");
                f.setAccessible(true);
                if (f.get(getServer()) instanceof org.bukkit.configuration.file.YamlConfiguration y) live = y;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 서버 구현이 다르면 파일만 고친다 (다음 시작부터)
            }
            java.io.File by = new java.io.File("bukkit.yml");
            org.bukkit.configuration.file.YamlConfiguration file = by.exists() ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(by)
                    : new org.bukkit.configuration.file.YamlConfiguration();
            String current = live != null ? live.getString(key) : file.getString(key);
            if (current != null && !current.isBlank()) {
                if (!current.startsWith(getName())) getLogger().warning("bukkit.yml 의 " + key + " = " + current + " — 다른 생성기라 그대로 둡니다 (VersaEra 지형을 쓰려면 지우세요)");
                return;
            }
            if (live != null) live.set(key, getName());
            file.set(key, getName());
            file.save(by);
            java.io.File region = new java.io.File(level, "region");
            boolean old = region.isDirectory() && region.list() != null && region.list().length > 0;
            getLogger().warning("bukkit.yml 에 " + key + ": " + getName() + " 를 넣었습니다" + (live != null ? " (이번 시작부터 적용)" : " (다음 시작부터 적용)"));
            if (old) getLogger().warning("이미 만들어진 세계 '" + level + "' 는 이미 생긴 땅이 그대로 남습니다 — 서버를 끄고 "
                    + level + ", " + level + "_nether, " + level + "_the_end 폴더를 지운 뒤 다시 켜면 처음부터 VersaEra 지형으로 만들어집니다");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "기본 세계 생성기를 지정하지 못했습니다 — bukkit.yml 에 worlds.<세계 이름>.generator: VersaEra 를 직접 적어 주세요", e);
        }
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

    /** 시작에 실패했을 때의 원인 (안전 모드 — 플러그인을 끄지 않고 명령 · 접속 때 알려 준다) */
    private volatile Throwable startupError;

    @Override
    public void onEnable() {
        try {
            start();
        } catch (Throwable t) {
            safeMode(t);
        }
    }

    /**
     * 시작 실패: 플러그인을 끄면 모든 명령이 "internal error" 로만 보여 원인을 알 수 없다 → 켜 둔 채 모든 VersaEra 명령과 관리자 접속 때
     * 원인 한 줄과 로그 위치를 알려 준다.
     */
    private void safeMode(Throwable t) {
        startupError = t;
        getLogger().log(Level.SEVERE, "VersaEra 시작 실패 — 안전 모드로 켜 둡니다 (아래 오류를 개발자에게 보내 주세요)", t);
        String why = describe(t);
        // 전체 오류를 파일로 — 콘솔을 뒤지지 않아도 이 파일 하나만 보내면 된다
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            getDataFolder().mkdirs();
            java.nio.file.Files.writeString(new File(getDataFolder(), "startup-error.txt").toPath(), "VersaEra " + getDescription().getVersion()
                    + " · " + Bukkit.getVersion() + " · Java " + System.getProperty("java.version") + " · " + System.getProperty("os.name") + "\n"
                    + java.time.LocalDateTime.now() + "\n\n" + why + "\n\n" + sw);
        } catch (Exception ignored) {
            // 파일을 못 써도 로그 · 채팅 안내는 남는다
        }
        org.bukkit.command.CommandExecutor tell = (sender, cmd, label, args) -> {
            sender.sendMessage(Ui.error("VersaEra 가 시작하지 못했습니다: " + why));
            sender.sendMessage(Ui.c("&7자세한 내용: plugins/VersaEra/startup-error.txt (이 파일을 보내 주세요)"));
            return true;
        };
        for (Object c : getDescription().getCommands().keySet()) {
            var pc = getCommand(String.valueOf(c));
            if (pc != null) { pc.setExecutor(tell); pc.setTabCompleter((a, b, l, x) -> List.of()); }
        }
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                if (e.getPlayer().isOp()) e.getPlayer().sendMessage(Ui.error("[VersaEra] 시작 실패: " + why + " — plugins/VersaEra/startup-error.txt"));
            }
        }, this);
    }

    /** 예외 한 줄 요약: 종류 · 메시지 · VersaEra 코드의 첫 위치 (원인까지 따라감) */
    static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String where = "";
        for (StackTraceElement el : root.getStackTrace())
            if (el.getClassName().startsWith("io.versaera")) { where = " @ " + el.getClassName().substring(el.getClassName().lastIndexOf('.') + 1) + ":" + el.getLineNumber(); break; }
        String msg = t == root ? String.valueOf(root.getMessage()) : t.getMessage() + " ← " + root.getMessage();
        return root.getClass().getSimpleName() + ": " + msg + where;
    }

    /**
     * plugins/VersaEra/content/*.yml 을 플러그인에 든 버전에 맞춘다.
     * 우리가 마지막으로 쓴 그대로(손대지 않음)면 새 버전으로 바꾸고, 관리자가 고친 파일은 content/backup-시각/ 에 옮겨 둔 뒤 바꾼다
     * (옛 yml 이 새 코드와 맞지 않아 시작이 실패하는 것을 막는다). 마지막으로 쓴 해시는 content/.bundled 에 남긴다.
     */
    private void syncContent() throws Exception {
        File dir = new File(getDataFolder(), "content"), marks = new File(dir, ".bundled");
        marks.mkdirs();
        File backup = new File(dir, "backup-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        for (String f : ContentBundle.FILES) {
            byte[] bundled;
            try (InputStream in = getResource("content/" + f)) {
                if (in == null) continue;
                bundled = in.readAllBytes();
            }
            File target = new File(dir, f), mark = new File(marks, f + ".sha1");
            String want = hex(sha256(bundled));
            if (target.exists()) {
                String have = hex(sha256(java.nio.file.Files.readAllBytes(target.toPath())));
                if (have.equals(want)) { java.nio.file.Files.writeString(mark.toPath(), want); continue; }
                String last = mark.exists() ? java.nio.file.Files.readString(mark.toPath()).strip() : "";
                if (!have.equals(last)) {   // 관리자가 고친 파일 (또는 표시가 없는 옛 버전) → 백업
                    backup.mkdirs();
                    java.nio.file.Files.copy(target.toPath(), new File(backup, f).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    getLogger().warning("content/" + f + " 를 새 버전으로 바꿨습니다 — 예전 파일은 " + backup.getName() + "/ 에 있습니다");
                }
            }
            target.getParentFile().mkdirs();
            java.nio.file.Files.write(target.toPath(), bundled);
            java.nio.file.Files.writeString(mark.toPath(), want);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private void start() throws Exception {
        {
            saveDefaultConfig();
            new File(getDataFolder(), "startup-error.txt").delete();
            syncContent();
            db = Database.open("jdbc:sqlite:" + new File(getDataFolder(), "versaera.db").getAbsolutePath());
            int applied = new Migrator(db).migrate(Migrator.fromClasspath(getClassLoader()));
            getLogger().info("DB 마이그레이션 " + applied + "개 적용");
            ContentBundle content;
            try {
                content = ContentBundle.load(f -> open(new File(getDataFolder(), "content/" + f)));
            } catch (RuntimeException e) {   // 고친 yml 에 오류 → 플러그인에 든 콘텐츠로라도 켠다
                getLogger().log(Level.SEVERE, "content/ 의 yml 에 오류가 있어 플러그인에 든 기본 콘텐츠로 켭니다: " + describe(e), e);
                content = ContentBundle.fromClasspath(getClassLoader());
            }
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
        artsR.pets(petRuntime);
        travelRuntime = new io.versaera.platform.bukkit.world.TravelRuntime(this, services, async);
        io.versaera.platform.bukkit.world.WeatherRuntime weatherR = new io.versaera.platform.bukkit.world.WeatherRuntime(this, services, regions::regionOf);
        io.versaera.platform.bukkit.world.RaidRuntime raidR = new io.versaera.platform.bukkit.world.RaidRuntime(this, services, async, bosses, regions::regionOf);
        artworkRuntime = new io.versaera.platform.bukkit.world.ArtworkRuntime(this, services, async, codec, regions::regionOf, sessions::deliver);
        sculpting = new io.versaera.platform.bukkit.world.SculptingRuntime(this, services, async, codec, artworkRuntime, regions::regionOf, sessions::deliver);
        sculpting.pets(petRuntime);
        Bukkit.getPluginManager().registerEvents(sculpting, this);
        io.versaera.platform.bukkit.command.AdventureCommands advCmd = new io.versaera.platform.bukkit.command.AdventureCommands(services, async, codec,
                sessions::deliver, petRuntime, travelRuntime, raidR, weatherR, artworkRuntime);
        for (String c : List.of("achievements", "title", "record", "pet", "mount", "raid", "weather", "gstorage", "gquest", "sculpt")) {
            getCommand(c).setExecutor(advCmd);
            getCommand(c).setTabCompleter(advCmd);
        }
        menus.adventure(advCmd, regions::regionOf);
        io.versaera.platform.bukkit.listener.AdventureListener advL = new io.versaera.platform.bukkit.listener.AdventureListener(this, services, async);
        if (getConfig().getBoolean("hunger.enabled", true))
            Bukkit.getPluginManager().registerEvents(new io.versaera.platform.bukkit.world.HungerRuntime(this, services,
                    getConfig().getDouble("hunger.hours_per_meal", 8), getConfig().getDouble("hunger.activity_scale", 0.5)), this);
        for (var l : List.<org.bukkit.event.Listener>of(petRuntime, travelRuntime, weatherR, artworkRuntime, advL)) Bukkit.getPluginManager().registerEvents(l, this);
        if (getConfig().getBoolean("restore.enabled", true)) {
            final GatherListener g = gather;
            restore = new io.versaera.platform.bukkit.world.BlockRestoreRuntime(this, getConfig().getLong("restore.delay_seconds", 180),
                    getConfig().getBoolean("restore.drops", false), b -> realmR.ownerAt(b).isPresent(), g::node);
            Bukkit.getPluginManager().registerEvents(restore, this);
        }
        Bukkit.getPluginManager().registerEvents(new io.versaera.platform.bukkit.listener.ServerIcon(getLogger()), this);
        if (getConfig().getBoolean("field-mobs.enabled", true)) {
            fieldMobs = new io.versaera.platform.bukkit.world.FieldMobRuntime(this, services, codec);
            Bukkit.getPluginManager().registerEvents(fieldMobs, this);
        }
                InventoryGuard guard = new InventoryGuard(this, services, async, codec);
        for (var l : List.of(sessions, guard, new CustodyGuard(this, codec, guard), regions, npcs, gather, combat, bosses, skills,
                deathL, dungeons, maps, originL, repL, artsR, trialR, realmR, fieldBosses, lifeCmd, new io.versaera.platform.bukkit.listener.HeadGear(codec), new io.versaera.platform.bukkit.listener.PotionListener(services, async, codec),
                new io.versaera.platform.bukkit.world.TrainingDummies(this, services, async),
                (stations = new StationListener(this, services, async, codec, sessions)), new MenuListener()))
            Bukkit.getPluginManager().registerEvents(l, this);
        try {
            startPack();   // 리소스팩은 없어도 게임은 돈다 — 실패해도 나머지는 켠다
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "리소스팩 준비 실패 — 팩 없이 계속합니다: " + describe(t), t);
        }
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
        for (String c : List.of("job", "quest", "guild", "auction", "dungeon", "mailbox")) getCommand(c).setExecutor(gc);
        AdminCommand ac = new AdminCommand(services, async, codec, npcs, bosses, getDataFolder(), sealer, sessions::deliver);
        ac.townReport(this::townReport);
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
        } catch (RuntimeException | Error e) {
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

    /** /va 마을: 서 있는 도시의 배치 · 실제 블록 · 생성기 상태 */
    private void townReport(Player p) {
        org.bukkit.World w = p.getWorld();
        VersaChunkGenerator g = generators.get(w.getName());
        boolean ours = w.getGenerator() instanceof VersaChunkGenerator;
        p.sendMessage(Ui.c("&6── 마을 진단 &7(" + w.getName() + ")"));
        p.sendMessage(Ui.c("&7생성기: " + (ours ? "&aVersaEra" : "&c" + (w.getGenerator() == null ? "바닐라" : w.getGenerator().getClass().getSimpleName())
                + " &7— 이 세계는 VersaEra 지형으로 만들어지지 않았습니다 (/va 초기화 전체 확인 후 재시작)")));
        var here = services.regions.at(w.getName(), p.getLocation().getBlockX(), 64, p.getLocation().getBlockZ());
        io.versaera.domain.world.Region town = null;
        for (var r = here; r != null; r = r.parent() == null ? null : services.regions.byId(r.parent()))
            if (io.versaera.domain.terrain.SettlementPlanner.isTown(r)) { town = r; break; }
        if (town == null) { p.sendMessage(Ui.c("&7도시 지역 밖입니다 (" + (here == null ? "-" : here.name()) + ")")); return; }
        int[] grid = io.versaera.domain.terrain.SettlementPlanner.townGrid(town);
        p.sendMessage(Ui.c("&f" + town.name() + " &7가운데 " + grid[0] + ", " + grid[1] + " · 반지름 " + grid[2]));
        if (g == null) return;
        String id = town.id();
        var plan = g.planOf(w.getName(), w.getSeed()).structures().stream().filter(st -> st.region.equals(id)).toList();
        java.util.Map<io.versaera.domain.terrain.SettlementPlanner.Kind, Long> n = new java.util.EnumMap<>(io.versaera.domain.terrain.SettlementPlanner.Kind.class);
        for (var st : plan) n.merge(st.kind, 1L, Long::sum);
        p.sendMessage(Ui.c("&7계획: " + n));
        // 가장 가까운 건물 하나가 실제로 서 있는지
        var near = plan.stream().filter(st -> st.kind == io.versaera.domain.terrain.SettlementPlanner.Kind.BUILDING)
                .min(java.util.Comparator.comparingDouble(st -> Math.hypot((st.minX + st.maxX) / 2.0 - p.getLocation().getX(), (st.minZ + st.maxZ) / 2.0 - p.getLocation().getZ())));
        near.ifPresent(st -> {
            int x = (st.minX + st.maxX) / 2, z = (st.minZ + st.maxZ) / 2, solid = 0;
            int top = w.getHighestBlockYAt(x, z);
            for (int y = top - 12; y <= top; y++) if (!w.getBlockAt(x, y, z).getType().isAir()) solid++;
            p.sendMessage(Ui.c("&7가까운 건물 " + x + ", " + z + " · 꼭대기 " + w.getBlockAt(x, top, z).getType().name().toLowerCase() + " (" + top + ")"));
        });
        p.sendMessage(Ui.c("&7꾸미기 실패: " + g.failures()));
    }

    /** bukkit.yml 의 worlds.<이름>.generator: VersaEra 로 쓰는 지형 생성기 (onEnable 전에 불릴 수 있어 콘텐츠를 따로 읽는다) */
    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        File dir = new File(getDataFolder(), "content");
        java.util.function.Function<String, InputStream> opener = new File(dir, "regions.yml").exists() ? f -> {
            InputStream in = open(new File(dir, f));
            return in != null ? in : getClassLoader().getResourceAsStream("content/" + f);
        } : f -> getClassLoader().getResourceAsStream("content/" + f);
        ContentBundle c = ContentBundle.load(opener);
        List<int[]> npcSpots = new java.util.ArrayList<>();
        // 손으로 둔 NPC 자리만 비워 둔다 (생성 주민 수천 명의 자리까지 비우면 건물이 거의 서지 못한다)
        ContentBundle.handPlaces(opener).values().forEach(m -> m.values().forEach(p -> npcSpots.add(new int[]{(int) Math.floor(p.x()), (int) Math.floor(p.z())})));
        java.util.Set<String> starts = new java.util.HashSet<>();
        for (var city : c.origins().cities()) starts.add(city.region());
        List<int[]> crowd = new java.util.ArrayList<>();   // 생성 주민 자리: 작은 장식만 비켜 선다
        c.places().values().forEach(m -> m.values().forEach(p -> crowd.add(new int[]{(int) Math.floor(p.x()), (int) Math.floor(p.z())})));
        VersaChunkGenerator g = new VersaChunkGenerator(new io.versaera.domain.world.RegionIndex(c.regions()), npcSpots, starts, crowd);
        generators.put(worldName, g);
        return g;
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
        if (sculpting != null) sculpting.shutdown();
        if (stations != null) stations.shutdown();
        if (artworkRuntime != null) artworkRuntime.shutdown();
        if (pack != null) pack.stop();
        if (gather != null) gather.restoreAll();
        if (restore != null) restore.restoreAll();
        if (fieldMobs != null) fieldMobs.removeAll();
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
