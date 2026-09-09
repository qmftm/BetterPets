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

    void delete(UUID petId);

    /** 종료 시 남은 것을 밀어낸다. 구현체가 버퍼를 쓴다면 여기서 flush 한다. */
    default void flush() {}
}
