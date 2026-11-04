# 로드맵

## 1차: 공통 라이브러리 기반

- [x] Jenkins Shared Library 표준 디렉토리와 공통 진입점
- [x] Gradle 서비스의 Checkout / Verify
- [x] 설정 검증, 실패 전파, 단위 테스트
- [x] Gradle Wrapper와 GitHub Actions CI
- [x] 서비스별 Jenkinsfile 예제와 API 문서
- [ ] 실제 Jenkins에서 Gradle 서비스 실행

첫 실제 사용 결과를 기준으로 에이전트 라벨과 빌드 옵션의 기본값을 조정합니다.

## 2차: 컨테이너 이미지 발행

- [ ] Dockerfile 기반 빌드와 레지스트리 push
- [ ] 커밋 SHA와 발행된 이미지 digest를 연결한 release 메타데이터
- [ ] 서비스별 레지스트리·자격증명 설정
- [ ] 테스트 실패 시 이미지 발행이 실행되지 않는 검증

완료 기준: sandbox에 사용할 실제 이미지를 발행하고 digest로 다시 지정할 수 있습니다.

## 3차: GitOps와 첫 배포

- [ ] 한 서비스용 Helm 차트와 환경별 values 예제
- [ ] sandbox의 이미지 digest 변경 PR 생성
- [ ] 동일 요청 재시도 시 중복 PR 방지
- [ ] 기존 Argo CD와 연결해 PR merge → 동기화 → Healthy 확인

완료 기준: 실제 서비스 하나를 이 라이브러리의 빌드 산출물로 sandbox에 배포합니다.

## 이후: 운영 경험을 바탕으로 확장

- [ ] 같은 digest의 beta / production 승격과 승인 지점
- [ ] Git 변경으로 이전 버전 복구, 복구 소요 시간 측정
- [ ] Argo Rollouts를 통한 카나리와 분석 기반 중단
- [ ] 장애·재시도 시나리오를 검증하는 하네스 확장
- [ ] 필요가 확인되면 서비스 등록 CLI와 구조화된 실행 결과 제공

현재 측정한 배포 속도, 실패율 개선, 운영 적용 서비스 수는 없습니다.
실제 적용 전후의 설정량, 서비스 연결 시간과 실패 사례를 기록해 개선 방향을 정합니다.
