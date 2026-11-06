# 아키텍처와 설계 결정

## 목표

새 서비스에서 공통 빌드·배포 흐름을 작은 Jenkinsfile로 재사용합니다.
공통 정책은 라이브러리에, 서비스별 설정은 서비스 저장소의 `jenkins.yml`에 둡니다.
공통화된 파이프라인과 모든 서비스를 한 번에 갱신하는 것은 별개입니다.
서비스별로 라이브러리 버전을 고정하고 검증한 변경부터 도입합니다.

## 구현 언어

Jenkins 파이프라인 실행부는 Groovy Shared Library로 작성합니다.
설정과 실행 책임을 클래스로 나누고 JUnit으로 동작을 검증합니다.
Kubernetes를 사용한다는 이유로 Go를 추가하거나 CLI를 위해 TypeScript를 도입하지 않습니다.
별도 CLI는 서비스 등록 과정에서 반복 작업이 확인될 때 검토합니다.

## 코드 경계

- `DeployOptions`: 파일을 읽기 전에 필요한 에이전트·경로·체크아웃 timeout을 검증합니다.
- `ServiceConfig`: YAML 루트와 서비스 설정을 검증하고 환경변수의 불변 복사본을 만듭니다.
- `ConfigChecks`: 두 설정 클래스의 공통 문자열·정수·알 수 없는 키 검증입니다.
- `vars/deploy.groovy`: Checkout → Configure → Verify → 선택적 Publish Image를 조합합니다.
- `vars/servicePipeline.groovy`: Map을 전달하는 기존 호출을 유지합니다.
- `GradleBuild`: 두 진입점이 공유하는 Verify와 `withEnv` 범위를 실행합니다.
- `ImageConfig`와 `ImagePublisher`: Docker Hub 설정을 검증하고 Buildx의 발행 digest를 기록합니다.
- 테스트 하네스: 스텝을 기록하고 YAML을 실제 파싱하여 순서와 실패 전파를 확인합니다.

설정을 읽으려면 체크아웃할 에이전트가 먼저 필요합니다. 에이전트 라벨은 `deploy(...)`에서 지정하며,
YAML에 뒤늦게 지정한 라벨을 무시하거나 다른 노드를 중첩 할당하지 않고 명시적으로 거부합니다.
같은 워크스페이스에서 한 번만 체크아웃하여 설정과 빌드 소스가 다른 체크아웃에서 읽히지 않게 합니다.

체크아웃과 YAML 읽기는 기본 10분, 이후 Verify는 YAML에서 정한 기본 30분 timeout을 사용합니다.
환경변수는 `withEnv`로 Verify에만 적용하고 전역 `env`를 직접 수정하지 않습니다.
설정 객체는 직렬화 가능하게 유지하지만 일반 Java 직렬화 테스트가 Jenkins CPS 재개를 검증하지는 않습니다.

서비스와 라이브러리 개발 환경 모두 `./gradlew --no-daemon build`를 사용합니다.
단위 테스트의 SnakeYAML은 test 의존성이고 실제 Jenkins에서는 Pipeline Utility Steps의 `readYaml`을 사용합니다.
로컬 Jenkins에서 실제 플러그인과 에이전트를 사용해 Java 샘플 빌드와 Docker Hub 발행을 검증했습니다. 결과는 [README](../README.md)에 정리했습니다.

## GitOps 연결 계획

```text
서비스 코드 변경
  → Jenkins Shared Library
      Checkout → Configure → Verify → Publish Image
  → published-image.json의 digest를 배포 설정 저장소에 커밋
      첫 검증은 수동, 이후 Jenkins가 PR 생성
  → Argo CD가 Git의 상태를 Kubernetes에 동기화
```

Jenkins는 테스트와 이미지 빌드·발행을 맡고, 이후 배포 설정 변경 PR 생성까지 연결할 계획입니다.
Argo CD는 배포 설정을 읽고 Kubernetes에 동기화합니다.
레지스트리에 이미지가 올라갔다는 사실만으로 기본 Argo CD가 새 버전을 배포하지는 않습니다.
이 경계는 [Argo CD 공식 CI 연동 흐름](https://argo-cd.readthedocs.io/en/stable/user-guide/ci_automation/)을 따릅니다.

애플리케이션 소스와 GitOps 설정 저장소를 분리합니다. 첫 배포에서는 일반 YAML의 `Deployment`에 이미지 digest를 기록하고, 환경별 차이가 생기면 Kustomize를 검토합니다.
검증된 같은 이미지를 sandbox → beta → production으로 승격하며 환경마다 재빌드하지 않습니다.
첫 연동은 한 서비스와 sandbox에 한정해 실제 동작부터 확인합니다.

기본 Deployment의 RollingUpdate로 시작합니다.
카나리·분석 기반 승격이나 중단은 [Argo Rollouts](https://argoproj.github.io/argo-rollouts/)의 역할로 분리해 후속 검토합니다.
Argo CD 도입만으로 빠른 롤백이나 무중단을 보장하지 않습니다.

## 하네스 관련 후속 주제

현재 작은 테스트 하네스는 파이프라인 스텝 호출만 대체합니다.
이후에는 정상 빌드, 테스트 실패, 레지스트리 장애, Git 충돌 같은 시나리오를
실제 연동 전 반복 재현할 수 있게 확장할 수 있습니다.
에이전트가 생성·수정한 파이프라인 설정을 평가하는 주제도 이 경계 위에서 검토하되,
첫 구현에 별도 에이전트 실행 프레임워크를 넣지는 않습니다.
