# BetterPets 개발 계획서

> 악어의 놀이터 2 스타일 펫 시스템을 **BetterModel** 기반으로 구현하기 위한 설계·개발 계획

| 항목 | 내용 |
| --- | --- |
| 문서 버전 | v1.0 (초안) |
| 작성일 | 2026-09-08 |
| 대상 저장소 | `qmftm/BetterPets` |
| 상태 | 리뷰 대기 — 3장 "확인 필요 사항" 답변 후 v1.1로 확정 |

---

## 0. 요약 (TL;DR)

Paper 플러그인 **BetterPets**를 만든다. 플레이어가 소유한 펫을 소환하면 BetterModel이 렌더링하는 3D 모델이 따라다니고, 레벨/능력/등급을 가지며, GUI로 관리한다.

핵심 설계 결정 4가지:

1. **렌더링 캐리어** — 펫 1마리 = 보이지 않는 `Mob` 엔티티 1개 + BetterModel `EntityTracker`. (`DummyTracker`가 아님 — 근거는 5.2)
2. **이동** — 바닐라 AI를 끄고(`setAI(false)`) 자체 이동 컨트롤러로 구동. 텔레포트 폴백 포함.
3. **애니메이션** — 상태 머신이 상태 전이 시점에만 `animate()`를 호출. 매 틱 호출 금지.
4. **영속화** — SQLite 기본, `PetRepository` 인터페이스로 추상화해 MySQL 교체 가능.

총 8개 마일스톤, 예상 소요 **10~13주** (1인 파트타임 기준). 상세는 15장.

---

## 1. 목표와 범위

### 1.1 목표

- 플레이어별로 여러 마리 펫을 **소유·보관·장착**할 수 있다.
- 장착한 펫은 3D 모델로 렌더링되어 플레이어를 **따라다니고 애니메이션**한다.
- 펫은 **등급·레벨·경험치**를 가지며 성장하면 능력이 강해진다.
- 펫은 **패시브 버프 / 트리거 능력 / 액티브 스킬**을 소유자에게 제공한다.
- 모든 펫 정의는 **설정 파일로 데이터화**되어 코드 수정 없이 펫을 추가할 수 있다.

### 1.2 범위에 포함 (In scope)

| # | 기능 | 마일스톤 |
| --- | --- | --- |
| F1 | 펫 소환 / 해제 / 모델 렌더링 | M1 |
| F2 | 추종 이동 · 텔레포트 · 상태 애니메이션 | M2 |
| F3 | 펫 인벤토리(보관함) 및 상세 GUI | M4 |
| F4 | 레벨 · 경험치 · 등급 성장 | M5 |
| F5 | 능력 시스템 (패시브 / 트리거 / 액티브) | M5 |
| F6 | 펫 획득 경로 (알 아이템, 뽑기, 관리자 지급) | M6 |
| F7 | 이름 변경, 해방(삭제), 진화 | M6 |
| F8 | 데이터 영속화 (SQLite / MySQL) | M3 |
| F9 | 명령어 · 권한 · PlaceholderAPI | M3, M7 |

### 1.3 범위에서 제외 (Out of scope — v1.0)

- 펫 탑승(라이딩) — v1.1 이후. BetterModel의 seat 본 기능 검증이 선행되어야 함.
- 펫 간 전투 / PvP 펫 대전
- 펫 거래 시장 (경매장)
- 웹 대시보드
- **모델 에셋 제작** — 별도 트랙. 6장 참고.

---

## 2. 전제 조건

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 서버 플랫폼 | Paper (Folia 호환 고려) | BetterModel이 Folia 지원 |
| Minecraft 버전 | 26.2 | BetterModel 3.4.1의 `minecraft_version` |
| Java | **25** | BetterModel 3.4.1 모듈 메타데이터 `org.gradle.jvm.version: 25` |
| BetterModel | 3.4.1 (2026-08-11) | Maven Central 최신 릴리스 |
| 언어 | Java 25 | 3.1 참고 |
| 빌드 | Gradle (Kotlin DSL) + Shadow | |

> ⚠️ **Java 25는 강한 제약이다.** 운영 서버가 Java 21에 묶여 있다면 BetterModel을 3.4.1로 올릴 수 없고, 2.x 계열(`bettermodel-bukkit-api:2.2.0`, Java 21)로 내려야 한다. 이 경우 API 시그니처가 달라지므로 **개발 착수 전 운영 서버의 JDK 버전을 반드시 확정할 것.**

---

## 3. 확인 필요 사항 (미해결 질문)

이 계획서는 아래 항목에 대해 가정을 세우고 작성했다. 답변에 따라 설계가 바뀌는 부분을 표시했다.

| # | 질문 | 현재 가정 | 영향 범위 |
| --- | --- | --- | --- |
| Q1 | 운영 서버 JDK 버전은? | Java 25 | 2장 전체 — BetterModel 버전 결정 |
| Q2 | 예상 동시 접속자 수는? | 50~100명 | 13장 성능 설계, 영속화 백엔드 |
| Q3 | 펫 모델 에셋은 이미 있는가? | 없음 — 신규 제작 필요 | 6장, M1 일정 |
| Q4 | 기존 서버 DB(MySQL)가 있는가? | 없음 — SQLite 기본 | 8장 영속화 |
| Q5 | 경제 플러그인(Vault) 사용 여부 | 사용 | 12장 연동, 뽑기 구현 |
| Q6 | "악어의 놀이터 2 스타일"의 구체적 기준은? | 아래 참고 | 9장, 10장 밸런싱 |

