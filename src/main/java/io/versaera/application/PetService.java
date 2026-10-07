package io.versaera.application;

import io.versaera.application.port.AdventureRepository;
import io.versaera.application.port.AdventureRepository.Pet;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.pet.PetRules;
import io.versaera.domain.pet.Species;

import java.util.*;

/**
 * 펫 · 길들이기 (PET-01 · PET-02). 확률 굴림(roll)은 플랫폼이 서버에서 만들어 넘긴다 — 클라이언트가 정하는 값은 없다.
 * 스레드: DB 스레드에서만.
 */
public final class PetService {
    public record TameResult(boolean success, Pet pet, double chance) {}

    /** 소환할 때 쓰는 능력치 */
    public record Stats(Pet pet, Species species, int maxHealth, double attack, Set<String> skills, int loyalty) {}

    private final TxRunner tx;
    private final AdventureRepository repo;
    private final GameServices s;
    private final EventBus bus;
    private final GameClock clock;
    private final Map<String, Species> species = new LinkedHashMap<>();

    PetService(TxRunner tx, AdventureRepository repo, GameServices s, List<Species> list, EventBus bus, GameClock clock) {
        this.tx = tx;
        this.repo = repo;
        this.s = s;
        this.bus = bus;
        this.clock = clock;
        for (Species sp : list) DomainException.require(species.putIfAbsent(sp.id(), sp) == null, "pet.dup", "펫 종류 중복: " + sp.id());
    }

    public Species species(String id) {
        Species sp = species.get(id);
        DomainException.require(sp != null, "pet.unknown", "없는 펫 종류: " + id);
        return sp;
    }

    public Collection<Species> allSpecies() {
        return species.values();
    }

    /** 엔티티 종류 + 지역 태그로 길들일 수 있는 종류 (서리 늑대처럼 같은 엔티티라도 사는 곳이 다르면 다른 종류) */
    public Optional<Species> speciesFor(String entity, Set<String> regionTags) {
        return species.values().stream().filter(sp -> sp.entity().equals(entity))
                .filter(sp -> sp.habitat().isEmpty() || sp.habitat().stream().anyMatch(regionTags::contains))
                .max(Comparator.comparingInt(Species::tameLevel));
    }

    public List<Pet> pets(String uuid) {
        return repo.pets(uuid);
    }

    public Pet pet(String uuid, String petId) {
        return repo.pet(petId).filter(p -> p.owner().equals(uuid)).orElseThrow(() -> DomainException.of("pet.not_owner", "내 펫이 아닙니다"));
    }

    /**
     * 길들이기 한 번. 먹이는 플랫폼이 이미 뺐다 (성공 · 실패 모두 먹이는 사라진다).
     * @param roll 서버가 만든 0~1 난수
     */
    public TameResult tame(String uuid, String speciesId, Set<String> regionTags, Set<String> foodTags, int foodQuality, double roll) {
        Species sp = species(speciesId);
        int lv = s.growth.level(uuid, "taming");
        DomainException.require(lv >= sp.tameLevel(), "pet.level", sp.name() + " 은(는) 길들이기 " + sp.tameLevel() + " 이 있어야 합니다 (지금 " + lv + ")");
        DomainException.require(foodTags.stream().anyMatch(sp.food()::contains), "pet.food", sp.name() + " 은(는) 그 먹이를 먹지 않습니다");
        DomainException.require(sp.habitat().isEmpty() || sp.habitat().stream().anyMatch(regionTags::contains), "pet.habitat", "이 지역의 " + sp.name() + " 이(가) 아닙니다");
        DomainException.require(repo.pets(uuid).size() < PetRules.maxPets(lv), "pet.full", "더 데리고 다닐 수 없습니다 (" + PetRules.maxPets(lv) + "마리)");
        double chance = PetRules.tameChance(sp, lv, foodQuality);
        if (roll >= chance) {
            s.growth.addXp(uuid, "taming", 15 + sp.tameLevel(), Math.max(1, sp.tameLevel()));
            return new TameResult(false, null, chance);
        }
        long now = clock.nowMillis();
        Pet p = new Pet(UUID.randomUUID().toString(), uuid, sp.id(), sp.name(), 1, 0, 60, now, 0, now);
        tx.inTx(() -> {
            repo.insertPet(p);
            s.audit.record("PET_TAMED", uuid, p.id(), sp.id(), null);
            return null;
        });
        s.growth.addXp(uuid, "taming", 80 + sp.tameLevel() * 6L, Math.max(1, sp.tameLevel()));
        s.growth.record(uuid, "pet.tamed", 1);
        return new TameResult(true, p, chance);
    }

