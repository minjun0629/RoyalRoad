package io.versaera.platform.bukkit.pack;

import com.sun.net.httpserver.HttpServer;
import io.versaera.pack.ResourcePackBuilder;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * 내장 HTTP 서버로 리소스팩을 내려 준다 (RP-01). GitHub 등 외부 주소(pack.urls)를 못 쓸 때의 대안.
 * GET /versaera.zip 하나만 응답한다.
 */
public final class PackServer {
    private final HttpServer http;
    private final String url;

    public PackServer(Plugin plugin, ResourcePackBuilder.Pack pack, int port, String publicUrl, String serverIp) throws IOException {
        this.http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/versaera.zip", ex -> {
            try (ex) {
                if (!"GET".equals(ex.getRequestMethod())) {
                    ex.sendResponseHeaders(405, -1);
                    return;
                }
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, pack.zip().length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(pack.zip());
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.FINE, "리소스팩 전송 중단", e);
            }
        });
        http.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "versa-pack");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        String host = serverIp == null || serverIp.isBlank() ? "localhost" : serverIp;
        this.url = publicUrl == null || publicUrl.isBlank() ? "http://" + host + ":" + port + "/versaera.zip" : publicUrl;
    }

    public String url() {
        return url;
    }

    public void stop() {
        http.stop(0);
    }
}
