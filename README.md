# BotTycoon 코드 학습

BotTycoon 프로젝트의 코드를 분석하고 학습하기 위한 레포지토리.
원본 프로젝트에서 **소스 코드 + 학습 문서 + 핵심 설정 파일**만 선별하여 관리한다.

---

## 포함된 프로젝트

### tycoon-lite/ — 서버 플러그인 (학습용 추출본)
- **종류**: Paper API 1.20.1+ Minecraft 서버 플러그인 (Java 21, Maven)
- **기능**: 경제 시스템, 직업, 강화, 도감, 업적, 칭호, 거래, 월드 관리
- **파일**: Java 소스 248+개, YAML 설정, 학습 가이드

### minecraft-mod/ — 클라이언트 모드
- **korean-input-fix/**: 한글 입력 수정 Fabric 모드 (Java 17, Gradle)
- **tycoon-client/**: BotTycoon 전용 HUD/UI 클라이언트 모드 (Fabric, 멀티 모듈)

---

## 원본 프로젝트 경로

필요 시 원본 전체를 참조할 수 있도록 경로를 기록해둔다.

| 프로젝트 | 로컬 경로 | GitHub |
|----------|-----------|--------|
| tycoon-lite (학습용 추출본) | `G:\tycoon-lite` | [bapuri-commits/tycoon-lite](https://github.com/bapuri-commits/tycoon-lite) |
| Minecraft 모드 | `G:\Minecraft Mod` | [bapuri-commits/minecraft-mods-for-bottycoon](https://github.com/bapuri-commits/minecraft-mods-for-bottycoon) |
| 풀 서버 (미포함) | `G:\minecraft-botttycoon-server` | [bapuri-commits/minecraft-botttycoon-server](https://github.com/bapuri-commits/minecraft-botttycoon-server) |

---

## 이 레포에 포함된 것 / 제외된 것

### 포함
- Java 소스 코드 전체 (`src/`)
- 빌드 설정 (`pom.xml`, `build.gradle`)
- 학습 문서 (`docs/study/`, `docs/reference/`)
- 프로젝트 문서 (`README.md`, `CHANGELOG.md`, `DESIGN.md`)
- Cursor AI 규칙 (`.cursor/rules/`, `.cursorrules`)

### 제외 (원본에만 존재)
- `vps-backup/` — 서버 백업 데이터 (6,900+ 파일)
- `scripts/`, `tools/` — 서버 운영용 스크립트
- `docs/reports/`, `docs/prompts/` — 페이즈 보고서, AI 프롬프트
- `docs/bug_balance/`, `docs/casino/`, `docs/cosmetic/` — 기획/운영 문서
- `.gradle/`, `build/`, `run/` — 빌드 산출물
- 풀 서버 프로젝트 (`minecraft-botttycoon-server`) 전체

---

## 학습 진행

- `tycoon-lite/docs/study/STUDY_PLAN.md` — 6단계 학습 계획
- `tycoon-lite/docs/study/STUDY_GUIDE.md` — 아키텍처 및 디자인 패턴 가이드
- `tycoon-lite/docs/study/CONCEPT_NOTES.md` — 개념 노트
