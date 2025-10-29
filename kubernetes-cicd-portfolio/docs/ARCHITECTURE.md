# 시스템 아키텍처

## 목차
- [전체 인프라 아키텍처](#전체-인프라-아키텍처)
- [CI/CD 파이프라인 플로우](#cicd-파이프라인-플로우)
- [Spring Boot 애플리케이션 아키텍처](#spring-boot-애플리케이션-아키텍처)
- [네트워크 구성](#네트워크-구성)

---

## 전체 인프라 아키텍처

```
                        Internet Users
                              │
                              │
                        ┌─────▼──────┐
                        │  Route 53  │       
                        │fresh-chicken│
                        │    .org     │
                        └─────┬──────┘
                              │
                              │
                        ┌─────▼──────┐
                        │ CloudFront │       
                        │ (CDN + S3) │
                        └─────┬──────┘
                              │
                              │
                         ┌────▼─────┐
                         │ AWS WAF  │       
                         │ (보안)   │
                         └────┬─────┘
                              │
        ┌─────────────────────────────────────────────┐
        │     Application Load Balancer               │
        │  - jenkins.fresh-chicken.org → 8080         │
        │  - argocd.fresh-chicken.org → 80            │
        │  - www.fresh-chicken.org → 8080             │
        │  - prometheus.fresh-chicken.org → 9090      │
        │  - grafana.fresh-chicken.org → 3000         │
        └─────────────────┬───────────────────────────┘
                          │
        ┌─────────────────────────────────────────────┐
        │      AWS EKS Cluster (v1.30)                │
        │                                             │
        │  Control Plane (Managed by AWS)             │
        │                                             │
        │    ┌────────────────────────────────────┐  │
        │    │ Worker Nodes (t3.medium x3)        │  │
        │    │                                    │  │
        │    │ [jenkins]  [argocd]  [production] │  │
        │    │ - Jenkins  - ArgoCD  - App (3 Pods)│  │
        │    │ - Kaniko   - Redis                 │  │
        │    │                                    │  │
        │    │ [monitoring]                       │  │
        │    │ - Prometheus  - Grafana            │  │
        │    └────────────────────────────────────┘  │
        └─────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │RDS MySQL │    │ElastiCache│   │ Amazon  │
    │(Multi-AZ)│    │  Redis    │   │   ECR   │
    └──────────┘    └──────────┘    └──────────┘
```

### 주요 컴포넌트

#### 1. AWS EKS Cluster
- **Control Plane**: AWS 관리형
- **Worker Nodes**: t3.medium x 3대 (Multi-AZ)
- **Kubernetes 버전**: 1.30

#### 2. Namespace 구성
| Namespace | 용도 | 주요 리소스 |
|-----------|------|------------|
| **jenkins** | CI | Jenkins, Kaniko Pod |
| **argocd** | CD | ArgoCD Server, Redis |
| **production** | App | Spring Boot App (3 Pods) |
| **monitoring** | 모니터링 | Prometheus, Grafana |

#### 3. 외부 서비스
- **RDS MySQL**: Multi-AZ (ap-northeast-2a, 2c)
- **ElastiCache Redis**: 클러스터 모드
- **Amazon ECR**: Docker 이미지 저장소

---

## CI/CD 파이프라인 플로우

```
Developer
    │
  1. git push
    │
    ▼
┌─────────────────────────────────────┐
│      GitHub Repository              │
│  - Source Code                      │
│  - Webhook Trigger                  │
└────────────┬────────────────────────┘
            │ 2. POST /github-webhook/
            ▼
┌─────────────────────────────────────┐
│      Jenkins CI Pipeline            │
│                                     │
│  Stage 1: Checkout Code             │
│     ├─ Git clone                    │
│                                     │
│  Stage 2: Build & Test (Gradle)     │
│     ├─ gradle build                 │
│     ├─ gradle test                  │
│                                     │
│  Stage 3: Build & Push (Kaniko)     │
│     ├─ Create Kaniko Pod in EKS     │
│     ├─ Build Docker image           │
│     ├─ Push to Amazon ECR           │
│     └─ Delete Kaniko Pod            │
│                                     │
│  Stage 4: Update Manifest           │
│     ├─ Git clone manifest repo      │
│     ├─ Update image tag             │
│     └─ Git commit & push            │
│                                     │
│  Stage 5: Trigger ArgoCD Sync       │
│     ├─ Call ArgoCD Sync API         │
│     └─ Monitor deployment           │
└────────────┬────────────────────────┘
            │ 3. Sync Request
            ▼
┌─────────────────────────────────────┐
│      ArgoCD (GitOps Engine)         │
│                                     │
│  - Fetch manifests from Git         │
│  - Compare desired vs current       │
│  - Apply changes to EKS             │
│  - Health check                     │
└────────────┬────────────────────────┘
            │ 4. Rolling Update
            ▼
┌─────────────────────────────────────┐
│   EKS Production Namespace          │
│                                     │
│   Pod v2    Pod v2    Pod v2        │
│   (new)     (new)     (new)         │
└────────────┬────────────────────────┘
            │ 5. Traffic Routing
            ▼
┌─────────────────────────────────────┐
│        ALB Ingress                  │
│  - Health Check: /actuator/health   │
│  - Route to Healthy Pods Only       │
└────────────┬────────────────────────┘
            │
            ▼
      End Users
   (Zero Downtime!)
```

### 파이프라인 소요 시간

| Stage | 평균 시간 |
|-------|----------|
| Stage 1: Checkout | 10초 |
| Stage 2: Build & Test | 90초 |
| Stage 3: Build & Push | 120초 |
| Stage 4: Update Manifest | 15초 |
| Stage 5: Trigger ArgoCD | 60초 |
| **총 소요 시간** | **5분** |

---

## Spring Boot 애플리케이션 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                    Fresh Chicken App                     │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │           Controller Layer (REST API)              │ │
│  │  - OrderController: 주문 CRUD API                  │ │
│  │  - HealthController: Health Check                  │ │
│  └────────────────────┬───────────────────────────────┘ │
│                       │                                  │
│  ┌────────────────────▼───────────────────────────────┐ │
│  │           Service Layer (Business Logic)           │ │
│  │  - OrderService: 주문 비즈니스 로직               │ │
│  │  - @Cacheable: Redis 캐싱                         │ │
│  │  - Metrics: Prometheus Counter                     │ │
│  └────────────────────┬───────────────────────────────┘ │
│                       │                                  │
│  ┌────────────────────▼───────────────────────────────┐ │
│  │         Repository Layer (Data Access)             │ │
│  │  - OrderRepository (Spring Data JPA)               │ │
│  └────────────────────┬───────────────────────────────┘ │
│                       │                                  │
└───────────────────────┼──────────────────────────────────┘
                        │
      ┌─────────────────┼─────────────────┐
      │                 │                 │
      ▼                 ▼                 ▼
┌──────────┐    ┌──────────┐    ┌──────────┐
│  MySQL   │    │  Redis   │    │Prometheus│
│  (RDS)   │    │(ElastiCache)│ │          │
└──────────┘    └──────────┘    └──────────┘
```

### 데이터 흐름

#### 1. 주문 생성 (POST /api/orders)
```
Client → Controller → Service → Repository → MySQL
                         │
                         └─→ Prometheus Counter++
```

#### 2. 주문 조회 (GET /api/orders/{id}) - 캐시 적용
```
Client → Controller → Service
                         │
                         ├─→ Redis 조회 (Cache Hit)
                         │     └─→ 즉시 반환
                         │
                         └─→ Redis 조회 (Cache Miss)
                               └─→ MySQL 조회
                                     └─→ Redis 저장 (TTL 5분)
                                           └─→ 반환
```

---

## 네트워크 구성

### VPC 설계

```
VPC: 10.0.0.0/16
│
├── Public Subnet (ap-northeast-2a): 10.0.1.0/24
│   └── NAT Gateway
│
├── Public Subnet (ap-northeast-2c): 10.0.2.0/24
│   └── NAT Gateway
│
├── Private Subnet (ap-northeast-2a): 10.0.11.0/24
│   └── EKS Worker Nodes
│
├── Private Subnet (ap-northeast-2c): 10.0.12.0/24
│   └── EKS Worker Nodes
│
├── Database Subnet (ap-northeast-2a): 10.0.21.0/24
│   └── RDS Primary
│
└── Database Subnet (ap-northeast-2c): 10.0.22.0/24
    └── RDS Standby
```

### 도메인 라우팅

| 도메인 | 서비스 | 포트 | Health Check |
|--------|--------|------|--------------|
| jenkins.fresh-chicken.org | Jenkins | 8080 | /login |
| argocd.fresh-chicken.org | ArgoCD | 80 | /healthz |
| www.fresh-chicken.org | App | 8080 | /actuator/health |
| prometheus.fresh-chicken.org | Prometheus | 9090 | /-/healthy |
| grafana.fresh-chicken.org | Grafana | 3000 | /api/health |

### Security Groups

#### 1. ALB Security Group
- Inbound: 0.0.0.0/0 → 80, 443
- Outbound: EKS Worker Nodes → All

#### 2. EKS Worker Node Security Group
- Inbound: ALB → 8080, 9090, 3000
- Outbound: 0.0.0.0/0 → All

#### 3. RDS Security Group
- Inbound: EKS Worker Nodes → 3306
- Outbound: None

---

## 참고 자료

- [Kubernetes Architecture](https://kubernetes.io/docs/concepts/architecture/)
- [AWS EKS Best Practices](https://aws.github.io/aws-eks-best-practices/)
- [ArgoCD Architecture](https://argo-cd.readthedocs.io/en/stable/operator-manual/architecture/)
