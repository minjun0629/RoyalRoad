package io.versaera.domain.party;

import io.versaera.domain.common.DomainException;

import java.util.*;

/**
 * 파티 (CANON 개념: 로열 로드의 던전은 대부분 파티로 돈다). 접속 중에만 있는 모임이라 DB 에 두지 않는다.
 * 한 사람은 한 파티에만. 최대 8명 (ORIGINAL). 파티원끼리는 서로 해치지 않고, 던전에 같이 들어간다.
 * 스레드: 메인 스레드에서만 부른다.
 */
public final class Parties {
    public static final int MAX = 8;
    private final Map<String, String> leaderOf = new HashMap<>();          // 사람 → 파티장
    private final Map<String, LinkedHashSet<String>> members = new HashMap<>();   // 파티장 → 파티원 (파티장 포함, 들어온 순서)
    private final Map<String, String> invites = new HashMap<>();           // 초대받은 사람 → 파티장

    public Optional<String> leader(String who) {
        return Optional.ofNullable(leaderOf.get(who));
    }

    public List<String> members(String who) {
        String l = leaderOf.get(who);
        return l == null ? List.of(who) : List.copyOf(members.get(l));
    }

    public boolean together(String a, String b) {
        String l = leaderOf.get(a);
        return l != null && l.equals(leaderOf.get(b));
    }

    public void invite(String from, String to) {
        DomainException.require(!from.equals(to), "party.self", "자기 자신은 초대할 수 없습니다");
        DomainException.require(!leaderOf.containsKey(to), "party.busy", "이미 다른 파티에 있습니다");
        String l = leaderOf.getOrDefault(from, from);
        DomainException.require(l.equals(from), "party.not_leader", "파티장만 초대할 수 있습니다");
        DomainException.require(members(from).size() < MAX, "party.full", "파티가 가득 찼습니다 (" + MAX + "명)");
        invites.put(to, from);
    }

    /** @return 들어간 파티의 파티장 */
    public String accept(String who) {
        String l = invites.remove(who);
        DomainException.require(l != null, "party.no_invite", "받은 초대가 없습니다");
        DomainException.require(!leaderOf.containsKey(who), "party.busy", "이미 다른 파티에 있습니다");
        DomainException.require(leaderOf.getOrDefault(l, l).equals(l), "party.gone", "그 파티는 없어졌습니다");
        LinkedHashSet<String> m = members.computeIfAbsent(l, k -> new LinkedHashSet<>(List.of(l)));
        DomainException.require(m.size() < MAX, "party.full", "파티가 가득 찼습니다");
        leaderOf.put(l, l);
        m.add(who);
        leaderOf.put(who, l);
        return l;
    }

    /** 나가기. 파티장이 나가면 다음 사람이 파티장, 혼자 남으면 해산 */
    public void leave(String who) {
        invites.remove(who);
        String l = leaderOf.remove(who);
        if (l == null) return;
        LinkedHashSet<String> m = members.remove(l);
        m.remove(who);
        if (m.size() <= 1) {
            for (String x : m) leaderOf.remove(x);
            return;
        }
        String nl = l.equals(who) ? m.iterator().next() : l;
        members.put(nl, m);
        for (String x : m) leaderOf.put(x, nl);
    }
}
