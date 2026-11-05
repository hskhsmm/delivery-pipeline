# Jenkins에 연결하기

## 준비

- Jenkins Pipeline과 Git 체크아웃이 가능한 Jenkins 환경
- `readYaml`을 제공하는 **Pipeline Utility Steps** 플러그인
- 서비스의 JDK와 POSIX 셸을 사용할 수 있는 에이전트. 기본 라벨은 `java`
- 서비스 저장소에 커밋된 실행 가능한 Gradle Wrapper와 테스트

라이브러리 자체 테스트에 사용하는 JDK 21과 서비스 에이전트의 JDK는 별개입니다.
서비스가 Java 17을 사용하면 해당 JDK를 갖춘 에이전트를 선택합니다.
`readYaml` 스텝은 [공식 레퍼런스](https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#readyaml-read-yaml-from-a-file-in-the-workspace-or-text)를 참고합니다.

## 공유 라이브러리 등록

Jenkins의 라이브러리 설정에 다음을 등록합니다. 전체 Jenkins에 적용할 필요가 없다면
폴더 범위 라이브러리로 연결할 수 있습니다.

| 항목 | 값 |
| --- | --- |
| Name | `delivery-pipeline` |
| Default version | `main` |
| Retrieval method | Modern SCM → Git |
| Project repository | `https://github.com/hskhsmm/delivery-pipeline.git` |

프로젝트에서 버전을 지정할 수 있도록 Allow default version to be overridden 설정을 확인합니다.
운영 중인 서비스는 검증한 커밋 SHA 또는 릴리스 태그를 명시합니다.
공유 라이브러리 설정은 [Jenkins 공식 가이드](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)를 참고합니다.

## 서비스 연결

1. `examples/gradle/Jenkinsfile`과 `examples/gradle/jenkins.yml`을 서비스 저장소 루트에 복사합니다.
2. YAML의 `serviceName`, 필요하면 `timeoutMinutes`와 `buildEnv`를 수정합니다.
3. 다른 에이전트가 필요하면 Jenkinsfile에서 `deploy(agentLabel: 'java21')`처럼 지정합니다.
4. Pipeline from SCM 또는 Multibranch Pipeline이 해당 서비스 저장소를 읽도록 등록합니다.
5. 실행 결과에서 `Checkout` → `Configure` → `Verify`와 서비스 테스트 결과를 확인합니다.

YAML을 읽으려면 에이전트와 체크아웃이 먼저 필요하므로 `agentLabel`을 YAML에 넣으면 실패합니다.
파일 위치가 다르면 `deploy(configFile: '.ci/jenkins.yml')`로 지정합니다.
한 번 체크아웃한 동일 워크스페이스에서 설정 읽기와 빌드를 수행합니다.

워크스페이스는 Checkout 단계에서 정리합니다. Jenkins 잡의 작업 공간을 사용해야 합니다.
`gradlew`의 실행 권한과 `gradle/wrapper` 파일이 서비스 저장소에 있는지 확인합니다.

## 설정 오류 확인

- `No such DSL method readYaml`: Pipeline Utility Steps 플러그인을 확인합니다.
- `Required configuration file not found`: 체크아웃한 서비스 저장소의 파일 경로를 확인합니다.
- `Unknown configuration key`: [지원하는 설정](reference.md)과 철자를 확인합니다.
- `buildEnv.* must be a string`: 숫자와 boolean 값을 따옴표로 감쌉니다.

현재 공통 명령은 `./gradlew --no-daemon build`입니다. 특정 모듈이나 별도 태스크를 선택하는 옵션은 아직 없습니다.
연결 완료 기준은 실제 서비스의 Gradle 빌드 성공이며, 이 저장소의 단위 테스트가 실제 Jenkins 실행을 대신하지는 않습니다.
빌드가 통과하면 [이미지 발행 가이드](image-publishing.md)대로 Docker Hub를 연결할 수 있습니다.
