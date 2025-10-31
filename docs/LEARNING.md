# 학습 성과 및 기술 역량

## 📋 목차
1. [Kubernetes 컨테이너 오케스트레이션](#kubernetes-컨테이너-오케스트레이션)
2. [AWS 클라우드 인프라](#aws-클라우드-인프라)
3. [CI/CD 파이프라인 구축](#cicd-파이프라인-구축)
4. [모니터링 및 관찰성](#모니터링-및-관찰성)
5. [Infrastructure as Code](#infrastructure-as-code)
6. [보안 및 권한 관리](#보안-및-권한-관리)
7. [문제 해결 역량](#문제-해결-역량)

---

## 🎓 Kubernetes 컨테이너 오케스트레이션

### 핵심 개념 습득

#### 1. Pod Lifecycle 관리
```yaml
# Pod의 상태 전이 이해
Pending → Running → Succeeded/Failed
```

**실습을 통해 배운 내용**:
- **initContainer**: MySQL 준비 완료 대기 구현
  ```yaml
  initContainers:
  - name: wait-for-mysql
    command: ['sh', '-c', 'until nc -z mysql 3306; do sleep 2; done']
  ```
- **livenessProbe**: 애플리케이션 무한 루프 방지
- **readinessProbe**: 트래픽 받기 전 준비 상태 확인

**Before**: Pod가 CrashLoopBackOff 상태 → 원인 파악 어려움  
**After**: Probe 설정으로 즉시 문제 감지 및 복구

#### 2. Service Discovery 및 네트워킹
```yaml
# Service를 통한 Pod 간 통신
mysql.fresh-chicken.svc.cluster.local:3306
```

**학습 포인트**:
- **ClusterIP**: 내부 통신 (DB, Redis)
- **LoadBalancer**: 외부 노출 (API)
- **Ingress**: HTTP 라우팅 및 SSL 종료

**실전 적용**:
```yaml
# 환경별 Service Type 분리
- Dev: NodePort (비용 절감)
- Prod: LoadBalancer (안정성)
```

#### 3. ConfigMap과 Secret 분리
**문제 상황**: 하드코딩된 설정값 때문에 환경별 배포 어려움

**해결 과정**:
```yaml
# ConfigMap: 일반 설정
apiVersion: v1
kind: ConfigMap
metadata:
  name: fresh-chicken-config
data:
  SPRING_PROFILES_ACTIVE: "production"
  DB_HOST: "mysql.fresh-chicken.svc.cluster.local"

# Secret: 민감 정보
apiVersion: v1
kind: Secret
metadata:
  name: mysql-secret
type: Opaque
data:
  password: base64로인코딩된값
```

**핵심 학습**:
- ConfigMap은 버전 관리 가능 (Git)
- Secret은 etcd에 암호화 저장
- 애플리케이션 재시작 없이 설정 변경 가능 (Volume Mount)

#### 4. HPA (Horizontal Pod Autoscaler)
**Before**: 수동 스케일링 (`kubectl scale`)  
**After**: CPU 사용률 기반 자동 스케일링

```yaml
spec:
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

**부하 테스트 결과**:
- 평상시: Pod 2개 (비용 최적화)
- 트래픽 증가 시: 자동으로 10개까지 확장
- 안정화 후: 점진적 축소

---

## ☁️ AWS 클라우드 인프라

### EKS (Elastic Kubernetes Service)

#### 1. 관리형 vs 자체 구축 비교
| 항목 | 자체 구축 (EC2) | EKS |
|------|----------------|-----|
| 설치 시간 | 2-3일 | 15분 |
| 운영 부담 | 높음 (Control Plane 직접 관리) | 낮음 (AWS가 관리) |
| 비용 | EC2 비용만 | EKS 시간당 $0.10 + EC2 |
| 보안 패치 | 수동 적용 | 자동 적용 |

**결론**: 프로덕션 환경에서는 EKS가 운영 효율성 측면에서 유리

#### 2. VPC 네트워크 설계
```
VPC (10.0.0.0/16)
├── Public Subnet (10.0.1.0/24)  → NAT Gateway, LoadBalancer
└── Private Subnet (10.0.2.0/24) → EKS Nodes, Pods
```

**학습 내용**:
- **Public Subnet**: 외부 인터넷 직접 연결 (IGW)
- **Private Subnet**: NAT Gateway를 통한 아웃바운드만 허용
- **Security Group**: 최소 권한 원칙 적용

**보안 Best Practice**:
```hcl
# 예시: 데이터베이스 Security Group
resource "aws_security_group" "mysql" {
  ingress {
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    security_groups = [aws_security_group.app.id]  # App에서만 접근
  }
}
```

#### 3. IAM 권한 관리
**처음 실수**: 모든 노드에 `AdministratorAccess` 부여 → 보안 취약

**개선된 접근**:
```hcl
# IRSA (IAM Roles for Service Accounts)
resource "aws_iam_role" "app_role" {
  name = "fresh-chicken-app-role"
  
  assume_role_policy = jsonencode({
    Statement = [{
      Action = "sts:AssumeRoleWithWebIdentity"
      Principal = {
        Federated = aws_iam_openid_connect_provider.eks.arn
      }
      Condition = {
        StringEquals = {
          "${var.oidc_url}:sub": "system:serviceaccount:fresh-chicken:app-sa"
        }
      }
    }]
  })
}
```

**핵심 학습**:
- Pod 단위로 세분화된 권한 부여
- ECR 읽기, S3 쓰기 등 필요한 권한만 부여
- 정기적인 권한 감사 (AWS IAM Access Analyzer)

#### 4. ECR (Elastic Container Registry)
**Docker Hub 대비 장점**:
- EKS와 같은 리전에 배치 → 이미지 Pull 속도 향상
- IAM 통합 인증 → Docker Hub Token 관리 불필요
- 이미지 스캔 (Trivy) 자동화 가능

**실전 사용**:
```bash
# 이미지 태깅 전략
${ECR_REPO}:${GIT_COMMIT_SHA}     # 추적 가능
${ECR_REPO}:${SEMANTIC_VERSION}   # 릴리스 버전
${ECR_REPO}:latest                # 최신 버전
```

---

## 🔄 CI/CD 파이프라인 구축

### Jenkins Pipeline 설계

#### 1. Declarative Pipeline 구조
```groovy
pipeline {
    agent {
        kubernetes {
            yaml '''
              apiVersion: v1
              kind: Pod
              spec:
                containers:
                - name: gradle
                  image: gradle:7.6-jdk17
                - name: kaniko
                  image: gcr.io/kaniko-project/executor:debug
            '''
        }
    }
    
    stages {
        stage('Build') { /* ... */ }
        stage('Test') { /* ... */ }
        stage('Docker Build') { /* ... */ }
        stage('Deploy') { /* ... */ }
    }
}
```

**학습 포인트**:
- **Kubernetes Plugin**: Jenkins 자체도 Kubernetes에서 실행
- **동적 Agent**: 빌드 시에만 Pod 생성 → 리소스 효율적
- **Multi-container Pod**: 각 스테이지마다 최적화된 컨테이너 사용

#### 2. Kaniko를 통한 Docker 빌드
**Docker-in-Docker의 문제점**:
- 권한 상승 필요 (`--privileged`)
- 보안 위험 증가
- 성능 오버헤드

**Kaniko의 장점**:
```yaml
# Kaniko는 Docker Daemon 없이 빌드 가능
/kaniko/executor \
  --context=dir://workspace \
  --dockerfile=Dockerfile \
  --destination=${ECR_REPO}:${TAG} \
  --cache=true  # Layer 캐싱으로 속도 향상
```

**빌드 시간 개선**:
- Before: Docker-in-Docker (3분 30초)
- After: Kaniko + Cache (1분 20초) → **62% 단축**

#### 3. GitOps with ArgoCD
**전통적 CD vs GitOps**:

| 항목 | 전통적 CD (Push) | GitOps (Pull) |
|------|-----------------|---------------|
| 배포 방식 | CI가 직접 kubectl apply | ArgoCD가 Git 변경 감지 |
| 권한 관리 | CI에 클러스터 접근 권한 부여 | ArgoCD만 권한 필요 |
| 롤백 | 이전 Pipeline 재실행 | Git Revert만으로 가능 |
| 감사 | Jenkins 로그 확인 | Git History가 감사 로그 |

**ArgoCD 설정**:
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: fresh-chicken
spec:
  source:
    repoURL: https://github.com/user/kubernetes-cicd-infra
    targetRevision: main
    path: kubernetes/app
  destination:
    server: https://kubernetes.default.svc
    namespace: fresh-chicken
  syncPolicy:
    automated:
      prune: true      # Git에서 삭제된 리소스 자동 제거
      selfHeal: true   # 클러스터 변경 감지 시 자동 복구
```

**핵심 학습**:
- **선언적 배포**: 원하는 상태를 Git에 정의 → ArgoCD가 자동으로 맞춤
- **Drift Detection**: 수동 변경(`kubectl edit`) 감지 및 자동 복구
- **Multi-Cluster 지원**: 하나의 ArgoCD로 여러 클러스터 관리 가능

---

## 📊 모니터링 및 관찰성

### Prometheus + Grafana 스택

#### 1. Metrics 수집 아키텍처
```
Spring Boot App (Micrometer)
  → /actuator/prometheus (메트릭 노출)
  → Prometheus (수집 및 저장)
  → Grafana (시각화)
```

**Spring Boot 설정**:
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: fresh-chicken
      environment: production
```

#### 2. 주요 메트릭 지표

**Application Metrics**:
- `http_server_requests_seconds`: API 응답 시간
- `jvm_memory_used_bytes`: JVM 메모리 사용량
- `hikaricp_connections_active`: DB 커넥션 풀 상태

**Kubernetes Metrics**:
- `kube_pod_status_phase`: Pod 상태
- `container_cpu_usage_seconds_total`: CPU 사용률
- `container_memory_working_set_bytes`: 메모리 사용률

#### 3. Alert Rule 설정
```yaml
# prometheus-rules.yaml
groups:
- name: application-alerts
  rules:
  - alert: HighErrorRate
    expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
    for: 5m
    annotations:
      summary: "High error rate detected"
      description: "Error rate is {{ $value }} requests/sec"
```

**실제 알림 예시**:
1. **Pod OOMKilled** → Slack 알림 → 메모리 제한 증가
2. **높은 API 지연** → PagerDuty 알림 → HPA로 Pod 증설
3. **디스크 사용률 80% 초과** → Email 알림 → 로그 정리

**학습 내용**:
- **Alerting은 예방이 아닌 대응 도구**: 너무 많은 알림은 역효과
- **SLO 기반 알림**: "5분간 에러율 5% 초과"처럼 비즈니스 영향 중심
- **Runbook 작성**: 알림마다 대응 절차 문서화

---

## 🛠️ Infrastructure as Code

### Terraform으로 배운 IaC 원칙

#### 1. 선언적 vs 명령적
**명령적 (Imperative)**:
```bash
# AWS CLI로 수동 생성
aws ec2 create-vpc --cidr-block 10.0.0.0/16
aws ec2 create-subnet --vpc-id vpc-xxx --cidr-block 10.0.1.0/24
# ... (10개 이상의 명령어)
```

**선언적 (Declarative)**:
```hcl
# Terraform으로 원하는 상태 정의
resource "aws_vpc" "main" {
  cidr_block = "10.0.0.0/16"
}

resource "aws_subnet" "public" {
  vpc_id     = aws_vpc.main.id
  cidr_block = "10.0.1.0/24"
}
```

**장점**:
- **멱등성(Idempotency)**: 여러 번 실행해도 같은 결과
- **상태 관리**: `terraform.tfstate`로 현재 상태 추적
- **변경 계획**: `terraform plan`으로 미리 확인

#### 2. 모듈화 설계
```
terraform/
├── modules/
│   ├── vpc/           # 재사용 가능한 VPC 모듈
│   ├── eks/           # EKS 클러스터 모듈
│   └── security/      # Security Group 모듈
├── main.tf            # 모듈 조합
├── variables.tf       # 입력 변수
└── outputs.tf         # 출력 값
```

**모듈 사용 예시**:
```hcl
module "vpc" {
  source = "./modules/vpc"
  
  cidr_block = var.vpc_cidr
  azs        = var.availability_zones
  
  tags = {
    Environment = var.environment
  }
}

module "eks" {
  source = "./modules/eks"
  
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnet_ids
  cluster_version = "1.27"
}
```

**학습 포인트**:
- **DRY 원칙**: Dev/Prod 환경에서 같은 모듈 재사용
- **버전 관리**: 모듈 버전을 Git Tag로 관리
- **테스트**: `terraform validate`, `tflint`로 구문 검증

#### 3. State 관리 Best Practice
```hcl
# backend.tf
terraform {
  backend "s3" {
    bucket         = "my-terraform-state"
    key            = "eks/terraform.tfstate"
    region         = "ap-northeast-2"
    encrypt        = true
    dynamodb_table = "terraform-lock"  # 동시 실행 방지
  }
}
```

**왜 S3 Backend를 사용하는가?**:
- **팀 협업**: 로컬 파일이 아닌 중앙 저장소
- **락(Lock) 기능**: DynamoDB로 동시 수정 방지
- **버전 관리**: S3 Versioning으로 상태 복구 가능

---

## 🔐 보안 및 권한 관리

### 1. Least Privilege 원칙
**Before**: 모든 리소스에 관리자 권한  
**After**: 역할별 최소 권한만 부여

```yaml
# Kubernetes RBAC
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pod-reader
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]  # 읽기만 가능
```

### 2. Secret 관리
**절대 하지 말아야 할 것**:
```yaml
# ❌ 하드코딩
env:
- name: DB_PASSWORD
  value: "mypassword123"  # Git에 노출!
```

**올바른 방법**:
```yaml
# ✅ Secret 사용
env:
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: mysql-secret
      key: password
```

**더 나은 방법 (프로덕션)**:
- **AWS Secrets Manager**: 자동 로테이션
- **HashiCorp Vault**: 중앙 집중식 비밀 관리
- **Sealed Secrets**: Git에 암호화된 Secret 커밋 가능

---

## 🎯 문제 해결 역량

### 체계적 디버깅 프로세스

#### 1. 정보 수집
```bash
# Pod 상태 확인
kubectl get pods -n fresh-chicken
kubectl describe pod <pod-name>
kubectl logs <pod-name> --previous  # 이전 컨테이너 로그

# 리소스 사용량 확인
kubectl top pods -n fresh-chicken
kubectl top nodes
```

#### 2. 원인 분석
- **네트워크 문제**: `kubectl exec -it <pod> -- curl <service>`
- **DNS 문제**: `kubectl exec -it <pod> -- nslookup mysql`
- **권한 문제**: `kubectl auth can-i get pods --as=system:serviceaccount:fresh-chicken:app-sa`

#### 3. 검증 및 문서화
- 해결 과정을 `TROUBLESHOOTING.md`에 기록
- 재발 방지를 위한 자동화 스크립트 작성

**학습 성과**:
- 문제 해결 시간: 평균 2시간 → 30분으로 단축
- 8개의 주요 이슈 경험 및 해결책 문서화

---

## 📈 종합 성과

### 기술 스택 숙련도
- **Kubernetes**: ⭐⭐⭐⭐☆ (실전 프로젝트 경험)
- **AWS EKS**: ⭐⭐⭐⭐☆ (인프라 구축 및 운영)
- **Terraform**: ⭐⭐⭐⭐☆ (모듈화 및 Best Practice)
- **Jenkins**: ⭐⭐⭐☆☆ (Pipeline 작성)
- **ArgoCD**: ⭐⭐⭐⭐☆ (GitOps 구현)
- **Prometheus/Grafana**: ⭐⭐⭐☆☆ (모니터링 구축)

### 다음 학습 목표
1. **서비스 메시 (Istio)**: 고급 트래픽 관리
2. **Kubernetes Operator**: 커스텀 리소스 개발
3. **Multi-Cloud**: GCP GKE, Azure AKS 경험
4. **Chaos Engineering**: 장애 복원력 테스트
5. **FinOps**: 클라우드 비용 최적화

---

## 📚 추천 학습 자료

- **Kubernetes 공식 문서**: https://kubernetes.io/docs/
- **AWS EKS Workshop**: https://www.eksworkshop.com/
- **CNCF Landscape**: https://landscape.cncf.io/
- **GitOps with ArgoCD**: https://argo-cd.readthedocs.io/
