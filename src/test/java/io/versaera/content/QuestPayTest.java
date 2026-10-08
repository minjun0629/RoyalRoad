package io.versaera.content;

import io.versaera.domain.economy.Money;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 의뢰 보수 눈금 (골드 · 실버 · 쿠퍼): 물건을 가져다 달라는 주민 의뢰는 그 물건 시세의 약 2 배 (적어도 시세 + 10쿠퍼).
 * 시세보다 적게 주면 시장에 파는 게 낫고, 몇 배씩 주면 의뢰가 돈 찍는 기계가 된다.
 */
class QuestPayTest {
    @Test
    void deliveryErrandsPayAboutTwiceTheGoods() {
        ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
        List<String> bad = new ArrayList<>();
        int n = 0;
        for (var a : c.archetypes().values())
            for (var q : a.quests()) {
                if (!q.target().startsWith("item:")) continue;
                Long each = c.market().prices().get(q.target().substring(5));
                assertNotNull(each, a.id() + ": 시세 없는 물건 " + q.target());
                long value = each * q.amount();
                n++;
                if (q.money() < value + 5 || q.money() > value * 2.2 + 10)
                    bad.add(a.id() + " " + q.target() + " ×" + q.amount() + ": " + Money.format(q.money()) + " (시세 " + Money.format(value) + ")");
            }
        assertTrue(n > 10);
        assertTrue(bad.isEmpty(), "보수가 눈금 밖:\n" + String.join("\n", bad));
    }
}