**Q6 관련 — 본 계획서가 가정한 "악어의 놀이터 2 스타일"**

원 서버의 정확한 펫 사양을 확인할 수 없어, 해당 장르의 일반적인 형태로 설계했다. 아래와 다른 부분이 있으면 알려주면 반영한다.

- 커스텀 3D 모델 펫이 플레이어를 따라다님
- 등급(일반/희귀/영웅/전설)이 있고 등급별 성능 배율이 다름
- 알 아이템 또는 뽑기로 획득
- 먹이/경험치로 레벨업하며 능력치가 성장
- 소유자에게 패시브 버프 제공 (이동속도, 공격력, 채굴 속도 등)
- 여러 마리 보유 후 한 마리만 장착

---

## 4. 시스템 아키텍처

### 4.1 레이어 구조

```
┌─────────────────────────────────────────────────────┐
│  Presentation                                       │
│  ├─ command/   /pet, /petadmin                      │
│  ├─ gui/       PetBoxMenu, PetDetailMenu            │
│  └─ listener/  Join, Quit, Damage, Death, Break     │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│  Application (서비스)                                │
│  ├─ PetService        소환/해제/획득/해방            │
│  ├─ ProgressService   경험치/레벨업/진화             │
│  └─ AbilityService    능력 적용/해제/쿨다운          │
└────────────────────┬────────────────────────────────┘
                     │
┌──────────┬─────────▼─────────┬──────────────────────┐
│ Domain   │  Runtime          │  Infrastructure      │
│ PetType  │  ActivePet        │  PetRepository       │
│ PetData  │  PetRenderer      │  SqlitePetRepository │
│ Rarity   │  MovementCtrl     │  ConfigLoader        │
│ Ability  │  AnimationState   │  BetterModelBridge   │
└──────────┴───────────────────┴──────────────────────┘
```

**의존 방향은 항상 안쪽(Domain)을 향한다.** Domain은 Bukkit API를 모른다 — 순수 데이터와 규칙만 담아 단위 테스트가 가능하게 한다.

### 4.2 패키지 구조

```
kr.qmftm.betterpets
├─ BetterPetsPlugin.java          진입점, 부트스트랩
├─ domain/
│   ├─ PetType.java               펫 "종류" 정의 (불변, 설정에서 로드)
│   ├─ PetData.java               펫 "개체" 데이터 (가변, DB 저장 대상)
│   ├─ Rarity.java                등급 enum + 배율
│   ├─ LevelCurve.java            경험치 곡선 계산
│   └─ ability/
│       ├─ AbilityDefinition.java
│       ├─ AbilityType.java       PASSIVE / TRIGGER / ACTIVE
│       └─ AbilityContext.java
├─ runtime/
│   ├─ ActivePet.java             소환된 펫 1마리 (엔티티+트래커+상태)
│   ├─ PetRegistry.java           UUID(플레이어) → ActivePet
│   ├─ movement/
│   │   ├─ MovementController.java
│   │   └─ MovementState.java     IDLE/WALK/RUN/TELEPORT
│   ├─ animation/
│   │   ├─ AnimationStateMachine.java
│   │   └─ AnimationSet.java      상태→애니메이션명 매핑
│   └─ PetTicker.java             전역 틱 루프
├─ render/
│   ├─ BetterModelBridge.java     ★ BetterModel API 격리 계층
│   └─ PetRenderHandle.java       트래커 래퍼 (close 보장)
├─ service/
│   ├─ PetService.java
│   ├─ ProgressService.java
│   └─ AbilityService.java
├─ ability/impl/                  능력 구현체들
├─ storage/
│   ├─ PetRepository.java         인터페이스
│   ├─ SqlitePetRepository.java
│   ├─ MySqlPetRepository.java
│   └─ PetCache.java              write-behind 캐시
├─ config/
│   ├─ MainConfig.java
│   ├─ PetTypeLoader.java
│   └─ Messages.java
├─ gui/
├─ command/
├─ listener/
└─ integration/                   Vault, PlaceholderAPI
```

### 4.3 ★ BetterModelBridge — 가장 중요한 설계 원칙

**BetterModel API 호출은 `render/BetterModelBridge.java` 한 파일 안에서만 일어난다.** 나머지 코드는 BetterModel 타입을 import하지 않는다.

이유:

- BetterModel은 2.x → 3.x에서 **아티팩트 이름 자체가 바뀌었다** (`bettermodel` → `bettermodel-bukkit-api`). 3.x에서는 멀티플랫폼화로 `BukkitAdapter.adapt()` 경유가 추가됐다. 앞으로도 깨질 수 있다.
- 브릿지 하나만 고치면 버전 업그레이드가 끝난다. 이 격리가 없으면 업그레이드마다 전 코드베이스를 훑어야 한다.
- 단위 테스트에서 브릿지를 가짜 구현으로 교체할 수 있다.

```java
public interface PetRenderer {
    PetRenderHandle spawn(Entity carrier, String modelId);
    void playAnimation(PetRenderHandle handle, String animation, boolean loop);
    void stopAnimation(PetRenderHandle handle, String animation);
    void applyTint(PetRenderHandle handle, int rgb);
    void setVisibleTo(PetRenderHandle handle, Predicate<Player> filter);
    void despawn(PetRenderHandle handle);
}
```

