# kubernetes-cicd-infrastructure 🚀

[![Kubernetes](https://img.shields.io/badge/Kubernetes-1.30-326CE5?logo=kubernetes&logoColor=white)](https://kubernetes.io/)
[![Jenkins](https://img.shields.io/badge/Jenkins-LTS-D24939?logo=jenkins&logoColor=white)](https://www.jenkins.io/)
[![ArgoCD](https://img.shields.io/badge/ArgoCD-Latest-EF7B4D?logo=argo&logoColor=white)](https://argoproj.github.io/cd/)
[![AWS EKS](https://img.shields.io/badge/AWS-EKS-FF9900?logo=amazon-aws&logoColor=white)](https://aws.amazon.com/eks/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Terraform](https://img.shields.io/badge/Terraform-1.5+-7B42BC?logo=terraform&logoColor=white)](https://www.terraform.io/)

> **Jenkins + ArgoCD GitOps 기반 완전 자동화된 CI/CD 파이프라인**  
> AWS EKS 환경에서 Spring Boot 애플리케이션 배포 자동화 및 Prometheus/Grafana 모니터링 구축

---

## 📊 핵심 성과

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| **배포 시간** | 25분 | 5분 | **80% 단축** ⚡ |
| **배포 빈도** | 주 2-3회 | 주 5-10회 | **200% 증가** 📈 |
| **배포 성공률** | 85% | 100% | **+15%p** ✅ |
| **롤백 시간** | 15분 | 2분 | **87% 단축** ⏪ |
| **다운타임** | 5-7분 | **0분** | **100% 제거** 🎯 |

```
✅ Git Push 한 번으로 전체 배포 프로세스 자동화 (평균 5분)
✅ 무중단 배포 (Zero-Downtime) 달성 - 50+ 배포에서 다운타임 0분
✅ Kaniko Daemon-less 빌드로 보안 강화
✅ GitOps 도입으로 배포 이력 추적 가능
✅ Prometheus/Grafana로 실시간 모니터링 (3개 대시보드)
```

👉 **[상세 성과 지표 보기](docs/ACHIEVEMENTS.md)**

---

## 🏗️ 시스템 아키텍처

### CI/CD 파이프라인

\`\`\`
Developer (git push)
    ↓
GitHub (Webhook)
    ↓
Jenkins (5-Stage Pipeline)
├── Checkout
├── Build & Test (Gradle)
├── Docker Build (Kaniko)
├── Update Manifest
└── Trigger ArgoCD
    ↓
ArgoCD (GitOps)
    ↓
EKS (Rolling Update)
    ↓
End Users (Zero Downtime)
\`\`\`

👉 **[아키텍처 상세 다이어그램](docs/ARCHITECTURE.md)**

---

## 🛠️ 기술 스택

### Infrastructure
- **Cloud**: AWS (EKS, RDS, ElastiCache, ALB, CloudFront, Route53)
- **IaC**: Terraform 1.5+ (7개 모듈)
- **Container**: Kubernetes 1.30

### CI/CD
- **CI**: Jenkins LTS
- **CD**: ArgoCD (GitOps)
- **Build**: Kaniko (Daemon-less)
- **Registry**: Amazon ECR

### Application
- **Backend**: Spring Boot 3.2 (Java 17)
- **Database**: RDS MySQL (Multi-AZ)
- **Cache**: ElastiCache Redis
- **ORM**: Spring Data JPA + HikariCP

### Monitoring
- **Metrics**: Prometheus
- **Visualization**: Grafana (3개 대시보드)

---

## 🚀 빠른 시작

\`\`\`bash
# 1. Terraform 인프라 배포
cd terraform && terraform apply

# 2. EKS 클러스터 접속
aws eks update-kubeconfig --region ap-northeast-2 --name fresh-chicken-eks

# 3. Jenkins 설치
kubectl apply -f kubernetes/jenkins/

# 4. ArgoCD 설치
kubectl apply -f kubernetes/argocd/

# 5. Spring Boot 애플리케이션 배포
kubectl apply -f kubernetes/app/

# 6. 모니터링 스택 배포
kubectl apply -f kubernetes/monitoring/
\`\`\`

👉 **[상세 설치 가이드](docs/SETUP_GUIDE.md)**

---

## 🍗 Spring Boot 애플리케이션

**Fresh Chicken 주문 플랫폼** - RESTful API 기반 주문 관리 시스템

### 주요 기능
- ✅ 주문 생성/조회/취소 API
- ✅ MySQL 연동 (Spring Data JPA)
- ✅ Redis 캐싱 (85% Hit Rate)
- ✅ Health Check + Prometheus 메트릭

### 성능 지표
- HTTP Request Rate: 120 req/s
- P95 Response Time: 150ms
- Cache Hit Rate: 85%

👉 **[Spring Boot API 문서](docs/SPRINGBOOT_GUIDE.md)**

---

## 📊 모니터링

### 3개 Grafana 대시보드
1. **Jenkins CI/CD Dashboard** - 빌드 성공률, 평균 빌드 시간
2. **ArgoCD GitOps Dashboard** - Sync 상태, Application Health
3. **Application Dashboard** - HTTP 요청, JVM 메트릭

👉 **[모니터링 가이드](docs/MONITORING_GUIDE.md)**

---

## 💼 본인 담당 업무 (35% 기여)

1. **Jenkins Pipeline 설계** - 5-Stage 파이프라인 구축
2. **ArgoCD GitOps 연동** - 자동 배포 워크플로우
3. **Spring Boot 개발** - RESTful API + MySQL/Redis 연동
4. **ALB Ingress 설정** - 도메인별 라우팅 (5개 서비스)
5. **Kubernetes Manifest** - Deployment, Service, HPA
6. **모니터링 구축** - Prometheus + Grafana 3개 대시보드
7. **트러블슈팅** - 8개 이슈 해결

👉 **[트러블슈팅 상세](docs/TROUBLESHOOTING.md)**

---

## 📚 문서

| 카테고리 | 문서 |
|---------|------|
| **아키텍처** | [시스템 아키텍처](docs/ARCHITECTURE.md) |
| **설치** | [설치 가이드](docs/SETUP_GUIDE.md) |
| **애플리케이션** | [Spring Boot 가이드](docs/SPRINGBOOT_GUIDE.md) |
| **운영** | [모니터링 가이드](docs/MONITORING_GUIDE.md) |
| **트러블슈팅** | [이슈 해결 과정](docs/TROUBLESHOOTING.md) |
| **학습** | [학습 성과](docs/LEARNING.md) |
| **계획** | [향후 개선 사항](docs/IMPROVEMENTS.md) |

---

## 📁 디렉토리 구조

\`\`\`
kubernetes-cicd-infrastructure/
├── fresh-chicken-app/          # Spring Boot 애플리케이션
├── terraform/                  # Infrastructure as Code (7개 모듈)
├── kubernetes/                 # Kubernetes Manifests
│   ├── jenkins/
│   ├── argocd/
│   ├── app/                    # Spring Boot Deployment
│   └── monitoring/             # Prometheus + Grafana
├── jenkins/Jenkinsfile         # 5-Stage Pipeline
├── docs/                       # 문서
└── README.md
\`\`\`

---

## 🎯 프로젝트 정보

| 항목 | 내용 |
|------|------|
| **기간** | 2024.07 ~ 2024.08 (4주) |
| **팀 구성** | 4명 |
| **본인 역할** | CI/CD Engineer |
| **본인 기여도** | 35% |

---

## 📞 Contact

- **Email**: rlagudfo1223@gmail.com
- **GitHub**: https://github.com/qkrtpdlr

---

## 📄 License

MIT License
