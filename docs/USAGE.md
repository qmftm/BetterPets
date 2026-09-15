# 사용 안내

설치와 소개는 [README](../README.md)에 있어요. 이 문서는 **명령어 · 권한 · 설정 · 문제 해결**을
다룹니다.

설정을 고친 뒤에는 `/betterpets reload` 로 바로 적용할 수 있어요. 오타가 있으면 무엇이
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
| `/pet release <펫id>` | **놓아주기 — 되돌릴 수 없습니다** |
| `/pet dismount` | 내리기 |
| `/pet help` | 명령어 안내 |

보관함에서 펫을 누르면 상세 화면이 열려요. 소환·이름 변경 버튼은 그 자리에서 끝나지만,
**놓아주기 버튼은 명령어를 안내만 하고 화면에서 끝내지 않습니다** — id 가 박힌
`/pet release <id>` 를 직접 쳐야 실제로 놓아줍니다. 되돌릴 수 없는 조작이라, 클릭
한 번으로 끝나지 않게 하려고 명령어로 옮겼어요.

아래 줄에 **정렬**(기본 · 최근 얻은 순 · 많이 자란 순)과 **보기**(전체 · 소환 중) 버튼이
있어요. 누를 때마다 다음 값으로 넘어가고, 버튼에 다음에 뭐가 나올지 적혀 있습니다.
상세 화면에서 "돌아가기"를 누르면 **보던 쪽·정렬·보기 그대로** 돌아와요.

이름에 쓴 색 태그(`<red>` 같은 것)는 저장할 때 걷어냅니다. 32자 제한도 태그를 뺀
**실제로 보이는 글자 수**로 셉니다.

`/pet rename` 의 펫 id 는 생략할 수 있어요 — 한 마리만 소환 중이면 그 펫을 바꿉니다.
여러 마리를 데리고 다니는 중이면 id 를 앞에 적어 주세요. **네 자리 이상 16진수**만
id 로 읽으니, `/pet rename 왕 드래곤` 처럼 이름으로 시작해도 이름으로 처리됩니다.

**관리자** — `/betterpets` 는 `/bp` 로 줄여 써도 됩니다.

| 명령어 | 설명 |
| --- | --- |
| `/betterpets give <플레이어> <펫종류>` | 펫 바로 지급 |
| `/betterpets egg <플레이어> <알id> [개수]` | 알 아이템 지급 |
| `/betterpets feed <플레이어> <먹이id> [개수]` | 먹이 아이템 지급 |
| `/betterpets eggmaterial <알id>` | 손에 든 아이템으로 그 알의 재질을 바꿉니다 |
| `/betterpets growth <플레이어> <펫id> <양>` | 성장도 지급 |
| `/betterpets reload` | 설정 다시 읽기 |
| `/betterpets debug` | 누수 진단 · 현재 한도 · 연동 상태 |

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
# pets/phantom_normal.yml
id: phantom_normal
display-name: "<gray>팬텀"
model: phantom_normal      # plugins/BetterModel/models/phantom_normal.bbmodel
rarity: A
growth-max: -1                # 음수 = 성장도 없음. next-stage 가 없는 종류는 이렇게 둡니다
# icon: NETHER_STAR          # 보관함 아이콘 재질. 안 적으면 LEAD
size: 2                      # 모델 크기 배율. 1.0 이 원래 크기
# hitbox-scale: 1.0          # 판정(클릭·충돌) 크기 배율. 안 적으면 size 를 그대로 씁니다

ride: true                  # 탈 수 있는지
flying: true                 # 탈 수 있으면, 날 수도 있는지
# ride-speed: 0.5            # 이 펫만 쓸 탑승 속도. 안 적으면 고정 기본값(0.28)을 씁니다
# flight-speed: 0.6          # 비행 중 수평 속도. 안 적으면 ride-speed 를 그대로 씀
# flight-lift: 0.6           # 이 펫만 쓸 비행 상승력. 안 적으면 config.yml 의 전역값을 따름

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

# next-stage 를 적지 않았으므로 이 펫은 더 진화하지 않는다 — growth-max 도 -1 이라
# 성장도가 아예 오르지 않는다. 아래 "성장 속도" 절 참고.

acquire:
  gacha-weight: 0          # 뽑기 알에서 뽑힐 가중치. 0 이면 뽑기 알에서 안 나와요