---

## 5. 펫 렌더링 설계

### 5.1 BetterModel 연동 기본 흐름

BetterModel 3.4.1 기준 API (패키지 루트: `kr.toxicity.model.api`):

```java
// 1) 모델 조회 → 트래커 생성
EntityTracker tracker = BetterModel.model("pet_wolf")
        .map(renderer -> renderer.getOrCreate(BukkitAdapter.adapt(carrier)))
        .orElse(null);

// 2) 생성 시점에 속성 지정 (등급별 틴트 등)
EntityTracker tracker = BetterModel.model("pet_wolf")
        .map(renderer -> renderer.create(
                BukkitAdapter.adapt(carrier),
                TrackerModifier.DEFAULT,
                t -> t.update(TrackerUpdateAction.tint(0xFFD700))))
        .orElse(null);

// 3) 애니메이션 재생
BetterModel.registry(BukkitAdapter.adapt(carrier))
        .map(reg -> reg.tracker("pet_wolf"))
        .ifPresent(t -> t.animate("walk", AnimationModifier.DEFAULT));

// 4) 1회 재생 + 완료 콜백
tracker.animate("attack", AnimationModifier.DEFAULT_WITH_PLAY_ONCE,
        () -> abilityService.onAttackAnimationEnd(petId));

// 5) 정리 — 반드시 호출
tracker.close();
```

> ⚠️ **트래커 누수는 이 프로젝트의 1순위 위험이다.** BetterModel 문서가 명시하듯 트래커를 닫지 않으면 "유령 모델"과 고아 스케줄 태스크가 남아 서버 자원을 갉아먹는다. `PetRenderHandle`이 `AutoCloseable`을 구현하게 하고, 플레이어 퇴장·서버 종료·월드 언로드·엔티티 사망 **네 경로 모두**에서 close를 보장한다. M1의 완료 조건에 "트래커 누수 0" 검증을 넣는다.

### 5.2 캐리어 엔티티 선택 — 결정과 근거

| 방식 | 장점 | 단점 | 채택 |
| --- | --- | --- | --- |
| **A. 실제 `Mob` + `EntityTracker`** | 히트박스·이름표·머리 회전이 BetterModel 기본 동작으로 작동. 클릭 상호작용 무료. 위치 동기화가 바닐라 처리 | 엔티티 카운트 증가, 몹 캡 간섭 가능 | ✅ **채택** |
| B. `DummyTracker` (엔티티 없음) | 엔티티 0개, 최소 오버헤드 | 클릭 판정·충돌을 전부 직접 구현. 이름표/히트박스 수동 | ❌ |
| C. `ArmorStand` 캐리어 | 가볍고 몹 캡 무관 | Mob이 아니라 Pathfinder 사용 불가, 일부 BetterModel 기능 제약 | ❌ |

**결정: A안.** 펫은 "클릭해서 상호작용하는 대상"이고 이름표가 필요하다. 이걸 직접 만드는 비용(레이캐스트 클릭 판정, 커스텀 이름표 렌더링)이 엔티티 1개 비용보다 훨씬 크다.

캐리어 엔티티 설정:

```java
Mob carrier = world.spawn(loc, Allay.class, SpawnReason.CUSTOM, mob -> {
    mob.setAI(false);              // 바닐라 AI 완전 차단 — 이동은 우리가 제어
    mob.setInvisible(true);        // 실제 모델은 BetterModel이 그림
    mob.setSilent(true);
    mob.setInvulnerable(true);
    mob.setPersistent(false);      // 청크 저장 대상에서 제외
    mob.setRemoveWhenFarAway(false);
    mob.setCollidable(false);
    mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1.0);
});
carrier.getPersistentDataContainer().set(PET_KEY, PersistentDataType.STRING, petId.toString());
```

- **PDC 태깅 필수**: 서버가 비정상 종료된 뒤 남은 캐리어를 시작 시 청소하려면 식별자가 필요하다.
- **`Allay` 선택 이유**: 몸집이 작아 히트박스 간섭이 적고, 기본 부양(hover) 특성이 `setAI(false)` 상태에선 무의미해져 부작용이 없다. 최종 선택은 M1에서 실측 후 확정.

### 5.3 가시성 제어

BetterModel의 `viewFilter`로 "본인에게만 보이는 펫" 같은 옵션을 구현할 수 있다.

```java
BetterModel.eventBus().subscribe(plugin, CreateEntityTrackerEvent.class, event ->
        event.tracker().getPipeline().viewFilter(viewer -> visibilityRule.test(viewer)));
```

용도: 설정 `pet.visible-to-others: false`, 특정 월드에서 펫 숨김, 관전 모드 처리.

---

## 6. 모델 에셋 트랙 (병행 작업)

> ⚠️ **저작권 주의.** 악어의 놀이터 2에서 사용 중인 실제 모델·텍스처 파일을 추출해 쓰는 것은 저작권 침해다. **"스타일과 시스템 구조를 참고"하는 것과 "에셋을 복제"하는 것은 다르다.** 모든 `.bbmodel` 파일은 직접 제작하거나 사용 허가가 명확한 것만 쓴다.

