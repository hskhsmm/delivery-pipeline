# Delivery Pipeline

[![CI](https://github.com/hskhsmm/delivery-pipeline/actions/workflows/ci.yml/badge.svg)](https://github.com/hskhsmm/delivery-pipeline/actions/workflows/ci.yml)

**Gradle 서비스가 Jenkins 빌드, Docker Hub 이미지 발행, GitOps 배포 PR을 재사용하는 Shared Library입니다.**

서비스마다 Jenkinsfile에 빌드 명령, 환경변수 처리, 레지스트리 인증을 반복해서 작성하면 같은 정책을 바꿀 때 여러 저장소를 고쳐야 합니다. 이 프로젝트는 공통 절차를 Groovy 라이브러리에 두고, 서비스는 짧은 `Jenkinsfile`과 `jenkins.yml`로 필요한 값만 전달하도록 구성했습니다. 발행이 성공하면 소스 커밋과 정확한 이미지 참조를 한 파일에 남겨 다음 배포 단계에 전달합니다.

## 배포 검증

[Java 데모 서비스](https://github.com/hskhsmm/delivery-pipeline-demo)와 ARM64 기반 로컬 kind 클러스터 `delivery-sandbox`에서 이미지 발행부터 서비스 응답까지 확인했습니다.

| 검증 대상 | 확인한 결과 |
| --- | --- |
| Jenkins Shared Library | 설정 검증, 단계 순서, 이미지 발행 실패 시 중단, 동일 digest로 PR 재시도 시 기존 PR 재사용을 테스트합니다. [GitHub Actions](https://github.com/hskhsmm/delivery-pipeline/actions/workflows/ci.yml) |
| 주문 화면 v2 배포 | Jenkins 빌드 #8이 이미지를 발행하고 GitOps PR #6을 생성했습니다. PR을 검토해 병합하자 Argo CD가 자동 동기화했고, 새 Pod와 주문 화면에서 일반·빠른 배송 계산 결과를 확인했습니다. [배포 기록과 캡처](https://github.com/hskhsmm/delivery-pipeline-demo/blob/main/docs/evidence/checkout-v2.md) |
| 배포 실패와 복구 | 잘못된 readiness 검사 경로로 새 Pod가 요청을 받을 준비를 마치지 못하는 상황을 재현했습니다. 기존 서비스 응답이 유지됐고, GitOps 변경을 되돌리는 PR을 병합해 Argo CD가 `Synced` · `Healthy` 상태로 복구되는 것을 확인했습니다. [실패·복구 기록과 캡처](https://github.com/hskhsmm/delivery-pipeline-demo/blob/main/docs/evidence/readiness.md) |

### 초기 발행 이미지 실행

이미지 태그 대신 **digest**로 실행하면 Jenkins에서 확인한 것과 같은 이미지를 받을 수 있습니다. 아래 이미지는 ARM64 환경에서 검증했습니다.

```sh
docker run --rm -p 127.0.0.1:18080:8080 \
  docker.io/smini/delivery-pipeline-demo@sha256:918b73fcd6ab1761dfe28dc7c36d62abdb005fed8198375c65f7971f900e7a6e
```

다른 터미널에서 `curl -i http://127.0.0.1:18080/health`를 실행하면 `200 OK`와 `ok`가 표시됩니다. 공개 이미지는 로컬 발행 검증용 경량 Dockerfile로 만들었습니다. 같은 Dockerfile을 별도 Java 테스트 서비스 저장소에 담았고, 이 라이브러리 저장소의 [기본 예제](examples/gradle/Dockerfile)는 일반 JRE 이미지로 시작합니다.

## 파이프라인 구조

```text
서비스 저장소 ── Jenkinsfile + jenkins.yml
    ↓
deploy() ── Checkout → Configure → Verify → Publish Image (선택)
    ↓
Docker Hub 이미지 + published-image.json (이미지 digest와 소스 커밋)
    ↓ Open GitOps PR (선택) → 리뷰 및 병합
GitHub GitOps 저장소 → Argo CD 동기화 → Kubernetes Deployment / Service
```

검증 환경에서는 Jenkins를 수동으로 시작했습니다. Jenkins는 Gradle 검증, 이미지 발행, 배포 PR 생성까지 담당합니다. 사람이 GitOps PR에서 배포할 digest를 확인하고 병합하면 Argo CD가 Kubernetes에 적용합니다. 배포할 버전은 사람이 결정하고 적용은 자동화하는 **Continuous Delivery** 흐름입니다.

| 설계 결정 | 코드에서 적용한 방식 |
| --- | --- |
| 서비스 설정과 공통 정책 분리 | [deploy()](vars/deploy.groovy)가 에이전트를 할당한 뒤 한 번만 체크아웃합니다. 같은 작업 공간에서 [YAML 설정](vars/deploy.groovy)을 읽고 필수값, 타입, 지원하지 않는 키를 검사합니다. |
| 실패 지점과 실행 범위 명확화 | 체크아웃·설정 읽기와 빌드에 각각 제한 시간을 둡니다. [Gradle Verify](vars/deploy.groovy)는 `buildEnv`를 빌드 명령에만 적용합니다. Verify에서 실패하면 발행 단계에 진입하지 않습니다. |
| 자격 증명 사용 범위 제한 | [이미지 발행 단계](vars/deploy.groovy)가 Jenkins 자격 증명을 발행 셸에만 연결합니다. [발행 스크립트](resources/dev/delivery/publish-image.sh)는 빌드 컨텍스트 밖에 임시 Docker 설정을 만들고 종료 시 삭제합니다. |
| 소스와 배포할 이미지 연결 | 소스 커밋 SHA를 이미지 태그에 넣고, Buildx가 반환한 digest를 검증한 뒤 `published-image.json`에 이미지 참조와 소스 커밋을 기록합니다. |
| 배포 변경을 리뷰 가능하게 유지 | GitOps PR에서 지정한 Deployment의 이미지 한 줄만 갱신합니다. Argo CD는 GitOps 저장소의 `main`에 반영된 변경을 읽습니다. |

처음에는 설정 검증과 단계 순서를 테스트 하네스로 확인했습니다. 실제 Jenkins로 옮기자 설정 객체 생성에서 Groovy CPS 실행 방식과 샌드박스의 제약이 드러났습니다. 검증을 팩터리 메서드로 옮기고 생성자에는 필드 대입만 남겼으며, 직접 생성하던 컬렉션을 Groovy 리터럴로 바꿔 샌드박스 승인 추가 없이 실행했습니다. [CPS 수정](https://github.com/hskhsmm/delivery-pipeline/commit/a7f9d3f) · [샌드박스 수정](https://github.com/hskhsmm/delivery-pipeline/commit/05e6d08) · [Jenkins CPS 레퍼런스](https://www.jenkins.io/doc/book/pipeline/cps-method-mismatches/)

실제 발행에서는 인증 후 레이어 업로드 중 연결이 끊겼습니다. 로컬 샘플에서 `jlink`로 필요한 Java 모듈만 포함하고 런타임을 여러 레이어로 나눈 뒤, 같은 라이브러리로 발행과 pull을 완료했습니다. 이 검증에서 공용 라이브러리의 발행 로직은 그대로 사용했습니다.

## 내 서비스에서 사용하기

1. Jenkins에 이 저장소를 `delivery-pipeline`이라는 Shared Library로 등록합니다. Jenkins에는 **Pipeline Utility Steps**가, 에이전트에는 서비스에 맞는 JDK와 POSIX 셸이 필요합니다. [Jenkins 등록 가이드](docs/getting-started.md)
2. 서비스 저장소 루트에 실행 가능한 Gradle Wrapper와 아래 두 파일을 둡니다. Jenkins 잡은 **Pipeline from SCM** 또는 **Multibranch Pipeline**으로 서비스 저장소를 연결합니다.
3. Docker Hub 발행도 하려면 에이전트에 Docker Engine·CLI·Buildx를 준비하고, Jenkins Credentials에 Docker Hub 사용자명과 쓰기 권한 토큰을 **Username with password**로 등록합니다. **Credentials Binding** 플러그인이 이 자격 증명을 발행 단계에 연결합니다. 서비스의 루트 `Dockerfile`과 아래 `image` 설정을 사용합니다. [이미지 발행 가이드](docs/image-publishing.md)
4. 배포 PR을 만들려면 에이전트에 Git과 GitHub CLI(`gh`)를 설치하고, Jenkins에 배포 저장소용 토큰을 **Secret text**로 등록한 뒤 `gitOps`를 설정합니다. [GitOps 설정과 권한](docs/reference.md#gitops)

**Jenkinsfile**

```groovy
@Library('delivery-pipeline@main') _

deploy()
```

**jenkins.yml**

```yaml
serviceName: orders-api
timeoutMinutes: 30
image:
  repository: your-namespace/orders-api
  credentialsId: dockerhub-publisher
```

`image` 블록을 생략하면 Gradle 검증까지만 실행합니다. 에이전트 기본 라벨은 `java`이며 필요하면 Jenkinsfile에서 `deploy(agentLabel: 'java21-docker')`로 지정합니다. 팀에서 계속 사용할 때는 `@main` 대신 검증한 커밋 SHA나 릴리스 태그를 고정할 수 있습니다. [설정과 API 레퍼런스](docs/reference.md)

## 로컬 개발과 코드 구조

JDK 21과 저장소에 포함된 Gradle Wrapper를 사용합니다. 아래 명령은 라이브러리의 Groovy 컴파일과 테스트를 실행하며, GitHub Actions도 같은 명령을 실행합니다.

```sh
./gradlew --no-daemon build
```

| 위치 | 역할 |
| --- | --- |
| [`vars/deploy.groovy`](vars/deploy.groovy) | `deploy()` 진입점, 설정 검증, Gradle 빌드, 이미지 발행, GitOps 단계 |
| [`vars/servicePipeline.groovy`](vars/servicePipeline.groovy) | 기존 Map 호출을 공통 구현으로 전달 |
| [`resources/dev/delivery/`](resources/dev/delivery/) | Docker Buildx 발행과 GitHub PR 생성 스크립트 |
| [`test/`](test/) | Jenkins 스텝 하네스, YAML 파싱, 이미지 발행과 GitOps PR 재시도 테스트 |
| [`examples/gradle/`](examples/gradle/) | Java 샘플, Jenkinsfile, 서비스 YAML, Dockerfile |

[아키텍처와 설계 결정](docs/architecture.md) · [기여와 커밋 규칙](CONTRIBUTING.md)

[코드로 따라가는 GitOps와 테스트 하네스](docs/gitops-walkthrough.md)
