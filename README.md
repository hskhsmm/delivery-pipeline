# Delivery Pipeline

[![CI](https://github.com/hskhsmm/delivery-pipeline/actions/workflows/ci.yml/badge.svg)](https://github.com/hskhsmm/delivery-pipeline/actions/workflows/ci.yml)

앞으로 만드는 Java 서비스에 재사용할 **Jenkins Shared Library**입니다.
서비스마다 Jenkinsfile을 복사해 고치는 대신, 작은 설정으로 공통 빌드 흐름을 호출합니다.
장기적으로는 컨테이너 이미지 발행과 GitOps 변경까지 연결하고 Argo CD가 배포를 담당하도록 확장합니다.

## 현재 구현

```text
설정 검증 → 에이전트 할당 → Checkout → Verify
```

- Gradle Wrapper를 이용한 빌드와 테스트
- 에이전트 할당 전 설정 검증과 알 수 없는 옵션 거부
- 빌드 timeout과 실패 전파
- Groovy 컴파일, JUnit 테스트 13개, GitHub Actions CI

**아직 이미지 발행이나 실제 배포는 수행하지 않습니다.**
다음 단계는 이미지 digest를 기록하고 배포 설정 저장소에 PR을 만드는 것입니다.
[로드맵](docs/roadmap.md)에서 현재 범위와 다음 완료 기준을 구분합니다.

## 사용 예시

Jenkins에 `delivery-pipeline`이라는 이름의 공유 라이브러리를 등록한 뒤,
사용할 서비스 저장소의 `Jenkinsfile`에서 호출합니다.

```groovy
@Library('delivery-pipeline@main') _

servicePipeline(
    serviceName: 'orders-api',
    agentLabel: 'java',
    timeoutMinutes: 30
)
```

처음 연결할 때는 `@main`을 사용할 수 있습니다. 계속 사용할 서비스에는 검증한 커밋 SHA 또는
릴리스 태그를 고정하여 라이브러리 변경을 서비스별로 도입하세요. 아직 릴리스 태그는 없습니다.
서비스 저장소에는 실행 가능한 `gradlew`와 해당 Wrapper 설정이 있어야 합니다.
Jenkins 에이전트에는 서비스가 요구하는 JDK와 빌드 의존성 저장소 접근이 필요합니다.

## 개발과 검증

라이브러리 개발 환경은 JDK 21과 Gradle Wrapper 9.8.2입니다.
Jenkins DSL과의 호환성을 고려해 Groovy 2.4.21 문법으로 컴파일합니다.
설정 객체에는 정적 타입 검사를 적용하고 Jenkins 스텝 호출은 `vars/`에 모았습니다.

```sh
./gradlew --no-daemon build
```

첫 실행에는 Gradle과 의존성을 내려받습니다. Gradle을 별도로 설치할 필요는 없습니다.
Wrapper 배포본은 SHA-256으로 검증하고 의존성 버전은 `gradle.lockfile`에 고정합니다.
테스트는 Jenkins 스텝을 대체한 작은 하네스를 사용합니다. 실제 Jenkins 서버, CPS 재개,
에이전트의 Gradle 실행을 통합 검증한 것은 아닙니다.

## 디렉토리

```text
vars/                         Jenkins에서 호출하는 공개 진입점과 도움말
src/dev/delivery/             설정 검증과 빌드 도구 정의
test/dev/delivery/            설정·실패 처리·파이프라인 흐름 테스트
examples/gradle/Jenkinsfile   Gradle 서비스의 호출 예시
build.gradle                 라이브러리 컴파일과 테스트 설정
gradle/wrapper/               고정된 Gradle 실행 환경
docs/                        시작 가이드, API, 설계와 로드맵
.github/workflows/ci.yml      라이브러리 자체 검증
```

`examples/*/Jenkinsfile`은 서비스 저장소용입니다.
이 레포의 `.github/workflows/ci.yml`은 공통 라이브러리를 검증하는 별도 CI입니다.
라이브러리는 Jenkins가 이 저장소의 Git 소스를 읽는 방식으로 사용합니다.

## 문서

- [Jenkins에 연결하기](docs/getting-started.md)
- [servicePipeline 레퍼런스](docs/reference.md)
- [아키텍처와 설계 결정](docs/architecture.md)
- [로드맵](docs/roadmap.md)
- [기여와 커밋 규칙](CONTRIBUTING.md)

## 참고

- [Jenkins Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)
- [Argo CD의 CI 연동](https://argo-cd.readthedocs.io/en/stable/user-guide/ci_automation/)
- [Argo Rollouts](https://argoproj.github.io/argo-rollouts/)
