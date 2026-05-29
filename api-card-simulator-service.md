# Card Simulator Service API 명세

## 목차

1. [카드사 토큰 발급](#1-카드사-토큰-발급)
2. [카드사 토큰 삭제](#2-카드사-토큰-삭제)
3. [결제 승인](#3-결제-승인)
4. [결제 취소](#4-결제-취소)
5. [가승인](#5-가승인)
6. [가승인 취소](#6-가승인-취소)
7. [결제 거래 조회](#7-결제-거래-조회)
8. [가승인 거래 조회](#8-가승인-거래-조회)
9. [카드사 토큰 조회](#9-카드사-토큰-조회)
10. [카드사 실적 조회](#10-카드사-실적-조회)

---

## 1. 카드사 토큰 발급

- **desc**: PG 서버의 billing-key-service로부터 카드 정보 및 멱등성 키를 받아 카드 검증 및 토큰 발급
- **method**: `POST`
- **path**: `/api/v1/card-simulator/token/issue`

### Headers

| Key             | Value              |
|-----------------|--------------------|
| Content-Type    | `application/json` |
| Idempotency-Key | 발급 멱등성 키     |

### Request Body

| 필드              | 타입   | 필수 | 설명                          |
|-------------------|--------|------|-------------------------------|
| `pg_id`           | String | Y    | PG 식별자 (예: "001")         |
| `card_company`    | String | Y    | 카드사                        |
| `card_number`     | String | Y    | 카드번호                      |
| `expiry_date`     | String | Y    | 유효기간 (YYMM)               |
| `cvc`             | String | Y    | 보안코드                      |
| `password_2digit` | String | Y    | 비밀번호 앞 두 자리           |
| `birth_date`      | String | Y    | 생년월일 (YYMMDD)             |

```json

Idempotency-Key : 001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaaa

{
  "pg_id": "001",
  "card_company": "삼성카드",
  "card_number": "8000111234567890",
  "expiry_date": "2912",
  "cvc": "123",
  "password_2digit": "12",
  "birth_date": "900101"
}
```

### Logic

1. `PG Server로부터 카드 정보(Body) 및 멱등성 키(Idempotency-Key 헤더) 수신`
2. `idempotency_key 기반 중복 요청 사전 검사`
    - simulator_card_token에서 issue_idempotency_key 일치 row 조회
        - 존재 시: 저장된 응답 반환 (echo)
        - 미존재 시: 신규 발급 절차(3번~) 진행
3. `카드 유효성 검증` (실패 시 응답만 반환, DB INSERT 없음)
    - card_company + card_number(ECB) 일치 row 조회
        - 미존재 시: (CARD, CARD_NOT_FOUND) 응답
    - card_status 검증
        - LOST: (CARD, CARD_LOST) 응답
        - EXPIRED: (CARD, CARD_EXPIRED) 응답
        - DELETED: (CARD, CARD_DELETED) 응답
        - ACTIVE: 다음 검증 진행
    - expiry_date(ECB) 일치 검증
        - 불일치 시: (CARD, CARD_INVALID_EXPIRY) 응답
    - cvc(ECB) 일치 검증
        - 불일치 시: (CARD, CARD_INVALID_CVC) 응답
    - password_2digit 해시 검증
        - 입력값 + 해당 row의 card_salt로 SHA-256 계산 후 DB 저장값과 비교
        - 불일치 시: (CARD, CARD_INVALID_PASSWORD) 응답
4. `사용자 유효성 검증`
    - simulator_card.user_id로 simulator_user 조회
    - birth_date(ECB) 일치 검증
        - 불일치 시 (또는 user 미존재): (USER, USER_INVALID_INFO) 응답
5. `(card_id, pg_id) ACTIVE 토큰 중복 검사`
    - 존재 시: (TOKEN, TOKEN_DUPLICATE) 응답
6. `카드사 토큰 생성`
    - UUID v4 기반 32자 (하이픈 제외)
7. `simulator_card_token row INSERT`
    - 컬럼: card_id, card_company, pg_id, issue_idempotency_key, card_token(ECB 암호화), issue_response_code, issue_response_message, token_status='ACTIVE'
    - 응답 코드/메시지: (TOKEN, SUCCESS)
    - DB UNIQUE 제약 위반 시 (동시 요청 race): (TOKEN, TOKEN_DUPLICATE) 응답
8. `PG Server에 응답 반환`

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `pg_id`            | String | PG 식별자         |
| `idempotency_key`  | String | 발급 멱등성 키    |
| `token_status`     | String | 토큰 상태 (성공 시 `ACTIVE`, 검증/중복 실패 시 null) |
| `card_token`       | String | 카드사 토큰 (성공 시) |
| `card_company`     | String | 카드사            |
| `masked_number`    | String | 마스킹 카드번호   |
| `response_code`    | String | 카드사 응답코드   |
| `response_message` | String | 카드사 응답메시지 |

```json
{
    "pg_id": "001",
    "idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaaa",
    "token_status": "ACTIVE",
    "card_token": "f7cd8f1fa47c461a9fb4f715df543bef",
    "card_company": "삼성카드",
    "masked_number": "8000-****-****-7890",
    "response_code": "100",
    "response_message": "정상 처리되었습니다."
}
```

---

## 2. 카드사 토큰 삭제

- **desc**: PG 서버의 billing-key-service로부터 카드사 토큰 및 멱등성 키를 받아 토큰 비활성화 (소프트 삭제)
- **method**: `POST`
- **path**: `/api/v1/card-simulator/token/delete`

### Headers

| Key             | Value              |
|-----------------|--------------------|
| Content-Type    | `application/json` |
| Idempotency-Key | 삭제 멱등성 키     |

### Request Body

| 필드              | 타입   | 필수 | 설명               |
|-------------------|--------|------|--------------------|
| `pg_id`           | String | Y    | PG 식별자          |
| `card_company`    | String | Y    | 카드사             |
| `card_token`      | String | Y    | 카드사 토큰        |

```json

Idempotency-Key : 001-DEL-20260519100000-bbbbbbbbbbbbbbbbbbbbbbbbb

{
  "pg_id": "001",
  "card_company": "삼성카드",
  "card_token": "5777475f7d34440497f13f5c9ae054a8"
}
```

### Logic

1. `PG Server로부터 토큰 삭제 요청 수신 (Idempotency-Key 헤더 포함)`
2. `idempotency_key 기반 중복 요청 사전 검사`
    - simulator_card_token에서 delete_idempotency_key 일치 row 조회
        - 존재 시: 기존 결과 반환 (echo)
        - 미존재 시: 신규 삭제 절차(3번~) 진행
3. `card_company + card_token(ECB) 일치 토큰 조회` (상태 무관)
    - 미발견 시: (TOKEN, TOKEN_NOT_FOUND) 응답
    - 발견 시 token_status 검증
        - DELETED: (TOKEN, TOKEN_ALREADY_DELETED) 응답
        - ACTIVE: 다음 단계 진행
4. `simulator_card_token row UPDATE`
    - 변경 컬럼: token_status='DELETED', delete_idempotency_key, delete_response_code, delete_response_message
    - 응답 코드/메시지: (TOKEN, SUCCESS)
    - DB UNIQUE 제약 위반 시 (동시 요청 race): 재조회 후 기존 결과 echo
5. `PG Server에 응답 반환`

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `pg_id`            | String | PG 식별자         |
| `idempotency_key`  | String | 삭제 멱등성 키    |
| `card_token`       | String | 카드사 토큰       |
| `response_code`    | String | 카드사 응답코드   |
| `response_message` | String | 카드사 응답메시지 |

```json
{
    "pg_id": "001",
    "idempotency_key": "001-ISS-20260519100000-bbbbbbbbbbbbbbbbbbbbbbbbb",
    "card_token": "5777475f7d34440497f13f5c9ae054a8",
    "response_code": "100",
    "response_message": "정상 처리되었습니다."
}
```

---

## 3. 결제 승인

- **desc**: PG 서버의 pg-payment-service로부터 결제 정보를 받아 결제 승인 처리 및 결제 이력 저장
- **method**: `POST`
- **path**: `/api/v1/card-simulator/payment/approve`

### Headers

| Key             | Value              |
|-----------------|--------------------|
| Content-Type    | `application/json` |
| Idempotency-Key | 결제 멱등성 키     |

### Request Body

| 필드              | 타입   | 필수 | 설명                  |
|-------------------|--------|------|-----------------------|
| `pg_id`           | String | Y    | PG 식별자             |
| `pg_txn_id`       | Long   | Y    | PG 거래 ID (명시형)   |
| `card_company`    | String | Y    | 카드사                |
| `card_token`      | String | Y    | 카드사 토큰           |
| `original_amount` | Long   | Y    | 원금액                |
| `approved_amount` | Long   | Y    | 실결제 금액           |

```json

Idempotency-Key : 001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa2

{
  "pg_id": "001",
  "pg_txn_id": 99012345,
  "card_company": "삼성카드",
  "card_token": "5777475f7d34440497f13f5c9ae054a8",
  "original_amount": 10000,
  "approved_amount": 12000
}
```

### Logic

1. PG 서버로부터 결제 정보 수신 (`Idempotency-Key` 헤더 포함)
2. `simulator_payment_history`에서 `idempotency_key` 중복 검사
   - 존재 시 → 기존 결과 반환 (멱등 처리)
3. `simulator_card_token` 기반 토큰 유효성 검증
   - `card_company`, `card_token`(ECB) 일치 + `token_status = 'ACTIVE'`
4. 카드 상태 확인 (`simulator_card.card_status = 'ACTIVE'`)
5. `simulator_config` 기반 시뮬레이션 적용
   - `reject_pattern` 매칭 시 → `PAYMENT_REJECTED`
   - 통과 시 `approval_rate` 기반 랜덤 → 성공/실패 결정
   - `delay_ms` 만큼 응답 지연
6. 결제 승인번호 생성 (랜덤 hex 8자)
7. `simulator_response_code`에서 응답 코드/메시지 조회 후 `simulator_payment_history` row INSERT (`card_id`, `card_company`, `pg_id`, `pg_txn_id`, `idempotency_key`, `original_amount`, `approved_amount`, `approval_number`, `response_code`, `response_message`)
   - 성공: `payment_status = 'APPROVED'`, `performance_date`는 현재 시각
   - 실패: `payment_status = 'FAILED'`
8. 응답 반환

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `pg_id`            | String | PG 식별자         |
| `idempotency_key`  | String | 결제 멱등성 키    |
| `pg_txn_id`        | Long   | PG 거래 ID        |
| `payment_status`   | String | 결제 상태 (`APPROVED`/`FAILED`) |
| `approval_number`  | String | 승인번호          |
| `approved_at`      | String | 승인일시 (YYYYMMDDHHmmss) |
| `approved_amount`  | Long   | 승인 금액         |
| `response_code`    | String | 카드사 응답코드   |
| `response_message` | String | 카드사 응답메시지 |

```json
{
    "pg_id": "001",
    "idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa2",
    "pg_txn_id": 99012345,
    "payment_status": "APPROVED",
    "approval_number": "3d1a489a",
    "approved_at": "20260527022423",
    "approved_amount": 12000,
    "response_code": "300",
    "response_message": "정상 처리되었습니다."
}
```

---

## 4. 결제 취소

- **desc**: PG 서버의 pg-payment-service로부터 원거래 정보를 받아 결제 취소 처리 및 결제 이력 저장
- **method**: `POST`
- **path**: `/api/v1/card-simulator/payment/cancel`

### Headers

| Key             | Value                          |
|-----------------|--------------------------------|
| Content-Type    | `application/json`             |
| Idempotency-Key | 취소 멱등성 키 (이번 취소건)   |

### Request Body

| 필드                     | 타입   | 필수 | 설명                       |
|--------------------------|--------|------|----------------------------|
| `pg_id`                  | String | Y    | PG 식별자                  |
| `origin_idempotency_key` | String | Y    | 원거래 멱등성 키           |
| `pg_txn_id`              | Long   | Y    | 취소 거래 ID (명시형)      |
| `origin_pg_txn_id`       | Long   | Y    | 원거래 ID (명시형)         |
| `card_company`           | String | Y    | 카드사                     |
| `card_token`             | String | Y    | 카드사 토큰                |
| `approval_number`        | String | Y    | 원거래 승인번호            |

```json

Idempotency-Key : 001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa3

{
  "pg_id": "001",
  "origin_idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa2",
  "pg_txn_id": 99123456,
  "origin_pg_txn_id": 99012345,
  "card_company": "삼성카드",
  "card_token": "5777475f7d34440497f13f5c9ae054a8",
  "approval_number": "3d1a489a"
}
```

### Logic

1. PG 서버로부터 취소 요청 수신 (`Idempotency-Key` 헤더 포함)
2. `simulator_payment_history`에서 `idempotency_key` 중복 검사 (이번 취소건)
3. `origin_idempotency_key`로 원 row 조회 (`payment_status = 'APPROVED'` 필수)
4. `card_company`, `card_token` 일치 검증
5. 취소 승인번호 생성 (랜덤 hex 8자)
6. `simulator_response_code`에서 응답 코드/메시지 조회 후 `simulator_payment_history` 신규 row INSERT (취소 row)
   - 원거래에서 복사: `card_id`, `card_company`, `pg_id`, `original_amount`, `approved_amount`, `performance_date` (실적 차감 시점을 원거래일 기준으로 맞추기 위함)
   - 이번 취소 정보 저장: `pg_txn_id` (취소 거래 ID), `origin_pg_txn_id` (원거래 ID), `idempotency_key` (헤더), `origin_idempotency_key` (원거래 참조), 취소 승인번호, `response_code`, `response_message`
   - `payment_status = 'CANCELED'`
7. 응답 반환

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `pg_id`            | String | PG 식별자         |
| `idempotency_key`  | String | 취소 멱등성 키    |
| `pg_txn_id`        | Long   | 취소 거래 ID      |
| `payment_status`   | String | 결제 상태 (`CANCELED`) |
| `approval_number`  | String | 취소 승인번호     |
| `cancelled_at`     | String | 취소일시          |
| `cancelled_amount` | Long   | 취소 금액         |
| `response_code`    | String | 카드사 응답코드   |
| `response_message` | String | 카드사 응답메시지 |

```json
{
    "pg_id": "001",
    "idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa3",
    "pg_txn_id": 99123456,
    "payment_status": "CANCELED",
    "approval_number": "ea1de037",
    "cancelled_at": "20260527022754",
    "cancelled_amount": 12000,
    "response_code": "300",
    "response_message": "정상 처리되었습니다."
}
```

---

## 5. 가승인

- **desc**: PG 서버의 pg-payment-service로부터 가승인 정보를 받아 한도 선점 처리 및 가승인 이력 저장
- **method**: `POST`
- **path**: `/api/v1/card-simulator/pre-approval/request`

### Headers

| Key             | Value              |
|-----------------|--------------------|
| Content-Type    | `application/json` |
| Idempotency-Key | 가승인 멱등성 키   |

### Request Body

| 필드              | 타입   | 필수 | 설명                  |
|-------------------|--------|------|-----------------------|
| `pg_id`           | String | Y    | PG 식별자             |
| `pg_txn_id`       | Long   | Y    | PG 거래 ID (명시형)   |
| `card_company`    | String | Y    | 카드사                |
| `card_token`      | String | Y    | 카드사 토큰           |
| `original_amount` | Long   | Y    | 원금액                |
| `approved_amount` | Long   | Y    | 실가승인 금액         |

```json

Idempotency-Key : 001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa4

{
  "pg_id": "001",
  "pg_txn_id": 99055111,
  "card_company": "삼성카드",
  "card_token": "5777475f7d34440497f13f5c9ae054a8",
  "original_amount": 30000,
  "approved_amount": 32000
}
```

### Logic

1. PG 서버로부터 가승인 요청 수신 (`Idempotency-Key` 헤더 포함)
2. `simulator_pre_approval`에서 `authorize_idempotency_key` 중복 검사
   - row 존재 + `pre_approval_status='AUTHORIZED'` → 기존 결과 그대로 반환 (멱등 처리)
   - row 존재 + `pre_approval_status IN ('CANCELED','FAILED')` → `TRANSACTION_ALREADY_PROCESSED` 응답 (종결된 키, 새 `Idempotency-Key`로 재요청 필요)
   - row 없음 → 신규 가승인 진행 (3단계로)
3. `simulator_card_token` 토큰 유효성 검증 (`card_company`, `card_token`, `token_status = 'ACTIVE'`)
4. `simulator_card.card_status = 'ACTIVE'` 검증
5. `simulator_config` 기반 시뮬레이션 적용 (승인률, 지연, 거절 패턴)
6. 가승인 승인번호 생성 (랜덤 hex 8자)
7. `simulator_response_code`에서 응답 코드/메시지 조회 후 `simulator_pre_approval` row INSERT (`card_id`, `card_company`, `pg_id`, `pg_txn_id`, `authorize_idempotency_key`, `original_amount`, `approved_amount`, `pre_approval_number`, `response_code`, `response_message`)
   - 성공: `pre_approval_status = 'AUTHORIZED'`
   - 실패: `pre_approval_status = 'FAILED'`
8. 응답 반환

### Response Body

| 필드                  | 타입   | 설명              |
|-----------------------|--------|-------------------|
| `pg_id`               | String | PG 식별자         |
| `idempotency_key`     | String | 가승인 멱등성 키  |
| `pg_txn_id`           | Long   | PG 거래 ID        |
| `pre_approval_status` | String | 가승인 상태 (`AUTHORIZED`/`FAILED`) |
| `pre_approval_id`     | Long   | 가승인 ID         |
| `pre_approval_number` | String | 가승인 승인번호   |
| `pre_approved_at`     | String | 가승인 일시       |
| `approved_amount`     | Long   | 실가승인 금액     |
| `response_code`       | String | 카드사 응답코드   |
| `response_message`    | String | 카드사 응답메시지 |

```json
{
    "pg_id": "001",
    "idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa4",
    "pg_txn_id": 99055111,
    "pre_approval_status": "AUTHORIZED",
    "pre_approval_id": 1,
    "pre_approval_number": "87ff8cba",
    "pre_approved_at": "20260527023054",
    "approved_amount": 32000,
    "response_code": "300",
    "response_message": "정상 처리되었습니다."
}
```

---

## 6. 가승인 취소

- **desc**: PG 서버의 pg-payment-service로부터 가승인 취소 요청을 받아 가승인 상태 갱신 및 한도 복원
- **method**: `POST`
- **path**: `/api/v1/card-simulator/pre-approval/cancel`

### Headers

| Key             | Value                          |
|-----------------|--------------------------------|
| Content-Type    | `application/json`             |
| Idempotency-Key | 취소 멱등성 키 (이번 취소건)   |

### Request Body

| 필드                     | 타입   | 필수 | 설명                       |
|--------------------------|--------|------|----------------------------|
| `pg_id`                  | String | Y    | PG 식별자                  |
| `origin_idempotency_key` | String | Y    | 원 가승인 멱등성 키        |
| `pg_txn_id`              | Long   | Y    | 취소 거래 ID (명시형)      |
| `origin_pg_txn_id`       | Long   | Y    | 원 가승인 거래 ID (명시형) |
| `card_company`           | String | Y    | 카드사                     |
| `card_token`             | String | Y    | 카드사 토큰                |
| `pre_approval_number`    | String | Y    | 원 가승인 승인번호         |

```json

Idempotency-Key : 001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa5

{
  "pg_id": "001",
  "origin_idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa4",
  "pg_txn_id": 99055999,
  "origin_pg_txn_id": 99055111,
  "card_company": "삼성카드",
  "card_token": "5777475f7d34440497f13f5c9ae054a8",
  "pre_approval_number": "87ff8cba"
}
```

### Logic

1. PG 서버로부터 가승인 취소 요청 수신 (`Idempotency-Key` 헤더 포함)
2. `simulator_pre_approval`에서 `cancel_idempotency_key` 중복 검사
3. `origin_idempotency_key`(= `authorize_idempotency_key`)로 원 가승인 조회
   - `pre_approval_status = 'AUTHORIZED'` 확인
4. `origin_pg_txn_id`가 row의 `pg_txn_id`와 일치 검증
5. row UPDATE
   - `pre_approval_status = 'CANCELED'`
   - `cancel_idempotency_key` 저장 (헤더 값)
   - 이번 취소 거래 ID(Body의 `pg_txn_id`)는 DB에 별도 저장하지 않고 응답에만 그대로 반영
6. 응답 반환

### Response Body

| 필드                  | 타입   | 설명              |
|-----------------------|--------|-------------------|
| `pg_id`               | String | PG 식별자         |
| `idempotency_key`     | String | 취소 멱등성 키    |
| `pg_txn_id`           | Long   | 취소 거래 ID      |
| `pre_approval_status` | String | 가승인 상태 (`CANCELED`) |
| `pre_approval_number` | String | 가승인 승인번호   |
| `cancelled_at`        | String | 취소일시          |
| `response_code`       | String | 카드사 응답코드   |
| `response_message`    | String | 카드사 응답메시지 |

```json
{
    "pg_id": "001",
    "idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa5",
    "pg_txn_id": 99055999,
    "pre_approval_status": "CANCELED",
    "pre_approval_number": "87ff8cba",
    "cancelled_at": "20260527023323",
    "response_code": "300",
    "response_message": "정상 처리되었습니다."
}
```

---

## 7. 결제 거래 조회

- **desc**: PG 서버의 pg-payment-service로부터 멱등성 키를 받아 결제 거래 상태 조회
- **method**: `POST`
- **path**: `/api/v1/card-simulator/payment/inquire`

### Headers

| Key          | Value              |
|--------------|--------------------|
| Content-Type | `application/json` |

### Request Body

| 필드                     | 타입   | 필수 | 설명                          |
|--------------------------|--------|------|-------------------------------|
| `pg_id`                  | String | Y    | PG 식별자 (예: "001")         |
| `card_company`           | String | Y    | 카드사 (한글명)               |
| `target_idempotency_key` | String | Y    | 조회 대상 결제 거래 멱등성 키 |

```json
{
  "pg_id": "001",
  "card_company": "삼성카드",
  "target_idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa2"
}
```

### Logic

1. PG 서버로부터 조회 요청 수신
2. `simulator_payment_history`에서 `pg_id`, `card_company`, `idempotency_key = target_idempotency_key` 일치하는 row 조회
   - 카드사는 실제로 서로 독립된 시스템이므로, 본인의 PG/카드사 영역 외 거래는 조회되지 않음 (시뮬레이터에서도 동일 가정)
3. 결과 응답 구성
   - 조회 성공: 결제 정보 반환
   - 조회 실패: `TRANSACTION_NOT_FOUND` 응답

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `pg_id`            | String | PG 식별자         |
| `idempotency_key`  | String | 결제 멱등성 키    |
| `pg_txn_id`        | Long   | PG 거래 ID        |
| `payment_status`   | String | 결제 상태 (`APPROVED`/`CANCELED`/`FAILED`) |
| `approval_number`  | String | 승인번호          |
| `approved_at`      | String | 거래 일시 YYYYMMDDHHmmss (해당 row의 `created_at`. CANCELED row 조회 시 취소 시점) |
| `approved_amount`  | Long   | 승인 금액         |
| `response_code`    | String | 카드사 응답코드   |
| `response_message` | String | 카드사 응답메시지 |

```json
{
    "pg_id": "001",
    "idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa2",
    "pg_txn_id": 99012345,
    "payment_status": "APPROVED",
    "approval_number": "3d1a489a",
    "approved_at": "20260527022424",
    "approved_amount": 12000,
    "response_code": "300",
    "response_message": "정상 처리되었습니다."
}
```

---

## 8. 가승인 거래 조회

- **desc**: PG 서버의 pg-payment-service로부터 멱등성 키를 받아 가승인 거래 상태 조회
- **method**: `POST`
- **path**: `/api/v1/card-simulator/pre-approval/inquire`

### Headers

| Key          | Value              |
|--------------|--------------------|
| Content-Type | `application/json` |

### Request Body

| 필드                     | 타입   | 필수 | 설명                          |
|--------------------------|--------|------|-------------------------------|
| `pg_id`                  | String | Y    | PG 식별자 (예: "001")         |
| `card_company`           | String | Y    | 카드사 (한글명)               |
| `target_idempotency_key` | String | Y    | 조회 대상 가승인 멱등성 키    |

```json
{
  "pg_id": "001",
  "card_company": "삼성카드",
  "target_idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa2"
}
```

### Logic

1. PG 서버로부터 조회 요청 수신
2. `simulator_pre_approval`에서 `pg_id`, `card_company`, `authorize_idempotency_key = target_idempotency_key` 일치하는 row 조회
   - 카드사는 실제로 서로 독립된 시스템이므로, 본인의 PG/카드사 영역 외 거래는 조회되지 않음 (시뮬레이터에서도 동일 가정)
3. 결과 응답 구성
   - 조회 성공: 가승인 정보 반환
   - 조회 실패: `TRANSACTION_NOT_FOUND` 응답

### Response Body

| 필드                  | 타입   | 설명              |
|-----------------------|--------|-------------------|
| `pg_id`               | String | PG 식별자         |
| `idempotency_key`     | String | 가승인 멱등성 키  |
| `pg_txn_id`           | Long   | PG 거래 ID        |
| `pre_approval_status` | String | 가승인 상태 (`AUTHORIZED`/`CANCELED`/`FAILED`) |
| `pre_approval_id`     | Long   | 가승인 ID         |
| `pre_approval_number` | String | 가승인 승인번호   |
| `pre_approved_at`     | String | 가승인 일시 (row의 `created_at`. UPDATE 모델이라 status와 무관하게 가승인 시점) |
| `approved_amount`     | Long   | 실가승인 금액     |
| `response_code`       | String | 카드사 응답코드   |
| `response_message`    | String | 카드사 응답메시지 |

```json
{
    "pg_id": "001",
    "idempotency_key": "001-ISS-20260519100000-aaaaaaaaaaaaaaaaaaaaaaaa2",
    "pg_txn_id": 99055111,
    "pre_approval_status": "AUTHORIZED",
    "pre_approval_id": 2,
    "pre_approval_number": "b963db37",
    "pre_approved_at": "20260527023818",
    "approved_amount": 32000,
    "response_code": "300",
    "response_message": "정상 처리되었습니다."
}
```

---

## 9. 카드사 토큰 조회

- **desc**: PG 서버의 billing-key-service로부터 발급 멱등성 키를 받아 토큰 발급 결과 조회 (발급 타임아웃 시 재확인용)
- **method**: `POST`
- **path**: `/api/v1/card-simulator/token/inquire`

### Headers

| Key          | Value              |
|--------------|--------------------|
| Content-Type | `application/json` |

### Request Body

| 필드                     | 타입   | 필수 | 설명                       |
|--------------------------|--------|------|----------------------------|
| `target_idempotency_key` | String | Y    | 조회 대상 발급 멱등성 키   |

```json
{
  "target_idempotency_key": "001-ISS-20260527020731-1491380aa8644c725b7601357"
}
```

### Logic

1. `PG Server로부터 토큰 조회 요청 수신`
2. `simulator_card_token에서 issue_idempotency_key = target_idempotency_key 일치 row 조회`
    - 미발견 시: (TOKEN, TOKEN_ISSUE_NOT_FOUND) 응답
3. `simulator_card 조회`
    - simulator_card_token.card_id로 simulator_card JOIN → masked_number 획득
4. `PG Server에 응답 반환`
    - 발급 시점의 issue_response_code, issue_response_message 그대로 반환
    - card_token은 ECB 복호화 후 반환

### Response Body

| 필드               | 타입   | 설명                              |
|--------------------|--------|-----------------------------------|
| `pg_id`            | String | PG 식별자                         |
| `idempotency_key`  | String | 발급 멱등성 키                    |
| `token_status`     | String | 토큰 상태 (`ACTIVE`/`DELETED`, 조회 실패 시 null) |
| `card_token`       | String | 카드사 토큰 (성공 시)             |
| `card_company`     | String | 카드사                            |
| `masked_number`    | String | 마스킹 카드번호                   |
| `response_code`    | String | 카드사 응답코드                   |
| `response_message` | String | 카드사 응답메시지                 |

```json
{
    "pg_id": "001",
    "idempotency_key": "001-ISS-20260527020731-1491380aa8644c725b7601357",
    "token_status": "ACTIVE",
    "card_token": "5777475f7d34440497f13f5c9ae054a8",
    "card_company": "삼성카드",
    "masked_number": "8000-****-****-7890",
    "response_code": "100",
    "response_message": "정상 처리되었습니다."
}
```

---

## 10. 카드사 실적 조회

- **desc**: Pay 서버의 card-service로부터 사용자 인증 정보 및 카드 정보를 받아 해당 카드의 누적 결제 금액 조회
- **method**: `POST`
- **path**: `/api/v1/card-simulator/performance/inquire`

### Headers

| Key          | Value              |
|--------------|--------------------|
| Content-Type | `application/json` |

### Request Body

| 필드             | 타입   | 필수 | 설명                       |
|------------------|--------|------|----------------------------|
| `name`           | String | Y    | 사용자 이름                |
| `phone_number`   | String | Y    | 사용자 휴대폰 번호         |
| `card_company`   | String | Y    | 카드사 (한글명)            |
| `product_name`   | String | Y    | 카드 상품명                |
| `inquiry_period` | String | Y    | 산정 기준 시점 (YYYYMM)    |

```json
{
  "name": "가나다",
  "phone_number": "01011111111",
  "card_company": "삼성카드",
  "product_name": "삼성 iD SELECT ALL 카드",
  "inquiry_period": "202605"
}
```

### Logic

1. Pay 서버로부터 실적 조회 요청 수신
2. `simulator_user` 사용자 인증
   - `name` (평문) + `phone_number`(ECB) 일치
3. `simulator_card_product`에서 카드 상품 식별 (`card_company`, `product_name`)
4. `simulator_card`에서 사용자 카드 유효성 확인
   - `user_id`, `product_id` 일치 + `card_status = 'ACTIVE'`
5. `simulator_payment_history` 기반 누적 금액 집계
   - `inquiry_period` 기준 `performance_date` 범위 내 (해당 월)
   - `payment_status = 'APPROVED'` 합계 − `payment_status = 'CANCELED'` 합계
   - 취소 row의 `performance_date`는 원거래 일자로 저장되어 있으므로, 원거래월 실적에서 정확히 차감됨
6. Pay 서버에 결과 반환

### Response Body

| 필드               | 타입   | 설명              |
|--------------------|--------|-------------------|
| `card_company`     | String | 카드사            |
| `product_name`     | String | 카드 상품명       |
| `inquiry_period`   | String | 산정 기준 시점    |
| `current_amount`   | Long   | 누적 결제 금액    |
| `response_code`    | String | 카드사 응답코드   |
| `response_message` | String | 카드사 응답메시지 |

```json
{
    "card_company": "삼성카드",
    "product_name": "삼성 iD SELECT ALL 카드",
    "inquiry_period": "202605",
    "current_amount": 0,
    "response_code": "200",
    "response_message": "정상 처리되었습니다."
}
```