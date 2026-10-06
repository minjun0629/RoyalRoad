package io.versaera.domain.pack;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * 리소스팩 모델 번호 (CustomModelData). 모델 이름에서 결정적으로 만든다 → 서버와 리소스팩 생성기가 같은 번호를 쓴다.
 * 범위 100000 ~ 999999 (다른 팩과 겹치지 않게 높은 대역). 충돌은 ResourcePackBuilder 가 빌드할 때 검사한다.
 */
public final class PackIds {
    private PackIds() {
    }

    public static int modelData(String model) {
        CRC32 c = new CRC32();
        c.update(model.getBytes(StandardCharsets.UTF_8));
        return 100_000 + (int) (c.getValue() % 900_000);
    }
}
