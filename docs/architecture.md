# 아키텍처와 설계 결정

## 목표

새 서비스에서 공통 빌드·배포 흐름을 작은 Jenkinsfile로 재사용합니다.
공통 정책은 라이브러리에, 서비스별 설정은 서비스 저장소에 둡니다.
공통화된 파이프라인과 모든 서비스를 한 번에 갱신하는 것은 별개입니다.
서비스별로 라이브러리 버전을 고정하고 검증한 변경부터 도입합니다.

## 구현 언어

Jenkins 파이프라인 실행부는 Groovy Shared Library로 작성합니다.
Java에서 익숙한 클래스, enum, 정적 타입 검사와 JUnit을 활용합니다.
Kubernetes를 사용한다는 이유로 Go를 추가하거나 CLI를 위해 TypeScript를 도입하지 않습니다.
별도 CLI는 서비스 등록 과정에서 반복 작업이 확인될 때 검토합니다.

## 코드 경계

- `ServiceConfig`: 입력 검증. Jenkins나 네트워크 없이 테스트할 수 있습니다.
- `vars/servicePipeline.groovy`: Jenkins 스텝을 조합합니다.
- 테스트 하네스: 스텝 호출을 기록하고 실패를 주입하여 실행 순서와 중단을 확인합니다.

설정은 `Serializable`로 유지합니다. 현재 직렬화 테스트는 일반 Java 직렬화만 확인하며
Jenkins CPS 체크포인트와 재개를 재현하지는 않습니다.

서비스와 라이브러리 개발 환경 모두 Gradle Wrapper를 사용합니다.
빌드 명령은 `./gradlew --no-daemon build`로 통일하고 빌드 도구 선택 옵션은 두지 않습니다.

## 목표 배포 흐름 — 미구현

```text
서비스 코드 변경
  → Jenkins Shared Library
      Checkout → Verify → Publish Image → Open GitOps PR
  → 배포 설정 저장소에 이미지 digest 반영
  → PR merge 또는 환경별 승인
  → Argo CD가 Git의 상태를 Kubernetes에 동기화
```

Jenkins는 테스트와 이미지 빌드·발행, 배포 설정 변경을 맡습니다.
Argo CD는 배포 설정을 읽고 Kubernetes에 동기화합니다.
레지스트리에 이미지가 올라갔다는 사실만으로 기본 Argo CD가 새 버전을 배포하지는 않습니다.
이 경계는 [Argo CD 공식 CI 연동 흐름](https://argo-cd.readthedocs.io/en/stable/user-guide/ci_automation/)을 따릅니다.

애플리케이션 소스와 GitOps 설정 저장소를 분리하고, Helm values에 환경별 이미지 digest를 기록할 계획입니다.
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
