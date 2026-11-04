# servicePipeline 레퍼런스

```groovy
servicePipeline(
    serviceName: 'my-api',
    agentLabel: 'java21 && linux',
    timeoutMinutes: 45
)
```

| 설정 | 타입 | 기본값 | 규칙 |
| --- | --- | --- | --- |
| `serviceName` | String | 필수 | 소문자 영숫자·하이픈, 1–63자, 시작과 끝은 영숫자 |
| `agentLabel` | String | `java` | 비어 있지 않은 Jenkins 라벨 표현식 |
| `timeoutMinutes` | Integer 또는 Long | `30` | 1–120분 |

알 수 없는 옵션, 잘못된 타입과 값은 `IllegalArgumentException`으로 실패합니다.
입력 검증은 `node` 호출 전이며 잘못된 설정으로 에이전트를 할당하지 않습니다.

## 단계

1. `Checkout`: 기존 워크스페이스를 지우고 서비스 잡의 `scm`을 체크아웃합니다.
2. `Verify`: 서비스 저장소의 Gradle Wrapper로 `./gradlew --no-daemon build`를 실행합니다.

`timeoutMinutes`는 에이전트를 할당한 뒤 Checkout과 Verify에 적용합니다.
에이전트 대기 시간은 포함하지 않습니다. 잡 전체 대기 제한은 Jenkins 잡 설정에서 관리합니다.
성공 시 별도 반환값 계약은 없으며, 체크아웃과 빌드 오류는 Jenkins에 그대로 전파합니다.
테스트 결과 게시, 빌드 산출물 보관, 이미지 발행은 아직 공개 API에 포함하지 않습니다.
