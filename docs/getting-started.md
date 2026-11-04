# Jenkins에 연결하기

## 준비

- Jenkins Pipeline과 Git 체크아웃이 가능한 Jenkins 환경
- Java 서비스의 JDK가 설치된 에이전트. 기본 라벨은 `java`이며 다른 라벨을 지정할 수 있습니다.
- 서비스 저장소에 커밋된 Gradle Wrapper와 테스트

라이브러리 자체 테스트에 사용하는 JDK 21과 서비스 에이전트의 JDK는 별개입니다.
서비스가 Java 17을 사용하면 해당 JDK를 갖춘 에이전트를 선택합니다.

## 공유 라이브러리 등록

Jenkins의 라이브러리 설정에서 다음을 등록합니다. 전체 Jenkins에 적용할 필요가 없다면
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

1. `examples/gradle/Jenkinsfile`을 서비스 저장소에 복사합니다.
2. `serviceName`, `agentLabel`, 필요하면 `timeoutMinutes`를 수정합니다.
3. Jenkins Pipeline from SCM 또는 Multibranch Pipeline이 해당 서비스 저장소를 읽도록 등록합니다.
4. 실행 결과에서 `Checkout`, `Verify`와 서비스 테스트 결과를 확인합니다.

워크스페이스는 Checkout 단계에서 정리합니다. Jenkins 잡의 작업 공간을 사용해야 합니다.
`gradlew`가 실행 가능하게 커밋됐는지도 확인합니다.

현재 공통 명령은 `./gradlew --no-daemon build`입니다.
특정 모듈만 선택하거나 별도 빌드 태스크를 지정하는 설정은 아직 지원하지 않으며,
실제 요구를 확인한 뒤 공개 설정으로 추가할 계획입니다.

지금은 빌드 성공까지 연결됩니다. 레지스트리 인증, 이미지 발행,
GitOps 저장소와 Argo CD 연결은 다음 단계입니다.
