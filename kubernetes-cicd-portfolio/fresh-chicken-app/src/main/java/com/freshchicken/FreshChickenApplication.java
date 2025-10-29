package com.freshchicken;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Fresh Chicken 주문 플랫폼 - Spring Boot Application
 * 
 * 기능:
 * - 주문 생성/조회/취소 RESTful API
 * - MySQL 데이터 저장 (Spring Data JPA)
 * - Redis 캐싱 (Spring Cache)
 * - Prometheus 메트릭 수집 (Actuator)
 * - Health Check 엔드포인트
 * 
 * @author DevOps Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
public class FreshChickenApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreshChickenApplication.class, args);
    }
}
