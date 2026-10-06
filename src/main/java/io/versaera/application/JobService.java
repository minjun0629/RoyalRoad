package io.versaera.application;

import io.versaera.application.port.JobRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.domain.job.JobDefinition;

import java.util.*;

/**
 * 직업. 전투 직업 1개 + 생활 직업 1개를 동시에 가질 수 있다.
 * 상위 직업(tier 2)은 그 계열의 하위 직업을 가진 상태에서만 오른다. 다른 계열로 바꾸는 건 7일에 한 번.
 */
public final class JobService {
    public static final long CHANGE_COOLDOWN_MS = 7L * 24 * 3600 * 1000;

    private final TxRunner tx;
    private final JobRepository repo;
    private final Map<String, JobDefinition> jobs = new LinkedHashMap<>();
    private final EventBus bus;
    private final GameClock clock;

    public JobService(TxRunner tx, JobRepository repo, Collection<JobDefinition> defs, EventBus bus, GameClock clock) {
        this.tx = tx;
        this.repo = repo;
        for (JobDefinition d : defs) if (jobs.putIfAbsent(d.id(), d) != null) throw new IllegalArgumentException("직업 id 중복: " + d.id());
        for (JobDefinition d : defs)
            if (d.parent() != null && (!jobs.containsKey(d.parent()) || !jobs.get(d.parent()).slot().equals(d.slot())))
                throw new IllegalArgumentException("상위 직업의 parent 가 잘못되었습니다: " + d.id());
        this.bus = bus;
        this.clock = clock;
    }

    public JobDefinition job(String id) {
        JobDefinition d = jobs.get(id);
        if (d == null) throw DomainException.of("job.unknown", "없는 직업: " + id);
        return d;
    }

    public Collection<JobDefinition> all() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    public Map<String, JobRepository.Held> held(String uuid) {
        return repo.jobs(uuid);
    }

    /** 지금 오를 수 있는 직업 */
    public List<JobDefinition> available(String uuid, PlayerFacts f) {
        Map<String, JobRepository.Held> now = repo.jobs(uuid);
        List<JobDefinition> out = new ArrayList<>();
        for (JobDefinition d : jobs.values()) {
            JobRepository.Held cur = now.get(d.slot());
            if (cur != null && cur.jobId().equals(d.id())) continue;
            if (d.parent() != null && (cur == null || !cur.jobId().equals(d.parent()))) continue;
            if (d.requires().test(f)) out.add(d);
        }
        return out;
    }

    public JobDefinition advance(String uuid, String jobId, PlayerFacts f) {
        JobDefinition d = job(jobId);
        long now = clock.nowMillis();
        tx.inTx(() -> {
            Map<String, JobRepository.Held> held = repo.jobs(uuid);
            JobRepository.Held cur = held.get(d.slot());
            if (cur != null && cur.jobId().equals(d.id())) throw DomainException.of("job.same", "이미 그 직업입니다");
            if (d.parent() != null && (cur == null || !cur.jobId().equals(d.parent())))
                throw DomainException.of("job.need_parent", "먼저 " + job(d.parent()).name() + "이(가) 되어야 합니다");
            boolean promotion = cur != null && cur.jobId().equals(d.parent());
            if (cur != null && !promotion && now - cur.since() < CHANGE_COOLDOWN_MS)
                throw DomainException.of("job.cooldown", "직업을 바꾼 지 7일이 지나야 합니다");
            if (!d.requires().test(f)) throw DomainException.of("job.requirements", "아직 조건을 채우지 못했습니다");
            repo.set(uuid, d.slot(), d.id(), now);
            return null;
        });
        bus.publish(new GameEvents.JobChanged(uuid, d.slot(), d.id()));
        return d;
    }

    /** 가진 직업들의 효과 합 */
    public Map<String, Double> perks(String uuid) {
        Map<String, Double> m = new HashMap<>();
        for (JobRepository.Held h : repo.jobs(uuid).values()) {
            JobDefinition d = jobs.get(h.jobId());
            if (d == null) continue;
            for (JobDefinition x = d; x != null; x = x.parent() == null ? null : jobs.get(x.parent()))
                x.perks().forEach((k, v) -> m.merge(k, v, Double::sum));
        }
        return m;
    }

    /** 가진 직업(상위 포함)으로 쓸 수 있는 스킬 */
    public Set<String> skills(String uuid) {
        Set<String> s = new LinkedHashSet<>();
        for (JobRepository.Held h : repo.jobs(uuid).values())
            for (JobDefinition x = jobs.get(h.jobId()); x != null; x = x.parent() == null ? null : jobs.get(x.parent())) s.addAll(x.skills());
        return s;
    }
}
