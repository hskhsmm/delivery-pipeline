#!/bin/sh
set +x
set -eu

# 인증 파일을 워크스페이스와 Docker 빌드 컨텍스트 밖에 생성한다.
umask 077
publish_dir=$(mktemp -d /tmp/delivery-image.XXXXXXXX)
trap 'rm -rf "$publish_dir"' 0
trap 'exit 1' HUP INT TERM
export DOCKER_CONFIG="$publish_dir"

# 임시 설정에 Docker Hub 인증만 넣어 공유 에이전트의 로그인 상태를 건드리지 않는다.
publish_auth=$(printf '%s:%s' "$DELIVERY_HUB_USER" "$DELIVERY_HUB_TOKEN" | base64)
publish_auth=$(printf '%s' "$publish_auth" | tr -d '\r\n')
printf '{"auths":{"https://index.docker.io/v1/":{"auth":"%s"}}}\n' "$publish_auth" > "$DOCKER_CONFIG/config.json"
unset publish_auth DELIVERY_HUB_USER DELIVERY_HUB_TOKEN

# Jenkins가 stdout으로 메타데이터만 받도록 빌드 진행 로그는 stderr에 남긴다.
docker buildx build --push --tag "$DELIVERY_IMAGE_TAG" \
    --label "org.opencontainers.image.revision=$DELIVERY_IMAGE_REVISION" \
    --metadata-file "$publish_dir/metadata.json" . >&2
cat "$publish_dir/metadata.json"
