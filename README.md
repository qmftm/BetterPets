# 🐾 BetterPets

_악어의 놀이터 스타일 펫 시스템 — Paper 26.2 · BetterModel 기반_

![Minecraft](https://img.shields.io/badge/Minecraft%20%2F%20Paper-26.2-brightgreen)
![Java](https://img.shields.io/badge/Java-25-orange)
![BetterModel](https://img.shields.io/badge/BetterModel-3.4.1+-blue)
![Language](https://img.shields.io/badge/Language-KO%20%2F%20EN-yellow)
![Status](https://img.shields.io/badge/Status-서버%20검증%20대기-lightgrey)

---

## ✨ 소개

알을 까면 아기 펫이 나옵니다. 먹이를 주거나 그냥 데리고 다니면 자라고, 다 크면
등에 올라탈 수 있어요. 운이 좋으면 하늘을 나는 펫이 나옵니다.

펫의 겉모습은 [BetterModel](https://github.com/toxicity188/BetterModel)이 그려줍니다.
BlockBench 로 만든 `.bbmodel` 을 넣으면 끝이고, 리소스팩이나 데이터팩을 직접 만들 필요가
없어요.

> ### ⚠️ 아직 서버에서 돌려본 적이 없습니다
>
> 기능은 전부 구현됐고 빌드와 단위 테스트 128개를 통과하지만, 펫이 화면에 제대로 뜨는지는
> `.bbmodel` 모델 파일이 있어야 확인할 수 있어요. 자세한 상태는 [ROADMAP](docs/ROADMAP.md)에
> 정리해 뒀습니다.

---

## 🎯 이런 서버에 맞아요

- **소규모 서버** — 동시 접속 5명 내외를 전제로 설계했어요. 거리 LOD 같은 대규모 최적화는
  일부러 넣지 않았습니다 ([이유](docs/DESIGN.md#설계-전제--서버-규모))
- **펫을 키우는 재미가 필요한 서버** — 뽑기 → 육성 → 탑승으로 이어지는 흐름이 통째로 들어 있어요
- **자바와 Bedrock 을 같이 받는 서버** — Geyser 연동을 고려했습니다

---

## 🌟 기능

**🥚 기르기**
- 알 우클릭으로 펫 획득 — 고정 알 / 가중치 랜덤 알
- 시간(1분당 +1)과 먹이로 오르는 성장도
- 성장 단계마다 다음 형태를 확률로 결정 — 아기 늑대가 드래곤이 될 수도 있어요
- 너무 몰아 먹이면 돼지가 되는 이스터에그 (끌 수 있습니다)

**🏇 데리고 다니기**
- 3D 모델로 따라오는 펫 — 정지 / 걷기 / 달리기 / 텔레포트 폴백
- 여러 마리 동시 소환, 부채꼴로 펼쳐서 따라옵니다
- 탑승 3종 — 못 탐 / 걸어서 / 날아서. 비행은 연료 없이 무제한
- 스니크로 하차, 낙하 피해 없음

**⭐ 등급과 능력**
- D~S 5등급 — 높을수록 빠르고, A등급부터 비행 가능
- 등급 수치를 `rarity.yml` 에서 직접 조정
- 능력 4종 — 이동속도 · 최대체력 · 공격력 · 처치 시 드롭 추가

**🧰 운영**
- 보관함 GUI — 등급 · 상태 · 성장도를 한눈에, 소환 중인 펫이 맨 위
- 보유 / 동시 소환 마릿수 제한
- 희귀 펫을 얻거나 다 키우면 서버 전체 알림
- 한국어 · 영어 지원, 언어 추가 가능
- 외부 런타임 의존성 없음 (저장은 YAML)

---

## 📦 설치

| | 버전 | |
| --- | --- | --- |
| Paper | **26.2+** | Spigot 은 지원하지 않아요 |
| Java | **25** | 아래 참고 |
| [BetterModel](https://hangar.papermc.io/toxicity188/BetterModel) | **3.4.1+** | 필수. 없으면 플러그인이 스스로 꺼집니다 |

1. BetterModel 을 `plugins/` 에 넣기
2. `BetterPets-*.jar` 를 `plugins/` 에 넣기
3. `.bbmodel` 모델을 `plugins/BetterModel/models/` 에 넣기 → [모델 규격](docs/MODELING.md)
4. 서버 재시작
5. `/petadmin egg <닉네임> wolf_egg` 로 알을 받아 우클릭해 보기

처음 켜면 `plugins/BetterPets/` 에 설정 파일이 전부 생깁니다. **예시 펫 3종(늑대 · 드래곤 ·
돼지)도 같이 들어 있어서**, 파일을 열어보고 고치는 식으로 시작하면 돼요.
고친 뒤엔 `/petadmin reload` 로 바로 적용됩니다.

> **⚠️ Java 25가 꼭 필요해요.** BetterModel 3.x 는 모든 버전이 Java 25로 컴파일돼 있어서,
> JDK 24 이하에서는 BetterModel 자체가 로드되지 않습니다. 서버와 빌드 머신 둘 다 25여야 해요.

---

## 💬 명령어

**플레이어**

| 명령어 | 설명 |
| --- | --- |
| `/pet` | 보관함 열기 |
| `/pet list` | 채팅으로 목록 — **이름을 클릭하면 바로 소환** |
| `/pet summon <펫id>` | 소환 |
| `/pet dismiss [펫id]` | 돌려보내기. id 를 주면 한 마리만, 없으면 전부 |
| `/pet rename [펫id] <이름>` | 이름 바꾸기 |
| `/pet dismount` | 내리기 |
| `/pet help` | 명령어 안내 |

**관리자**

| 명령어 | 설명 |
| --- | --- |
| `/petadmin give <플레이어> <펫종류>` | 펫 바로 지급 |
| `/petadmin egg <플레이어> <알id> [개수]` | 알 아이템 지급 |
| `/petadmin feed <플레이어> <먹이id> [개수]` | 먹이 아이템 지급 |
| `/petadmin growth <플레이어> <펫id> <양>` | 성장도 지급 |
| `/petadmin reload` | 설정 다시 읽기 |
| `/petadmin debug` | 누수 진단 · 현재 한도 · 연동 상태 |

> 펫 id 는 **앞 8자리만** 쳐도 되고, 탭 완성도 됩니다.

---

## 🔐 권한

| 권한 | 기본값 | 범위 |
| --- | --- | --- |
| `betterpets.use` | 모두 | 보관함, 소환 / 해제, 이름 변경, 탑승 |
| `betterpets.admin` | OP | 지급, 리로드, 진단 |

---

## ⚙️ 설정

```
plugins/BetterPets/
├─ config.yml       한도 · 알림 · 연동 · 성장 · 기믹 · 비행
├─ rarity.yml       등급별 수치 (속도 · 비행 확률 · 색)
├─ items.yml        알 · 먹이 아이템
├─ lang/*.yml       화면에 보이는 모든 문구 (ko_kr · en_us)
├─ pets/*.yml       펫 종류 정의
└─ playerdata/      플레이어별 펫 데이터 (건드리지 마세요)
```

### 🐺 펫 만들기

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

설정에 오타가 있으면 **기동할 때 전부 모아서 한 번에** 알려줍니다. 고치고 재시작하기를
반복할 일이 없어요.

### 🔢 보유 · 소환 마릿수

```yaml
# config.yml
pets:
  max-owned: 20    # 한 명이 가질 수 있는 마릿수. 0 = 무제한
  max-active: 1    # 동시에 꺼내둘 수 있는 마릿수. 0 = 무제한
```

- 보유 한도가 차면 알을 우클릭해도 **아이템이 사라지지 않아요.** 안내만 나갑니다
- 동시 소환 한도가 찬 상태에서 새로 부르면 **가장 먼저 부른 펫이 돌아갑니다.**
  그래서 기본값 1은 "부르면 교체"로 동작해요
- `max-active` 를 올리면 소환한 마릿수만큼 능력 보너스가 **겹쳐서** 붙습니다

### ⭐ 등급 수치

```yaml
# rarity.yml — 안 적은 등급 · 항목은 기본값을 씁니다
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

### ✨ 능력

**능력은 펫이 아니라 주인에게 붙습니다.** 펫이 싸우는 게 아니라, 주인이 강해져요.

| id | 종류 | 하는 일 | 수치 키 |
| --- | --- | --- | --- |
| `attribute_speed` | 패시브 | 이동속도 증가 | `base` · `per-growth` |
| `attribute_health` | 패시브 | 최대 체력 증가 | `base` · `per-growth` |
| `attribute_damage` | 패시브 | 공격력 증가 | `base` · `per-growth` |
| `on_kill_extra_drop` | 트리거 | 몹 처치 시 확률로 드롭 한 벌 더 | `chance-base` · `chance-per-growth` |

패시브 수치는 `(base + per-growth × 성장도) × 등급 이동속도 배율` 입니다.

**성체부터 발현돼요.** 아기와 돼지는 능력이 없고, 보관함 상세 화면이 그 사실과 현재
수치를 같이 보여줍니다. 꺼내둔 채로 성체가 되면 다시 부르지 않아도 바로 붙어요.

### 📢 알림

```yaml
broadcast:
  enabled: true
  min-rarity: A          # 이 등급 이상만. D 로 두면 채팅이 밀려요
  min-growth-stage: 1
  on-obtain: true        # 알에서 나왔을 때
  on-grown: true         # 다 자랐을 때
  on-stage-up: false     # 성장 단계가 올랐을 때
  sound: true
```

### 🌐 언어

```yaml
language: ko_kr          # lang/ko_kr.yml 을 읽습니다
```

`lang/ko_kr.yml` 을 복사해 `lang/<코드>.yml` 로 저장하면 새 언어가 됩니다.
**안 적은 키는 한국어로 채워지니 바꾸고 싶은 줄만 남겨도 돼요.** 플러그인이 업데이트돼서
문구가 늘어도 번역 파일이 깨지지 않습니다.

---

## 🧩 선택 연동

없어도 전부 정상 동작해요. 연동 상태는 `/petadmin debug` 로 확인할 수 있습니다.

| 플러그인 | 하는 일 |
| --- | --- |
| **DiscordSRV** | 서버 전체 알림을 디스코드 채널로도 보냅니다 |
| **PlaceholderAPI** | `%betterpets_pet_name%` · `%betterpets_owned%` · `%betterpets_active%` 등 |
| **Floodgate / Geyser** | Bedrock 플레이어를 알아보고 비행 이륙 확인을 건너뜁니다 (터치로는 맞히기 어려워서요) |

```yaml
integrations:
  discord:
    enabled: true
    channel: global      # DiscordSRV 의 게임 채널 이름
  bedrock:
    skip-mount-confirm: true
```

### 🪄 Bedrock 플레이어를 받는다면

Bedrock 클라이언트는 BetterModel 의 커스텀 모델을 그냥은 못 봅니다. 아래가 더 필요해요.

| 넣는 곳 | 플러그인 |
| --- | --- |
| `plugins/` | [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine), geyserutils-spigot, packetevents |
| `plugins/[Geyser]/extensions/` | GeyserModelEngineExtension, geyserutils-geyser |

모델도 Bedrock 용으로 따로 내보내야 합니다 → [MODELING.md](docs/MODELING.md#bedrockgeyser-대응)

---

## 🎨 모델 만들기

펫 모델은 직접 만드셔야 해요. 플러그인이 기대하는 규격이 있습니다:

- **애니메이션 이름** — `idle` · `walk` · `run` 은 필수. 비행 펫은 `fly`, 탑승 펫은 `ride` 추가
- **본 태그** — `h_head`(머리 회전), `b_body`(히트박스), `p_seat`(탑승 좌석) 처럼 접두사로 기능을 붙입니다

전체 규격과 예시는 **[docs/MODELING.md](docs/MODELING.md)** 에 있어요.

---

## ❓ 자주 묻는 것

<details>
<summary><b>펫이 안 보여요</b></summary>

`.bbmodel` 파일이 `plugins/BetterModel/models/` 에 있는지, `pets/*.yml` 의 `model:` 값이
파일 이름과 같은지 확인해 주세요. `/petadmin debug` 로 트래커가 잡혔는지도 볼 수 있어요.
Bedrock 플레이어에게만 안 보인다면 GeyserModelEngine 이 필요합니다.
</details>

<details>
<summary><b>펫이 벽에 끼거나 뒤처져요</b></summary>

3초 동안 가까워지지 못하면 자동으로 순간이동합니다. 너무 자주 그러면 `pets/*.yml` 의
`teleport-distance` 를 줄여 보세요.
</details>

<details>
<summary><b>설정을 고쳤는데 그대로예요</b></summary>

`/petadmin reload` 를 쳐 주세요. 문제가 있으면 뭐가 잘못됐는지 하나씩 알려줍니다.
</details>

<details>
<summary><b>펫을 여러 마리 꺼내고 싶어요</b></summary>

`config.yml` 의 `pets.max-active` 를 올리세요. 소환한 마릿수만큼 능력이 겹치니
밸런스도 같이 봐 주세요.
</details>

<details>
<summary><b>디스코드로 알림이 안 가요</b></summary>

`/petadmin debug` 의 연동 줄에서 DiscordSRV 가 `(O)` 인지 보세요. `(X)` 면 플러그인이
없는 거고, `(O)` 인데도 안 가면 `integrations.discord.channel` 이름이 DiscordSRV 쪽
채널 이름과 같은지 확인해 주세요.
</details>

---

## 🛠️ 빌드

```bash
export JAVA_HOME=/path/to/jdk-25     # 25 필수
mvn clean package                    # target/BetterPets-*.jar
mvn test                             # 테스트만
```

---

## 📚 문서

| 문서 | 내용 |
| --- | --- |
| **[MODELING.md](docs/MODELING.md)** | 모델 제작 규격 — 애니메이션 이름, 본 태그, 권장 구성 |
| **[DESIGN.md](docs/DESIGN.md)** | 설계와 그 근거 — 아키텍처, BetterModel API, 원작 분석, 리스크 |
| **[ROADMAP.md](docs/ROADMAP.md)** | 개발 진행 상황과 남은 검증 |
| **[CLAUDE.md](CLAUDE.md)** | 코드에 손댈 때 지키는 규칙 |

---

## 📜 참고

- [BetterModel](https://github.com/toxicity188/BetterModel) — 렌더링 엔진 (MIT)
- [betterpets-paper](https://github.com/yourShika/betterpets-paper) — 탑승 · 비행 구현을 참고했습니다 (MIT)
- [악어의 놀이터 2](https://namu.wiki/w/%EC%95%85%EC%96%B4(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EB%B0%A9%EC%86%A1%EC%9D%B8)/%EB%8C%80%EA%B7%9C%EB%AA%A8%20%EC%BD%98%ED%85%90%EC%B8%A0/%EC%95%85%EC%96%B4%EC%9D%98%20%EB%86%80%EC%9D%B4%ED%84%B0%202) — 원작 시스템

---

## ⚠️ 주의

**악어의 놀이터에서 쓰는 실제 모델 · 텍스처를 추출해 쓰는 건 저작권 침해입니다.**
시스템 구조를 참고하는 것과 에셋을 복제하는 건 다른 얘기예요. 모든 `.bbmodel` 은
직접 만들거나 사용 허가가 명확한 것만 쓰세요.

---

## 📄 라이선스

**아직 정하지 않았습니다.** 라이선스 파일이 없으면 기본적으로 "모든 권리 보유"라
다른 사람이 쓰거나 고칠 수 없어요. 공개할 생각이라면 `LICENSE` 파일을 추가해 주세요 —
참고한 BetterModel 과 betterpets-paper 는 둘 다 MIT 입니다.
