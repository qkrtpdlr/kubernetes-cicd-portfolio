# Multi-stage Dockerfile for Fresh Chicken Order Platform
# 
# Stage 1: Build (Gradle)
# Stage 2: Runtime (JRE only)
# 
# Image Size Optimization:
# - Build stage: ~800MB
# - Final image: ~250MB (70% 감소)

# ============================================
# Stage 1: Build
# ============================================
FROM gradle:8.5-jdk17-alpine AS builder

WORKDIR /app

# Gradle Wrapper 및 의존성 파일 복사 (캐싱 최적화)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

# 의존성 다운로드 (레이어 캐싱)
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사
COPY src ./src

# 애플리케이션 빌드 (테스트 제외)
RUN ./gradlew bootJar --no-daemon -x test

# ============================================
# Stage 2: Runtime
# ============================================
FROM eclipse-temurin:17-jre-alpine

# 비root 사용자 생성 (보안 강화)
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 사용자 변경
RUN chown spring:spring app.jar
USER spring:spring

# 애플리케이션 포트
EXPOSE 8080

# Health Check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM 옵션 설정
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
