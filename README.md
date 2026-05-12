# CREEV 주문/결제 API

선착순 한정 상품에 동시에 많은 구매 요청이 들어오는 상황을 가정한 주문/결제 API입니다.

단순히 주문을 저장하거나 재고 수량을 차감하는 기능이 아니라 다음 문제를 어떻게 안전하게 처리할지에 초점을 두었습니다.

1. 동시에 많은 사용자가 같은 상품을 구매할 때 초과 판매를 어떻게 막을 것인가
2. 외부 PG 결제 실패 시 주문, 결제, 재고 상태를 어떻게 일관되게 복구할 것인가
3. 같은 요청이 반복되거나 동시에 들어올 때 주문과 PG 호출이 중복되지 않도록 어떻게 처리할 것인가
4. 클라이언트가 품절, 중복 주문, 멱등키 충돌, 결제 실패를 명확히 구분할 수 있도록 어떻게 응답할 것인가

또한 선택 구현으로 CREEV 서비스의 숏폼 UX에 맞는 피드 커서 페이징 API를 구현했습니다.

---

# 1. 프로젝트 실행 방법

## 1.1 실행 환경

필수 실행 환경은 다음과 같습니다.

| 항목 | 내용 |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot |
| Persistence | Spring Data JPA |
| Database | H2 In-memory Database |
| Build Tool | Gradle |

평가자가 별도의 DB를 설치하지 않아도 실행할 수 있도록 H2 인메모리 DB를 사용했습니다.

---

## 1.2 프로젝트 실행

프로젝트 루트 경로에서 아래 명령어를 실행합니다.

```
./gradlew bootRun
```

또는 IntelliJ에서 `CreevApplication`을 실행합니다.

애플리케이션이 정상 실행되면 기본 포트는 `8080`입니다.

```
http://localhost:8080
```

실행 후 API 테스트는 새 터미널 창에서 진행합니다.

`bootRun`을 실행한 터미널은 서버 실행 상태로 유지되어야 합니다.

---

## 1.3 서버 실행 확인

서버가 정상적으로 떠 있는지 먼저 피드 조회 API로 확인합니다.

```
curl -i "http://localhost:8080/feeds?size=2"
```

정상 실행 시 `HTTP/1.1 200` 응답과 함께 피드 목록이 반환됩니다.

예시 응답:

```
{
  "success": true,
  "code": "SUCCESS",
  "message": "피드 목록 조회에 성공했습니다.",
  "data": {
    "items": [
      {
        "feedId": 5,
        "productId": 1,
        "title": "남성 아우터 숏폼 5",
        "videoUrl": "https://example.com/video/5.mp4",
        "thumbnailUrl": "https://example.com/thumb/5.jpg",
        "createdAt": "2026-05-11T10:04:00"
      },
      {
        "feedId": 4,
        "productId": 1,
        "title": "남성 아우터 숏폼 4",
        "videoUrl": "https://example.com/video/4.mp4",
        "thumbnailUrl": "https://example.com/thumb/4.jpg",
        "createdAt": "2026-05-11T10:03:00"
      }
    ],
    "nextCursor": "...",
    "hasNext": true
  },
  "status": 200
}
```

---

## 1.4 주문 생성 API 테스트

주문 생성 API는 아래 명령어로 테스트할 수 있습니다.

```
curl -i -X POST "http://localhost:8080/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "productId": 1,
    "quantity": 1,
    "idempotencyKey": "order-user1-product1-001"
  }'
```

정상적으로 결제가 성공하면 `201 Created`와 함께 아래와 같은 응답이 반환됩니다.

```
{
  "success": true,
  "code": "SUCCESS",
  "message": "주문 결제가 완료되었습니다.",
  "data": {
    "orderId": 1,
    "productId": 1,
    "quantity": 1,
    "amount": 59000,
    "status": "PAID"
  },
  "status": 201
}
```

주의할 점은 `idempotencyKey`입니다.

같은 `userId`와 같은 `idempotencyKey`로 다시 요청하면 새 주문을 만들지 않고 기존 주문을 반환합니다.

새로운 주문 생성을 테스트하려면 `userId` 또는 `idempotencyKey` 값을 바꿔야 합니다.

예를 들어 아래처럼 다른 사용자와 다른 key로 요청합니다.

```
curl -i -X POST "http://localhost:8080/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "productId": 1,
    "quantity": 1,
    "idempotencyKey": "order-user2-product1-001"
  }'
```

---

## 1.5 같은 멱등키 재요청 테스트

같은 요청을 다시 보내면 PG를 다시 호출하지 않고 기존 주문을 반환합니다.

```
curl -i -X POST "http://localhost:8080/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "productId": 1,
    "quantity": 1,
    "idempotencyKey": "order-user1-product1-001"
  }'
```

기존 주문이 이미 결제 완료 상태라면 아래처럼 `200 OK`와 함께 기존 주문 상태가 반환됩니다.

```
{
  "success": true,
  "code": "SUCCESS",
  "message": "이미 결제가 완료된 주문입니다.",
  "data": {
    "orderId": 1,
    "productId": 1,
    "quantity": 1,
    "amount": 59000,
    "status": "PAID"
  },
  "status": 200
}
```

---

## 1.6 중복 구매 테스트

같은 사용자가 다른 `idempotencyKey`로 같은 상품을 다시 주문하면 중복 구매로 처리됩니다.