    /**
     * 조각에 생명을 불어넣는다 (조각 생명술 · 대형 조각 깨우기). 길들이기 숙련이 없어도 되고, 데리고 다닐 수 있는 수에 하나 더 얹힌다.
     * 처음 레벨은 조각 품질만큼 (품질 100 마다 1, 최대 10), 충성 100 으로 시작한다
     */
    public Pet awaken(String uuid, String speciesId, String name, int quality) {
        Species sp = species(speciesId);
        int lv = s.growth.level(uuid, "taming");
        DomainException.require(repo.pets(uuid).size() < PetRules.maxPets(lv) + 1, "pet.full", "더 데리고 다닐 수 없습니다");
        int start = Math.max(1, Math.min(10, 1 + quality / 100));
        long xp = 0;
        for (int i = 1; i < start; i++) xp += PetRules.xpToNext(i);
        String nm = name == null || name.isBlank() ? sp.name() : name.strip();
        if (nm.length() > 16) nm = nm.substring(0, 16);
        long now = clock.nowMillis();
        Pet p = new Pet(UUID.randomUUID().toString(), uuid, sp.id(), nm, PetRules.levelOf(xp), xp, 100, now, 0, now);
        tx.inTx(() -> {
            repo.insertPet(p);
            s.audit.record("PET_AWAKENED", uuid, p.id(), sp.id() + " q" + quality, null);
            return null;
        });
        s.growth.record(uuid, "pet.awakened", 1);
        return p;
    }

    public Pet rename(String uuid, String petId, String name) {
        DomainException.require(name != null && name.strip().length() >= 1 && name.strip().length() <= 16 && !name.contains("&") && !name.contains("§"),
                "pet.bad_name", "이름은 1~16자 (색 코드 없이)");
        Pet p = pet(uuid, petId);
        Pet n = new Pet(p.id(), p.owner(), p.species(), name.strip(), p.level(), p.xp(), p.loyalty(), p.fedAt(), p.faintedUntil(), p.createdAt());
        tx.inTx(() -> { repo.updatePet(n); return null; });
        return n;
    }

    /** 먹이 주기: 충성 +10 (+품질 보너스 최대 +5), 최대 100. 먹이는 플랫폼이 이미 뺐다 */
    public int feed(String uuid, String petId, Set<String> foodTags, int quality) {
        Pet p = pet(uuid, petId);
        Species sp = species(p.species());
        DomainException.require(foodTags.stream().anyMatch(sp.food()::contains), "pet.food", sp.name() + " 은(는) 그 먹이를 먹지 않습니다");
        long now = clock.nowMillis();
        int loyalty = Math.min(100, PetRules.loyaltyNow(p.loyalty(), p.fedAt(), now) + 10 + Math.max(0, Math.min(1000, quality)) / 200);
        Pet n = new Pet(p.id(), p.owner(), p.species(), p.name(), p.level(), p.xp(), loyalty, now, p.faintedUntil(), p.createdAt());
        tx.inTx(() -> { repo.updatePet(n); return null; });
        return loyalty;
    }

    /** 소환 전에: 능력치 · 스킬 · 지금 충성. 기절해 있으면 거부 */
    public Stats summon(String uuid, String petId) {
        Pet p = pet(uuid, petId);
        long now = clock.nowMillis();
        DomainException.require(p.faintedUntil() <= now, "pet.fainted", p.name() + " 은(는) 쉬는 중입니다 (" + Math.max(1, (p.faintedUntil() - now) / 1000) + "초)");
        return stats(p);
    }

    public Stats stats(Pet p) {
        Species sp = species(p.species());
        return new Stats(p, sp, sp.maxHealth(p.level()), sp.attack(p.level()), sp.skillsAt(p.level()), PetRules.loyaltyNow(p.loyalty(), p.fedAt(), clock.nowMillis()));
    }

    /** 함께 싸워 얻은 경험 (충성에 따라 0.5~1.2배). 레벨이 오르면 이벤트 */
    public Pet gainXp(String uuid, String petId, long base) {
        Pet p = pet(uuid, petId);
        if (base <= 0 || p.level() >= PetRules.MAX_LEVEL) return p;
        long gained = Math.max(1, Math.round(base * PetRules.loyaltyMult(PetRules.loyaltyNow(p.loyalty(), p.fedAt(), clock.nowMillis()))));
        long xp = p.xp() + gained;
        int lv = PetRules.levelOf(xp);
        Pet n = new Pet(p.id(), p.owner(), p.species(), p.name(), lv, xp, p.loyalty(), p.fedAt(), p.faintedUntil(), p.createdAt());
        tx.inTx(() -> { repo.updatePet(n); return null; });
        if (lv > p.level()) {
            long best = s.growth.counter(uuid, "pet.level");
            if (lv > best) s.growth.record(uuid, "pet.level", lv - best);   // "가장 높은 펫 레벨" 기록
            s.growth.addXp(uuid, "taming", 20L * lv, Math.max(1, lv));
            bus.publish(new GameEvents.PetLevelUp(uuid, p.id(), lv));
        }
        return n;
    }

    /** 쓰러짐: 5분 쉰다 */
    public void faint(String uuid, String petId) {
        Pet p = pet(uuid, petId);
        Pet n = new Pet(p.id(), p.owner(), p.species(), p.name(), p.level(), p.xp(), Math.max(0, p.loyalty() - 5), p.fedAt(),
                clock.nowMillis() + PetRules.FAINT_MS, p.createdAt());
        tx.inTx(() -> { repo.updatePet(n); return null; });
    }

    public void release(String uuid, String petId) {
        Pet p = pet(uuid, petId);
        tx.inTx(() -> {
            repo.releasePet(p.id());
            s.audit.record("PET_RELEASED", uuid, p.id(), p.species() + " lv" + p.level(), null);
            return null;
        });
    }
}
