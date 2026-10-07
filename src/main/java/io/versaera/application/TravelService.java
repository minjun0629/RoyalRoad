package io.versaera.application;

import io.versaera.application.port.AdventureRepository;
import io.versaera.application.port.AdventureRepository.Journey;
import io.versaera.application.port.AdventureRepository.Mount;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.travel.MountKind;
import io.versaera.domain.travel.Route;
import io.versaera.domain.travel.TravelNetwork;

import java.util.*;

/**
 * 이동 (TRV-01 · TRV-02): 탈것 사기 · 부르기 · 승마 숙련, 마차 · 배 표 사기 · 여행 · 도착.
 * 여행은 DB 에 남는다 — 서버가 꺼졌다 켜져도 도착 시각이 지나 있으면 도착지에서 깨어난다.
 * 폭풍 · 눈보라 · 모래폭풍이면 배가 뜨지 않는다 (날씨 연동). 스레드: DB 스레드에서만.
 */
public final class TravelService {
    public static final int MAX_MOUNTS = 6;

    /** 탈것을 부를 때: 종류 · 승마 숙련을 더한 속도 */
    public record Ride(Mount mount, MountKind kind, double speed) {}

    private final TxRunner tx;
    private final AdventureRepository repo;
    private final GameServices s;
    private final GameClock clock;
    private final Map<String, MountKind> kinds = new LinkedHashMap<>();
    private final TravelNetwork network;

    TravelService(TxRunner tx, AdventureRepository repo, GameServices s, List<MountKind> kinds, TravelNetwork network, GameClock clock) {
        this.tx = tx;
        this.repo = repo;
        this.s = s;
        this.clock = clock;
        this.network = network;
        for (MountKind k : kinds) DomainException.require(this.kinds.putIfAbsent(k.id(), k) == null, "mount.dup", "탈것 중복: " + k.id());
    }

    public TravelNetwork network() {
        return network;
    }

    public Collection<MountKind> mountKinds() {
        return kinds.values();
    }

    public MountKind kind(String id) {
        MountKind k = kinds.get(id);
        DomainException.require(k != null, "mount.unknown", "없는 탈것: " + id);
        return k;
    }

    // ------------------------------------------------------------------ 탈것
    public List<Mount> mounts(String uuid) {
        return repo.mounts(uuid);
    }

    /** 마구간지기(STABLE)에게서 산다 */
    public Mount buy(String uuid, String npcId, String kindId, String requestId) {
        DomainException.require(s.npcWorld.offers(npcId, "STABLE"), "mount.no_stable", "이 사람은 말을 팔지 않습니다");
        MountKind k = kind(kindId);
        DomainException.require(repo.mounts(uuid).size() < MAX_MOUNTS, "mount.full", "탈것은 " + MAX_MOUNTS + "마리까지");
        long price = Math.max(1, Math.round(k.price() * (1 - s.npcWorld.stage(uuid, npcId).discount())));
        DomainException.require(s.economy.withdraw(uuid, price, "mount_buy", "mount:" + requestId), "mount.dup", "이미 처리한 요청입니다");
        Mount m = new Mount(UUID.randomUUID().toString(), uuid, k.id(), k.name(), clock.nowMillis());
        tx.inTx(() -> {
            repo.insertMount(m);
            s.audit.record("MOUNT_BOUGHT", uuid, m.id(), k.id() + " " + price, "mount:" + requestId);
            return null;
        });
        return m;
    }

    /** 부르기: 승마 숙련이 모자라면 거부 */
    public Ride summon(String uuid, String mountId) {
        Mount m = repo.mounts(uuid).stream().filter(x -> x.id().equals(mountId)).findFirst()
                .orElseThrow(() -> DomainException.of("mount.not_owner", "내 탈것이 아닙니다"));
        MountKind k = kind(m.kind());
        int lv = s.growth.level(uuid, "riding");
        DomainException.require(lv >= k.ridingLevel(), "mount.level", k.name() + " 은(는) 승마 " + k.ridingLevel() + " 이 있어야 탈 수 있습니다 (지금 " + lv + ")");
        return new Ride(m, k, k.speedFor(lv));
    }