첫 번째 요청:

```
curl -i -X POST "http://localhost:8080/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 20,
    "productId": 1,
    "quantity": 1,
    "idempotencyKey": "duplicate-test-key-1"
  }'
```

두 번째 요청:

```
curl -i -X POST "http://localhost:8080/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 20,
    "productId": 1,
    "quantity": 1,
    "idempotencyKey": "duplicate-test-key-2"
  }'
```

응답 예시는 다음과 같습니다.

```
{
  "success": false,
  "code": "DUPLICATE_ORDER",
  "message": "이미 해당 상품에 대한 주문이 존재합니다.",
  "data": null,
  "status": 409
}
```

---

## 1.7 품절 테스트

샘플 재고는 100개입니다.

서로 다른 `userId`로 100개 주문이 모두 성공한 뒤 추가 주문을 보내면 품절 응답이 발생합니다.

수동으로 100번 호출하는 것은 비효율적이므로, 품절 및 초과 판매 방지는 자동화 테스트에서 검증했습니다.

```
./gradlew test --tests "*OrderServiceConcurrencyTest"
```

품절 응답 예시는 다음과 같습니다.

```
{
  "success": false,
  "code": "PRODUCT_OUT_OF_STOCK",
  "message": "해당 상품의 재고가 소진되었습니다.",
  "data": null,
  "status": 409
}
```

---

## 1.8 숏폼 피드 조회 API 테스트

첫 페이지 조회:

```
curl -i "http://localhost:8080/feeds?size=2"
```

정상 응답 예시:

```
{
  "success": true,
  "code": "SUCCESS",
  "message": "피드 목록 조회에 성공했습니다.",
  "data": {
    "items": [
      {
        "feedId": 5,
        "productId": 1,
        "title": "남성 아우터 숏폼 5",
        "videoUrl": "https://example.com/video/5.mp4",
        "thumbnailUrl": "https://example.com/thumb/5.jpg",
        "createdAt": "2026-05-11T10:04:00"
      },
      {
        "feedId": 4,
        "productId": 1,
        "title": "남성 아우터 숏폼 4",
        "videoUrl": "https://example.com/video/4.mp4",
        "thumbnailUrl": "https://example.com/thumb/4.jpg",
        "createdAt": "2026-05-11T10:03:00"
      }
    ],
    "nextCursor": "응답에서 반환된 커서 값",
    "hasNext": true
  },
  "status": 200
}
```

다음 페이지 조회 시에는 첫 응답의 `nextCursor` 값을 복사해서 사용합니다.

```
curl -i "http://localhost:8080/feeds?size=2&cursor=여기에_nextCursor_값_붙여넣기"
```

주의할 점은 `{nextCursor}`라는 문자열을 그대로 보내면 안 된다는 점입니다.

반드시 실제 응답에 있는 `nextCursor` 값을 넣어야 합니다.

잘못된 cursor 요청 예시:

```
curl -i "http://localhost:8080/feeds?size=2&cursor=invalid-cursor"
```

응답 예시:

```
{
  "success": false,
  "code": "INVALID_CURSOR",
  "message": "커서 값이 올바르지 않습니다.",
  "data": null,
  "status": 400
}
```

---

## 1.9 테스트 실행

전체 테스트 실행:

```
./gradlew test
```

빌드와 테스트를 함께 실행:

```
./gradlew clean build
```

동시성 테스트만 실행:

```
./gradlew test --tests "*OrderServiceConcurrencyTest"
```

PG 실패 복구 테스트만 실행:

```
./gradlew test --tests "*OrderServicePaymentFailureTest"
```

멱등성 테스트만 실행:

```
./gradlew test --tests "*OrderServiceIdempotencyTest"
```

피드 커서 페이징 테스트만 실행:

```
./gradlew test --tests "*FeedServiceTest"
```

---

## 1.10 실행 중 자주 발생할 수 있는 문제

### 같은 주문 요청을 다시 보냈는데 새 주문이 생성되지 않는 경우

정상 동작입니다.

같은 `userId + idempotencyKey + requestHash` 요청은 멱등 요청으로 판단하여 기존 주문을 반환합니다.

새 주문을 테스트하려면 `idempotencyKey` 값을 바꾸거나 `userId`를 바꿔야 합니다.

---

### 주문 API가 502 PAYMENT_FAILED를 반환하는 경우

PG 결제 실패 상황입니다.

Mock PG가 실패하도록 구현되어 있거나, 결제 실패 시나리오가 발생한 경우입니다.

이때도 주문과 결제 이력은 실패 상태로 남고, 예약 재고는 복구되어야 합니다.

---

### 피드 다음 페이지 조회가 안 되는 경우

`cursor={nextCursor}`를 그대로 보내면 안 됩니다.

첫 페이지 응답에서 내려온 실제 `nextCursor` 값을 복사해서 아래처럼 넣어야 합니다.

```
curl -i "http://localhost:8080/feeds?size=2&cursor=실제_커서_값"
```

---

# 2. 동시성 문제를 해결하기 위해 선택한 방식

## 2.1 문제 상황

이번 과제는 선착순 100개 한정 상품에 1,000건의 구매 요청이 짧은 시간 안에 동시에 들어오는 상황을 전제로 합니다.

단순히 현재 재고를 조회한 뒤 차감하면 다음 문제가 발생할 수 있습니다.

