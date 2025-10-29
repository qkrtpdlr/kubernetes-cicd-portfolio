# 설치 및 배포 가이드

## 📋 목차
1. [사전 요구사항](#사전-요구사항)
2. [로컬 개발 환경 설정](#로컬-개발-환경-설정)
3. [AWS 인프라 구축](#aws-인프라-구축)
4. [애플리케이션 배포](#애플리케이션-배포)
5. [CI/CD 파이프라인 설정](#cicd-파이프라인-설정)

---

## 🔧 사전 요구사항

### 필수 도구
```bash
# 버전 확인
terraform --version  # v1.5.0+
kubectl version      # v1.27+
aws --version        # AWS CLI v2
docker --version     # 20.10+
git --version        # 2.x+
```

### AWS 계정 설정
- **IAM 권한**: EKS, EC2, VPC, S3, ECR, CloudWatch
- **리전**: ap-northeast-2 (서울)
- **비용 예상**: 월 $100-150 (프리티어 제외)

### 필수 지식
- Kubernetes 기본 개념 (Pod, Service, Deployment)
- Docker 컨테이너 빌드 및 실행
- Git/GitHub 기본 사용법
- Linux 기본 명령어

---

## 💻 로컬 개발 환경 설정

### 1단계: 프로젝트 클론
```bash
git clone https://github.com/yourusername/kubernetes-cicd-infra.git
cd kubernetes-cicd-infra
```

### 2단계: Spring Boot 애플리케이션 로컬 실행
```bash
cd fresh-chicken-app

# Gradle 빌드
./gradlew clean build

# 로컬 실행 (MySQL/Redis 없이 H2 사용)
./gradlew bootRun --args='--spring.profiles.active=local'

# 헬스체크
curl http://localhost:8080/actuator/health
```

### 3단계: Docker 로컬 테스트
```bash
# 이미지 빌드
docker build -t fresh-chicken:local .

# 컨테이너 실행
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  fresh-chicken:local

# API 테스트
curl http://localhost:8080/api/orders
```

### 4단계: Kubernetes 로컬 클러스터 (선택)
```bash
# Minikube 설치 및 시작
minikube start --cpus=4 --memory=8192

# 애플리케이션 배포 테스트
kubectl apply -f kubernetes/app/

# 서비스 접속
minikube service fresh-chicken-service
```

---

## ☁️ AWS 인프라 구축

### 1단계: Terraform 초기화
```bash
cd terraform/

# AWS 자격증명 설정
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_DEFAULT_REGION="ap-northeast-2"

# Terraform 초기화
terraform init
```

### 2단계: 인프라 계획 확인
```bash
# 생성될 리소스 확인
terraform plan -out=tfplan

# 주요 생성 리소스:
# - VPC (10.0.0.0/16)
# - Public Subnet 2개
# - Private Subnet 2개
# - EKS Cluster (1.27)
# - Node Group (t3.medium x 2-4)
# - ECR Repository
```

### 3단계: 인프라 프로비저닝
```bash
# 인프라 생성 (약 15-20분 소요)
terraform apply tfplan

# 출력 정보 저장
terraform output -json > ../outputs.json

# EKS 클러스터 접근 설정
aws eks update-kubeconfig \
  --region ap-northeast-2 \
  --name fresh-chicken-cluster
```

### 4단계: 클러스터 확인
```bash
# 노드 상태 확인
kubectl get nodes

# Namespace 확인
kubectl get namespaces

# 기본 리소스 확인
kubectl get all -A
```

---

## 🚀 애플리케이션 배포

### 1단계: ECR에 이미지 푸시
```bash
# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin \
  123456789012.dkr.ecr.ap-northeast-2.amazonaws.com

# 이미지 빌드 및 태그
docker build -t fresh-chicken:v1.0.0 ./fresh-chicken-app/
docker tag fresh-chicken:v1.0.0 \
  123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/fresh-chicken:v1.0.0

# 이미지 푸시
docker push 123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/fresh-chicken:v1.0.0
```

### 2단계: Kubernetes Secret 생성
```bash
# MySQL 비밀번호 생성
kubectl create secret generic mysql-secret \
  --from-literal=password='your-strong-password' \
  -n fresh-chicken

# ECR 접근 Secret 생성
kubectl create secret docker-registry ecr-secret \
  --docker-server=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password) \
  -n fresh-chicken
```

### 3단계: ConfigMap 업데이트
```bash
# ConfigMap 수정
kubectl edit configmap fresh-chicken-config -n fresh-chicken

# 또는 파일에서 생성
kubectl apply -f kubernetes/app/configmap.yaml
```

### 4단계: 애플리케이션 배포
```bash
# 전체 매니페스트 적용
kubectl apply -f kubernetes/app/

# 배포 상태 확인
kubectl rollout status deployment/fresh-chicken-app -n fresh-chicken

# Pod 로그 확인
kubectl logs -f deployment/fresh-chicken-app -n fresh-chicken
```

### 5단계: 서비스 접근 확인
```bash
# Ingress 확인
kubectl get ingress -n fresh-chicken

# 외부 URL 확인
EXTERNAL_URL=$(kubectl get ingress fresh-chicken-ingress \
  -n fresh-chicken -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

# API 테스트
curl http://$EXTERNAL_URL/actuator/health
curl http://$EXTERNAL_URL/api/orders
```

---

## 🔄 CI/CD 파이프라인 설정

### 1단계: Jenkins 설치
```bash
# Helm으로 Jenkins 설치
helm repo add jenkins https://charts.jenkins.io
helm repo update

helm install jenkins jenkins/jenkins \
  --namespace jenkins \
  --create-namespace \
  --set controller.serviceType=LoadBalancer \
  --set controller.adminPassword=admin123
```

### 2단계: Jenkins 접속
```bash
# 외부 IP 확인
kubectl get svc -n jenkins

# 초기 비밀번호 확인
kubectl exec -n jenkins -it svc/jenkins -c jenkins -- \
  cat /run/secrets/additional/chart-admin-password
```

### 3단계: Jenkins 플러그인 설치
Jenkins 대시보드에서 다음 플러그인 설치:
- Kubernetes Plugin
- Docker Pipeline
- Git Plugin
- Pipeline Plugin
- Credentials Binding Plugin

### 4단계: Jenkins Credentials 설정
1. **GitHub Token**
   - Manage Jenkins → Credentials
   - Kind: Secret text
   - ID: `github-token`

2. **AWS Credentials**
   - Kind: AWS Credentials
   - ID: `aws-credentials`

3. **Kubernetes Config**
   - Kind: Secret file
   - ID: `kubeconfig`

### 5단계: Jenkins Pipeline 생성
```groovy
// Jenkinsfile 사용
pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    credentialsId: 'github-token',
                    url: 'https://github.com/yourusername/kubernetes-cicd-infra.git'
            }
        }
        // ... (나머지 스테이지)
    }
}
```

### 6단계: ArgoCD 설치
```bash
# ArgoCD 설치
kubectl create namespace argocd
kubectl apply -n argocd -f \
  https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# ArgoCD Server 외부 노출
kubectl patch svc argocd-server -n argocd -p \
  '{"spec": {"type": "LoadBalancer"}}'

# 초기 비밀번호 확인
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

### 7단계: ArgoCD 애플리케이션 등록
```bash
# ArgoCD CLI 로그인
argocd login <ARGOCD_SERVER>

# 애플리케이션 생성
argocd app create fresh-chicken \
  --repo https://github.com/yourusername/kubernetes-cicd-infra.git \
  --path kubernetes/app \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace fresh-chicken \
  --sync-policy automated
```

---

## 📊 모니터링 설정

### Prometheus + Grafana 배포
```bash
# Prometheus 배포
kubectl apply -f kubernetes/monitoring/prometheus/

# Grafana 배포
kubectl apply -f kubernetes/monitoring/grafana/

# Grafana 접속
kubectl get svc -n monitoring

# 초기 로그인: admin / admin
```

### Grafana 대시보드 추가
1. Grafana 접속 (http://EXTERNAL_IP:3000)
2. Configuration → Data Sources → Prometheus 추가
3. Dashboards → Import → 대시보드 JSON 업로드
   - `kubernetes/monitoring/grafana/dashboards/application-dashboard.json`
   - `kubernetes/monitoring/grafana/dashboards/kubernetes-dashboard.json`
   - `kubernetes/monitoring/grafana/dashboards/cicd-pipeline-dashboard.json`

---

## ✅ 배포 검증 체크리스트

### 인프라 검증
- [ ] EKS 클러스터 정상 동작
- [ ] Node Group 2개 이상 Ready 상태
- [ ] VPC 및 Subnet 생성 확인
- [ ] ECR Repository 생성 확인

### 애플리케이션 검증
- [ ] Pod 모두 Running 상태
- [ ] Service 외부 접근 가능
- [ ] Health Check 통과
- [ ] API 엔드포인트 응답 정상

### CI/CD 검증
- [ ] Jenkins Pipeline 실행 성공
- [ ] Docker 이미지 ECR 푸시 성공
- [ ] ArgoCD 자동 동기화 동작
- [ ] Rolling Update 무중단 배포 확인

### 모니터링 검증
- [ ] Prometheus 메트릭 수집 정상
- [ ] Grafana 대시보드 데이터 표시
- [ ] Alert Manager 알림 설정 완료

---

## 🔧 문제 해결

배포 중 문제 발생 시 [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)를 참고하세요.

### 자주 발생하는 이슈
1. **EKS 노드 시작 실패** → IAM Role 권한 확인
2. **Pod ImagePullBackOff** → ECR 인증 및 이미지 태그 확인
3. **Service 외부 접근 불가** → Security Group 및 Ingress 설정 확인
4. **Jenkins 빌드 실패** → Credentials 및 플러그인 확인

---

## 📚 다음 단계

- [아키텍처 문서](./ARCHITECTURE.md) - 시스템 구조 이해
- [모니터링 가이드](./MONITORING_GUIDE.md) - 대시보드 활용법
- [학습 성과](./LEARNING.md) - 프로젝트를 통해 배운 내용
- [개선 계획](./IMPROVEMENTS.md) - 향후 발전 방향