| 단계 | 작업 | 산출물 |
| --- | --- | --- |
| A1 | BlockBench로 펫 모델 제작 | `pet_*.bbmodel` |
| A2 | 애니메이션 제작 — 최소 `idle`, `walk`, `run` 필수 | 위 파일에 포함 |
| A3 | `BetterModel/models/` 에 배치 | 서버 배포물 |
| A4 | 리소스팩 생성 및 배포 경로 확정 | `resourcepack.zip` |

**애니메이션 명명 규칙 (플러그인이 이 이름을 기대한다):**

| 이름 | 필수 | 재생 | 용도 |
| --- | --- | --- | --- |
| `idle` | ✅ | loop | 정지 상태 |
| `walk` | ✅ | loop | 걷기 |
| `run` | ✅ | loop | 달리기 |
| `sit` | | loop | 대기 명령 |
| `attack` | | once | 공격 능력 발동 |
| `skill` | | once | 액티브 스킬 |
| `spawn` | | once | 소환 연출 |

필수 3종이 없는 모델은 로드 시 경고 후 `idle`로 폴백한다.

---

## 7. 펫 행동 설계

### 7.1 이동 상태 머신

```
        거리 < 2.5                거리 3~8            거리 8~24         거리 > 24 또는 다른 월드
   ┌──────────────┐        ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
   │     IDLE     │◄──────►│     WALK     │◄──►│     RUN      │───►│  TELEPORT    │
   └──────────────┘        └──────────────┘    └──────────────┘    └──────┬───────┘
          ▲                                                                │
          └────────────────────────────────────────────────────────────────┘
```

| 상태 | 조건 | 동작 |
| --- | --- | --- |
| `IDLE` | 거리 < 2.5 | 정지, 소유자 방향으로 머리 회전 |
| `WALK` | 2.5 ≤ 거리 < 8 | 목표 지점으로 보간 이동 (기본 속도) |
| `RUN` | 8 ≤ 거리 < 24 | 보간 이동 (가속 속도) |
| `TELEPORT` | 거리 ≥ 24 또는 다른 월드 또는 3초 이상 경로 실패 | 소유자 뒤쪽으로 즉시 이동 + 파티클 |

**목표 지점**: 소유자 위치에서 시야 반대 방향으로 `follow-distance`(기본 2.0블록) 떨어진 지점. 소유자가 제자리 회전할 때 펫이 따라 도는 것을 막기 위해 **목표 지점에 데드존(0.8블록)** 을 둔다.

### 7.2 이동 구현 방식

`setAI(false)` 상태이므로 바닐라 경로탐색을 쓰지 않는다. 대신:

1. 목표 지점을 향한 방향 벡터 계산
2. 속도 상한을 적용해 다음 위치 계산
3. **간이 지형 처리**: 진행 방향 앞 1블록이 막혔고 그 위가 비었으면 y를 0.5 올림(계단/블록 오르기). 아래가 비었으면 최대 3블록까지 하강
4. `carrier.teleport()` 대신 **`carrier.setVelocity()` 혹은 위치 보간 후 `teleport`** — 어느 쪽이 클라이언트에서 부드러운지 M2에서 실측 비교 후 확정
5. 물/용암/공허 감지 시 즉시 `TELEPORT` 상태로 전이

> 바닐라 `Pathfinder`(`Mob#getPathfinder().moveTo()`)를 쓰는 대안이 있다. 지형 처리를 공짜로 얻지만 `setAI(false)`와 병행하기 까다롭고 CPU 비용이 크다. **M2에서 두 방식을 프로토타입으로 비교하고 결정한다.** 이 계획서는 자체 구현을 기본안으로 잡았다.

### 7.3 틱 루프 설계

```java
// 2틱(0.1초) 주기 전역 태스크 — 펫마다 태스크를 만들지 않는다
scheduler.runTaskTimer(plugin, this::tickAll, 20L, 2L);

private void tickAll() {
    for (ActivePet pet : registry.all()) {
        Player owner = pet.owner();
        if (owner == null || !owner.isOnline()) continue;
        if (!owner.getWorld().equals(pet.carrier().getWorld())) { pet.teleportToOwner(); continue; }
        pet.movement().tick();
        pet.animation().tick();   // 상태가 바뀐 경우에만 animate() 호출
    }
}
```

원칙:
- 펫당 개별 스케줄 태스크 **금지** — 태스크 수가 동접에 비례해 폭증한다.
- 오프라인 소유자의 펫은 즉시 디스폰. 틱 대상에서 제외.
- **Folia 대응**: 엔티티 소속 리전에서 실행되어야 하므로, Folia 감지 시 `EntityScheduler`(`entity.getScheduler().runAtFixedRate(...)`)로 전환한다. 이 분기는 `PetTicker` 안에 캡슐화한다.

### 7.4 애니메이션 상태 머신

```java
void tick() {
    AnimationState next = resolve(movement.state());
    if (next == current) return;        // ★ 변화 없으면 아무것도 하지 않는다
    renderer.stopAnimation(handle, current.animationName());
    renderer.playAnimation(handle, next.animationName(), next.loop());
    current = next;
}
```

**매 틱 `animate()`를 호출하면 안 된다.** BetterModel은 애니메이션 재생 시 패킷을 발생시키므로, 상태가 바뀐 프레임에서만 호출해야 한다. 이것이 13장 성능 목표의 핵심이다.

