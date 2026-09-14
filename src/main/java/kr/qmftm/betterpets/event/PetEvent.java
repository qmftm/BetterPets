package kr.qmftm.betterpets.event;

import kr.qmftm.betterpets.domain.PetData;

/**
 * "이 이벤트는 펫 하나를 들고 있다"는 표시. {@code event-pet}(Skript) 하나가 이 인터페이스만
 * 보고 어떤 펫 이벤트든 처리하게 하려고 뺐다 — 이벤트가 늘 때마다 그쪽 코드를 고칠
 * 필요가 없다.
 */
public interface PetEvent {

    PetData pet();
}
