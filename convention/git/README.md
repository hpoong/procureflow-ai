# Git · 브랜치 · PR

GitHub Flow · Semantic Versioning 관례를 따른다.
커밋 메시지는 [commit-convention](../commit/commit-convention.md).

## 브랜치

- `main`은 항상 배포 가능한 상태로 둔다.
- 작업 브랜치는 `main`에서 딴다.
- 이름 예: `feat/order-confirm`, `fix/empty-list`, `docs/convention`
- 브랜치 하나 = 목적 하나. 끝나면 삭제한다.
- `main`에 직접 커밋하지 않는다.

## PR

- 대상은 기본 `main`.
- 제목은 커밋 형식과 같게 (`feat: …` / `fix: …`).
- 본문에 무엇을·왜·어떻게 검증했는지 적는다.
- 작게 유지한다. 커지면 나눈다.
- CI가 통과한 뒤 병합한다. 실패한 검사를 우회하지 않는다.
- 동작·API·설정이 바뀌면 같은 PR에 `docs/`도 맞춘다.
- 비밀·키가 diff에 없는지 확인한다.

## 리뷰 · 병합

- 가능하면 승인 후 병합한다 (혼자 작업이면 README에 예외를 적어 둔다).
- 리뷰는 동작·경계·보안·가독성을 본다. 취향 포맷 트집은 하지 않는다.
- 병합 방식은 저장소에서 squash 또는 rebase 중 하나로 통일한다.
- `main`에 force-push 하지 않는다. 되돌리기는 revert.

## 릴리스

- 태그는 `vMAJOR.MINOR.PATCH` (SemVer).
- 배포 태그는 annotated tag.
- 이미 올린 배포 태그를 옮기지 않는다.

## 체크리스트

- [ ] 브랜치가 `main`에서 왔고 목적이 하나인가?
- [ ] PR에 검증 내용이 있는가? CI가 초록인가?
- [ ] 문서·비밀 점검이 됐는가?
