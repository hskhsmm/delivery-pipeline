# 기여 가이드

## 검증

JDK 21에서 다음 명령을 실행합니다. 로컬과 CI에서 같은 Gradle Wrapper를 사용합니다.

```sh
./gradlew --no-daemon build
git diff --check
```

설정 옵션을 바꿀 때는 `ServiceConfig`·`DeployOptions`, 테스트, `docs/reference.md`와 YAML 예제를 함께 수정합니다.
공개 진입점의 조합은 `vars/`에, 설정 검증과 공통 Verify 구현은 `src/`에 유지합니다.
Groovy 2.4와 Jenkins CPS에서 동작하는 문법을 사용합니다.
실제 Jenkins에서의 동작 확인은 단위 테스트와 별도로 기록합니다.
테스트 하네스는 Jenkins 실행을 대체하며 실제 YAML 파싱은 test 의존성인 SnakeYAML로 확인합니다.
진입점 변경 시 누락·잘못된 YAML과 빌드 실패 후 환경 복구를 함께 검증합니다.
주석은 체크아웃 순서, timeout 범위, CPS 상태와 환경변수 수명처럼 구현 이유가 필요한 곳에 작성합니다.

의존성을 변경하면 `./gradlew --no-daemon build --write-locks`로 lockfile을 갱신하고
`build.gradle`과 `gradle.lockfile`을 같은 커밋에 포함합니다.

## 커밋

의미 있는 변경 단위로 나누고 메시지 본문은 한국어로 작성합니다.

```text
feat: 컨테이너 이미지 발행 단계 추가
fix: 테스트 실패 시 배포 설정 변경 방지
test: 레지스트리 장애 시나리오 추가
ci: 라이브러리 검증 자동화 추가
docs: 서비스 연결 가이드 보완
```

아직 배포 연동을 완료한 상태가 아니므로 문서에서 구현된 동작과 목표를 구분합니다.
