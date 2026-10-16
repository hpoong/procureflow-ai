# docs

`procureflow-ai` 문서 루트.

## 구조

```
docs/
├── README.md                 ← 이 파일 (구조 설명)
├── architecture/             ← 아키텍처
│   └── README.md
├── features/                 ← 기능 정리 (패키지와 동일)
│   └── README.md
├── guides/                   ← How-to (로컬·연동·작업 절차)
│   └── local-db.md
├── decisions/                ← ADR (왜 이렇게 했는지)
│   └── .keep
└── runbooks/                 ← 장애·운영 대응
    └── .keep
```

| 경로 | 역할 |
|------|------|
| `architecture/` | 시스템 다이어그램, 레이어, 공통 계약 |
| `features/<패키지>/` | 도메인 기능 설계 — **코드 패키지와 동일한 경로** |
| `guides/` | 로컬 기동, 연동, 작업 절차 |
| `decisions/` | 되돌리기 비용이 큰 결정 기록 (ADR) |
| `runbooks/` | 장애 증상 → 확인 → 조치 |

빈 폴더는 `.keep` 으로 추적한다. 첫 문서를 넣으면 `.keep` 을 지워도 된다.

---

## features ↔ 패키지 정렬

`features/` 문서는 **코드 패키지 구조를 최대한 그대로** 따른다.  
패키지가 생기면 문서 폴더도 같은 이름으로 두고, 패키지가 쪼개지면 문서도 같이 쪼갠다.

| 코드 패키지 | 문서 경로 |
|-------------|-----------|
| `com.hopoong.procureflow.order` | `features/order/` |
| `com.hopoong.procureflow.order.item` (예시) | `features/order/item/` |
| `com.hopoong.procureflow.common.*` | `features/common/` (횡단만) |

문서 안에서 하위 주제는 **패키지명(한글명).세부 내용** 형식으로 적는다.

```text
예) order 도메인

- order(주문).주문 상품 정보
- order(주문).배송지 정보
- order(주문).주문 상태 전이
```

```text
예) 패키지·문서 대응

com.hopoong.procureflow.order/
├── controller/
├── service/
├── mapper/
└── model/

docs/features/order/
└── README.md          ← order(주문).* 주제들을 여기(또는 하위 md)에 정리
```

- 도메인 패키지 이름 = `features/` 폴더 이름 (영문 소문자, 코드와 동일)
- 한글명은 문서 제목·목차용 (`order(주문)`). 폴더명에 한글을 쓰지 않는다
- 한 기능만의 내용은 해당 도메인 `features/<도메인>/`에 둔다. 전체 그림만 `architecture/`
- 애매하면 `features/<도메인>/`부터 두고, 패키지가 생기면 경로를 맞춘다
