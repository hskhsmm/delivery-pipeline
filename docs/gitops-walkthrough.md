# 코드로 따라가는 GitOps와 테스트 하네스

서비스마다 Jenkins 파이프라인을 다시 작성하는 대신, Gradle 서비스가 `deploy()`를 호출해 검증과 이미지 발행을 공통으로 사용합니다. 배포할 이미지가 정해지면 Git에 그 버전을 기록하고, Argo CD가 그 설정을 읽어 Kubernetes에 반영합니다.

## 배포에 관여하는 세 저장소

| 저장소 | 담고 있는 것 | 사용하는 쪽 |
| --- | --- | --- |
| `delivery-pipeline` | 공통 Groovy 코드, 셸 스크립트, 테스트 | Jenkins가 Shared Library로 로드하고, GitHub Actions가 라이브러리 자체를 테스트합니다. |
| `delivery-pipeline-demo` | Java 서비스 소스, `Dockerfile`, `Jenkinsfile`, `jenkins.yml` | Jenkins가 체크아웃해서 검증하고 이미지로 만듭니다. |
| `delivery-pipeline-gitops` | sandbox에서 실행할 Kubernetes `Deployment`와 `Service` | Jenkins가 이미지 변경을 제안하고, Argo CD가 `main`을 읽습니다. |

Docker Hub는 실제 컨테이너 이미지를 보관합니다. GitOps 저장소에는 **어떤 이미지를 실행할지** 적습니다. 책으로 비유하면 Docker Hub에 책의 여러 판본이 있고, GitOps 저장소에는 지금 진열할 판본 번호가 적혀 있습니다. Jenkins가 새 판본을 만들면 Git의 번호를 바꾸는 PR을 열고, Argo CD는 병합된 번호를 읽습니다.

## 1. 서비스가 공통 파이프라인을 호출한다

```groovy
@Library('delivery-pipeline@main') _
deploy()
```