```
요청 A가 재고 1개를 확인합니다.
요청 B도 거의 동시에 재고 1개를 확인합니다.
요청 A가 재고를 차감합니다.
요청 B도 재고를 차감합니다.

결과적으로 실제 재고보다 더 많이 판매되거나, 재고가 음수가 될 수 있습니다.
```

따라서 재고 확인과 재고 변경은 동시에 여러 요청이 접근하더라도 하나의 요청씩 안전하게 처리되어야 합니다.

---

## 2.2 선택한 방식: ProductStock row 비관적 락

본 프로젝트에서는 상품 재고를 `ProductStock` 엔티티로 분리하고, 해당 row에 비관적 락을 적용했습니다.

재고 변경이 필요한 주문 요청은 `ProductStock` row를 `SELECT FOR UPDATE` 방식으로 조회합니다.

```
select *
from product_stocks
where product_id = ?
for update;
```

이 방식은 같은 `productId`의 재고를 변경하려는 요청들이 `ProductStock` row를 기준으로 순차적으로 처리되도록 만듭니다.

즉, 동시에 많은 요청이 들어와도 한 요청이 재고를 확인하고 예약하는 동안 다른 요청은 같은 재고 row를 동시에 수정할 수 없습니다.

처리 흐름은 다음과 같습니다.

1. `ProductStock` row를 잠급니다.
2. 가용 재고를 확인합니다.
3. 재고가 있으면 `reservedStock`을 증가시킵니다.
4. 주문을 `PENDING_PAYMENT` 상태로 생성합니다.
5. 결제 이력을 `READY` 상태로 생성합니다.
6. DB 트랜잭션을 종료합니다.
7. 외부 PG 호출은 트랜잭션 밖에서 수행합니다.

---

## 2.3 비관적 락을 선택한 이유

이번 상황은 일반적인 상품 주문이 아니라, 같은 상품 재고에 요청이 집중되는 선착순 이벤트입니다.

낙관적 락은 충돌 가능성이 낮은 상황에서는 효율적일 수 있습니다. 하지만 이번 과제처럼 같은 상품 재고에 요청이 집중되는 상황에서는 여러 요청이 같은 version을 읽고, 일부만 update에 성공하며, 나머지는 `OptimisticLockException`을 받게 됩니다.

이 경우 실패한 요청을 다시 시도하는 재시도 로직이 필요합니다. 그러나 재시도하더라도 동일 상품에 계속 경합이 발생할 가능성이 높습니다.

반면 비관적 락은 다음 기준이 명확합니다.

| 상황 | 처리 |
| --- | --- |
| 락 획득 후 재고 있음 | 재고 예약 후 주문 생성 |
| 락 획득 후 재고 없음 | PRODUCT_OUT_OF_STOCK |
| 락 대기 시간 초과 | LOCK_TIMEOUT |

비관적 락의 단점은 락 대기 시간이 생긴다는 점입니다. 이를 줄이기 위해 락을 잡은 트랜잭션 안에서는 재고 예약과 주문 생성 같은 짧은 DB 작업만 수행하고, 외부 PG 호출은 트랜잭션 밖에서 수행했습니다.

---

## 2.4 재고 모델: totalStock / reservedStock / soldStock

재고는 단일 수량으로 관리하지 않고 세 가지 값으로 나누었습니다.

| 필드 | 의미 |
| --- | --- |
| totalStock | 전체 한정 수량 |
| reservedStock | 결제 진행 중으로 임시 점유된 수량 |
| soldStock | 결제 완료된 수량 |

가용 재고는 다음 기준으로 계산합니다.

```
availableStock = totalStock - reservedStock - soldStock
```

이 모델을 선택한 이유는 결제 진행 중인 재고와 실제 판매 완료된 재고를 구분하기 위해서입니다.

주문이 생성될 때 바로 `soldStock`을 증가시키면, PG 결제 실패 시 어떤 재고를 복구해야 하는지 모호해집니다.

따라서 PG 호출 전에는 `reservedStock`만 증가시킵니다.

PG 결제가 성공하면 `reservedStock`을 줄이고 `soldStock`을 증가시킵니다.

PG 결제가 실패하면 `soldStock`은 증가하지 않고 `reservedStock`만 다시 줄여 가용 재고를 복구합니다.

이 구조를 통해 결제 성공과 결제 실패 상황에서 재고 상태를 명확하게 관리할 수 있습니다.

---

## 2.5 중복 주문과 멱등성 처리

비관적 락은 초과 판매를 막지만, 같은 사용자가 결제 버튼을 여러 번 누르는 중복 주문까지 자동으로 막지는 못합니다.

이를 방지하기 위해 `userId + idempotencyKey`에 unique constraint를 두었습니다.

```
unique(user_id, idempotency_key)
```

또한 같은 `idempotencyKey`로 다른 상품이나 다른 수량을 요청하는 경우를 구분하기 위해 `requestHash`를 저장했습니다.

`requestHash`는 `userId`, `productId`, `quantity`를 기반으로 생성합니다.

```
requestHash = SHA-256(userId + ":" + productId + ":" + quantity)
```

처리 기준은 다음과 같습니다.

| 상황 | 처리 |
| --- | --- |
| 같은 userId + 같은 idempotencyKey + 같은 requestHash | 기존 주문 반환 |
| 같은 userId + 같은 idempotencyKey + 다른 requestHash | IDEMPOTENCY_KEY_CONFLICT |
| 기존 주문 없음 | 신규 주문 생성 |

