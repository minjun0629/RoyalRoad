package io.versaera.application;

import io.versaera.application.port.RuntimeStateRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.GameClock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 서버가 꺼져도 남아야 하는 진행 중 상태 (공성 · 필드 보스 · 손질 버프 · 시련 · 비기 동료 · 던전).
 * 런타임이 바뀔 때 · 몇 초마다 · 끌 때 저장하고, 켤 때 읽어 이어 간다. 값은 문자열 맵 (key=value;…). DB 스레드 전용.
 */
public final class RuntimeStateService {
    private final TxRunner tx;
    private final RuntimeStateRepository repo;
    private final GameClock clock;

    RuntimeStateService(TxRunner tx, RuntimeStateRepository repo, GameClock clock) {
        this.tx = tx;
        this.repo = repo;
        this.clock = clock;
    }

    /** @param expiresAt 이 시각이 지나면 버린다 (0 = 끝없음) */
    public void save(String scope, String key, Map<String, String> data, long expiresAt) {
        String enc = encode(data);
        tx.inTx(() -> { repo.put(scope, key, enc, expiresAt, clock.nowMillis()); return null; });
    }

    public Optional<Map<String, String>> load(String scope, String key) {
        long now = clock.nowMillis();
        return tx.inTx(() -> repo.get(scope, key)).filter(r -> r.expiresAt() == 0 || r.expiresAt() > now).map(r -> decode(r.data()));
    }

    /** 범위 안의 살아 있는 모든 상태 (키 → 값) */
    public Map<String, Map<String, String>> loadAll(String scope) {
        long now = clock.nowMillis();
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        tx.inTx(() -> repo.all(scope)).forEach((k, r) -> { if (r.expiresAt() == 0 || r.expiresAt() > now) out.put(k, decode(r.data())); });
        return out;
    }

    public void delete(String scope, String key) {
        tx.inTx(() -> { repo.delete(scope, key); return null; });
    }

    /** 범위 안을 모두 지운다 (다시 저장하기 전) */
    public void clear(String scope) {
        tx.inTx(() -> { for (String k : repo.all(scope).keySet()) repo.delete(scope, k); return null; });
    }

    public int purge() {
        return tx.inTx(() -> repo.purge(clock.nowMillis()));
    }

    // ------------------------------------------------------------------ key=value;… (%, =, ; 는 %XX)
    public static String encode(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(esc(e.getKey())).append('=').append(esc(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    public static Map<String, String> decode(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String part : s.split(";")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            m.put(unesc(part.substring(0, i)), unesc(part.substring(i + 1)));
        }
        return m;
    }

    private static String esc(String s) {
        return s.replace("%", "%25").replace("=", "%3D").replace(";", "%3B");
    }

    private static String unesc(String s) {
        return s.replace("%3B", ";").replace("%3D", "=").replace("%25", "%");
    }
}
