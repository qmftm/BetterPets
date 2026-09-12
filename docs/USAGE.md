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

보관함에서 펫을 누르면 상세 화면이 열려요. 소환·이름 변경·놓아주기가 거기 있고,
**놓아주기는 쉬프트 + 클릭**이어야 합니다 — 되돌릴 수 없는 조작이라 한 번 더 확인해요.

아래 줄에 **정렬**(기본 · 최근 얻은 순 · 많이 자란 순)과 **보기**(전체 · 소환 중 · 아기 ·
성체) 버튼이 있어요. 누를 때마다 다음 값으로 넘어가고, 버튼에 다음에 뭐가 나올지 적혀
있습니다. 상세 화면에서 "돌아가기"를 누르면 **보던 쪽·정렬·보기 그대로** 돌아와요.

이름에 쓴 색 태그(`<red>` 같은 것)는 저장할 때 걷어냅니다. 32자 제한도 태그를 뺀
**실제로 보이는 글자 수**로 셉니다.

`/pet rename` 의 펫 id 는 생략할 수 있어요 — 한 마리만 소환 중이면 그 펫을 바꿉니다.
여러 마리를 데리고 다니는 중이면 id 를 앞에 적어 주세요. **네 자리 이상 16진수**만
id 로 읽으니, `/pet rename 왕 드래곤` 처럼 이름으로 시작해도 이름으로 처리됩니다.

**관리자**

| 명령어 | 설명 |
| --- | --- |
| `/petadmin give <플레이어> <펫종류>` | 펫 바로 지급 |
| `/petadmin egg <플레이어> <알id> [개수]` | 알 아이템 지급 |
| `/petadmin feed <플레이어> <먹이id> [개수]` | 먹이 아이템 지급 |
| `/petadmin eggmaterial <알id>` | 손에 든 아이템으로 그 알의 재질을 바꿉니다 |
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
  idle: idle               # 키는 정해져 있어요. 오타를 내면 기동할 때 알려드립니다
  walk: walk
  run: run
  fly: fly
  ride: ride

movement:                  # 안 적으면 전부 기본값입니다
  follow-distance: 3.0     # 주인 뒤 이만큼 떨어져 따라와요
  walk-speed: 0.28
  run-speed: 0.5           # 8블록 이상 뒤처지면 이 속도로 뜁니다
  teleport-distance: 28.0  # 이보다 멀어지면 순간이동으로 따라잡아요

abilities:
  - id: attribute_speed
    base: 0.05
    per-growth: 0.0005

acquire:
  gacha-weight: 1          # 뽑기 알에서 뽑힐 가중치. 0 이면 뽑기 알에서 안 나와요
```

설정에 오타가 있으면 **기동할 때 전부 모아서 한 번에** 알려줍니다. 고치고 재시작하기를
반복할 일이 없어요.

`movement:` 값은 쓸 수 있는 범위로 접습니다. 예를 들어 `teleport-distance: 0` 은 펫이
매 틱 순간이동하게 만들어서 서버가 느려지는데, 그런 값은 조용히 최소값으로 올려요 —
설정 한 줄 때문에 서버가 멈추는 편보다 낫습니다.

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

**성체부터 발현돼요.** 아기와 돼지는 능력이 없습니다. 꺼내둔 채로 성체가 되면 다시
부르지 않아도 바로 붙어요. (보관함 화면에는 능력 수치를 따로 보여주지 않습니다 —
효과는 그대로 적용되지만 표시는 생략했어요.)

### 🥚 알과 먹이

`items.yml` 에서 알과 먹이를 정합니다.

```yaml
eggs:
  wolf_egg:
    display-name: "<white>늑대 알"
    material: SHULKER_SPAWN_EGG
    gives: wolf              # 고정 알 — 이 펫이 확정으로 나옵니다

  random_egg:
    display-name: "<white>수상한 알"
    material: EGG
    weights:                 # 랜덤 알 — 가중치 추첨
      wolf: 50
      dragon: 1

  gacha_egg:
    display-name: "<light_purple>뽑기 알"
    material: EGG            # 둘 다 안 적으면 펫들의 gacha-weight 를 그대로 씁니다
