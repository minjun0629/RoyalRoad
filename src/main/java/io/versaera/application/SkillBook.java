package io.versaera.application;

import io.versaera.domain.combat.CombatState;
import io.versaera.domain.combat.SkillDefinition;

import java.util.*;

/**
 * 지금 쓸 수 있는 스킬 (CMB-02). 무기 종류 · 숙련 · 직업으로 정해진다.
 * 슬롯 1 = 그 무기의 기본 스킬, 슬롯 2 = 직업 스킬 (없으면 두 번째 기본 스킬). 콤보 마무리 스킬은 슬롯에 오지 않는다.
 */
public final class SkillBook {
    private final GameServices s;
    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();
    private final Set<String> finishers = new HashSet<>();

    SkillBook(GameServices s, Collection<SkillDefinition> list, Collection<CombatState.Combo> combos) {
        this.s = s;
        for (SkillDefinition d : list) {
            if (skills.putIfAbsent(d.id(), d) != null) throw new IllegalArgumentException("스킬 id 중복: " + d.id());
            s.growth.discipline(d.discipline());
        }
        for (CombatState.Combo c : combos) {
            if (!skills.containsKey(c.finisher())) throw new IllegalArgumentException(c.id() + ": 없는 마무리 스킬 " + c.finisher());
            finishers.add(c.finisher());
        }
        for (var j : s.jobs.all()) for (String sk : j.skills()) if (!skills.containsKey(sk)) throw new IllegalArgumentException(j.id() + ": 없는 스킬 " + sk);
    }

    public SkillDefinition skill(String id) {
        return skills.get(id);
    }

    public Collection<SkillDefinition> all() {
        return Collections.unmodifiableCollection(skills.values());
    }

    /** @param weaponTag 손에 든 무기 태그 (sword · dagger · bow · staff · spear, 없으면 null) — DB 스레드 */
    public List<SkillDefinition> loadout(String uuid, String weaponTag) {
        Set<String> jobSkills = s.jobs.skills(uuid);
        List<SkillDefinition> basic = new ArrayList<>(), job = new ArrayList<>();
        for (SkillDefinition d : skills.values()) {
            if (finishers.contains(d.id())) continue;
            if (d.weapon() != null && !d.weapon().equals(weaponTag)) continue;
            if (s.growth.level(uuid, d.discipline()) < d.minLevel()) continue;
            if (d.basic()) basic.add(d);
            else if (jobSkills.contains(d.id())) job.add(d);
        }
        List<SkillDefinition> out = new ArrayList<>();
        if (!basic.isEmpty()) out.add(basic.get(0));
        if (!job.isEmpty()) out.add(job.get(job.size() - 1));   // 가장 나중(상위)에 얻은 직업 스킬
        else if (basic.size() > 1) out.add(basic.get(1));
        return out;
    }

    /** 콤보 마무리를 쓸 수 있나 (숙련) */
    public boolean canFinish(String uuid, String finisher) {
        SkillDefinition d = skills.get(finisher);
        return d != null && s.growth.level(uuid, d.discipline()) >= d.minLevel();
    }
}