    /** 말을 타고 달린 거리 (플랫폼이 100 블록마다 알린다) */
    public void rode(String uuid, int hundreds) {
        if (hundreds <= 0) return;
        s.growth.record(uuid, "ride.distance", hundreds);
        s.growth.addXp(uuid, "riding", 6L * hundreds, 1);
    }

    // ------------------------------------------------------------------ 마차 · 배
    public List<Route> routesFrom(String region) {
        return network.from(region);
    }

    public Optional<Journey> journey(String uuid) {
        return repo.journey(uuid);
    }

    /**
     * 표를 사고 떠난다. 마부(CARRIAGE) · 선장(SHIP) 에게서, 출발 도시 안에서만.
     * @param here 플랫폼이 확인한 지금 지역
     */
    public Journey depart(String uuid, String npcId, String routeId, String here, String requestId) {
        Route r = network.route(routeId).orElseThrow(() -> DomainException.of("travel.no_route", "없는 노선입니다"));
        DomainException.require(r.from().equals(here), "travel.wrong_place", "이 노선은 " + s.regions.byId(r.from()).name() + " 에서 떠납니다");
        String service = r.kind() == Route.Kind.SHIP ? "SHIP" : "CARRIAGE";
        DomainException.require(s.npcWorld.offers(npcId, service), "travel.no_service", r.kind() == Route.Kind.SHIP ? "선장에게 표를 사야 합니다" : "마부에게 표를 사야 합니다");
        DomainException.require(s.npcWorld.stage(uuid, npcId) != io.versaera.domain.npc.Relation.Stage.HOSTILE, "npc.hostile", "태워 주지 않습니다");
        if (r.kind() == Route.Kind.SHIP) {
            var w = s.weather.at(here);
            DomainException.require(!w.indoor(), "travel.storm", w.name() + " — 오늘은 배가 뜨지 않습니다");
        }
        DomainException.require(repo.journey(uuid).isEmpty(), "travel.busy", "이미 여행 중입니다");
        // 항해 숙련: 배 여행 시간 -1% / 레벨 (최대 -25%)
        long duration = r.kind() == Route.Kind.SHIP
                ? Math.round(r.durationMs() * (1 - Math.min(0.25, s.growth.level(uuid, "sailing") * 0.01))) : r.durationMs();
        long now = clock.nowMillis();
        Journey j = new Journey(uuid, r.id(), r.to(), now, now + duration);
        DomainException.require(s.economy.withdraw(uuid, r.fare(), "travel_" + service.toLowerCase(Locale.ROOT), "travel:" + requestId), "travel.dup", "이미 처리한 요청입니다");
        boolean ok = tx.inTx(() -> repo.startJourney(j));
        if (!ok) {
            s.economy.deposit(uuid, r.fare(), "travel_refund", "travel_refund:" + requestId);
            throw DomainException.of("travel.busy", "이미 여행 중입니다");
        }
        s.growth.record(uuid, r.kind() == Route.Kind.SHIP ? "travel.ship" : "travel.carriage", 1);
        if (r.kind() == Route.Kind.SHIP) s.growth.addXp(uuid, "sailing", 20 + r.distance() / 200, 1);
        return j;
    }

    /** 도착: 도착 시각이 지났으면 여행을 끝내고 도착 지역을 준다. 아직이면 empty */
    public Optional<String> arrive(String uuid) {
        Optional<Journey> j = repo.journey(uuid);
        if (j.isEmpty() || j.get().arriveAt() > clock.nowMillis()) return Optional.empty();
        tx.inTx(() -> { repo.endJourney(uuid); return null; });
        return Optional.of(j.get().dest());
    }
}
