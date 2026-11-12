/** 기존 Map 호출을 공통 파이프라인으로 전달한다. */
def call(Map options = [:]) {
    deploy.runLegacy(options)
}
