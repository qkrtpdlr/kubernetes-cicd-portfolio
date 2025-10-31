# 트러블슈팅 가이드

## 📋 목차
1. [인프라 구축 단계 이슈](#인프라-구축-단계-이슈)
2. [애플리케이션 배포 이슈](#애플리케이션-배포-이슈)
3. [CI/CD 파이프라인 이슈](#cicd-파이프라인-이슈)
4. [모니터링 시스템 이슈](#모니터링-시스템-이슈)
5. [성능 및 최적화](#성능-및-최적화)

---

## 🏗️ 인프라 구축 단계 이슈

### Issue #1: EKS 클러스터 생성 실패

**증상**
```
Error: error creating EKS Cluster: operation error EKS: CreateCluster
InvalidParameterException: Role arn:aws:iam::xxx:role/eks-cluster-role is not valid
```

**원인**
- IAM Role의 신뢰 관계(Trust Policy)가 올바르게 설정되지 않음
- EKS 서비스 접근 권한 부족

**해결 과정**
```bash
# 1. IAM Role 신뢰 관계 확인
aws iam get-role --role-name eks-cluster-role

# 2. Trust Policy 업데이트
cat > trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "eks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

aws iam update-assume-role-policy \
  --role-name eks-cluster-role \
  --policy-document file://trust-policy.json

# 3. 필수 정책 연결 확인
aws iam attach-role-policy \
  --role-name eks-cluster-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy
```

**학습 내용**
- IAM Role의 신뢰 관계는 "누가" 역할을 맡을 수 있는지 정의
- EKS 클러스터는 `eks.amazonaws.com` 서비스가 역할을 맡아야 함
- Terraform으로 자동화할 때도 순서가 중요 (Role → Policy → Cluster)

**예방 조치**
```hcl
# terraform/iam.tf
resource "aws_iam_role" "eks_cluster_role" {
  name = "eks-cluster-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "eks.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster_role.name
}
```

---

### Issue #2: EKS 노드가 Ready 상태가 되지 않음

**증상**
```bash
kubectl get nodes
NAME                                      STATUS     ROLES    AGE   VERSION
ip-10-0-1-100.ap-northeast-2.compute...  NotReady   <none>   5m    v1.27.0
```

**원인**
- VPC CNI 플러그인 미설치
- Security Group에서 노드 간 통신 차단
- IAM Role에 노드 정책 누락

**해결 과정**
```bash
# 1. CNI 플러그인 확인
kubectl get daemonset -n kube-system aws-node

# CNI 없으면 설치
kubectl apply -f https://raw.githubusercontent.com/aws/amazon-vpc-cni-k8s/release-1.12/config/master/aws-k8s-cni.yaml

# 2. Security Group 규칙 확인
aws ec2 describe-security-groups \
  --group-ids sg-xxxxx \
  --query 'SecurityGroups[0].IpPermissions'

# 3. 노드 간 통신 허용 규칙 추가
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxx \
  --protocol all \
  --source-group sg-xxxxx

# 4. 노드 로그 확인
kubectl logs -n kube-system -l k8s-app=aws-node
```

**학습 내용**
- Kubernetes는 네트워크 플러그인 없이는 동작 불가
- AWS EKS는 VPC CNI를 기본 네트워크 플러그인으로 사용
- 노드 간 통신은 Security Group에서 자기 자신을 소스로 허용해야 함

**예방 조치**
- Terraform에서 EKS 모듈 사용 시 자동으로 CNI 설치됨
- Security Group 규칙을 명시적으로 정의

---

### Issue #3: Terraform Apply 중 리소스 의존성 오류

**증상**
```
Error: error creating EKS Node Group: InvalidParameterException: 
Subnets specified must exist
```

**원인**
- Terraform 리소스 생성 순서 문제
- VPC/Subnet이 완전히 생성되기 전에 EKS가 생성 시도

**해결 과정**
```hcl
# terraform/eks.tf
resource "aws_eks_cluster" "main" {
  # 명시적 의존성 추가
  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy,
    aws_subnet.private
  ]
  
  name     = var.cluster_name
  role_arn = aws_iam_role.eks_cluster_role.arn
  
  vpc_config {
    subnet_ids = aws_subnet.private[*].id
  }
}
```

**학습 내용**
- Terraform의 암시적 의존성(참조)만으로 부족한 경우가 있음
- `depends_on`으로 명시적 의존성 정의 필요
- 복잡한 인프라는 모듈 단위로 분리하여 단계적 적용

---

## 🚀 애플리케이션 배포 이슈

### Issue #4: Pod ImagePullBackOff 에러

**증상**
```bash
kubectl get pods -n fresh-chicken
NAME                          READY   STATUS             RESTARTS   AGE
fresh-chicken-app-xxx         0/1     ImagePullBackOff   0          2m

kubectl describe pod fresh-chicken-app-xxx -n fresh-chicken
Failed to pull image "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/fresh-chicken:v1.0.0":
rpc error: code = Unknown desc = Error response from daemon: 
pull access denied for ..., repository does not exist or may require 'docker login'
```

**원인**
- ECR 인증 토큰 만료 (12시간 유효)
- ServiceAccount에 ECR 접근 권한 없음
- 이미지 태그 오타 또는 실제로 존재하지 않음

**해결 과정**
```bash
# 1. 이미지 존재 확인
aws ecr describe-images \
  --repository-name fresh-chicken \
  --region ap-northeast-2

# 2. ECR 로그인 확인
aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin \
  123456789012.dkr.ecr.ap-northeast-2.amazonaws.com

# 3. Kubernetes Secret 재생성
kubectl delete secret ecr-secret -n fresh-chicken

kubectl create secret docker-registry ecr-secret \
  --docker-server=123456789012.dkr.ecr.ap-northeast-2.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region ap-northeast-2) \
  -n fresh-chicken

# 4. Deployment에 imagePullSecrets 추가
kubectl patch deployment fresh-chicken-app -n fresh-chicken \
  --type='json' \
  -p='[{"op": "add", "path": "/spec/template/spec/imagePullSecrets", "value": [{"name": "ecr-secret"}]}]'
```

**더 나은 해결책: IRSA (IAM Roles for Service Accounts)**
```hcl
# terraform/irsa.tf
resource "aws_iam_role" "ecr_access" {
  name = "eks-ecr-access-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRoleWithWebIdentity"
      Effect = "Allow"
      Principal = {
        Federated = aws_iam_openid_connect_provider.eks.arn
      }
      Condition = {
        StringEquals = {
          "${replace(aws_iam_openid_connect_provider.eks.url, "https://", "")}:sub": 
          "system:serviceaccount:fresh-chicken:fresh-chicken-sa"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecr_access" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.ecr_access.name
}
```

```yaml
# kubernetes/app/serviceaccount.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: fresh-chicken-sa
  namespace: fresh-chicken
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/eks-ecr-access-role
```

**학습 내용**
- ECR 인증은 12시간마다 만료되어 수동 관리는 비효율적
- IRSA를 사용하면 자동으로 토큰 갱신 (보안 Best Practice)
- ServiceAccount 단위로 세분화된 권한 부여 가능

---

### Issue #5: CrashLoopBackOff - MySQL 연결 실패

**증상**
```bash
kubectl logs fresh-chicken-app-xxx -n fresh-chicken

com.mysql.cj.jdbc.exceptions.CommunicationsException: 
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago.
```

**원인**
- MySQL Service가 아직 준비되지 않음
- ConfigMap의 DB 호스트명 오타
- MySQL Pod가 실행 중이지만 초기화 중

**해결 과정**
```bash
# 1. MySQL Pod 상태 확인
kubectl get pods -l app=mysql -n fresh-chicken
kubectl logs mysql-xxx -n fresh-chicken

# 2. MySQL Service 확인
kubectl get svc mysql -n fresh-chicken
kubectl describe svc mysql -n fresh-chicken

# 3. ConfigMap 확인
kubectl get configmap fresh-chicken-config -n fresh-chicken -o yaml

# 4. 수동 연결 테스트
kubectl run mysql-client --rm -it --restart=Never \
  --image=mysql:8.0 -n fresh-chicken -- \
  mysql -h mysql.fresh-chicken.svc.cluster.local -u root -p

# 5. Deployment에 initContainer 추가
```

```yaml
# kubernetes/app/deployment.yaml
spec:
  template:
    spec:
      initContainers:
      - name: wait-for-mysql
        image: busybox:1.35
        command: 
        - 'sh'
        - '-c'
        - |
          until nc -z mysql.fresh-chicken.svc.cluster.local 3306; do
            echo "Waiting for MySQL..."
            sleep 2
          done
          echo "MySQL is ready!"
      containers:
      - name: fresh-chicken-app
        # ... (기존 설정)
```

**학습 내용**
- Kubernetes는 Pod 시작 순서를 보장하지 않음
- initContainer로 의존성 체크 가능
- readinessProbe와 livenessProbe로 헬스체크 필수

**Best Practice**
```yaml
containers:
- name: fresh-chicken-app
  # ...
  livenessProbe:
    httpGet:
      path: /actuator/health/liveness
      port: 8080
    initialDelaySeconds: 60
    periodSeconds: 10
    
  readinessProbe:
    httpGet:
      path: /actuator/health/readiness
      port: 8080
    initialDelaySeconds: 30
    periodSeconds: 5
```

---

## 🔄 CI/CD 파이프라인 이슈

### Issue #6: Jenkins Pipeline - Gradle 빌드 실패

**증상**
```
[ERROR] Could not find or load main class org.gradle.wrapper.GradleWrapperMain
```

**원인**
- Git에 `gradle/wrapper/gradle-wrapper.jar` 커밋 안 됨
- `.gitignore`에서 wrapper jar 제외됨

**해결 과정**
```bash
# 1. .gitignore 수정
# .gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar  # 이 줄 추가

# 2. wrapper jar 다시 추가
git add -f gradle/wrapper/gradle-wrapper.jar
git commit -m "Add Gradle wrapper jar"
git push

# 3. Jenkins에서 재빌드
```

**학습 내용**
- Gradle Wrapper는 빌드 환경 통일을 위해 jar 파일도 커밋해야 함
- Jenkins에서 Gradle 로컬 설치 없이 `./gradlew` 사용 가능

---

### Issue #7: Kaniko 빌드 - "error building image: getting stage builder"

**증상**
```
error building image: getting stage builder for stage 0: 
Get https://index.docker.io/v2/: dial tcp: lookup index.docker.io: 
no such host
```

**원인**
- Jenkins Pod의 DNS 설정 문제
- Kubernetes CoreDNS 서비스 장애

**해결 과정**
```bash
# 1. CoreDNS 상태 확인
kubectl get pods -n kube-system -l k8s-app=kube-dns

# 2. Jenkins Pod DNS 테스트
kubectl exec -it jenkins-xxx -n jenkins -- nslookup google.com

# 3. CoreDNS 재시작
kubectl rollout restart deployment coredns -n kube-system

# 4. Kaniko 빌드에 DNS 서버 명시
```

```groovy
// Jenkinsfile
stage('Build Docker Image') {
    steps {
        container('kaniko') {
            sh '''
            /kaniko/executor \
              --context=dir://${WORKSPACE}/fresh-chicken-app \
              --dockerfile=Dockerfile \
              --destination=${ECR_REPO}:${IMAGE_TAG} \
              --cache=true \
              --dns=8.8.8.8  # Google DNS 추가
            '''
        }
    }
}
```

---

### Issue #8: ArgoCD - Application 동기화 실패

**증상**
```
ComparisonError: Manifest generation error (cached): 
repository not found
```

**원인**
- ArgoCD가 Private Repository에 접근 권한 없음
- GitHub Token 만료

**해결 과정**
```bash
# 1. GitHub Token 생성 (repo 권한 포함)

# 2. ArgoCD에 Repository 등록
argocd repo add https://github.com/yourusername/kubernetes-cicd-infra.git \
  --username yourusername \
  --password ghp_xxxxxxxxxxxxx

# 3. 또는 SSH Key 사용
ssh-keygen -t rsa -b 4096 -C "argocd@example.com"
# GitHub에 Deploy Key 등록

argocd repo add git@github.com:yourusername/kubernetes-cicd-infra.git \
  --ssh-private-key-path ~/.ssh/id_rsa

# 4. Application 동기화 재시도
argocd app sync fresh-chicken
```

**학습 내용**
- Private Repository는 인증 필수
- Personal Access Token보다 Deploy Key가 보안상 유리
- ArgoCD는 Git Repository를 "진실의 원천(Source of Truth)"으로 사용

---

## 📊 모니터링 시스템 이슈

### Prometheus가 메트릭을 수집하지 못함

**증상**
- Grafana 대시보드에 "No Data" 표시
- Prometheus Target이 "Down" 상태

**해결 과정**
```bash
# 1. ServiceMonitor 확인
kubectl get servicemonitor -n monitoring

# 2. Prometheus가 ServiceMonitor를 인식하는지 확인
kubectl logs -n monitoring prometheus-xxx

# 3. Service에 올바른 Label 추가
kubectl label service fresh-chicken-service -n fresh-chicken \
  prometheus.io/scrape=true \
  prometheus.io/port=8080 \
  prometheus.io/path=/actuator/prometheus
```

---

## 🎯 성능 및 최적화

### HPA가 스케일링하지 않음

**해결 과정**
```bash
# Metrics Server 설치
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# HPA 상태 확인
kubectl get hpa -n fresh-chicken
kubectl describe hpa fresh-chicken-hpa -n fresh-chicken
```

---

## 📚 참고 자료

- [Kubernetes 공식 트러블슈팅](https://kubernetes.io/docs/tasks/debug/)
- [AWS EKS 문제 해결 가이드](https://docs.aws.amazon.com/eks/latest/userguide/troubleshooting.html)
- [ArgoCD FAQ](https://argo-cd.readthedocs.io/en/stable/faq/)
