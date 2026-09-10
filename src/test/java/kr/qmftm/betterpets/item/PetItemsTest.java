package kr.qmftm.betterpets.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetItemsTest {

    @Test
    @DisplayName("흔한 확률은 소수점 한 자리")
    void commonOddsUseOneDecimal() {
        assertEquals("50.0%", PetItems.percent(1, 2));
        assertEquals("98.0%", PetItems.percent(50, 51));
        assertEquals("2.0%", PetItems.percent(1, 51));
    }

    @Test
    @DisplayName("아주 낮은 확률은 두 자리까지 — 0% 로 보이면 뽑을 마음이 사라진다")
    void tinyOddsKeepMoreDigits() {
        assertEquals("0.05%", PetItems.percent(1, 2000));
        assertEquals("0.01%", PetItems.percent(1, 10000));
    }

    @Test
    @DisplayName("100%도 그대로 나온다 — 가중치가 하나뿐인 랜덤 알")
    void singleEntryIsHundred() {
        assertEquals("100.0%", PetItems.percent(7, 7));
    }

    @Test
    @DisplayName("서버 로케일이 무엇이든 소수점은 점이다")
    void decimalSeparatorIsAlwaysADot() {
        // 로케일에 따라 쉼표가 나오면 "0,05%" 가 되어 확률로 안 읽힌다.
        assertEquals("12.5%", PetItems.percent(1, 8));
    }
}