```

`hitbox-scale` 은 **모델이 보이는 크기와 별개로 클릭·충돌 판정 크기**를 정합니다.
안 적으면 `size` 를 그대로 씁니다 — 지금까지 하던 대로예요. 모델은 크게 그리되
클릭 자리는 좁히고 싶을 때(또는 반대로)만 따로 적으면 됩니다.

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

### ⭐ 등급

```yaml
# rarity.yml — 안 적은 등급은 기본값을 씁니다
rarities:
  S:
    display-name: "전설"
```

> **등급은 표기 전용이에요.** 보관함·알림에 뜨는 이름표일 뿐, 이동속도나 탑승속도에는
> 영향을 주지 않습니다 — 등급이 실력 차이가 아니라 이름표 하나로만 쓰이길 원하시면
> 이대로 두시면 됩니다. 펫마다 속도를 다르게 하고 싶으시면 `pets/*.yml`의
> `ride-speed`를 펫별로 적으세요 (아래 "🕊️ 비행 조작" 절 참고).
> 비행 여부는 등급이 아니라 각 펫의 `flying` 설정으로만 정해집니다 — 개체별 추첨은
> 없어요. `flying: true` 인 종류는 전부 납니다.

### 🥚 알과 먹이

`items.yml` 에서 알과 먹이를 정합니다.

```yaml
eggs:
  chaos_egg:
    display-name: "<dark_purple>혼돈의 알"
    material: SHULKER_SPAWN_EGG
    gives: egg               # 고정 알 — 이 펫이 확정으로 나옵니다

  random_egg:
    display-name: "<white>수상한 알"
    material: EGG
    weights:                 # 랜덤 알 — 가중치 추첨
      egg: 50
      some_other_pet: 1

  gacha_egg:
    display-name: "<light_purple>뽑기 알"
    material: EGG            # 둘 다 안 적으면 펫들의 gacha-weight 를 그대로 씁니다
```

`gives` 도 `weights` 도 없는 알은 각 펫이 `pets/*.yml` 에 적어둔
`acquire.gacha-weight` 를 표로 씁니다. **새 펫을 만들 때 그 한 줄만 적어두면 뽑기 알에
자동으로 들어가요** — `items.yml` 을 다시 열 필요가 없습니다. `0` 으로 두면 안 나오고요
(기본 설정의 모든 펫이 그렇습니다 — 뽑기가 아니라 알을 키워서 얻는 구성이에요).

알은 **우클릭하면 까집니다.** 블록을 바라보고 있어도 마찬가지예요 — 상자나 문 같은
블록과 상호작용하고 싶으시면 알을 잠깐 다른 슬롯으로 옮기시면 됩니다.

**알 아이템에 무엇이 들었는지 자동으로 적힙니다.** 고정 알은 나올 펫 이름을, 랜덤 알은
가능한 펫과 확률을 흔한 순으로 보여줘요. 확률을 모르면 뽑을 이유가 없으니까요.
종류가 많으면 여섯 줄까지만 적고 나머지는 "그 외 N종" 으로 접습니다.

```yaml
feeds:
  feed:
    display-name: "<gold>성장 사료"
    material: COOKIE
    growth: 10               # 안 적으면 config 의 feed-amount 를 씁니다
```

`item-model` 로 리소스팩 모델을 지정할 수 있어요 (`betterpets:egg_chaos` 형식).
**소문자만 됩니다** — 대문자를 쓰면 기동할 때 알려줍니다.

기본 알 재질은 전부 `SHULKER_SPAWN_EGG` 예요. 알마다 다른 재질을 쓰고 싶으면
`items.yml` 을 직접 열 필요 없이, 원하는 아이템을 손에 들고
`/betterpets eggmaterial <알id>` 를 치면 됩니다.

지급은 `/betterpets egg <플레이어> <알id> [개수]` 와 `/betterpets feed …` 로 합니다.

### 🎁 놓아주기 보상

`/pet release` 로 펫을 놓아주면 위로가 되는 아이템을 대신 줄 수 있어요.

```yaml
# items.yml
release-reward:
  material: PAPER
  display-name: "<yellow>펫 놓아주기 증표"
  lore:
    - "<gray>펫을 놓아줘서 받았습니다."
  min-amount: 1
  max-amount: 3
  glow: true
```

`min-amount`~`max-amount` 사이에서 무작위로 골라 줍니다(둘이 같으면 항상 그 개수).
이 섹션 자체를 지우면 놓아줘도 아무것도 주지 않아요 — 선택 기능입니다. 인벤토리가
꽉 찼으면 발밑에 떨어뜨려서라도 반드시 드립니다.

### ✨ 반짝임

알·먹이·놓아주기 보상 전부에 `glow: true` 를 적으면, 실제로 인챈트하지 않아도
인챈트 반짝임이 보입니다. 안 적으면 꺼져 있어요. 펫 보관함 아이콘도 `pets/*.yml`
의 `icon-glow: true` 로 항상 반짝이게 할 수 있어요 — 소환 중인 펫은 이 설정과
무관하게 항상 반짝입니다.

### 🌱 성장 속도

**성장도가 하는 일은 하나뿐입니다 — 다음 종류로 진화하기까지 걸리는 시간.** 아기·성체
같은 생애주기 구분은 없어요. 탑승도 소환하는 순간부터 전부 붙습니다.

```yaml
growth:
  feed-amount: 10      # 먹이 한 번에 오르는 성장도. 원작(우유)은 10이에요
```

```yaml
# pets/baby_phantom.yml — 같이 들어 있는 예시입니다
next-stage:
  phantom_normal: 70  # 70% 확률로 평범한 팬텀
  phantom_ender: 30   # 30% 확률로 엔더 팬텀
```

`next-stage` 가 있는 종류만 먹이·시간 경과로 성장도가 오릅니다. 다 차면 이 가중치로
다음 형태를 뽑고 성장도를 0부터 다시 채워요. 가중치 비율이 그대로 확률이 됩니다.
**자기 자신을 넣어도 됩니다** — 다음에 더 자랄 기회를 한 번 더 미루는 셈입니다.

**`next-stage` 를 안 적은 종류는 `growth-max` 를 음수(권장값 `-1`)로 두세요.**
더 진화할 곳이 없으니 잴 이유가 없거든요 — 먹여도 포만도만 오르고 성장도는 그대로,
보관함·GUI 에도 성장도 자체가 표시되지 않습니다. 기본 제공되는
`phantom_normal`/`phantom_ender`/`pig` 가 그렇습니다.

시간 경과는 **꺼내둔 펫에 한해, 1분당 +1로 고정**이고 설정 대상이 아닙니다. 보관함에
넣어둔 동안은 시간이 아무리 지나도 자라지 않아요 — 접속하거나 보관함을 열면 그동안
꺼내둔 시간만큼 자란 만큼이 반영되고, 그새 진화했으면 그때 알려드려요.

### 🍖 포만도

```yaml
growth:
  fullness:
    min-gain: 5        # 먹일 때마다 이 값 ~
    max-gain: 15       #                   이 값 사이에서 무작위로 오릅니다
    max: 100           # 이 이상이면 더 못 먹여요
    decay-seconds: 30  # 이 시간마다 1씩 저절로 내려갑니다. 0 이하면 안 내려가요
```

한 번에 몰아 먹여서 순식간에 다 키우는 걸 막는 값이에요. 시간이 지나면 `decay-seconds`
마다 1씩 저절로 내려가니, 상한에 닿아 못 먹이던 펫도 기다리면 다시 먹일 수 있게 됩니다.
성장도는 이 값과 무관해요 — 급여로 얻는 성장은 포만도가 상한일 때만 막히고, 그 사이에도
시간 경과(1분당 +1)로는 여전히 자랍니다.

### 🐷 과급식 기믹

짧은 시간에 먹이를 몰아주면 펫이 돼지가 됩니다.

```yaml
gimmick:
  overfeed:
    enabled: true
    count: 10            # 이 횟수 이상
    window-seconds: 60   # 이 시간 안에 주면
    chance: 100          # 위 조건을 채워도 이 확률(%)로만 발동
    min-fullness: 0      # 발동 순간의 포만도가 이 값 이상이어야 함 (0 이면 조건 없음)
    becomes: pig         # 이 종류로 바뀝니다 (pets/ 에 있는 id)
    # model: pet_pig     # 종류는 그대로 두고 모습만 이걸로 바꿉니다 (becomes 와 별개)
```

- **아직 돼지가 안 된 펫에게만** 일어납니다. 이미 돼지가 된 펫은 다시 걸리지 않아요
- `becomes` 는 종류 자체를 통째로 갈아끼웁니다(능력치·성장 상한·라이드 설정까지).
  안 그러면 "돼지가 됐다"는 메시지와 화면이 어긋나요. 가리키는 펫이 없으면 기동할 때
  경고하고, 종류는 바뀌지 않습니다
- `model` 은 **종류는 그대로 두고 모습만** 바꿉니다 — 별도의 `pets/*.yml` 없이 겉모습만
  바꾸고 싶을 때 씁니다. `becomes` 와 동시에 적으면 종류는 `becomes` 를, 모습은
  `model` 을 따릅니다
- `count` 를 **2 미만으로 두면 2로 올려 씁니다.** 먹이 한두 번에 바로 돼지가 되는 건
  의도가 아닐 테니까요. 끄고 싶으면 `enabled: false` 를 쓰세요
- `chance`·`min-fullness` 조건을 못 채우면 **과급식 카운터는 그대로 남습니다** — 다음
  먹이를 줄 때 다시 확인합니다(창이 지나기 전까지는 처음부터 다시 몰아 먹일 필요가
  없어요)

### 🕊️ 비행 조작

```yaml
ride:
  flight-lift: 0.5          # 점프/스니크 키를 눌렀을 때 오르내리는 정도
  flight-max-height: 1024.0 # 비행 고도 상한
```

**점프로 오르고, 스니크로 내려갑니다.** 지상 탑승과 달리 비행 중에는 스니크가
하차가 아니에요 — 하차만 시키면 위로만 갈 수 있고 내려올 방법이 없거든요.
비행 중 내리려면 `/pet dismount` 를 쓰세요. **완강 낙하는 안 걸어드려요** —
공중에서 내리면 그대로 떨어집니다. 땅 가까이에서 내리세요. 탈 때 확인 절차는
없습니다 — 나는 종류를 우클릭하면 곧바로 이륙해요.

고도 상한을 빌드 높이와 따로 두는 이유는, 원작처럼 **빌드 높이 위 빈 하늘로도**
올라갈 수 있어야 해서예요. 터무니없는 y 값만 막는 안전장치입니다.

`flight-lift` 는 전역 기본값입니다. 특정 펫만 더 빠르거나 느리게 오르내리게
하고 싶으면 `pets/*.yml` 에 `flight-lift` 를 따로 적으세요 — `ride-speed` 와
같은 자리입니다.

비행 중 **수평** 속도는 `ride-speed` 를 그대로 씁니다. 걷는 속도와 나는 속도를
다르게 두고 싶으면 `pets/*.yml` 에 `flight-speed` 를 따로 적으세요 — 안 적으면
`ride-speed` 를 그대로 따릅니다.

### 🪑 좌석 높이

```yaml
# config.yml
ride:
  seat-offset: 0.0   # 탑승 시 앉는 높이. 0이 기본 자리, 양수면 위로, 음수면 아래로
```

몸집이 큰 펫(예: `size: 2`)은 플레이어가 몸통에 파묻혀 보일 수 있어요. 이럴 때
`pets/*.yml` 에 `seat-offset` 을 펫별로 적으면 그 펫만 다른 높이에 앉힐 수 있습니다 —
안 적으면 `config.yml` 의 전역값을 그대로 씁니다.

```yaml
# pets/phantom_normal.yml
seat-offset: 0.3   # 기본 자리보다 0.3블록 위에 앉힙니다
```

### 🕊️ 추종 호버 높이

```yaml
# config.yml
ride:
  hover-height: 0.0   # 나는 펫이 추종할 때 주인 위로 띄워 둘 높이. 0이 발높이(기본)
```

**탑승 중이 아니라, 따라올 때 얘기입니다.** 나는 펫(`flying: true`)은 기본적으로
주인 발높이를 목표로 추종해요 — 머리 위로 살짝 띄우고 싶으면 이 값을 올리세요.
플레이어 키가 1.8블록이니 `1.2`~`1.5` 정도면 머리 위쯤입니다. 너무 크게 잡으면
목표 거리가 순전히 수직 오프셋만으로 채워져서, 펫이 옆이 아니라 **정수리 위**에
자리잡는 것처럼 보일 수 있어요 — `follow-distance` 와 비교해 가며 조정하세요.

`pets/*.yml` 에 `hover-height` 를 펫별로 적으면 그 펫만 다른 높이에 띄울 수
있습니다 — 안 적으면 `config.yml` 의 전역값을 그대로 씁니다. 걷는 펫에는
의미가 없어요(추종은 항상 지면을 따라갑니다).

```yaml
# pets/phantom_normal.yml
hover-height: 1.3   # 주인 머리 위쯤에 떠서 따라옵니다
```

### 📢 알림

```yaml
broadcast:
  enabled: true
  min-rarity: A          # 이 등급 이상만. D 로 두면 채팅이 밀려요
  min-growth-stage: 1    # 이 성장 단계 이상만
  on-obtain: true        # 알에서 나왔을 때
  on-stage-up: false     # next-stage 로 진화했을 때
  sound: true
```

등급과 성장 단계를 **둘 다** 넘겨야 알림이 나갑니다.

`min-growth-stage` 를 2 이상으로 올리면 **획득 알림(`on-obtain`)은 나가지 않아요** —
알에서 갓 나온 펫은 언제나 1단계니까요. 그때는 `on-stage-up` 을 켜서 "일정 단계를
넘겼다"를 알리시는 게 맞습니다. next-stage 로 여러 번 진화하는 펫이 하나도 없다면
이 단계는 항상 1이라 의미가 없습니다.

**주인에게 가는 알림은 이 문턱과 무관해요.** 자기 펫이 진화했다는 건 등급과 상관없이
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

없어도 전부 정상 동작해요. 연동 상태는 `/betterpets debug` 로 확인할 수 있습니다.

| 플러그인 | 하는 일 |
| --- | --- |
| **DiscordSRV** | 서버 전체 알림을 디스코드 채널로도 보냅니다 |
| **PlaceholderAPI** | `%betterpets_pet_name%` · `%betterpets_owned%` · `%betterpets_active%` 등 |
| **Floodgate / Geyser** | GeyserModelEngine 이 있는지 기동 로그로 알려줍니다 |
| **Skript** | 획득·소환·해제·급여 감지, 펫 목록·추가·제거·보유 여부를 스크립트에서 씁니다 |

```yaml
integrations:
  discord:
    enabled: true
    channel: global      # DiscordSRV 의 게임 채널 이름
```

### 🪄 Bedrock 플레이어를 받는다면

Bedrock 클라이언트는 BetterModel 의 커스텀 모델을 그냥은 못 봅니다. 아래가 더 필요해요.

| 넣는 곳 | 플러그인 |
| --- | --- |
| `plugins/` | [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine), geyserutils-spigot, packetevents |
| `plugins/[Geyser]/extensions/` | GeyserModelEngineExtension, geyserutils-geyser |

모델도 Bedrock 용으로 따로 내보내야 합니다 → [MODELING.md](MODELING.md#bedrockgeyser-대응)

### 📜 Skript 연동

펫은 전부 **id(문자열, 예: `a1b2c3d4-...`)** 로 다룹니다 — 이름은 바꿀 수 있어서
식별자로 못 씁니다. `/pet list` 로 앞 8자리를 볼 수 있지만, Skript 쪽은 항상
전체 id 를 돌려줍니다.

```
on pet obtain:
    send "%player% 가 %event-pet% 를 얻었습니다!" to console

on pet summon:
    send "%player% 가 %event-pet% 를 소환했습니다." to console

on pet dismiss:
    send "%player% 가 펫을 돌려보냈습니다." to console

on pet release:
    send "%player% 가 펫과 작별했습니다." to console

on pet feed:
    send "%player% 가 %event-pet% 에게 먹이를 줬습니다." to console

command /내펫:
    trigger:
        send "소환 중: %summoned pets of player%"
        send "보유: %owned pets of player%"
        if player has pet "phantom_normal":
            send "팬텀을 가지고 있네요."
        add pet "phantom_normal" to player
        remove pet "{_그펫의-id}" from player   # id 는 event-pet 등으로 미리 얻어둡니다
```

**감지(이벤트)** — 전부 `player` 로 누구인지, `event-pet` 으로 그 펫의 id 를 줍니다.

| 구문 | 뜻 |
| --- | --- |
| `on pet obtain` | 알을 까거나 관리자가 지급해 **새 펫을 얻은 순간** |
| `on pet summon` | 펫을 **소환한** 순간 |
| `on pet dismiss` | 펫을 **소환 해제한** 순간 (퇴장·놓아주기 전 자동 해제도 포함) |
| `on pet release` | 펫과 **작별한**(`/pet release`, 영구 삭제) 순간. 소환 중이었다면 `on pet dismiss` 가 먼저 나갑니다 |
| `on pet feed` | 펫에게 **먹이가 실제로 반영된** 순간 (포만도가 꽉 차 거절된 경우는 제외) |

**목록**

| 구문 | 뜻 |
| --- | --- |
| `summoned pets of %player%` | 지금 **소환 중인** 펫 id 목록 (`%player%'s summoned pets` 도 됩니다) |
| `owned pets of %player%` | **보관함 전체**(소환 여부 무관) 펫 id 목록 (`%player%'s pets` 도 됩니다) |

**추가·제거·보유 여부**

| 구문 | 뜻 |
| --- | --- |
| `add pet %string% to %player%` | 그 **종류**(예: `phantom_normal`)의 펫 한 마리를 지급 — `/betterpets give` 와 같은 경로입니다 |
| `remove pet %string% from %player%` | 그 **id**(앞자리만 적어도 됩니다)를 가진 펫을 놓아줍니다 — `/pet release` 와 같은 경로라 **되돌릴 수 없습니다** |
| `%player% has pet %string%` | 그 **종류를 하나라도** 가졌거나, 그 **id** 를 가진 펫이 있으면 참 (`doesn't have pet` 도 됩니다) |

`on pet obtain` 은 알 우클릭·관리자 지급(`/betterpets give`·`egg`) 을 전부 잡습니다 —
"뽑기로 얻었을 때만" 가리고 싶으면 스크립트 안에서 따로 조건을 거세요. `add`/`remove`·
종류 id 를 잘못 적으면(없는 종류, 없는 펫) **조용히 아무 일도 하지 않습니다** — 콘솔
경고 없이 실패하니, 먼저 `has pet`/`owned pets of` 로 확인하고 쓰는 편이 안전합니다.

---

---

## ❓ 자주 묻는 것

<details>
<summary><b>펫이 안 보여요</b></summary>

`.bbmodel` 파일이 `plugins/BetterModel/models/` 에 있는지, `pets/*.yml` 의 `model:` 값이
파일 이름과 같은지 확인해 주세요. `/betterpets debug` 로 트래커가 잡혔는지도 볼 수 있어요.
Bedrock 플레이어에게만 안 보인다면 GeyserModelEngine 이 필요합니다.
</details>

<details>
<summary><b>펫이 벽에 끼거나 뒤처져요</b></summary>

3초 동안 가까워지지 못하면 자동으로 순간이동합니다. 너무 자주 그러면 `pets/*.yml` 의
`teleport-distance` 를 줄여 보세요.
</details>

<details>
<summary><b>설정을 고쳤는데 그대로예요</b></summary>

`/betterpets reload` 를 쳐 주세요. 문제가 있으면 뭐가 잘못됐는지 하나씩 알려줍니다.
(설정 오류 문구는 파일 경로와 키를 그대로 담고 있어서 언어 설정과 무관하게
한국어로 나옵니다 — 번역해도 그 안의 경로는 그대로라 오히려 읽기 어려워져요.)
`config.yml` · `rarity.yml` · `items.yml` · `pets/*.yml` · `lang/*.yml` 이 전부 다시
읽히고, 성장·기믹·한도·알림·비행·Discord 채널까지 그 자리에서 바뀝니다.

> **이미 소환해 둔 펫은 예외예요.** 모델·속도는 소환할 때 붙기 때문에, 나와 있는
> 펫은 예전 정의로 계속 돌아요. 다시 소환하면 새 설정이 붙습니다. 리로드할 때 몇 마리가
> 그런 상태인지 알려드려요 — 전부 자동으로 다시 소환하지 않는 건, 설정 오타 하나로
> 서버 전체의 펫이 사라질 수 있어서입니다.
</details>

<details>
<summary><b>펫을 여러 마리 꺼내고 싶어요</b></summary>

`config.yml` 의 `pets.max-active` 를 올리세요.
</details>

<details>
<summary><b>디스코드로 알림이 안 가요</b></summary>

`/betterpets debug` 의 연동 줄에서 DiscordSRV 가 `(O)` 인지 보세요. `(X)` 면 플러그인이
없는 거고, `(O)` 인데도 안 가면 `integrations.discord.channel` 이름이 DiscordSRV 쪽
채널 이름과 같은지 확인해 주세요.
</details>

---