일회성 애니메이션(`attack`, `skill`)은 상태 머신 위에 **오버레이 레이어**로 재생하고, 완료 콜백에서 기존 루프 상태를 복원한다.

---

## 8. 데이터 모델 및 영속화

### 8.1 도메인 모델

**`PetType`** — 펫 "종류" 정의. 설정에서 로드하는 불변 객체.

```java
public record PetType(
        String id,                      // "wolf_alpha"
        String displayName,             // "알파 늑대"
        String modelId,                 // BetterModel 모델명
        Rarity rarity,
        AnimationSet animations,
        MovementProfile movement,       // 속도, 추종거리
        List<AbilityDefinition> abilities,
        int maxLevel,
        String evolvesInto               // nullable — 진화 대상 PetType id
) {}
```

**`PetData`** — 플레이어가 가진 펫 "개체". DB 저장 대상.

```java
public final class PetData {
    private final UUID petId;          // 개체 고유 ID (PK)
    private final UUID ownerId;
    private final String typeId;       // → PetType 참조
    private String nickname;           // nullable
    private int level;
    private long experience;
    private long acquiredAt;
    private boolean active;            // 현재 장착 중인가
}
```

**`Rarity`** — 등급과 배율.

| 등급 | 능력 배율 | 최대 레벨 | 색상 |
| --- | --- | --- | --- |
| `COMMON` 일반 | 1.0 | 30 | 회색 |
| `RARE` 희귀 | 1.3 | 50 | 파랑 |
| `EPIC` 영웅 | 1.7 | 70 | 보라 |
| `LEGENDARY` 전설 | 2.2 | 100 | 금색 |

> 위 수치는 **플레이스홀더**다. 실제 밸런싱은 M5에서 별도 문서로 잡는다.

### 8.2 DB 스키마

```sql
CREATE TABLE IF NOT EXISTS bp_pet (
    pet_id      CHAR(36)     NOT NULL PRIMARY KEY,
    owner_id    CHAR(36)     NOT NULL,
    type_id     VARCHAR(64)  NOT NULL,
    nickname    VARCHAR(32)  NULL,
    level       INT          NOT NULL DEFAULT 1,
    experience  BIGINT       NOT NULL DEFAULT 0,
    active      TINYINT      NOT NULL DEFAULT 0,
    acquired_at BIGINT       NOT NULL,
    updated_at  BIGINT       NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bp_pet_owner ON bp_pet (owner_id);
-- 소유자당 활성 펫은 1마리로 제한 (부분 인덱스, SQLite/PostgreSQL)
CREATE UNIQUE INDEX IF NOT EXISTS idx_bp_pet_active
    ON bp_pet (owner_id) WHERE active = 1;

CREATE TABLE IF NOT EXISTS bp_meta (
    key   VARCHAR(64) NOT NULL PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);  -- 스키마 버전 관리용
```

### 8.3 영속화 전략

- **인터페이스 우선**: `PetRepository`에 `findByOwner`, `save`, `delete`, `setActive`. 구현체는 SQLite/MySQL.
- **모든 DB I/O는 비동기.** 메인 스레드에서 JDBC 호출 금지 — 예외 없음.
- **write-behind 캐시**: 접속 시 로드 → 메모리에서 읽기/쓰기 → dirty 플래그 → (a) 5분 주기 (b) 퇴장 시 (c) 서버 종료 시 flush.
- **서버 종료 시 flush는 동기**로 수행한다. 비동기면 데이터를 잃는다.
- **스키마 마이그레이션**: `bp_meta.schema_version`으로 버전을 추적하고 순차 마이그레이션 적용.
- **MySQL 선택 시** HikariCP 사용, `pool-size` 설정 노출.

---

## 9. 성장 시스템

### 9.1 경험치 곡선

```
requiredExp(level) = base * level^exponent
기본값: base = 100, exponent = 1.5
→ Lv1→2: 100 / Lv10→11: 3,162 / Lv50→51: 35,355
```

곡선 파라미터는 설정 노출. `LevelCurve`는 순수 함수로 만들어 단위 테스트한다.

### 9.2 경험치 획득 경로

| 경로 | 기본값 | 조건 |
| --- | --- | --- |
| 몬스터 처치 | 몹별 설정값 | 소유자가 처치, 펫이 근처(32블록) |
| 블록 채굴 | 블록별 설정값 | 어뷰징 방지 — 설치 블록 제외(PDC 태깅) |
| 먹이 아이템 | 아이템별 설정값 | 우클릭 소비 |
| 시간 경과 | 5분당 N | 선택적, 기본 비활성 |

> **어뷰징 방지 필수**: 블록 채굴 경험치는 "플레이어가 설치한 블록"을 제외해야 한다. 설치 시 PDC/블록 메타로 태깅하거나 CoreProtect 연동. 이게 없으면 흙블록 무한 설치/파괴로 즉시 만렙이 된다.

### 9.3 레벨업 효과

- 능력 수치가 `base + perLevel * (level - 1)`로 증가, 여기에 등급 배율 곱연산
- 레벨업 시 파티클 + 사운드 + 액션바 알림
- `evolvesInto`가 설정된 레벨 도달 시 진화 안내 → GUI에서 확정 시 `type_id` 교체 + 모델 교체 + 레벨 1 리셋(설정 가능)

---

