package kr.qmftm.betterpets.render;

import org.bukkit.entity.Player;

/**
 * 소환된 펫 하나의 렌더링 핸들.
 *
 * <p><b>{@link AutoCloseable} 인 것이 핵심이다.</b> BetterModel 문서가 명시하듯 트래커를 닫지
 * 않으면 유령 모델과 고아 스케줄 태스크가 남는다. 소유자 퇴장 · 서버 종료 · 월드 언로드 ·
 * 캐리어 사망 <b>네 경로 모두</b>에서 {@link #close()} 가 불려야 한다.
 *
 * <p>이 인터페이스에는 BetterModel 타입이 하나도 없다. 구현체만 알면 된다.
 */
public interface PetRenderHandle extends AutoCloseable {

    /** 모델을 제거한다. 여러 번 불러도 안전해야 한다. */
    @Override
    void close();

    boolean isClosed();

    /**
     * 애니메이션을 재생한다.
     *
     * <p><b>매 틱 부르면 안 된다.</b> 재생은 패킷을 만든다. 상태 머신이 전이하는
     * 프레임에서만 부른다.
     *
     * @param loop true 면 반복, false 면 1회 재생
     * @return 재생에 성공했는가. 모델에 해당 애니메이션이 없으면 false
     */
    boolean play(String animation, boolean loop);

    /**
     * 우선순위를 올려 겹쳐 재생한다. 공격·먹이 섭취처럼 이동 애니메이션 위에
     * 잠깐 얹었다가 돌아와야 하는 동작에 쓴다.
     *
     * @param onFinish 재생이 끝난 뒤 실행할 작업. 보통 이전 상태 복원
     */
    boolean playOverlay(String animation, int priority, Runnable onFinish);

    boolean stop(String animation);

    /** 모델 전체에 색을 입힌다. 등급 표시에 쓴다. */
    void tint(int rgb);

    /** 특정 플레이어에게만 숨긴다. */
    void hide(Player viewer);

    void show(Player viewer);
}
