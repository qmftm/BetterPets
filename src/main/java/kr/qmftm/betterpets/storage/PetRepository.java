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

    /**
     * 여러 마리를 한 번에 저장한다. <b>이쪽이 기본 연산이다.</b>
     *
     * <p>반대 방향(한 마리를 기본으로 두고 묶음을 반복문으로)이었는데, 파일 하나에
     * 여럿을 담는 구현에서는 그게 곧 같은 파일을 마리 수만큼 열고 닫는 코드가 된다.
     * 퇴장·종료 때 20마리가 한꺼번에 더러워지는 경우가 실제로 있다.
     *
     * <p><b>저장에 실패하면 던져야 한다.</b> {@link PetStore} 는 예외가 났을 때만
     * dirty 를 유지해 다음 기회에 다시 쓴다 — 조용히 실패하면 그게 곧 데이터 손실이다.
     */
    void saveAll(java.util.Collection<PetData> pets);

    /** 한 마리. 편의용이다. */
    default void save(final PetData pet) {
        saveAll(List.of(pet));
    }

    /**
     * 한 마리를 지운다.
     *
     * <p>소유자를 함께 받는다. 펫 id 만으로는 어느 파일에 있는지 알 수 없어서, 전체를
     * 훑는 구현이 나올 수밖에 없다 — 호출부는 언제나 소유자를 알고 있다.
     */
    void delete(UUID ownerId, UUID petId);

    /** 종료 시 남은 것을 밀어낸다. 구현체가 버퍼를 쓴다면 여기서 flush 한다. */
    default void flush() {}
}
