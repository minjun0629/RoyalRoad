package io.versaera.domain.art;

import io.versaera.domain.item.ItemType;

import java.util.List;

/**
 * 조각 재료의 "모양" (색 · 결) — 리소스팩과 서버가 같은 표를 쓴다 (ART-02).
 * 재료 아이템 id · 태그로 고른다. 모양마다 팩에 받침 · 몸체 · 장식 모델이 만들어진다.
 */
public final class ArtMaterials {
    public static final List<String> LOOKS = List.of("stone", "sandstone", "marble", "iron", "silver", "skymetal", "amethyst", "frost", "oak", "spruce");

    private ArtMaterials() {
    }

    public static String look(ItemType t) {
        String id = t.id();
        if (id.contains("marble")) return "marble";
        if (id.contains("sandstone")) return "sandstone";
        if (id.contains("silver")) return "silver";
        if (id.contains("sky")) return "skymetal";
        if (id.contains("frost")) return "frost";
        if (id.contains("highland") || id.contains("spruce")) return "spruce";
        if (t.tags().contains("gem")) return "amethyst";
        if (t.tags().contains("metal")) return "iron";
        if (t.tags().contains("wood")) return "oak";
        return "stone";
    }
}
