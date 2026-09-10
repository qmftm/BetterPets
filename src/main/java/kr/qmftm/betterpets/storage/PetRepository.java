package kr.qmftm.betterpets.storage;

import kr.qmftm.betterpets.domain.PetData;

import java.util.List;
import java.util.UUID;

/**
 * 펫 개체 영속화.
 *
 * <p>구현체는 <b>메인 스레드에서 불리지 않는다</b> — {@link PetStore} 가 전용 실행자 위에서만
 * 호출한다. 그래서 여기 메서드는 블로킹이어도 된다.
 *
 * <p>지금은 YAML 구현만 있다. 규모가 커져 DB가 필요해지면 이 인터페이스만 새로 구현하면 된다.
 */
public interface PetRepository {

    List<PetData> loadOwner(UUID ownerId);

    void save(PetData pet);

    /**
     * 여러 마리를 한 번에 저장한다.
     *
     * <p>기본 구현은 한 마리씩 부르지만, <b>파일 하나에 여럿을 담는 구현은 반드시
     * 재정의해야 한다.</b> 안 그러면 같은 파일을 마리 수만큼 열고 닫는다.
     */
    default void saveAll(java.util.Collection<PetData> pets) {
        for (final PetData pet : pets) {
            save(pet);
        }
    }

    void delete(UUID petId);

    /** 종료 시 남은 것을 밀어낸다. 구현체가 버퍼를 쓴다면 여기서 flush 한다. */
    default void flush() {}
}
