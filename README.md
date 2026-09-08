# BetterPets

악어의 놀이터 스타일 펫 플러그인 — [BetterModel](https://github.com/toxicity188/BetterModel) 기반 Paper 플러그인

| | |
| --- | --- |
| 계획서 버전 | **v2.0** (2026-09-08) |
| 대상 | Paper / Minecraft **26.2** / Java **25** |
| BetterModel | **3.4.1** (MIT) |
| 빌드 | **Maven** |
| 상태 | 기획 확정, 구현 착수 전 |
| 예상 기간 | 약 16주 (1인 파트타임) |

---

## 목차

- [1. 요약](#1-요약)
- [2. 원작 시스템 분석](#2-원작-시스템-분석)
- [3. 목표와 범위](#3-목표와-범위)
- [4. 전제 조건과 빌드](#4-전제-조건과-빌드)
- [5. 남은 확인 사항](#5-남은-확인-사항)
- [6. 아키텍처](#6-아키텍처)
- [7. BetterModel 연동 — 검증된 API](#7-bettermodel-연동--검증된-api)
- [8. 모델 제작 규격](#8-모델-제작-규격)
- [9. 펫 생애주기](#9-펫-생애주기)
- [10. 행동 설계](#10-행동-설계)
- [11. 탑승과 비행](#11-탑승과-비행)
- [12. 데이터 모델과 영속화](#12-데이터-모델과-영속화)
- [13. 등급과 능력](#13-등급과-능력)
- [14. 획득 경로](#14-획득-경로)
- [15. GUI](#15-gui)
- [16. 명령어와 권한](#16-명령어와-권한)
- [17. 설정 파일](#17-설정-파일)
- [18. 성능 — 최우선 제약](#18-성능--최우선-제약)
- [19. 테스트](#19-테스트)
- [20. 로드맵](#20-로드맵)
- [21. 리스크](#21-리스크)
- [22. 참고 자료](#22-참고-자료)

---

## 1. 요약

플레이어가 **알을 부화시켜** 펫을 키우고, 성장한 펫을 **타고 다니며**(일부는 **하늘을 날며**), 등급에 따라 성능이 달라지는 시스템.

**핵심 설계 결정 5가지:**

| | 결정 | 근거 |
| --- | --- | --- |
| **렌더링 캐리어** | 보이지 않는 `Mob` + `EntityTracker` | `DummyTracker`는 클릭 판정·이름표·탑승을 전부 직접 구현해야 한다 |
| **탑승** | BetterModel `SEAT`(`p_`) 본 + `MountedHitBox` | 원작의 핵심 기능. BetterModel이 네이티브로 지원한다 (7장) |
| **이동** | `setAI(false)` 후 자체 컨트롤러 | 지상/비행/탑승 3가지 모드를 한 컨트롤러에서 전환해야 한다 |
| **애니메이션** | 상태 전이 시점에만 `animate()` 호출 | `animate()`는 패킷을 만든다. **원작이 렉으로 실패한 지점이다** (2장) |
| **영속화** | SQLite 기본 + `PetRepository` 추상화 | MySQL 교체 가능. 모든 I/O 비동기, write-behind 캐시 |

---

## 2. 원작 시스템 분석

> 나무위키 문서는 직접 접근이 차단(403)되어 **검색 결과 요약을 통해** 확인했다. 여러 검색에서 일관되게 나온 내용만 정리했으나, **원문 대조는 하지 못했다.** 실제 사양과 다를 수 있으니 확인해주면 반영한다.

### 시즌 1 — 악어의 놀이터

| 항목 | 내용 |
| --- | --- |
| 등급 | **D ~ S** (D·C·B·A·S 5단계로 추정) |
| 탑승 | **가능** — 펫을 탈것으로 사용 |
| 이동속도 | **등급이 오를수록 빨라짐** |
| 비행 | **A등급부터 드물게** 비행 펫 등장 (통칭 "날탈") |
| 비행 특성 | 폭죽+겉날개 글라이딩보다 **느리지만**, 재화 소모 없이 **무한 비행** |
| 획득 | **펫 뽑기권** 가챠 — 놀이터 코인 5개 또는 10만 골드로 구매 |
| 연계 | S등급 드래곤 최초 획득자는 히든직업 '드래곤 워리어' 전직 |

### 시즌 2 — 악어의 놀이터 2 (변경점)

| 항목 | 내용 |
| --- | --- |
| 획득 | **알 상태로 제공.** 동물조련사 NPC에게 알을 구매 |
| 부화 | 우유 같은 **펫 전용 음식**을 먹여 부화시킴 |
| 성장도 | **1분에 1씩 자동 상승** |
| 먹이 | 동물조련사가 파는 **우유 사용 시 한 번에 +10** |
| 탑승 | 잘 키운 펫을 탈것으로 사용. **확률에 따라 비행 펫** |
| 기믹 | 아기 펫에게 **먹이를 한꺼번에 많이 주면 탈것 '돼지'로 진화** |

### ⚠️ 결정적 교훈 — 원작은 성능 때문에 실패했다

> 서버 오픈 **6일차에 운영진이 렉을 이유로 펫 소환 자제를 요청**했고, 여러 방송인이 방송 중 불만을 표시했다.

이건 이 프로젝트의 **가장 중요한 입력**이다. 펫 시스템의 실패 지점은 기능 부족이 아니라 **성능**이었다. 따라서:

- 성능은 "나중에 최적화할 항목"이 아니라 **1순위 설계 제약**이다 (18장, R1)
- "동시 활성 펫 N마리에서 TPS 20 유지"를 **각 마일스톤의 완료 조건**에 넣는다
- 전역 틱 루프·상태 전이 시에만 애니메이션·거리 LOD는 선택이 아니라 필수다

### 원작 대비 이 설계의 차이

| 항목 | 원작 | 본 설계 | 이유 |
| --- | --- | --- | --- |
| 등급 | D~S | **D~S 채택** | 원작 준수 |
| 성장 | 성장도 (시간+먹이) | **성장도 채택** + 선택적 경험치 | 원작 준수, 확장 여지 |
| 탑승 | 핵심 기능 | **핵심 기능으로 승격** | v1.x에서 범위 제외했던 것을 정정 |
| 비행 | A등급부터 확률 | **채택** | 원작 준수 |
| 획득 | NPC 알 구매 + 뽑기 | **둘 다 지원** | 설정으로 선택 |
| 능력 | 이동속도 위주 | **패시브/트리거/액티브 확장** | 원작보다 확장. 끄면 원작과 동일 |

---

## 3. 목표와 범위

### 포함 기능

| # | 기능 | 마일스톤 |
| --- | --- | --- |
| F1 | 펫 소환 / 해제 / 모델 렌더링 | M1 |
| F2 | 추종 이동 · 텔레포트 · 상태 애니메이션 | M2 |
| F3 | 데이터 영속화 · 명령어 · 권한 | M3 |
| F4 | **생애주기 — 알 → 부화 → 아기 → 성체** | M4 |
| F5 | **성장도 — 시간 경과 + 먹이** | M4 |
| F6 | **탑승 (라이딩)** | M5 |
| F7 | **비행 펫 (A등급 이상)** | M5 |
| F8 | 펫 보관함 및 상세 GUI | M6 |
| F9 | 등급(D~S) 시스템 · 능력 | M7 |
| F10 | 획득 — NPC 알 상점, 뽑기권 가챠 | M8 |
| F11 | 진화 (과급식 기믹 포함) | M8 |
| F12 | PlaceholderAPI · 최적화 | M9 |

### v1.0 제외

- 펫 간 전투 / PvP 펫 대전
- 펫 거래 시장 (경매장)
- 히든직업 전직 연계 — 별도 플러그인 영역
- 웹 대시보드

---

## 4. 전제 조건과 빌드

BetterModel 3.4.1 **소스 JAR을 직접 내려받아 검증한** 값이다.

| 항목 | 값 | 확인 방법 |
| --- | --- | --- |
| Minecraft | `26.2` | 3.4.1 태그의 `gradle.properties` |
| Java | `25` | 배포 모듈 메타데이터 `org.gradle.jvm.version: 25` |
| BetterModel | `3.4.1` (2026-08-11) | Maven Central 최신 릴리스, MIT |
| 아티팩트 | `io.github.toxicity188:bettermodel-bukkit-api` | 2.x의 `bettermodel`에서 **이름이 바뀌었다** |
| API 패키지 | `kr.toxicity.model.api` | 소스 JAR 확인 |
| paper-api | `io.papermc.paper:paper-api:26.2.build.121-stable` | **버전 체계가 바뀌었다.** 더 이상 `1.21.x-R0.1-SNAPSHOT`이 아니다 |

### 빌드

빌드 도구는 **Maven**이다. 루트의 [`pom.xml`](pom.xml) 참고.

```bash
mvn clean package        # target/BetterPets-0.1.0-SNAPSHOT.jar
mvn test                 # 단위 테스트만
```

핵심 설정:

```xml
<properties>
  <maven.compiler.release>25</maven.compiler.release>
  <paper.version>26.2.build.121-stable</paper.version>
  <bettermodel.version>3.4.1</bettermodel.version>
</properties>

<repositories>
  <repository><id>papermc</id>   <url>https://repo.papermc.io/repository/maven-public/</url></repository>
  <!-- BetterModel 의 전이 의존성. 빼면 해결에 실패한다 -->
  <repository><id>blamejared</id><url>https://maven.blamejared.com/</url></repository>
  <repository><id>nucleoid</id>  <url>https://maven.nucleoid.xyz/</url></repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.github.toxicity188</groupId>
    <artifactId>bettermodel-bukkit-api</artifactId>
    <version>3.4.1</version>
    <scope>provided</scope>
  </dependency>
  ...
</dependencies>
```

`maven-shade-plugin`으로 SQLite/HikariCP를 `kr.qmftm.betterpets.lib.*` 로 **재배치(relocate)** 한다. 다른 플러그인이 같은 라이브러리를 셰이딩했을 때 충돌하지 않기 위해서다.

```yaml
# plugin.yml
name: BetterPets
main: kr.qmftm.betterpets.BetterPetsPlugin
api-version: '1.21'
depend: [BetterModel]
softdepend: [Vault, PlaceholderAPI, MythicMobs, Citizens]
```

> ⚠️ **BetterModel 3.4.1은 Java 25 전용이다.** 클래스 파일 버전 69라서 JDK 24 이하로는 컴파일조차 되지 않는다. 빌드 머신과 운영 서버 모두 JDK 25가 필요하다.

---

## 5. 남은 확인 사항

| # | 질문 | 현재 가정 | 필요 시점 |
| --- | --- | --- | --- |
| ~~Q1~~ | ~~JDK 버전~~ | ✅ **Java 25 / MC 26.2 확정** | — |
| ~~Q3~~ | ~~모델 에셋~~ | ✅ **직접 제작 (8장이 규격)** | — |
| ~~Q6~~ | ~~원작 시스템 사양~~ | ✅ **조사 완료 (2장)** — 원문 대조는 미완 | — |
| **Q2** | 예상 동시 접속자 수는? | 50~100명 | **M1 전** (성능 목표 기준선) |
| **Q4** | 기존 MySQL DB가 있는가? | 없음 — SQLite 기본 | M3 전 |
| **Q5** | Vault(경제)를 쓰는가? | 사용 | M8 전 |
| **Q7** | **NPC는 무엇으로 만드나?** | Citizens 연동 또는 자체 구현 | M8 전 |
| **Q8** | **등급별 이동속도·비행 확률 수치는?** | 13장 플레이스홀더 | M7 전 |
| **Q9** | **비행 펫의 조작 방식은?** | 겉날개식 활공 vs 자유 비행 | M5 전 |

> Q2가 M1 전으로 올라갔다. 원작이 성능으로 실패했기 때문에 **"몇 마리에서 TPS 20을 지켜야 하는가"** 를 처음부터 알아야 한다.

---

## 6. 아키텍처

의존 방향은 항상 안쪽(Domain)을 향한다. **Domain은 Bukkit API를 모른다** — 순수 데이터와 규칙만 담아 단위 테스트가 가능하게 한다.

```
Presentation   /pet · /petadmin · PetBoxMenu · PetDetailMenu · listener/*
       ↓
Application    PetService · GrowthService · RideService · AbilityService
       ↓
Domain         PetType · PetData · Rarity · LifeStage · GrowthCurve
       ↓
Runtime        ActivePet · PetRegistry · PetTicker
               MovementController (GROUND / FLY / RIDDEN)
               AnimationStateMachine
       ↓
Infrastructure BetterModelBridge ★ · PetRenderHandle
               SqlitePetRepository · ConfigLoader
```

### 패키지 구조

```
kr.qmftm.betterpets
├─ BetterPetsPlugin.java
├─ domain/          PetType, PetData, Rarity, LifeStage, GrowthCurve, ability/
├─ runtime/         ActivePet, PetRegistry, PetTicker
│   ├─ movement/    MovementController, MovementMode, GroundMover, FlightMover
│   ├─ ride/        RideController, SeatBinding
│   └─ animation/   AnimationStateMachine, AnimationSet
├─ render/          BetterModelBridge ★, PetRenderHandle
├─ service/         PetService, GrowthService, RideService, AbilityService
├─ ability/impl/
├─ storage/         PetRepository, SqlitePetRepository, PetCache
├─ config/          MainConfig, PetTypeLoader, Messages
├─ gui/ · command/ · listener/ · integration/
```

### ★ 가장 중요한 원칙 — BetterModelBridge

**BetterModel API 호출은 `render/BetterModelBridge.java` 한 파일 안에서만 일어난다.** 나머지 코드는 BetterModel 타입을 import하지 않는다.

BetterModel은 2.x → 3.x에서 아티팩트 이름 자체가 바뀌었고(`bettermodel` → `bettermodel-bukkit-api`), 멀티플랫폼화로 `BukkitAdapter.adapt()` 경유가 추가됐다. 브릿지 하나만 고치면 업그레이드가 끝나고, 테스트에서는 가짜 구현으로 교체할 수 있다.

---

## 7. BetterModel 연동 — 검증된 API

> 아래 시그니처는 **`bettermodel-bukkit-api:3.4.1` 소스 JAR에서 직접 확인한 것**이다. 추정이 아니다.

### 진입점

```java
// kr.toxicity.model.api.BetterModel
Optional<ModelRenderer>         BetterModel.model(String name);
Optional<EntityTrackerRegistry> BetterModel.registry(PlatformEntity entity);
Set<String>                     BetterModel.modelKeys();   // 설정 검증에 사용
BetterModelEventBus             BetterModel.eventBus();

// kr.toxicity.model.api.bukkit.platform.BukkitAdapter
PlatformEntity   BukkitAdapter.adapt(org.bukkit.entity.Entity entity);
PlatformPlayer   BukkitAdapter.adapt(org.bukkit.entity.Player player);
PlatformLocation BukkitAdapter.adapt(org.bukkit.Location location);
```

### 트래커 생성

```java
EntityTracker getOrCreate(PlatformEntity entity);
EntityTracker create(PlatformEntity entity, TrackerModifier m, Consumer<EntityTracker> preUpdate);
DummyTracker  create(PlatformLocation location);

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

AnimationModifier.DEFAULT
AnimationModifier.DEFAULT_WITH_PLAY_ONCE

AnimationModifier.builder()
    .type(AnimationIterator.Type.LOOP)   // PLAY_ONCE | LOOP | HOLD_ON_LAST — 3종뿐
    .priority(10)                        // 오버레이 레이어 구현에 사용
    .speed(1.0f)
    .player(platformPlayer)              // 특정 플레이어에게만
    .build();
```

### ★ 탑승 — 원작 핵심 기능의 API 근거

`EntityTrackerRegistry`가 좌석 마운트를 네이티브로 지원한다. **직접 구현할 필요가 없다.**

```java
Map<UUID, MountedHitBox> mountedHitBox();
Collection<HitBox>       hitBoxes();
boolean                  hasPassenger();
boolean                  hasControllingPassenger();   // 조종 중인 탑승자 여부

public record MountedHitBox(PlatformEntity entity, HitBox hitBox) {
    void dismount();
    void dismountAll();
}
```

모델 쪽에서는 `p_`(SEAT, 조종 가능) 또는 `sp_`(SUB_SEAT, 조종 불가) 본 태그를 붙인다. 8장 참고.

> **M5의 첫 작업은 이 API로 실제 탑승이 되는지 검증하는 것이다.** 되면 탑승 구현이 크게 줄고, 안 되면 `addPassenger` 기반 자체 구현으로 전환한다. 이 분기가 M5 일정의 최대 변수다.

### 표시 속성 · 생명주기

```java
tracker.update(TrackerUpdateAction.tint(0xFFD700));    // 등급별 색
tracker.update(TrackerUpdateAction.glow(true));
tracker.update(TrackerUpdateAction.glowColor(0x00FF00));
tracker.update(TrackerUpdateAction.viewRange(48f));    // ★ 성능 — 렌더 거리 제한
tracker.update(TrackerUpdateAction.composite(a, b, c));

boolean hide(PlatformPlayer player);   // 특정 플레이어에게만 숨김
boolean show(PlatformPlayer player);

void    despawn();
void    close();                       // Tracker implements AutoCloseable
boolean isClosed();

// EntityTrackerRegistry
EntityTracker tracker(String key);
boolean       close();
static List<EntityTrackerRegistry> registries();   // ★ 전역 진단 — 누수 탐지
```

> ⚠️ **트래커 누수.** 트래커를 닫지 않으면 유령 모델과 고아 스케줄 태스크가 남는다. `PetRenderHandle`이 `AutoCloseable`을 구현하게 하고, **플레이어 퇴장 · 서버 종료 · 월드 언로드 · 엔티티 사망 네 경로 모두**에서 close를 보장한다. `EntityTrackerRegistry.registries()`로 실제 트래커 수를 세어 `/petadmin debug`에 노출한다.

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

| | 방식 | 판정 |
| --- | --- | --- |
| **A** | 실제 `Mob` + `EntityTracker` | ✅ **채택** — 히트박스·이름표·**탑승**이 기본 동작으로 작동 |
| B | `DummyTracker` (엔티티 없음) | ❌ 탑승·클릭 판정·이름표를 전부 직접 구현 |
| C | `ArmorStand` 캐리어 | ❌ `Mob`이 아니라 일부 기능에 제약 |

---

## 8. 모델 제작 규격

> 모델은 직접 제작한다. 플러그인은 아래 규격을 기대하므로 BlockBench 작업 시 이 규칙을 따라야 한다.

### 8.1 애니메이션 이름 계약

| 이름 | 필수 | 재생 타입 | 용도 |
| --- | --- | --- | --- |
| `idle` | ✅ | `LOOP` | 정지 |
| `walk` | ✅ | `LOOP` | 걷기 |
| `run` | ✅ | `LOOP` | 달리기 |
| `fly` | 비행 펫 필수 | `LOOP` | 비행 |
| `fly_idle` | | `LOOP` | 공중 정지(호버링) |
| `ride` | 탑승 펫 필수 | `LOOP` | 탑승 중 이동 |
| `egg` | | `LOOP` | 알 상태 |
| `hatch` | | `PLAY_ONCE` | 부화 연출 |
| `eat` | | `PLAY_ONCE` | 먹이 섭취 |
| `sit` | | `LOOP` | 대기 명령 |
| `attack` | | `PLAY_ONCE` | 공격 능력 |
| `spawn` | | `PLAY_ONCE` | 소환 연출 |

필수 항목이 없으면 로드 시 경고하고 `idle`로 폴백한다. 이름은 설정의 `animations:` 블록에서 재매핑할 수 있다.

### 8.2 본 태그 규칙 — 소스에서 확인한 실제 사양

BlockBench에서 **본 이름 앞에 `태그_` 접두사**를 붙이면 특수 기능이 붙는다.

파싱 규칙 (`BoneTagRegistry.parse`):
1. 본 이름을 **소문자로 변환**
2. `_`로 분리
3. **앞에서부터 연속으로** 알려진 태그와 일치하는 조각을 태그로 흡수
4. 처음 일치하지 않는 조각부터 끝까지가 실제 본 이름

`h_head` → 태그 `HEAD` + 이름 `head` · `hi_b_body` → 태그 `HEAD_WITH_CHILDREN`+`HITBOX` + 이름 `body`
태그는 **반드시 앞쪽에 연속**해야 한다. `body_h`는 태그가 아니다.

| 접두사 | 상수 | 기능 |
| --- | --- | --- |
| `h_` | `HEAD` | 엔티티 머리 회전을 따라감 |
| `hi_` | `HEAD_WITH_CHILDREN` | 머리 회전을 따라감 (자식 본 포함) |
| `b_` / `ob_` | `HITBOX` | 이 본을 따라가는 히트박스 생성 |
| **`p_`** | **`SEAT`** | **탑승 좌석 (조종 가능)** |
| **`sp_`** | **`SUB_SEAT`** | **탑승 좌석 (조종 불가)** |
| `tag_` | `TAG` | 이름표 |
| `mtag_` / `ptag_` | `MOB_TAG` / `PLAYER_TAG` | 몹 / 플레이어 이름표 |
| `glow_` | `GLOW` | 발광 |
| `li_` / `ri_` | `LEFT_ITEM` / `RIGHT_ITEM` | 좌/우손 아이템 표시 |
| `ph_` `pra_` `prfa_` `pla_` | `PLAYER_*` | 플레이어 머리·팔·아래팔 |

### 8.3 펫 모델 권장 구성

```
pet_wolf.bbmodel
├─ h_head          ← 소유자를 바라보게 하려면 HEAD 태그 필요
├─ b_body          ← 클릭 상호작용용 히트박스
│   ├─ p_seat      ← ★ 탑승 좌석. 탑승 펫이면 필수
│   ├─ leg_fl · leg_fr · leg_bl · leg_br
│   └─ wing_l · wing_r   ← 비행 펫
└─ tag_name        ← 이름표 위치 (선택)
```

- `.bbmodel` 은 `plugins/BetterModel/models/` 에 배치
- 파일명(확장자 제외)이 설정의 `model:` 값
- **알 상태용 모델을 따로 만들지, 같은 모델의 `egg` 애니메이션으로 처리할지는 선택**이다. 설정에서 `egg-model:` 을 지정하면 별도 모델을 쓴다

> ⚠️ **저작권** — 악어의 놀이터에서 사용 중인 실제 모델·텍스처를 추출해 쓰는 것은 침해다. 시스템 구조를 참고하는 것과 에셋을 복제하는 것은 다르다.

---

## 9. 펫 생애주기

원작 시즌 2의 알–부화–성장 구조를 따른다.

```
   구매/획득          먹이            성장도 100         (조건)
 ┌─────────┐      ┌─────────┐      ┌─────────┐      ┌─────────┐
 │   EGG   │─────►│ HATCHING│─────►│  BABY   │─────►│  ADULT  │
 │   알    │ 먹이 │  부화중  │ 연출 │  아기   │ 성장 │  성체   │
 └─────────┘      └─────────┘      └─────────┘      └────┬────┘
                                        │                 │
                                        │ 과급식           │ 탑승 가능
                                        ▼                 │ 비행 가능(A+)
                                   ┌─────────┐            ▼
                                   │  PIG    │      등급별 능력 발현
                                   │ (기믹)   │
                                   └─────────┘
```

| 상태 | 진입 조건 | 특성 |
| --- | --- | --- |
| `EGG` | 획득 시 | 소환 불가. 인벤토리 아이템 또는 보관함에만 존재 |
| `HATCHING` | 먹이 급여 시작 | `hatch` 애니메이션 재생 |
| `BABY` | 부화 완료 | 소환·추종 가능. **탑승 불가** |
| `ADULT` | 성장도 100 도달 | **탑승 가능**, 등급별 능력 발현, 비행 펫이면 비행 가능 |
| `PIG` | 아기 상태에서 단시간 과급식 | 이스터에그. 설정으로 on/off |

### 성장도

| 경로 | 증가량 | 비고 |
| --- | --- | --- |
| 시간 경과 | **1분당 +1** | 소환 중일 때만 카운트할지는 설정 |
| 먹이(우유) | **+10** | 아이템 소비. 쿨다운 있음 |
| 관리자 지급 | 임의 | `/petadmin growth` |

- 성장도 상한(기본 100)에 도달하면 `ADULT`로 전이
- **과급식 판정**: 짧은 시간(기본 60초) 내에 먹이를 N개(기본 10) 이상 주면 `PIG` 전이. 설정 `gimmick.overfeed.enabled: false` 로 끌 수 있다
- 시간 경과 증가는 **소환 중인 펫만** 대상으로 한다. 보관함의 모든 펫을 1분마다 갱신하면 DB 쓰기가 폭증한다

> **성장도는 `updated_at` 기준으로 지연 계산한다.** 1분마다 전체 펫을 순회하며 +1 하는 대신, 읽을 때 `(now - updated_at) / 60000` 을 더해 계산하고 저장 시점에만 반영한다. 이게 없으면 동접에 비례해 DB 쓰기가 늘어난다.

---

## 10. 행동 설계

### 이동 모드

컨트롤러는 3가지 모드를 전환한다.

| 모드 | 조건 | 담당 |
| --- | --- | --- |
| `GROUND` | 기본 | `GroundMover` — 지상 추종 |
| `FLY` | 비행 펫이 공중에 있거나 지형 우회 필요 | `FlightMover` — 고도 유지 + 직선 이동 |
| `RIDDEN` | 플레이어가 탑승 중 | `RideController` — 탑승자 입력에 따름 (11장) |

### 지상 추종 상태 머신

| 상태 | 조건 | 동작 | 애니메이션 |
| --- | --- | --- | --- |
| `IDLE` | 거리 < 2.5 | 정지, 소유자 방향 머리 회전 | `idle` (LOOP) |
| `WALK` | 2.5 ≤ 거리 < 8 | 보간 이동 | `walk` (LOOP) |
| `RUN` | 8 ≤ 거리 < 24 | 보간 이동 (가속) | `run` (LOOP) |
| `TELEPORT` | 거리 ≥ 24 · 다른 월드 · 3초 이상 경로 실패 | 소유자 뒤로 즉시 이동 + 파티클 → `IDLE` | — |

**목표 지점**은 소유자 위치에서 시야 반대 방향으로 `follow-distance`(기본 2.0블록) 떨어진 지점. 소유자가 제자리 회전할 때 펫이 따라 도는 것을 막기 위해 **데드존 0.8블록**을 둔다.

`setAI(false)`이므로 바닐라 경로탐색을 쓰지 않는다. 방향 벡터 → 속도 상한 → 간이 지형 처리(앞 1블록이 막히고 위가 비었으면 `y += 0.5`, 아래가 비었으면 최대 3블록 하강) → 위치 갱신. 물·용암·공허 감지 시 즉시 `TELEPORT`.

> **M2에서 실측 확정** — `setVelocity()` vs 위치 보간 후 `teleport()` 중 어느 쪽이 클라이언트에서 부드러운지.

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
- **오프라인 소유자의 펫은 즉시 디스폰**
- **Folia 대응** — 감지 시 `EntityScheduler`로 전환. `PetTicker` 안에 캡슐화

### 애니메이션 상태 머신

```java
void tick() {
    AnimationState next = resolve(movement.mode(), movement.state());
    if (next == current) return;   // ★ 변화 없으면 아무것도 하지 않는다
    tracker.stopAnimation(current.animationName());
    tracker.animate(next.animationName(), LOOP_MODIFIER);
    current = next;
}
```

일회성 애니메이션(`attack`, `eat`, `hatch`)은 `priority`를 올린 `PLAY_ONCE` 모디파이어로 **오버레이**하고, `animate(..., Runnable onRemove)` 콜백에서 상태를 복원한다.

---

## 11. 탑승과 비행

원작의 핵심 기능이자 **이 프로젝트에서 가장 불확실한 부분**이다.

### 탑승

1. `ADULT` 상태이고 `rideable: true` 인 펫만 탑승 가능
2. 모델에 `p_seat` 본이 있어야 한다 (8.3)
3. 플레이어가 펫을 **우클릭**하면 탑승 시도
4. BetterModel의 `MountedHitBox`가 좌석 결합을 처리한다 (7장)
5. 탑승 중에는 `MovementMode.RIDDEN` 으로 전환 — 추종 로직 정지, 탑승자 입력에 따름
6. 하차: 스니크 또는 `/pet dismount`

**이동 속도는 등급에 비례한다** (원작 준수). `Rarity.rideSpeed` 참고.

### 비행

| 항목 | 설계 |
| --- | --- |
| 자격 | **A등급 이상**에서 확률적으로 `canFly` 부여 (원작 준수) |
| 확률 | 등급별 설정값 (13장) |
| 속도 | **겉날개+폭죽보다 느리게** 설정 — 원작의 밸런스 의도 |
| 연료 | **없음. 무한 비행** — 원작 준수 |
| 조작 | **Q9 미결** — 겉날개식 활공 vs 자유 비행 |

**구현 후보 2가지 (M5에서 프로토타입 비교):**

| | 방식 | 장점 | 단점 |
| --- | --- | --- | --- |
| A | 캐리어 `Mob`에 `setGravity(false)` + 탑승자 시선 방향으로 속도 부여 | 구현 단순, 조작 직관적 | 지형 충돌 처리를 직접 해야 함 |
| B | 탑승자에게 `allow-flight` 부여 + 펫을 플레이어에 종속 이동 | 바닐라 비행 물리 재사용 | 비행 권한 누수 위험, 하차 시 반드시 회수 필요 |

> B안은 **`allowFlight`를 반드시 원복**해야 한다. 하차·로그아웃·서버 종료·펫 사망 모든 경로에서 회수하지 않으면 플레이어가 영구 크리에이티브 비행을 얻는다. 능력 시스템의 `onUnequip` 역연산 원칙(13장)이 여기에도 그대로 적용된다.

### 위험 요소

- **탑승 상태에서 로그아웃** → 재접속 시 좌석 상태 복원 또는 안전 하차
- **탑승 중 펫 디스폰** → 플레이어가 공중에 남는다. 낙하 피해 면제 처리 필요
- **월드 이동 중 탑승** → 포탈 통과 시 강제 하차가 안전하다

---

## 12. 데이터 모델과 영속화

```java
// 펫 "종류" — 설정에서 로드하는 불변 객체
public record PetType(
        String id, String displayName, String modelId, String eggModelId,
        Rarity rarity, AnimationSet animations, MovementProfile movement,
        boolean rideable, double flyChance,
        List<AbilityDefinition> abilities,
        int growthMax, String evolvesInto
) {}

// 펫 "개체" — DB 저장 대상
public final class PetData {
    private final UUID petId;      // PK
    private final UUID ownerId;
    private final String typeId;
    private String nickname;
    private LifeStage stage;       // EGG / HATCHING / BABY / ADULT / PIG
    private int growth;            // 성장도
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

CREATE TABLE IF NOT EXISTS bp_meta (
    key VARCHAR(64) NOT NULL PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);
```

**원칙**

- **모든 DB I/O는 비동기.** 메인 스레드에서 JDBC 호출 금지 — 예외 없음
- **write-behind 캐시** — 접속 시 로드 → 메모리 읽기/쓰기 → dirty 플래그 → 5분 주기 · 퇴장 시 · 종료 시 flush
- **서버 종료 시 flush는 동기**로 수행. 비동기면 데이터를 잃는다
- **성장도는 `updated_at` 기반 지연 계산** (9장). 주기적 일괄 UPDATE 금지
- `bp_meta.schema_version`으로 버전 추적, 순차 마이그레이션

---

## 13. 등급과 능력

### 등급 — 원작의 D~S 체계

| 등급 | 이동속도 배율 | 탑승속도 | 비행 확률 | 색상 |
| --- | --- | --- | --- | --- |
| `D` | 1.00 | 0.20 | 0% | 회색 |
| `C` | 1.10 | 0.24 | 0% | 흰색 |
| `B` | 1.25 | 0.28 | 0% | 초록 |
| `A` | 1.45 | 0.34 | **10%** | 파랑 |
| `S` | 1.70 | 0.42 | **35%** | 금색 |

> ⚠️ 수치는 **플레이스홀더**다 (Q8). 원작의 실제 수치를 확인하지 못했다. 밸런싱은 M7에서 잡는다.
> **비행 확률은 A등급부터**라는 원작 규칙만 확정이다.

### 능력 (원작보다 확장 — 끄면 원작과 동일)

| 유형 | 발동 | 구현 | 예시 |
| --- | --- | --- | --- |
| `PASSIVE` | 소환 중 상시 | `AttributeModifier` 부착 | 이동속도, 최대체력 |
| `TRIGGER` | 특정 이벤트 | 리스너 + 확률 판정 | 피격 시 회복, 처치 시 추가 드랍 |
| `ACTIVE` | 수동 / 쿨다운 | 쿨다운 맵 + 스킬 실행 | 주변 몹 넉백 |

```java
public interface PetAbility {
    AbilityType type();
    void onEquip(AbilityContext ctx);      // 펫 소환 시
    void onUnequip(AbilityContext ctx);    // 펫 해제 시 — 반드시 원복
    default void onTrigger(AbilityContext ctx, Event event) {}
}
```

`AbilityRegistry`에 id → 팩토리를 등록하고 설정에서 id로 참조한다. **새 능력 추가 = 클래스 1개 + 등록 1줄.**

> ⚠️ **모디파이어 누적 버그.** 재접속·펫 교체·리로드마다 `AttributeModifier`가 쌓이면 플레이어가 로켓처럼 날아간다. **부착 전 항상 같은 키의 모디파이어를 제거**하고 접속 시점에도 청소한다. `onUnequip`은 `onEquip`의 정확한 역연산이어야 한다. **비행 권한(11장)에도 동일 원칙이 적용된다.**

---

## 14. 획득 경로

원작은 시즌마다 방식이 달랐다. **둘 다 지원하고 설정으로 선택**한다.

| 경로 | 원작 | 구현 |
| --- | --- | --- |
| **NPC 알 상점** | 시즌 2 | 동물조련사 NPC에게 알 구매. 등급별 가격 |
| **뽑기권 가챠** | 시즌 1 | 뽑기권 아이템 소비 → 가중치 추첨 |
| 관리자 지급 | — | `/petadmin give` |

- NPC는 **Citizens 연동 또는 자체 구현** (Q7)
- 뽑기 가중치는 등급별 설정. 결과 연출은 GUI에서
- 먹이(우유)도 NPC가 판매

---

## 15. GUI

| 화면 | 크기 | 내용 |
| --- | --- | --- |
| 펫 보관함 | 6줄 | 소유 펫 목록(페이지네이션), 등급 테두리, 생애주기 표시, 장착 표시 |
| 펫 상세 | 3줄 | 성장도 바, 등급, 비행 여부, 능력, [소환] [이름변경] [해방] |
| 부화/급여 | 3줄 | 성장도, [먹이 주기], 남은 시간 |
| 뽑기 | 3줄 | 비용, [뽑기], 결과 연출 |

- `InventoryHolder`를 구현한 커스텀 홀더로 GUI를 식별한다. **제목 문자열 비교 금지**
- `InventoryClickEvent`에서 GUI면 **무조건 `setCancelled(true)` 먼저** 호출한 뒤 로직 실행. 아이템 복제 취약점 대부분이 여기서 발생한다
- 이름 변경 입력은 **타임아웃(30초)과 취소 경로**를 반드시 제공

---

## 16. 명령어와 권한

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/pet` | `betterpets.use` | 보관함 GUI |
| `/pet summon <petId>` | `betterpets.use` | 소환 |
| `/pet dismiss` | `betterpets.use` | 해제 |
| `/pet dismount` | `betterpets.use` | 하차 |
| `/pet rename <이름>` | `betterpets.use` | 이름 변경 |
| `/petadmin give <플레이어> <타입> [등급]` | `betterpets.admin` | 지급 |
| `/petadmin growth <플레이어> <petId> <양>` | `betterpets.admin` | 성장도 지급 |
| `/petadmin reload` | `betterpets.admin` | 설정 리로드 |
| `/petadmin debug` | `betterpets.admin` | **누수 진단** |

`/petadmin debug`는 활성 펫 수 · 캐리어 엔티티 수 · `EntityTrackerRegistry.registries()` 기준 트래커 수를 출력해 **세 숫자가 일치하는지** 확인한다.

---

## 17. 설정 파일

```
plugins/BetterPets/
├─ config.yml       일반 설정, DB, 성능 튜닝, 기믹 on/off
├─ messages.yml     사용자 노출 문자열 (한국어)
├─ gui.yml          GUI 레이아웃
├─ rarity.yml       등급별 수치 (13장)
└─ pets/
    ├─ wolf.yml
    └─ dragon.yml
```

```yaml
# pets/dragon.yml
id: dragon
display-name: "&6드래곤"
model: pet_dragon
egg-model: pet_egg_dragon     # 알 상태 전용 모델 (선택)
rarity: S
growth-max: 100

rideable: true
fly-chance: 0.35              # 부화 시 이 확률로 canFly 확정

animations:
  idle: idle
  walk: walk
  run: run
  fly: fly
  ride: ride
  egg: egg
  hatch: hatch
  eat: eat

movement:
  follow-distance: 2.0
  walk-speed: 0.25
  run-speed: 0.45
  teleport-distance: 24.0

abilities:
  - id: attribute_speed
    type: PASSIVE
    base: 0.05
    per-growth: 0.0005

acquire:
  egg-price: 100000           # NPC 알 가격
  gacha-weight: 1             # 뽑기 가중치
```

**설정 검증**: 로드 시 필수 필드 누락, 존재하지 않는 모델 참조(`BetterModel.modelKeys()`로 대조), 미등록 능력 id를 **모두 수집해 한 번에 보고**한다. 첫 오류에서 멈추지 않는다.

---

## 18. 성능 — 최우선 제약

> **원작은 오픈 6일차에 렉으로 펫 소환 자제 요청을 받았다** (2장). 이 시스템의 실패 지점은 기능이 아니라 성능이다.

### 목표

| 지표 | 목표 | 비고 |
| --- | --- | --- |
| 활성 펫 **100마리** 기준 메인 스레드 부하 | < 1ms/tick | Q2 확정 시 기준선 조정 |
| 접속 시 펫 데이터 로드 | < 50ms (비동기) | |
| 메모리 (펫 100마리) | < 20MB | |
| 펫 1마리당 추가 패킷 | BetterModel 기본 + α (α ≈ 0) | |

### 원칙 — 선택이 아니라 필수

1. **전역 틱 루프 1개.** 펫당 태스크 금지
2. **애니메이션은 상태 전이 시에만 호출.** 매 틱 `animate()` 금지
3. **거리 기반 LOD** — 관전자 없으면 이동 갱신 2틱 → 10틱
4. **`viewRange` 제한** — `TrackerUpdateAction.viewRange()` 로 원거리 패킷 차단
5. **오프라인 소유자 펫 즉시 디스폰**
6. **성장도 지연 계산** — 주기적 일괄 DB UPDATE 금지 (9장)
7. **DB I/O 전량 비동기 + write-behind 캐시**

### 검증

- **각 마일스톤 완료 조건에 부하 테스트를 포함한다.** 마지막에 몰아서 하지 않는다
- M9에서 Spark로 실측한다
- 목표치는 **추정이며 측정된 값이 아니다.** 실측 전까지 달성 여부를 주장하지 않는다

---

## 19. 테스트

| 레벨 | 대상 | 도구 |
| --- | --- | --- |
| 단위 | `GrowthCurve`, 등급 배율, 능력 수치, 설정 파서 | JUnit 5 |
| 통합 | Repository CRUD | JUnit + 인메모리 SQLite |
| 수동 | 렌더링 · 이동 · 탑승 · 비행 | Paper 테스트 서버 |

**수동 체크리스트 (매 마일스톤 반복)**

- [ ] 소환 → 로그아웃 → 재접속 시 유령 모델이 남지 않는가
- [ ] 소환 상태로 서버 재시작 시 캐리어 엔티티가 청소되는가
- [ ] 월드 이동 / 네더 포탈 통과 시 펫이 따라오는가
- [ ] 펫 교체 20회 반복 시 `AttributeModifier`가 누적되지 않는가
- [ ] **탑승 중 로그아웃 → 재접속 시 안전하게 하차되는가**
- [ ] **탑승 중 펫이 디스폰돼도 낙하 피해를 입지 않는가**
- [ ] **하차 후 `allowFlight`가 회수되는가** (비행 구현 B안 채택 시)
- [ ] `/petadmin debug`의 세 숫자가 일치하는가
- [ ] **펫 N마리 소환 상태에서 TPS 20을 유지하는가** (N은 Q2로 확정)
- [ ] BetterModel을 제거한 상태에서 플러그인이 안전하게 비활성화되는가

---

## 20. 로드맵

| M | 마일스톤 | 산출물 | 예상 |
| --- | --- | --- | --- |
| **M0** | 프로젝트 뼈대 | **Maven `pom.xml`** · plugin.yml · CI · 패키지 구조 · **JDK 25 + BetterModel 컴파일 확인** | 3일 |
| **M1** | 렌더링 수직 슬라이스 | `/pet summon`으로 모델 소환/해제 · `BetterModelBridge` · **트래커 누수 0 + 부하 기준선 측정** | 1.5주 |
| **M2** | 이동 + 애니메이션 | 지상 추종 상태 머신 · 텔레포트 폴백 · 애니메이션 상태 머신 | 2주 |
| **M3** | 데이터 + 명령어 | `PetRepository`(SQLite) · 캐시 · 명령어/권한 | 1.5주 |
| **M4** | 생애주기 | 알 → 부화 → 아기 → 성체 · 성장도(시간+먹이) · 지연 계산 | 1.5주 |
| **M5** | **탑승 + 비행** | `MountedHitBox` 검증 → 탑승 · 비행 A/B 프로토타입 · 안전 하차 | **2.5주** |
| **M6** | GUI | 보관함 · 상세 · 부화/급여 · 이름변경 | 1.5주 |
| **M7** | 등급 + 능력 | D~S 등급 · 3종 능력 시스템 · 능력 3~5개 | 2주 |
| **M8** | 획득 + 진화 | NPC 알 상점 · 뽑기 가챠 · 진화 · 과급식 기믹 | 1.5주 |
| **M9** | 최적화 + 폴리시 | Spark 프로파일링 · LOD · Folia · PlaceholderAPI · 문서화 | 2주 |

**합계 약 16주** (1인 파트타임, 모델 제작 시간 제외)

**M5가 최대 변수다.** BetterModel의 `MountedHitBox`로 탑승이 바로 되면 1.5주, 자체 구현으로 가면 3주 이상 걸릴 수 있다. **M5 착수 첫날에 이 검증부터 한다.**

**병행**: 8장 규격에 맞춘 모델 제작을 M1과 동시에 진행해야 M2 진입 시 테스트 모델이 준비된다. 탑승 펫 모델(`p_seat` 본 포함)은 M5 전까지 필요하다.

### 마일스톤 완료 조건

1. 기능이 테스트 서버에서 동작한다
2. 해당 범위의 단위 테스트가 통과한다
3. 19장 수동 체크리스트를 통과한다
4. **부하 테스트를 통과한다** (18장)
5. README를 갱신하고 브랜치에 커밋·푸시한다

---

## 21. 리스크

| # | 리스크 | 영향 | 확률 | 대응 |
| --- | --- | --- | --- | --- |
| **R1** | **성능 — 원작이 실제로 실패한 지점** | **높음** | **높음** | 18장 원칙을 필수로 강제. 각 마일스톤에 부하 테스트 포함. M1에서 기준선 측정 |
| **R2** | 트래커 누수 → 유령 모델 / TPS 저하 | 높음 | 중 | `AutoCloseable` + 4경로 close + `registries()` 진단 + M1 DoD |
| **R3** | **탑승 API가 기대대로 동작하지 않음** | **높음** | **중** | M5 첫날 `MountedHitBox` 검증. 실패 시 `addPassenger` 자체 구현으로 전환 (일정 +1.5주) |
| **R4** | **비행 권한(`allowFlight`) 누수** | 높음 | 중 | 하차·로그아웃·종료·사망 모든 경로에서 회수. 체크리스트 항목화. 가능하면 A안(중력 제어) 채택 |
| **R5** | BetterModel 버전 업 시 API 파괴 | 중 | 높음 | `BetterModelBridge` 단일 격리 계층. 버전 핀 고정 |
| **R6** | `AttributeModifier` 누적 버그 | 중 | 높음 | 부착 전 항상 제거 + 접속 시 청소 + 반복 테스트 |
| **R7** | **원작 사양 오해** | 중 | 중 | 2장은 검색 요약 기반이며 **원문 대조를 못 했다.** 실제와 다르면 밸런싱 수치부터 조정 |
| **R8** | 모델 제작 지연으로 M2/M5 블로킹 | 중 | 중 | M1과 병렬 착수. 탑승 모델은 M5 전까지 |
| **R9** | 모델 에셋 저작권 | 높음 | 낮음 | 직접 제작 확정으로 완화. 원본 복제 금지 원칙 유지 |
| **R10** | GUI 아이템 복제 취약점 | 높음 | 낮음 | `setCancelled(true)` 선행 + 코드 리뷰 |
| **R11** | Folia 스케줄러 차이 | 중 | 낮음 | `PetTicker`에 분기 캡슐화. M9 검증 |
| **R12** | JDK 25 빌드 환경 부재 | 중 | 중 | 개발 머신·CI 모두 JDK 25 설치. M0 첫 작업 |

---

## 22. 참고 자료

### BetterModel

- [BetterModel — GitHub](https://github.com/toxicity188/BetterModel)
- [BetterModel — API example (Wiki)](https://github.com/toxicity188/BetterModel/wiki/API-example)
- [BetterModel — Configuring bone tag](https://github.com/toxicity188/BetterModel/wiki/Configuring-bone-tag)
- [BetterModel — Hangar (PaperMC)](https://hangar.papermc.io/toxicity188/BetterModel)
- [BetterModel — 공식 문서](https://mintlify.wiki/toxicity188/BetterModel/introduction)

### 원작 시스템 (2장 근거)

- [악어의 놀이터 — 나무위키](https://namu.wiki/w/%EC%95%85%EC%96%B4(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EB%B0%A9%EC%86%A1%EC%9D%B8)/%EB%8C%80%EA%B7%9C%EB%AA%A8%20%EC%BD%98%ED%85%90%EC%B8%A0/%EC%95%85%EC%96%B4%EC%9D%98%20%EB%86%80%EC%9D%B4%ED%84%B0)
- [악어의 놀이터 2 — 나무위키](https://namu.wiki/w/%EC%95%85%EC%96%B4(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EB%B0%A9%EC%86%A1%EC%9D%B8)/%EB%8C%80%EA%B7%9C%EB%AA%A8%20%EC%BD%98%ED%85%90%EC%B8%A0/%EC%95%85%EC%96%B4%EC%9D%98%20%EB%86%80%EC%9D%B4%ED%84%B0%202)
- [악어의 놀이터 2 / 콘텐츠 — 나무위키](https://namu.wiki/w/%EC%95%85%EC%96%B4(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EB%B0%A9%EC%86%A1%EC%9D%B8)/%EB%8C%80%EA%B7%9C%EB%AA%A8%20%EC%BD%98%ED%85%90%EC%B8%A0/%EC%95%85%EC%96%B4%EC%9D%98%20%EB%86%80%EC%9D%B4%ED%84%B0%202/%EC%BD%98%ED%85%90%EC%B8%A0)

---

## 문서 이력

| 버전 | 날짜 | 변경 |
| --- | --- | --- |
| v1.0 | 2026-09-08 | 최초 작성 |
| v1.1 | 2026-09-08 | Java 25 / MC 26.2 확정 · 모델 직접 제작 확정 · 소스 JAR로 API 검증 · 본 태그 규칙 확정 · paper-api 좌표 정정 |
| **v2.0** | 2026-09-08 | **빌드를 Maven으로 전환** · **원작 시스템 조사 반영(2장)** — 등급 D~S, 알/부화/성장도, 탑승·비행을 핵심 기능으로 승격, 성능을 R1으로 격상 · 생애주기(9장)·탑승과 비행(11장) 신설 · 로드맵 11.5주 → 16주 |
