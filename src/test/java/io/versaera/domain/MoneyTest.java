package io.versaera.domain;

import io.versaera.domain.economy.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test
    void formatsGoldSilverCopper() {
        assertEquals("0쿠퍼", Money.format(0));
        assertEquals("5쿠퍼", Money.format(5));
        assertEquals("2실버 50쿠퍼", Money.format(250));
        assertEquals("3골드 5쿠퍼", Money.format(30_005));
        assertEquals("1,200골드", Money.format(12_000_000));
        assertEquals("-1실버", Money.format(-100));
        assertEquals("2.5실버", Money.brief(250));
        assertEquals("1.2골드", Money.brief(12_000));
    }

    @Test
    void parsesWhatPeopleType() {
        assertEquals(5, Money.parse("5"), "단위 없으면 쿠퍼");
        assertEquals(30_000 + 2_000, Money.parse("3골드20실버"));
        assertEquals(30_000 + 2_000 + 7, Money.parse("3g 20s 7c"));
        assertEquals(15_000, Money.parse("1.5골드"));
        assertEquals(50, Money.parse("50쿠퍼"));
        assertEquals(-1, Money.parse("abc"));
        assertEquals(-1, Money.parse(""));
        assertEquals(Money.silver(0.3), 30);
    }
}
