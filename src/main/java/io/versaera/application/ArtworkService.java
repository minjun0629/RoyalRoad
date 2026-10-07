package io.versaera.application;

import io.versaera.application.port.AdventureRepository;
import io.versaera.application.port.AdventureRepository.Artwork;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.art.ArtMaterials;
import io.versaera.domain.art.ArtworkKind;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.item.ItemType;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 대형 조각 작품 (ART-02): 여러 재료로 만들어 세상에 세우고, 다른 사람이 감상한다.
 * <ul>
 *   <li>재료는 플랫폼이 인벤토리에서 먼저 뺀다. 실패하면 모두 배달함으로 돌려준다</li>
 *   <li>품질 = 재료 품질(개수 가중) × 조각 숙련 · 예술 스탯 × 손 떨림(정밀 스탯이면 절반)</li>
 *   <li>남의 땅에는 세울 수 없고, 다른 작품과 6 블록 안에도 세울 수 없다</li>
 *   <li>감상: 사람마다 작품마다 하루 한 번 → 감상한 사람 예술 경험 · 버프, 만든 사람 명성 +1, 그 지역 번영</li>
 * </ul>
 * 스레드: DB 스레드 (all() 은 어느 스레드에서나 — 복사본 목록).
 */
public final class ArtworkService {
    /** 플랫폼이 고른 재료 한 자리 */
    public record Pick(String slot, String typeId, int quality, int amount) {}

    /** 감상 결과: buffMinutes 0 이면 오늘 이미 봄 */
    public record View(Artwork artwork, String owner, int buffMinutes, int buffLevel, boolean fresh) {}

    public static final int MIN_GAP = 6;

    private final TxRunner tx;
    private final AdventureRepository repo;
    private final GameServices s;
    private final GameClock clock;
    private final ZoneId zone;
    private final Map<String, ArtworkKind> kinds = new LinkedHashMap<>();
    private final List<Artwork> cache = new CopyOnWriteArrayList<>();
    private volatile boolean loaded;

    ArtworkService(TxRunner tx, AdventureRepository repo, GameServices s, List<ArtworkKind> list, GameClock clock, ZoneId zone) {
        this.tx = tx;
        this.repo = repo;
        this.s = s;
        this.clock = clock;
        this.zone = zone;
        for (ArtworkKind k : list) DomainException.require(kinds.putIfAbsent(k.id(), k) == null, "art.dup", "작품 종류 중복: " + k.id());
    }

    public Collection<ArtworkKind> kinds() {
        return kinds.values();
    }

    public ArtworkKind kind(String id) {
        ArtworkKind k = kinds.get(id);
        DomainException.require(k != null, "art.unknown", "없는 작품 종류: " + id);
        return k;
    }

    /** DB 스레드에서 한 번 (서버 시작) */
    public List<Artwork> load() {
        cache.clear();
        cache.addAll(repo.artworks());
        loaded = true;
        return List.copyOf(cache);
    }

    /** 어느 스레드에서나: 세워진 작품들 (load 뒤) */
    public List<Artwork> all() {
        return List.copyOf(cache);
    }

    private long day() {
        return Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate().toEpochDay();
    }

