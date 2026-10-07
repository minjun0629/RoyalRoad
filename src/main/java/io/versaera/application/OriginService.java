package io.versaera.application;

import io.versaera.application.port.OriginRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.origin.Gender;
import io.versaera.domain.origin.Origins;
import io.versaera.domain.origin.Race;
import io.versaera.domain.origin.StartCity;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 캐릭터 만들기 · 초보 기간 (CHR-01 · BEG-01).
 * 처음 접속하면 종족 · 성별 · 시작 도시를 한 번 고른다. 그 뒤 게임 시간 30일(원작 '1달') 동안 시작 도시 밖으로 못 나가고, 그동안 죽어도 페널티가 없다.
 */
public final class OriginService {
    public record Character(OriginRepository.Origin origin, Race race, Gender gender, StartCity city, long beginnerUntil) {
        public boolean beginner(long now) {
            return now < beginnerUntil;
        }
    }

    private final TxRunner tx;
    private final OriginRepository repo;
    private final Origins origins;
    private final RegionIndex regions;
    private final ItemService items;
    private final GameClock clock;
    private final Supplier<ServerRules> rules;

    public OriginService(TxRunner tx, OriginRepository repo, Origins origins, RegionIndex regions, ItemService items, GameClock clock, Supplier<ServerRules> rules) {
        this.tx = tx;
        this.repo = repo;
        this.origins = origins;
        this.regions = regions;
        this.items = items;
        this.clock = clock;
        this.rules = rules;
        for (StartCity c : origins.cities()) {
            Region r = regions.byId(c.region());
            DomainException.require(r != null, "origins.no_region", "시작 도시 지역이 없습니다: " + c.region());
            DomainException.require(r.tags().contains("city") || r.tags().contains("fortress"), "origins.not_city", "시작 도시는 도시 · 성이어야 합니다: " + c.region());
        }
        for (String k : origins.startingKit()) items.types().get(k.split(":")[0]);
    }

    public Origins options() {
        return origins;
    }

    public Optional<Character> character(String uuid) {
        return repo.find(uuid).map(this::toCharacter);
    }

    private Character toCharacter(OriginRepository.Origin o) {
        long until = o.createdAt() + rules.get().time().realMillisFor(origins.beginnerGameDays());
        return new Character(o, origins.race(o.race()), Gender.valueOf(o.gender()), origins.city(o.city()), until);
    }

    /** 처음 한 번만. 시작 물품(보리빵 10개)은 배달함으로 간다 */
    public Character create(String uuid, String raceId, Gender gender, String cityId) {
        Race race = origins.race(raceId);
        StartCity city = origins.city(cityId);
        boolean made = tx.inTx(() -> repo.create(new OriginRepository.Origin(uuid, race.id(), gender.name(), city.id(), clock.nowMillis())));
        DomainException.require(made, "origins.exists", "이미 캐릭터를 만들었습니다 — 종족 · 시작 도시는 바꿀 수 없습니다");
        for (String k : origins.startingKit()) {
            String[] p = k.split(":");
            items.deliverBulk(uuid, p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), "starting_kit");
        }
        return character(uuid).orElseThrow();
    }

    public boolean beginner(String uuid) {
        return character(uuid).map(c -> c.beginner(clock.nowMillis())).orElse(false);
    }

    /** 초보가 이 지역에 있어도 되는가: 시작 도시 지역이거나 그 하위 지역 */
    public boolean insideCity(Character c, String regionId) {
        for (Region r = regionId == null ? null : regions.byId(regionId); r != null; r = r.parent() == null ? null : regions.byId(r.parent()))
            if (r.id().equals(c.city().region())) return true;
        return false;
    }

    /** 종족 숙련 보너스 (DB 스레드) */
    public double xpMult(String uuid, String discipline) {
        return repo.find(uuid).map(o -> origins.race(o.race()).xpMult(discipline)).orElse(1.0);
    }

    // ------------------------------------------------------------------ 접속 제한 (원작식 사망 페널티)
    public Optional<OriginRepository.Lock> activeLock(String uuid) {
        return repo.lock(uuid).filter(l -> l.until() > clock.nowMillis());
    }

    void lock(String uuid, long until, String reason) {
        repo.setLock(uuid, until, reason);
    }

    public void unlock(String uuid) {
        tx.inTx(() -> {
            repo.clearLock(uuid);
            return null;
        });
    }
}
