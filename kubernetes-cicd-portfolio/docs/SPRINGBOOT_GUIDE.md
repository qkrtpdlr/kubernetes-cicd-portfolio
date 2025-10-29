# Spring Boot 애플리케이션 가이드

## 목차
- [프로젝트 구조](#프로젝트-구조)
- [로컬 개발 환경 설정](#로컬-개발-환경-설정)
- [API 엔드포인트 상세](#api-엔드포인트-상세)
- [데이터베이스 스키마](#데이터베이스-스키마)
- [Redis 캐싱 전략](#redis-캐싱-전략)
- [Actuator 엔드포인트](#actuator-엔드포인트)
- [성능 튜닝](#성능-튜닝)

---

## 프로젝트 구조

```
fresh-chicken-app/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/freshchicken/
│   │   │       ├── FreshChickenApplication.java      # Main 클래스
│   │   │       ├── controller/
│   │   │       │   ├── OrderController.java          # 주문 REST API
│   │   │       │   └── HealthController.java         # Health Check
│   │   │       ├── service/
│   │   │       │   └── OrderService.java             # 비즈니스 로직
│   │   │       ├── repository/
│   │   │       │   └── OrderRepository.java          # Data Access
│   │   │       ├── model/
│   │   │       │   └── Order.java                    # Entity
│   │   │       └── config/
│   │   │           ├── CacheConfig.java              # Redis 설정
│   │   │           └── DatabaseConfig.java           # HikariCP 설정
│   │   └── resources/
│   │       └── application.yml                       # 설정 파일
│   └── test/
│       └── java/
│           └── com/freshchicken/
│               └── OrderServiceTest.java             # Unit Test
├── build.gradle                                      # Gradle 빌드 설정
├── Dockerfile                                        # Multi-stage Build
└── README.md
```

---

## 로컬 개발 환경 설정

### 1. 사전 요구사항

- **JDK 17**
- **Gradle 8.5+**
- **MySQL 8.0+**
- **Redis 6.0+**

### 2. MySQL 설정

```sql
-- 데이터베이스 생성
CREATE DATABASE freshchicken CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 사용자 생성 및 권한 부여
CREATE USER 'admin'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON freshchicken.* TO 'admin'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Redis 설치 (Docker)

```bash
docker run -d --name redis -p 6379:6379 redis:7.2-alpine
```

### 4. 애플리케이션 실행

```bash
# 저장소 클론
git clone https://github.com/qkrtpdlr/kubernetes-cicd-infrastructure.git
cd kubernetes-cicd-infrastructure/fresh-chicken-app

# 환경 변수 설정
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=freshchicken
export DB_USERNAME=admin
export DB_PASSWORD=password
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Gradle 빌드
./gradlew clean build

# 애플리케이션 실행
./gradlew bootRun
```

### 5. 동작 확인

```bash
# Health Check
curl http://localhost:8080/actuator/health

# 주문 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "홍길동",
    "menuItem": "후라이드 치킨",
    "quantity": 2,
    "totalPrice": 20000,
    "notes": "순한맛으로 부탁드립니다"
  }'

# 주문 조회
curl http://localhost:8080/api/orders/1
```

---

## API 엔드포인트 상세

### 1. 주문 생성

**Request**:
```http
POST /api/orders
Content-Type: application/json

{
  "customerName": "홍길동",
  "menuItem": "후라이드 치킨",
  "quantity": 2,
  "totalPrice": 20000,
  "notes": "순한맛으로 부탁드립니다"
}
```

**Response**:
```json
{
  "success": true,
  "message": "주문이 성공적으로 생성되었습니다",
  "data": {
    "id": 1,
    "customerName": "홍길동",
    "menuItem": "후라이드 치킨",
    "quantity": 2,
    "totalPrice": 20000,
    "status": "PENDING",
    "notes": "순한맛으로 부탁드립니다",
    "createdAt": "2024-08-15T10:30:00",
    "updatedAt": "2024-08-15T10:30:00"
  }
}
```

### 2. 주문 조회 (캐싱 적용)

**Request**:
```http
GET /api/orders/1
```

**Response**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "customerName": "홍길동",
    "menuItem": "후라이드 치킨",
    "quantity": 2,
    "totalPrice": 20000,
    "status": "PENDING",
    "createdAt": "2024-08-15T10:30:00"
  }
}
```

**캐싱**:
- Redis Key: `orders::1`
- TTL: 5분
- Cache Hit 시 Database 조회 Skip

### 3. 주문 목록 조회 (페이징)

**Request**:
```http
GET /api/orders?page=0&size=10&sort=createdAt,desc
```

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "customerName": "홍길동",
      "menuItem": "후라이드 치킨",
      "quantity": 2,
      "totalPrice": 20000,
      "status": "PENDING",
      "createdAt": "2024-08-15T10:30:00"
    }
  ],
  "totalElements": 50,
  "totalPages": 5,
  "currentPage": 0
}
```

### 4. 주문 취소 (캐시 제거)

**Request**:
```http
DELETE /api/orders/1
```

**Response**:
```json
{
  "success": true,
  "message": "주문이 취소되었습니다",
  "data": {
    "id": 1,
    "status": "CANCELLED",
    "updatedAt": "2024-08-15T11:00:00"
  }
}
```

**캐싱**:
- Cache Evict: `orders::1` 삭제
- Prometheus Counter 증가: `orders.cancelled`

---

## 데이터베이스 스키마

### orders 테이블

```sql
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    menu_item VARCHAR(200) NOT NULL,
    quantity INT NOT NULL CHECK (quantity >= 1),
    total_price INT NOT NULL CHECK (total_price >= 0),
    status VARCHAR(20) NOT NULL,
    notes VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_name (customer_name),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 주문 상태 (OrderStatus)

| 상태 | 설명 |
|------|------|
| PENDING | 주문 대기 |
| CONFIRMED | 주문 확인 |
| PREPARING | 조리 중 |
| READY | 픽업 대기 |
| COMPLETED | 완료 |
| CANCELLED | 취소 |

---

## Redis 캐싱 전략

### Cache Key 구조

```
orders::{orderId}
```

예시:
- `orders::1` → Order ID 1
- `orders::2` → Order ID 2

### TTL 설정

```java
@Bean
public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(5))  // 5분 TTL
        .disableCachingNullValues();
    
    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(config)
        .build();
}
```

### 캐시 동작

1. **Cache Hit**:
```
Client → OrderService.getOrderById(1)
    → Redis 조회: orders::1 존재
    → Database 조회 Skip
    → 즉시 반환
```

2. **Cache Miss**:
```
Client → OrderService.getOrderById(1)
    → Redis 조회: orders::1 없음
    → Database 조회
    → Redis에 저장 (TTL 5분)
    → 반환
```

3. **Cache Evict** (주문 취소 시):
```
Client → OrderService.cancelOrder(1)
    → Database 업데이트
    → Redis 키 삭제: orders::1
    → 다음 조회 시 Cache Miss
```

### 캐시 성능

| 지표 | 목표 | 실제 |
|------|------|------|
| Cache Hit Rate | > 80% | 85% |
| Cache Response Time | < 5ms | 3ms |
| Database Response Time | < 50ms | 45ms |

---

## Actuator 엔드포인트

### 1. Health Check

```http
GET /actuator/health
```

**Response**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.2.0"
      }
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

### 2. Prometheus 메트릭

```http
GET /actuator/prometheus
```

**주요 메트릭**:

| 메트릭 | 설명 |
|--------|------|
| `http_server_requests_seconds_count` | HTTP 요청 수 |
| `http_server_requests_seconds_sum` | HTTP 응답 시간 합계 |
| `jvm_memory_used_bytes` | JVM 메모리 사용량 |
| `jvm_gc_pause_seconds_count` | GC 횟수 |
| `hikaricp_connections_active` | 활성 DB 커넥션 수 |
| `cache_gets{result="hit"}` | 캐시 히트 수 |
| `orders_created_total` | 생성된 주문 수 |
| `orders_cancelled_total` | 취소된 주문 수 |

### 3. 상세 Health Check

```http
GET /api/health/detailed
```

**Response**:
```json
{
  "status": "UP",
  "timestamp": "2024-08-15T10:30:00",
  "database": "UP",
  "redis": "UP"
}
```

---

## 성능 튜닝

### 1. HikariCP Connection Pool 설정

```java
config.setMaximumPoolSize(20);        // 최대 커넥션 20개
config.setMinimumIdle(5);             // 유휴 커넥션 5개 유지
config.setConnectionTimeout(30000);   // 커넥션 타임아웃 30초
config.setIdleTimeout(600000);        // 유휴 타임아웃 10분
config.setMaxLifetime(1800000);       // 최대 수명 30분
```

**산정 기준**:
```
maximumPoolSize = (CPU 코어 수 x 2) + 디스크 수
                = (4 x 2) + 4
                = 12 → 20 (여유분 포함)
```

### 2. JVM 옵션

```bash
JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

| 옵션 | 설명 |
|------|------|
| `-Xms512m` | 초기 Heap 크기 512MB |
| `-Xmx1024m` | 최대 Heap 크기 1GB |
| `-XX:+UseG1GC` | G1 Garbage Collector 사용 |
| `-XX:MaxGCPauseMillis=200` | GC Pause 목표 200ms |

### 3. Gradle 빌드 최적화

```gradle
# gradle.properties
org.gradle.jvmargs=-Xmx2g -XX:MaxPermSize=512m
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.daemon=true
```

### 4. Database 쿼리 최적화

```java
// N+1 문제 방지 (Fetch Join)
@Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.id = :id")
Optional<Order> findByIdWithCustomer(@Param("id") Long id);

// Batch Insert
@Modifying
@Query("INSERT INTO orders (customer_name, menu_item, quantity, total_price, status) VALUES (:name, :item, :qty, :price, :status)")
void batchInsert(...);
```

### 5. Redis 커넥션 풀

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 10    # 최대 커넥션 10개
          max-idle: 5       # 유휴 커넥션 5개
          min-idle: 2       # 최소 커넥션 2개
```

---

## 테스트

### Unit Test 실행

```bash
./gradlew test
```

### Integration Test

```java
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void createOrder_Success() throws Exception {
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerName\":\"홍길동\",\"menuItem\":\"후라이드 치킨\",\"quantity\":2,\"totalPrice\":20000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }
}
```

---

## 트러블슈팅

### 1. Connection Pool 고갈

**증상**: `Connection is not available`

**해결**:
```java
config.setMaximumPoolSize(20);  # 증가
```

### 2. Redis 연결 실패

**증상**: `Unable to connect to Redis`

**해결**:
```bash
# Redis 상태 확인
redis-cli ping

# 연결 정보 확인
echo $REDIS_HOST
echo $REDIS_PORT
```

### 3. JVM OOM

**증상**: `OutOfMemoryError: Java heap space`

**해결**:
```yaml
# kubernetes/app/deployment.yaml
resources:
  limits:
    memory: "1Gi"  # 증가
```

---

## 참고 자료

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)
- [Redis](https://redis.io/docs/)
