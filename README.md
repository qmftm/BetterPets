# 🐾 BetterPets

![Minecraft](https://img.shields.io/badge/Minecraft%20%2F%20Paper-26.2-brightgreen)
![Java](https://img.shields.io/badge/Java-25-orange)
![BetterModel](https://img.shields.io/badge/BetterModel-3.4.1-blue)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

**악어의 놀이터 스타일 펫 플러그인이에요.** 알을 까서 펫을 얻고, 먹이를 주며 키우고,
다 자란 펫에 올라타 돌아다닐 수 있습니다. 운 좋은 펫은 하늘도 날아요.

펫 모습은 [BetterModel](https://github.com/toxicity188/BetterModel)이 그려줍니다.
BlockBench 로 만든 `.bbmodel` 을 그대로 넣으면 되고, 리소스팩이나 데이터팩을 직접
만질 필요가 없어요.

> ### ⚠️ 먼저 알아두세요
>
> **아직 실제 서버에서 돌려본 적이 없습니다.** 기능은 전부 구현됐고 빌드와 단위 테스트
> 128개를 통과하지만, 펫이 화면에 제대로 뜨는지는 `.bbmodel` 모델 파일이 있어야 확인할
> 수 있어요. 자세한 상태는 [ROADMAP.md](docs/ROADMAP.md)에 정리해 뒀습니다.

---

## 어떻게 노나요

```
  알 아이템 우클릭          먹이 주기 · 시간 경과          성체
   🥚  ──────────▶  🐺 아기  ──────────────────▶  🐺 성체  🏇 탑승 가능
                      │                                    ✨ 능력 발현
                      │  짧은 시간에 몰아 먹이면
                      ▼
                     🐷 (이스터에그)
```

1. **알을 우클릭**하면 안에 든 펫이 아기로 나옵니다. 랜덤 알이면 뽑기가 되고요.
2. **먹이를 주거나 그냥 데리고 다니면** 자랍니다 — 시간이 흘러도 1분에 1씩 오릅니다.
3. **다 자라면** 등에 탈 수 있고, 등급별 능력이 주인에게 붙습니다.
4. 하지만 **짧은 시간에 너무 몰아 먹이면**... 돼지가 됩니다. 조심하세요.

---

## 기능

**펫 기르기**
- 알 아이템 우클릭으로 펫 획득 — 고정 알과 가중치 랜덤 알
- 시간(1분당 +1)과 먹이로 자라는 성장도
- 성장 단계마다 다음 형태를 확률로 결정 — 아기 늑대가 드래곤이 될 수도
- 과급식하면 돼지가 되는 이스터에그 (끄고 켤 수 있어요)

**데리고 다니기**
- 3D 모델로 따라다니는 펫 — 정지 / 걷기 / 달리기 / 텔레포트
- 여러 마리 동시 소환 (마릿수는 설정), 부채꼴로 펼쳐 따라옵니다
- 탑승 3종 — 탈 수 없음 / 걸어서 탑승 / 날아서 탑승
- 비행은 등급이 높을수록 확률이 올라가고, 연료 없이 무제한

**등급과 능력**
- D~S 5등급 — 높을수록 빠르고, A등급부터 비행 가능
- 등급별 수치는 `rarity.yml` 에서 직접 조정
- 능력 4종 — 이동속도 · 최대체력 · 공격력 · 처치 시 드롭 추가

**서버 운영**
- 보관함 GUI — 등급·상태·성장도를 한눈에, 소환 중인 펫이 맨 위
- 보유/동시 소환 마릿수 제한
- 희귀 펫을 얻거나 다 키우면 전체 알림 (문턱은 설정)
- 한국어·영어 지원, 언어 추가 가능
- DiscordSRV · PlaceholderAPI · Geyser 연동 (없어도 정상 동작)

---

## 필요한 것

| | 버전 | |
| --- | --- | --- |
| Paper | **26.2+** | Spigot 은 지원하지 않아요 |
| Java | **25** | 아래 참고 |
| [BetterModel](https://hangar.papermc.io/toxicity188/BetterModel) | **3.4.1+** | 필수. 없으면 플러그인이 스스로 꺼집니다 |
| PlaceholderAPI | — | 선택 |
| DiscordSRV | — | 선택 |

> **Java 25가 꼭 필요합니다.** BetterModel 3.x 는 모든 버전이 Java 25로 컴파일돼 있어서,
> JDK 24 이하에서는 BetterModel 자체가 로드되지 않아요. 서버와 빌드 머신 둘 다 25가 필요합니다.

**Bedrock(모바일·콘솔) 플레이어를 받는다면** 아래가 더 필요해요. Bedrock 클라이언트는
BetterModel 의 커스텀 모델을 그냥은 못 봅니다.

| 넣는 곳 | 플러그인 |
| --- | --- |
| `plugins/` | [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine), geyserutils-spigot, packetevents |
| `plugins/[Geyser]/extensions/` | GeyserModelEngineExtension, geyserutils-geyser |

모델도 Bedrock 용으로 따로 내보내야 합니다 — [MODELING.md](docs/MODELING.md#bedrockgeyser-대응) 참고.

---

## 설치

1. [BetterModel](https://hangar.papermc.io/toxicity188/BetterModel) 3.4.1 이상을 `plugins/` 에 넣기
2. `BetterPets-*.jar` 를 `plugins/` 에 넣기
3. `.bbmodel` 모델 파일을 `plugins/BetterModel/models/` 에 넣기 — 규격은 [MODELING.md](docs/MODELING.md)
4. 서버 재시작
5. `/petadmin egg <닉네임> wolf_egg` 로 알을 하나 받아서 우클릭해 보기

처음 켜면 `plugins/BetterPets/` 에 설정 파일이 자동으로 생깁니다. 예시 펫 3종
(늑대·드래곤·돼지)도 같이 들어 있어요.

---

## 명령어

**플레이어용** — `betterpets.use` (기본 허용)

| 명령어 | 설명 |
| --- | --- |
| `/pet` | 보관함 열기 |
| `/pet list` | 채팅으로 목록 보기 — **이름을 클릭하면 바로 소환** |
| `/pet summon <펫id>` | 소환 |
| `/pet dismiss [펫id]` | 돌려보내기. id 를 주면 그 한 마리만, 없으면 전부 |
| `/pet rename [펫id] <이름>` | 이름 바꾸기 |
| `/pet dismount` | 내리기 |
| `/pet help` | 명령어 안내 |

**관리자용** — `betterpets.admin` (기본 OP)

| 명령어 | 설명 |
| --- | --- |
| `/petadmin give <플레이어> <펫종류>` | 펫 바로 지급 |
| `/petadmin egg <플레이어> <알id> [개수]` | 알 아이템 지급 |
| `/petadmin feed <플레이어> <먹이id> [개수]` | 먹이 아이템 지급 |
| `/petadmin growth <플레이어> <펫id> <양>` | 성장도 지급 |
| `/petadmin reload` | 설정 다시 읽기 |
| `/petadmin debug` | 누수 진단 · 현재 한도 · 연동 상태 |

펫 id 는 **앞 8자리만** 쳐도 됩니다. 탭 완성도 되고요.

---

## 설정

```
plugins/BetterPets/
├─ config.yml       한도 · 알림 · 연동 · 성장 · 기믹 · 비행
├─ rarity.yml       등급별 수치 (속도 · 비행 확률 · 색)
├─ items.yml        알 · 먹이 아이템
├─ lang/*.yml       화면에 보이는 모든 문구 (ko_kr · en_us)
├─ pets/*.yml       펫 종류 정의
└─ playerdata/      플레이어별 펫 데이터 (건드리지 마세요)
```

### 펫 만들기

`pets/` 에 파일을 하나 더 넣으면 새 펫이 됩니다.

```yaml
# pets/dragon.yml
id: dragon
display-name: "<gold>드래곤"
model: pet_dragon          # plugins/BetterModel/models/pet_dragon.bbmodel
rarity: S
growth-max: 100

ride: FLY                  # NONE(못 탐) / GROUND(걸어서) / FLY(날아서)
fly-chance: 0.35           # FLY 라도 실제로 날 확률. 실패하면 걷는 탑승

animations:                # 모델의 애니메이션 이름과 연결
  idle: idle
  walk: walk
  run: run
  fly: fly
  ride: ride

abilities:
  - id: attribute_speed
    base: 0.05
    per-growth: 0.0005

acquire:
  gacha-weight: 1          # 랜덤 알에서 뽑힐 가중치
```

### 보유·소환 마릿수

```yaml
# config.yml
pets:
  max-owned: 20    # 한 명이 가질 수 있는 마릿수. 0 = 무제한
  max-active: 1    # 동시에 꺼내둘 수 있는 마릿수. 0 = 무제한
```

- 보유 한도가 차면 알을 우클릭해도 **아이템이 사라지지 않아요.** 안내만 나갑니다.
- 동시 소환 한도가 찬 상태에서 새로 부르면 **가장 먼저 부른 펫이 돌아갑니다.**
  그래서 기본값 1은 "부르면 교체"로 동작해요.
- `max-active` 를 올리면 소환한 마릿수만큼 능력 보너스가 **겹쳐서** 붙습니다.

### 등급 수치

```yaml
# rarity.yml — 안 적은 등급·항목은 기본값을 씁니다
rarities:
  S:
    display-name: "전설"
    move-speed: 1.70     # 추종 이동 배율
    ride-speed: 0.42     # 탑승 속도
    fly-chance: 0.35     # 성체가 될 때 비행이 붙을 확률
    color: "FFAA00"      # 모델 색 · GUI 표시색
```

> 기본 수치는 **전부 임시값이에요.** 원작의 실제 값은 확인하지 못했습니다. 확실한 건
> "등급이 오를수록 빨라진다"와 "비행은 A등급부터" 둘뿐이라, 서버에 맞게 고쳐 쓰시라고
> 파일로 뺐어요.

### 능력

**능력은 펫이 아니라 주인에게 붙습니다.** 펫이 싸우는 게 아니라, 주인이 강해져요.

| id | 종류 | 하는 일 | 수치 키 |
| --- | --- | --- | --- |
| `attribute_speed` | 패시브 | 이동속도 증가 | `base` · `per-growth` |
| `attribute_health` | 패시브 | 최대 체력 증가 | `base` · `per-growth` |
| `attribute_damage` | 패시브 | 공격력 증가 | `base` · `per-growth` |
| `on_kill_extra_drop` | 트리거 | 몹 처치 시 확률로 드롭 한 벌 더 | `chance-base` · `chance-per-growth` |

패시브 수치는 `(base + per-growth × 성장도) × 등급 이동속도 배율` 로 계산합니다.

**성체부터 발현돼요.** 아기와 돼지는 능력이 없고, 보관함 상세 화면이 그 사실과 현재
수치를 같이 보여줍니다. 꺼내둔 채로 성체가 되면 다시 부르지 않아도 바로 붙어요.

> `on_kill_extra_drop` 은 등급 배율을 곱하지 않습니다. 확률에 1.7배를 곱하면 상한(100%)에
> 금방 붙어서 등급 차이가 오히려 뭉개지거든요. 등급별 차이는 `chance-base` 로 냅니다.

### 알림과 연동

```yaml
broadcast:
  enabled: true
  min-rarity: A          # 이 등급 이상만 알림. D 로 두면 채팅이 밀려요
  min-growth-stage: 1
  on-obtain: true        # 알에서 나왔을 때
  on-grown: true         # 다 자랐을 때
  on-stage-up: false     # 성장 단계가 올랐을 때
  sound: true

integrations:
  discord:
    enabled: true
    channel: global      # DiscordSRV 의 게임 채널 이름
  bedrock:
    skip-mount-confirm: true
```

- **DiscordSRV** — 같은 알림이 디스코드로도 갑니다. 없으면 조용히 꺼져요.
- **PlaceholderAPI** — 설정 없이 자동으로 붙습니다. `%betterpets_pet_name%`,
  `%betterpets_owned%`, `%betterpets_active%` 등.
- **Bedrock** — Floodgate 가 있으면 Bedrock 플레이어를 알아보고, 비행 이륙 확인
  (한 번 더 우클릭)을 건너뜁니다. 터치로는 맞히기 어려우니까요.

### 언어

```yaml
# config.yml
language: ko_kr
```

`lang/ko_kr.yml` 을 복사해서 `lang/<코드>.yml` 로 저장하면 새 언어가 됩니다.
**안 적은 키는 한국어로 채워지니까 바꾸고 싶은 줄만 남겨도 돼요.** 플러그인이
업데이트돼서 문구가 늘어도 번역 파일이 깨지지 않습니다.

---

## 모델 만들기

펫 모델은 직접 만드셔야 해요. 플러그인이 기대하는 규격이 있습니다:

- **애니메이션 이름** — `idle` · `walk` · `run` 은 필수. 비행 펫은 `fly`, 탑승 펫은 `ride` 추가
- **본 태그** — `h_head`(머리 회전), `b_body`(히트박스), `p_seat`(탑승 좌석) 처럼
  접두사로 기능을 붙입니다

전체 규격과 예시는 **[docs/MODELING.md](docs/MODELING.md)** 에 있어요.

> 악어의 놀이터에서 쓰는 실제 모델·텍스처를 추출해 쓰는 건 저작권 침해입니다.
> 시스템 구조를 참고하는 것과 에셋을 복제하는 건 다른 얘기예요.

---

## 자주 묻는 것

**펫이 안 보여요**
`.bbmodel` 파일이 `plugins/BetterModel/models/` 에 있는지, `pets/*.yml` 의 `model:` 값이
파일 이름과 같은지 확인해 주세요. `/petadmin debug` 로 트래커가 잡혔는지도 볼 수 있어요.

**펫이 벽에 끼거나 뒤처져요**
3초 동안 가까워지지 못하면 자동으로 순간이동합니다. 너무 자주 그러면 `pets/*.yml` 의
`teleport-distance` 를 줄여 보세요.

**설정을 고쳤는데 그대로예요**
`/petadmin reload` 를 쳐 주세요. 문제가 있으면 뭐가 잘못됐는지 하나씩 알려줍니다.

**펫을 여러 마리 꺼내고 싶어요**
`config.yml` 의 `pets.max-active` 를 올리세요. 소환한 마릿수만큼 능력이 겹치니
밸런스도 같이 봐 주세요.

**디스코드로 알림이 안 가요**
`/petadmin debug` 의 연동 줄에서 DiscordSRV 가 `(O)` 인지 보세요. `(X)` 면 플러그인이
없는 거고, `(O)` 인데도 안 가면 `integrations.discord.channel` 이름이 DiscordSRV 쪽
채널 이름과 같은지 확인해 주세요.

---

## 빌드

```bash
export JAVA_HOME=/path/to/jdk-25     # 25 필수
mvn clean package                    # target/BetterPets-*.jar
mvn test                             # 테스트만
```

**외부 런타임 의존성이 없습니다.** 저장은 YAML 로 해요 — 소규모 서버에 DB는 과하고,
문제가 생겼을 때 사람이 직접 열어 고칠 수 있으니까요.

---

## 문서

| 문서 | 내용 |
| --- | --- |
| **[docs/MODELING.md](docs/MODELING.md)** | 모델 제작 규격 — 애니메이션 이름, 본 태그, 권장 구성 |
| **[docs/DESIGN.md](docs/DESIGN.md)** | 설계와 그 근거 — 아키텍처, BetterModel API, 원작 분석, 리스크 |
| **[docs/ROADMAP.md](docs/ROADMAP.md)** | 개발 진행 상황과 남은 검증 |
| **[CLAUDE.md](CLAUDE.md)** | 코드에 손댈 때 지키는 규칙 |

---

## 참고

- [BetterModel](https://github.com/toxicity188/BetterModel) — 렌더링 엔진 (MIT)
- [betterpets-paper](https://github.com/yourShika/betterpets-paper) — 탑승·비행 구현을 참고한 플러그인 (MIT)
- [악어의 놀이터 2](https://namu.wiki/w/%EC%95%85%EC%96%B4(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EB%B0%A9%EC%86%A1%EC%9D%B8)/%EB%8C%80%EA%B7%9C%EB%AA%A8%20%EC%BD%98%ED%85%90%EC%B8%A0/%EC%95%85%EC%96%B4%EC%9D%98%20%EB%86%80%EC%9D%B4%ED%84%B0%202) — 원작 시스템
