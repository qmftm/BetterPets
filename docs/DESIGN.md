# 설계 문서

BetterPets의 아키텍처, 검증된 API, 조사 근거, 리스크. 사용 안내는 [README](../README.md) 참고.

- [설계 전제 — 서버 규모](#설계-전제--서버-규모)
- [아키텍처](#아키텍처)
- [BetterModel API — 검증된 사양](#bettermodel-api--검증된-사양)
- [원작 시스템 분석](#원작-시스템-분석)
- [참고 구현 분석](#참고-구현-분석)
- [Bedrock 지원 — Geyser 연동](#bedrock-지원--geyser-연동)
- [펫 생애주기](#펫-생애주기)
- [행동 설계](#행동-설계)
- [탑승과 비행](#탑승과-비행)
- [데이터와 영속화](#데이터와-영속화)
- [등급과 능력](#등급과-능력)
- [획득 경로](#획득-경로)
- [보유·소환 한도](#보유소환-한도)
- [다국어](#다국어)
- [전체 알림](#전체-알림)
- [선택적 연동 — DiscordSRV · Geyser](#선택적-연동--discordsrv--geyser)
- [설정 파일](#설정-파일)
- [성능](#성능)
- [테스트](#테스트)
- [리스크](#리스크)
- [미해결 질문](#미해결-질문)

---

## 설계 전제 — 서버 규모

**동시 접속 최대 5명.** 활성 펫은 많아야 5마리다. 이 전제가 설계 전반을 좌우한다.

초기 계획은 100마리를 기준으로 잡았는데 20배 과잉이었다. 다음을 걷어냈다:

| 항목 | 판단 |
| --- | --- |
| 거리 기반 LOD | ❌ 구현하지 않는다 |
| `viewRange` 패킷 튜닝 | ❌ 기본값으로 둔다 |
| MySQL 구현체 | ❌ 인터페이스만 두고 만들지 않는다 |
| 커넥션 풀(HikariCP) | ❌ 단일 커넥션 + 단일 스레드 실행자로 충분 |
| write-behind 배치 flush | ❌ 변경 시 즉시 비동기 저장 |
| 마일스톤마다 부하 테스트 | ❌ M9에서 1회 |

**그래도 지키는 것** — 규모와 무관하게 옳고, 오히려 구현이 더 단순해지는 것들:

1. **전역 틱 루프 1개.** 펫당 태스크를 만들지 않는다
2. **애니메이션은 상태 전이 시에만 호출.** 매 틱 `animate()` 는 규모와 무관한 버그다
3. **DB I/O는 메인 스레드 밖에서.** 5명이어도 블로킹은 버그다
4. **성장도 지연 계산.** 주기적 일괄 UPDATE보다 구현이 간단하다
5. **트래커 누수 방지.** 유령 모델은 1마리만 남아도 버그다

> 요컨대 **최적화는 빼고, 올바른 구조는 남긴다.** 원작이 렉으로 곤란을 겪은 건 사실이지만 그건 수십~수백 명 규모의 이야기다. 5명 서버에서 LOD를 만드는 건 쓰지도 않을 복잡도를 떠안는 것이다.

---

## 아키텍처

의존 방향은 항상 안쪽(Domain)을 향한다. **Domain은 Bukkit API를 모른다.**

```
Presentation   /pet · /petadmin · PetBoxMenu · listener/*
       ↓
Application    PetService · GrowthService · RideService · AbilityService
       ↓
Domain         Rarity · LifeStage · GrowthCurve · PetType · PetData · PetLimits
       ↓
Runtime        ActivePet · PetRegistry · PetTicker
               MovementController (GROUND / FLY / RIDDEN)
               AnimationStateMachine
       ↓
Infrastructure BetterModelRenderer ★ · PetRepository · ConfigLoader
```

### ★ 렌더 격리 계층

**BetterModel API 호출은 `render/BetterModelRenderer.java` 한 파일 안에서만 일어난다.** 다른 곳에 `kr.toxicity.model.api` import가 생기면 격리가 깨진 것이다.

이유: BetterModel은 2.x → 3.x에서 아티팩트 이름 자체가 바뀌었고(`bettermodel` → `bettermodel-bukkit-api`), 멀티플랫폼화로 `BukkitAdapter.adapt()` 경유가 추가됐다. 앞으로도 깨질 수 있다. 브릿지 하나만 고치면 업그레이드가 끝나고, 테스트에서는 가짜 구현으로 교체할 수 있다.

---

## BetterModel API — 검증된 사양

> `bettermodel-bukkit-api:3.4.1` **소스 JAR을 직접 읽고**, JDK 25로 **실제 컴파일까지 확인한** 내용이다. 추정이 아니다.

패키지 루트는 `kr.toxicity.model.api`.

### 진입점

```java
Optional<ModelRenderer>         BetterModel.model(String name);
Optional<ModelRenderer>         BetterModel.limb(String name);
Optional<EntityTrackerRegistry> BetterModel.registry(PlatformEntity entity);
Set<String>                     BetterModel.modelKeys();     // 설정 검증용
BetterModelEventBus             BetterModel.eventBus();
BetterModelPlatform             BetterModel.platform();      // dataFolder(), reload()

// kr.toxicity.model.api.bukkit.platform.BukkitAdapter
PlatformEntity   BukkitAdapter.adapt(org.bukkit.entity.Entity entity);
PlatformPlayer   BukkitAdapter.adapt(org.bukkit.entity.Player player);
PlatformLocation BukkitAdapter.adapt(org.bukkit.Location location);
```

### 트래커 생성

```java
EntityTracker getOrCreate(PlatformEntity entity);
EntityTracker getOrCreate(PlatformEntity entity, TrackerModifier m);
EntityTracker create(PlatformEntity entity, TrackerModifier m, Consumer<EntityTracker> preUpdate);
DummyTracker  create(PlatformLocation location);
```

### 애니메이션

`Tracker`(EntityTracker의 상위 클래스)가 제공한다. **`animate` 는 `boolean` 을 반환한다** — 모델에 해당 애니메이션이 없으면 false다. 무시하지 말 것.

```java
boolean animate(String animation);
boolean animate(String animation, AnimationModifier modifier);
boolean animate(String animation, AnimationModifier modifier, Runnable onRemove);
boolean stopAnimation(String animation);
boolean replace(String target, String animation, AnimationModifier modifier);
```

```java
AnimationModifier.DEFAULT
AnimationModifier.DEFAULT_WITH_PLAY_ONCE

AnimationModifier.builder()
    .type(AnimationIterator.Type.LOOP)   // PLAY_ONCE | LOOP | HOLD_ON_LAST — 3종뿐
    .priority(10)                        // 오버레이 레이어에 사용
    .speed(1.0f)
    .player(platformPlayer)              // 특정 플레이어에게만
    .build();
```

### 표시 속성

```java
TrackerUpdateAction.tint(int rgb)
TrackerUpdateAction.glow(boolean) / glowColor(int)
TrackerUpdateAction.brightness(int block, int sky)
TrackerUpdateAction.viewRange(float)
TrackerUpdateAction.enchant(boolean)
TrackerUpdateAction.billboard(PlatformBillboard)
TrackerUpdateAction.composite(TrackerUpdateAction...)
```

### 가시성과 생명주기

```java
boolean hide(PlatformPlayer player);    // 특정 플레이어에게만
boolean show(PlatformPlayer player);
void    despawn();
void    close();                        // Tracker implements AutoCloseable
boolean isClosed();

// EntityTrackerRegistry
EntityTracker tracker(String key);
Collection<EntityTracker> trackers();
boolean close();
static List<EntityTrackerRegistry> registries();   // ★ 전역 진단 — 누수 탐지
```

> ⚠️ **트래커 누수가 1순위 구현 위험이다.** 닫지 않으면 유령 모델과 고아 스케줄 태스크가 남는다. `PetRenderHandle` 이 `AutoCloseable` 을 구현하고, **소유자 퇴장 · 서버 종료 · 월드 언로드 · 캐리어 사망 네 경로 모두**에서 close를 보장한다. `registries()` 로 엔진이 실제로 들고 있는 트래커 수를 세어 `/petadmin debug` 에 노출한다.

### 탑승 관련 (사용하지 않기로 함)

```java
Map<UUID, MountedHitBox> mountedHitBox();
boolean hasControllingPassenger();
```

BetterModel이 좌석 마운트를 네이티브로 지원하지만, [참고 구현](#참고-구현-분석)이 이 경로를 쓰지 않고 `ArmorStand` 를 직접 구동한다. 우리도 그쪽을 기본안으로 삼는다. 자세한 건 [탑승과 비행](#탑승과-비행).

---

## 원작 시스템 분석

> 나무위키 문서는 직접 접근이 차단(403)되어 **검색 결과 요약을 통해** 확인했다. 여러 검색에서 일관되게 나온 내용만 정리했으나 **원문 대조는 하지 못했다.**

### 시즌 1

| 항목 | 내용 |
| --- | --- |
| 등급 | **D ~ S** |
| 탑승 | **가능** — 펫을 탈것으로 사용 |
| 이동속도 | **등급이 오를수록 빨라짐** |
| 비행 | **A등급부터 드물게** (통칭 "날탈") |
| 비행 특성 | 겉날개+폭죽보다 느리지만 재화 소모 없이 **무한 비행** |
| 획득 | **펫 뽑기권** 가챠 — 놀이터 코인 5개 또는 10만 골드 |

### 시즌 2 (변경점)

| 항목 | 내용 |
| --- | --- |
| 획득 | **알 상태로 제공.** 동물조련사 NPC에게 구매 |
| 부화 | 우유 같은 **펫 전용 음식**을 먹여 부화 |
| 성장도 | **1분에 1씩 자동 상승** |
| 먹이 | 우유 사용 시 **한 번에 +10** |
| 기믹 | 아기 펫에 **먹이를 한꺼번에 많이 주면 탈것 '돼지'로 진화** |

### 성능에 대한 교훈

> 서버 오픈 **6일차에 운영진이 렉을 이유로 펫 소환 자제를 요청**했고, 여러 방송인이 방송 중 불만을 표시했다.

펫 시스템의 실패 지점이 기능 부족이 아니라 성능이었다는 건 기억해둘 만하다. 다만 원작은 방송인 수십 명이 붙은 대규모 서버였고 **우리는 최대 5명**이다. 구조적으로 옳은 것만 지키고, 규모 대응 최적화는 만들지 않는다. ([설계 전제](#설계-전제--서버-규모))

### 원작 대비 차이

| 항목 | 원작 | 본 설계 | 이유 |
| --- | --- | --- | --- |
| 등급 | D~S | D~S | 원작 준수 |
| 성장 | 성장도 (시간+먹이) | 동일 | 원작 준수 |
| 탑승·비행 | 핵심 기능 | 핵심 기능 | 원작 준수 |
| 획득 | NPC 알 구매(S2) + 뽑기권(S1) | **알 아이템 우클릭으로 통합** | 5명 서버에 NPC는 과잉. 랜덤 알로 뽑기 감각 유지 |
| 부화 | 알에 먹이를 먹여 부화 | **알 아이템이 아기를 바로 준다** | 소환도 못 하는 대기 상태를 하나 줄인다. 키우는 재미는 아기→성체 구간이 담당한다 |
| 능력 | 이동속도 위주 | 패시브/트리거 확장 | 원작보다 확장. 끄면 원작과 동일 |

---

## 참고 구현 분석

[`yourShika/betterpets-paper`](https://github.com/yourShika/betterpets-paper) (MIT, v1.26.1)는 **이미 BetterModel 연동과 비행 탑승을 구현해 배포 중인** Paper 플러그인이다. Paper 26.2 · Maven · BetterModel · 탑승 펫으로 목표가 크게 겹친다.

> MIT이므로 저작권 표시를 유지하면 참고·차용할 수 있다. 다만 이 프로젝트는 스스로 "AI의 도움으로 만들어졌다"고 밝히고 있으니 검증 없이 옮기지 않는다.

### 우리 설계가 확인된 것

| 우리 결정 | 그쪽 구현 |
| --- | --- |
| Maven 빌드 | ✅ 동일 |
| BetterModel API 격리 계층 | ✅ 동일 — `PetModelBridge` ← `BetterModelHook` |
| 핸들이 `AutoCloseable` | ✅ 동일 |
| `getOrCreate` + `TrackerModifier.DEFAULT` | ✅ 동일 |

### 그대로 가져올 기법 — 우리가 겪었을 버그들

이들이 릴리스 노트에서 고쳤다고 밝힌 것들이 곧 우리가 만날 버그다.

| 문제 | 해법 |
| --- | --- |
| 빠른 마운트가 얇은 벽을 **관통** | 이동 경로를 **0.45블록 이하 서브스텝으로 분할 검사**. 하나라도 막히면 전체 취소 |
| 벽에 닿으면 이동이 완전히 멈춤 | **벽 슬라이딩** — 전체 → 수평만 → 수직만 순으로 시도 |
| 어깨/머리가 벽에 **끼임** | 중심 + 반경 0.35의 4방향 점을 **발치와 머리 높이 양쪽에서** 검사 |
| 하차 시 **낙하 피해** | `setFallDistance(0)` + `SLOW_FALLING` 100틱 |
| 비행 중 히트박스가 **블록 파괴 레이캐스트를 가로챔** | 비행 중 0.1×0.1로 축소, 하차 시 복원 |
| 펫이 채굴·건축을 방해 | 우클릭 동작이 없는 펫은 히트박스를 아예 작게 |
| 실수로 이륙 | **두 번째 우클릭 확인**을 요구 |
| 비행 중 우클릭하면 하차돼버림 | 비행 중 하차는 **스니크 전용** |
| 고도 제한 | 빌드 높이가 아니라 `flight-max-height`(기본 1024)로 별도 상한 |
| 펫 본체가 마운트를 못 따라감 | `setTeleportDuration(1)` — 탑승 중 1틱 보간, 평상시 8틱 |

### 반면교사 — 구조

전체 12,816줄 중 **`BetterPetsPlugin.java` 5,034줄, `ActivePetManager.java` 3,792줄**. 두 파일이 코드의 69%인 전형적인 신 클래스다.

우리의 계층 분리는 이걸 피하기 위한 것이다. **한 파일이 800줄을 넘으면 분리 신호로 본다.**

### 엔티티 구성 차이

그쪽은 펫 1마리당 `ItemDisplay`(본체) + `Interaction`(클릭) + `TextDisplay`(이름표) 3개를 쓰고, 탑승 시 `ArmorStand` 가 더 붙는다. BetterModel이 **선택 사항**이라 없을 때 플레이어 머리로 대체 렌더링해야 하기 때문이다.

우리는 BetterModel에 **하드 의존**하므로 본 태그로 히트박스(`b_`)와 이름표(`tag_`)를 얻어 엔티티를 줄일 수 있다. 다만 **BetterModel이 `ItemDisplay` 에도 정상적으로 붙는 것**이 확인됐으므로, 캐리어를 `Mob` 으로 할지 `ItemDisplay` 로 할지는 **M1에서 실측해 정한다.**

---

## Bedrock 지원 — Geyser 연동

**이 서버는 Bedrock 플레이어를 받는다.** 그런데 Bedrock 클라이언트는 BetterModel이 쓰는 item-display 기반 커스텀 모델을 **볼 수 없다.** 자바 플레이어에게 드래곤이 보이는 자리에 Bedrock 플레이어에게는 아무것도 안 보인다.

### GeyserModelEngine

[`GeyserExtensionists/GeyserModelEngine`](https://github.com/GeyserExtensionists/GeyserModelEngine)이 이 간극을 메운다. **BetterModel을 공식 지원한다.**

소스에서 확인한 것 (최신 커밋 2026-08-27, 활발히 관리됨):

- README: *"This plugin converts ModelEngine/BetterModel models for bedrock players!"*
- 전용 클래스가 실재한다 — `BetterModelHandler`, `BetterModelListener`, `BetterModelTaskHandler`, `BetterModelPropertyHandler`, `BetterModelModel`, `BetterModelEntityData`
- 런타임에 엔진을 감지한다:

```java
} else if (Bukkit.getPluginManager().getPlugin("BetterModel") != null) {
    this.modelHandler = new BetterModelHandler(plugin);
    plugin.getLogger().info("Using BetterModel handler!");
}
```

- `paper-plugin.yml` 에서 ModelEngine과 BetterModel 둘 다 `required: false` 로 선언하고 있는 쪽을 골라 쓴다

### 버전 호환성 — 검증한 것과 못 한 것

GeyserModelEngine은 `bettermodel-bukkit-api:2.2.0` 에 대해 빌드된다. 우리는 **3.4.1** 이다.

**확인함** — 이들이 import하는 BetterModel 클래스 15개가 3.4.1에 **전부 존재한다**:

```
AnimationIterator · RenderedBone · BetterModelBukkit · BukkitAdapter · BukkitLocation
BlueprintAnimation · RenderPipeline · BaseEntity · CreateEntityTrackerEvent · ModelDisplay
PlatformAdapter · EntityTracker · ModelScaler · Tracker · BonePredicate
```

**확인 못 함** — 메서드 시그니처까지는 대조하지 않았다. 클래스가 있어도 메서드가 바뀌었으면 `NoSuchMethodError` 가 난다. 실제로 붙여봐야 안다.

**또 하나** — GeyserModelEngine의 `api-version` 은 `1.21` 이고 우리는 `26.2` 다. Paper가 하위 api-version을 허용하지만 26.2에서 검증됐는지는 불확실하다.

Java 타깃은 21이라 Java 25 JVM에서 도는 데는 문제가 없다.

### 검증 시점 — 일찍 해야 한다

> **M1에서 첫 모델이 자바에 뜨자마자 그 하나로 Bedrock 변환을 시험한다.**

이유는 단순하다. 모델을 여러 개 만든 뒤에 변환이 안 되는 걸 알면 텍스처부터 다시 작업해야 한다. 조합(BetterModel 3.4.1 + GME + MC 26.2)이 검증된 적 없으므로, **되돌리기 싼 시점에 확인한다.**

되면 M10에서 나머지 모델을 처리한다. 안 되면 대안을 찾는다 — GME에 이슈를 올리거나, BetterModel 버전을 2.2.0으로 내리거나(Java 21로 내려갈 수 있어 오히려 제약이 풀린다), Bedrock 플레이어에게는 대체 표현을 주거나.

### 운영 부담

| 항목 | 내용 |
| --- | --- |
| 플러그인 스택 | 4개 추가 (GME, geyserutils-spigot, packetevents + Geyser 확장 2개) |
| 모델 작업 | **두 번 내보내기.** Java용 `.bbmodel` + Bedrock용 packer 내보내기 |
| 텍스처 제약 | 멀티 텍스처·애니메이션 텍스처는 packer 플러그인 필수 |
| 함정 | 리소스팩은 모델 **개수**가 바뀔 때만 재생성. 수정만 하면 반영 안 됨 |

모델 제작 지침은 [MODELING.md](MODELING.md#bedrockgeyser-대응)에 정리했다.

---

## 펫 생애주기

```
  알 아이템 우클릭        성장도 100        (조건)
  ┌───────┐            ┌───────┐      ┌────────┐
  │ 알 아이템 │───────────▶│ BABY  │─────▶│ ADULT  │
  └───────┘            └───┬───┘      └────────┘
                           │ 과급식        탑승 가능
                           ▼              비행 가능(A+)
                        ┌───────┐         능력 발현
                        │  PIG  │
                        └───────┘
```

**알은 생애주기 상태가 아니다.** 알 아이템은 그 안에 담긴 펫을 곧바로 꺼내주는 아이템이고, 보관함에 "알" 상태로 남지 않는다. 원작은 알을 사서 먹여 부화시켰지만, 5명 서버에서 그 한 단계는 소환도 못 하는 대기 시간만 늘린다 — 우클릭한 순간 데리고 다닐 수 있는 아기를 준다.

| 상태 | 진입 조건 | 탑승 | 능력 |
| --- | --- | :---: | :---: |
| `BABY` | 알 아이템 우클릭 / `/petadmin give` | ❌ | ❌ |
| `ADULT` | 최대 단계에서 성장도 상한 도달 | ✅ | ✅ |
| `PIG` | 아기 상태에서 단시간 과급식 | ✅ | ❌ |

`PIG` 로 갈 때는 **상태뿐 아니라 종류(`typeId`)도 바꾼다.** 상태만 바꾸면 "돼지가 됐다"는 메시지와 화면이 어긋난다 — 겉모습은 여전히 드래곤이다. 어떤 펫으로 바뀔지는 `config.yml` 의 `gimmick.overfeed.becomes` 가 정하고(기본 `pig`), 기본 제공 `pets/pig.yml` 은 걷는 탑승만 되는 능력 없는 펫이다. 그 종류가 없으면 상태만 바꾸고 기동 시 경고한다.

> ⚠️ **성장한 뒤에는 화면과 능력을 다시 맞춰야 한다.** 성장은 **재소환 없이** 일어나는데, 모델과 능력은 소환 시점에 붙는다. 빠뜨리면 두 가지가 어긋난다 — `ActivePet` 이 소환 시점의 `PetType` 을 붙들고 있어 진화해도 예전 모델과 예전 애니메이션 이름을 계속 쓰고, `equip` 이 `summon` 에서만 불려서 **아기로 꺼내둔 펫이 성체가 돼도 능력이 붙지 않는다.** 후자는 "펫을 데리고 다니며 키운다"는 가장 자연스러운 경로에서 등급별 능력이 통째로 조용히 안 도는 상태였다. 급여 · 시간 경과 · `/petadmin growth` 세 경로 모두 `PetService.refreshAfterGrowth` 로 마무리한다.
>
> 능력은 떼었다 다시 붙인다. `unequip` 은 생애주기와 무관하게 돌고 `equip` 은 성체에게만 붙으므로, 이 한 쌍이 **어느 방향의 변화든** 맞춘다 — 아기→성체는 붙고, 성체→돼지는 떨어진다.

### 성장도

| 경로 | 증가량 |
| --- | --- |
| 시간 경과 | 1분당 +1 |
| 먹이(우유) | +10 |
| 관리자 지급 | 임의 |

**성장도는 `updated_at` 기준으로 지연 계산한다.** 1분마다 전체 펫을 순회하며 +1 하는 대신, 읽을 때 경과 시간을 환산하고 저장 시점에만 반영한다. 주기 작업이 없어져 구현도 더 단순하다.

> ⚠️ **함정** — 계산 후 기준 시각을 무조건 `now` 로 밀면 1분에 못 미친 나머지 시간이 매번 버려진다. 저장이 잦으면 펫이 영원히 자라지 않는다. `GrowthCurve.project()` 가 새 성장도와 **새 기준 시각을 함께** 돌려주는 이유다. 이 경우는 테스트로 고정돼 있다.

### 성장 단계 — 성장도가 차도 곧바로 성체가 되지 않을 수 있다

원작에는 없던 확장이다. 성장도가 상한(`growth-max`)에 닿았을 때, **설정된 최대 단계(`growth.max-stage`, 전역)** 에 아직 못 미쳤으면 곧바로 성체가 되는 대신 **다음 성장 단계로 넘어간다**:

1. 펫 종류의 `next-stage` 가중치 맵으로 다음 형태를 추첨한다 — 알의 랜덤 뽑기와 같은 알고리즘(`Weighted.pick`)이다. 맵이 비어 있으면 종류는 그대로 두고 단계 수만 오른다
2. `growthStage` 를 1 올린다
3. 성장도를 0으로 되돌리고 다시 채운다 (기준 시각도 함께 갱신 — `applyGrowth` 계약)

이 과정을 최대 단계에 이를 때까지 반복한 뒤에야 `ADULT` 로 전이하고, **그 뒤로는 더 자라지 않는다.** `max-stage` 기본값은 **1**이다 — 성장도가 차면 바로 성체가 되는 예전 동작과 정확히 같다. 서버가 다단계 확률적 진화를 원하면 이 값을 올리고 `pets/*.yml` 에 `next-stage` 를 채운다.

```yaml
# pets/wolf.yml
next-stage:
  wolf: 70       # 70% 확률로 그대로 늑대 유지
  dragon: 30     # 30% 확률로 드래곤으로 (같은 종류가 아니어도 된다)
```

> ⚠️ **가중치 맵의 반복 순서가 결과에 영향을 준다** (확률 자체는 아니다 — 어느 항목이 어느 수치 구간을 차지하는지가 순서에 좌우될 뿐). `Map.copyOf` 는 JVM 마다 순서를 랜덤하게 흩기 때문에 `EggDefinition.weights` 와 `PetType.nextStage` 는 둘 다 **순서를 보존하는 맵**(`LinkedHashMap` 래핑)으로 저장한다. 이걸 놓쳤다가 실제로 테스트가 간헐적으로 깨졌다 — `WeightedTest` 에 재현 테스트로 남겨뒀다.

---

## 행동 설계

### 이동 모드

| 모드 | 조건 | 담당 |
| --- | --- | --- |
| `GROUND` | 기본 | 지상 추종 |
| `FLY` | 비행 펫이 공중에 있을 때 | 고도 유지 + 직선 이동 |
| `RIDDEN` | 플레이어 탑승 중 | 탑승자 입력에 따름 |

### 지상 추종 상태 머신

| 상태 | 조건 | 애니메이션 |
| --- | --- | --- |
| `IDLE` | 거리 < 2.5 | `idle` (LOOP) |
| `WALK` | 2.5 ≤ 거리 < 8 | `walk` (LOOP) |
| `RUN` | 8 ≤ 거리 < 24 | `run` (LOOP) |
| `TELEPORT` | 거리 ≥ 24 · 다른 월드 · 3초 이상 경로 실패 | — |

목표 지점은 소유자 위치에서 시야 반대 방향으로 `follow-distance`(기본 2.0) 떨어진 곳. 소유자가 제자리 회전할 때 펫이 따라 도는 것을 막기 위해 **데드존 0.8블록**을 둔다.

`setAI(false)` 이므로 바닐라 경로탐색을 쓰지 않는다. 방향 벡터 → 속도 상한 → 간이 지형 처리(앞이 막히고 위가 비었으면 `y += 0.5`, 아래가 비었으면 최대 3블록 하강) → 위치 갱신. 물·용암·공허 감지 시 즉시 `TELEPORT`.

### 틱 루프

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

### 애니메이션 상태 머신

```java
void tick() {
    AnimationState next = resolve(movement.mode(), movement.state());
    if (next == current) return;   // ★ 변화 없으면 아무것도 하지 않는다
    handle.stop(current.animationName());
    handle.play(next.animationName(), true);
    current = next;
}
```

일회성 애니메이션(`attack`, `eat`)은 `priority` 를 올린 `PLAY_ONCE` 로 **오버레이**하고, 완료 콜백에서 루프 상태를 복원한다.

---

## 탑승과 비행

원작의 핵심 기능이다. [참고 구현](#참고-구현-분석)에서 검증된 구조를 기본안으로 삼는다.

### 탑승 방식 — 펫 종류마다 3가지

`pets/*.yml` 의 `ride:` 값이다 (`RideMode`).

| 값 | 뜻 |
| --- | --- |
| `NONE` | 탈 수 없다 |
| `GROUND` | 걷는 탑승. 지형을 따라 달린다 |
| `FLY` | 나는 탑승. 개체별 추첨에 성공해야 실제로 난다 |

개체가 실제로 무엇을 할 수 있는지는 여기에 두 가지가 더 곱해진다:

1. **생애주기** — 아기는 못 탄다. `ADULT` 여야 한다
2. **비행 추첨** — `FLY` 종류라도 성체가 될 때 `fly-chance` 에 실패한 개체는 **걷는 탑승까지만** 된다 (`RideMode.effective`). 원작에서도 같은 종의 일부만 날탈이 되고 나머지는 지상 탈것으로 쓴다

`fly-chance` 는 `FLY` 일 때만 의미가 있다. `FLY` 인데 확률이 0이면 어떤 개체도 날지 못하므로, 설정 검증이 이 조합을 경고한다.

### 마운트 구현 방식

| | 방식 | 판정 |
| --- | --- | --- |
| **A** | 보이지 않는 `ArmorStand` + `addPassenger` + Paper `Input` API | ✅ **기본안** — 배포 중인 플러그인에서 검증됨 |
| B | BetterModel `MountedHitBox` + `p_` 좌석 본 | ⚪ M5에서 확인. 더 간단하면 채택 |

### 절차

1. `ADULT` 이고 `ride` 가 `NONE` 이 아닌 펫만 탑승 가능
2. 우클릭 → 지상 마운트는 즉시, **비행은 시간 창 내 두 번째 우클릭으로 확인** (실수 이륙 방지)
3. 보이지 않는 `ArmorStand` 스폰 후 `addPassenger(player)`
   - `setVisible(false)` · `setGravity(false)` · `setInvulnerable(true)` · `setCollidable(false)` · `setSmall(true)` · `setBasePlate(false)` · `setPersistent(false)`
   - 스코어보드 태그 + PDC(소유자·펫 UUID)로 식별 — 서버 재시작 후 청소용
   - `addPassenger` 실패 시 마운트를 **즉시 제거**. 유령 아머스탠드를 남기지 않는다
4. 펫 본체는 `setTeleportDuration(1)` 로 마운트에 밀착 추적. 하차 시 평상시 값(8틱) 복원
5. **하차는 스니크 전용** 또는 `/pet dismount`

### 조향 — Paper `Input` API

```java
Vector look = player.getLocation().getDirection();
Vector move = new Vector();
if (input.isForward())  move.add(look);
if (input.isBackward()) move.subtract(look);
Vector flat  = new Vector(look.getX(), 0, look.getZ()).normalize();
Vector right = new Vector(-flat.getZ(), 0, flat.getX());
if (input.isRight()) move.add(right);
if (input.isLeft())  move.subtract(right);
if (move.lengthSquared() > 1e-4) move.normalize().multiply(speed);
if (input.isJump()) move.setY(move.getY() + lift);
```

### 이동 적용 — 서브스텝 + 벽 슬라이딩

```java
// 전체 이동 → 수평만 → 수직만 순으로 시도
if (!tryMove(mount, base, dx, dy, dz, yaw)
 && !tryMove(mount, base, dx, 0,  dz, yaw)
 && !tryMove(mount, base, 0,  dy, 0,  yaw)) {
    mount.setRotation(yaw, 0f);   // 셋 다 막히면 회전만
}

// tryMove: 목적지만이 아니라 경로 전체를 0.45블록 이하로 쪼개 검사한다.
// 하나라도 막히면 all-or-nothing 으로 취소 → 호출부가 축을 바꿔 재시도
int steps = Math.max(1, (int) Math.ceil(dist / 0.45));
```

안전 검사는 **탑승자의 바운딩 박스를 표본**한다 — 중심 + 반경 0.35의 4방향 점을 발치와 머리 높이 양쪽에서. 중심만 검사하면 어깨가 벽에 낀다.

### 비행

| 항목 | 설계 |
| --- | --- |
| 자격 | **A등급 이상**에서 확률적으로 `canFly` 부여 |
| 속도 | 겉날개+폭죽보다 느리게 — 원작의 밸런스 의도 |
| 연료 | 없음. 무한 비행 |
| 고도 상한 | `flight-max-height` (기본 1024) |

> **`allowFlight` 부여 방식은 채택하지 않는다.** 하차·로그아웃·종료·사망 모든 경로에서 회수하지 않으면 플레이어가 영구 크리에이티브 비행을 얻는다. `ArmorStand` 마운트 방식은 이 위험 자체가 없다.

### 위험 요소

| 위험 | 대응 |
| --- | --- |
| 하차 시 낙하 피해 | `setFallDistance(0)` + `SLOW_FALLING` 100틱 |
| 비행 중 히트박스가 블록 파괴를 방해 | 비행 중 0.1×0.1로 축소, 하차 시 복원 |
| 탑승 상태로 로그아웃 | 재접속 시 **안전 하차** (좌석 복원 안 함) |
| 탑승 중 펫/마운트 디스폰 | 매 틱 생존·탑승자·월드 일치 확인, 어긋나면 즉시 안전 하차 |
| 월드 이동 / 포탈 | 강제 하차 |
| 재시작 후 유령 마운트 | 태그 + PDC로 식별해 기동 시 청소 |

---

## 데이터와 영속화

```java
public record PetType(
        String id, String displayName, String modelId,
        Rarity rarity, AnimationSet animations, MovementProfile movement,
        RideMode ride, double flyChance,   // NONE / GROUND / FLY
        List<AbilityDefinition> abilities,
        int growthMax, Map<String, Integer> nextStage   // 가중치. 비어 있으면 종류 유지
) {}

public final class PetData {
    private final UUID petId;      // PK
    private final UUID ownerId;
    private final String typeId;
    private String nickname;
    private LifeStage stage;
    private int growth;
    private int growthStage;       // 1부터 시작
    private boolean canFly;        // 성체가 될 때 확률로 확정
    private boolean active;        // 런타임 전용. 저장하지 않는다 (아래 참고)
}
```

**M3에서 SQLite가 아니라 YAML로 결정됐다.** 5명 규모에 DB는 과잉이고, 파일 하나면 사람이 직접 열어 고칠 수 있다는 실질적 장점이 있다 — 참고 구현(betterpets-paper)도 YAML이 기본이다. `YamlPetRepository` 가 플레이어 한 명당 `playerdata/<uuid>.yml` 파일 하나를 쓴다:

```yaml
pets:
  <petId>:
    type: dragon
    nickname: 화룡이
    stage: BABY
    growth: 42
    growth-stage: 2        # 최대 단계에 못 미쳤으면 여러 번 오를 수 있다
    can-fly: false
    acquired-at: 1700000000000
    updated-at: 1700000600000     # 성장도 지연 계산의 기준
```

**원칙**

- **단일 스레드 실행자로 I/O를 직렬화한다.** 커넥션 풀 없음 — 경합 자체가 없다
- **모든 파일 I/O는 그 실행자 위에서.** 메인 스레드에서 디스크 접근 금지
- **캐시는 단순하게** — 접속 시 로드 → 메모리 읽기/쓰기 → 변경 시 즉시 비동기 저장
- **서버 종료 시 실행자를 동기 대기**(`awaitTermination`). 안 하면 마지막 쓰기를 잃는다
- **`active` 는 저장하지 않는다.** 소환 여부는 런타임 사실이라 파일에 남길 게 못 된다. 남겨두면 서버가 비정상 종료됐을 때 `active: true` 인 채로 굳어, 다음 접속에서 소환하지도 않은 펫이 보관함에 "소환 중"으로 보인다. 진실은 언제나 `PetRegistry` 에 있다
- **동시 소환 마릿수는 `PetService.summon` 이 강제한다** — DB 제약이 없으니 여기서 직접 막는다. 한도는 `config.yml` 의 `pets.max-active`, 보유 한도는 `pets.max-owned` 다 (아래 참고)
- **읽히지 않은 파일은 덮어쓰지 않고 옆으로 치운다.** Bukkit 의 `YamlConfiguration.loadConfiguration` 은 **파싱에 실패해도 예외를 던지지 않고 빈 설정을 돌려준다.** 그래서 "펫이 없는 플레이어"와 "파일이 깨진 플레이어"가 호출부에서 똑같이 보이고, 그대로 두면 다음 저장이 원본을 덮어써 복구할 길이 사라진다. 내용이 있는데 `pets` 섹션이 없으면 `<uuid>.yml.broken-<시각>` 으로 옮기고 경고한다. **내용이 있는 파일만** 옮긴다 — 정상적으로 비어 있는 파일(펫을 다 놓아준 경우)까지 옮기면 접속마다 쓰레기가 쌓인다
- **다른 백엔드가 필요해지면** `PetRepository` 인터페이스만 새로 구현하면 된다. SQLite로 가더라도 셰이딩 대신 **Paper 라이브러리 로더**를 쓴다 (README의 빌드 절 참고)

---

## 등급과 능력

### 등급 — D~S

| 등급 | 이동속도 배율 | 탑승속도 | 비행 확률 | 색상 |
| --- | --- | --- | --- | --- |
| `D` 일반 | 1.00 | 0.20 | 0% | 회색 |
| `C` 고급 | 1.10 | 0.24 | 0% | 흰색 |
| `B` 희귀 | 1.25 | 0.28 | 0% | 초록 |
| `A` 영웅 | 1.45 | 0.34 | **10%** | 파랑 |
| `S` 전설 | 1.70 | 0.42 | **35%** | 금색 |

> ⚠️ 수치는 **플레이스홀더**다. 원작의 실제 수치를 확인하지 못했다. 확정된 규칙은 **등급이 오를수록 빨라진다**와 **비행은 A등급부터**뿐이다.

**그래서 `rarity.yml` 로 뺐다.** 숫자 하나 고치는 데 재컴파일이 필요하면 아무도 밸런싱하지 않는다.

등급과 수치를 나눈 것이 핵심이다:

| | 어디에 | 왜 |
| --- | --- | --- |
| `Rarity` | 코드 (enum) | 서열이자 식별자다. `min-rarity: A` 같은 설정이 이름으로 비교하고, `atLeast` 가 선언 순서에 기댄다 |
| `RarityStats` | `rarity.yml` | 순전히 밸런싱이다. 서버마다 다를 수 있고, 우리가 정답을 모른다 |

수치는 로드 시점에 **`PetType` 안으로 박아 넣는다.** 표를 들고 다니게 하면 이동 컨트롤러·능력·GUI·알림 전부에 표를 넘겨야 한다. 리로드하면 어차피 펫 정의를 통째로 다시 만들므로 값이 굳어 있어도 문제없다.

빠진 등급·항목은 내장 기본값으로 채운다 — 언어 파일과 같은 원칙이다. `S` 의 `fly-chance` 만 손보는 게 흔한 경우인데, 그러자고 다섯 등급을 전부 적게 만들 이유가 없다.

### 능력

| 유형 | 발동 | 구현 | 예시 |
| --- | --- | --- | --- |
| `PASSIVE` | 소환 중 상시 | `AttributeModifier` 부착 | 이동속도, 최대체력 |
| `TRIGGER` | 특정 이벤트 | 리스너 + 확률 판정 | 피격 시 회복, 처치 시 추가 드랍 |

> 쿨다운 기반 수동 발동(액티브) 능력은 만들지 않기로 했다. `PetAbility.Type` 은 `PASSIVE`/`TRIGGER` 둘뿐이다.

```java
public interface PetAbility {
    AbilityType type();
    void onEquip(AbilityContext ctx);
    void onUnequip(AbilityContext ctx);    // onEquip 의 정확한 역연산이어야 한다
    default void onTrigger(AbilityContext ctx, Event event) {}
}
```

> ⚠️ **모디파이어 누적 버그.** 재접속·펫 교체·리로드마다 `AttributeModifier` 가 쌓이면 플레이어가 로켓처럼 날아간다. **부착 전 항상 같은 키의 모디파이어를 제거**하고 접속 시점에도 청소한다.

---

## 획득 경로

**NPC는 만들지 않는다.** 원작은 동물조련사 NPC에게 알을 샀지만, 5명 서버에서 NPC 상점은 비용 대비 얻는 게 없다. **알 아이템 우클릭 하나로 대체한다.**

| 경로 | 구현 |
| --- | --- |
| **알 아이템 우클릭** | 아이템 1개 소비 → 보관함에 `BABY` 상태 펫 추가 |
| 관리자 지급 | `/petadmin give`, `/petadmin egg` |

### 아이템 — 알과 먹이

알과 먹이를 `items.yml` 한 파일에 모은다. 둘 다 성격이 비슷하고 개수도 적어서, 관리자가 아이템을 손볼 때 파일을 오가지 않아도 되게 했다.

**식별은 PDC로 한다.** 이름·로어 문자열 비교 금지 — 플레이어가 모루로 이름을 바꾸면 깨진다.

```java
meta.getPersistentDataContainer().set(EGG_KEY, PersistentDataType.STRING, eggId);
meta.getPersistentDataContainer().set(FEED_KEY, PersistentDataType.STRING, feedId);
```

먹이도 **id를 담는다** — 단순 플래그가 아니다. 먹이마다 성장도 증가량이 달라서 어떤 먹이인지 알아야 한다.

```yaml
# items.yml
eggs:
  dragon_egg:
    display-name: "<gold>드래곤 알"
    material: TURTLE_EGG
    gives: dragon            # 고정 알

  random_egg:
    display-name: "<white>수상한 알"
    material: EGG
    weights:                 # 랜덤 알 — 가중치 추첨
      wolf: 50
      dragon: 1

feeds:
  milk:
    display-name: "<white>펫 우유"
    material: MILK_BUCKET
    growth: 10               # 이 먹이가 올려줄 성장도

  premium_feed:
    display-name: "<gold>고급 사료"
    material: WHEAT
    growth: 30
```

`growth` 를 적지 않은 먹이는 `config.yml` 의 `growth.feed-amount` 를 쓴다 (`FeedDefinition.growthOr`).

`item-model` 로 리소스팩 모델을 네임스페이스 키로 가리킨다 — `setCustomModelData(int)` 는 지원 중단됐다.

- 우클릭 처리는 `PlayerInteractEvent`. **`EquipmentSlot.HAND` 만 처리**해 오프핸드 중복 발동을 막는다
- 보관함이 가득 찼으면 **아이템을 소비하지 않고** 메시지만 띄운다
- 설정에서 지워진 먹이를 들고 있으면 **아이템을 먹어치우지 않고** 알려준다
- 지급: `/petadmin egg <플레이어> <알id> [개수]`, `/petadmin feed <플레이어> <먹이id> [개수]`

---

## 보유·소환 한도

한 명이 가질 수 있는 마릿수(`pets.max-owned`)와 동시에 소환해 둘 수 있는 마릿수(`pets.max-active`)를 `config.yml` 에서 정한다. **0 은 무제한**이다 — 음수를 무제한으로 쓰면 `-1` 을 의도한 사람과 오타를 구분할 수 없어서 0 으로 통일했다 (`PetLimits`).

| 한도 | 넘겼을 때 |
| --- | --- |
| `max-owned` | 지급 자체를 거절한다. **알 아이템은 소비하지 않는다** — 아이템을 잃는 게 제일 나쁜 결과다 |
| `max-active` | 거절하지 않고 **가장 먼저 소환했던 펫을 돌려보낸다** |

동시 소환을 거절하지 않는 이유는 기본값이 1이기 때문이다. 거절하면 한 마리만 두고 쓰는 서버에서 소환할 때마다 먼저 해제해야 한다. "오래된 것부터 밀어낸다"는 규칙은 한도를 올려도 그대로 성립하고, 한도 1에서는 예전의 "소환하면 교체"와 정확히 같은 동작이 된다. GUI 는 누르기 전에 그 사실을 소환 버튼 로어로 알려준다.

**`max-active` 를 올릴 때 따라오는 것들**

- `PetRegistry` 가 소유자당 여러 마리를 **소환 순서대로** 들고 있어야 한다 (`CopyOnWriteArrayList`) — "가장 오래된 것"이 순서에 기대고 있어서다
- **추종 목표를 마리마다 벌려야 한다.** 안 그러면 전부 "주인 뒤 한 점"을 노려서 겹쳐 떨거나 서로 밀어내는 것처럼 보인다. 슬롯마다 각도를 `0° · +35° · -35° · +70° …` 로 줘서 부채꼴로 세운다. 전체 마릿수를 모르는 채 고정 간격을 쓰는 이유는, 마릿수에 맞춰 매번 다시 배치하면 한 마리를 넣고 뺄 때마다 나머지가 우르르 움직여 더 어수선해지기 때문이다
- 탑승은 여전히 한 번에 한 마리다. 대신 `Ride` 가 **어느 펫인지**(`petId`)를 들고 있어야 한다. 없으면 틱 루프가 같이 나와 있는 펫을 전부 마운트로 순간이동시킨다
- 능력치 모디파이어 키가 **능력별이 아니라 개체별**이어야 한다(`ability_<능력>_<펫id>`). 능력별 키면 둘째 펫이 첫째 것을 덮어쓰고, 둘째를 해제할 때 첫째 것까지 사라진다. 개체별 키라서 여러 마리의 보너스는 **겹쳐서** 적용된다
- 소환한 마릿수만큼 캐리어 엔티티와 렌더 트래커가 늘어난다. 5명 서버라도 1인 10마리면 트래커 50개다

---

## 다국어

`config.yml` 의 `language` 로 `lang/<코드>.yml` 을 고른다. 기본 제공은 `ko_kr` 과 `en_us` 다.

**두 겹으로 읽는다.** jar 안의 `ko_kr` 을 통째로 깔고 그 위에 선택한 언어를 덮는다. 이 순서가 전부인데, 얻는 게 둘이다:

1. **번역이 빠진 키가 화면에 키 이름 그대로 뜨지 않는다.** 플러그인이 올라가 문구가 늘어도 기존 번역 파일을 그대로 두면 된다 — 새 키만 한국어로 나온다
2. **번역자는 바꾸고 싶은 줄만 남겨도 된다.** 파일 전체를 최신 상태로 유지할 의무가 없다

`language` 값은 파일 경로로 쓰이므로 `[a-z0-9_]{1,32}` 로 제한한다. 설정 파일이라고 해서 경로를 그대로 믿지 않는다 — `../../` 같은 값이 들어와도 기본 언어로 접힌다.

---

## 전체 알림

희귀 펫을 얻거나 성체가 되면 서버 전체에 알린다. **기본 문턱은 A등급**이다.

문턱을 두는 이유는 단순하다. 아무 펫이나 알리면 5명 서버에서도 채팅이 밀리고, 그러면 관리자가 기능을 통째로 끈다. 등급(`min-rarity`)과 성장 단계(`min-growth-stage`) 두 조건을 **모두** 넘겨야 나간다.

성체 알림은 **두 경로 모두**에서 나가야 한다:

| 경로 | 어디서 |
| --- | --- |
| 먹여서 자람 | `InteractionListener.feed` 의 `GREW_UP` |
| 시간이 흘러 자람 | `PetTicker.applyTimeGrowth` 의 `promoteIfGrown` 결과 |

후자를 빠뜨리면 "가만히 뒀더니 조용히 성체가 됐다"가 된다. 성장도가 시간 경과로도 오르는 설계라 이 경로가 오히려 더 흔하다.

---

## 선택적 연동 — DiscordSRV · Geyser

**둘 다 리플렉션으로 붙는다. 컴파일 의존이 없다.**

DiscordSRV 는 메이븐 센트럴에 없고 자체 저장소에 있다. 의존을 걸면 이 플러그인을 빌드하려는 사람이 저장소 설정까지 따라 해야 하는데, 실제로 쓰는 건 "채널 하나에 문자열 하나 보내기"뿐이다. 값이 맞지 않는다.

```
DiscordSRV.getPlugin()
  → getDestinationTextChannelForGameChannelName(channel)   // 별칭을 DiscordSRV 가 풀어준다
  → sendMessage(CharSequence).queue()                      // queue() 를 빼면 요청이 안 나간다
```

원칙은 **실패하면 조용히 꺼진다**는 것이다. Discord 알림이 안 가는 것보다 그 때문에 펫 시스템이 멈추는 게 훨씬 나쁘다. 이유는 기동 시 한 번만 로그에 남긴다. 전송은 **비동기**다 — Discord 왕복은 네트워크라 메인 스레드에서 하면 서버가 멈춘다.

### Bedrock 판별

Floodgate 의 `FloodgateApi.isFloodgatePlayer(uuid)` 로 구분한다. 쓰는 곳은 지금 한 군데다 — **비행 이륙 확인 생략**.

이륙 확인은 "3초 안에 한 번 더 우클릭"인데, 터치 조작에서 두 번째 우클릭을 맞히기가 훨씬 어렵다. 자바 플레이어의 실수 방지는 그대로 두고 Bedrock 쪽만 건너뛴다.

**Floodgate 가 없으면 전원 자바로 취급한다.** 모르면 자바로 보는 쪽이 맞다 — 반대로 하면 자바 플레이어의 조작까지 바뀌어서, 틀렸을 때의 피해가 훨씬 크다.

모델 변환 자체(GeyserModelEngine)에는 이 플러그인이 관여하지 않는다. BetterModel 과 GME 사이에서 처리되고, 우리는 GME 가 없을 때 **기동 시 경고**만 한다 — 없으면 Bedrock 플레이어에게 펫이 히트박스로만 존재하고 모델은 보이지 않는다.

---

## 설정 파일

```
plugins/BetterPets/
├─ config.yml       언어 · 알림 · 연동 · 한도 · 성장 · 기믹 · 비행 설정
├─ lang/*.yml       사용자 노출 문자열 (MiniMessage). ko_kr · en_us 제공
├─ items.yml        알 · 먹이 아이템 정의
├─ rarity.yml      등급별 수치 (속도 · 비행 확률 · 색)
├─ pets/*.yml       펫 종류 정의 (wolf · dragon · pig 예시 제공)
└─ playerdata/      플레이어별 펫 데이터 (자동 생성)
```

GUI 레이아웃은 아직 코드에 있다. 외부화가 필요해지면 그때 파일을 나눈다.

**리로드가 실제로 반영되는지가 별도의 문제다.** 값을 읽어 들고 있는 쪽은 `/petadmin reload` 때 다시 밀어 넣어야 한다 — 지금은 셋이다: 비행 수치(`RideController`), 보유·소환 한도(`PetService`), 방송 조건(`BroadcastService`). 하나라도 빠뜨리면 "설정을 다시 읽었습니다"가 거짓말이 되고, 그 침묵은 관리자의 오후를 통째로 잡아먹는다. GUI 와 플레이스홀더는 값을 들지 않고 **매번 서비스에 묻는다** — 들고 있으면 같은 문제가 하나 더 생긴다.

**설정 검증** — 로드 시 필수 필드 누락, 존재하지 않는 모델 참조(`BetterModel.modelKeys()` 로 대조), 미등록 능력 id를 **모두 수집해 한 번에 보고**한다. 첫 오류에서 멈추지 않는다. 관리자가 재시작을 반복하게 만들지 않기 위해서다.

---

## 성능

| 지표 | 목표 |
| --- | --- |
| 활성 펫 10마리 기준 메인 스레드 부하 | < 1ms/tick |
| 접속 시 펫 데이터 로드 | < 50ms (비동기) |

목표치는 **추정이며 측정된 값이 아니다.** Spark 실측은 아직 남아 있다.

하지 않는 것: 거리 LOD, `viewRange` 튜닝, 마일스톤마다의 부하 테스트. 이유는 [설계 전제](#설계-전제--서버-규모).

### 실제로 손댄 곳

측정 전이라 **추측으로 고르지 않았다.** "틱 루프 안에 있는가"만 기준으로 삼았다 —
초당 여러 번 도는 코드에서 객체를 만들거나 패킷을 보내는 자리가 그것이다.

| 자리 | 무엇이 문제였나 | 어떻게 |
| --- | --- | --- |
| `MovementController.faceOwner` | 가만히 서 있는 펫이 매 틱 <b>같은 각도를 다시 보냈다</b>. 소환 마릿수 × 시청자 수만큼 패킷이 된다 | 2도 미만이면 보내지 않는다 |
| `MovementController` 전반 | `getLocation()` 이 호출마다 새 `Location` 을 만든다. 지형 검사는 한 번에 최대 5개 | 인스턴스별 버퍼를 재사용 |
| `PetTicker.tickRides` | 소환된 펫 전부를 훑으며 "타고 있나"를 물었다. 탑승자는 보통 0~1명 | 타는 사람 쪽을 훑도록 뒤집음 |
| `PetRegistry.all()` | 호출마다 `ArrayList` 를 뜬다. 틱 루프가 초당 30번 | 복사 없는 `forEach` |
| `RideController.drive`·`isSafe` | 매 틱, 탑승자마다, 서브스텝마다 `getConfig()` | 기동/리로드 시 캐시 |
| `GrowthService.refresh` | 1분이 안 지나도 `Projection` 을 하나 만든다. 초당 5번 × 펫 수 | 값이 안 바뀌는 경우를 먼저 쳐냄 |
| `AbilityTriggerListener` | 피격·처치 이벤트마다 `List.copyOf` | 복사 없는 `forEachOf` |
| `Tags.strip` | `String.replaceAll` 이 호출마다 `Pattern.compile` | 미리 컴파일해 한곳으로 |
| `PetStore.persist` | 한 마리씩 저장 → 같은 `<uuid>.yml` 을 마리 수만큼 파싱·직렬화 | 소유자별로 묶어 파일당 한 번 |

**하지 않은 것도 기록해 둔다.** 거리에 따라 틱을 건너뛰는 LOD 는 넣지 않았다. 5명 규모에서
얻는 것보다 "멀리 있는 펫이 끊겨 보인다"는 버그 신고가 더 비싸다. 필요해지면 그때 넣는다.

---

## 테스트

| 레벨 | 대상 | 도구 |
| --- | --- | --- |
| 단위 | Bukkit 을 안 쓰는 도메인·설정 로직 | JUnit 5 |
| 수동 | 렌더링 · 이동 · 탑승 · 비행 · 연동 | Paper 테스트 서버 |

**선은 "Bukkit 이 필요한가"로 긋는다.** 서버 없이 돌릴 수 있는 것만 단위 테스트로 덮고,
나머지는 아래 수동 체크리스트에 맡긴다. Mockito 로 `Player` 를 흉내 내는 것은 하지 않는다 —
그렇게 만든 테스트는 우리 코드가 아니라 우리가 상상한 Bukkit 을 검증한다.

지금 덮고 있는 것 (93개):

| 대상 | 무엇을 지키는가 |
| --- | --- |
| `GrowthCurve` | 성장도와 기준 시각이 <b>함께</b> 움직인다. 시계가 뒤로 가도 안전하다 |
| `Weighted` · `EggDefinition` · `PetType` | 가중치 추첨이 <b>설정에 적은 순서</b>를 지킨다 (실제로 겪은 버그) |
| `PetLimits` · `RarityTable` · `RarityStats` | 0 = 무제한, 설정 실수(음수 속도·1 넘는 확률)를 접는다 |
| `BroadcastService.Rules` | 등급·단계 문턱이 <b>둘 다</b> 걸린다 |
| `Messages` | 언어 코드가 파일 경로로 새지 않는다 (`../../`) |
| `LanguageFilesTest` | ko_kr 과 en_us 의 키·치환 자리가 어긋나지 않는다 |
| `AbilityDefinition` | 성장도 비례 수치와 등급 배율 |

### 수동 체크리스트

매 마일스톤마다 반복한다.

- [ ] 소환 → 로그아웃 → 재접속 시 유령 모델이 남지 않는가
- [ ] 소환 상태로 서버 재시작 시 캐리어 엔티티가 청소되는가
- [ ] 월드 이동 / 네더 포탈 통과 시 펫이 따라오는가
- [ ] 펫 교체 20회 반복 시 `AttributeModifier` 가 누적되지 않는가
- [ ] 아기 펫을 꺼내둔 채 먹여서 성체가 되면 능력이 바로 붙는가 (재소환 없이)
- [ ] 소환 중인 펫의 캐리어를 강제로 죽이면 능력 버프가 같이 사라지는가
- [ ] 탑승 중 로그아웃 → 재접속 시 안전하게 하차되는가
- [ ] 탑승 중 펫이 디스폰돼도 낙하 피해를 입지 않는가
- [ ] 알 아이템을 모루로 개명해도 여전히 인식되는가 (PDC 식별)
- [ ] 보관함이 꽉 찬 상태에서 알을 우클릭하면 아이템이 소비되지 않는가
- [ ] `/petadmin debug` 의 활성 펫 수와 트래커 수가 일치하는가
- [ ] 펫 10마리 소환 상태에서 TPS 20을 유지하는가
- [ ] 여러 마리 소환 상태에서 한 마리에 탔을 때 나머지가 제자리에 남는가
- [ ] DiscordSRV 없이 기동해도 경고 한 줄만 남고 정상 동작하는가
- [ ] `language: en_us` 로 바꾸면 모든 문구가 영어로 나오는가
- [ ] 번역 파일에서 키를 지우면 그 줄만 한국어로 나오는가 (전체가 깨지지 않는가)
- [ ] BetterModel을 제거한 상태에서 플러그인이 안전하게 비활성화되는가

---

## 리스크

| # | 리스크 | 영향 | 확률 | 대응 |
| --- | --- | :---: | :---: | --- |
| R1 | 트래커 누수 → 유령 모델 | 높음 | 중 | `AutoCloseable` + 4경로 close + `registries()` 진단 + M1 완료 조건 |
| R2 | 탑승 구현 난도 | 중 | 낮음 | 검증된 `ArmorStand` + `Input` 구조 확보 ([참고 구현](#참고-구현-분석)) |
| R3 | BetterModel 버전 업 시 API 파괴 | 중 | 높음 | 렌더 격리 계층 + 버전 핀 고정 |
| R4 | `AttributeModifier` 누적 | 중 | 높음 | 부착 전 항상 제거 + 접속 시 청소 + 반복 테스트 |
| R5 | 원작 사양 오해 | 중 | 중 | 검색 요약 기반이며 **원문 대조를 못 했다.** 다르면 밸런싱 수치부터 조정 |
| R6 | 모델 제작 지연으로 M2/M5 블로킹 | 중 | 중 | M1과 병렬 착수. 탑승 모델은 M5 전까지 |
| R12 | **Bedrock 변환 실패** — BetterModel 3.4.1 + GME(2.2.0 빌드) + MC 26.2 조합이 미검증 | 높음 | 중 | **M1에서 모델 1개로 조기 검증.** 실패 시 BetterModel 2.2.0 하향 등 대안 검토 ([Geyser 연동](#bedrock-지원--geyser-연동)) |
| R13 | Bedrock 모델 작업이 두 배 | 중 | 높음 | 텍스처 한 장 원칙을 처음부터 지켜 변환을 단순하게 유지 |
| R7 | 성능 | 낮음 | 낮음 | 5명 규모라 강등. 기본 원칙만 지키고 M9에서 1회 확인 |
| R8 | 유령 마운트 잔존 | 중 | 중 | 태그 + PDC 식별, 기동 시 청소, 매 틱 생존 검사 |
| R9 | GUI 아이템 복제 취약점 | 높음 | 낮음 | `setCancelled(true)` 선행 + 코드 리뷰 |
| R10 | 모델 에셋 저작권 | 높음 | 낮음 | 직접 제작. 원본 복제 금지 |
| R11 | JDK 25 빌드 환경 부재 | 중 | 중 | 개발 머신에 JDK 25 설치 |

---

## 미해결 질문

| # | 질문 | 현재 가정 | 필요 시점 |
| --- | --- | --- | --- |
| **Q1** | 등급별 이동속도·비행 확률 실제 수치 | [등급 표](#등급--ds)의 플레이스홀더 | M7 전 |

나머지 질문은 모두 해소됐다: JDK 버전(Java 25), 서버 규모(5명), 저장소(SQLite/YAML, M3 결정), 경제 연동(보류), NPC(제거), 모델 에셋(직접 제작), 비행 조작(`ArmorStand` + `Input`).

Q1은 밸런싱 수치 문제라 실제로 타보면서 정하는 게 맞다. 개발 진행을 막지 않는다.
