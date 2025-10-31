package com.freshchicken.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health Check Controller
 * 
 * Kubernetes Readiness/Liveness Probe용 엔드포인트
 * ALB Health Check 타겟
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthController implements HealthIndicator {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * 기본 Health Check
     * 
     * Kubernetes Readiness Probe: /api/health
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("application", "Fresh Chicken Order Platform");
        response.put("version", "1.0.0");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 상세 Health Check
     * 
     * 데이터베이스 + Redis 연결 상태 확인
     */
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        
        // Database 연결 확인
        boolean dbHealthy = checkDatabaseHealth();
        response.put("database", dbHealthy ? "UP" : "DOWN");
        
        // Redis 연결 확인
        boolean redisHealthy = checkRedisHealth();
        response.put("redis", redisHealthy ? "UP" : "DOWN");
        
        // 전체 상태
        boolean overallHealthy = dbHealthy && redisHealthy;
        response.put("status", overallHealthy ? "UP" : "DOWN");
        
        return overallHealthy 
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(503).body(response);
    }

    /**
     * Spring Boot Actuator Health Indicator
     */
    @Override
    public Health health() {
        boolean dbHealthy = checkDatabaseHealth();
        boolean redisHealthy = checkRedisHealth();
        
        if (dbHealthy && redisHealthy) {
            return Health.up()
                .withDetail("database", "UP")
                .withDetail("redis", "UP")
                .build();
        } else {
            return Health.down()
                .withDetail("database", dbHealthy ? "UP" : "DOWN")
                .withDetail("redis", redisHealthy ? "UP" : "DOWN")
                .build();
        }
    }

    /**
     * 데이터베이스 연결 확인
     */
    private boolean checkDatabaseHealth() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return false;
        }
    }

    /**
     * Redis 연결 확인
     */
    private boolean checkRedisHealth() {
        try {
            redisConnectionFactory.getConnection().ping();
            return true;
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return false;
        }
    }
}