```

`gives` 도 `weights` 도 없는 알은 각 펫이 `pets/*.yml` 에 적어둔
`acquire.gacha-weight` 를 표로 씁니다. **새 펫을 만들 때 그 한 줄만 적어두면 뽑기 알에
자동으로 들어가요** — `items.yml` 을 다시 열 필요가 없습니다. `0` 으로 두면 안 나오고요
(기본 설정의 돼지와 새끼가 그렇습니다).

알은 **허공에 대고 우클릭**하면 까집니다. 블록을 바라보고 클릭하면 블록이 먼저예요 —
알을 든 채로 상자를 열려다 알이 까지면 되돌릴 수가 없으니까요. 블록 쪽을 보면서 까고
싶으시면 **스니크 + 우클릭**하시면 됩니다.

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

기본 알 재질은 전부 `SHULKER_SPAWN_EGG` 예요. 알마다 다른 재질을 쓰고 싶으면
`items.yml` 을 직접 열 필요 없이, 원하는 아이템을 손에 들고
`/petadmin eggmaterial <알id>` 를 치면 됩니다.

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

시간 경과는 **1분당 +1로 고정**이고 설정 대상이 아닙니다. 접속해 있지 않아도, 보관함에
넣어둔 채로도 자라요 — 마지막으로 본 시각을 기준으로 계산하기 때문입니다. 접속하거나
보관함을 열면 그동안 자란 만큼이 한 번에 반영되고, 그새 다 자랐으면 그때 알려드려요.

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
  min-growth-stage: 1    # 이 성장 단계 이상만
  on-obtain: true        # 알에서 나왔을 때
  on-grown: true         # 다 자랐을 때
  on-stage-up: false     # 성장 단계가 올랐을 때
  sound: true
```

등급과 성장 단계를 **둘 다** 넘겨야 알림이 나갑니다.

> `min-growth-stage` 를 `growth.max-stage` 보다 크게 두면 어떤 펫도 그 단계에
> 닿을 수 없어서 알림이 하나도 안 나가요. `enabled: false` 와 증상이 똑같아 원인을
> 찾기 어려운 조합이라, 기동할 때 경고로 알려드립니다.

`min-growth-stage` 를 2 이상으로 올리면 **획득 알림(`on-obtain`)은 나가지 않아요** —
알에서 갓 나온 펫은 언제나 1단계니까요. 그때는 `on-stage-up` 을 켜서 "일정 단계를
넘겼다"를 알리시는 게 맞습니다.

**주인에게 가는 알림은 이 문턱과 무관해요.** 자기 펫이 자랐다는 건 등급과 상관없이
본인이 알아야 하니까요. 문턱은 서버 전체 방송에만 걸립니다.

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
(설정 오류 문구는 파일 경로와 키를 그대로 담고 있어서 언어 설정과 무관하게
한국어로 나옵니다 — 번역해도 그 안의 경로는 그대로라 오히려 읽기 어려워져요.)
`config.yml` · `rarity.yml` · `items.yml` · `pets/*.yml` · `lang/*.yml` 이 전부 다시
읽히고, 성장·기믹·한도·알림·비행·Discord 채널까지 그 자리에서 바뀝니다.

> **이미 소환해 둔 펫은 예외예요.** 모델·속도·능력은 소환할 때 붙기 때문에, 나와 있는
> 펫은 예전 정의로 계속 돌아요. 다시 소환하면 새 설정이 붙습니다. 리로드할 때 몇 마리가
> 그런 상태인지 알려드려요 — 전부 자동으로 다시 소환하지 않는 건, 설정 오타 하나로
> 서버 전체의 펫이 사라질 수 있어서입니다.
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
