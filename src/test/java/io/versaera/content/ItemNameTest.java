package io.versaera.content;

import io.versaera.domain.item.ItemType;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** /va 지급 은 한국어 이름(띄어쓰기는 _)으로 아이템을 고른다 — 이름이 겹치면 한쪽을 줄 수 없다 */
class ItemNameTest {
    @Test
    void everyItemHasAKoreanNameNoOtherItemShares() {
        var c = ContentBundle.fromClasspath(ItemNameTest.class.getClassLoader());
        Map<String, List<String>> byName = c.items().stream().collect(Collectors.groupingBy(t -> t.name().trim().replace(' ', '_'),
                TreeMap::new, Collectors.mapping(ItemType::id, Collectors.toList())));
        List<String> dup = byName.entrySet().stream().filter(e -> e.getValue().size() > 1).map(e -> e.getKey() + "=" + e.getValue()).toList();
        assertTrue(dup.isEmpty(), "이름이 겹친다: " + dup);
        List<String> english = c.items().stream().filter(t -> !t.name().codePoints().anyMatch(cp -> cp >= 0xAC00 && cp <= 0xD7A3)).map(ItemType::id).toList();
        assertTrue(english.isEmpty(), "한국어 이름이 없다: " + english);
    }
}
