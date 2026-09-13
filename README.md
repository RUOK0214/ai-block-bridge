# AI Block Bridge

Minecraft **Java 26.2 / Fabric** — 제작자 **RUOK0214**.
AI와 블록 배치 데이터를 텍스트로 주고받기 위한 모드입니다. AI API나 인터넷 연결은 사용하지 않습니다.
현재 버전: **0.1.12**. 중요한 월드의 복사본에서 먼저 테스트하세요.

## 설치

1. Fabric Loader **0.19.5 이상**, Minecraft **26.2**를 사용합니다.
2. `ai-block-bridge-0.1.12.jar`와 Fabric API **0.160.0+26.2 이상(26.2용)**을 `mods` 폴더에 넣습니다. 기존 버전 JAR는 제거합니다.
3. 싱글플레이는 **치트 허용**이 필요합니다. 멀티플레이는 서버·클라이언트 양쪽에 설치하고 **OP 레벨 4**가 필요합니다.
4. Java 개발·실행 기준은 **Java 25**입니다.

설치 파일은 [최신 릴리스](https://github.com/RUOK0214/ai-block-bridge/releases/latest)에서 받습니다. `-sources.jar`는 설치용이 아닙니다.

## 언어 지원

게임의 **설정 → 언어**를 따릅니다. 한국어(`ko_kr`)와 영어(`en_us`)를 지원하며, 번역이 없는 언어에서는 영어가 기본으로 표시됩니다. 설정창, 틱 기록, 파일 선택창의 제목, 상태 안내와 모드 자체 오류 메시지에 적용됩니다. 운영체제 파일 선택창의 기본 버튼과 외부 라이브러리 오류는 해당 환경의 언어를 따릅니다.

서버 응답은 클라이언트에서 번역하므로 멀티플레이에서도 각자의 언어로 표시됩니다. **서버와 클라이언트를 모두 0.1.7으로 업데이트하세요.** 구조·타임라인의 블록 ID와 데이터 문법은 언어 설정에 관계없이 동일하며, 자동 생성되는 파일 주석은 영어입니다.

## AI 요청문 복사

설정창 또는 틱 기록 창의 **AI 요청문** 버튼에서 **회로 설명 / 새 회로 설계 / 오류 수정**을 선택합니다. 요청문을 수정하고 복사한 뒤 AI 채팅에 붙여넣으세요. 블록 문법, 상대좌표, 공기 처리, 기록 해석과 검증 조건이 포함됩니다. 게임 언어에 따라 한국어·영어로 생성됩니다.

요청문에는 회로 데이터가 포함되지 않습니다. 설명·수정 요청에는 같은 회로의 구조 파일과 타임라인 파일을 직접 첨부하세요. AI 서비스로 자동 전송하거나 자동 첨부하지 않습니다.

## 기록 제한 알림

6,000틱, 변경 항목 100,000개 또는 기록 용량 20,000,000자 제한에 도달하면 기록을 멈추고 **화면 안내와 채팅에 이유를 한 번 표시**합니다. 영역을 더 이상 불러올 수 없을 때도 알립니다.

완료된 기록은 접속 중 서버에 보관됩니다. **N 키 또는 틱 기록 창의 ‘기록 가져오기’**로 가져와 파일로 내보내세요. 가져오기 전에는 새 기록을 시작할 수 없습니다. 한 틱 중간에 용량·항목 제한을 넘으면 해당 틱 전체를 제외하고, 이전에 완료한 틱은 유지합니다. 게임 종료·접속 해제 전 파일로 저장하세요.

## 기본 사용법

| 조작 | 기능 |
|---|---|
| 블록을 보고 `[` | 모서리 1 선택 |
| 블록을 보고 `]` | 모서리 2 선택 |
| `B` | 설정·스크립트 창 열기 |
| `\` | 월드 안의 선택 영역 표시 켜기 / 끄기 |
| `N` | 선택 영역의 틱 변화 기록 시작 / 중지 |
| 모서리 좌표 입력 → 좌표 적용 | 작업 영역 직접 변경 |
| 영역 → 스크립트 | 선택 영역에서 공기를 제외한 블록을 텍스트화 |
| 스크립트화 취소 | 직전 스크립트화 전의 텍스트로 복원 |
| 월드에 붙여넣기 | 확인 후 스크립트에 지정된 좌표만 교체 |
| 붙여넣기 취소 | 직전 붙여넣기의 블록·NBT를 복원 |
| 틱 기록 | 배치용 스크립트와 분리된 틱 기록 창 열기 |

키는 게임의 설정 → 조작 → 키 지정 → AI Block Bridge에서 바꿀 수 있습니다.
두 모서리는 포함 범위입니다. X/Y/Z 각각의 최소값이 상대좌표 `(0,0,0)`입니다. 회전·축 반전은 하지 않습니다.
선택 영역은 **노란 외곽선과 옅은 파란 격자**로 표시되며, 벽 너머에서도 보입니다. 모서리 1은 **하늘색**, 모서리 2는 **주황색**, 원점 블록은 **초록색**입니다. 모서리 월드 좌표와 영역의 X×Y×Z 크기도 표시합니다.
모서리 하나만 선택해도 그 블록을 표시합니다. 좌표 적용 후 즉시 갱신되며, 차원 이동·접속 종료 시 사라집니다. WorldEdit나 WorldEdit CUI 설치는 필요하지 않습니다.
표시 격자는 작은 영역에서 1블록 간격이며, 긴 축은 최대 64분할에 맞춰 간격을 넓힙니다. 작업 한도 1,048,576블록을 넘는 선택은 빨간 외곽선으로 표시합니다. 표시 켜짐 상태는 클라이언트 세션 동안 유지합니다.
편집창의 모서리 숫자는 붙여넣기·스크립트화를 누를 때에도 적용됩니다.
`Ctrl+A/C/V/X`는 편집창 기본 조작, `Ctrl+Z`와 **편집 취소**는 텍스트 수정 취소입니다.
파일 버튼은 운영체제 파일 선택 창을 엽니다. UTF-8 텍스트를 사용하세요. 기본 위치는 게임 폴더의 `ai-block-bridge/`입니다.

## 틱 변화 기록

배치 스크립트 창의 **틱 기록** 버튼으로 별도의 기록 창을 엽니다. 기록 시작 후 회로를 작동하고 `N`을 누르면 기록이 끝나면서 결과 창이 열립니다.

```text
# AI Block Bridge Timeline v1
# Only changes after recording started. @tick is a server-tick offset.

@tick 1
6 4 3 | minecraft:lever[face=wall,facing=east,powered=true]

@tick 2
3 1 3 | minecraft:sticky_piston[extended=false,facing=east]
5 1 3 | minecraft:air
```

- 기록을 시작한 다음 서버 틱부터 계산하며, 변화가 없는 틱은 생략합니다.
- 같은 틱에 바뀐 블록은 하나의 `@tick` 구역에 모읍니다.
- 블록 상태와 블록 엔티티 NBT를 기록하고, 사라진 블록은 `minecraft:air`로 남깁니다.
- 최대 6,000틱(20 TPS 기준 5분), 변경 항목 100,000개 또는 20,000,000자까지 기록합니다. 제한에 도달하면 수집을 중단하고 기존 기록을 보존합니다. `N` 또는 기록 중지로 결과를 가져오세요. 제한을 넘기는 틱은 부분 기록하지 않습니다.
- 기록 창의 **호퍼 쿨다운 제외**는 기본 켜짐입니다. 호퍼 NBT의 `TransferCooldown`을 비교·출력에서 제외하되 아이템 내용물과 블록 상태 변화는 유지합니다. 끄면 원본 NBT를 기록합니다. 옵션은 기록 시작 전에 선택하며 이번 게임 실행 동안 유지됩니다.
- 배치 스크립트는 기존 2,000,000자 제한을 유지합니다. 큰 기록은 전송·편집창 표시 시 잠시 지연될 수 있습니다.
- 기록 스크립트는 전용 창에서 편집·복사·불러오기·내보내기 할 수 있습니다. 현재 버전에서는 분석용이며 월드 재생이나 붙여넣기에 사용하지 않습니다.

## 스크립트 형식

```text
# AI Block Bridge Script v1
# size: 3 2 4
0 0 0 | minecraft:stone
1 0 0 | minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
2 0 0 | minecraft:barrel[facing=up,open=false] | {Items:[{Slot:0b,id:"minecraft:diamond",count:3}]}
0 1 0 | minecraft:air
```

- 1행 = 상대좌표 `x y z` + `|` + 블록 ID와 선택적 `[상태]` + 선택적 `| {SNBT}`.
- 좌표의 쉼표도 허용합니다: `0, 2, 1 | minecraft:stone`.
- `#`로 시작하는 행과 빈 행은 무시합니다. `size`, `origin` 주석은 설명용이며 실제 영역을 변경하지 않습니다.
- 범위 밖 좌표·중복 좌표·잘못된 블록/상태·NBT 문법을 실행 전에 거부합니다.
- 기록하지 않은 좌표는 **유지**합니다. 제거하려면 `minecraft:air`를 명시하세요.
- 영역을 스크립트화할 때 공기 블록은 자동으로 제외됩니다. 따라서 이 스크립트를 다른 영역에 붙여넣어도 그 위치의 기존 블록은 유지됩니다.
- 블록 엔티티 `x/y/z`는 붙여넣을 실제 좌표로, `id`는 해당 블록의 엔티티 타입으로 보정합니다.
- NBT가 없는 통을 붙여넣으면 **빈 통**으로 교체됩니다. 기존 내용물을 합치지 않습니다.
- 블록 엔티티 종류별 NBT 의미·항목 스키마는 Minecraft가 해석합니다. 알 수 없는 NBT 키는 게임에서 무시될 수 있습니다.

## 제한과 안전

- 영역 최대 **1,048,576블록**(예: 128×128×64), 스크립트 최대 **2,000,000자**. 읽히지 않은 청크, 월드 경계 밖, 높이 밖 작업은 거부합니다.
- 0.1.5부터 큰 영역의 높이·경계는 양 끝 모서리로, 청크 로드는 청크별로 검사합니다. 틱 기록기는 상태 배열을 재사용하고 바뀐 블록만 기록 항목을 만듭니다. 공기에서 블록으로 바뀌는 경우도 감지합니다. 전체 영역 스캔은 매 틱 수행하므로 영역 크기와 블록 엔티티 수에 따라 기록 부하는 증가합니다.
- OP 레벨 4가 필요한 이유: 전체 NBT에는 명령 블록 등 강력한 데이터도 들어갈 수 있습니다.
- 검증과 원본 스냅샷 저장 후에만 붙여넣습니다. 실패하면 원본 복원을 시도합니다.
- 붙여넣기 취소는 **직전 1회**, 해당 차원에서만 가능합니다. 이후 블록·NBT가 변경됐으면 덮어쓰지 않고 중단합니다. 화로·호퍼 등은 틱 동작으로 취소 충돌이 날 수 있습니다.
- 취소는 블록/블록 엔티티 대상입니다. 엔티티, 아이템 드롭, 예약 틱, 이후 레드스톤·유체 반응까지 시간을 되돌리는 기능은 아닙니다.
- 일괄 설치 중 주변·형상 업데이트를 억제해 스크립트의 상태를 보존합니다. 연결된 레드스톤의 재계산이나 유체/중력 동작은 추가 블록 업데이트가 필요할 수 있습니다.
- 선택 좌표·붙여넣기 취소 기록은 접속을 종료하면 초기화됩니다. 텍스트는 클라이언트 세션 동안 유지하지만 게임 종료 후 남기려면 파일로 내보내세요.
- 파일 내보내기 외 자동 파일 저장, AI 통신, 원격 코드 실행 기능은 없습니다.

## 개발 및 검증

```sh
./gradlew build
```

Java 25에서 실행합니다. 단위 테스트와 서버 GameTest가 포함됩니다. `./gradlew runClientGameTest`는 실제 클라이언트에서 선택 영역의 일반 표시·벽 너머 표시·숨기기·첫 모서리 표시 화면을 캡처합니다. Linux CI에서는 Xvfb를 사용합니다. OS 파일 창·클립보드는 별도로 수동 확인해야 합니다.
수동 점검 순서: 두 모서리 선택 → 영역 변환 → 통 내용물 확인 → 파일 내보내기/다시 불러오기 → 다른 영역에 붙여넣기 → 붙여넣기 취소 → 스크립트화 취소.

## 프로젝트 기반

개발 설정과 Gradle wrapper는 Fabric 공식 예제 프로젝트를 기반으로 합니다. 기존 CC0 텍스트는 `licenses/CC0-1.0.txt`에 보존했습니다.


### 0.1.8: 구조와 타임라인 세트 저장
기록 시작 시 선택 영역의 초기 구조도 자동으로 캡처합니다. 기록 종료 또는 제한 도달 후 기록을 가져온 다음, 타임라인 화면의 **기록 세트 저장 (원본 구조 + 타임라인)** 을 누르고 저장 위치를 선택하세요. 새 `recording_날짜_시간_고유번호` 폴더 안에 `structure.txt`와 `timeline.txt`가 저장됩니다. 두 파일을 함께 AI에 첨부하면 됩니다.

세트 저장은 가장 최근에 가져온 기록 원본을 저장합니다. 편집기 수정·다른 파일 가져오기는 기록 원본에 영향을 주지 않습니다. 편집한 타임라인만 저장하려면 기존 내보내기를 사용하세요. 새 기록을 가져오면 이전 세트가 교체되므로 필요한 기록은 먼저 저장하세요. 구조 캡처가 크기 제한을 넘으면 기록을 시작하지 않습니다. 멀티플레이에서는 서버와 클라이언트 모두 0.1.8로 업데이트하세요.

Recording now captures the initial structure automatically. After retrieving a recording, choose **Save recording set (original structure + timeline)** and select a parent directory. A new timestamped, unique folder contains `structure.txt` and `timeline.txt`. This saves the latest original recording, independently of editor changes/imports; use the existing Export button for edited timeline text. Save each set before retrieving another recording. Update both server and client to 0.1.8.


## 라이선스 / License

**MIT License · Copyright (c) 2026 RUOK0214**

0.1.11부터 MIT 라이선스로 배포합니다. 사용·수정·재배포·상업적 이용을 허용하며, 복제본이나 상당 부분을 재사용할 때 저작권 고지와 라이선스 고지를 포함해야 합니다. [LICENSE](LICENSE)와 [NOTICE](NOTICE)를 참고하세요.

**0.1.10까지 CC0로 공개된 기존 부분의 이용 권한은 유지됩니다.** MIT 전환은 기존 CC0 권한을 취소하거나 소급하여 제한하지 않습니다. 외부 구성요소는 각자의 라이선스를 유지합니다.

From 0.1.11, the project is distributed under MIT. Preserve the copyright and license notice when redistributing copies or substantial portions. Historical material published through 0.1.10 remains available under CC0; this change does not revoke those permissions. New copyrightable contributions owned by RUOK0214 are offered under MIT. See LICENSE and NOTICE for scope and third-party notices.


## 0.1.12 — 엔티티 기록 옵션

- 구조 편집 화면: **엔티티도 포함하기 · 구조**. 영역 가져오기 시점의 엔티티를 `structure.txt`에 포함합니다.
- 타임라인 화면: **엔티티도 포함하기 · 타임라인**. 기록 시작 시점과 이후 매 서버 틱의 엔티티 변화를 포함합니다.
- 두 옵션은 독립적이며 기본값은 꺼짐입니다. N 키로 시작해도 현재 옵션이 적용됩니다.
- 기록 세트의 시작 구조에는 **구조 옵션**, 타임라인에는 **타임라인 옵션**이 적용됩니다.
- 아이템, 떨어지는 모래/콘크리트 가루, 몹, 화살, 광산 수레, 아이템 액자 등 서버 엔티티의 종류·상대 위치·저장 가능한 전체 NBT를 기록합니다. 플레이어는 제외합니다.
- 같은 엔티티는 UUID로 연결됩니다. `initial`은 시작 상태, `enter`는 영역에 새로 관측됨, `update`는 위치/NBT 변경, `leave`는 관측 영역에서 사라짐입니다. `enter`/`leave`는 생성/죽음만을 뜻하지 않습니다.
- 범위 판정은 엔티티 위치가 최소 좌표 이상, 최대 블록 좌표+1 미만인지로 합니다. 한 틱 안에 생겼다가 사라진 엔티티는 서버 틱 끝 샘플링으로 관측되지 않을 수 있습니다.

```text
# @entity initial UUID | 1.25 2.0 3.75 | minecraft:item | {전체 저장 NBT}

@tick 12
# @entity update UUID | 1.25 1.5 3.75 | minecraft:item | {변경 후 전체 NBT}

@tick 13
# @entity leave UUID | 1.25 1.5 3.75 | minecraft:item | {마지막으로 관측한 NBT}
```

위 예시의 `UUID`와 NBT 설명은 자리표시자입니다. 실제 파일에는 UUID와 실제 SNBT가 들어갑니다.
별도 위치 필드는 영역 원점 기준 상대 좌표이고, **NBT 안의 Pos·부착 위치 등은 원래 월드 좌표**입니다.
타임라인 옵션을 켜면 기존 엔티티는 `@tick 0`에 포함되어 구조 옵션이 꺼져 있어도 분석할 수 있습니다.
엔티티 레코드는 기존 파일과 호환되는 **분석용 주석**입니다. 블록 붙여넣기·되돌리기는 기존처럼 블록만 처리하며 엔티티 생성이나 타임라인 재생을 수행하지 않습니다.

엔티티는 한 시점에 최대 4,096개입니다. 엔티티 기록도 구조 200만 자, 타임라인 2,000만 자/100,000항목 제한에 포함됩니다.
이동·나이·상태가 자주 바뀌는 엔티티가 많으면 한도에 빨리 도달할 수 있습니다. 한도를 넘는 마지막 틱은 부분 저장하지 않고 제외합니다.

### Entity capture (English)
Structure capture and timeline recording have independent, default-off **Include entities** switches. Players are excluded.
Entity comments contain UUID, relative position, registry type and full serialized NBT (whose internal coordinates remain absolute).
Timelines include existing entities at tick zero and then `enter`, `update`, and `leave` observations at end-of-server-tick resolution.
Entity comments are analysis data, not entity spawning/replay instructions; block paste/undo behavior is unchanged.
