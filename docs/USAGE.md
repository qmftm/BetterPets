# 사용 안내

설치와 소개는 [README](../README.md)에 있어요. 이 문서는 **명령어 · 권한 · 설정 · 문제 해결**을
다룹니다.

설정을 고친 뒤에는 `/petadmin reload` 로 바로 적용할 수 있어요. 오타가 있으면 무엇이
잘못됐는지 하나씩 알려줍니다.

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

---

## 🔐 권한

| 권한 | 기본값 | 범위 |
| --- | --- | --- |
| `betterpets.use` | 모두 | 보관함, 소환 / 해제, 이름 변경, 탑승 |
| `betterpets.admin` | OP | 지급, 리로드, 진단 |

---

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
`on_kill_extra_drop` 의 확률에는 **등급 배율을 곱하지 않아요** — 확률에 배율을 곱하면
상한(100%)에 금방 붙어서 등급 차이가 오히려 뭉개지거든요. 등급 차이는 `chance-base`
로 내시면 됩니다.

능력 이름은 `lang/*.yml` 의 `ability:` 절에서 옵니다. 직접 만든 능력이면 거기에 한 줄
더하세요 — 없으면 보관함에 id 가 그대로 보입니다.

**성체부터 발현돼요.** 아기와 돼지는 능력이 없고, 보관함 상세 화면이 그 사실과 현재
수치를 같이 보여줍니다. 꺼내둔 채로 성체가 되면 다시 부르지 않아도 바로 붙어요.

### 🥚 알과 먹이

`items.yml` 에서 알과 먹이를 정합니다.

```yaml
eggs:
  wolf_egg:
    display-name: "<white>늑대 알"
    material: TURTLE_EGG
    gives: wolf              # 고정 알 — 이 펫이 확정으로 나옵니다

  random_egg:
    display-name: "<white>수상한 알"
    material: EGG
    weights:                 # 랜덤 알 — 가중치 추첨
      wolf: 50
      dragon: 1
```

**알 아이템에 무엇이 들었는지 자동으로 적힙니다.** 고정 알은 나올 펫 이름을, 랜덤 알은
가능한 펫과 확률을 흔한 순으로 보여줘요. 확률을 모르면 뽑을 이유가 없으니까요.
종류가 많으면 여섯 줄까지만 적고 나머지는 "그 외 N종" 으로 접습니다.

```yaml
feeds:
  milk:
    display-name: "<white>우유"
    material: MILK_BUCKET
    growth: 10               # 안 적으면 config 의 feed-amount 를 씁니다
```

`item-model` 로 리소스팩 모델을 지정할 수 있어요 (`betterpets:egg_wolf` 형식).
**소문자만 됩니다** — 대문자를 쓰면 기동할 때 알려줍니다.

지급은 `/petadmin egg <플레이어> <알id> [개수]` 와 `/petadmin feed …` 로 합니다.

### 🌱 성장 속도

```yaml
growth:
  feed-amount: 10      # 먹이 한 번에 오르는 성장도. 원작(우유)은 10이에요
  max-stage: 1         # 성장 단계 개수
```

`max-stage` 가 **1이면** 성장도가 다 차는 순간 성체가 됩니다. **2 이상이면** 성장도가
찰 때마다 `pets/*.yml` 의 `next-stage` 가중치로 다음 형태를 뽑고 성장도를 0부터 다시
채워요. 이걸 `max-stage - 1` 번 반복한 뒤에야 성체가 됩니다.

```yaml
# pets/hatchling.yml — 같이 들어 있는 예시입니다
next-stage:
  wolf: 70       # 70% 확률로 늑대
  hatchling: 25  # 25% 확률로 그대로 — "한 단계 더 기다린다"가 됩니다
  dragon: 5      # 5% 확률로 드래곤
```

가중치 비율이 그대로 확률이 돼요. **자기 자신을 넣어도 됩니다** — 단계를 더 기다리는
재미가 생깁니다. `next-stage` 를 안 적으면 종류는 그대로 두고 단계만 올라가요.

> `max-stage` 를 올리면 **모든 펫이 성체가 되기까지 더 오래 걸려요.** 이미 균형을
> 잡아둔 서버라면 신중하게 바꾸세요.

시간 경과는 **1분당 +1로 고정**이고 설정 대상이 아닙니다. 접속해 있지 않아도 자라요 —
마지막으로 본 시각을 기준으로 계산하기 때문입니다.

### 🐷 과급식 기믹

짧은 시간에 먹이를 몰아주면 펫이 돼지가 됩니다.

```yaml
gimmick:
  overfeed:
    enabled: true
    count: 10            # 이 횟수 이상
    window-seconds: 60   # 이 시간 안에 주면
    becomes: pig         # 이 종류로 바뀝니다 (pets/ 에 있는 id)
```

- **아기 펫에게만** 일어납니다. 성체는 아무리 먹여도 안 변해요
- 상태만 바뀌는 게 아니라 **종류까지 바뀝니다.** 안 그러면 "돼지가 됐다"는 메시지와
  화면이 어긋나요. `becomes` 가 가리키는 펫이 없으면 기동할 때 경고합니다
- `count` 를 **2 미만으로 두면 2로 올려 씁니다.** 먹이 한두 번에 바로 돼지가 되는 건
  의도가 아닐 테니까요. 끄고 싶으면 `enabled: false` 를 쓰세요

### 🕊️ 비행 조작

```yaml
ride:
  flight-lift: 0.5          # 점프 키를 눌렀을 때 올라가는 정도
  flight-max-height: 1024.0 # 비행 고도 상한
```

고도 상한을 빌드 높이와 따로 두는 이유는, 원작처럼 **빌드 높이 위 빈 하늘로도**
올라갈 수 있어야 해서예요. 터무니없는 y 값만 막는 안전장치입니다.

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

모델도 Bedrock 용으로 따로 내보내야 합니다 → [MODELING.md](MODELING.md#bedrockgeyser-대응)

---

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
