# 설계 문서

BetterPets의 아키텍처, 검증된 API, 조사 근거, 리스크. 사용 안내는 [README](../README.md) 참고.

- [설계 전제 — 서버 규모](#설계-전제--서버-규모)
- [아키텍처](#아키텍처)
- [BetterModel API — 검증된 사양](#bettermodel-api--검증된-사양)
- [원작 시스템 분석](#원작-시스템-분석)
- [참고 구현 분석](#참고-구현-분석)
- [펫 생애주기](#펫-생애주기)
- [행동 설계](#행동-설계)
- [탑승과 비행](#탑승과-비행)
- [데이터와 영속화](#데이터와-영속화)
- [등급과 능력](#등급과-능력)
- [획득 경로](#획득-경로)
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
Domain         Rarity · LifeStage · GrowthCurve · PetType · PetData
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
| 능력 | 이동속도 위주 | 패시브/트리거/액티브 확장 | 원작보다 확장. 끄면 원작과 동일 |

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

## 펫 생애주기

```
   획득            먹이           성장도 100        (조건)
 ┌───────┐     ┌──────────┐     ┌───────┐      ┌────────┐
 │  EGG  │────▶│ HATCHING │────▶│ BABY  │─────▶│ ADULT  │
 └───────┘     └──────────┘     └───┬───┘      └────────┘
                                    │ 과급식        탑승 가능
                                    ▼              비행 가능(A+)
                                 ┌───────┐         능력 발현
                                 │  PIG  │
                                 └───────┘
```

| 상태 | 진입 조건 | 소환 | 탑승 | 능력 |
| --- | --- | :---: | :---: | :---: |
| `EGG` | 획득 시 | ❌ | ❌ | ❌ |
| `HATCHING` | 먹이 급여 시작 | ❌ | ❌ | ❌ |
| `BABY` | 부화 완료 | ✅ | ❌ | ❌ |
| `ADULT` | 성장도 상한 도달 | ✅ | ✅ | ✅ |
| `PIG` | 아기 상태에서 단시간 과급식 | ✅ | ✅ | ❌ |

### 성장도

| 경로 | 증가량 |
| --- | --- |
| 시간 경과 | 1분당 +1 |
| 먹이(우유) | +10 |
| 관리자 지급 | 임의 |

**성장도는 `updated_at` 기준으로 지연 계산한다.** 1분마다 전체 펫을 순회하며 +1 하는 대신, 읽을 때 경과 시간을 환산하고 저장 시점에만 반영한다. 주기 작업이 없어져 구현도 더 단순하다.

> ⚠️ **함정** — 계산 후 기준 시각을 무조건 `now` 로 밀면 1분에 못 미친 나머지 시간이 매번 버려진다. 저장이 잦으면 펫이 영원히 자라지 않는다. `GrowthCurve.project()` 가 새 성장도와 **새 기준 시각을 함께** 돌려주는 이유다. 이 경우는 테스트로 고정돼 있다.

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

일회성 애니메이션(`attack`, `eat`, `hatch`)은 `priority` 를 올린 `PLAY_ONCE` 로 **오버레이**하고, 완료 콜백에서 루프 상태를 복원한다.

---

## 탑승과 비행

원작의 핵심 기능이다. [참고 구현](#참고-구현-분석)에서 검증된 구조를 기본안으로 삼는다.

### 마운트 방식

| | 방식 | 판정 |
| --- | --- | --- |
| **A** | 보이지 않는 `ArmorStand` + `addPassenger` + Paper `Input` API | ✅ **기본안** — 배포 중인 플러그인에서 검증됨 |
| B | BetterModel `MountedHitBox` + `p_` 좌석 본 | ⚪ M5에서 확인. 더 간단하면 채택 |

### 절차

1. `ADULT` 이고 `rideable: true` 인 펫만 탑승 가능
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
        String id, String displayName, String modelId, String eggModelId,
        Rarity rarity, AnimationSet animations, MovementProfile movement,
        boolean rideable, double flyChance,
        List<AbilityDefinition> abilities,
        int growthMax, String evolvesInto
) {}

public final class PetData {
    private final UUID petId;      // PK
    private final UUID ownerId;
    private final String typeId;
    private String nickname;
    private LifeStage stage;
    private int growth;
    private boolean canFly;        // 부화 시 확률로 확정
    private boolean active;
}
```

```sql
CREATE TABLE IF NOT EXISTS bp_pet (
    pet_id      CHAR(36)     NOT NULL PRIMARY KEY,
    owner_id    CHAR(36)     NOT NULL,
    type_id     VARCHAR(64)  NOT NULL,
    nickname    VARCHAR(32)  NULL,
    stage       VARCHAR(16)  NOT NULL DEFAULT 'EGG',
    growth      INT          NOT NULL DEFAULT 0,
    can_fly     TINYINT      NOT NULL DEFAULT 0,
    active      TINYINT      NOT NULL DEFAULT 0,
    acquired_at BIGINT       NOT NULL,
    updated_at  BIGINT       NOT NULL     -- 성장도 지연 계산의 기준
);
CREATE INDEX IF NOT EXISTS idx_bp_pet_owner ON bp_pet (owner_id);

-- 소유자당 활성 펫 1마리를 DB에서 강제한다
CREATE UNIQUE INDEX IF NOT EXISTS idx_bp_pet_active
    ON bp_pet (owner_id) WHERE active = 1;
```

**원칙**

- **SQLite 단일 커넥션 + 단일 스레드 실행자.** 커넥션 풀 없음 — 단일 스레드가 SQLite 잠금 문제를 애초에 없앤다
- **모든 DB I/O는 그 실행자 위에서.** 메인 스레드에서 JDBC 호출 금지
- **캐시는 단순하게** — 접속 시 로드 → 메모리 읽기/쓰기 → 변경 시 즉시 비동기 저장
- **서버 종료 시 실행자를 동기 대기**(`awaitTermination`). 안 하면 마지막 쓰기를 잃는다
- **MySQL 구현체는 만들지 않는다.** 인터페이스는 유지하므로 필요해지면 추가

> **M3에서 결정할 것** — 5명 규모면 YAML도 충분하다. 참고 구현도 YAML이 기본이다. SQLite를 쓴다면 셰이딩 대신 **Paper 라이브러리 로더**를 쓴다 (README의 빌드 절 참고).

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

> ⚠️ 수치는 **플레이스홀더**다. 원작의 실제 수치를 확인하지 못했다. 확정된 규칙은 **등급이 오를수록 빨라진다**와 **비행은 A등급부터**뿐이다. 밸런싱은 M7에서 `rarity.yml` 로 외부화한다.

### 능력

| 유형 | 발동 | 구현 | 예시 |
| --- | --- | --- | --- |
| `PASSIVE` | 소환 중 상시 | `AttributeModifier` 부착 | 이동속도, 최대체력 |
| `TRIGGER` | 특정 이벤트 | 리스너 + 확률 판정 | 피격 시 회복, 처치 시 추가 드랍 |
| `ACTIVE` | 수동 / 쿨다운 | 쿨다운 맵 | 주변 몹 넉백 |

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
| **알 아이템 우클릭** | 아이템 1개 소비 → 보관함에 `EGG` 상태 펫 추가 |
| 관리자 지급 | `/petadmin give`, `/petadmin egg` |

### 알 아이템

**식별은 PDC로 한다.** 이름·로어 문자열 비교 금지 — 플레이어가 모루로 이름을 바꾸면 깨진다.

```java
meta.getPersistentDataContainer().set(EGG_KEY, PersistentDataType.STRING, eggId);
```

```yaml
# eggs.yml
dragon_egg:
  display-name: "&6드래곤 알"
  material: TURTLE_EGG
  model-data: 1001
  gives: dragon             # 고정 알

random_egg:
  display-name: "&f수상한 알"
  material: EGG
  weights:                  # 랜덤 알 — 가중치 추첨
    wolf: 50
    bear: 30
    dragon: 1
```

- 우클릭 처리는 `PlayerInteractEvent`. **`EquipmentSlot.HAND` 만 처리**해 오프핸드 중복 발동을 막는다
- 보관함이 가득 찼으면 **아이템을 소비하지 않고** 메시지만 띄운다

---

## 설정 파일

```
plugins/BetterPets/
├─ config.yml       일반 설정, 저장소, 성능, 기믹 on/off
├─ messages.yml     사용자 노출 문자열 (한국어)
├─ gui.yml          GUI 레이아웃
├─ rarity.yml       등급별 수치
├─ eggs.yml         알 아이템 정의
└─ pets/*.yml       펫 종류 정의
```

**설정 검증** — 로드 시 필수 필드 누락, 존재하지 않는 모델 참조(`BetterModel.modelKeys()` 로 대조), 미등록 능력 id를 **모두 수집해 한 번에 보고**한다. 첫 오류에서 멈추지 않는다. 관리자가 재시작을 반복하게 만들지 않기 위해서다.

---

## 성능

| 지표 | 목표 |
| --- | --- |
| 활성 펫 10마리 기준 메인 스레드 부하 | < 1ms/tick |
| 접속 시 펫 데이터 로드 | < 50ms (비동기) |

목표치는 **추정이며 측정된 값이 아니다.** M9에서 Spark로 1회 실측한다.

하지 않는 것: 거리 LOD, `viewRange` 튜닝, 마일스톤마다의 부하 테스트. 이유는 [설계 전제](#설계-전제--서버-규모).

---

## 테스트

| 레벨 | 대상 | 도구 |
| --- | --- | --- |
| 단위 | `GrowthCurve`, `Rarity`, `LifeStage`, 설정 파서 | JUnit 5 |
| 통합 | Repository CRUD | JUnit + 인메모리 DB |
| 수동 | 렌더링 · 이동 · 탑승 · 비행 | Paper 테스트 서버 |

### 수동 체크리스트

매 마일스톤마다 반복한다.

- [ ] 소환 → 로그아웃 → 재접속 시 유령 모델이 남지 않는가
- [ ] 소환 상태로 서버 재시작 시 캐리어 엔티티가 청소되는가
- [ ] 월드 이동 / 네더 포탈 통과 시 펫이 따라오는가
- [ ] 펫 교체 20회 반복 시 `AttributeModifier` 가 누적되지 않는가
- [ ] 탑승 중 로그아웃 → 재접속 시 안전하게 하차되는가
- [ ] 탑승 중 펫이 디스폰돼도 낙하 피해를 입지 않는가
- [ ] 알 아이템을 모루로 개명해도 여전히 인식되는가 (PDC 식별)
- [ ] 보관함이 꽉 찬 상태에서 알을 우클릭하면 아이템이 소비되지 않는가
- [ ] `/petadmin debug` 의 활성 펫 수와 트래커 수가 일치하는가
- [ ] 펫 10마리 소환 상태에서 TPS 20을 유지하는가 (M9)
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
