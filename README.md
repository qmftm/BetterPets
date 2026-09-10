# 🐾 BetterPets

_알에서 키워 타고 다니는 펫 — Paper 26.2 · [BetterModel](https://github.com/toxicity188/BetterModel) 기반_

![Minecraft](https://img.shields.io/badge/Minecraft%20%2F%20Paper-26.2-brightgreen)
![Java](https://img.shields.io/badge/Java-25-orange)
![BetterModel](https://img.shields.io/badge/BetterModel-3.4.1+-blue)
![Language](https://img.shields.io/badge/Language-KO%20%2F%20EN-yellow)

알을 까면 아기 펫이 나옵니다. 먹이를 주거나 그냥 데리고 다니면 자라고, 다 크면 등에
올라탈 수 있어요. 운이 좋으면 하늘을 나는 펫이 나옵니다. 악어의 놀이터 2의 펫 시스템을
Paper 서버에 옮긴 플러그인이에요.

> **⚠️ 아직 서버에서 돌려본 적이 없습니다.** 기능은 전부 구현됐고 단위 테스트 141개를
> 통과하지만, 펫이 화면에 뜨는지는 `.bbmodel` 모델 파일이 있어야 확인할 수 있어요.
> 자세한 상태는 [ROADMAP](docs/ROADMAP.md)에 있습니다.

## 시작하기

| | 버전 |
| --- | --- |
| Paper | **26.2+** (Spigot 미지원) |
| Java | **25** — BetterModel 이 Java 25로 빌드돼 있어서 그 아래로는 로드되지 않아요 |
| [BetterModel](https://hangar.papermc.io/toxicity188/BetterModel) | **3.4.1+** — 없으면 플러그인이 스스로 꺼집니다 |

1. BetterModel 과 `BetterPets-*.jar` 를 `plugins/` 에 넣고 서버 재시작
2. `.bbmodel` 모델을 `plugins/BetterModel/models/` 에 넣기 → [모델 규격](docs/MODELING.md)
3. `/petadmin egg <닉네임> wolf_egg` 로 알을 받아 우클릭

처음 켜면 `plugins/BetterPets/` 에 설정이 전부 생기고, 예시 펫 3종(늑대 · 드래곤 · 돼지)도
같이 들어 있어요. 파일을 열어 고치고 `/petadmin reload` 하면 바로 적용됩니다.

**→ 명령어 · 권한 · 설정은 [사용 안내](docs/USAGE.md)에 있어요.**

## 문서

| | |
| --- | --- |
| [사용 안내](docs/USAGE.md) | 명령어 · 권한 · 설정 · 연동 · 문제 해결 |
| [모델 규격](docs/MODELING.md) | 애니메이션 이름, 본 태그, Bedrock 내보내기 |
| [설계 문서](docs/DESIGN.md) | 아키텍처와 그 근거, 원작 분석, 리스크 |
| [진행 상황](docs/ROADMAP.md) | 무엇이 끝났고 무엇이 남았는지 |

## 빌드

```bash
export JAVA_HOME=/path/to/jdk-25     # 25 필수
mvn clean package                    # target/BetterPets-*.jar
```

외부 런타임 의존성이 없습니다. 저장은 YAML 로 해요.

## 참고

- [BetterModel](https://github.com/toxicity188/BetterModel) — 렌더링 엔진 (MIT)
- [betterpets-paper](https://github.com/yourShika/betterpets-paper) — 탑승 · 비행 구현을 참고했습니다 (MIT)
- [악어의 놀이터 2](https://namu.wiki/w/%EC%95%85%EC%96%B4(%EC%9D%B8%ED%84%B0%EB%84%B7%20%EB%B0%A9%EC%86%A1%EC%9D%B8)/%EB%8C%80%EA%B7%9C%EB%AA%A8%20%EC%BD%98%ED%85%90%EC%B8%A0/%EC%95%85%EC%96%B4%EC%9D%98%20%EB%86%80%EC%9D%B4%ED%84%B0%202) — 원작 시스템

> 원작의 실제 모델 · 텍스처를 추출해 쓰는 건 저작권 침해입니다. 시스템 구조를 참고하는
> 것과 에셋을 복제하는 건 다른 얘기예요.

## 라이선스

아직 정하지 않았습니다. `LICENSE` 파일이 없으면 기본이 "모든 권리 보유"라 다른 사람이
쓰거나 고칠 수 없어요. 공개하실 거면 하나 추가해 주세요.
