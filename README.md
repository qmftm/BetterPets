# BetterPets

악어의 놀이터 스타일 펫 플러그인 — [BetterModel](https://github.com/toxicity188/BetterModel) 기반 Paper 플러그인

| | |
| --- | --- |
| 계획서 버전 | **v2.2** (2026-09-08) |
| 대상 | Paper / Minecraft **26.2** / Java **25** |
| BetterModel | **3.4.1** (MIT) |
| 빌드 | **Maven** |
| **서버 규모** | **최대 5명** — 설계 전반의 기준 (3장) |
| 상태 | **M0 완료** — 빌드·테스트 통과. M1(렌더링) 대기 |
| 예상 기간 | 약 11주 (1인 파트타임) |

---

## 목차

- [1. 요약](#1-요약)
- [2. 원작 시스템 분석](#2-원작-시스템-분석)
- [2.5 참고 구현 분석 — betterpets-paper](#25-참고-구현-분석--betterpets-paper)
- [3. 서버 규모와 범위](#3-서버-규모와-범위)
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
| **탑승** | 보이지 않는 `ArmorStand` + `addPassenger` + Paper `Input` API | 원작의 핵심 기능. 배포 중인 플러그인에서 검증된 구조 (2.5장, 11장) |
| **이동** | `setAI(false)` 후 자체 컨트롤러 | 지상/비행/탑승 3가지 모드를 한 컨트롤러에서 전환해야 한다 |
| **애니메이션** | 상태 전이 시점에만 `animate()` 호출 | `animate()`는 패킷을 만든다. 매 틱 호출은 규모와 무관한 버그다 |
| **영속화** | SQLite 단일 커넥션 + 단일 스레드 실행자 | 5명 규모라 커넥션 풀·MySQL은 과잉 (3장) |
| **획득** | **알 아이템 우클릭** — NPC 없음 | 5명 서버에서 NPC 상점은 비용 대비 얻는 게 없다 (14장) |

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

### 원작은 성능 때문에 곤란을 겪었다 — 다만 규모가 다르다

> 서버 오픈 **6일차에 운영진이 렉을 이유로 펫 소환 자제를 요청**했고, 여러 방송인이 방송 중 불만을 표시했다.

펫 시스템의 실패 지점이 기능 부족이 아니라 **성능**이었다는 건 기억해둘 만하다. 다만 원작은 방송인 수십 명이 붙은 대규모 서버였고, **이 프로젝트는 최대 5명이다**(3장). 그래서 교훈은 이렇게 적용한다:

- **구조적으로 옳은 것만 지킨다** — 전역 틱 루프 1개, 상태 전이 시에만 애니메이션, DB I/O는 메인 스레드 밖. 이건 규모와 무관하게 맞는 방식이고 비용도 없다
- **규모 대응 최적화는 만들지 않는다** — 거리 LOD, `viewRange` 튜닝 등은 5마리에 필요 없다
- 상세는 3장과 18장 참고

### 원작 대비 이 설계의 차이

| 항목 | 원작 | 본 설계 | 이유 |
| --- | --- | --- | --- |
| 등급 | D~S | **D~S 채택** | 원작 준수 |
| 성장 | 성장도 (시간+먹이) | **성장도 채택** + 선택적 경험치 | 원작 준수, 확장 여지 |
| 탑승 | 핵심 기능 | **핵심 기능으로 승격** | v1.x에서 범위 제외했던 것을 정정 |
| 비행 | A등급부터 확률 | **채택** | 원작 준수 |
| 획득 | NPC 알 구매(S2) + 뽑기권(S1) | **알 아이템 우클릭 하나로 통합** | 5명 서버에 NPC는 과잉. 랜덤 알로 뽑기 감각은 유지 (14장) |
| 능력 | 이동속도 위주 | **패시브/트리거/액티브 확장** | 원작보다 확장. 끄면 원작과 동일 |

---

## 2.5 참고 구현 분석 — betterpets-paper

[`yourShika/betterpets-paper`](https://github.com/yourShika/betterpets-paper) (MIT, v1.26.1)는 **이미 BetterModel 연동과 비행 탑승을 구현해 배포 중인 Paper 플러그인**이다. 소스를 읽고 확인한 내용을 정리한다. 우리와 목표가 상당히 겹쳐서 — Paper 26.2, Maven, BetterModel, 탑승·비행 펫 — 가장 값진 입력이다.

> MIT 라이선스이므로 **저작권 표시를 유지하면 코드를 참고·차용할 수 있다.** 다만 이 프로젝트는 스스로 "AI의 도움으로 만들어졌다"고 밝히고 있으니, 검증 없이 그대로 옮기지는 않는다.

### 우리 설계가 확인된 것

| 우리 결정 | 그쪽 구현 |
| --- | --- |
| Maven 빌드 | ✅ 동일 (`pom.xml`) |
| BetterModel API 격리 계층 | ✅ 동일 — `PetModelBridge` 인터페이스 ← `BetterModelHook` 구현 |
| 핸들이 `AutoCloseable` | ✅ 동일 — `PetModelHandle extends AutoCloseable` |
| `getOrCreate` + `TrackerModifier.DEFAULT` | ✅ 동일 |
| `tracker.close()` / `isClosed()` / `hide` / `show` | ✅ 동일 |

7장에 정리한 API 시그니처가 **실제 동작하는 플러그인에서 그대로 쓰이고 있음이 확인됐다.**

### 우리 계획을 고쳐야 하는 것

| # | 발견 | 조치 |
| --- | --- | --- |
| 1 | **`paper-plugin.yml` + `api-version: '26.2'`** 를 쓴다 | 우리 계획의 `plugin.yml` / `api-version: '1.21'` 은 **구식이다.** 4장 정정 |
| 2 | **탑승에 BetterModel `MountedHitBox`를 쓰지 않는다.** 보이지 않는 `ArmorStand` + `addPassenger`로 직접 구현 | 11장 전면 개정. **R1이 크게 완화된다** — 검증된 대안이 생겼다 |
| 3 | 조향에 **Paper `Input` API** (`input.isSneak()` 등 WASD/점프 상태)를 쓴다 | 11장 반영. 시선+속도 해킹보다 훨씬 낫다 |
| 4 | Paper 26.2는 **Adventure 5.x** 기반이다 (4.x는 `ObjectContentsLike` 부재) | 의존성 충돌 시 확인 |
| 5 | 저장소 기본값이 **YAML**이고 SQLite는 미구현 옵션 | 5명 규모면 YAML도 충분하다. 12장에 대안으로 명시 |

### 그대로 가져올 만한 기법 — 우리가 겪었을 버그들

이들이 릴리스 노트에서 고쳤다고 밝힌 것들이 곧 우리가 만날 버그다.

| 문제 | 해법 |
| --- | --- |
| 빠른 마운트가 얇은 벽을 **관통** | 이동 경로를 **0.45블록 이하 서브스텝으로 분할 검사**. 하나라도 막히면 전체 취소 |
| 벽에 닿으면 이동이 완전히 멈춤 | **벽 슬라이딩** — 전체 이동 → 수평만 → 수직만 순으로 시도 |
| 어깨/머리가 벽에 **끼임** | 중심 + 반경 0.35의 4방향 점을 **발치와 머리 높이 양쪽에서** 검사 |
| 하차 시 **낙하 피해** | `setFallDistance(0)` + `SLOW_FALLING` 100틱 |
| 비행 중 펫 히트박스가 **블록 파괴 레이캐스트를 가로챔** | 비행 중 `Interaction` 히트박스를 0.1×0.1로 축소, 하차 시 복원 |
| 펫이 채굴·건축을 방해 | 우클릭 동작이 없는 펫은 히트박스를 아예 작게. 공격 불가 처리 |
| 실수로 이륙 | **두 번째 우클릭 확인**을 요구 (시간 창 내) |
| 비행 중 우클릭하면 하차돼버림 | 비행 중 하차는 **스니크 전용** |
| 고도 제한 | 빌드 높이가 아니라 `flight-max-height`(기본 1024)로 별도 상한 |
| 펫 본체가 마운트를 못 따라감 | `display.setTeleportDuration(1)` — 탑승 중엔 1틱 보간, 평상시엔 8틱 |

### 반면교사 — 구조

전체 12,816줄 중 **`BetterPetsPlugin.java`가 5,034줄, `ActivePetManager.java`가 3,792줄**이다. 두 파일이 코드의 69%를 차지하는 전형적인 신 클래스(God class)다.

우리의 계층 분리(6장)는 이걸 피하기 위한 것이다. **`ActivePetManager`에 해당하는 책임을 `MovementController` / `RideController` / `AnimationStateMachine` / `PetTicker`로 나눠 유지한다.** 한 파일이 800줄을 넘으면 분리 신호로 본다.

### 엔티티 구성 — 그쪽과 우리의 차이

그쪽은 펫 1마리당 **`ItemDisplay`(본체) + `Interaction`(클릭) + `TextDisplay`(이름표)** 3개를 쓰고, 탑승 시 `ArmorStand`가 하나 더 붙는다. BetterModel이 **선택 사항**이라 없을 때 플레이어 머리로 대체 렌더링해야 하기 때문이다.

우리는 BetterModel에 **하드 의존**하므로 본 태그로 히트박스(`b_`)와 이름표(`tag_`)를 얻을 수 있어 엔티티를 줄일 수 있다. 다만 **BetterModel이 `ItemDisplay`에도 정상적으로 붙는다는 것**이 확인됐으므로, 캐리어를 `Mob`으로 할지 `ItemDisplay`로 할지는 M1에서 실측해 정한다.

---

## 3. 서버 규모와 범위

### ★ 서버 규모 — 최대 5명

이 프로젝트의 목표 서버는 **최대 동시 접속 5명**이다. 이 전제가 설계 전반을 바꾼다.

**활성 펫은 많아야 5마리.** 앞선 v2.0 계획서는 100마리를 기준으로 잡았는데, 그건 이 서버에 대해 20배 과잉이었다. 다음이 따라온다:

| 항목 | v2.0 (100마리 가정) | **v2.1 (5명 기준)** |
| --- | --- | --- |
| 성능 리스크 등급 | R1 — 최상위 | **R7로 강등** — 여전히 지키되 설계를 왜곡하지 않는다 |
| 거리 기반 LOD | 필수 | **불필요.** 구현하지 않는다 |
| `viewRange` 패킷 제한 | 필수 | **선택.** 기본값으로 둔다 |
| DB 백엔드 | SQLite + MySQL 구현체 | **SQLite만.** MySQL 구현체는 만들지 않는다 |
| 커넥션 풀 | HikariCP | **제거.** 단일 커넥션 + 단일 스레드 실행자 |
| write-behind 캐시 | 5분 주기 일괄 flush | **변경 시 즉시 비동기 저장.** 배치 불필요 |
| 성능 목표 | 100마리 < 1ms/tick | **10마리 < 1ms/tick** (2배 여유) |

**그래도 유지하는 것** — 비용이 거의 없으면서 원작의 실패를 막아주는 것들:

1. **전역 틱 루프 1개** — 펫당 태스크를 만들지 않는다. 5마리든 100마리든 이게 더 단순하다
2. **애니메이션은 상태 전이 시에만 호출** — 매 틱 `animate()` 호출은 규모와 무관하게 잘못됐다
3. **DB I/O는 메인 스레드 밖에서** — 5명이어도 메인 스레드 블로킹은 그냥 버그다
4. **성장도 지연 계산** — 주기적 일괄 UPDATE보다 오히려 구현이 간단하다
5. **트래커 누수 방지** — 규모와 무관하다. 유령 모델은 1마리만 남아도 버그다

> 요컨대 **최적화는 빼고, 올바른 구조는 남긴다.** 원작이 렉으로 실패한 건 사실이지만(2장) 그건 수십~수백 명 규모의 이야기다. 5명 서버에서 LOD를 만드는 건 쓰지도 않을 복잡도를 떠안는 것이다.

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
| F10 | 획득 — **알 아이템 우클릭** | M8 |
| F11 | 진화 (과급식 기믹 포함) | M8 |
| F12 | PlaceholderAPI · 폴리시 | M9 |

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
export JAVA_HOME=/path/to/jdk-25      # ★ JDK 25 필수
mvn clean package                     # target/BetterPets-0.1.0-SNAPSHOT.jar
mvn test                              # 단위 테스트만
```

**빌드하며 실측으로 확인한 것 두 가지** (M3에서 저장소 라이브러리를 들일 때 필요):

1. **`maven-shade-plugin` 은 3.6.2 이상**이어야 한다. 3.6.0은 번들 ASM이 낡아 Java 25 클래스(major 69)를 못 읽고 `Unsupported class file major version 69` 로 패키징에서 실패한다.
2. **셰이딩보다 Paper 라이브러리 로더가 낫다.** sqlite-jdbc를 셰이딩했더니 전 플랫폼 네이티브가 딸려와 jar가 **16KB → 14MB** 로 불었다. Paper는 플러그인별로 클래스로더를 격리하므로 재배치도 필요 없다.

그래서 M0 시점에는 외부 런타임 의존성이 없다. 저장소 백엔드 결정은 M3로 미뤘다.

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

`maven-shade-plugin`으로 SQLite를 `kr.qmftm.betterpets.lib.sqlite` 로 **재배치(relocate)** 한다. 다른 플러그인이 같은 라이브러리를 셰이딩했을 때 충돌하지 않기 위해서다.

셰이딩 대상은 SQLite 하나뿐이다. **커넥션 풀(HikariCP)은 쓰지 않는다** — 5명 규모에 과잉이고, 단일 커넥션 + 단일 스레드 실행자가 SQLite 잠금 문제를 애초에 없앤다 (12장).

```yaml
# src/main/resources/paper-plugin.yml
# ⚠️ plugin.yml 이 아니라 paper-plugin.yml 이다. api-version 도 '1.21' 이 아니라 '26.2'.
name: BetterPets
version: '${project.version}'
main: kr.qmftm.betterpets.BetterPetsPlugin
api-version: '26.2'
dependencies:
  server:
    BetterModel:
      load: BEFORE
      required: true          # 우리 펫은 3D 모델이 본체다. 선택 사항이 아니다
      join-classpath: true
    PlaceholderAPI:
      load: BEFORE
      required: false
      join-classpath: true
```

> Paper 26.2는 **Adventure 5.x** 기반이다. Adventure 4.x 계열 라이브러리를 끌어오는 다른 플러그인과 섞이면 `ObjectContentsLike` 같은 클래스 부재로 깨질 수 있다 (2.5장).

> ⚠️ **BetterModel 3.4.1은 Java 25 전용이다.** 클래스 파일 버전 69라서 JDK 24 이하로는 컴파일조차 되지 않는다. 빌드 머신과 운영 서버 모두 JDK 25가 필요하다.

---

## 5. 남은 확인 사항

| # | 질문 | 현재 가정 | 필요 시점 |
| --- | --- | --- | --- |
| ~~Q1~~ | ~~JDK 버전~~ | ✅ **Java 25 / MC 26.2 확정** | — |
| ~~Q3~~ | ~~모델 에셋~~ | ✅ **직접 제작 (8장이 규격)** | — |
| ~~Q6~~ | ~~원작 시스템 사양~~ | ✅ **조사 완료 (2장)** — 원문 대조는 미완 | — |
| ~~Q2~~ | ~~동시 접속자 수~~ | ✅ **최대 5명 확정 (3장)** | — |
| ~~Q4~~ | ~~MySQL 필요 여부~~ | ✅ **SQLite만. MySQL 구현체 제외** | — |
| ~~Q5~~ | ~~Vault 사용 여부~~ | ✅ **보류.** NPC 상점을 없애며 결제 지점 소멸 | — |
| ~~Q7~~ | ~~NPC 구현 방식~~ | ✅ **NPC 없음. 알 아이템으로 대체 (14장)** | — |
| ~~Q9~~ | ~~비행 조작 방식~~ | ✅ **`ArmorStand` 마운트 + Paper `Input` API (11장)** | — |
| **Q8** | **등급별 이동속도·비행 확률 수치는?** | 13장 플레이스홀더 | M7 전 |

남은 건 Q8 하나이고 **밸런싱 수치** 문제다. 실제로 타보면서 정하는 게 맞으니 착수를 막지 않는다.

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

> **2.5장 조사로 접근이 바뀌었다.** 원래는 BetterModel의 `MountedHitBox`(`p_` 좌석 본)에 기대는 계획이었으나, 같은 일을 하는 배포 중인 플러그인이 **그 경로를 쓰지 않고** 보이지 않는 `ArmorStand`를 직접 구동한다. 그래서 아래를 **기본안**으로 삼고, `MountedHitBox`는 더 간단하면 채택하는 선택지로 내린다.

### 탑승 — 기본안 (검증된 구조)

1. `ADULT` 상태이고 `rideable: true` 인 펫만 탑승 가능
2. 플레이어가 펫을 **우클릭** → 지상 마운트는 즉시, **비행은 시간 창 내 두 번째 우클릭으로 확인** (실수 이륙 방지)
3. 보이지 않는 `ArmorStand`를 스폰해 `addPassenger(player)` 로 태운다
   - `setVisible(false)` · `setGravity(false)` · `setInvulnerable(true)` · `setCollidable(false)` · `setSmall(true)` · `setBasePlate(false)` · `setPersistent(false)`
   - 스코어보드 태그 + PDC(소유자·펫 UUID)로 식별 — 서버 재시작 후 청소용
   - `addPassenger`가 실패하면 마운트를 **즉시 제거**하고 메시지. 유령 아머스탠드를 남기지 않는다
4. 펫 본체는 마운트를 따라 `teleport` + **`setTeleportDuration(1)`** (1틱 보간)으로 밀착 추적. 하차 시 평상시 값(8틱)으로 복원
5. 탑승 중에는 `MovementMode.RIDDEN` — 추종 로직 정지, 탑승자 입력에 따름
6. **하차는 스니크 전용** (비행 중 우클릭으로 하차하면 오작동이 잦다) 또는 `/pet dismount`

**조향은 Paper `Input` API로 한다.** 시선 방향 + 속도 부여 같은 옛 해킹 대신, `input.isForward()/isJump()/isSneak()` 등 실제 키 입력 상태를 읽는다.

```java
// 비행 이동 벡터 구성
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

**이동 적용은 서브스텝 검사 + 벽 슬라이딩**으로 한다. 이게 없으면 빠른 마운트가 얇은 벽을 관통하거나 벽에 닿는 순간 완전히 멈춘다.

```java
// 전체 이동 → 수평만 → 수직만 순으로 시도 (벽 슬라이딩)
if (!tryMove(mount, base, dx, dy, dz, yaw)
 && !tryMove(mount, base, dx, 0,  dz, yaw)
 && !tryMove(mount, base, 0,  dy, 0,  yaw)) {
    mount.setRotation(yaw, 0f);   // 셋 다 막히면 회전만
}

// tryMove: 목적지만이 아니라 경로 전체를 0.45블록 이하 서브스텝으로 검사한다.
// 하나라도 막히면 all-or-nothing 으로 취소 → 호출부가 축을 바꿔 재시도
int steps = Math.max(1, (int) Math.ceil(dist / 0.45));
```

안전 검사는 **탑승자의 대략적 바운딩 박스를 표본**한다 — 중심 + 반경 0.35의 4방향 점을, 발치와 머리 높이 **양쪽에서**. 중심만 검사하면 어깨가 벽에 끼인다.

**이동 속도는 등급에 비례한다** (원작 준수).

### 마운트 방식 비교

| | 방식 | 판정 |
| --- | --- | --- |
| **A** | 보이지 않는 `ArmorStand` + `addPassenger` + `Input` API | ✅ **기본안** — 배포 중인 플러그인에서 검증됨 |
| B | BetterModel `MountedHitBox` + `p_` 좌석 본 | ⚪ **M5 첫날 검증.** 더 간단하면 채택 |

### 비행

| 항목 | 설계 |
| --- | --- |
| 자격 | **A등급 이상**에서 확률적으로 `canFly` 부여 (원작 준수) |
| 확률 | 등급별 설정값 (13장) |
| 속도 | **겉날개+폭죽보다 느리게** — 원작의 밸런스 의도 |
| 연료 | **없음. 무한 비행** — 원작 준수 |
| 고도 상한 | 빌드 높이가 아니라 **`flight-max-height`(기본 1024)** 로 따로 둔다 |
| 조작 | Paper `Input` API — 보고 조향, 앞으로 전진, 점프로 상승, 스니크로 하차 |

> **`allowFlight` 부여 방식은 채택하지 않는다.** 바닐라 비행 물리를 재사용할 수 있지만, 하차·로그아웃·종료·사망 모든 경로에서 회수하지 않으면 플레이어가 영구 크리에이티브 비행을 얻는다. `ArmorStand` 마운트 방식은 이 위험 자체가 없다. **Q9는 이것으로 해소한다.**

### 위험 요소와 대응

| 위험 | 대응 |
| --- | --- |
| 하차 시 낙하 피해 | `setFallDistance(0)` + `SLOW_FALLING` 100틱 부여 |
| 비행 중 히트박스가 블록 파괴 레이캐스트를 가로챔 | 비행 중 히트박스를 0.1×0.1로 축소, 하차 시 복원 |
| 탑승 상태로 로그아웃 | 재접속 시 **안전 하차**로 처리 (좌석 복원은 하지 않는다) |
| 탑승 중 펫/마운트 디스폰 | 매 틱 마운트 생존·탑승자 일치·월드 일치를 확인하고, 어긋나면 즉시 안전 하차 |
| 월드 이동 / 포탈 | 강제 하차가 안전하다 |
| 서버 재시작 후 유령 마운트 | 스코어보드 태그 + PDC로 식별해 기동 시 청소 |

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

**원칙** (5명 규모에 맞춰 단순화)

- **SQLite 단일 커넥션 + 단일 스레드 실행자.** 커넥션 풀(HikariCP) 없음 — 5명에 과잉이고, 단일 스레드가 SQLite 잠금 문제를 애초에 없앤다
- **모든 DB I/O는 그 실행자 위에서.** 메인 스레드에서 JDBC 호출 금지 — 예외 없음
- **캐시는 단순하게** — 접속 시 로드 → 메모리에서 읽기/쓰기 → **변경 시 즉시 비동기 저장**. 5분 배치 flush는 불필요
- **서버 종료 시에는 실행자를 동기 대기**(`awaitTermination`)한다. 안 하면 마지막 쓰기를 잃는다
- **성장도는 `updated_at` 기반 지연 계산** (9장). 주기적 일괄 UPDATE 금지
- **MySQL 구현체는 만들지 않는다.** `PetRepository` 인터페이스는 유지하므로 필요해지면 그때 추가한다
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

**NPC는 만들지 않는다.** 원작은 동물조련사 NPC에게 알을 샀지만, 5명 서버에서 NPC 상점(+Citizens 의존성 또는 자체 NPC 구현)은 비용 대비 얻는 게 없다. **알 아이템 우클릭 하나로 대체한다.**

| 경로 | 구현 |
| --- | --- |
| **알 아이템 우클릭** | 손에 든 알 아이템을 우클릭 → 아이템 1개 소비 → 보관함에 `EGG` 상태 펫 추가 |
| 관리자 지급 | `/petadmin give <플레이어> <타입>` — 펫 직접 지급 |
| 관리자 알 지급 | `/petadmin egg <플레이어> <알종류> [개수]` — 알 아이템 지급 |

### 알 아이템 설계

- **아이템 식별은 PDC로 한다.** 이름·로어 문자열 비교 금지 — 플레이어가 모루로 이름을 바꾸면 깨진다

```java
// 알 아이템 생성 시
meta.getPersistentDataContainer().set(EGG_KEY, PersistentDataType.STRING, eggId);
```

- **두 가지 알 종류를 지원한다** (설정으로 정의):

| 종류 | 동작 |
| --- | --- |
| **고정 알** | 지정된 `PetType` 하나를 준다. 예) `dragon_egg` → 드래곤 |
| **랜덤 알** | 가중치 추첨으로 펫을 결정한다. 원작의 뽑기 감각을 아이템 하나로 대체 |

```yaml
# eggs.yml
dragon_egg:
  display-name: "&6드래곤 알"
  material: TURTLE_EGG
  model-data: 1001          # 리소스팩 커스텀 모델
  gives: dragon             # 고정

random_egg:
  display-name: "&f수상한 알"
  material: EGG
  model-data: 1000
  weights:                  # 랜덤 — 가중치 추첨
    wolf: 50
    bear: 30
    dragon: 1
```

- 우클릭 처리는 `PlayerInteractEvent`. **`EquipmentSlot.HAND`만 처리**하고 오프핸드 중복 발동을 막는다
- 보관함이 가득 찼으면 **아이템을 소비하지 않고** 메시지만 띄운다
- 먹이(우유)도 같은 방식의 PDC 아이템으로 만든다. 별도 상점 없이 `/petadmin` 지급 또는 서버 운영자가 배포

> **Vault(경제) 연동은 보류한다** (Q5). NPC 상점이 없어지면서 결제 지점이 사라졌다. 나중에 알 아이템을 상점에서 팔고 싶어지면 그때 붙인다 — `softdepend`로 남겨둔다.

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

## 18. 성능

원작은 오픈 6일차에 렉으로 펫 소환 자제 요청을 받았다(2장). 다만 그건 수십~수백 명 규모의 이야기고, **이 서버는 최대 5명**이다(3장). 그래서 성능은 **지키되 설계를 왜곡하지 않는 선**에서 다룬다.

### 목표

| 지표 | 목표 | 비고 |
| --- | --- | --- |
| 활성 펫 **10마리** 기준 메인 스레드 부하 | < 1ms/tick | 실사용(5마리)의 2배 여유 |
| 접속 시 펫 데이터 로드 | < 50ms (비동기) | |
| 메모리 | 무시할 수준 | 별도 목표를 두지 않는다 |

### 지키는 원칙 — 비용이 없고 구조를 더 단순하게 만드는 것들

1. **전역 틱 루프 1개.** 펫당 태스크 금지 — 규모와 무관하게 이게 더 단순하다
2. **애니메이션은 상태 전이 시에만 호출.** 매 틱 `animate()`는 규모와 무관한 버그다
3. **DB I/O는 메인 스레드 밖에서.** 5명이어도 블로킹은 버그다
4. **성장도 지연 계산** (9장) — 주기적 일괄 UPDATE보다 구현이 간단하다
5. **오프라인 소유자 펫 즉시 디스폰**
6. **트래커 누수 방지** — 규모 무관. 유령 모델은 1마리만 남아도 버그다

### 하지 않는 것

- ❌ **거리 기반 LOD** — 5마리에 필요 없다
- ❌ **`viewRange` 튜닝** — 기본값으로 둔다
- ❌ **마일스톤마다 부하 테스트** — M9에서 한 번만 확인한다

### 검증

M9에서 Spark로 한 번 실측한다. 목표치는 **추정이며 측정된 값이 아니다.**

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
- [ ] **알 아이템을 이름 변경(모루)해도 여전히 인식되는가** (PDC 식별 검증)
- [ ] **보관함이 꽉 찬 상태에서 알을 우클릭하면 아이템이 소비되지 않는가**
- [ ] 펫 10마리 소환 상태에서 TPS 20을 유지하는가 (M9 1회)
- [ ] BetterModel을 제거한 상태에서 플러그인이 안전하게 비활성화되는가

---

## 20. 로드맵

| M | 마일스톤 | 산출물 | 예상 |
| --- | --- | --- | --- |
| ~~**M0**~~ | ✅ **프로젝트 뼈대 — 완료** | `pom.xml` · `paper-plugin.yml` · 진입점 · 렌더 격리 계층 · 도메인(등급/생애주기/성장도) · **JDK 25 실제 빌드 성공, 테스트 24개 통과** | 완료 |
| **M1** | 렌더링 수직 슬라이스 | `/pet summon`으로 모델 소환/해제 · `BetterModelBridge` · **트래커 누수 0 검증** | 1.5주 |
| **M2** | 이동 + 애니메이션 | 지상 추종 상태 머신 · 텔레포트 폴백 · 애니메이션 상태 머신 | 2주 |
| **M3** | 데이터 + 명령어 | `PetRepository`(SQLite) · 단일 스레드 실행자 · 명령어/권한 | 1주 |
| **M4** | 생애주기 | 알 → 부화 → 아기 → 성체 · 성장도(시간+먹이) · 지연 계산 | 1.5주 |
| **M5** | **탑승 + 비행** | `ArmorStand` 마운트 + `Input` 조향 · 서브스텝/슬라이딩 · 안전 하차 | **1.5주** |
| **M6** | GUI | 보관함 · 상세 · 부화/급여 · 이름변경 | 1.5주 |
| **M7** | 등급 + 능력 | D~S 등급 · 3종 능력 시스템 · 능력 3~5개 | 2주 |
| **M8** | 획득 + 진화 | **알 아이템 우클릭** · 랜덤 알 가중치 · 진화 · 과급식 기믹 | 1주 |
| **M9** | 폴리시 | Spark 1회 실측 · Folia · PlaceholderAPI · 문서화 | 1주 |

**합계 약 11주** (1인 파트타임, 모델 제작 시간 제외)

v2.0의 16주 → v2.1에서 12주(5명 규모) → v2.2에서 11주. M5가 2.5주에서 1.5주로 줄었는데, **2.5장에서 검증된 탑승 구조를 확보해 프로토타입 A/B 비교가 필요 없어졌기 때문**이다.

이제 최대 변수는 M5가 아니라 **모델 제작 진척**이다.

**병행**: 8장 규격에 맞춘 모델 제작을 M1과 동시에 진행해야 M2 진입 시 테스트 모델이 준비된다. 탑승 펫 모델(`p_seat` 본 포함)은 M5 전까지 필요하다.

### 마일스톤 완료 조건

1. 기능이 테스트 서버에서 동작한다
2. 해당 범위의 단위 테스트가 통과한다
3. 19장 수동 체크리스트를 통과한다
4. README를 갱신하고 커밋·푸시한다

---

## 21. 리스크

| # | 리스크 | 영향 | 확률 | 대응 |
| --- | --- | --- | --- | --- |
| **R1** | 트래커 누수 → 유령 모델 | 높음 | 중 | `AutoCloseable` + 4경로 close + `registries()` 진단 + M1 DoD |
| **R2** | 탑승 구현 난도 | 중 | 낮음 | **2.5장으로 크게 완화.** `ArmorStand` + `Input` API 기본안이 검증돼 있다. `MountedHitBox`는 선택지로만 |
| **R3** | ~~비행 권한(`allowFlight`) 누수~~ | — | — | ✅ **해소.** `allowFlight`를 쓰지 않는 마운트 방식 채택 (11장) |
| **R13** | **탑승 중 유령 마운트 잔존** | 중 | 중 | 스코어보드 태그 + PDC 식별, 기동 시 청소. 매 틱 생존 검사 후 안전 하차 |
| **R4** | BetterModel 버전 업 시 API 파괴 | 중 | 높음 | `BetterModelBridge` 단일 격리 계층. 버전 핀 고정 |
| **R5** | `AttributeModifier` 누적 버그 | 중 | 높음 | 부착 전 항상 제거 + 접속 시 청소 + 반복 테스트 |
| **R6** | **원작 사양 오해** | 중 | 중 | 2장은 검색 요약 기반이며 **원문 대조를 못 했다.** 실제와 다르면 밸런싱 수치부터 조정 |
| **R7** | 성능 | 낮음 | 낮음 | **5명 규모라 강등**(3장). 18장의 기본 원칙만 지키고 M9에서 1회 확인 |
| **R8** | 모델 제작 지연으로 M2/M5 블로킹 | 중 | 중 | M1과 병렬 착수. 탑승 모델은 M5 전까지 |
| **R9** | 모델 에셋 저작권 | 높음 | 낮음 | 직접 제작 확정으로 완화. 원본 복제 금지 원칙 유지 |
| **R10** | GUI 아이템 복제 취약점 | 높음 | 낮음 | `setCancelled(true)` 선행 + 코드 리뷰 |
| **R11** | Folia 스케줄러 차이 | 낮음 | 낮음 | 5명 서버가 Folia를 쓸 이유는 거의 없다. `PetTicker`에 분기만 캡슐화 |
| **R12** | JDK 25 빌드 환경 부재 | 중 | 중 | 개발 머신에 JDK 25 설치. M0 첫 작업 |

---

## 22. 참고 자료

### BetterModel

- [BetterModel — GitHub](https://github.com/toxicity188/BetterModel)
- [BetterModel — API example (Wiki)](https://github.com/toxicity188/BetterModel/wiki/API-example)
- [BetterModel — Configuring bone tag](https://github.com/toxicity188/BetterModel/wiki/Configuring-bone-tag)
- [BetterModel — Hangar (PaperMC)](https://hangar.papermc.io/toxicity188/BetterModel)
- [BetterModel — 공식 문서](https://mintlify.wiki/toxicity188/BetterModel/introduction)

### 참고 구현 (2.5장 근거)

- [yourShika/betterpets-paper — GitHub](https://github.com/yourShika/betterpets-paper) (MIT) — BetterModel 연동 + 비행 탑승을 구현한 Paper 플러그인
- [BetterPets-Paper — Modrinth](https://modrinth.com/plugin/betterpets-paper)

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
| **v2.2** | 2026-09-08 | **참고 구현 `yourShika/betterpets-paper` 분석(2.5장)** — API 격리·`AutoCloseable` 등 우리 설계가 확인됨 · `plugin.yml`→**`paper-plugin.yml`, api-version `26.2`** 정정 · **탑승을 `MountedHitBox`에서 `ArmorStand`+Paper `Input` API로 전환**(11장), 서브스텝/벽 슬라이딩/안전 하차 기법 도입 · Q9 해소, R3 해소, R13 추가 · 로드맵 12주 → 11주 |
| **v2.1** | 2026-09-08 | **서버 규모 5명 확정(3장)** — 100마리 가정이 20배 과잉이었음. LOD·`viewRange` 튜닝·MySQL 구현체·HikariCP 제거, 성능을 R1→R7로 강등 · **NPC 상점을 알 아이템 우클릭으로 대체(14장)**, Citizens 불필요, Vault 보류 · Q2/Q4/Q5/Q7 해소 · 로드맵 16주 → 12주 |