`@Library`는 Jenkins에 등록해 둔 공유 저장소의 버전을 지정합니다. `deploy()`를 호출하면 Jenkins가 `vars/deploy.groovy`의 `call()`을 실행합니다. 예제는 `main`을 쓰고, 실제 서비스는 검증한 태그나 커밋으로 고정할 수 있습니다. [예제 Jenkinsfile](../examples/gradle/Jenkinsfile) · [공통 진입점](../vars/deploy.groovy#L1) · [Jenkins Shared Library 문서](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)

`call()`에서 아래 순서로 진행합니다.

1. **Checkout**에서 작업 폴더를 비우고 이번 서비스 소스를 받아옵니다. Git에서 받은 `GIT_COMMIT`은 어떤 코드를 이미지로 만들었는지 추적하는 데 쓰고, `GIT_BRANCH`는 발행 대상인지 판단하는 데 씁니다.
2. **Configure**에서 그 체크아웃의 `jenkins.yml`을 읽습니다. `gitOps`에 적힌 저장소와 YAML 경로를 검사하고, `gitOps`가 있다면 `image`도 있어야 빌드가 진행됩니다.
3. **Verify**에서 서비스의 `./gradlew --no-daemon build`를 실행합니다. `JAVA_TOOL_OPTIONS` 같은 `buildEnv`는 이 명령의 실행 범위에만 전달됩니다. Gradle에서 실패하면 이후 단계도 멈춥니다.
4. **Publish Image**는 `image`가 있을 때 실행합니다. `gitOps`까지 켠 서비스의 기능 브랜치와 PR 빌드는 Verify까지만 진행합니다. 서비스 `main`에서 발행한 이미지가 다음 단계의 입력입니다.

[체크아웃과 단계 순서](../vars/deploy.groovy#L6) · [Gradle 실행](../vars/deploy.groovy#L65) · [설정 검증](../vars/deploy.groovy#L234)

## 2. Docker Hub가 받은 이미지의 digest를 다음 단계에 넘긴다

Jenkins는 `publishImage()`에서 Docker Hub 자격 증명을 잠시 연결하고, `publish-image.sh`에서 `docker buildx build --push`를 실행합니다. 서비스 소스 커밋이 `f626f72…`이면 `sha-f626f72…`라는 태그가 붙습니다. Buildx가 반환한 `containerimage.digest`는 이미지 내용을 가리키는 고유 식별자입니다. 배포 YAML에 `repository@sha256:…`를 기록하면 해당 이미지를 정확하게 지정할 수 있습니다.

Jenkins 빌드 #4의 `published-image.json`에는 다음 값이 들어갔습니다.

```json
{
  "image": "docker.io/smini/delivery-pipeline-demo@sha256:2c62fbc6ec52f00a908c122f68493595cc2cdd98ab97f45a26ac512e736de62b",
  "tag": "docker.io/smini/delivery-pipeline-demo:sha-f626f7292ce1d89f2ce79705ee47581210aff1f2",
  "digest": "sha256:2c62fbc6ec52f00a908c122f68493595cc2cdd98ab97f45a26ac512e736de62b",
  "sourceRevision": "f626f7292ce1d89f2ce79705ee47581210aff1f2"
}
```

여기서 `sourceRevision`은 **서비스 소스의 커밋**, `digest`는 **Docker 이미지의 식별자**입니다. 라이브러리는 `published-image.json`을 Jenkins 빌드 결과에 보관하고, 같은 `image` 값을 `publishImage()`의 반환값으로 `updateGitOps()`에 전달합니다. 자동 PR 단계가 실패하더라도 어떤 이미지가 발행됐는지 아티팩트에서 확인할 수 있습니다. [이미지 발행 코드](../vars/deploy.groovy#L76) · [Buildx 스크립트](../resources/dev/delivery/publish-image.sh#L18) · [Docker의 digest 설명](https://docs.docker.com/reference/cli/docker/image/pull/#pull-an-image-by-digest-immutable-identifier)

## 3. GitOps 저장소에서 Deployment 이미지 한 줄을 바꾼다

자동 PR을 켜려면 기존 `image` 설정을 유지하고 서비스의 `jenkins.yml`에 이 블록을 추가합니다. 아래 값은 데모용 설정 예시입니다.

```yaml
gitOps:
  repository: hskhsmm/delivery-pipeline-gitops
  manifest: apps/delivery-demo/deployment.yaml
  credentialsId: delivery-gitops-publisher
```

`updateGitOps()`는 서비스 소스와 별도의 임시 작업 폴더를 만들고 `gitops-pr.sh`를 `checkout` 모드로 실행합니다. 이 모드는 GitOps 저장소의 `main`을 가져오고, 지정한 YAML이 Git으로 관리되는 일반 파일인지 확인합니다. `credentialsId`는 Jenkins에 저장한 GitHub 토큰의 이름이며, 스크립트는 단계 안에서 `GH_TOKEN`으로 받습니다. 임시 작업 폴더는 성공 여부와 관계없이 `finally`에서 정리합니다. [GitOps 단계](../vars/deploy.groovy#L124) · [체크아웃 스크립트](../resources/dev/delivery/gitops-pr.sh#L14) · [설정과 토큰 권한](reference.md#gitops)

`updateGitOpsManifest()`는 YAML을 읽고 `apps/v1 Deployment`의 이름과 그 안의 컨테이너 이름이 `serviceName`과 같은지 확인합니다. 예를 들어 `delivery-demo`의 대상은 `spec.template.spec.containers` 아래에 있는 `delivery-demo` 컨테이너입니다.

```diff
         - name: delivery-demo
-          image: docker.io/smini/delivery-pipeline-demo@sha256:<기존-digest>
+          image: docker.io/smini/delivery-pipeline-demo@sha256:<새-digest>
```

코드는 이전 이미지 문자열이 파일에서 한 번만 등장하는 경우 그 자리의 문자열을 바꿉니다. 그런 다음 변경된 YAML을 다시 파싱해서, 메모리에서 대상 컨테이너의 `image`만 바꾼 결과와 일치하는지 확인합니다. 이 방식으로 주석과 들여쓰기를 보존하면서 엉뚱한 위치를 수정했을 때 실패시킵니다. 이미 같은 digest를 가리키면 파일을 쓰는 단계와 PR 생성을 건너뜁니다. [매니페스트 갱신 코드](../vars/deploy.groovy#L161)

## 4. 배포 브랜치를 만들고 PR을 연다

YAML 변경이 필요하면 같은 `gitops-pr.sh`를 `open` 모드로 실행합니다. 서비스 이름, YAML 경로, 이미지 참조를 조합해 항상 같은 입력에는 같은 `delivery/<service>/<hash>` 브랜치 이름을 만듭니다.

1. GitHub에 그 브랜치에서 `main`으로 열린 PR이 있는지 조회합니다. 열려 있으면 기존 링크를 출력합니다.
2. 새 PR이 필요하면 지정한 `deployment.yaml`만 커밋하고 배포 브랜치에 푸시합니다. 커밋 대상을 파일 경로로 지정하기 때문에 같은 작업 폴더에서 바뀐 다른 파일은 이 커밋에 들어가지 않습니다.
3. `gh api`로 `main` 대상 PR을 엽니다. PR 본문에 이미지 참조와 서비스 소스 커밋을 남겨 리뷰할 때 무엇을 배포하는지 확인할 수 있게 합니다.

푸시가 충돌하면 기존 브랜치의 파일 상태가 이번에 만들려던 커밋과 같은지 확인합니다. PR 생성 요청이 실패했을 때도 다른 실행이 이미 만들었는지 한 번 더 조회합니다. 닫힌 PR만 있으면 상태를 확인하도록 빌드를 실패시킵니다. 재시도에서 재사용하는 단위는 **같은 digest**입니다. 서비스를 다시 빌드해 새 digest가 나오면 새 배포 요청이 됩니다. [브랜치·PR·재시도 코드](../resources/dev/delivery/gitops-pr.sh#L34)

## 5. Argo CD가 Git을 읽고 Kubernetes가 새 이미지를 실행한다

전용 kind 클러스터 `delivery-sandbox`에 설치한 Argo CD의 `Application`에는 다음 설정을 넣었습니다.

| 항목 | 데모 설정 | 하는 일 |
| --- | --- | --- |
| `repoURL` | `delivery-pipeline-gitops`의 GitHub SSH 주소 | 배포 설정을 읽을 곳을 지정합니다. 비공개 저장소에는 읽기 전용 SSH 키로 접속합니다. |
| `targetRevision` | `main` | PR 변경이 이 브랜치에 들어오면 배포 대상으로 읽습니다. |
| `path` | `apps/delivery-demo` | 이 폴더의 `Deployment`와 `Service`를 읽습니다. |
| `destination.namespace` | `delivery-demo` | 이 네임스페이스에 데모 서비스를 배포합니다. |
| `syncPolicy.automated.enabled` | `true` | Git의 설정과 클러스터 상태가 다를 때 자동으로 동기화합니다. |

Argo CD가 Kubernetes에 새 Deployment 설정을 반영하면, Deployment의 Pod 템플릿에 이미지 변경이 생깁니다. Kubernetes가 그 digest의 이미지를 Docker Hub에서 사용해 새 Pod를 띄웁니다. readiness probe는 Pod가 요청을 받을 준비가 됐는지 `/health`로 확인합니다. `Service`는 `app: delivery-demo` 라벨이 붙은 준비된 Pod에 요청을 보냅니다. [Argo CD 자동 동기화](https://argo-cd.readthedocs.io/en/stable/user-guide/auto_sync/) · [Kubernetes Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)

실제 검증은 두 번에 나눠 진행했습니다. 먼저 로컬 Git 저장소로 Argo CD가 새 이미지를 띄우는지 확인했습니다. 그다음 Jenkins #4가 서비스 커밋 `f626f72`를 빌드해 발행한 digest를 비공개 GitHub GitOps 저장소의 `aff3ae3` 커밋에 **직접 기록**했습니다. Argo CD가 그 커밋으로 자동 동기화했고 `Synced`·`Healthy`, Kubernetes 롤아웃 성공, 서비스 `/health`의 `200 OK`를 확인했습니다. `aff3ae3`는 **배포 설정 커밋**이므로 소스 커밋 `f626f72`와 서로 다른 기록입니다. [README의 검증 기록](../README.md#현재-어디까지-되었나요)

이 수동 검증으로 이미지 발행과 Git 변경 이후의 배포를 먼저 확인했고, 같은 한 줄 변경을 Jenkins가 PR로 제안하는 코드를 추가했습니다. **실제 Jenkins에서 자동 생성한 PR의 병합부터 Argo CD 배포까지 이어지는 한 번의 실행**은 GitHub 쓰기 토큰 연결 후 확인할 단계입니다.

### 단계별로 검증해서 찾은 문제

- 처음에는 Groovy 테스트가 통과했는데 실제 Jenkins에서 설정 객체를 만드는 중 오류가 났습니다. Jenkins는 중간에 멈춘 파이프라인을 이어서 실행할 수 있도록 Groovy 코드를 특수한 방식으로 실행합니다. 당시에는 생성자가 하던 검증을 별도 메서드로 옮기고 샌드박스가 허용하는 컬렉션 표현을 사용했습니다. 이 경험으로 단계의 규칙을 테스트하는 것과 실제 Jenkins에서 실행을 확인하는 일이 모두 필요하다는 걸 확인했습니다. [CPS 수정 커밋](https://github.com/hskhsmm/delivery-pipeline/commit/a7f9d3f) · [샌드박스 수정 커밋](https://github.com/hskhsmm/delivery-pipeline/commit/05e6d08)
- 실제 Docker Hub 발행에서는 인증이 끝난 뒤 이미지 레이어 업로드 중 연결이 끊겼습니다. 데모에서 `jlink`로 실행에 필요한 Java 모듈만 담고 런타임을 여러 레이어로 나눠 발행을 마쳤습니다. 이후 같은 digest로 이미지를 받아 실행하는 것까지 확인했습니다. [README의 발행 기록](../README.md#어떻게-설계했나요)

## 6. 하네스가 실패를 어떻게 재현하는가

하네스는 테스트에서 필요한 상황을 만들어 주고, 파이프라인이 어떤 선택을 했는지 기록하는 코드입니다. 실제 Jenkins와 Docker Hub를 매번 띄우면 테스트 실패와 외부 시스템 오류가 섞이고, 위험한 경우를 반복하기 어렵습니다. 그래서 같은 라이브러리 코드를 작은 테스트 환경에서 실행할 수 있게 했습니다.

### Jenkins 스텝을 대체해서 의사결정을 검사한다

`PipelineHarness.load('deploy')`는 실제 `vars/deploy.groovy`를 읽고 `stage`, `checkout`, `sh`, `withCredentials` 같은 Jenkins 명령에 테스트용 동작을 연결합니다. 예를 들어 테스트용 `stage`는 이름을 `events`에 기록한 뒤 본문을 실행하고, `checkout`은 준비해 둔 파일과 가짜 커밋·브랜치를 돌려줍니다. `readYaml`은 SnakeYAML로 입력을 실제로 읽습니다. 따라서 YAML이 잘못됐을 때 테스트에도 같은 형태의 입력 오류를 넣을 수 있습니다. [하네스 구현](../test/dev/delivery/PipelineHarness.groovy#L27)

예를 들어 이미지 발행이 실패했을 때 **Jenkins가 GitHub 쓰기 토큰을 꺼내지 않는지** 아래처럼 검사합니다. 실제 테스트의 핵심 부분입니다.

```groovy
PipelineHarness harness = service()
harness.failPublish = true
expectFailure(harness, 'publish failed')
assertEquals(['hub-publisher'], harness.events.findAll { it.type == 'withCredentials' }.collect {
    it.bindings[0].credentialsId
})
```

`failPublish`를 켜면 테스트용 `sh`가 이미지 발행 단계에서 예외를 던집니다. 기록된 자격 증명 요청이 `hub-publisher` 하나라면 Docker Hub 발행은 시도했고 GitOps 쓰기 자격 증명 단계에는 들어가지 않았음을 알 수 있습니다. 같은 방식으로 기능 브랜치에서 Verify까지만 실행되는지, 이미 동일한 digest라면 파일과 PR을 생성하지 않는지, 잘못된 Deployment를 받으면 중단하는지 검사합니다. [GitOps 동작 테스트](../test/dev/delivery/GitOpsTest.groovy#L10)

### 실제 셸과 Git으로 PR 재시도를 검사한다

`GitOpsShellTest`는 임시 로컬 Git 저장소를 원격처럼 만들고 실제 `gitops-pr.sh`를 실행합니다. GitHub API를 부르는 `gh`만 테스트용 스크립트로 바꿉니다. 첫 실행에서 배포 브랜치를 푸시하고 PR을 열게 한 뒤, 두 번째 실행이 같은 PR을 재사용하는지 확인합니다. 일부러 `service.yaml`도 바꿔 두고 `deployment.yaml`만 커밋됐는지 검사합니다. 테스트 마지막에는 원격 `main`이 처음 커밋 그대로인지 확인합니다. [셸 통합 테스트](../test/dev/delivery/GitOpsShellTest.groovy#L12)

공유 라이브러리 저장소 루트에서 GitOps 테스트만 실행해볼 수 있습니다.

```sh
./gradlew --no-daemon test --tests 'dev.delivery.GitOps*'
```

전체 검증 명령은 `./gradlew --no-daemon build`입니다. GitHub Actions도 `main` 푸시와 PR에서 이 명령을 실행합니다. 하네스는 Jenkins 단계 순서와 입력·실패 처리를 빠르게 확인하고, 실제 Jenkins 실행은 플러그인, Jenkins의 파이프라인 재개 동작, 인증과 에이전트 환경을 검증하는 데 필요합니다. [GitHub Actions 설정](../.github/workflows/ci.yml) · [Gradle CI 결과](https://github.com/hskhsmm/delivery-pipeline/actions/runs/43170340035)

## 이 프로젝트에서 하네스가 하는 역할

같은 배포 경로를 여러 서비스에 제공하려면, 라이브러리를 고칠 때마다 기존 서비스가 기대하는 동작을 빠르게 확인할 수 있어야 합니다. 하네스는 **어떤 입력을 줬을 때 어디까지 실행되고 무엇을 남겨야 하는가**를 코드로 고정합니다. 예를 들어 `jenkins.yml`에 `../deployment.yaml` 같은 경로를 쓰면 빌드 전에 실패하고, 발행이 실패하면 배포 PR을 열지 않는다는 규칙을 반복해서 검사합니다. 형식이 올바른 경로에 파일이 실제로 있는지는 GitOps 체크아웃에서 확인합니다.

이 규칙들은 사람이나 AI가 공용 코드를 수정할 때 기존 동작이 유지됐는지 판단할 기준이 됩니다. 에이전트가 설정을 만드는 능력까지 평가하고 싶다면, 별도로 정상 설정과 실패 사례를 입력으로 주고 결과를 이 규칙에 대조할 수 있습니다. 현재 자동화된 테스트는 라이브러리의 동작을 검증하는 데 사용합니다.

면접에서 한 문장으로 정리하면 이렇게 말할 수 있습니다.

> Gradle 서비스가 재사용하는 Jenkins Shared Library를 만들고, 발행한 이미지 digest로 GitOps 배포 변경을 제안하도록 구현했습니다. 테스트 하네스로 발행 실패와 PR 재시도를 재현했고, 원격 Git 변경이 Argo CD를 통해 로컬 Kubernetes에 반영되는 것까지 검증했습니다.