동시에 같은 `idempotencyKey` 요청이 들어오면 unique constraint 위반이 발생할 수 있습니다. 이 경우 `DataIntegrityViolationException`을 잡고 기존 주문을 다시 조회해 반환합니다.

이를 통해 중복 요청이 주문 중복 생성이나 PG 중복 호출로 이어지지 않게 했습니다.

또한 이번 상품은 선착순 이벤트 상품이므로 같은 사용자가 다른 `idempotencyKey`로 같은 상품을 다시 구매하는 것도 막았습니다.

이 중복 구매 체크는 `ProductStock` row lock을 획득한 뒤 수행합니다. 락 밖에서 active order를 확인하면 동시에 들어온 요청들이 모두 “기존 주문 없음”으로 판단할 수 있기 때문입니다.

동시에 같은 `idempotencyKey` 요청이 들어온 경우에는 `ProductStock` lock 이후 한 번 더 기존 멱등 주문을 확인합니다. 이를 통해 동일 요청 재시도는 `DUPLICATE_ORDER`가 아니라 기존 주문 반환으로 처리합니다.

---

# 3. 트랜잭션 롤백은 어떻게 처리했는가

## 3.1 핵심 원칙

외부 PG 호출은 DB 트랜잭션 안에서 수행하지 않았습니다.

PG 서버는 실패하거나 지연될 수 있습니다. 만약 PG 호출을 DB 트랜잭션 안에서 수행하면 PG 응답을 기다리는 동안 DB 커넥션과 재고 row lock이 계속 점유됩니다.

선착순 이벤트처럼 요청이 몰리는 상황에서는 전체 주문 처리가 지연되거나 DB 커넥션 풀이 고갈될 수 있습니다.

따라서 본 프로젝트에서는 트랜잭션을 다음과 같이 분리했습니다.

| 단계 | 트랜잭션 여부 | 역할 |
| --- | --- | --- |
| reserveStockAndCreateOrder | 있음 | 재고 예약, 주문 생성, 결제 READY 생성 |
| pgClient.pay | 없음 | 외부 PG 호출 |
| confirmPayment | 있음 | 결제 성공 확정, 재고 판매 확정 |
| cancelPayment | 있음 | 결제 실패 보상, 예약 재고 복구 |

---

## 3.2 주문 생성 및 재고 예약

`reserveStockAndCreateOrder`는 짧은 DB 트랜잭션으로 실행됩니다.

처리 흐름은 다음과 같습니다.

1. 기존 멱등 주문이 있는지 확인합니다.
2. 상품을 조회합니다.
3. 최대 구매 수량을 검증합니다.
4. `ProductStock` row를 `FOR UPDATE`로 잠급니다.
5. `ProductStock` lock 이후 기존 멱등 주문을 한 번 더 확인합니다.
6. 같은 사용자의 active order가 있는지 확인합니다.
7. 가용 재고가 있으면 `reservedStock`을 증가시킵니다.
8. `Order`를 `PENDING_PAYMENT` 상태로 생성합니다.
9. `Payment`를 `READY` 상태로 생성합니다.
10. 트랜잭션을 종료합니다.

이 단계에서는 PG 호출을 하지 않습니다. 재고 row lock을 오래 점유하지 않기 위해 외부 호출은 트랜잭션 밖으로 분리했습니다.

---

## 3.3 PG 성공 처리

PG 결제가 성공하면 `confirmPayment` 트랜잭션을 실행합니다.

처리 흐름은 다음과 같습니다.

1. `Order` row를 `FOR UPDATE`로 잠급니다.
2. `ProductStock` row를 `FOR UPDATE`로 잠급니다.
3. `Payment`를 조회합니다.
4. `reservedStock`을 감소시킵니다.
5. `soldStock`을 증가시킵니다.
6. `Order` 상태를 `PAID`로 변경합니다.
7. `Payment` 상태를 `SUCCESS`로 변경합니다.

이 단계에서는 이미 PG 결제가 성공했기 때문에 DB 확정 처리에 실패하면 운영 장애로 봐야 합니다.

따라서 `confirmPayment` 실패 시에는 `RECOVERY_REQUIRED` 마킹을 시도하고, 장애 로그를 남기도록 설계했습니다.

---

## 3.4 PG 실패 처리

PG 결제가 실패하면 `cancelPayment` 보상 트랜잭션을 실행합니다.

처리 흐름은 다음과 같습니다.

1. `Order` row를 `FOR UPDATE`로 잠급니다.
2. `ProductStock` row를 `FOR UPDATE`로 잠급니다.
3. `Payment`를 조회합니다.
4. `reservedStock`을 감소시켜 예약 재고를 해제합니다.
5. `Order` 상태를 `PAYMENT_FAILED`로 변경합니다.
6. `Payment` 상태를 `FAILED`로 변경합니다.

여기서 중요한 기준은 다음입니다.

```
PG 실패 = PAYMENT_FAILED가 아닙니다.

PG 실패 + 예약 재고 해제 성공 = PAYMENT_FAILED입니다.
```

즉, `PAYMENT_FAILED`는 단순히 PG 호출이 실패했다는 뜻이 아니라, 예약 재고 복구까지 완료된 상태로 정의했습니다.

---

