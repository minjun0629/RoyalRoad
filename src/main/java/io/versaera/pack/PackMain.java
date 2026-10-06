package io.versaera.pack;

import io.versaera.content.ContentBundle;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 리소스팩을 파일로 내보낸다 (gradle buildPack). 결과를 GitHub 에 올리면 서버가 그 주소로 팩을 내려 준다.
 * 사용: java io.versaera.pack.PackMain &lt;출력 폴더&gt;
 */
public final class PackMain {
    private PackMain() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "pack");
        Files.createDirectories(out);
        ResourcePackBuilder.Pack pack = ResourcePackBuilder.build(ContentBundle.fromClasspath(PackMain.class.getClassLoader()));
        Files.write(out.resolve("VersaEra-pack.zip"), pack.zip());
        Files.writeString(out.resolve("VersaEra-pack.zip.sha1"), pack.sha1Hex() + "\n");
        System.out.println("pack " + pack.zip().length + " bytes · sha1 " + pack.sha1Hex() + " → " + out.toAbsolutePath());
    }
}
