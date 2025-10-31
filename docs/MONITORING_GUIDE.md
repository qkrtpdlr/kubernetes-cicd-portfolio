# 모니터링 가이드 (Prometheus + Grafana)

## 목차
- [아키텍처 개요](#아키텍처-개요)
- [Prometheus 설정](#prometheus-설정)
- [Grafana Dashboard](#grafana-dashboard)
- [Alert Rules](#alert-rules)
- [메트릭 상세](#메트릭-상세)
- [트러블슈팅](#트러블슈팅)

---

## 아키텍처 개요

```
┌─────────────────────────────────────────────────────────┐
│                  사용자 (Grafana UI)                     │
└──────────────────────┬──────────────────────────────────┘
                       │ Query (PromQL)
┌──────────────────────▼──────────────────────────────────┐
│                   Grafana                                │
│  - Jenkins Dashboard                                     │
│  - ArgoCD Dashboard                                      │
│  - Application Dashboard                                 │
└──────────────────────┬──────────────────────────────────┘
                       │ Data Source
┌──────────────────────▼──────────────────────────────────┐
│                  Prometheus                              │
│  - Metrics Storage (TSDB)                                │
│  - Scrape Interval: 15s                                  │
│  - Retention: 15일                                       │
│  - Alert Manager 연동                                    │
└──────────────────────┬──────────────────────────────────┘
                       │ Scrape
        ┌──────────────┼──────────────┐
        │              │              │
┌───────▼──────┐ ┌────▼─────┐ ┌─────▼────┐
│   Jenkins    │ │  ArgoCD  │ │   App    │
│  /prometheus │ │ /metrics │ │/actuator/│
│              │ │          │ │prometheus│
└──────────────┘ └──────────┘ └──────────┘
```

---

## Prometheus 설정

### 1. 설치

```bash
# Namespace 생성
kubectl create namespace monitoring

# Prometheus 배포
kubectl apply -f kubernetes/monitoring/prometheus/

# 상태 확인
kubectl get pods -n monitoring
kubectl get svc -n monitoring
```

### 2. Scrape Config

```yaml
# kubernetes/monitoring/prometheus/configmap.yaml
scrape_configs:
  # Jenkins 메트릭 수집
  - job_name: 'jenkins'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - jenkins
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: jenkins
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true

  # ArgoCD 메트릭 수집
  - job_name: 'argocd'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - argocd

  # Fresh Chicken App 메트릭 수집
  - job_name: 'fresh-chicken-app'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - production
```

### 3. Prometheus UI 접속

```bash
# 도메인 접속
https://prometheus.fresh-chicken.org

# 또는 Port Forward
kubectl port-forward -n monitoring svc/prometheus 9090:9090
# http://localhost:9090
```

### 4. Target 확인

Prometheus UI → Status → Targets

| Job | Namespace | State | Labels |
|-----|-----------|-------|--------|
| jenkins | jenkins | UP | app=jenkins |
| argocd | argocd | UP | app=argocd-server |
| fresh-chicken-app | production | UP | app=fresh-chicken |

---

## Grafana Dashboard

### 1. 설치 및 접속

```bash
# Grafana 배포
kubectl apply -f kubernetes/monitoring/grafana/

# 상태 확인
kubectl get pods -n monitoring -l app=grafana

# 도메인 접속
https://grafana.fresh-chicken.org

# 또는 Port Forward
kubectl port-forward -n monitoring svc/grafana 3000:3000
# http://localhost:3000
```

**로그인 정보**:
- Username: `admin`
- Password: `admin123` (최초 로그인 후 변경 권장)

### 2. Data Source 설정

Grafana UI → Configuration → Data Sources → Add data source

- **Type**: Prometheus
- **URL**: `http://prometheus:9090`
- **Access**: Server (default)

### 3. Dashboard Import

#### Dashboard 1: Jenkins CI/CD

**메트릭**:
- Total Builds: 총 빌드 횟수
- Success Rate: 빌드 성공률
- Average Build Time: 평균 빌드 시간
- Failed Builds (Last 24h): 최근 24시간 실패 빌드 수

**PromQL 쿼리**:
```promql
# 총 빌드 수
sum(jenkins_builds_total)

# 성공률
sum(jenkins_builds_success_total) / sum(jenkins_builds_total) * 100

# 평균 빌드 시간
avg(jenkins_builds_duration_milliseconds_summary / 1000)

# 최근 24시간 실패 빌드
sum(increase(jenkins_builds_failed_total[24h]))
```

---

#### Dashboard 2: ArgoCD GitOps

**메트릭**:
- Total Applications
- Synced Applications
- Healthy Applications
- Sync Failures (Last 24h)

**PromQL 쿼리**:
```promql
# 총 애플리케이션 수
count(argocd_app_info)

# Synced 애플리케이션 수
count(argocd_app_sync_status{sync_status="Synced"})

# Healthy 애플리케이션 수
count(argocd_app_health_status{health_status="Healthy"})

# 최근 24시간 Sync 실패
sum(increase(argocd_app_sync_total{phase="Failed"}[24h]))
```

---

#### Dashboard 3: Fresh Chicken Application

**Panel 1: HTTP Request Rate**
```promql
# 초당 요청 수
sum(rate(http_server_requests_seconds_count{namespace="production"}[5m]))
```

**Panel 2: Response Time (P95)**
```promql
# P95 응답 시간 (밀리초)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{namespace="production"}[5m])) * 1000
```

**Panel 3: Error Rate**
```promql
# HTTP 5xx 에러율 (%)
sum(rate(http_server_requests_seconds_count{namespace="production",status=~"5.."}[5m])) 
/ sum(rate(http_server_requests_seconds_count{namespace="production"}[5m])) 
* 100
```

**Panel 4: JVM Memory Usage**
```promql
# Heap 메모리 사용률 (%)
jvm_memory_used_bytes{namespace="production",area="heap"} 
/ jvm_memory_max_bytes{namespace="production",area="heap"} 
* 100
```

**Panel 5: Database Connection Pool**
```promql
# 활성 커넥션
hikaricp_connections_active{namespace="production"}

# 유휴 커넥션
hikaricp_connections_idle{namespace="production"}
```

**Panel 6: Cache Hit Rate**
```promql
# Redis 캐시 히트율 (%)
sum(rate(cache_gets{namespace="production",result="hit"}[5m])) 
/ sum(rate(cache_gets{namespace="production"}[5m])) 
* 100
```

---

## Alert Rules

### 1. Alert Rules 설정

```yaml
# kubernetes/monitoring/prometheus/alert-rules.yaml
groups:
  - name: application_alerts
    rules:
      # Pod 재시작 알림
      - alert: PodRestarting
        expr: rate(kube_pod_container_status_restarts_total[15m]) > 0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Pod {{ $labels.pod }} is restarting frequently"
          description: "Pod {{ $labels.pod }} has restarted {{ $value }} times"

      # 높은 CPU 사용률
      - alert: HighCPUUsage
        expr: sum(rate(container_cpu_usage_seconds_total{namespace="production"}[5m])) by (pod) > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage on {{ $labels.pod }}"
          description: "CPU usage is above 80%"

      # Health Check 실패
      - alert: HealthCheckFailed
        expr: up{job="fresh-chicken-app"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Health check failed for Fresh Chicken App"
          description: "App has been down for more than 2 minutes"
```

### 2. AlertManager 설정

```yaml
# kubernetes/monitoring/alertmanager/configmap.yaml
global:
  resolve_timeout: 5m
  slack_api_url: 'https://hooks.slack.com/services/YOUR_SLACK_WEBHOOK_URL'

route:
  group_by: ['alertname', 'cluster']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'slack-notifications'

receivers:
- name: 'slack-notifications'
  slack_configs:
  - channel: '#alerts'
    title: '🚨 {{ .GroupLabels.alertname }}'
    text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
```

### 3. Slack 알림 예시

```
🚨 HighCPUUsage

Severity: warning
Instance: fresh-chicken-app-abc123
Description: CPU usage is above 80% (current: 85%)
Started: 2024-08-15 10:30:00
```

---

## 메트릭 상세

### 1. Jenkins 메트릭

| 메트릭 | 설명 | Type |
|--------|------|------|
| `jenkins_builds_total` | 총 빌드 횟수 | Counter |
| `jenkins_builds_success_total` | 성공 빌드 횟수 | Counter |
| `jenkins_builds_failed_total` | 실패 빌드 횟수 | Counter |
| `jenkins_builds_duration_milliseconds_summary` | 빌드 소요 시간 | Summary |
| `jenkins_executor_count` | Executor 수 | Gauge |
| `jenkins_queue_size` | 빌드 큐 크기 | Gauge |

### 2. ArgoCD 메트릭

| 메트릭 | 설명 | Type |
|--------|------|------|
| `argocd_app_info` | 애플리케이션 정보 | Gauge |
| `argocd_app_sync_status` | Sync 상태 | Gauge |
| `argocd_app_health_status` | Health 상태 | Gauge |
| `argocd_app_sync_total` | Sync 총 횟수 | Counter |
| `argocd_app_reconcile_duration_seconds` | Reconcile 소요 시간 | Histogram |

### 3. Spring Boot 메트릭

| 메트릭 | 설명 | Type |
|--------|------|------|
| `http_server_requests_seconds_count` | HTTP 요청 수 | Counter |
| `http_server_requests_seconds_sum` | HTTP 응답 시간 합계 | Counter |
| `jvm_memory_used_bytes` | JVM 메모리 사용량 | Gauge |
| `jvm_gc_pause_seconds_count` | GC 횟수 | Counter |
| `hikaricp_connections_active` | 활성 DB 커넥션 | Gauge |
| `hikaricp_connections_idle` | 유휴 DB 커넥션 | Gauge |
| `cache_gets` | 캐시 조회 횟수 (hit/miss) | Counter |
| `orders_created_total` | 생성된 주문 수 | Counter |
| `orders_cancelled_total` | 취소된 주문 수 | Counter |

### 4. Kubernetes 메트릭

| 메트릭 | 설명 | Type |
|--------|------|------|
| `kube_pod_container_status_restarts_total` | Pod 재시작 횟수 | Counter |
| `container_cpu_usage_seconds_total` | CPU 사용 시간 | Counter |
| `container_memory_working_set_bytes` | 메모리 사용량 | Gauge |
| `kube_pod_status_phase` | Pod 상태 (Running/Pending) | Gauge |

---

## 트러블슈팅

### 1. Prometheus Target Down

**증상**: Prometheus UI에서 Target이 DOWN 상태

**원인**:
- Pod가 실행되지 않음
- Service Endpoint가 없음
- Annotation `prometheus.io/scrape: "true"` 누락

**해결**:
```bash
# Pod 상태 확인
kubectl get pods -n production

# Service Endpoint 확인
kubectl get endpoints -n production

# Annotation 확인
kubectl get pod fresh-chicken-app-xxx -n production -o yaml | grep prometheus
```

### 2. Grafana Dashboard 데이터 없음

**증상**: Grafana Dashboard에 데이터가 표시되지 않음

**원인**:
- Prometheus Data Source 미설정
- PromQL 쿼리 오류
- 메트릭이 수집되지 않음

**해결**:
```bash
# Prometheus에서 메트릭 확인
curl http://prometheus:9090/api/v1/query?query=up

# Grafana에서 Data Source 테스트
Configuration → Data Sources → Prometheus → Test
```

### 3. Alert 알림 미수신

**증상**: Alert 발생했으나 Slack 알림 없음

**원인**:
- Slack Webhook URL 오류
- AlertManager 설정 오류

**해결**:
```bash
# AlertManager 상태 확인
kubectl logs -n monitoring alertmanager-xxx

# Slack Webhook 테스트
curl -X POST https://hooks.slack.com/services/YOUR_WEBHOOK \
  -H "Content-Type: application/json" \
  -d '{"text":"Test message"}'
```

---

## 참고 자료

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [PromQL Cheat Sheet](https://promlabs.com/promql-cheat-sheet/)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
