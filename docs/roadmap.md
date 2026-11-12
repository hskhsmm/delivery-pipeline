# 로드맵

## 1차: 공통 라이브러리 기반

- [x] Jenkins Shared Library 표준 디렉토리와 공통 진입점
- [x] Gradle 서비스의 Checkout / Verify
- [x] 설정 검증, 실패 전파, 단위 테스트
- [x] Gradle Wrapper와 GitHub Actions CI
- [x] `deploy()`가 서비스 저장소의 `jenkins.yml`을 읽고 Gradle Verify 실행
- [x] YAML 검증, 빌드 환경변수 범위, 파싱 오류·실패 전파 테스트
- [x] 서비스별 Jenkinsfile·jenkins.yml 예제와 API 문서
- [x] 실제 Jenkins와 Pipeline Utility Steps 플러그인에서 YAML 기반 Gradle 서비스 실행

첫 실제 사용 결과를 기준으로 에이전트 라벨과 빌드 옵션의 기본값을 조정합니다.

## 2차: 컨테이너 이미지 발행

- [x] Dockerfile 기반 Docker Hub 이미지 발행
- [x] 커밋 SHA와 발행된 이미지 digest를 연결한 아티팩트
- [x] 서비스별 Docker Hub 저장소·Jenkins 자격증명 설정
- [x] 검증 실패 시 이미지 발행을 건너뛰고 발행 실패 시 아티팩트를 남기지 않는 테스트
- [x] 실제 Jenkins에서 Java 샘플 발행 후 digest로 pull·실행 및 `/health` 응답 확인
- [x] 실제 발행에 사용한 경량 Dockerfile과 실행 절차를 별도 Java 테스트 서비스에 반영
- [x] Jenkins가 GitHub의 테스트 서비스와 공유 라이브러리를 직접 체크아웃하고 새 이미지 발행 및 digest 기록 검증
- [ ] `amd64` 환경에서 이미지 빌드·실행 검증
- [ ] Kotlin 서비스에서 같은 호출 방식으로 재사용 검증

완료 기준: sandbox에 사용할 실제 이미지를 발행하고 digest로 다시 지정할 수 있습니다.

## 3차: GitOps와 첫 배포

- [x] 별도 로컬 kind 클러스터 `delivery-sandbox` 구성 및 Argo CD 설치
- [x] 로컬 GitOps 저장소에 한 서비스의 `Deployment`와 `Service`를 일반 YAML로 작성
- [x] 발행된 digest를 Git에 직접 커밋하고 Argo CD의 `Synced`·`Healthy` 및 Service의 `/health` 확인
- [x] GitHub에서 직접 체크아웃해 발행한 이미지로 샌드박스의 기존 배포 갱신 검증
- [x] Argo CD를 원격 GitOps 저장소에 연결하고 새 이미지 digest 반영 검증
- [x] sandbox 이미지 digest 변경 PR 단계와 동일 요청의 열린 PR 재사용 구현 및 테스트
- [ ] 실제 Jenkins에서 배포 설정 PR 생성 → 병합 → Argo CD 자동 동기화 검증

완료 기준: 실제 서비스 하나를 이 라이브러리의 빌드 산출물로 sandbox에 배포합니다.

## 이후: 운영 경험을 바탕으로 확장

- [ ] 환경별 설정 차이가 늘어나면 Kustomize 도입 검토
- [ ] 같은 digest의 beta / production 승격과 승인 지점
- [ ] Git 변경으로 이전 버전 복구, 복구 소요 시간 측정
- [ ] Argo Rollouts를 통한 카나리와 분석 기반 중단
- [ ] 장애·재시도 시나리오를 검증하는 하네스 확장
- [ ] 필요가 확인되면 서비스 등록 CLI와 구조화된 실행 결과 제공

현재 측정한 배포 속도, 실패율 개선, 운영 적용 서비스 수는 없습니다.
실제 적용 전후의 설정량, 서비스 연결 시간과 실패 사례를 기록해 개선 방향을 정합니다.
