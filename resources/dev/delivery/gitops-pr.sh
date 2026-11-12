#!/bin/sh
set +x
set -eu

fail() {
    printf '%s\n' "$1" >&2
    exit 1
}

command -v gh >/dev/null 2>&1 || fail 'GitOps PRs require GitHub CLI (gh) on the Jenkins agent'
export GH_NO_UPDATE_NOTIFIER=1
repository="github.com/$DELIVERY_GITOPS_REPO"

github_git() {
    # GH_TOKEN은 Jenkins가 셸 범위에 주입하며 Git 설정 파일이나 원격 URL에 기록하지 않는다.
    git -c credential.helper= -c 'credential.helper=!gh auth git-credential' "$@"
}

case "$DELIVERY_GITOPS_ACTION" in
    checkout)
        github_git clone --template= --depth 1 --single-branch --branch main --no-tags -- \
            "https://$repository.git" .
        mode=$(git ls-files --stage -- "$DELIVERY_GITOPS_MANIFEST" | cut -d ' ' -f 1)
        case "$mode" in
            100644|100755) ;;
            *) fail 'GitOps manifest must be a tracked regular file' ;;
        esac
        exit 0
        ;;
    open) ;;
    *) fail 'Unknown GitOps operation' ;;
esac

# 같은 서비스, 파일, 이미지 조합은 매번 같은 PR 브랜치를 사용한다.
branch_key=$(printf '%s\n' "$DELIVERY_GITOPS_MANIFEST" "$DELIVERY_GITOPS_SERVICE" "$DELIVERY_GITOPS_IMAGE" |
    git hash-object --stdin)
branch="delivery/$DELIVERY_GITOPS_SERVICE/$branch_key"

existing_pr() {
    gh api --method GET "repos/$DELIVERY_GITOPS_REPO/pulls" -f base=main \
        -f "head=${DELIVERY_GITOPS_REPO%%/*}:$branch" -f state=all -F per_page=1 \
        --jq '.[0] // empty | if .state == "open" then "OPEN \(.html_url)" else "CLOSED \(.html_url)" end'
}

use_existing() {
    case "$1" in
        'OPEN '*) printf 'Reusing GitOps pull request: %s\n' "${1#OPEN }" ;;
        *) fail "GitOps pull request already completed: $1. Review it before retrying." ;;
    esac
}

existing=$(existing_pr)
if [ -n "$existing" ]; then
    use_existing "$existing"
    exit 0
fi

git -c user.name='Delivery Pipeline' -c user.email='delivery-pipeline@users.noreply.github.com' \
    -c core.hooksPath=/dev/null -c commit.gpgSign=false commit \
    -m "deploy: update $DELIVERY_GITOPS_SERVICE image" -- "$DELIVERY_GITOPS_MANIFEST"

if ! github_git push origin "HEAD:refs/heads/$branch"; then
    # 동시에 실행했거나 이전 실행이 push 직후 중단됐다면, 같은 변경의 브랜치인지 확인한다.
    github_git fetch --depth 1 origin "refs/heads/$branch"
    [ "$(git rev-parse 'HEAD^{tree}')" = "$(git rev-parse 'FETCH_HEAD^{tree}')" ] ||
        fail 'Existing GitOps branch contains a different change'
fi

if ! gh api --method POST "repos/$DELIVERY_GITOPS_REPO/pulls" -f base=main -f "head=$branch" \
    -f "title=Deploy $DELIVERY_GITOPS_SERVICE" \
    -f "body=Image: $DELIVERY_GITOPS_IMAGE

Source revision: $DELIVERY_GITOPS_REVISION" --jq .html_url; then
    existing=$(existing_pr)
    [ -n "$existing" ] || fail 'Could not create GitOps pull request'
    use_existing "$existing"
fi
