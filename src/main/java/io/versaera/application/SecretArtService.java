package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.art.SecretArt;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.item.Custody;

import java.util.*;

/**
 * 비기 (ART-01). 배운 비기는 발견 기록(kind "art")에 남는다.
 * 배우기: 그 직업 · 숙련 레벨 이상 + (비기의 조각상을 바치거나, 스스로 깨우치는 조건을 채움). 최후의 비기는 같은 직업의 비기를 모두 배운 뒤.
 * 쓰기의 효과(소환 · 변신 · 정지 …)는 플랫폼이 하고, 여기서는 '쓸 수 있는가'와 단계만 정한다.
 */
public final class SecretArtService {
    public record Status(SecretArt art, boolean learned, String missing) {}

    private final TxRunner tx;
    private final ProgressRepository progress;
    private final GameServices s;
    private final GameClock clock;
    private final Map<String, SecretArt> arts = new LinkedHashMap<>();

    SecretArtService(TxRunner tx, ProgressRepository progress, Collection<SecretArt> defs, GameServices s, GameClock clock) {
        this.tx = tx;
        this.progress = progress;
        this.s = s;
        this.clock = clock;
        for (SecretArt a : defs) {
            DomainException.require(arts.putIfAbsent(a.id(), a) == null, "art.dup", "비기 id 중복: " + a.id());
            s.jobs.job(a.job());
            s.growth.discipline(a.discipline());
            if (a.relic() != null) DomainException.require(s.items.types().get(a.relic()).category().unique(), "art.relic", "비기의 조각상은 고유 아이템이어야 합니다: " + a.id());
        }
    }

    public Collection<SecretArt> all() {
        return arts.values();
    }

    public SecretArt art(String id) {
        SecretArt a = arts.get(id);
        if (a == null) throw DomainException.of("art.unknown", "없는 비기: " + id);
        return a;
    }

    public boolean learned(String uuid, String id) {
        return progress.discovered(uuid, "art", id);
    }

    private boolean hasJob(String uuid, String job) {
        return s.jobs.held(uuid).values().stream().anyMatch(h -> h.jobId().equals(job));
    }

    /** 배우는 데 모자란 것 (없으면 null) — 조각상 · 깨우침은 따로 */
    private String gate(String uuid, SecretArt a) {
        if (!hasJob(uuid, a.job())) return s.jobs.job(a.job()).name() + " 직업이어야 한다";
        int lv = s.growth.level(uuid, a.discipline());
        if (lv < a.minLevel()) return s.growth.discipline(a.discipline()).name() + " 숙련 " + a.minLevel() + " 이상 (지금 " + lv + ")";
        if (a.finalArt())
            for (SecretArt o : arts.values())
                if (o.job().equals(a.job()) && !o.finalArt() && !learned(uuid, o.id())) return "먼저 " + o.name() + "을(를) 익혀야 한다";
        return null;
    }

    public List<Status> status(String uuid) {
        List<Status> out = new ArrayList<>();
        for (SecretArt a : arts.values()) {
            boolean l = learned(uuid, a.id());
            out.add(new Status(a, l, l ? null : gate(uuid, a)));
        }
        return out;
    }

    /**
     * @param relicItemId 손에 든 고유 아이템 id (없으면 null). 비기의 조각상이면 바쳐서 사라진다.
     * @return 배운 방법 ("relic" · "discover" · "final")
     */
    public String learn(String uuid, String artId, String relicItemId) {
        SecretArt a = art(artId);
        DomainException.require(!learned(uuid, a.id()), "art.known", "이미 익힌 비기입니다");
        String why = gate(uuid, a);
        if (why != null) throw DomainException.of("art.not_ready", a.name() + ": " + why);
        String how;
        if (a.relic() != null && relicItemId != null && s.items.find(relicItemId)
                .filter(it -> it.typeId().equals(a.relic()) && it.custody().equals(Custody.player(uuid))).isPresent()) {
            s.items.destroy(relicItemId, uuid, "비기 " + a.id(), "art:" + uuid + ":" + a.id());
            how = "relic";
        } else if (a.discover() != null && a.discover().test(s.facts(uuid, null, 12))) {
            how = "discover";
        } else if (a.finalArt() && a.relic() == null && a.discover() == null) {
            how = "final";
        } else {
            throw DomainException.of("art.need", a.name() + ": " + (a.relic() != null ? "「" + s.items.types().get(a.relic()).name() + "」을(를) 손에 들어야 한다" : "아직 깨우치지 못했다"));
        }
        tx.inTx(() -> {
            progress.discover(uuid, "art", a.id(), clock.nowMillis());
            s.audit.record("ART_LEARNED", uuid, a.id(), how, "art:" + uuid + ":" + a.id());
            return null;
        });
        return how;
    }

    /** 쓸 수 있으면 그 분야 숙련 레벨 (시간 조각술의 단계에 씀) */
    public int castLevel(String uuid, String artId) {
        SecretArt a = art(artId);
        DomainException.require(learned(uuid, a.id()), "art.unknown_to_you", "익히지 않은 비기입니다");
        DomainException.require(hasJob(uuid, a.job()), "art.job", s.jobs.job(a.job()).name() + " 직업일 때만 쓸 수 있습니다");
        return s.growth.level(uuid, a.discipline());
    }

    /** 천상의 맛: 먹은 사람은 하루에 한 번 인내 기록 +200 (영구 스탯) */
    public boolean feast(String eater) {
        long day = clock.nowMillis() / 86_400_000L;
        return tx.inTx(() -> {
            if (!progress.discover(eater, "feast", Long.toString(day), clock.nowMillis())) return false;
            s.growth.record(eater, "hit_taken", 200);
            return true;
        });
    }
}