## 3.5 왜 단순 @Transactional rollback으로 처리하지 않았는가

가장 단순한 방식은 하나의 트랜잭션 안에서 재고 차감, 주문 생성, PG 호출을 모두 수행하고 PG 실패 시 전체를 rollback하는 것입니다.

```
@Transactional
재고 차감
주문 생성
PG 호출
PG 실패 시 rollback
```

하지만 이 방식은 실무적으로 위험하다고 판단했습니다.

PG 호출이 지연되면 DB 트랜잭션이 열린 채로 대기하게 됩니다. 그동안 재고 row lock과 DB 커넥션이 점유됩니다.

선착순 이벤트처럼 요청이 몰리는 상황에서는 전체 주문 처리가 지연되거나 DB 커넥션 풀이 고갈될 수 있습니다.

따라서 DB 트랜잭션은 짧게 유지하고, 외부 PG 호출은 트랜잭션 밖에서 수행했습니다.

이 구조에서는 PG 실패 시 단순 rollback이 아니라 `cancelPayment`라는 명시적인 보상 트랜잭션으로 재고와 주문 상태를 복구해야 합니다.

---

## 3.6 보상 트랜잭션 실패 처리

PG 실패 후 예약 재고를 해제하는 `cancelPayment` 자체도 실패할 수 있습니다.

이 경우는 단순 결제 실패가 아니라 운영 장애로 봐야 합니다. 주문은 결제 실패로 처리되어야 하는데, 예약 재고가 복구되지 않았을 가능성이 있기 때문입니다.

본 프로젝트에서는 보상 트랜잭션 실패 시 `RECOVERY_REQUIRED` 상태로 마킹을 시도하고, 실패 로그를 남기도록 설계했습니다.

다만 DB 장애 상황에서는 `RECOVERY_REQUIRED` 마킹 자체도 실패할 수 있습니다. 실제 운영 환경에서는 이런 경우를 위해 별도의 복구 대상 테이블, 재처리 배치, 장애 알림, 운영자 수동 복구 도구가 필요합니다.

---

# 4. 클라이언트를 배려한 에러 응답

프론트엔드가 상황을 명확히 구분할 수 있도록 성공과 실패 응답을 공통 포맷으로 통일했습니다.

실패 응답 예시는 다음과 같습니다.

```
{
  "success":false,
  "code":"PRODUCT_OUT_OF_STOCK",
  "message":"해당 상품의 재고가 소진되었습니다.",
  "data":null,
  "status":409
}
```

주요 에러 코드는 다음과 같습니다.

| 상황 | HTTP Status | ErrorCode | 이유 |
| --- | --- | --- | --- |
| 요청 값 오류 | 400 | INVALID_REQUEST | 필수 값 누락 또는 잘못된 요청 |
| 주문 수량 오류 | 400 | INVALID_ORDER_QUANTITY | quantity가 1보다 작음 |
| 최대 구매 수량 초과 | 400 | EXCEEDS_MAX_PURCHASE_QUANTITY | 이벤트 상품의 1인 구매 제한 초과 |
| 상품 없음 | 404 | PRODUCT_NOT_FOUND | 구매 대상 상품이 존재하지 않음 |
| 재고 없음 | 404 | STOCK_NOT_FOUND | 상품 재고 정보가 존재하지 않음 |
| 품절 | 409 | PRODUCT_OUT_OF_STOCK | 상품은 존재하지만 구매 가능한 재고가 없음 |
| 중복 주문 | 409 | DUPLICATE_ORDER | 같은 유저가 같은 이벤트 상품을 이미 주문 |
| 멱등키 충돌 | 409 | IDEMPOTENCY_KEY_CONFLICT | 같은 key로 다른 요청을 보냄 |
| 결제 정보 없음 | 404 | PAYMENT_NOT_FOUND | 주문에 연결된 결제 정보가 없음 |
| 결제 실패 | 502 | PAYMENT_FAILED | 외부 PG 결제 실패 |
| 락 대기 초과 | 503 | LOCK_TIMEOUT | 순간 트래픽으로 락 대기 시간이 초과됨 |
| 잘못된 커서 | 400 | INVALID_CURSOR | 피드 커서 값이 올바르지 않음 |

품절을 404가 아니라 409로 처리한 이유는 상품 자체는 존재하지만, 현재 재고 상태가 구매 요청과 충돌하기 때문입니다.

결제 실패를 500이 아니라 502로 처리한 이유는 내부 애플리케이션 로직 오류가 아니라 외부 PG 연동 실패에 가까운 상황이기 때문입니다.

---

# 5. 선택 구현: 숏폼 피드 커서 페이징

크리브 서비스의 숏폼 피드는 사용자가 아래로 계속 스와이프하며 소비하는 구조입니다.

이런 환경에서는 page 번호 기반 offset pagination보다 cursor pagination이 적합하다고 판단했습니다.

Offset 방식은 뒤 페이지로 갈수록 앞 데이터를 스캔하고 버리는 비용이 커집니다. 또한 조회 중 새 피드가 추가되면 중복 또는 누락이 발생할 수 있습니다.

따라서 `GET /feeds` API는 다음 정렬 기준을 사용합니다.

```
createdAt desc, id desc
```

커서는 `createdAt`과 `id`를 함께 사용합니다.

`createdAt`만 사용할 경우 같은 시간에 생성된 피드가 여러 개 있을 때 페이지 경계에서 중복 또는 누락이 발생할 수 있기 때문입니다.

