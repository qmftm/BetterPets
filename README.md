# BetterPets

악어의 놀이터2 스타일 펫 플러그인 — [BetterModel](https://github.com/toxicity188/BetterModel) 기반 Paper 플러그인

| | |
| --- | --- |
| 계획서 버전 | **v1.1** (2026-09-08) |
| 대상 | Paper / Minecraft **26.2** / Java **25** |
| BetterModel | **3.4.1** (MIT) |
| 상태 | 기획 확정, 구현 착수 전 |
| 예상 기간 | 약 11.5주 (1인 파트타임) |

---

## 목차

- [1. 요약](#1-요약)
- [2. 목표와 범위](#2-목표와-범위)
- [3. 전제 조건](#3-전제-조건)
- [4. 남은 확인 사항](#4-남은-확인-사항)
- [5. 아키텍처](#5-아키텍처)
- [6. BetterModel 연동 — 검증된 API](#6-bettermodel-연동--검증된-api)
- [7. 모델 제작 규격 (직접 제작용)](#7-모델-제작-규격-직접-제작용)
- [8. 펫 행동 설계](#8-펫-행동-설계)
- [9. 데이터 모델과 영속화](#9-데이터-모델과-영속화)
- [10. 성장 시스템](#10-성장-시스템)
- [11. 능력 시스템](#11-능력-시스템)
- [12. GUI](#12-gui)
- [13. 명령어와 권한](#13-명령어와-권한)
- [14. 설정 파일](#14-설정-파일)
- [15. 성능](#15-성능)
- [16. 테스트](#16-테스트)
- [17. 로드맵](#17-로드맵)
- [18. 리스크](#18-리스크)
- [19. 참고 자료](#19-참고-자료)

---

## 1. 요약

플레이어가 소유한 펫을 소환하면 BetterModel이 렌더링하는 3D 모델이 따라다니고, 레벨·등급·능력을 가지며, GUI로 관리한다.

**핵심 설계 결정 4가지:**

| | 결정 | 근거 |
| --- | --- | --- |
| **렌더링 캐리어** | 보이지 않는 `Mob` + `EntityTracker` | `DummyTracker`는 클릭 판정·이름표를 전부 직접 구현해야 한다. 그 비용이 엔티티 1개 비용보다 크다 |
| **이동** | `setAI(false)` 후 자체 컨트롤러 | 바닐라 경로탐색은 CPU 비용이 크고 제어가 어렵다. 거리 초과 시 텔레포트 폴백 |
| **애니메이션** | 상태 전이 시점에만 `animate()` 호출 | `animate()`는 패킷을 만든다. 매 틱 호출하면 동접에 비례해 트래픽이 터진다 |
| **영속화** | SQLite 기본 + `PetRepository` 추상화 | MySQL 교체 가능. 모든 I/O 비동기, write-behind 캐시 |

---

## 2. 목표와 범위

### 목표

- 플레이어별로 여러 마리 펫을 **소유·보관·장착**한다
- 장착한 펫은 3D 모델로 렌더링되어 **따라다니고 애니메이션**한다
- 펫은 **등급·레벨·경험치**를 가지며 성장하면 능력이 강해진다
- 펫은 **패시브 / 트리거 / 액티브** 능력을 소유자에게 제공한다
- 모든 펫 정의는 **설정 파일로 데이터화**되어 코드 수정 없이 펫을 추가할 수 있다

### 포함 기능

| # | 기능 | 마일스톤 |
| --- | --- | --- |
| F1 | 펫 소환 / 해제 / 모델 렌더링 | M1 |
| F2 | 추종 이동 · 텔레포트 · 상태 애니메이션 | M2 |
| F3 | 펫 보관함 및 상세 GUI | M4 |
| F4 | 레벨 · 경험치 · 등급 성장 | M5 |
| F5 | 능력 시스템 (패시브 / 트리거 / 액티브) | M5 |
| F6 | 획득 경로 — 알 아이템, 뽑기, 관리자 지급 | M6 |
| F7 | 이름 변경, 해방, 진화 | M6 |
| F8 | 데이터 영속화 (SQLite / MySQL) | M3 |
| F9 | 명령어 · 권한 · PlaceholderAPI | M3, M7 |

### v1.0 제외

- **펫 탑승(라이딩)** — BetterModel의 `SEAT` 본 태그로 구현 가능하나 검증 필요. v1.1 이후
- 펫 간 전투 / PvP 펫 대전, 펫 거래 시장, 웹 대시보드

---

## 3. 전제 조건

BetterModel 3.4.1 **소스 JAR을 직접 내려받아 검증한** 값이다.

| 항목 | 값 | 확인 방법 |
| --- | --- | --- |
| Minecraft | `26.2` | 3.4.1 태그의 `gradle.properties` |
| Java | `25` | 배포 모듈 메타데이터 `org.gradle.jvm.version: 25` |
| BetterModel | `3.4.1` (2026-08-11) | Maven Central 최신 릴리스, MIT |
| 아티팩트 | `io.github.toxicity188:bettermodel-bukkit-api` | 2.x의 `bettermodel`에서 **이름이 바뀌었다** |
| API 패키지 | `kr.toxicity.model.api` | 소스 JAR 확인 |
| paper-api | `io.papermc.paper:paper-api:26.2.build.121-stable` | **버전 체계가 바뀌었다.** 더 이상 `1.21.x-R0.1-SNAPSHOT`이 아니다 |

### 빌드 설정

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
    maven("https://maven.blamejared.com/")   // BetterModel 전이 의존성
    maven("https://maven.nucleoid.xyz/")     // BetterModel 전이 의존성
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.4.1")

    implementation("org.xerial:sqlite-jdbc:3.50.1.0")
    implementation("com.zaxxer:HikariCP:6.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.0")
}

tasks.test { useJUnitPlatform() }
```

```yaml
# plugin.yml
name: BetterPets
main: kr.qmftm.betterpets.BetterPetsPlugin
api-version: '1.21'
depend: [BetterModel]
softdepend: [Vault, PlaceholderAPI, MythicMobs]
```

> ⚠️ **BetterModel 3.4.1은 Java 25 전용이다.** 클래스 파일 버전 69라서 JDK 21 이하로는 컴파일조차 되지 않는다. 빌드 머신과 운영 서버 모두 JDK 25가 필요하다.

---

## 4. 남은 확인 사항

| # | 질문 | 현재 가정 | 필요 시점 |
| --- | --- | --- | --- |
| ~~Q1~~ | ~~JDK 버전~~ | ✅ **Java 25 / MC 26.2 확정** | — |
| ~~Q3~~ | ~~모델 에셋~~ | ✅ **직접 제작 (7장이 규격)** | — |
| **Q2** | 예상 동시 접속자 수는? | 50~100명 | M3 전 |
| **Q4** | 기존 MySQL DB가 있는가? | 없음 — SQLite 기본 | M3 전 |
| **Q5** | Vault(경제)를 쓰는가? | 사용 | M6 전 |
| **Q6** | 등급/능력 밸런싱 기준은? | 아래 가정 | M5 전 |

**Q6 — 본 계획서가 가정한 "악어의 놀이터 2 스타일"**

원 서버의 정확한 펫 사양을 확인할 수 없어 해당 장르의 일반적 형태로 설계했다. 다르면 알려주면 반영한다.

- 커스텀 3D 모델 펫이 플레이어를 따라다님
- 등급(일반/희귀/영웅/전설)별 성능 배율
- 알 아이템 또는 뽑기로 획득
- 먹이/경험치로 레벨업
- 소유자에게 패시브 버프 제공
- 여러 마리 보유 후 한 마리만 장착

---

## 5. 아키텍처

의존 방향은 항상 안쪽(Domain)을 향한다. **Domain은 Bukkit API를 모른다** — 순수 데이터와 규칙만 담아 단위 테스트가 가능하게 한다.

```
Presentation   /pet · /petadmin · PetBoxMenu · PetDetailMenu · listener/*
       ↓
Application    PetService · ProgressService · AbilityService
       ↓
Domain         PetType · PetData · Rarity · LevelCurve · AbilityDefinition
       ↓
Runtime        ActivePet · PetRegistry · MovementController
               AnimationStateMachine · PetTicker
       ↓
Infrastructure BetterModelBridge ★ · PetRenderHandle
               SqlitePetRepository · ConfigLoader
```

### 패키지 구조

```
kr.qmftm.betterpets
├─ BetterPetsPlugin.java
├─ domain/          PetType, PetData, Rarity, LevelCurve, ability/
├─ runtime/         ActivePet, PetRegistry, PetTicker
│   ├─ movement/    MovementController, MovementState
│   └─ animation/   AnimationStateMachine, AnimationSet
├─ render/          BetterModelBridge ★, PetRenderHandle
├─ service/         PetService, ProgressService, AbilityService
├─ ability/impl/
├─ storage/         PetRepository, SqlitePetRepository, PetCache
├─ config/          MainConfig, PetTypeLoader, Messages
├─ gui/ · command/ · listener/ · integration/
```

### ★ 가장 중요한 원칙 — BetterModelBridge

**BetterModel API 호출은 `render/BetterModelBridge.java` 한 파일 안에서만 일어난다.** 나머지 코드는 BetterModel 타입을 import하지 않는다.

BetterModel은 2.x → 3.x에서 아티팩트 이름 자체가 바뀌었고(`bettermodel` → `bettermodel-bukkit-api`), 멀티플랫폼화로 `BukkitAdapter.adapt()` 경유가 추가됐다. 앞으로도 깨질 수 있다. 브릿지 하나만 고치면 업그레이드가 끝나고, 테스트에서는 가짜 구현으로 교체할 수 있다.

```java
public interface PetRenderer {
    PetRenderHandle spawn(Entity carrier, String modelId);
    void playAnimation(PetRenderHandle handle, String animation, boolean loop);
    void stopAnimation(PetRenderHandle handle, String animation);
    void applyTint(PetRenderHandle handle, int rgb);
    void despawn(PetRenderHandle handle);
}
```

---

## 6. BetterModel 연동 — 검증된 API

> 아래 시그니처는 **`bettermodel-bukkit-api:3.4.1` 소스 JAR에서 직접 확인한 것**이다. 추정이 아니다.

### 진입점

```java
// kr.toxicity.model.api.BetterModel
Optional<ModelRenderer>        BetterModel.model(String name);
Optional<ModelRenderer>        BetterModel.limb(String name);
Optional<EntityTrackerRegistry> BetterModel.registry(PlatformEntity entity);
Set<String>                    BetterModel.modelKeys();   // 로드된 모델 목록 — 설정 검증에 사용
BetterModelEventBus            BetterModel.eventBus();

// kr.toxicity.model.api.bukkit.platform.BukkitAdapter
PlatformEntity   BukkitAdapter.adapt(org.bukkit.entity.Entity entity);
PlatformPlayer   BukkitAdapter.adapt(org.bukkit.entity.Player player);
PlatformLocation BukkitAdapter.adapt(org.bukkit.Location location);
```

### 트래커 생성

```java
// ModelRenderer 의 오버로드 (PlatformEntity / PlatformLocation 기준)
EntityTracker getOrCreate(PlatformEntity entity);
EntityTracker getOrCreate(PlatformEntity entity, TrackerModifier m, Consumer<EntityTracker> preUpdate);
EntityTracker create(PlatformEntity entity, TrackerModifier m, Consumer<EntityTracker> preUpdate);
DummyTracker  create(PlatformLocation location);

// 실제 사용
EntityTracker tracker = BetterModel.model("pet_wolf")
        .map(r -> r.getOrCreate(BukkitAdapter.adapt(carrier)))
        .orElse(null);
```

### 애니메이션

`Tracker`(EntityTracker의 상위 클래스)가 제공한다. **`animate`는 `boolean`을 반환한다** — 실패를 무시하지 말 것.

```java
boolean animate(String animation);
boolean animate(String animation, AnimationModifier modifier);
boolean animate(String animation, AnimationModifier modifier, Runnable onRemove);
boolean stopAnimation(String animation);
boolean replace(String target, String animation, AnimationModifier modifier);
```

`AnimationModifier`:

```java
AnimationModifier.DEFAULT
AnimationModifier.DEFAULT_WITH_PLAY_ONCE

AnimationModifier.builder()
    .type(AnimationIterator.Type.LOOP)   // PLAY_ONCE | LOOP | HOLD_ON_LAST
    .priority(10)                        // 레이어 우선순위 — 오버레이 구현에 사용
    .speed(1.0f)
    .player(platformPlayer)              // 특정 플레이어에게만
    .override(true)
    .build();
```

> `AnimationIterator.Type`은 **`PLAY_ONCE` / `LOOP` / `HOLD_ON_LAST` 세 가지**다. 이동 상태는 `LOOP`, 공격·스킬은 `PLAY_ONCE` + `priority`를 올려 오버레이한다.

### 표시 속성

```java
tracker.update(TrackerUpdateAction.tint(0xFFD700));       // 등급별 색
tracker.update(TrackerUpdateAction.glow(true));
tracker.update(TrackerUpdateAction.glowColor(0x00FF00));
tracker.update(TrackerUpdateAction.brightness(15, 15));
tracker.update(TrackerUpdateAction.viewRange(48f));
tracker.update(TrackerUpdateAction.enchant(true));
tracker.update(TrackerUpdateAction.composite(a, b, c));
```

### 가시성 · 생명주기

```java
boolean hide(PlatformPlayer player);      // 특정 플레이어에게만 숨김
boolean show(PlatformPlayer player);
boolean isHide(PlatformPlayer player);

void despawn();
void close();                             // Tracker implements AutoCloseable
boolean isClosed();
```

`EntityTrackerRegistry`:

```java
EntityTracker tracker(String key);
Collection<EntityTracker> trackers();
boolean close();
static List<EntityTrackerRegistry> registries();   // ★ 전역 진단 — 누수 탐지에 사용
```

> ⚠️ **1순위 위험 — 트래커 누수.** 트래커를 닫지 않으면 유령 모델과 고아 스케줄 태스크가 남아 서버 자원을 갉아먹는다.
>
> `PetRenderHandle`이 `AutoCloseable`을 구현하게 하고, **플레이어 퇴장 · 서버 종료 · 월드 언로드 · 엔티티 사망 네 경로 모두**에서 close를 보장한다. `EntityTrackerRegistry.registries()`로 실제 트래커 수를 세어 `/petadmin debug`에 노출하고, M1 완료 조건에 "누수 0" 검증을 넣는다.

### 캐리어 엔티티

```java
Mob carrier = world.spawn(loc, Allay.class, SpawnReason.CUSTOM, mob -> {
    mob.setAI(false);              // 바닐라 AI 차단 — 이동은 우리가 제어
    mob.setInvisible(true);        // 실제 모델은 BetterModel이 그린다
    mob.setSilent(true);
    mob.setInvulnerable(true);
    mob.setPersistent(false);      // 청크 저장 대상에서 제외
    mob.setCollidable(false);
});
// PDC 태깅 필수 — 비정상 종료 후 남은 캐리어를 시작 시 청소하려면 식별자가 필요하다
carrier.getPersistentDataContainer().set(PET_KEY, PersistentDataType.STRING, petId.toString());
```

**캐리어 방식 비교**

| | 방식 | 판정 |
| --- | --- | --- |
| **A** | 실제 `Mob` + `EntityTracker` | ✅ **채택** — 히트박스·이름표·머리 회전이 기본 동작으로 작동, 클릭 상호작용 무료 |
| B | `DummyTracker` (엔티티 없음) | ❌ 오버헤드는 최소지만 클릭 판정·충돌·이름표를 전부 직접 구현 |
| C | `ArmorStand` 캐리어 | ❌ `Mob`이 아니라 일부 기능에 제약 |

---

## 7. 모델 제작 규격 (직접 제작용)

> 모델은 직접 제작한다. 플러그인은 아래 규격을 기대하므로 BlockBench 작업 시 이 규칙을 따라야 한다.

### 7.1 애니메이션 이름 계약

| 이름 | 필수 | 재생 타입 | 용도 |
| --- | --- | --- | --- |
| `idle` | ✅ | `LOOP` | 정지 상태 |
| `walk` | ✅ | `LOOP` | 걷기 |
| `run` | ✅ | `LOOP` | 달리기 |
| `sit` | | `LOOP` | 대기 명령 |
| `attack` | | `PLAY_ONCE` | 공격 능력 발동 |
| `skill` | | `PLAY_ONCE` | 액티브 스킬 |
| `spawn` | | `PLAY_ONCE` | 소환 연출 |

필수 3종이 없으면 플러그인이 로드 시 경고하고 `idle`로 폴백한다. 이름은 설정 파일에서 재지정할 수 있으므로 다른 이름을 쓰고 싶으면 `animations:` 블록에서 매핑하면 된다.

### 7.2 본 태그 규칙 — 소스에서 확인한 실제 사양

BlockBench에서 **본 이름 앞에 `태그_` 접두사**를 붙이면 특수 기능이 붙는다.

파싱 규칙 (`BoneTagRegistry.parse`):
1. 본 이름을 **소문자로 변환**한다
2. `_`로 자른다
3. **앞에서부터 연속으로** 알려진 태그와 일치하는 조각을 태그로 흡수한다
4. 처음 일치하지 않는 조각부터 끝까지가 실제 본 이름이 된다

즉 `h_head` → 태그 `HEAD` + 이름 `head`, `hi_b_body` → 태그 `HEAD_WITH_CHILDREN`+`HITBOX` + 이름 `body`.
태그는 **반드시 앞쪽에 연속**해야 한다. `body_h`는 태그가 아니다.

**내장 태그 전체 목록:**

| 태그 접두사 | 상수 | 기능 |
| --- | --- | --- |
| `h_` | `HEAD` | 엔티티 머리 회전을 따라감 |
| `hi_` | `HEAD_WITH_CHILDREN` | 머리 회전을 따라감 (자식 본 포함) |
| `b_` / `ob_` | `HITBOX` | 이 본을 따라가는 히트박스 생성 |
| `p_` | `SEAT` | 탑승 좌석 (조종 가능) |
| `sp_` | `SUB_SEAT` | 탑승 좌석 (조종 불가) |
| `tag_` | `TAG` | 이름표 |
| `mtag_` | `MOB_TAG` | 몹 이름표 |
| `ptag_` | `PLAYER_TAG` | 플레이어 이름표 |
| `glow_` | `GLOW` | 발광 |
| `li_` / `pli_` | `LEFT_ITEM` | 왼손 아이템 표시 |
| `ri_` / `pri_` | `RIGHT_ITEM` | 오른손 아이템 표시 |
| `ph_` | `PLAYER_HEAD` | 플레이어 머리 |
| `pra_` / `prfa_` | `PLAYER_RIGHT_ARM` / `_FOREARM` | 플레이어 오른팔 / 아래팔 |
| `pla_` | `PLAYER_LEFT_ARM` | 플레이어 왼팔 |

### 7.3 펫 모델 권장 구성

```
pet_wolf.bbmodel
├─ h_head        ← 소유자를 바라보게 하려면 HEAD 태그 필요
├─ b_body        ← 클릭 상호작용용 히트박스
│   ├─ leg_fl
│   ├─ leg_fr
│   ├─ leg_bl
│   └─ leg_br
└─ tag_name      ← 펫 이름표 위치 (선택)
```

- `.bbmodel` 파일은 서버의 `plugins/BetterModel/models/` 에 배치한다
- 모델 파일명(확장자 제외)이 곧 설정 파일의 `model:` 값이다
- 리소스팩은 BetterModel이 생성하며, 배포 경로는 서버 설정에 따른다

> ⚠️ **저작권** — 악어의 놀이터 2에서 사용 중인 실제 모델·텍스처를 추출해 쓰는 것은 침해다. 시스템 구조를 참고하는 것과 에셋을 복제하는 것은 다르다.

---

## 8. 펫 행동 설계

### 이동 상태 머신

| 상태 | 조건 | 동작 | 애니메이션 |
| --- | --- | --- | --- |
| `IDLE` | 거리 < 2.5 | 정지, 소유자 방향 머리 회전 | `idle` (LOOP) |
| `WALK` | 2.5 ≤ 거리 < 8 | 보간 이동 (0.25) | `walk` (LOOP) |
| `RUN` | 8 ≤ 거리 < 24 | 보간 이동 (0.45) | `run` (LOOP) |
| `TELEPORT` | 거리 ≥ 24 · 다른 월드 · 3초 이상 경로 실패 | 소유자 뒤로 즉시 이동 + 파티클 → `IDLE` | — |

**목표 지점**은 소유자 위치에서 시야 반대 방향으로 `follow-distance`(기본 2.0블록) 떨어진 지점. 소유자가 제자리 회전할 때 펫이 따라 도는 것을 막기 위해 **데드존 0.8블록**을 둔다.

`setAI(false)`이므로 바닐라 경로탐색을 쓰지 않는다. 방향 벡터 → 속도 상한 → 간이 지형 처리(앞 1블록이 막히고 위가 비었으면 `y += 0.5`, 아래가 비었으면 최대 3블록 하강) → 위치 갱신. 물·용암·공허 감지 시 즉시 `TELEPORT`.

> **M2에서 실측 확정할 항목** — `setVelocity()` vs 위치 보간 후 `teleport()` 중 어느 쪽이 클라이언트에서 부드러운지. 그리고 바닐라 `Pathfinder` 대안과의 프로토타입 비교.

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

- **펫당 개별 스케줄 태스크 금지** — 태스크 수가 동접에 비례해 폭증한다
- **오프라인 소유자의 펫은 즉시 디스폰**하고 틱 대상에서 제외
- **Folia 대응** — 감지 시 `EntityScheduler`로 전환. 이 분기는 `PetTicker` 안에 캡슐화

### 애니메이션 상태 머신

```java
void tick() {
    AnimationState next = resolve(movement.state());
    if (next == current) return;   // ★ 변화 없으면 아무것도 하지 않는다
    tracker.stopAnimation(current.animationName());
    tracker.animate(next.animationName(), LOOP_MODIFIER);
    current = next;
}
```

일회성 애니메이션(`attack`, `skill`)은 `priority`를 올린 `PLAY_ONCE` 모디파이어로 **오버레이**하고, `animate(..., Runnable onRemove)` 콜백에서 상태를 복원한다.

---

## 9. 데이터 모델과 영속화

### 도메인

```java
// 펫 "종류" — 설정에서 로드하는 불변 객체
public record PetType(
        String id, String displayName, String modelId,
        Rarity rarity, AnimationSet animations, MovementProfile movement,
        List<AbilityDefinition> abilities, int maxLevel,
        String evolvesInto        // nullable
) {}

// 펫 "개체" — DB 저장 대상
public final class PetData {
    private final UUID petId;      // PK
    private final UUID ownerId;
    private final String typeId;
    private String nickname;       // nullable
    private int level;
    private long experience;
    private boolean active;
}
```

### 스키마

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

-- 소유자당 활성 펫 1마리를 DB에서 강제한다
CREATE UNIQUE INDEX IF NOT EXISTS idx_bp_pet_active
    ON bp_pet (owner_id) WHERE active = 1;

CREATE TABLE IF NOT EXISTS bp_meta (
    key VARCHAR(64) NOT NULL PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);
```

### 원칙

- **모든 DB I/O는 비동기.** 메인 스레드에서 JDBC 호출 금지 — 예외 없음
- **write-behind 캐시** — 접속 시 로드 → 메모리 읽기/쓰기 → dirty 플래그 → 5분 주기 · 퇴장 시 · 종료 시 flush
- **서버 종료 시 flush는 동기**로 수행한다. 비동기면 데이터를 잃는다
- `bp_meta.schema_version`으로 버전을 추적하고 순차 마이그레이션 적용

---

## 10. 성장 시스템

### 등급

| 등급 | 능력 배율 | 최대 레벨 | 색상 |
| --- | --- | --- | --- |
| `COMMON` 일반 | 1.0 | 30 | 회색 |
| `RARE` 희귀 | 1.3 | 50 | 파랑 |
| `EPIC` 영웅 | 1.7 | 70 | 보라 |
| `LEGENDARY` 전설 | 2.2 | 100 | 금색 |

> 수치는 **플레이스홀더**다. 실제 밸런싱은 M5에서 별도로 잡는다 (Q6).

### 경험치 곡선

```
requiredExp(level) = base × level^exponent      (base=100, exponent=1.5)

Lv1→2: 100 · Lv10→11: 3,162 · Lv50→51: 35,355
```

파라미터는 설정으로 노출. `LevelCurve`는 순수 함수로 만들어 단위 테스트한다.

### 획득 경로

| 경로 | 조건 |
| --- | --- |
| 몬스터 처치 | 소유자가 처치, 펫이 32블록 내 |
| 블록 채굴 | **설치 블록 제외** |
| 먹이 아이템 | 우클릭 소비 |
| 시간 경과 | 선택적, 기본 비활성 |

> ⚠️ **어뷰징 방지 필수** — 채굴 경험치는 플레이어가 설치한 블록을 제외해야 한다. 설치 시 PDC/블록 메타 태깅 또는 CoreProtect 연동. 없으면 흙블록 무한 설치/파괴로 즉시 만렙이 된다.

### 진화

`evolvesInto`가 설정된 레벨 도달 시 안내 → GUI에서 확정하면 `type_id` 교체 + 모델 교체 + 레벨 리셋(설정 가능).

---

## 11. 능력 시스템

| 유형 | 발동 | 구현 | 예시 |
| --- | --- | --- | --- |
| `PASSIVE` | 소환 중 상시 | `AttributeModifier` 부착 | 이동속도 +10%, 최대체력 +4 |
| `TRIGGER` | 특정 이벤트 | 리스너 + 확률 판정 | 피격 시 20% 회복, 처치 시 추가 드랍 |
| `ACTIVE` | 수동 / 쿨다운 | 쿨다운 맵 + 스킬 실행 | 30초마다 주변 몹 넉백 |

```java
public interface PetAbility {
    AbilityType type();
    void onEquip(AbilityContext ctx);      // 펫 소환 시
    void onUnequip(AbilityContext ctx);    // 펫 해제 시 — 반드시 원복
    default void onTrigger(AbilityContext ctx, Event event) {}
}
```

`AbilityRegistry`에 id → 팩토리를 등록하고 설정에서 id로 참조한다. **새 능력 추가 = 클래스 1개 + 등록 1줄.**

> ⚠️ **흔한 치명적 버그 — 모디파이어 누적.** 재접속·펫 교체·리로드마다 `AttributeModifier`가 쌓이면 플레이어가 로켓처럼 날아간다. **부착 전 항상 같은 키의 모디파이어를 제거**하고 접속 시점에도 청소한다. `onUnequip`은 `onEquip`의 정확한 역연산이어야 한다.

---

## 12. GUI

| 화면 | 크기 | 내용 |
| --- | --- | --- |
| 펫 보관함 | 6줄 | 소유 펫 목록(페이지네이션), 등급별 테두리, 장착 표시 |
| 펫 상세 | 3줄 | 레벨/경험치, 능력 목록, [장착] [이름변경] [해방] |
| 진화 확인 | 3줄 | 전후 비교, [확정] [취소] |
| 뽑기 | 3줄 | 비용, [뽑기], 결과 연출 |

- `InventoryHolder`를 구현한 커스텀 홀더로 GUI를 식별한다. **제목 문자열 비교 금지** (색코드·번역으로 깨진다)
- `InventoryClickEvent`에서 GUI면 **무조건 `setCancelled(true)` 먼저** 호출한 뒤 로직 실행. 아이템 복제 취약점 대부분이 여기서 발생한다
- 이름 변경 입력은 **타임아웃(30초)과 취소 경로**를 반드시 제공

---

## 13. 명령어와 권한

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/pet` | `betterpets.use` | 보관함 GUI |
| `/pet summon <petId>` | `betterpets.use` | 소환 |
| `/pet dismiss` | `betterpets.use` | 해제 |
| `/pet rename <이름>` | `betterpets.use` | 이름 변경 |
| `/petadmin give <플레이어> <타입>` | `betterpets.admin` | 지급 |
| `/petadmin remove <플레이어> <petId>` | `betterpets.admin` | 회수 |
| `/petadmin exp <플레이어> <양>` | `betterpets.admin` | 경험치 지급 |
| `/petadmin reload` | `betterpets.admin` | 설정 리로드 |
| `/petadmin debug` | `betterpets.admin` | **누수 진단** |

`/petadmin debug`는 활성 펫 수 · 살아있는 캐리어 엔티티 수 · `EntityTrackerRegistry.registries()` 기준 트래커 수를 출력해 **세 숫자가 일치하는지** 확인한다.

---

## 14. 설정 파일

```
plugins/BetterPets/
├─ config.yml       일반 설정, DB, 성능 튜닝
├─ messages.yml     모든 사용자 노출 문자열 (한국어)
├─ gui.yml          GUI 레이아웃
└─ pets/
    ├─ wolf.yml
    └─ dragon.yml
```

```yaml
# pets/wolf.yml
id: wolf_alpha
display-name: "&f알파 늑대"
model: pet_wolf              # plugins/BetterModel/models/pet_wolf.bbmodel
rarity: RARE
max-level: 50
evolves-into: wolf_fenrir

animations:                  # 모델의 실제 애니메이션 이름과 매핑
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
  from-kill: { ZOMBIE: 10, SKELETON: 12 }
  from-mining: { DIAMOND_ORE: 50 }
```

**설정 검증**: 로드 시 필수 필드 누락, 존재하지 않는 모델 참조(`BetterModel.modelKeys()`로 대조), 미등록 능력 id를 **모두 수집해 한 번에 보고**한다. 첫 오류에서 멈추지 않는다.

---

## 15. 성능

| 지표 | 목표 |
| --- | --- |
| 활성 펫 100마리 기준 메인 스레드 부하 | < 1ms/tick |
| 접속 시 펫 데이터 로드 | < 50ms (비동기) |
| 메모리 (펫 100마리) | < 20MB |

**최적화 원칙**

1. 전역 틱 루프 1개. 펫당 태스크 금지
2. 애니메이션은 상태 전이 시에만 호출
3. 거리 기반 LOD — 관전자가 없으면 이동 갱신 주기를 2틱 → 10틱으로
4. 오프라인 소유자 펫 즉시 디스폰
5. DB I/O 전량 비동기 + write-behind 캐시

> 목표치는 **추정이며 측정된 값이 아니다.** M7에서 Spark로 실측하기 전까지 달성 여부를 주장하지 않는다.

---

## 16. 테스트

| 레벨 | 대상 | 도구 |
| --- | --- | --- |
| 단위 | `LevelCurve`, 등급 배율, 능력 수치, 설정 파서 | JUnit 5 |
| 통합 | Repository CRUD | JUnit + 인메모리 SQLite |
| 수동 | 렌더링 · 애니메이션 · 이동 | Paper 테스트 서버 |

**수동 체크리스트 (매 마일스톤 반복)**

- [ ] 소환 → 로그아웃 → 재접속 시 유령 모델이 남지 않는가
- [ ] 소환 상태로 서버 재시작 시 캐리어 엔티티가 청소되는가
- [ ] 월드 이동 / 네더 포탈 통과 시 펫이 따라오는가
- [ ] 펫 교체 20회 반복 시 `AttributeModifier`가 누적되지 않는가
- [ ] `/petadmin debug`의 세 숫자가 일치하는가
- [ ] 펫 100마리 소환 상태에서 TPS 20을 유지하는가
- [ ] BetterModel을 제거한 상태에서 플러그인이 안전하게 비활성화되는가

---

## 17. 로드맵

| M | 마일스톤 | 산출물 | 예상 |
| --- | --- | --- | --- |
| **M0** | 프로젝트 뼈대 | Gradle · plugin.yml · CI · 패키지 구조 · **JDK 25 + BetterModel 의존성 컴파일 확인** | 3일 |
| **M1** | 렌더링 수직 슬라이스 | `/pet summon`으로 모델 소환/해제 · `BetterModelBridge` · **트래커 누수 0 검증** | 1.5주 |
| **M2** | 이동 + 애니메이션 | 추종 상태 머신 · 텔레포트 폴백 · 애니메이션 상태 머신 · 이동 방식 A/B 실측 | 2주 |
| **M3** | 데이터 + 명령어 | `PetRepository`(SQLite) · 캐시 · 전체 명령어/권한 | 1.5주 |
| **M4** | GUI | 보관함 · 상세 · 장착/해제/이름변경/해방 | 1.5주 |
| **M5** | 성장 + 능력 | 경험치/레벨 · 3종 능력 시스템 · 능력 3~5개 | 2주 |
| **M6** | 획득 + 진화 | 알 아이템 · 뽑기 · 진화 | 1.5주 |
| **M7** | 최적화 + 폴리시 | Spark 프로파일링 · Folia 대응 · PlaceholderAPI · 문서화 | 1.5주 |

**합계 약 11.5주** (1인 파트타임, 모델 제작 시간 제외)

**병행**: 7장 규격에 맞춘 모델 제작을 M1과 동시에 진행해야 M2 진입 시 테스트 모델이 준비된다.

### 마일스톤 완료 조건

1. 기능이 테스트 서버에서 동작한다
2. 해당 범위의 단위 테스트가 통과한다
3. 16장 수동 체크리스트를 통과한다
4. README를 갱신한다
5. 브랜치에 커밋·푸시한다

---

## 18. 리스크

| # | 리스크 | 영향 | 확률 | 대응 |
| --- | --- | --- | --- | --- |
| ~~R1~~ | ~~Java 25 요구 충돌~~ | — | — | ✅ **해소** — Java 25 확정 |
| **R2** | 트래커 누수 → 유령 모델 / TPS 저하 | 높음 | 중 | `AutoCloseable` + 4경로 close + `registries()` 진단 + M1 DoD |
| **R3** | BetterModel 버전 업 시 API 파괴 | 중 | 높음 | `BetterModelBridge` 단일 격리 계층. 버전 핀 고정 |
| **R4** | 모델 에셋 저작권 | 높음 | 낮음 | 직접 제작 확정으로 완화. 원본 복제 금지 원칙 유지 |
| **R5** | `AttributeModifier` 누적 버그 | 중 | 높음 | 부착 전 항상 제거 + 접속 시 청소 + 반복 테스트 항목화 |
| **R6** | 채굴 경험치 어뷰징 | 중 | 높음 | 설치 블록 태깅 또는 CoreProtect 연동 |
| **R7** | 모델 제작 지연으로 M2 블로킹 | 중 | 중 | M1과 병렬 착수. 임시 대체 모델 확보 |
| **R8** | Folia 스케줄러 차이 | 중 | 낮음 | `PetTicker`에 분기 캡슐화. M7 검증 |
| **R9** | GUI 아이템 복제 취약점 | 높음 | 낮음 | `setCancelled(true)` 선행 + 코드 리뷰 체크 |
| **R10** | **JDK 25 빌드 환경 부재** | 중 | 중 | 개발 머신·CI 모두 JDK 25 설치 필요. M0의 첫 작업 |

---

## 19. 참고 자료

- [BetterModel — GitHub](https://github.com/toxicity188/BetterModel)
- [BetterModel — API example (Wiki)](https://github.com/toxicity188/BetterModel/wiki/API-example)
- [BetterModel — Configuring bone tag](https://github.com/toxicity188/BetterModel/wiki/Configuring-bone-tag)
- [BetterModel — Create your own model using BlockBench](https://github.com/toxicity188/BetterModel/wiki/Create-your-own-model-using-BlockBench)
- [BetterModel — Hangar (PaperMC)](https://hangar.papermc.io/toxicity188/BetterModel)
- [BetterModel — 공식 문서](https://mintlify.wiki/toxicity188/BetterModel/introduction)

---

## 문서 이력

| 버전 | 날짜 | 변경 |
| --- | --- | --- |
| v1.0 | 2026-09-08 | 최초 작성 |
| v1.1 | 2026-09-08 | Java 25 / MC 26.2 확정 (R1 해소) · 모델 직접 제작 확정 · **소스 JAR로 API 시그니처 검증** · **본 태그 규칙 확정 (7.2)** · paper-api 좌표 정정 · R10 추가 |
