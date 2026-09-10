package kr.qmftm.betterpets.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /pet rename [펫id] <이름>} 의 첫 인자 추측.
 *
 * <p>id 일 수도 있고 이름의 첫 단어일 수도 있어서 추측해야 하는 자리다. 앞자리 일치만
 * 보면 {@code /pet rename a b} 가 "이름을 'a b' 로" 가 아니라 "id 가 a 로 시작하는 펫을
 * 'b' 로" 가 된다.
 */
class PetCommandTest {

    @Test
    @DisplayName("보여주는 id 는 여덟 자리다. 네 자리 미만은 이름의 일부로 본다")
    void shortTokensAreNames() {
        assertFalse(PetCommand.looksLikeId("a"));
        assertFalse(PetCommand.looksLikeId("ab"));
        assertFalse(PetCommand.looksLikeId("abc"));
        assertTrue(PetCommand.looksLikeId("abcd"));
    }

    @Test
    @DisplayName("16진수가 아닌 글자가 섞이면 이름이다")
    void nonHexIsAName() {
        assertFalse(PetCommand.looksLikeId("바둑이"));
        assertFalse(PetCommand.looksLikeId("puppy"));
        assertFalse(PetCommand.looksLikeId("dragon"), "g 는 16진수가 아니다");
        assertFalse(PetCommand.looksLikeId(""));
    }

    @Test
    @DisplayName("실제로 쓰는 두 형태 — 앞 여덟 자리와 전체 UUID")
    void realIdFormsPass() {
        assertTrue(PetCommand.looksLikeId("3f2a91cd"));
        assertTrue(PetCommand.looksLikeId("3f2a91cd-1234-5678-9abc-def012345678"));
    }

    @Test
    @DisplayName("16진수로만 이뤄진 짧은 단어는 통과한다 — 그래도 소유한 펫 id 와 겹쳐야 대상이 된다")
    void hexLookingWordsStillPass() {
        // "beef" 같은 단어는 여기를 통과한다. 그 다음 관문이 "실제로 그 앞자리를 가진
        // 펫을 소유했는가" 라, 여기서 더 좁히면 진짜 id 를 거부하게 된다.
        assertTrue(PetCommand.looksLikeId("beef"));
    }
}
