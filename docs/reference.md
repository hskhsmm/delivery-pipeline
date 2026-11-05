# deploy와 servicePipeline 레퍼런스

## deploy(options = [:])

서비스를 한 번 체크아웃하고 YAML을 읽은 뒤 공통 Gradle 빌드를 실행합니다.

```groovy
deploy(
    agentLabel: 'java21 && linux',
    configFile: '.ci/jenkins.yml',
    checkoutTimeoutMinutes: 10
)
```

| 호출 옵션 | 타입 | 기본값 | 규칙 |
| --- | --- | --- | --- |
| `agentLabel` | String | `java` | 비어 있지 않은 Jenkins 라벨 표현식 |
| `configFile` | String | `jenkins.yml` | 저장소 상대 경로. 빈 경로와 `.`·`..` 경로 요소는 거부 |
| `checkoutTimeoutMinutes` | Integer 또는 Long | `10` | 1–120분. Checkout과 Configure에 적용 |

호출 옵션은 에이전트 할당 전에 검증합니다.
YAML은 체크아웃한 워크스페이스에서 읽으므로 에이전트 라벨은 호출 옵션에서 지정합니다.
에이전트 대기 시간은 timeout에 포함하지 않습니다.

### jenkins.yml

```yaml
serviceName: orders-api
timeoutMinutes: 30
buildEnv:
  JAVA_TOOL_OPTIONS: '-Xmx1g'
  CI: 'true'
  PORT: '8080'
```

| YAML 설정 | 타입 | 기본값 | 규칙 |
| --- | --- | --- | --- |
| `serviceName` | String | 필수 | 소문자 영숫자·하이픈, 1–63자, 시작과 끝은 영숫자 |
| `timeoutMinutes` | Integer 또는 Long | `30` | 1–120분. Configure 이후 Verify에 적용 |
| `buildEnv` | Map<String, String> | 빈 Map | 환경변수 이름은 영문자·숫자·밑줄, 첫 글자는 영문자 또는 밑줄 |

환경변수 값은 문자열만 받습니다. YAML의 숫자와 boolean은 인용하고, `timeoutMinutes`는 숫자로 씁니다.
`buildEnv`에는 빈 문자열도 허용하지만 null과 NUL 문자는 거부합니다.
루트가 빈 값·리스트·스칼라인 YAML, 여러 문서, 알 수 없는 키와 YAML 안의 `agentLabel`도 거부합니다.
현재 `image`, `gitops`, `argocd` 등의 배포 설정은 지원하지 않습니다.

### 단계와 실패 처리

1. `Checkout`: 워크스페이스를 정리하고 서비스 잡의 `scm`을 체크아웃합니다.
2. `Configure`: `fileExists` 확인 후 `readYaml`로 읽고 서비스 설정을 검증합니다.
3. `Verify`: `withEnv` 범위에서 `./gradlew --no-daemon build`를 실행합니다.

`readYaml`은 Pipeline Utility Steps 플러그인 스텝입니다. 파서의 code-point 제한은 65,536,
컬렉션 별칭 제한은 20으로 전달합니다. Jenkins 관리자가 더 낮은 상한을 설정했다면 실행 오류가 날 수 있습니다.
누락된 파일, YAML 파싱·설정 오류는 Gradle 실행 전에 실패하고, 체크아웃·빌드 오류는 그대로 전파합니다.
성공 시 별도 반환값 계약은 없습니다.

`buildEnv`는 Verify의 프로세스에만 적용하며 Kubernetes 애플리케이션의 환경변수를 설정하지 않습니다.
라이브러리는 값을 직접 로그로 출력하지 않지만 Gradle·JVM·테스트가 값을 출력할 수 있습니다.
일반 빌드 옵션에 사용하며 비밀값용 Credentials 연결과 마스킹은 현재 구현 범위에 포함하지 않습니다.

## servicePipeline(options)

Map을 직접 전달하던 기존 방식도 유지합니다. YAML 플러그인은 필요하지 않습니다.

```groovy
servicePipeline(
    serviceName: 'my-api',
    agentLabel: 'java21 && linux',
    timeoutMinutes: 45,
    buildEnv: [JAVA_TOOL_OPTIONS: '-Xmx1g']
)
```

설정은 YAML의 `serviceName`, `timeoutMinutes`, `buildEnv`와 호출 옵션의 `agentLabel`을 합친 형태입니다.
기존 호출에서는 모든 설정을 에이전트 할당 전에 검증하고, 단일 timeout이 Checkout과 Verify를 감쌉니다.
실제 Verify는 `deploy()`와 동일한 `GradleBuild`를 사용합니다.