다음 페이지 조건은 다음 기준으로 구성했습니다.

```
createdAt < cursorCreatedAt
```

또는

```
createdAt = cursorCreatedAt and id < cursorId
```

다음 페이지 존재 여부는 `size + 1`개를 조회해 판단합니다.

```
요청 size = 20
DB 조회 = 21
응답 = 앞의 20개
21번째 데이터가 있으면 hasNext = true
```

잘못된 cursor가 전달되면 조용히 첫 페이지를 반환하지 않고 `INVALID_CURSOR`로 실패시켰습니다. 잘못된 cursor를 첫 페이지로 처리하면 클라이언트가 중복 피드를 받을 수 있기 때문입니다.

---

# 6. 테스트

본 프로젝트에서는 과제 핵심 요구사항을 테스트로 검증했습니다.

## 6.1 동시성 테스트

`totalStock = 100`인 상품에 대해 1,000개의 주문 요청을 동시에 실행했습니다.

각 요청은 서로 다른 `userId`와 `idempotencyKey`를 사용했고, PG는 항상 성공하도록 테스트 대역을 사용했습니다.

검증 내용은 다음과 같습니다.

- 결제 성공 주문은 정확히 100건
- 나머지 요청은 `PRODUCT_OUT_OF_STOCK`
- `soldStock = 100`
- `reservedStock = 0`
- `availableStock = 0`
- PG 호출 수 = 100

이를 통해 `ProductStock` row 비관적 락이 초과 판매를 방지하고, 결제 확정 이후 예약 재고가 판매 재고로 정상 전환됨을 검증했습니다.

---

## 6.2 PG 실패 복구 테스트

PG가 항상 실패하도록 테스트 대역을 설정한 뒤 주문 요청을 실행했습니다.

검증 내용은 다음과 같습니다.

- `Order` 상태 = `PAYMENT_FAILED`
- `Payment` 상태 = `FAILED`
- `reservedStock = 0`
- `soldStock = 0`
- `availableStock = 100`
- 같은 `idempotencyKey`로 재요청해도 PG를 다시 호출하지 않음

이를 통해 PG 실패 시 예약 재고가 복구되고, 주문·결제 상태가 실패 상태로 일관되게 정리되는지 확인했습니다.

---

## 6.3 멱등성 테스트

같은 `userId + idempotencyKey + requestHash` 요청을 여러 번 보내도 주문과 결제가 중복 생성되지 않는지 검증했습니다.

검증 내용은 다음과 같습니다.

- 주문은 1개만 생성
- 결제는 1개만 생성
- PG는 1번만 호출
- 같은 key로 다른 유효 요청을 보내면 `IDEMPOTENCY_KEY_CONFLICT`
- 같은 key의 동일 요청이 동시에 들어와도 주문과 PG 호출은 1번만 발생

이를 통해 멱등성 처리가 단순 재요청뿐 아니라 동시 요청 상황에서도 유지되는지 검증했습니다.

---

## 6.4 중복 구매 테스트

같은 사용자가 서로 다른 `idempotencyKey`로 같은 상품을 동시에 주문하는 경우를 검증했습니다.

검증 내용은 다음과 같습니다.

- 하나의 주문만 성공
- 나머지 요청은 `DUPLICATE_ORDER`
- 주문은 1개만 생성
- 결제는 1개만 생성
- PG는 1번만 호출
- `reservedStock = 0`
- `soldStock = 1`

이를 통해 멱등 요청과 중복 구매를 구분했습니다.

```
같은 idempotencyKey의 동일 요청
→ 기존 주문 반환 대상

서로 다른 idempotencyKey로 같은 상품을 다시 구매하는 요청
→ 중복 구매 차단 대상
```

---

## 6.5 피드 커서 페이징 테스트

피드 커서 페이징은 다음을 검증했습니다.

- 첫 페이지가 `createdAt desc, id desc` 순서로 조회되는지
- `nextCursor`로 다음 페이지를 조회하면 중복 없이 이어지는지
- 같은 `createdAt`을 가진 피드가 `id desc` 순서로 조회되는지
- 잘못된 cursor가 `INVALID_CURSOR`로 실패하는지
- size가 1보다 작으면 `INVALID_REQUEST`로 실패하는지

---

# 7. 주요 클래스 및 PR

## 7.1 주요 클래스

