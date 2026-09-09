# 🐾 BetterPets

![Minecraft](https://img.shields.io/badge/Minecraft%20%2F%20Paper-26.2-brightgreen)
![Java](https://img.shields.io/badge/Java-25-orange)
![BetterModel](https://img.shields.io/badge/BetterModel-3.4.1-blue)
![Build](https://img.shields.io/badge/Build-Maven-C71A36)
![Status](https://img.shields.io/badge/Status-검증%20대기%20(M9)-yellow)

악어의 놀이터 스타일 펫 시스템. 알을 부화시켜 펫을 키우고, 성장한 펫을 타고 다니며, 일부는 하늘을 난다.

렌더링은 [BetterModel](https://github.com/toxicity188/BetterModel)이 맡는다 — 리소스팩이나 데이터팩을 직접 다룰 필요 없이 BlockBench 모델을 그대로 쓴다.

> ⚠️ **아직 실제 서버에서 검증되지 않았다.** 기능 전체가 구현돼 빌드와 단위 테스트는 통과하지만, **모델 파일이 있어야 실제로 펫이 뜨는지 확인할 수 있다.** 진행 상황은 아래 [개발 단계](#개발-단계) 참고.

---

## 개발 단계

전체 11단계 중 **9단계 코드 완료**. 남은 것은 실제 서버 검증과 Bedrock 대응이다.

"코드 완료"는 구현·빌드·단위 테스트가 끝났다는 뜻이고, **테스트 서버에서 확인된 것은 아니다.** 그건 모델 파일이 필요하다.

| 단계 | 내용 | 상태 |
| :---: | --- | :---: |
| **M0** | **프로젝트 뼈대** — Maven 빌드, `paper-plugin.yml`, 진입점, 렌더 격리 계층, 도메인(등급·생애주기·성장도) | ✅ **완료** |
| **M1** | **렌더링** — `/pet summon`, 캐리어 엔티티(보이지 않는 Mob), 트래커 격리 계층 | ✅ **코드 완료** |
| **M2** | **이동 · 애니메이션** — 추종 상태 머신, 텔레포트 폴백, 전이 시에만 애니메이션 호출 | ✅ **코드 완료** |
| **M3** | **데이터 · 명령어** — YAML 저장소, 단일 스레드 비동기 I/O, `/pet` `/petadmin` | ✅ **코드 완료** |
| **M4** | **생애주기** — 알 → 부화 → 아기 → 성체, 성장도(시간 + 먹이), 과급식 기믹 | ✅ **코드 완료** |
| **M5** | **탑승 · 비행** — ArmorStand 마운트, Paper Input 조향, 서브스텝/벽 슬라이딩, 안전 하차 | ✅ **코드 완료** |
| **M6** | **GUI** — 보관함, 상세, 등급·성장도 표시 | ✅ **코드 완료** |
| **M7** | **등급 · 능력** — D~S 등급, 패시브/트리거 능력, 모디파이어 누적 방지 | ✅ **코드 완료** |
| **M8** | **획득 · 진화** — 알 아이템 우클릭(PDC 식별), 고정/랜덤 알, 진화 | ✅ **코드 완료** |
| **M9** | **폴리시** — 성능 실측, PlaceholderAPI, 문서 정리 | ⬜ |
| **M10** | **Bedrock 지원** — 전체 모델 변환, 리소스팩 파이프라인, Bedrock 실기 테스트 | ⬜ |

남은 작업은 대부분 **직접 서버에서 확인**하는 일이다. 코드 작성은 끝났다.

> **Bedrock 검증은 M1에서 미리 한 번 한다.** 서버가 Bedrock 플레이어를 받으므로 [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine)이 필요한데, 조합(BetterModel 3.4.1 + GME + MC 26.2)이 검증된 적 없다. **모델을 여러 개 만든 뒤에 안 되는 걸 알면 늦으므로**, M1에서 첫 모델이 자바에 뜨자마자 그 하나로 Bedrock 변환을 시험한다. 되면 M10에서 나머지를 처리하고, 안 되면 그때 대안을 찾는다.

### 각 단계의 완료 조건

1. 기능이 테스트 서버에서 동작한다
2. 해당 범위의 단위 테스트가 통과한다
3. [수동 체크리스트](docs/DESIGN.md#테스트)를 통과한다
4. README를 갱신하고 커밋·푸시한다

### 지금까지 확정된 것

- **빌드가 통과한다** — JDK 25로 BetterModel 3.4.1 API에 대해 실제 컴파일, 단위 테스트 37개 통과
- **캐리어 엔티티는 보이지 않는 `Mob`** — `ItemDisplay` 대신 고른 이유는 자체 히트박스가 있어서다. 모델에 히트박스 본(`b_`)이 없어도 우클릭이 먹는다
- **BetterModel API 격리** — `BetterModelRenderer` 가 그 API를 import하는 유일한 파일이다
- **탑승은 `ArmorStand` + Paper `Input`** — `allowFlight` 를 쓰지 않으므로 비행 권한이 샐 위험 자체가 없다

### 다음에 직접 확인할 것

코드로는 더 할 게 없고, **테스트 서버에서 확인해야 하는 것들**이다.

1. **모델이 실제로 뜨는가** — `.bbmodel` 하나를 넣고 `/petadmin egg`, 먹이, `/pet summon` 순으로
2. **트래커 누수가 없는가** — `/petadmin debug` 의 세 숫자(활성 펫 / 캐리어 / 트래커)가 일치하는지
3. **추종 이동이 자연스러운가** — 계단·언덕에서 끊기지 않는지, 텔레포트 폴백이 과하지 않은지
4. **탑승·비행 조작감** — 벽 슬라이딩과 하차 안전 처리가 실제로 먹는지
5. **Bedrock 변환** — 모델 하나로 GeyserModelEngine 파이프라인을 끝까지 통과시켜 본다

---

## 기능

체크된 것은 **코드가 있고 빌드·테스트를 통과했다**는 뜻이다. 실제 서버 확인은 별개다.

**펫 기르기**
- [x] 알 아이템 우클릭으로 펫 획득 (고정 알 / 가중치 랜덤 알)
- [x] 먹이를 줘서 부화
- [x] 성장도 — 시간 경과 1분당 +1, 먹이 +10
- [x] 알 → 아기 → 성체 생애주기
- [x] 과급식 시 돼지로 변하는 기믹

**데리고 다니기**
- [x] 3D 모델 렌더링 (BetterModel)
- [x] 추종 이동 — 정지 / 걷기 / 달리기 / 텔레포트
- [x] 상태별 애니메이션 전환

**타고 다니기**
- [x] 지상 탑승
- [x] 비행 — A등급 이상에서 확률적으로, 연료 없이 무제한
- [x] 스니크 하차, 낙하 피해 방지

**성장과 능력**
- [x] 등급 D~S — 등급이 오를수록 이동속도 상승, A등급부터 비행 가능
- [x] 패시브 능력 (상시 버프)
- [x] 트리거 능력 (이벤트 반응)
- [x] 진화

**관리**
- [x] 펫 보관함 GUI
- [x] 이름 변경 · 해방
- [ ] PlaceholderAPI 연동

---

## 요구 사항

| 항목 | 버전 | 비고 |
| --- | --- | --- |
| 서버 | **Paper 26.2** | Spigot은 지원하지 않는다 |
| Java | **25** | 아래 주의 참고 |
| [BetterModel](https://hangar.papermc.io/toxicity188/BetterModel) | **3.4.1+** | 필수. 없으면 플러그인이 비활성화된다 |
| PlaceholderAPI | — | 선택 |

**Bedrock 플레이어를 받는다면** 아래가 추가로 필요하다. Bedrock 클라이언트는 BetterModel의 커스텀 모델을 그대로 볼 수 없다.

| 위치 | 플러그인 |
| --- | --- |
| `plugins/` | [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine), geyserutils-spigot, packetevents |
| `plugins/[Geyser]/extensions/` | GeyserModelEngineExtension, geyserutils-geyser |

모델도 Bedrock용으로 따로 내보내야 한다 — [MODELING.md](docs/MODELING.md#bedrockgeyser-대응) 참고.

> ⚠️ **Java 25가 필수다.** BetterModel 3.x는 모든 버전이 클래스 파일 major 69(Java 25)로 배포되므로, JDK 24 이하에서는 BetterModel 자체가 로드되지 않는다. 서버와 빌드 머신 모두 JDK 25가 필요하다.

**대상 규모** — 동시 접속 5명 내외의 소규모 서버를 전제로 설계했다. 거리 LOD나 커넥션 풀 같은 대규모 최적화는 의도적으로 넣지 않았다. 근거는 [DESIGN.md](docs/DESIGN.md#설계-전제--서버-규모)에 있다.

---

## 설치

1. [BetterModel](https://hangar.papermc.io/toxicity188/BetterModel) 3.4.1 이상을 `plugins/` 에 넣는다
2. `BetterPets-*.jar` 를 `plugins/` 에 넣는다
3. `.bbmodel` 파일을 `plugins/BetterModel/models/` 에 넣는다 — 규격은 [MODELING.md](docs/MODELING.md)
4. 서버를 재시작한다

---

## 명령어

| 명령어 | 권한 | 설명 |
| --- | --- | --- |
| `/pet` | `betterpets.use` | 보관함 GUI |
| `/pet summon <petId>` | `betterpets.use` | 소환 |
| `/pet dismiss` | `betterpets.use` | 해제 |
| `/pet dismount` | `betterpets.use` | 하차 |
| `/pet rename <이름>` | `betterpets.use` | 이름 변경 |
| `/petadmin give <플레이어> <타입>` | `betterpets.admin` | 펫 지급 |
| `/petadmin egg <플레이어> <알> [개수]` | `betterpets.admin` | 알 아이템 지급 |
| `/petadmin growth <플레이어> <petId> <양>` | `betterpets.admin` | 성장도 지급 |
| `/petadmin reload` | `betterpets.admin` | 설정 리로드 |
| `/petadmin debug` | `betterpets.admin` | 트래커 누수 진단 |

## 권한

| 권한 | 기본값 | 범위 |
| --- | --- | --- |
| `betterpets.use` | 모두 | 보관함, 소환/해제, 이름 변경 |
| `betterpets.admin` | OP | 지급, 리로드, 진단 |

---

## 설정

```
plugins/BetterPets/
├─ config.yml       성장·기믹·비행 설정
├─ messages.yml     사용자 노출 문자열 (MiniMessage)
├─ eggs.yml         알 아이템 정의
├─ pets/*.yml       펫 종류 정의
└─ playerdata/      플레이어별 펫 데이터 (자동 생성)
```

펫 하나를 정의하는 예:

```yaml
# pets/dragon.yml
id: dragon
display-name: "<gold>드래곤"
model: pet_dragon          # plugins/BetterModel/models/pet_dragon.bbmodel
rarity: S
growth-max: 100

rideable: true
fly-chance: 0.35           # 부화 시 이 확률로 비행 능력 확정

animations:
  idle: idle
  walk: walk
  run: run
  fly: fly
  ride: ride

movement:
  follow-distance: 2.0
  walk-speed: 0.25
  run-speed: 0.45
  teleport-distance: 24.0

acquire:
  gacha-weight: 1          # 랜덤 알에서 뽑힐 가중치
```

설정 항목 전체는 [DESIGN.md](docs/DESIGN.md#설정-파일) 참고.

---

## 모델 제작

펫 모델은 직접 만든다. 플러그인이 기대하는 규격이 있다:

- **애니메이션 이름** — `idle` · `walk` · `run` 은 필수. 비행 펫은 `fly`, 탑승 펫은 `ride` 추가
- **본 태그** — `h_head`(머리 회전), `b_body`(히트박스), `p_seat`(탑승 좌석) 등 접두사로 기능을 붙인다

전체 규격은 **[docs/MODELING.md](docs/MODELING.md)** 에 있다.

> 악어의 놀이터에서 쓰는 실제 모델·텍스처를 추출해 쓰는 것은 저작권 침해다. 시스템 구조를 참고하는 것과 에셋을 복제하는 것은 다르다.

---

## 빌드

```bash
export JAVA_HOME=/path/to/jdk-25     # JDK 25 필수
mvn clean package                    # target/BetterPets-*.jar
mvn test                             # 단위 테스트만
```

빌드하며 실측으로 확인한 것 두 가지:

1. **`maven-shade-plugin` 은 3.6.2 이상**이어야 한다. 3.6.0은 번들 ASM이 낡아 Java 25 클래스를 못 읽고 `Unsupported class file major version 69` 로 실패한다.
2. **셰이딩보다 Paper 라이브러리 로더가 낫다.** sqlite-jdbc를 셰이딩했더니 전 플랫폼 네이티브가 딸려와 jar가 16KB → 14MB가 됐다.

**외부 런타임 의존성이 없다.** 저장소는 YAML을 쓴다 — 5명 규모에 DB는 과잉이고, 문제가 생겼을 때 사람이 직접 열어 고칠 수 있다.

---

## 프로젝트 구조

```
kr.qmftm.betterpets
├─ BetterPetsPlugin        진입점 — 배선만 한다
├─ domain/                 순수 로직. Bukkit 비의존이라 테스트 가능
│   ├─ Rarity              등급 D~S
│   ├─ LifeStage           EGG · HATCHING · BABY · ADULT · PIG
│   ├─ GrowthCurve         성장도 지연 계산
│   ├─ PetType             펫 종류 정의 (설정에서 로드)
│   ├─ PetData             펫 개체 (저장 대상)
│   └─ EggDefinition       알 아이템 정의, 가중치 추첨
├─ render/                 BetterModel 격리 계층
│   ├─ PetRenderer         인터페이스
│   ├─ PetRenderHandle     AutoCloseable 핸들
│   └─ BetterModelRenderer ★ BetterModel API를 import하는 유일한 파일
├─ runtime/                살아 움직이는 부분
│   ├─ CarrierFactory      캐리어 엔티티 스폰 · 고아 청소
│   ├─ ActivePet           캐리어 + 트래커 + 상태 한 덩어리
│   ├─ PetRegistry         소유자 → 소환된 펫
│   ├─ MovementController  추종 상태 머신
│   ├─ AnimationStateMachine  전이 시에만 animate() 호출
│   ├─ RideController      ArmorStand 마운트, 서브스텝 충돌
│   └─ PetTicker           전역 틱 루프 (추종 2틱 / 탑승 1틱)
├─ service/                PetService · GrowthService · AbilityService
├─ ability/                능력 인터페이스 + 등록소 + 구현체
├─ storage/                PetRepository · YamlPetRepository · PetStore
├─ config/                 PetCatalog (검증 포함) · Messages
├─ item/                   PetItems — PDC 기반 알·먹이 식별
├─ gui/                    Menus (홀더) · PetMenuFactory
├─ command/                PetCommand · PetAdminCommand
└─ listener/               Session · Interaction · Menu · AbilityTrigger
```

계층 분리를 유지하는 이유가 있다. 같은 일을 하는 참고 플러그인은 12,816줄 중 69%가 단 두 파일에 몰려 있다. **한 파일이 800줄을 넘으면 분리 신호로 본다.**

---

## 문서

| 문서 | 내용 |
| --- | --- |
| **[docs/DESIGN.md](docs/DESIGN.md)** | 아키텍처, 검증된 BetterModel API, 원작 시스템 분석, 참고 구현 분석, 리스크 |
| **[docs/MODELING.md](docs/MODELING.md)** | 모델 제작 규격 — 애니메이션 이름 계약, 본 태그, 권장 구성 |

---

## 참고

- [BetterModel](https://github.com/toxicity188/BetterModel) — 렌더링 엔진 (MIT)
- [betterpets-paper](https://github.com/yourShika/betterpets-paper) — 탑승·비행 구현을 참고한 플러그인 (MIT)
- [악어의 놀이터 2](https://namu.wiki/w/%EC%95%85%EC%96%B4(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EB%B0%A9%EC%86%A1%EC%9D%B8)/%EB%8C%80%EA%B7%9C%EB%AA%A8%20%EC%BD%98%ED%85%90%EC%B8%A0/%EC%95%85%EC%96%B4%EC%9D%98%20%EB%86%80%EC%9D%B4%ED%84%B0%202) — 원작 시스템
