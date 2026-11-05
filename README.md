# Delivery Pipeline

[![CI](https://github.com/hskhsmm/delivery-pipeline/actions/workflows/ci.yml/badge.svg)](https://github.com/hskhsmm/delivery-pipeline/actions/workflows/ci.yml)

앞으로 만드는 Java 서비스에 재사용할 **Jenkins Shared Library**입니다.
서비스는 작은 `Jenkinsfile`과 `jenkins.yml`을 제공하고, 공통 라이브러리가 설정 검증과 Gradle 빌드를 실행합니다.
장기적으로는 이미지 발행과 GitOps 변경까지 연결하고 Argo CD가 배포를 담당하도록 확장합니다.

## 현재 구현

```text
deploy() 호출 옵션 검증 → 에이전트 할당 → Checkout → Configure → Verify
```

- 서비스 저장소의 `jenkins.yml` 읽기와 필수값·타입·알 수 없는 옵션 검증
- `buildEnv`를 Verify 범위에만 적용하고 Gradle Wrapper로 빌드·테스트
- 체크아웃·설정 읽기와 Gradle 빌드의 timeout 분리 및 실패 전파
- 기존 `servicePipeline(...)`과 YAML 진입점이 동일한 Verify 구현 재사용
- Groovy 컴파일, 실제 YAML 파싱을 포함한 단위 테스트, GitHub Actions CI

**`deploy()`라는 이름이지만 아직 이미지 발행이나 실제 배포는 수행하지 않습니다.**
[로드맵](docs/roadmap.md)에서 구현된 범위와 다음 완료 기준을 구분합니다.

## 사용 예시

Jenkins에 `delivery-pipeline` 라이브러리를 등록하고 **Pipeline Utility Steps** 플러그인을 준비합니다.
서비스 저장소 루트에 다음 두 파일을 둡니다.

`Jenkinsfile`:

```groovy
@Library('delivery-pipeline@main') _

deploy()
```

`jenkins.yml`:

```yaml
serviceName: orders-api
timeoutMinutes: 30
buildEnv:
  JAVA_TOOL_OPTIONS: '-Xmx1g'
  CI: 'true'
```

YAML을 읽으려면 에이전트와 체크아웃이 먼저 필요합니다. 기본 라벨은 `java`이며,
다른 에이전트가 필요하면 `deploy(agentLabel: 'java21')`처럼 Jenkinsfile에서 지정합니다.
YAML 안의 `agentLabel`은 적용 시점이 늦기 때문에 거부합니다.

처음 연결할 때는 `@main`을 사용할 수 있습니다. 계속 사용할 서비스에는 검증한 커밋 SHA 또는
릴리스 태그를 고정하세요. 아직 릴리스 태그는 없습니다.
서비스에는 실행 가능한 `gradlew`와 Wrapper 설정이, 에이전트에는 해당 서비스의 JDK가 필요합니다.

## 개발과 검증

라이브러리 개발 환경은 JDK 21과 Gradle Wrapper 9.8.2입니다.
Jenkins DSL과의 호환성을 고려해 Groovy 2.4.21로 컴파일합니다.

```sh
./gradlew --no-daemon build
```

첫 실행에는 Gradle과 의존성을 내려받습니다. 배포본 SHA-256과 `gradle.lockfile`로 버전을 고정합니다.
테스트 하네스는 Jenkins 스텝을 대체하고 YAML은 SnakeYAML로 실제 파싱합니다.
**실제 Jenkins 서버, Pipeline Utility Steps 플러그인, CPS 재개와 서비스의 Gradle 실행은 통합 검증하지 않았습니다.**

## 디렉토리

```text
vars/                         deploy, servicePipeline 공개 진입점과 도움말
src/dev/delivery/             설정 검증과 공통 Gradle Verify 단계
test/dev/delivery/            설정·진입점·실패 처리 테스트와 스텝 하네스
test/fixtures/                실제 파싱하는 YAML 테스트 입력
examples/gradle/              서비스용 Jenkinsfile과 jenkins.yml
build.gradle                 라이브러리 컴파일과 테스트 설정
gradle/wrapper/               고정된 Gradle 실행 환경
docs/                        시작 가이드, API, 설계와 로드맵
.github/workflows/ci.yml      라이브러리 자체 검증
```

라이브러리는 Jenkins가 Git 소스를 읽는 방식으로 사용합니다.
이 레포의 GitHub Actions는 라이브러리를 검증하고, 서비스의 Jenkins 잡은 실제 Gradle 빌드를 실행합니다.

## 문서

- [Jenkins에 연결하기](docs/getting-started.md)
- [deploy와 servicePipeline 레퍼런스](docs/reference.md)
- [아키텍처와 설계 결정](docs/architecture.md)
- [로드맵](docs/roadmap.md)
- [기여와 커밋 규칙](CONTRIBUTING.md)

## 참고

- [Jenkins Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)
- [Pipeline Utility Steps: readYaml](https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#readyaml-read-yaml-from-a-file-in-the-workspace-or-text)
- [Argo CD의 CI 연동](https://argo-cd.readthedocs.io/en/stable/user-guide/ci_automation/)