## 10. 능력 시스템

### 10.1 세 가지 유형

| 유형 | 발동 시점 | 구현 방식 | 예시 |
| --- | --- | --- | --- |
| `PASSIVE` | 소환 중 상시 | `AttributeModifier` 부착 | 이동속도 +10%, 최대체력 +4 |
| `TRIGGER` | 특정 이벤트 | 이벤트 리스너 + 확률 판정 | 피격 시 20% 확률 회복, 몹 처치 시 추가 드랍 |
| `ACTIVE` | 수동/쿨다운 | 쿨다운 맵 + 스킬 실행 | 30초마다 주변 몹 넉백 |

### 10.2 패시브 구현 — AttributeModifier

```java
private static final NamespacedKey SPEED_KEY =
        new NamespacedKey(plugin, "pet_speed");

void applySpeed(Player owner, double amount) {
    AttributeInstance attr = owner.getAttribute(Attribute.MOVEMENT_SPEED);
    attr.getModifiers().stream()
        .filter(m -> m.getKey().equals(SPEED_KEY))
        .forEach(attr::removeModifier);              // 중복 방지 — 먼저 제거
    attr.addModifier(new AttributeModifier(
            SPEED_KEY, amount, AttributeModifier.Operation.ADD_SCALAR));
}
```

> ⚠️ **모디파이어 누적은 흔한 치명적 버그다.** 재접속/펫 교체/서버 리로드 때마다 모디파이어가 쌓이면 플레이어가 로켓처럼 날아간다. **부착 전 항상 같은 키의 모디파이어를 제거**하고, 접속 시점에도 한 번 청소한다. 통합 테스트 항목에 넣는다.

### 10.3 확장 구조

```java
public interface PetAbility {
    AbilityType type();
    void onEquip(AbilityContext ctx);      // 펫 소환 시
    void onUnequip(AbilityContext ctx);    // 펫 해제 시 — 반드시 원복
    default void onTrigger(AbilityContext ctx, Event event) {}
}
```

- `AbilityRegistry`에 id → 팩토리 등록. 설정에서 id로 참조.
- 새 능력 추가 = 클래스 1개 + 등록 1줄. 다른 코드 수정 없음.
- **`onUnequip`은 `onEquip`의 정확한 역연산이어야 한다.** 이 계약을 어기면 상태가 샌다.

---

## 11. GUI 설계

| 화면 | 크기 | 내용 |
| --- | --- | --- |
| 펫 보관함 | 6줄 | 소유 펫 목록(페이지네이션), 등급별 테두리, 장착 중 표시 |
| 펫 상세 | 3줄 | 레벨/경험치 바, 능력 목록, [장착] [이름변경] [해방] |
| 진화 확인 | 3줄 | 이전/이후 비교, [확정] [취소] |
| 뽑기 | 3줄 | 비용 표시, [뽑기] — 결과 연출 |

구현 원칙:
- `InventoryHolder`를 구현한 커스텀 홀더로 GUI를 식별한다. **제목 문자열 비교 금지** (번역/색코드로 깨진다).
- `InventoryClickEvent`에서 GUI 인벤토리면 **무조건 `setCancelled(true)` 먼저** 호출한 뒤 로직 실행. 아이템 복제 취약점의 대부분이 여기서 발생한다.
- 이름 변경 입력은 anvil GUI 또는 채팅 입력. 채팅 입력 시 **타임아웃(30초)과 취소 경로**를 반드시 제공.
- 닫힘 처리에서 상태 정리.

---

## 12. 명령어 및 권한

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/pet` | `betterpets.use` | 보관함 GUI |
| `/pet summon <petId>` | `betterpets.use` | 소환 |
| `/pet dismiss` | `betterpets.use` | 해제 |
| `/pet rename <이름>` | `betterpets.use` | 이름 변경 |
| `/petadmin give <플레이어> <타입> [등급]` | `betterpets.admin` | 지급 |
| `/petadmin remove <플레이어> <petId>` | `betterpets.admin` | 회수 |
| `/petadmin exp <플레이어> <양>` | `betterpets.admin` | 경험치 지급 |
| `/petadmin reload` | `betterpets.admin` | 설정 리로드 |
| `/petadmin debug` | `betterpets.admin` | 활성 펫/트래커 수 진단 |

`/petadmin debug`는 **트래커 누수 탐지용**이다. 활성 펫 수, 살아있는 캐리어 엔티티 수, BetterModel 트래커 수를 출력해 세 숫자가 일치하는지 확인한다.

---

## 13. 설정 파일 구조

```
plugins/BetterPets/
├─ config.yml           일반 설정, DB, 성능 튜닝
├─ messages.yml         모든 사용자 노출 문자열 (한국어)
├─ gui.yml              GUI 레이아웃
└─ pets/
    ├─ wolf.yml
    └─ dragon.yml
```

**`pets/wolf.yml` 예시:**

```yaml
id: wolf_alpha
display-name: "&f알파 늑대"
model: pet_wolf              # BetterModel/models/ 의 모델명
rarity: RARE
max-level: 50
evolves-into: wolf_fenrir    # 없으면 생략

animations:
  idle: idle
  walk: walk
  run: run
  attack: attack

movement:
  follow-distance: 2.0
  walk-speed: 0.25
  run-speed: 0.45
  teleport-distance: 24.0

