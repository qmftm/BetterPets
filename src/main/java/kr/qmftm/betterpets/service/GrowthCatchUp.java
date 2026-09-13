package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.Tags;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.service.GrowthService.StageResult;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 시간이 흘러 일어난 진화를 지금 상태에 반영한다.
 *
 * <p>성장도 자체는 지연 계산이라 읽는 순간 최신이 된다(꺼내져 있고 next-stage 가 있는
 * 펫만 — {@link GrowthService#growsOverTime} 참고). 그런데 <b>진화는 계산이 아니라
 * 사건이다</b> — 누군가 확인해 줘야 일어난다. 확인하는 자리가 틱 루프 하나뿐이었고
 * 틱은 <b>소환된 펫만</b> 돈다. 그래서 보관함에 넣어둔 펫은 성장도가 상한에 붙은 채
 * 굳었다 — 상한에 붙으면 {@code refresh} 가 값을 바꾸지 않으니, 꺼내서 틱을 태워도
 * <b>영원히</b> 그대로다.
 *
 * <p>그래서 펫을 <b>읽는 자리마다</b> 여기를 지나게 한다 — 접속 직후, 보관함을 열 때,
 * {@code /pet list}, 그리고 틱 루프. 넷 다 이미 전체 목록을 훑는 자리라 새로 도는
 * 주기 작업이 생기지 않는다. (지연 계산을 택한 이유가 그거였다)
 *
 * <p>알림은 여기 한 곳에서만 낸다. 먹여서 진화한 쪽은 {@code InteractionListener} 가
 * 자기 문구를 쓴다 — "먹이를 줬더니 진화했다"와 "가만히 뒀더니 진화했다"는 다른 사건이다.
 */
public final class GrowthCatchUp {

    private final PetStore store;
    private final PetService pets;
    private final GrowthService growth;
    private final BroadcastService broadcasts;
    private final Messages messages;

    public GrowthCatchUp(final PetStore store,
                         final PetService pets,
                         final GrowthService growth,
                         final BroadcastService broadcasts,
                         final Messages messages) {
        this.store = store;
        this.pets = pets;
        this.growth = growth;
        this.broadcasts = broadcasts;
        this.messages = messages;
    }

    /** 이 플레이어가 가진 펫 전부를 지금 시각에 맞춘다. */
    public void all(final Player owner) {
        for (final PetData data : store.owned(owner.getUniqueId())) {
            one(owner, data);
        }
    }

    /**
     * 한 마리.
     *
     * @return 이번 확인으로 일어난 일. 아무 일도 없었으면 {@link StageResult#NONE}
     */
    public StageResult one(final Player owner, final PetData data) {
        if (data.stage() != LifeStage.NORMAL) {
            return StageResult.NONE;    // 돼지는 더 자라지 않는다
        }
        final int before = data.growth();
        // 경과 반영과 단계 확인은 짝이다. 왜 나눠 부르면 안 되는지는 catchUp 의 주석에.
        final StageResult result = growth.catchUp(data);
        if (result == StageResult.NONE) {
            if (data.growth() != before) {
                store.saveAsync(data);
            }
            return result;
        }
        store.saveAsync(data);

        // 소환 중이었다면 모델과 능력을 지금 상태에 맞춘다. 보관함에 있으면 할 일이 없다.
        if (pets.refreshAfterGrowth(owner, data) == PetService.RefreshResult.DETACHED) {
            // 진화한 종류의 모델이 없어서 눈앞의 펫이 사라졌다. "진화했습니다!" 만
            // 보내고 넘어가면 플레이어는 빈자리를 보며 무슨 일인지 알 수 없다.
            messages.send(owner, "pet.model-missing");
            owner.playSound(owner.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
        }
        announce(owner, data);
        return result;
    }

    /**
     * 주인에게 알리고, 알릴 만한 일이면 서버에도 알린다.
     *
     * <p><b>주인에게는 등급과 무관하게 알린다.</b> 방송은 문턱이 있어서 흔한 펫은
     * 걸리지 않는데, 그것만 두면 D등급 펫도 조용히 진화한다. 알림의 목적은
     * 자랑이 아니라 "네 펫이 달라졌다"를 알리는 것이다.
     */
    private void announce(final Player owner, final PetData data) {
        final PetType type = pets.catalog().type(data.typeId()).orElse(null);
        if (type == null) {
            return;
        }
        // 이름은 지금 종류 기준이다. 진화로 종류가 바뀌었으면 새 이름을 불러야 한다.
        final String name = Tags.strip(data.displayNameOr(type.displayName()));
        owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        messages.send(owner, "pet.stage-up",
            "name", name,
            "stage", String.valueOf(data.growthStage()));
        broadcasts.onStageUp(owner, data, type);
    }
}
