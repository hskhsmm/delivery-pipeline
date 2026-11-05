// 처음 연결할 때는 main을 사용하고, 실제 서비스에는 검증한 태그나 커밋을 고정한다.
@Library('delivery-pipeline@main') _

// 서비스 설정은 같은 저장소 루트의 jenkins.yml에서 읽는다.
// 다른 라벨이 필요하면 deploy(agentLabel: 'java21')로 에이전트를 먼저 선택한다.
deploy()
