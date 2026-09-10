# BetterPets 작업 규칙

악어의 놀이터 2 스타일 펫 시스템. Paper 26.2 / Java 25 / BetterModel 3.4.1.
설계 근거는 [docs/DESIGN.md](docs/DESIGN.md), 모델 규격은 [docs/MODELING.md](docs/MODELING.md),
진행 상황은 [docs/ROADMAP.md](docs/ROADMAP.md). 사용법은 [README](README.md).

**README 는 짧게 유지한다.** 답할 것은 셋뿐이다 — 이게 뭔지, 내 서버에 맞는지, 어디로 가면
되는지. 명령어·권한·설정 표를 README 에 넣지 않는다. 잘 만든 플러그인들(LuckPerms, Chunky)이
그렇게 한다: README 는 안내판이고 상세는 링크 너머에 있다.

| 문서 | 담는 것 |
| --- | --- |
| `README.md` | 소개 · 설치 3단계 · 문서 링크 · 빌드 · 라이선스 |
| `docs/USAGE.md` | 명령어 · 권한 · 설정 전체 · 연동 · 문제 해결 |
| `docs/MODELING.md` | 모델 규격 |
| `docs/DESIGN.md` | 설계와 그 근거 |
| `docs/ROADMAP.md` | 진행 상황 |

문체도 다르다. README 와 USAGE 는 **존댓말로 친근하게**, DESIGN 과 CLAUDE.md 는
**평서형으로 간결하게**.

## 빌드

```bash
JAVA_HOME=/opt/jdk/jdk-25.0.4.1+1 mvn -B clean package
```

`JAVA_HOME` 을 반드시 지정한다. **BetterModel 3.x 는 모든 버전이 클래스 파일 major 69(Java 25)**
로 배포돼서 JDK 24 이하로는 컴파일도 로드도 안 된다. 이건 문서가 아니라 바이트코드를 직접
확인해 정한 것이다 (deepwiki 는 25, README 는 21 이라고 적혀 있었다).

셰이딩은 하지 않는다. `maven-shade-plugin` 은 3.6.2 미만이 Java 25 클래스를 못 읽고,
sqlite-jdbc 를 넣으면 jar 이 16KB → 14MB 가 된다. 저장은 YAML 로 간다.

## 커밋

- **항상 `main` 에 직접 커밋·푸시한다.** 사용자가 명시적으로 요청했다.
- 커밋 메시지는 **한국어**로, **무엇을 했는지가 아니라 왜 그렇게 했는지**를 적는다.
  diff 를 보면 무엇을 했는지는 알 수 있다.
- 푸시 전에 반드시 빌드·테스트를 통과시킨다.

## 코드 규칙

- **주석은 한국어.** 설명이 아니라 **판단의 근거**를 적는다 —
  "이 값을 캐시한다"가 아니라 "매 틱 도는 자리라 캐시한다".
- 되돌리기 어려운 선택에는 **대안을 왜 버렸는지**를 남긴다.
  (예: `ItemDisplay` 대신 보이지 않는 `Mob` 을 쓰는 이유, 리플렉션 연동을 택한 이유)
- 죽은 코드를 남기지 않는다. 안 쓰는 메서드는 지우거나, 쓰이게 만든다.

### 절대 하지 않는 것

- **이름·로어 문자열로 아이템을 식별하지 않는다.** PDC(NBT)만 쓴다 — 모루로 이름을 바꾸면 깨진다.
- **`Map.copyOf` 를 가중치 맵에 쓰지 않는다.** JVM 마다 반복 순서가 달라져 같은 시드에서도
  추첨 결과가 갈린다. `LinkedHashMap` 으로 감싼다 (`EggDefinition`, `PetType.nextStage`).
- **GUI 클릭 핸들러에서 `setCancelled(true)` 를 가장 먼저 부른다.** 로직 뒤로 미루면
  중간에 예외나 조기 return 이 걸리는 순간 아이템 복제가 된다.