    /** 재료 자리 → 모양 (리소스팩 모델 고르기). 저장 형식 "pedestal=marble:marble_block:700,body=…" */
    public static Map<String, String> looks(Artwork a) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : a.materials().split(",")) {
            String[] kv = part.split("=");
            if (kv.length == 2) out.put(kv[0], kv[1].split(":")[0]);
        }
        return out;
    }

    /**
     * 만들기. 재료는 이미 인벤토리에서 빠졌다 — 실패하면 모두 돌려준다.
     * @param roll 서버가 만든 0~1 난수 (손 떨림)
     */
    public Artwork create(String uuid, String kindId, List<Pick> picks, String world, int x, int y, int z, int yaw, String title, String region,
                          double roll) {
        boolean[] done = {false};
        try {
            Artwork a = doCreate(uuid, kindId, picks, world, x, y, z, yaw, title, region, roll);
            done[0] = true;
            return a;
        } finally {
            if (!done[0]) for (Pick p : picks) if (p.amount() > 0 && p.amount() <= 64 * 36) s.items.deliverBulk(uuid, p.typeId(), p.quality(), p.amount(), "art_refund");
        }
    }

    private Artwork doCreate(String uuid, String kindId, List<Pick> picks, String world, int x, int y, int z, int yaw, String title, String region,
                             double roll) {
        ArtworkKind k = kind(kindId);
        int lv = s.growth.level(uuid, "sculpting");
        DomainException.require(lv >= k.level(), "art.level", k.name() + " 은(는) 조각 " + k.level() + " 이 있어야 합니다 (지금 " + lv + ")");
        String t = title == null ? "" : title.strip();
        DomainException.require(t.length() >= 1 && t.length() <= 24 && !t.contains("&") && !t.contains("§"), "art.bad_title", "작품 이름은 1~24자 (색 코드 없이)");
        Set<String> slots = new HashSet<>();
        for (ArtworkKind.Part part : k.parts()) slots.add(part.slot());
        for (Pick p : picks) DomainException.require(slots.contains(p.slot()) && p.amount() > 0, "art.parts", "없는 재료 자리: " + p.slot());
        List<int[]> qa = new ArrayList<>();
        List<String> mats = new ArrayList<>();
        for (ArtworkKind.Part part : k.parts()) {
            // 한 자리에 여러 묶음(품질이 다른 같은 재료 등)을 쓸 수 있다 — 모양은 가장 많이 쓴 재료
            List<Pick> ps = picks.stream().filter(x2 -> x2.slot().equals(part.slot())).toList();
            DomainException.require(!ps.isEmpty(), "art.parts", part.slot() + " 재료가 없습니다");
            int total = 0;
            long qsum = 0;
            for (Pick p : ps) {
                ItemType it = s.items.types().get(p.typeId());
                DomainException.require(!it.category().unique() && it.tags().stream().anyMatch(part.tags()::contains), "art.material",
                        it.name() + " 은(는) " + part.slot() + " 에 쓸 수 없습니다 (" + String.join("·", part.tags()) + ")");
                total += p.amount();
                qsum += (long) p.quality() * p.amount();
                qa.add(new int[]{p.quality(), p.amount()});
            }
            DomainException.require(total == part.amount(), "art.amount", part.slot() + " 재료는 " + part.amount() + "개 (지금 " + total + ")");
            Pick main = ps.stream().max(Comparator.comparingInt(Pick::amount)).orElseThrow();
            mats.add(part.slot() + "=" + ArtMaterials.look(s.items.types().get(main.typeId())) + ":" + main.typeId() + ":" + (qsum / total));
        }
        // 땅 · 간격
        var plot = s.realm.plot(world, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        DomainException.require(plot.isEmpty() || plot.get().owner().equals(uuid) || s.realm.members(world, Math.floorDiv(x, 16), Math.floorDiv(z, 16)).contains(uuid),
                "art.land", "남의 땅에는 세울 수 없습니다");
        for (Artwork o : loaded ? cache : repo.artworks())
            DomainException.require(!o.world().equals(world) || Math.hypot(o.x() - x, o.z() - z) >= MIN_GAP, "art.crowded", "다른 작품과 너무 가깝습니다 (" + MIN_GAP + " 블록)");
        Map<String, Integer> pts = s.growth.statPoints(uuid);
        int quality = k.quality(qa, lv, pts.getOrDefault("artistry", 0), pts.getOrDefault("steady_hand", 0) > 0, roll);
        Artwork a = new Artwork(UUID.randomUUID().toString(), uuid, k.id(), t, world, x, y, z, Math.floorMod(yaw, 360), quality, String.join(",", mats), 0,
                clock.nowMillis());
        tx.inTx(() -> {
            repo.insertArtwork(a);
            s.audit.record("ARTWORK_CREATED", uuid, a.id(), k.id() + " q" + quality + " " + a.materials(), null);
            return null;
        });
        cache.add(a);
        s.growth.addXp(uuid, "sculpting", 60L + k.level() * 8L + quality / 10, Math.max(1, k.level()));
        s.growth.record(uuid, "art.created", 1);
        s.growth.record(uuid, "art.experience", 5);
        long fame = Math.round(k.fame() * quality / 1000.0);
        if (fame > 0) s.reputation.addFame(uuid, fame);
        if (region != null) s.npcWorld.contribute(region, Math.max(1, quality / 100), "art:" + a.id());
        return a;
    }

    /** 이름 짓기 · 바꾸기 (만든 사람만). 다 깎은 뒤 채팅으로 짓는다 */
    public Artwork rename(String uuid, String artworkId, String title) {
        Artwork a = repo.artwork(artworkId).orElseThrow(() -> DomainException.of("art.gone", "사라진 작품입니다"));
        DomainException.require(a.owner().equals(uuid), "art.not_owner", "내 작품이 아닙니다");
        String t = title == null ? "" : title.strip();
        DomainException.require(t.length() >= 1 && t.length() <= 24 && !t.contains("&") && !t.contains("§"), "art.bad_title", "작품 이름은 1~24자 (색 코드 없이)");
        tx.inTx(() -> {
            repo.renameArtwork(a.id(), t);
            return null;
        });
        Artwork b = new Artwork(a.id(), a.owner(), a.kind(), t, a.world(), a.x(), a.y(), a.z(), a.yaw(), a.quality(), a.materials(), a.views(), a.createdAt());
        cache.replaceAll(x -> x.id().equals(b.id()) ? b : x);
        return b;
    }

    /** 감상 (만든 사람은 자기 작품을 감상해도 효과 없음) */
    public View view(String uuid, String artworkId, String region) {
        Artwork a = repo.artwork(artworkId).orElseThrow(() -> DomainException.of("art.gone", "사라진 작품입니다"));
        String owner = a.owner();
        boolean fresh = !owner.equals(uuid) && tx.inTx(() -> {
            if (!repo.viewArtwork(a.id(), uuid, day())) return false;
            repo.addViews(a.id(), 1);
            return true;
        });
        if (!fresh) return new View(a, owner, 0, 0, false);
        s.growth.record(uuid, "art.experience", 1);
        s.reputation.addFame(owner, 1);
        if (region != null) s.npcWorld.contribute(region, 1, "artview:" + a.id() + ":" + uuid + ":" + day());
        // 품질이 높을수록 오래 · 세게 (300 미만 10분 1단계 … 900 이상 40분 3단계)
        int minutes = 10 + a.quality() / 30, level = a.quality() >= 900 ? 3 : a.quality() >= 600 ? 2 : 1;
        return new View(a, owner, Math.min(40, minutes), level, true);
    }

    /** 허물기: 만든 사람만. 재료의 절반을 돌려준다 */
    public Artwork remove(String uuid, String artworkId, boolean admin) {
        Artwork a = repo.artwork(artworkId).orElseThrow(() -> DomainException.of("art.gone", "사라진 작품입니다"));
        DomainException.require(admin || a.owner().equals(uuid), "art.not_owner", "내 작품이 아닙니다");
        tx.inTx(() -> {
            repo.deleteArtwork(a.id());
            s.audit.record("ARTWORK_REMOVED", uuid, a.id(), a.kind() + " " + a.title(), null);
            return null;
        });
        cache.removeIf(x -> x.id().equals(a.id()));
        ArtworkKind k = kind(a.kind());
        for (String part : a.materials().split(",")) {
            String[] kv = part.split("=");
            String[] lt = kv[1].split(":");
            if (lt.length < 3) continue;
            int amount = k.parts().stream().filter(p -> p.slot().equals(kv[0])).mapToInt(ArtworkKind.Part::amount).findFirst().orElse(0) / 2;
            if (amount > 0) s.items.deliverBulk(a.owner(), lt[1], Integer.parseInt(lt[2]), amount, "art_dismantle");
        }
        return a;
    }
}
