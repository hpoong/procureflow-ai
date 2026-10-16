# AGENTS.md

`procureflow-ai` 작업 규칙의 **단일 원본**이다.  
Codex · Cursor · Claude 모두 이 파일만 진입점으로 쓴다. 규칙은 여기에만 쓰고, `CLAUDE.md` / `.cursor/rules/` 에는 중복으로 적지 않는다.

상세 근거·긴 설명은 `docs/`, 공통 코딩 규칙은 `convention/` 에 있다. 이 파일은 **지켜야 할 것**만 적는다.

---

## 1. 작업 전에 읽을 곳

| 작업 | 읽을 문서 |
| --- | --- |
| 공통 코딩·API·SQL·설정·테스트 등 | [convention/README.md](convention/README.md) — **작업별 문서 표에서 필요한 섹션만** |
| 아키텍처·기능·가이드·ADR·런북 | [docs/README.md](docs/README.md) → 해당 하위 문서 |

`convention/`·`docs/` 전체를 한 번에 읽지 않는다. 표에서 고른 문서·섹션만 연다.

---

## 2. 반드시 지킬 것

1. **패키지** — 애플리케이션 코드는 `com.hopoong.procureflow` 아래.
2. **범위** — 요청·지적에 없는 리팩토링, import 정리, 포맷만 바꾸는 변경을 섞지 않는다.
3. **비밀값** — JWT·DB 비밀번호·API 키를 코드나 `application*.yaml` 에 적지 않는다. 환경 변수로 받는다.
4. **문서 동기화** — 패키지·클래스·경로·설정 키가 바뀌면 같은 작업에서 `docs/` 도 맞춘다.
5. **컨벤션** — 새 코드·수정 코드는 `convention/` 해당 문서를 따른다. 기존 코드를 일괄 고치지 않는다.

---

## 3. 에이전트 진입 (이 파일만 규칙 본문)

| 도구 | 진입 파일 | 역할 |
| --- | --- | --- |
| Codex | `AGENTS.md` | 단일 원본 (네이티브 로드) |
| Claude Code | `CLAUDE.md` | `@AGENTS.md` import 만 |
| Cursor | `AGENTS.md` + `.cursor/rules/agents.mdc` | 규칙 본문은 `AGENTS.md`만. mdc는 포인터 |

`CLAUDE.md` 는 `.cursorignore` 에 넣어 Cursor 이중 로드를 막는다.  
지침 변경은 **`AGENTS.md` 만** 수정한다.
