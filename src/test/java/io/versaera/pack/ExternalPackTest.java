package io.versaera.pack;

import com.sun.net.httpserver.HttpServer;
import io.versaera.content.ContentBundle;
import io.versaera.platform.bukkit.pack.ExternalPack;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ExternalPackTest {
    @Test
    void firstReachableZipWinsAndHashMatchesBuiltPack() throws Exception {
        var pack = ResourcePackBuilder.build(ContentBundle.fromClasspath(getClass().getClassLoader()));
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/page", ex -> {
            byte[] b = "<html>github page</html>".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        http.createContext("/pack.zip", ex -> {
            ex.sendResponseHeaders(200, pack.zip().length);
            ex.getResponseBody().write(pack.zip());
            ex.close();
        });
        http.start();
        try {
            String base = "http://127.0.0.1:" + http.getAddress().getPort();
            var found = ExternalPack.resolve(List.of(base + "/missing.zip", base + "/page", base + "/pack.zip"), Logger.getLogger("test")).orElseThrow();
            assertEquals(base + "/pack.zip", found.url(), "404 · zip 아닌 응답은 건너뛴다");
            assertArrayEquals(pack.sha1(), found.sha1(), "서버가 계산한 해시 = 팩 해시");
            assertTrue(ExternalPack.resolve(List.of(base + "/page"), Logger.getLogger("test")).isEmpty());
        } finally {
            http.stop(0);
        }
    }
}
