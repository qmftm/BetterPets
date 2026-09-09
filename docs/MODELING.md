# 모델 제작 규격

BetterPets가 기대하는 `.bbmodel` 규격. BlockBench로 펫 모델을 만들 때 이 문서를 따르면 된다.

- [빠른 체크리스트](#빠른-체크리스트)
- [애니메이션 이름](#애니메이션-이름)
- [본 태그](#본-태그)
- [권장 구성](#권장-구성)
- [배치와 등록](#배치와-등록)
- [Bedrock(Geyser) 대응](#bedrockgeyser-대응)
- [저작권](#저작권)

---

## 빠른 체크리스트

새 펫 모델을 만들 때 최소한 이것만 지키면 동작한다.

- [ ] 애니메이션 `idle` · `walk` · `run` 이 있다
- [ ] 머리 본 이름이 `h_` 로 시작한다 → 소유자를 바라본다
- [ ] 몸통 본 이름이 `b_` 로 시작한다 → 클릭할 수 있다
- [ ] 탑승 펫이면 `p_seat` 본과 `ride` 애니메이션이 있다
- [ ] 비행 펫이면 `fly` 애니메이션이 있다
- [ ] 파일을 `plugins/BetterModel/models/` 에 넣었다
- [ ] `pets/*.yml` 의 `model:` 값이 파일명(확장자 제외)과 같다
- [ ] 과급식 기믹을 쓴다면 `pet_pig` 도 만들었다 ([돼지](#돼지-과급식-이스터에그))

**Bedrock 플레이어를 받는다면 추가로:**

- [ ] 텍스처가 **한 장**이다 (멀티 텍스처는 변환이 까다롭다)
- [ ] 애니메이션 텍스처를 쓰지 않았다
- [ ] Bedrock용으로 따로 내보냈다 → [Bedrock(Geyser) 대응](#bedrockgeyser-대응)

---

## 애니메이션 이름

플러그인은 아래 이름을 기대한다. 다른 이름을 쓰고 싶으면 `pets/*.yml` 의 `animations:` 블록에서 매핑하면 된다.

| 이름 | 필수 | 재생 | 용도 |
| --- | :---: | --- | --- |
| `idle` | ✅ | 반복 | 정지 |
| `walk` | ✅ | 반복 | 걷기 |
| `run` | ✅ | 반복 | 달리기 |
| `fly` | 비행 펫 | 반복 | 비행 |
| `fly_idle` | | 반복 | 공중 정지(호버링) |
| `ride` | 탑승 펫 | 반복 | 탑승 중 이동 |
| `eat` | | 1회 | 먹이 섭취 |
| `sit` | | 반복 | 대기 명령 |
| `attack` | | 1회 | 공격 능력 |
| `spawn` | | 1회 | 소환 연출 |

필수 항목이 없으면 로드할 때 경고하고 `idle` 로 폴백한다.

> BetterModel의 재생 타입은 `PLAY_ONCE` · `LOOP` · `HOLD_ON_LAST` 세 가지뿐이다. 위 표의 "반복"은 `LOOP`, "1회"는 `PLAY_ONCE` 에 대응한다.

### 이름을 바꾸고 싶다면

```yaml
# pets/wolf.yml
animations:
  idle: wolf_stand      # 모델에 실제로 있는 이름
  walk: wolf_walk
  run: wolf_sprint
```

---

## 본 태그

BlockBench에서 **본 이름 앞에 `태그_` 접두사**를 붙이면 특수 기능이 붙는다.

### 파싱 규칙

BetterModel이 본 이름을 읽는 방식이다. 정확히 알아야 태그가 안 먹는 상황을 피할 수 있다.

1. 본 이름을 **소문자로 변환**한다
2. `_` 로 자른다
3. **앞에서부터 연속으로** 알려진 태그와 일치하는 조각을 태그로 흡수한다
4. 처음 일치하지 않는 조각부터 끝까지가 실제 본 이름이 된다

| 본 이름 | 결과 |
| --- | --- |
| `h_head` | 태그 `HEAD` + 이름 `head` |
| `hi_b_body` | 태그 `HEAD_WITH_CHILDREN` + `HITBOX`, 이름 `body` |
| `body_h` | ❌ 태그 아님 — 태그는 **앞에 연속으로** 와야 한다 |
| `Head` | 소문자 변환되어 `head`, 태그 없음 |

### 태그 목록

| 접두사 | 기능 |
| --- | --- |
| `h_` | 엔티티 머리 회전을 따라감 |
| `hi_` | 머리 회전을 따라감 (자식 본 포함) |
| `b_` / `ob_` | 이 본을 따라가는 **히트박스** 생성 |
| **`p_`** | **탑승 좌석** (조종 가능) |
| `sp_` | 탑승 좌석 (조종 불가) |
| `tag_` | 이름표 |
| `mtag_` / `ptag_` | 몹 / 플레이어 이름표 |
| `glow_` | 발광 |
| `li_` / `ri_` | 좌손 / 우손 아이템 표시 |
| `ph_` `pra_` `prfa_` `pla_` | 플레이어 머리 · 오른팔 · 오른아래팔 · 왼팔 |

---

## 권장 구성

### 기본 펫

```
pet_wolf.bbmodel
├─ h_head            머리 — 소유자를 바라본다
├─ b_body            몸통 — 클릭 상호작용용 히트박스
│   ├─ leg_fl
│   ├─ leg_fr
│   ├─ leg_bl
│   └─ leg_br
└─ tag_name          이름표 위치 (선택)
```

### 탑승 · 비행 펫

```
pet_dragon.bbmodel
├─ h_head
├─ b_body
│   ├─ p_seat        ★ 탑승 좌석. 탑승 펫이면 필수
│   ├─ wing_l
│   ├─ wing_r
│   └─ leg_*
└─ tag_name
```

`p_seat` 의 위치가 곧 플레이어가 앉는 자리다. 등에 얹히도록 몸통 위쪽에 둔다.

### 돼지 (과급식 이스터에그)

기본 설정이 `pet_pig` 모델을 참조한다. 아기 펫에게 먹이를 몰아주면 이 종류로 바뀌므로,
**이 모델이 없으면 변신했을 때 소환이 실패한다.** 걷는 탑승만 되는 평범한 지상 펫이면 된다.

```
pet_pig.bbmodel
├─ h_head
├─ b_body
│   ├─ p_seat        걷는 탑승만 되므로 좌석은 있어야 한다
│   └─ leg_*
└─ tag_name
```

기믹을 쓰지 않는다면 `config.yml` 의 `gimmick.overfeed.enabled: false` 로 끄면
이 모델을 만들지 않아도 된다.

### 알

**알 모델은 만들지 않아도 된다.** 알은 아이템일 뿐이라 3D 모델이 아니라 아이템 텍스처만 쓴다.
`items.yml` 의 `item-model` 에 리소스팩 아이템 모델 키를 적으면 된다 (생략하면 `material` 의 기본 텍스처).

---

## 배치와 등록

1. `.bbmodel` 파일을 `plugins/BetterModel/models/` 에 넣는다
2. 파일명(확장자 제외)이 곧 모델 이름이다 — `pet_wolf.bbmodel` → `pet_wolf`
3. `pets/*.yml` 에서 그 이름을 참조한다

```yaml
# plugins/BetterPets/pets/wolf.yml
id: wolf
model: pet_wolf      # ← plugins/BetterModel/models/pet_wolf.bbmodel
```

4. `/petadmin reload` 또는 서버 재시작

설정에서 존재하지 않는 모델을 참조하면 로드 시 경고한다. 오타는 이때 잡힌다.

리소스팩은 BetterModel이 생성하며, 배포 경로는 BetterModel 설정을 따른다.

---

## Bedrock(Geyser) 대응

Bedrock 클라이언트는 BetterModel이 쓰는 item-display 방식의 커스텀 모델을 **볼 수 없다.** 자바 플레이어에게는 드래곤이 보이는 자리에 Bedrock 플레이어에게는 아무것도 안 보인다.

[GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine)이 이걸 메워준다. BetterModel을 **공식 지원한다** — README에 "converts ModelEngine/BetterModel models for bedrock players" 라고 명시돼 있고, 소스에도 `BetterModelHandler` 가 따로 있다.

### 모델 작업이 두 갈래가 된다

같은 `.bbmodel` 로 **두 번 내보내야** 한다.

| 대상 | 방법 | 결과물 위치 |
| --- | --- | --- |
| Java | `.bbmodel` 그대로 | `plugins/BetterModel/models/` |
| Bedrock | BlockBench 전용 packer로 내보내기 | GME 확장의 `input/` 폴더 |

Bedrock 내보내기 절차:

1. [GeyserModelEngineBlockbenchPacker](https://github.com/GeyserExtensionists/GeyserModelEngineBlockbenchPacker) 플러그인을 BlockBench에 설치
2. `.bbmodel` 을 열고 `File → Export → Export GeyserModelEngine Model`
3. 나온 zip을 풀어 `input/` 폴더에 넣는다 — **모델당 폴더 하나씩**
4. Geyser를 리로드하면 리소스팩이 자동 생성·적용된다

### 모델을 만들 때 지킬 제약

변환을 염두에 두면 나중에 고생이 줄어든다.

| 제약 | 이유 |
| --- | --- |
| **텍스처는 한 장으로** | 멀티 텍스처는 packer 플러그인을 거쳐야만 변환된다 |
| **애니메이션 텍스처 피하기** | 위와 같다 |
| 본 구조를 단순하게 | 변환 대상이 적을수록 문제가 적다 |

Java 전용으로 만들었다가 나중에 Bedrock을 붙이면 텍스처를 다시 작업해야 할 수 있다. **처음부터 단순하게 만드는 편이 낫다.**

### 알아둘 함정

> ⚠️ **리소스팩은 모델 "개수"가 바뀔 때만 재생성된다.**
>
> 모델을 수정만 하고 개수는 그대로면 반영되지 않는다. `generated_pack` 폴더를 지워서 강제 재생성해야 한다. 모델을 고쳤는데 Bedrock에서 그대로라면 이걸 의심할 것.

### 필요한 플러그인

플러그인 하나가 아니라 스택 전체를 깔아야 한다.

| 위치 | 플러그인 |
| --- | --- |
| `plugins/` | GeyserModelEngine, geyserutils-spigot, packetevents |
| `plugins/[Geyser]/extensions/` | GeyserModelEngineExtension, geyserutils-geyser |

Floodgate를 쓴다면 `send-floodgate-data: true` 설정과 `key.pem` 복사도 필요하다.

---

## 저작권

> ⚠️ **악어의 놀이터에서 사용 중인 실제 모델·텍스처 파일을 추출해 쓰는 것은 저작권 침해다.**
>
> 시스템 구조(등급 체계, 알 부화, 탑승 방식)를 참고하는 것과 에셋을 복제하는 것은 다르다. 모든 `.bbmodel` 은 직접 제작하거나 사용 허가가 명확한 것만 쓴다.