| 클래스 | 위치 | 역할 |
| --- | --- | --- |
| OrderController | order.api | 주문 생성 API 진입점입니다. 요청을 Command로 변환하고 주문 처리 결과를 공통 응답 형식으로 반환합니다. |
| OrderService | order.core.application | 주문 생성 전체 흐름을 조율합니다. 멱등 요청이면 기존 주문을 반환하고, 신규 주문이면 PG 결제 후 확정 또는 실패 처리를 수행합니다. |
| OrderTransactionService | order.core.application | 짧은 DB 트랜잭션 단위의 핵심 처리를 담당합니다. 재고 예약, 결제 확정, 결제 실패 보상 처리를 수행합니다. |
| Order | order.core.domain | 주문 도메인 엔티티입니다. PENDING_PAYMENT, PAID, PAYMENT_FAILED, RECOVERY_REQUIRED 상태 전이 규칙을 가집니다. |
| Payment | order.core.domain | 결제 이력 엔티티입니다. READY, SUCCESS, FAILED 상태를 관리하고 PG 실패 메시지를 저장합니다. |
| ProductStock | product.domain | 재고 도메인 엔티티입니다. totalStock, reservedStock, soldStock을 기준으로 가용 재고를 계산하고 예약, 확정, 해제 처리를 수행합니다. |
| ProductStockRepository | product.infrastructure | ProductStock row를 비관적 락으로 조회합니다. 선착순 주문에서 초과 판매를 방지하는 핵심 DB 접근 클래스입니다. |
| PgClient | payment.infrastructure | 외부 PG 연동을 추상화한 인터페이스입니다. 주문 서비스가 실제 PG 구현체에 직접 의존하지 않도록 분리했습니다. |
| MockPgClient | payment.infrastructure | 과제 실행용 Mock PG 구현체입니다. 결제 성공/실패 상황을 재현합니다. |
| RequestHashGenerator | order.core.application.support | userId, productId, quantity를 기반으로 requestHash를 생성합니다. 같은 idempotencyKey로 다른 요청이 들어오는지 구분하는 데 사용합니다. |
| FeedService | feed.application | 숏폼 피드 목록을 cursor 기반으로 조회합니다. createdAt과 id를 사용해 no-offset 페이징을 처리합니다. |
| FeedCursorCodec | feed.application.support | 피드 커서 값을 Base64 URL-safe 형식으로 인코딩하고 디코딩합니다. 잘못된 커서는 INVALID_CURSOR로 처리합니다. |
| FeedQueryRepositoryImpl | feed.infrastructure | QueryDSL로 createdAt desc, id desc 기준의 커서 페이징 쿼리를 수행합니다. |

---

## 7.2 주요 PR 흐름

이번 과제는 기능을 한 번에 구현하기보다, 주문/결제/재고 정합성을 검증 가능한 단위로 나누어 구현했습니다.

