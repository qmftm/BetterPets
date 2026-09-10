package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetSortTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final UUID OWNER = UUID.randomUUID();

    /** 종류 id 를 그대로 등급 서열로 쓴다. "3" 이 "1" 보다 좋은 등급이라는 뜻이다. */
    private static final ToIntFunction<PetData> RANK_BY_TYPE =
        pet -> Integer.parseInt(pet.typeId());

    private static PetData pet(final String typeRank, final long acquiredAt, final boolean active) {
        final PetData data = PetData.newBaby(OWNER, typeRank, acquiredAt);
        data.active(active);
        return data;
    }

    private static List<String> order(final PetData... pets) {
        final List<PetData> list = new ArrayList<>(List.of(pets));
        list.sort(PetSort.DEFAULT.comparator(RANK_BY_TYPE));
        return list.stream().map(PetData::typeId).toList();
    }

    @Test
    @DisplayName("소환 중인 펫이 맨 앞으로 온다 — 등급이 낮아도")
    void activeComesFirst() {
        final PetData weakButOut = pet("1", T0 + 5_000, true);
        final PetData strongAtHome = pet("9", T0, false);

        assertEquals(List.of("1", "9"), order(strongAtHome, weakButOut));
    }

    @Test
    @DisplayName("그다음은 등급이 높은 순")
    void thenByRarityDescending() {
        assertEquals(List.of("9", "5", "1"),
            order(pet("1", T0, false), pet("9", T0, false), pet("5", T0, false)));
    }

    @Test
    @DisplayName("등급이 같으면 오래 데리고 있던 순")
    void thenByAcquiredAt() {
        final PetData older = pet("5", T0, false);
        final PetData newer = pet("5", T0 + 60_000, false);

        // 같은 등급이라 획득 시각이 가른다. 결과는 id 가 아니라 시각으로 확인한다.
        final List<PetData> list = new ArrayList<>(List.of(newer, older));
        list.sort(PetSort.DEFAULT.comparator(RANK_BY_TYPE));
        assertEquals(List.of(T0, T0 + 60_000),
            list.stream().map(PetData::acquiredAt).toList());
    }

    @Test
    @DisplayName("모든 조건이 같아도 순서가 흔들리지 않는다")
    void tieBreakIsStable() {
        // 같은 순간에 두 마리를 받는 일이 실제로 있다 (관리자 지급). 못을 박지 않으면
        // 보관함을 열 때마다 자리가 바뀐다.
        final PetData a = pet("5", T0, false);
        final PetData b = pet("5", T0, false);

        final List<PetData> first = new ArrayList<>(List.of(a, b));
        final List<PetData> second = new ArrayList<>(List.of(b, a));
        first.sort(PetSort.DEFAULT.comparator(RANK_BY_TYPE));
        second.sort(PetSort.DEFAULT.comparator(RANK_BY_TYPE));

        assertEquals(first.stream().map(PetData::petId).toList(),
            second.stream().map(PetData::petId).toList(),
            "입력 순서가 달라도 같은 결과가 나와야 한다");
    }

    @Test
    @DisplayName("등급을 못 찾은 펫은 맨 뒤로 — 설정이 깨진 펫을 앞에 둘 이유가 없다")
    void unknownRarityGoesLast() {
        final PetData broken = pet("-1", T0, false);
        final PetData normal = pet("1", T0 + 60_000, false);

        assertEquals(List.of("1", "-1"), order(broken, normal));
    }

    private static List<String> orderBy(final PetSort sort, final PetData... pets) {
        final List<PetData> list = new ArrayList<>(List.of(pets));
        list.sort(sort.comparator(RANK_BY_TYPE));
        return list.stream().map(PetData::typeId).toList();
    }

    @Test
    @DisplayName("최신순은 등급도 소환 여부도 보지 않는다 — 방금 깐 알을 찾는 순서다")
    void newestIgnoresRarityAndActive() {
        final PetData old = pet("9", T0, true);
        final PetData fresh = pet("1", T0 + 60_000, false);

        assertEquals(List.of("1", "9"), orderBy(PetSort.NEWEST, old, fresh));
    }

    @Test
    @DisplayName("성장순은 단계를 먼저 보고, 같으면 성장도를 본다")
    void growthLooksAtStageFirst() {
        final PetData stage2 = pet("1", T0, false);
        stage2.growthStage(2);
        final PetData stage1High = pet("2", T0, false);
        stage1High.addGrowth(99, 100);

        assertEquals(List.of("1", "2"), orderBy(PetSort.GROWTH, stage1High, stage2),
            "성장도 99 인 1단계보다 2단계가 앞이다");
    }

    @Test
    @DisplayName("정렬은 한 바퀴 돌아 처음으로 온다")
    void sortCyclesBack() {
        PetSort sort = PetSort.DEFAULT;
        for (int i = 0; i < PetSort.values().length; i++) {
            sort = sort.next();
        }
        assertEquals(PetSort.DEFAULT, sort);
    }

    @Test
    @DisplayName("같은 값이면 정렬 방식이 달라도 순서가 흔들리지 않는다")
    void everySortIsStable() {
        final PetData a = pet("5", T0, false);
        final PetData b = pet("5", T0, false);

        for (final PetSort sort : PetSort.values()) {
            final List<PetData> first = new ArrayList<>(List.of(a, b));
            final List<PetData> second = new ArrayList<>(List.of(b, a));
            first.sort(sort.comparator(RANK_BY_TYPE));
            second.sort(sort.comparator(RANK_BY_TYPE));
            assertEquals(first.stream().map(PetData::petId).toList(),
                second.stream().map(PetData::petId).toList(), sort + " 가 흔들린다");
        }
    }
}
