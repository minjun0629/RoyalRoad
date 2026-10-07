package io.versaera.pack;

import io.versaera.content.ContentBundle;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 리소스팩을 파일로 내보낸다 (gradle buildPack). 결과를 GitHub 에 올리면 서버가 그 주소로 팩을 내려 준다.
 * 사용: java io.versaera.pack.PackMain &lt;출력 폴더&gt; (gradle 은 저장소 맨 위)
 */
public final class PackMain {
    /** 저장소 맨 위에 올리는 팩 이름 — 이름이 바뀌면 플러그인의 기본 주소도 바꿔야 한다 */
    public static final String FILE = "VersaEra-ResourcePack.zip";

    private PackMain() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : ".");
        Files.createDirectories(out);
        ResourcePackBuilder.Pack pack = ResourcePackBuilder.build(ContentBundle.fromClasspath(PackMain.class.getClassLoader()));
        Files.write(out.resolve(FILE), pack.zip());
        System.out.println("pack " + pack.zip().length + " bytes · sha1 " + pack.sha1Hex() + " → " + out.toAbsolutePath());
    }
}