| PR | 내용 | 의도 |
| --- | --- | --- |
| [#1 주문 결제 도메인 엔티티 추가](https://github.com/YangDaJin0104/payments_assignment/pull/1) | Order, Payment, Product, ProductStock 도메인 구성 | 주문, 결제, 재고 상태를 분리해 상태 전이와 재고 복구 기준을 명확히 하기 위함 |
| [#6 주문 생성 API 컨트롤러 및 Mock PG 클라이언트 추가](https://github.com/YangDaJin0104/payments_assignment/pull/6) | 주문 생성 API와 PgClient 연동 경계 구현 | 외부 PG 호출을 애플리케이션 내부 로직과 분리하고, 결제 성공/실패 흐름을 명확히 처리하기 위함 |
| [#9 주문 결제 서비스 통합 테스트](https://github.com/YangDaJin0104/payments_assignment/pull/9) | 결제 성공, PG 실패, 기본 멱등 요청 검증 | 주문·결제·재고 상태가 함께 일관되게 변경되는지 확인하기 위함 |
| [#11 주문 동시성 테스트](https://github.com/YangDaJin0104/payments_assignment/pull/11) | 1,000건 동시 주문 상황 검증 | ProductStock 비관적 락으로 초과 판매가 발생하지 않는지 검증하기 위함 |
| [#13 주문 멱등성 테스트](https://github.com/YangDaJin0104/payments_assignment/pull/13) | 동일 key 재요청, key 충돌, 동시 멱등 요청 검증 | 주문 중복 생성과 PG 중복 호출 방지 정책을 검증하기 위함 |
| [#17 QueryDSL 설정 추가](https://github.com/YangDaJin0104/payments_assignment/pull/17) | QueryDSL 설정 및 피드 조회 기반 구성 | 선택 구현인 GET /feeds에서 무한 스크롤에 적합한 no-offset 조회를 구현하기 위함 |

위 PR 흐름은 단순 기능 추가 순서가 아니라, 재고 정합성 → 외부 PG 실패 처리 → 동시성 검증 → 멱등성 검증 → 선택 구현 순서로 과제의 핵심 문제를 나누어 해결한 과정입니다.

---

# 8. 구현하지 못한 부분 및 Production 개선점

## 8.1 실제 PG timeout 처리

과제에서 사용한 `MockPgClient`는 결제 실패와 타임아웃을 단순화해 표현합니다. 따라서 본 프로젝트에서는 해당 예외를 결제 실패로 보고 예약 재고를 해제했습니다.

하지만 실제 PG에서는 실패와 타임아웃을 분리해야 합니다.

타임아웃은 실제 결제가 성공했지만 우리 서버가 응답을 받지 못한 상황일 수 있습니다.

이 경우 즉시 재고를 해제하면 다음 문제가 생길 수 있습니다.

```
고객 카드 결제는 성공했습니다.
서버는 timeout으로 판단했습니다.
주문은 PAYMENT_FAILED가 됩니다.
재고는 다시 판매 가능 상태가 됩니다.
```

이렇게 되면 결제와 주문 상태가 불일치합니다.

운영 환경에서는 timeout 발생 시 PG 거래 상태를 재조회한 뒤 주문을 `PAID` 또는 `PAYMENT_FAILED`로 확정해야 합니다.

---

## 8.2 보상 트랜잭션 실패 복구

PG 실패 후 `cancelPayment` 보상 트랜잭션이 실패할 수 있습니다.

본 프로젝트에서는 `RECOVERY_REQUIRED` 마킹과 로그를 남기는 수준으로 처리했습니다.

실제 운영 환경에서는 다음 보완이 필요합니다.

- RecoveryTask 테이블
- 재처리 배치
- Slack, Sentry 등 장애 알림
- 운영자 수동 복구 도구

---

## 8.3 예약 재고 만료 처리

현재 구현에서는 주문 생성 후 PG 호출 결과에 따라 결제 성공 시 `confirmPayment`, 결제 실패 시 `cancelPayment`를 실행해 `reservedStock`을 정리합니다.

다만 서버가 PG 호출 직후 종료되거나, 결제 확정 또는 취소 트랜잭션을 실행하기 전에 장애가 발생하면 `PENDING_PAYMENT` 주문과 `reservedStock`이 남을 수 있습니다.

실제 운영 환경에서는 이를 방지하기 위해 `reservationExpiresAt`을 두고, 만료된 `PENDING_PAYMENT` 주문을 주기적으로 조회해 `reservedStock`을 복구하는 스케줄러가 필요합니다.

운영 적용 시에는 다음과 같은 방식으로 개선할 수 있습니다.

- 주문 생성 시 `reservationExpiresAt` 저장
- 만료된 `PENDING_PAYMENT` 주문을 batch size 단위로 조회
- `Order` row와 `ProductStock` row를 짧은 트랜잭션에서 잠금
- `reservedStock` 복구
- `Order` 상태를 `EXPIRED`로 변경
- `Payment` 상태를 `FAILED`로 변경
- 다중 서버 환경에서는 ShedLock 또는 DB 기반 분산 락 적용

---

## 8.4 운영 DB 기준 검증

과제 실행 편의성을 위해 H2 인메모리 DB를 사용했습니다.

다만 실제 운영 환경에서는 MySQL 또는 PostgreSQL 같은 RDBMS에서 아래 항목을 다시 검증해야 합니다.

- `SELECT FOR UPDATE` 동작
- lock wait timeout
- 인덱스 사용 여부
- 피드 커서 쿼리 실행 계획

특히 피드 커서 조회는 `createdAt desc, id desc` 정렬과 커서 조건을 사용하므로 운영 DB 기준으로 실행 계획을 확인해야 합니다.

---

## 8.5 피드 개인화와 조회 정책 고도화

현재 `GET /feeds` API는 전체 피드를 `createdAt desc, id desc` 기준으로 조회합니다.

실제 숏폼 커머스 서비스에서는 사용자의 시청 이력, 구매 이력, 관심 카테고리, 품절 여부, 노출 제한 정책 등을 반영해야 합니다.

운영 환경에서는 다음과 같은 확장이 필요합니다.

- 사용자별 추천 피드
- 품절 상품 피드 제외 또는 표시 정책
- 피드 노출 로그 수집
- 중복 노출 방지
- 검색/추천 인덱스 도입

이번 과제에서는 가산점 요소의 범위를 넘지 않도록, 숏폼 UX에 적합한 cursor pagination 구조를 중심으로 구현했습니다.

---

## 8.6 API 인증과 사용자 식별

현재 과제에서는 `userId`를 요청 body로 전달받습니다.

실제 운영 환경에서는 클라이언트가 `userId`를 임의로 전달하게 두면 안 됩니다.

운영 환경에서는 JWT, Session, OAuth 등 인증 체계를 통해 서버가 사용자를 식별하고, 주문 요청에는 인증된 사용자 정보만 사용해야 합니다.

---

## 8.7 멱등키 운영 정책

현재는 `userId + idempotencyKey`를 unique constraint로 관리합니다.

운영 환경에서는 멱등키를 무기한 보관할지, 일정 기간 이후 정리할지 정책이 필요합니다.

예를 들어 주문 이력이 장기간 누적되면 멱등키 unique index도 계속 커집니다. 따라서 운영 환경에서는 다음 기준을 정해야 합니다.

- 멱등키 보관 기간
- 만료된 멱등키 정리 배치
- 주문 상태별 멱등키 재사용 가능 여부
- 클라이언트 idempotencyKey 생성 규칙

---

# 9. 구현 과정에서의 주요 의사결정

이 과제에서는 기능을 많이 추가하는 것보다 문제가 발생할 수 있는 지점을 명확히 나누는 데 집중했습니다.

가장 먼저 재고 초과 판매를 막기 위해 `ProductStock` row 비관적 락을 선택했습니다. 이후 외부 PG 호출을 DB 트랜잭션 안에 넣지 않기 위해 재고 예약, PG 호출, 결제 확정, 결제 취소를 서로 다른 단계로 분리했습니다.

이 구조에서는 단순 rollback만으로 모든 상황을 처리할 수 없기 때문에 `cancelPayment` 보상 트랜잭션을 별도로 두었습니다. 또한 같은 요청이 반복되거나 동시에 들어오는 상황을 고려해 `idempotencyKey`와 `requestHash`를 함께 사용했습니다. 같은 key의 같은 요청은 기존 주문을 반환하고, 같은 key의 다른 요청은 충돌로 처리했습니다.

선택 구현인 피드 조회는 CREEV가 숏폼 기반 서비스라는 점을 고려해 offset pagination이 아니라 cursor pagination으로 구현했습니다. `createdAt`과 `id`를 함께 사용하는 복합 커서를 적용해 페이지 경계에서 중복과 누락을 줄였습니다.