abilities:
  - id: attribute_speed
    type: PASSIVE
    base: 0.05
    per-level: 0.002
  - id: on_kill_extra_drop
    type: TRIGGER
    chance-base: 0.05
    chance-per-level: 0.003

experience:
  from-kill:
    ZOMBIE: 10
    SKELETON: 12
  from-mining:
    DIAMOND_ORE: 50
```

**설정 검증**: 로드 시 필수 필드 누락, 존재하지 않는 모델 참조, 미등록 능력 id를 **모두 수집해 한 번에 보고**한다. 첫 오류에서 멈추지 않는다 — 관리자가 반복 재시작하게 만들지 않기 위해서다.

---

## 14. 외부 연동

| 대상 | 필수 | 용도 |
| --- | --- | --- |
| **BetterModel** | ✅ 필수 | 모델 렌더링. `depend: [BetterModel]` |
| Vault | 선택 | 뽑기 비용 결제 |
| PlaceholderAPI | 선택 | `%betterpets_active_name%`, `%betterpets_active_level%`, `%betterpets_count%` |
| MythicMobs | 선택 | 몹 처치 경험치 연동 |

선택 연동은 **전부 soft-depend**로 두고, 클래스 존재 여부를 확인한 뒤 어댑터를 등록한다. 없어도 플러그인이 정상 기동해야 한다.

```yaml
# plugin.yml
name: BetterPets
main: kr.qmftm.betterpets.BetterPetsPlugin
api-version: '1.21'
depend: [BetterModel]
softdepend: [Vault, PlaceholderAPI, MythicMobs]
```

---

## 15. 성능 및 확장성

### 15.1 목표

| 지표 | 목표 |
| --- | --- |
| 동시 활성 펫 100마리 기준 메인 스레드 부하 | < 1ms/tick |
| 펫 1마리당 평균 패킷 | BetterModel 기본 오버헤드 + α (α ≈ 0) |
| 접속 시 펫 데이터 로드 | < 50ms (비동기) |
| 메모리 (펫 100마리) | < 20MB |

### 15.2 최적화 원칙

1. **전역 틱 루프 1개.** 펫당 태스크 금지.
2. **애니메이션은 상태 전이 시에만 호출.** (7.4)
3. **거리 기반 LOD** — 소유자 주변에 아무 관전자도 없으면 이동 갱신 주기를 2틱 → 10틱으로 늘린다.
4. **오프라인 소유자 펫 즉시 디스폰.**
5. **DB I/O 전량 비동기 + write-behind 캐시.**
6. `viewFilter`로 원거리 플레이어에게 패킷 미전송 (BetterModel이 기본 처리하는지 M1에서 확인).

### 15.3 측정

M7에서 Spark 프로파일러로 실측한다. **목표치는 추정이며, 측정 전까지 달성 여부를 주장하지 않는다.**

---

## 16. 테스트 전략

| 레벨 | 대상 | 도구 |
| --- | --- | --- |
| 단위 | `LevelCurve`, `Rarity` 배율, 능력 수치 계산, 설정 파서 | JUnit 5 |
| 단위 | 도메인 로직 (Bukkit 비의존) | JUnit 5 |
| 통합 | Repository CRUD | JUnit + 인메모리 SQLite |
| 통합 | 이벤트 리스너, GUI | MockBukkit |
| 수동 | 렌더링·애니메이션·이동 | Paper 테스트 서버 |

**수동 테스트 체크리스트 (매 마일스톤 반복):**

- [ ] 소환 → 로그아웃 → 재접속 시 유령 모델이 남지 않는가
- [ ] 소환 상태로 서버 재시작 시 캐리어 엔티티가 청소되는가
- [ ] 월드 이동 / 네더 포탈 통과 시 펫이 따라오는가
- [ ] 펫 교체를 20회 반복해도 AttributeModifier가 누적되지 않는가
- [ ] `/petadmin debug`의 세 숫자(활성 펫 / 캐리어 / 트래커)가 일치하는가
- [ ] 펫 100마리 소환 상태에서 TPS 20 유지되는가
- [ ] BetterModel을 제거한 상태에서 플러그인이 안전하게 비활성화되는가

---

## 17. 개발 로드맵

| M | 마일스톤 | 산출물 | 예상 |
| --- | --- | --- | --- |
| **M0** | 프로젝트 뼈대 | Gradle 빌드, plugin.yml, CI, 패키지 구조, BetterModel 의존성 해결 확인 | 3일 |
| **M1** | 렌더링 수직 슬라이스 | `/pet summon`으로 모델 1개 소환/해제. `BetterModelBridge` 완성. **트래커 누수 0 검증** | 1.5주 |
| **M2** | 이동 + 애니메이션 | 추종 상태 머신, 텔레포트 폴백, 애니메이션 상태 머신. 이동 방식 A/B 실측 후 확정 | 2주 |
| **M3** | 데이터 + 명령어 | `PetRepository`(SQLite), 캐시, 전체 명령어/권한 | 1.5주 |
| **M4** | GUI | 보관함, 상세, 장착/해제/이름변경/해방 | 1.5주 |
| **M5** | 성장 + 능력 | 경험치/레벨, 3종 능력 시스템, 능력 3~5개 구현 | 2주 |
| **M6** | 획득 + 진화 | 알 아이템, 뽑기, 진화 | 1.5주 |
| **M7** | 최적화 + 폴리시 | Spark 프로파일링, Folia 대응, PlaceholderAPI, 문서화 | 1.5주 |

**합계: 약 11.5주** (1인 파트타임 기준, 모델 에셋 제작 시간 제외)

**병렬 트랙**: 6장의 모델 에셋 제작은 M1과 동시에 시작해야 M2 진입 시 테스트 모델이 준비된다. 이게 밀리면 전체 일정이 밀린다.

### 마일스톤별 완료 조건 (DoD)

각 마일스톤은 아래를 모두 만족해야 완료로 친다:

1. 기능이 테스트 서버에서 동작함
2. 해당 범위의 단위 테스트 통과
3. 16장 수동 체크리스트 통과
4. `docs/`에 변경사항 반영
5. 브랜치에 커밋·푸시 완료

---

## 18. 리스크 관리

| # | 리스크 | 영향 | 확률 | 대응 |
| --- | --- | --- | --- | --- |
| R1 | **Java 25 요구가 운영 환경과 충돌** | 높음 | 중 | Q1 즉시 확인. 불가 시 BetterModel 2.2.0(Java 21)으로 하향, API 차이 흡수 |
| R2 | **트래커 누수로 유령 모델/TPS 저하** | 높음 | 중 | `AutoCloseable` + 4경로 close 보장 + `/petadmin debug` + M1 DoD에 포함 |
| R3 | **BetterModel 버전 업 시 API 파괴** | 중 | 높음 | `BetterModelBridge` 단일 격리 계층 (4.3). 버전 핀 고정 |
| R4 | **모델 에셋 저작권 문제** | 높음 | 중 | 원본 에셋 복제 금지 원칙 명문화. 자체 제작 또는 라이선스 확인된 것만 사용 |
| R5 | **AttributeModifier 누적 버그** | 중 | 높음 | 부착 전 항상 제거 + 접속 시 청소 + 반복 테스트 항목화 |
| R6 | **채굴 경험치 어뷰징** | 중 | 높음 | 설치 블록 태깅 (9.2) |
| R7 | **모델 에셋 지연으로 M2 블로킹** | 중 | 중 | M1과 병렬 착수. 임시 대체 모델 확보 |
| R8 | **Folia 환경 스케줄러 차이** | 중 | 낮음 | `PetTicker`에 분기 캡슐화. M7에서 검증 |
| R9 | **GUI 아이템 복제 취약점** | 높음 | 낮음 | `setCancelled(true)` 선행 원칙 + 코드 리뷰 체크 |

---

## 19. 다음 단계

1. **3장 확인 필요 사항 Q1~Q6에 답변** — 특히 **Q1(JDK 버전)** 은 M0 착수 전 필수
2. 답변 반영해 계획서 v1.1 확정
3. M0 착수: Gradle 프로젝트 초기화 + BetterModel 의존성 해결 확인
4. 병행: 6장 모델 에셋 트랙 착수

---

## 부록 A. Gradle 빌드 설정 (M0 착수용)

```kotlin
plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
}

