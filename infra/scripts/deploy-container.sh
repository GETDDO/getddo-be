#!/usr/bin/env bash
# SSM이 /opt/getddo/releases/<실행 ID>/에서 실행한다.
# 사용: bash deploy-container.sh <CI가 검사한 커밋 SHA>
set -Eeuo pipefail
umask 077
source_sha=${1:?커밋 SHA가 필요합니다}
[[ $source_sha =~ ^[a-f0-9]{40}$ ]] || exit 1
release=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
root=$(dirname -- "$(dirname -- "$release")")

# Actions 실행과 별개로 서버에서도 한 번에 한 배포만 허용한다.
exec 9>"$root/deploy.lock"
flock -n 9 || { echo '다른 배포가 실행 중입니다.' >&2; exit 1; }

compose() {
  local limit=$1 directory=$2 image=$3
  shift 3
  APP_IMAGE="$image" timeout "$limit" docker compose --project-name getddo \
    --env-file /dev/null --file "$directory/compose.deploy.yaml" "$@"
}

healthy() {
  local directory=$1 image=$2 container state status attempt
  local deadline=$((SECONDS + 180))
  for ((attempt = 0; attempt < 30 && SECONDS < deadline; attempt++)); do
    container=$(compose 10 "$directory" "$image" ps -q app) || container=''
    state=''
    if [[ -n $container ]]; then
      state=$(timeout 10 docker inspect --format '{{.State.Running}} {{.Config.Image}}' "$container") || state=''
    fi
    if [[ $state == "true $image" ]]; then
      # 기존 HTTP API로 기동을 확인한다. DB·업무 기능 전체 검증은 아니다.
      status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
        --max-time 5 http://127.0.0.1:8080/v3/api-docs) || status=''
      [[ $status == 200 ]] && return 0
    fi
    sleep 4
  done
  return 1
}

record_current() {
  ln -sfn "$1" "$root/current.next"
  mv -Tf "$root/current.next" "$root/current"
}

previous=''
old_image=''
if [[ -L $root/current ]]; then
  previous=$(readlink -f "$root/current")
  old_image=$(cat "$previous/image-id.txt")
  [[ $old_image =~ ^sha256:[a-f0-9]{64}$ ]]
  compose 20 "$previous" "$old_image" config --quiet
  timeout 10 docker image inspect "$old_image" >/dev/null
elif [[ -e $root/current ]]; then
  echo 'current 경로가 배포용 링크가 아닙니다.' >&2
  exit 1
fi

# 교체 전에 파일·이미지·Compose 설정을 검증한다. 실패해도 기존 앱은 유지된다.
cd "$release"
sha256sum --check image.tar.gz.sha256
image=$(cat image-id.txt)
[[ $image =~ ^sha256:[a-f0-9]{64}$ ]]
compose 20 "$release" "$image" config --quiet
if [[ -z $previous && -n $(compose 10 "$release" "$image" ps --all --quiet app) ]]; then
  echo '복구 기록이 없는 기존 앱이 있습니다. 기존 배포를 먼저 확인하세요.' >&2
  exit 1
fi
timeout 300 docker image load --input image.tar.gz
[[ $(timeout 10 docker image inspect --format '{{.Id}}' "getddo-be:$source_sha") == "$image" ]]
revision=$(timeout 10 docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")
[[ $revision == "$source_sha" ]]

rollback() {
  if [[ -n $previous ]]; then
    # 이전 Compose와 이미지 ID를 함께 사용한다. 이미지는 로컬에 유지한다.
    if compose 120 "$previous" "$old_image" up -d --no-build --pull never app &&
       healthy "$previous" "$old_image" && record_current "$previous"; then
      echo 'ROLLBACK_OK: 이전 앱을 복구했습니다. 이번 배포는 실패입니다.'
    else
      echo 'ROLLBACK_FAILED: 이전 앱의 정상 응답을 확인하지 못했습니다.' >&2
      return 1
    fi
  else
    compose 60 "$release" "$image" stop app || true
    if [[ -L $root/current && $(readlink -f "$root/current") == "$release" ]]; then
      unlink "$root/current"
    fi
    echo '첫 배포 실패: 복구 대상이 없어 실패한 앱을 중지했습니다.' >&2
  fi
}

trap 'trap - INT TERM; rollback; exit 130' INT TERM
# 이미지 ID로 고정해 기존 앱을 교체한다. 이때 짧은 접속 중단이 생긴다.
if compose 120 "$release" "$image" up -d --no-build --pull never app &&
   healthy "$release" "$image" && record_current "$release"; then
  trap - INT TERM
  echo "DEPLOY_OK: $source_sha $image"
else
  trap - INT TERM
  rollback || true
  exit 1
fi
