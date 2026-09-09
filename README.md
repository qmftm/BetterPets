# 🐾 BetterPets

![Minecraft](https://img.shields.io/badge/Minecraft%20%2F%20Paper-26.2-brightgreen)
![Java](https://img.shields.io/badge/Java-25-orange)
![BetterModel](https://img.shields.io/badge/BetterModel-3.4.1-blue)
![Build](https://img.shields.io/badge/Build-Maven-C71A36)
![Status](https://img.shields.io/badge/Status-개발%20중%20(M1)-yellow)

악어의 놀이터 스타일 펫 시스템. 알을 부화시켜 펫을 키우고, 성장한 펫을 타고 다니며, 일부는 하늘을 난다.

렌더링은 [BetterModel](https://github.com/toxicity188/BetterModel)이 맡는다 — 리소스팩이나 데이터팩을 직접 다룰 필요 없이 BlockBench 모델을 그대로 쓴다.

> ⚠️ **아직 배포 가능한 상태가 아니다.** 뼈대와 도메인 로직만 있고 실제 펫은 아직 소환되지 않는다. 진행 상황은 아래 [개발 단계](#개발-단계) 참고.

---

## 개발 단계

전체 10단계 중 **1단계 완료**. 각 단계는 앞 단계 위에 쌓이며, 완료 조건을 모두 채워야 다음으로 넘어간다.

| 단계 | 내용 | 상태 |
| :---: | --- | :---: |
| **M0** | **프로젝트 뼈대** — Maven 빌드, `paper-plugin.yml`, 진입점, 렌더 격리 계층, 도메인(등급·생애주기·성장도) | ✅ **완료** |
| **M1** | **렌더링** — `/pet summon` 으로 모델 소환/해제, 캐리어 엔티티 확정, 트래커 누수 0 검증 | 🔨 **진행 예정** |
| **M2** | **이동 · 애니메이션** — 추종 상태 머신, 텔레포트 폴백, 애니메이션 전이 | ⬜ |
| **M3** | **데이터 · 명령어** — 저장소, 비동기 I/O, 전체 명령어와 권한 | ⬜ |
| **M4** | **생애주기** — 알 → 부화 → 아기 → 성체, 성장도(시간 + 먹이) | ⬜ |
| **M5** | **탑승 · 비행** — 마운트 조향, 충돌 처리, 안전 하차 | ⬜ |
| **M6** | **GUI** — 보관함, 상세, 부화/급여 | ⬜ |
| **M7** | **등급 · 능력** — D~S 등급, 패시브/트리거/액티브 능력 | ⬜ |
| **M8** | **획득 · 진화** — 알 아이템 우클릭, 랜덤 알, 진화 | ⬜ |
| **M9** | **폴리시** — 성능 실측, PlaceholderAPI, 문서 정리 | ⬜ |

남은 기간 약 11주 (1인 파트타임 기준, 모델 제작 시간 제외).

### 각 단계의 완료 조건

1. 기능이 테스트 서버에서 동작한다
2. 해당 범위의 단위 테스트가 통과한다
3. [수동 체크리스트](docs/DESIGN.md#테스트)를 통과한다
4. README를 갱신하고 커밋·푸시한다

### M0에서 확정된 것

- **빌드가 실제로 통과한다** — JDK 25로 BetterModel 3.4.1 API에 대해 컴파일 검증 완료, 테스트 24개 통과
- **BetterModel API 격리 계층** — `PetRenderer` / `PetRenderHandle` 인터페이스 뒤에 BetterModel을 숨겼다. `BetterModelRenderer` 가 그 API를 import하는 유일한 파일이다
- **도메인 로직** — 등급(D~S), 생애주기(EGG~PIG), 성장도 지연 계산. Bukkit에 의존하지 않아 단위 테스트가 가능하다

### M1에서 결정할 것

- **캐리어 엔티티** — 보이지 않는 `Mob` vs `ItemDisplay`. BetterModel이 양쪽 모두에 붙는 것은 확인됐고, 히트박스·이름표를 본 태그로 얻을지 별도 엔티티로 둘지가 쟁점
- **트래커 누수 진단** — `/petadmin debug` 로 활성 펫 수와 엔진 트래커 수를 대조

---

## 기능

계획된 전체 기능. 체크된 것만 구현돼 있다.

**펫 기르기**
- [ ] 알 아이템 우클릭으로 펫 획득 (고정 알 / 가중치 랜덤 알)
- [ ] 먹이를 줘서 부화
- [ ] 성장도 — 시간 경과 1분당 +1, 먹이 +10
- [ ] 알 → 아기 → 성체 생애주기
- [ ] 과급식 시 돼지로 변하는 기믹

**데리고 다니기**
- [ ] 3D 모델 렌더링 (BetterModel)
- [ ] 추종 이동 — 정지 / 걷기 / 달리기 / 텔레포트
- [ ] 상태별 애니메이션 전환

**타고 다니기**
- [ ] 지상 탑승
- [ ] 비행 — A등급 이상에서 확률적으로, 연료 없이 무제한
- [ ] 스니크 하차, 낙하 피해 방지

**성장과 능력**
- [x] 등급 D~S — 등급이 오를수록 이동속도 상승, A등급부터 비행 가능
- [ ] 패시브 능력 (상시 버프)
- [ ] 트리거 능력 (이벤트 반응)
- [ ] 액티브 능력 (쿨다운 발동)
- [ ] 진화

**관리**
- [ ] 펫 보관함 GUI
- [ ] 이름 변경 · 해방
- [ ] PlaceholderAPI 연동

---

## 요구 사항

| 항목 | 버전 | 비고 |
| --- | --- | --- |
| 서버 | **Paper 26.2** | Spigot은 지원하지 않는다 |
| Java | **25** | 아래 주의 참고 |
| [BetterModel](https://hangar.papermc.io/toxicity188/BetterModel) | **3.4.1+** | 필수. 없으면 플러그인이 비활성화된다 |
| PlaceholderAPI | — | 선택 |

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
├─ config.yml       일반 설정, 저장소, 기믹 on/off
├─ messages.yml     사용자 노출 문자열
├─ gui.yml          GUI 레이아웃
├─ rarity.yml       등급별 수치
├─ eggs.yml         알 아이템 정의
└─ pets/*.yml       펫 종류 정의
```

펫 하나를 정의하는 예:

```yaml
# pets/dragon.yml
id: dragon
display-name: "&6드래곤"
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

현재 외부 런타임 의존성은 없다. 저장소 백엔드는 M3에서 정한다.

---

## 프로젝트 구조

```
kr.qmftm.betterpets
├─ BetterPetsPlugin        진입점 — 배선만 한다
├─ domain/                 순수 로직. Bukkit 비의존이라 테스트 가능
│   ├─ Rarity              등급 D~S
│   ├─ LifeStage           EGG · HATCHING · BABY · ADULT · PIG
│   └─ GrowthCurve         성장도 지연 계산
└─ render/                 BetterModel 격리 계층
    ├─ PetRenderer         인터페이스
    ├─ PetRenderHandle     AutoCloseable 핸들
    └─ BetterModelRenderer ★ BetterModel API를 import하는 유일한 파일
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
