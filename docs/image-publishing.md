# Docker Hub 이미지 발행

`deploy()`는 Gradle의 Verify가 성공한 뒤 `image` 설정이 있는 서비스의 이미지를 발행합니다.
서비스 저장소의 루트 `Dockerfile`과 빌드 컨텍스트를 사용합니다. Java와 Kotlin의 패키징 차이는 각 서비스의 Gradle 설정과 Dockerfile에서 처리합니다.

## 준비

- [Jenkins 연결](getting-started.md)을 완료하고 Git 체크아웃, JDK, Docker Engine, Docker CLI와 Buildx를 사용할 수 있는 에이전트를 선택합니다.
- 발행 스크립트는 임시 Docker 설정을 사용하므로 Buildx 플러그인은 에이전트의 공용 CLI 플러그인 경로에 설치합니다. Docker는 기본 소켓 또는 `DOCKER_HOST`로 연결합니다.
- Jenkins의 Credentials Binding 플러그인에서 Docker Hub 사용자명과 push 권한이 있는 토큰을 **Username with password**로 등록합니다. YAML에는 해당 자격증명 ID를 지정합니다.
- Docker Hub에 발행 대상 저장소를 준비합니다.

```yaml
serviceName: delivery-demo
image:
  repository: your-namespace/delivery-demo
  credentialsId: dockerhub-publisher
```

기존 Jenkinsfile에서 `deploy()`를 호출합니다. 에이전트 라벨이 다르면 `deploy(agentLabel: 'java21-docker')`처럼 지정합니다.
발행은 최대 30분이며, Git 체크아웃의 커밋 SHA를 `sha-<commit>` 태그와 OCI 이미지 revision 라벨에 사용합니다.
인증은 발행 셸에서 사용하고 종료 시 임시 Docker 설정을 지웁니다.

## 작은 Java 샘플로 검증

1. `examples/gradle/`의 파일을 별도 서비스 저장소 루트에 복사합니다. `.dockerignore`도 포함합니다.
2. 라이브러리 저장소의 `gradlew`, `gradlew.bat`, `gradle/wrapper/`를 샘플 저장소에 복사하고 `gradlew` 실행 권한을 유지합니다.
3. 샘플의 `jenkins.yml`에서 `image` 주석을 풀고 본인의 Docker Hub 저장소와 Credentials ID를 설정합니다.
4. Jenkins에서 서비스 잡을 실행하고 `Verify`와 `Publish Image`가 성공하는지 확인합니다.
5. Jenkins 아티팩트 `published-image.json`을 내려받습니다. `image`는 `docker.io/namespace/repository@sha256:...` 형식이며, `sourceRevision`과 `tag`도 함께 보관합니다.
6. pull 권한이 있는 환경에서 `image` 값을 사용해 다시 받아 실행합니다.

```sh
image_ref=$(jq -er .image published-image.json)
docker pull "$image_ref"
docker run --rm -p 127.0.0.1:8080:8080 "$image_ref"
```

다른 터미널에서 `curl -fsS http://127.0.0.1:8080/health`가 `ok`를 반환하면 검증이 끝납니다.
다음 GitOps 단계에서도 이 아티팩트의 `image` 값을 배포 설정에 사용합니다.

샘플의 JAR만 확인하려면 라이브러리 저장소 루트에서 `./gradlew -p examples/gradle --no-daemon build`를 실행합니다.