- **틱 루프 안에서 객체를 만들거나 `getConfig()` 를 읽지 않는다.** 원작이 렉으로 무너진
  지점이다. 자세한 목록은 DESIGN 의 "실제로 손댄 곳".

### 자주 놓치는 것

- **성장이 일어난 뒤에는 반드시 `PetService.refreshAfterGrowth` 를 부른다.**
  성장은 **재소환 없이** 일어나는데, 모델과 능력은 소환 시점에 붙는다. 빠뜨리면 둘이 어긋난다:
  `ActivePet` 이 소환 시점의 `PetType` 을 붙들고 있어 진화해도 예전 모델이 남고,
  `equip` 이 `summon` 에서만 불려서 **아기로 꺼내둔 펫이 성체가 돼도 능력이 안 붙는다.**
  급여·시간 경과·`/petadmin growth` 세 경로 모두 이 마무리가 필요하다.
  같은 이유로 **알림에도 `pet.type()` 을 쓰면 안 된다** — 진화 전 이름을 부르게 된다.
- **설정을 읽어 들고 있는 쪽은 `/petadmin reload` 때 다시 밀어 넣어야 한다.**
  지금은 셋이다: `RideController.reloadTuning`, `PetService.limits`, `BroadcastService.rules`.
  빠뜨리면 "설정을 다시 읽었습니다"가 거짓말이 된다.
- **레지스트리에서 펫을 뺄 때 소유자가 접속 중이면 `registry.remove` 가 아니라
  `PetService.dismiss` 를 쓴다.** 레지스트리는 능력을 모른다. 직접 빼면 펫 없는
  플레이어에게 능력치 모디파이어가 남는다 (접속 중에는 `purge` 가 돌지 않는다).
  소유자가 이미 나갔다면 `registry.remove` 로 충분하다 — 다음 접속의 `purge` 가 걷는다.
- **사용자에게 보이는 문자열을 코드에 적지 않는다.** 채팅뿐 아니라 **GUI 아이템 이름·로어·
  인벤토리 제목·명령어 사용법**도 전부 `lang/*.yml` 로 간다. 한동안 GUI 만 한국어가 박혀
  있어서, `language: en_us` 로 바꾸면 채팅은 영어인데 보관함은 한국어였다 — 반쯤 번역된
  화면이 아예 번역이 없는 것보다 나쁘다.
- **성체·성장 알림은 두 경로 모두에서 나가야 한다** — 먹여서 자란 쪽(`InteractionListener`)과
  시간이 흘러 자란 쪽(`PetTicker`). 후자를 빠뜨리면 조용히 성체가 된다.

## 테스트

선은 **"Bukkit 이 필요한가"**로 긋는다. 서버 없이 돌릴 수 있는 것만 단위 테스트로 덮고,
나머지는 DESIGN 의 수동 체크리스트에 맡긴다. **Mockito 로 `Player` 를 흉내 내지 않는다** —
그렇게 만든 테스트는 우리 코드가 아니라 우리가 상상한 Bukkit 을 검증한다.

로직을 테스트하고 싶으면 Bukkit 을 안 쓰는 쪽으로 **옮긴다.**
(예: 방송 판정을 `BroadcastService` 에서 `Rules` 레코드 안으로)

## 외부 연동

`DiscordSRV` · `Floodgate` 는 **리플렉션**으로 붙는다 — 컴파일 의존이 없어서 없이도 빌드된다.
`PlaceholderAPI` 만 상속이 필요해 `provided` 의존을 건다. 셋 다 **실패하면 조용히 꺼진다.**
연동이 안 되는 것보다 그 때문에 펫 시스템이 멈추는 게 훨씬 나쁘다.

## 아직 검증되지 않은 것

**실제 서버에서 한 번도 돌려본 적이 없다.** `.bbmodel` 파일이 없어서 모델이 뜨는지 확인할
수 없고, DiscordSRV·Floodgate 호출 사슬은 문서 기준이라 실기에서 어긋날 수 있다.
"코드 완료"는 빌드·단위 테스트까지라는 뜻이다. 그렇지 않은 것처럼 쓰지 않는다.