group = "kr.qmftm"
version = "0.1.0"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.blamejared.com/")
    maven("https://maven.nucleoid.xyz/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.4.1")

    implementation("org.xerial:sqlite-jdbc:3.50.1.0")
    implementation("com.zaxxer:HikariCP:6.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.0")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:4.75.0")
}

tasks.test { useJUnitPlatform() }
```

> 라이브러리 버전은 M0 착수 시점에 최신 안정판으로 재확인한다. `blamejared`/`nucleoid` 저장소는 BetterModel 문서가 명시한 전이 의존성용이다.

---

## 부록 B. 참고 자료

- [BetterModel — GitHub](https://github.com/toxicity188/BetterModel)
- [BetterModel — API example (Wiki)](https://github.com/toxicity188/BetterModel/wiki/API-example)
- [BetterModel — Configuring bone tag](https://github.com/toxicity188/BetterModel/wiki/Configuring-bone-tag)
- [BetterModel — Compare with ModelEngine](https://github.com/toxicity188/BetterModel/wiki/Compare-with-ModelEngine)
- [BetterModel — Hangar (PaperMC)](https://hangar.papermc.io/toxicity188/BetterModel)
- [BetterModel — Modrinth](https://modrinth.com/plugin/bettermodel)
- [BetterModel — 문서 사이트](https://mintlify.wiki/toxicity188/BetterModel/introduction)
- [BetterModel — DeepWiki (아키텍처 개요)](https://deepwiki.com/toxicity188/BetterModel)

---

## 부록 C. 이 문서의 한계

- **5.1의 API 시그니처는 공개 문서 기준**이며, 실제 3.4.1 JAR로 컴파일 검증하지 않았다. M0에서 실제 의존성을 걸고 검증한 뒤 이 문서를 갱신한다.
- **6장의 본 태그(bone tag) 규칙은 미확정**이다. BetterModel 위키의 해당 페이지 본문을 읽지 못했다. 히트박스·이름표·좌석 본 명명 규칙은 M1에서 확인 후 문서화한다.
- **3장 Q6의 "악어의 놀이터 2 스타일"은 추정**이다. 원 서버의 실제 펫 사양을 확인하지 못해 장르 일반형으로 설계했다.
- **15.1의 성능 목표치는 추정**이며 측정된 값이 아니다.
</content>
