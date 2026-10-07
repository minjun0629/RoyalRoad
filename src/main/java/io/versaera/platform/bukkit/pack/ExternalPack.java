package io.versaera.platform.bukkit.pack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * 외부(GitHub 등)에 올린 리소스팩 주소 확인. 서버가 직접 받아 SHA-1 을 계산한다 →
 * config 에 해시를 손으로 적지 않아도 되고, 팩을 다시 올려도 다음 시작 때 자동으로 맞는다.
 * 메인 · DB 스레드가 아닌 별도 스레드에서만 부른다.
 */
public final class ExternalPack {
    public record Found(String url, byte[] sha1, int size) {}

    private static final int MAX_BYTES = 100 * 1024 * 1024;   // Minecraft 클라이언트 한도 (1.20)

    private ExternalPack() {
    }

    /** 목록에서 처음으로 받아지는 주소 */
    public static Optional<Found> resolve(List<String> urls, Logger log) {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
        for (String u : urls) {
            if (u == null || u.isBlank()) continue;
            try {
                // GitHub raw 는 CDN 이 몇 분 동안 옛 파일을 줄 수 있다 → 시각을 붙여 늘 새 파일을 받는다
                String fresh = u.strip() + (u.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(fresh)).timeout(Duration.ofSeconds(60)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = r.body();
                if (r.statusCode() != 200 || body.length == 0) {
                    log.info("리소스팩 주소 응답 " + r.statusCode() + ": " + u);
                    continue;
                }
                if (body.length > MAX_BYTES) {
                    log.warning("리소스팩이 너무 큽니다 (" + body.length / 1048576 + "MB): " + u);
                    continue;
                }
                if (body.length < 4 || body[0] != 'P' || body[1] != 'K') {
                    log.warning("zip 파일이 아닙니다 (GitHub 페이지 주소가 아니라 raw 주소를 쓰세요): " + u);
                    continue;
                }
                byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(body);
                // 플레이어에게는 해시를 붙인 주소를 준다: 팩을 새로 올리면 주소도 바뀌어 CDN 의 옛 파일을 받지 않는다 (어느 IP 에서든 GitHub 에서 직접 받음)
                String sent = u.strip() + (u.contains("?") ? "&" : "?") + "v=" + hex(sha1).substring(0, 12);
                return Optional.of(new Found(sent, sha1, body.length));
            } catch (Exception e) {
                log.info("리소스팩 주소에 접속하지 못했습니다: " + u + " (" + e.getMessage() + ")");
            }
        }
        return Optional.empty();
    }

    /** 플러그인에 박아 둔 기본 주소: 저장소 맨 위의 팩 (config 를 고치지 않은 옛 서버도 이 주소로 받는다) */
    public static final List<String> DEFAULT_URLS = List.of(
            "https://raw.githubusercontent.com/minjun0629/RoyalRoad/main/VersaEra-ResourcePack.zip",
            "https://raw.githubusercontent.com/minjun0629/RoyalRoad/refs/heads/claude/inspiring-hopper-9qdtgd/VersaEra-ResourcePack.zip");

    /** config 의 주소 뒤에 기본 주소를 붙인다 (겹치면 하나만) */
    public static List<String> withDefaults(List<String> configured) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String u : configured) if (u != null && !u.isBlank()) out.add(u.strip());
        out.addAll(DEFAULT_URLS);
        return List.copyOf(out);
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
