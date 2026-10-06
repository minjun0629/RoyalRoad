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
                HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(u.strip())).timeout(Duration.ofSeconds(60)).GET().build(),
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
                return Optional.of(new Found(u.strip(), MessageDigest.getInstance("SHA-1").digest(body), body.length));
            } catch (Exception e) {
                log.info("리소스팩 주소에 접속하지 못했습니다: " + u + " (" + e.getMessage() + ")");
            }
        }
        return Optional.empty();
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
