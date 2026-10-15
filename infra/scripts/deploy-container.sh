#!/usr/bin/env bash
# SSM이 /opt/getddo/releases/<실행 ID>/에서 root로 실행한다.
# 사용: bash deploy-container.sh <CI가 검사한 커밋 SHA> <AWS 리전>
set -Eeuo pipefail
umask 077
source_sha=${1:?커밋 SHA가 필요합니다}
region=${2:?AWS 리전이 필요합니다}
[[ $source_sha =~ ^[a-f0-9]{40}$ && $region == ap-northeast-2 ]] || exit 1
release=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
root=$(dirname -- "$(dirname -- "$release")")
app_env=/etc/getddo/app.env
env_tmp=''
env_changed=0
committed=0
had_env=0
previous=''
old_image=''
image=''

# Actions와 별개로 서버에서도 한 번에 한 배포만 허용한다.
exec 9>"$root/deploy.lock"
flock -n 9 || { echo '다른 배포가 실행 중입니다.' >&2; exit 1; }

compose() {
  local limit=$1 directory=$2 selected_image=$3 selected_env=$4
  shift 4
  # 임시 설정으로 사전 검증하고 실제 실행·복구 때는 정식 app.env를 읽는다.
  # 설정값 자체를 셸 변수로 가져오거나 source로 실행하지 않는다.
  APP_IMAGE="$selected_image" APP_ENV_FILE="$selected_env" timeout "$limit" docker compose --project-name getddo \
    --env-file /dev/null --file "$directory/compose.deploy.yaml" "$@"
}

healthy() {
  local directory=$1 selected_image=$2 container state status attempt
  local deadline=$((SECONDS + 180))
  for ((attempt = 0; attempt < 30 && SECONDS < deadline; attempt++)); do
    container=$(compose 10 "$directory" "$selected_image" "$app_env" ps -q app) || container=''
    state=''
    if [[ -n $container ]]; then
      state=$(timeout 10 docker inspect --format '{{.State.Running}} {{.Config.Image}}' "$container") || state=''
    fi
    if [[ $state == "true $selected_image" ]]; then
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

rollback() {
  # 첫 배포에는 복구할 앱이 없다. env 파일을 제거하기 전에 Compose가 설정을 읽고 중지한다.
  if [[ -z $previous ]]; then
    compose 60 "$release" "$image" "$app_env" stop app || true
  fi
  # 이미지보다 설정을 먼저 복원한다. 복원 실패 시 새 설정으로 이전 앱을 실행하지 않는다.
  if ((had_env)); then
    if ! mv -f -- "$env_tmp/previous.env" "$app_env"; then
      echo 'ROLLBACK_FAILED: 이전 설정을 복원하지 못했습니다.' >&2
      return 1
    fi
  else
    rm -f -- "$app_env" || return 1
  fi
  env_changed=0
  if [[ -n $previous ]]; then
    # 이전 Compose·이미지와 복원한 env를 함께 사용한다. 같은 이미지여도 설정을 다시 주입한다.
    if compose 120 "$previous" "$old_image" "$app_env" up -d --force-recreate --no-build --pull never app &&
       healthy "$previous" "$old_image" && record_current "$previous"; then
      echo 'ROLLBACK_OK: 이전 이미지와 설정을 복구했습니다. 이번 배포는 실패입니다.'
    else
      echo 'ROLLBACK_FAILED: 이전 앱의 정상 응답을 확인하지 못했습니다.' >&2
      return 1
    fi
  else
    if [[ -L $root/current && $(readlink -f "$root/current") == "$release" ]]; then
      unlink "$root/current"
    fi
    echo '첫 배포 실패: 이전 설정 상태를 복원하고 실패한 앱을 중지했습니다.' >&2
  fi
}

finish() {
  local status=$?
  trap - EXIT INT TERM
  # 설정 교체 이후의 오류·중단도 복구한다. 조회·검증 실패는 기존 앱을 건드리지 않는다.
  if ((env_changed && !committed)); then
    rollback || true
    ((status != 0)) || status=1
  fi
  if ((env_changed && !committed)); then
    # 설정 복원 자체가 실패한 경우에는 진단·수동 복구에 필요한 비공개 백업을 남긴다.
    echo "ROLLBACK_FAILED: 설정 백업을 보존했습니다: $env_tmp" >&2
  elif [[ -n $env_tmp ]]; then
    # 비밀값이 있는 새 임시 파일·백업만 정리한다. 배포 S3·release.tar에는 넣지 않는다.
    rm -f -- "$env_tmp/next.env" "$env_tmp/previous.env"
    rmdir -- "$env_tmp"
  fi
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# 설정 디렉터리는 초기 설치에서 준비한다. 기존 env의 링크·소유권·권한도 검사한다.
[[ -d /etc/getddo && ! -L /etc/getddo ]]
if [[ -e $app_env || -L $app_env ]]; then
  [[ -f $app_env && ! -L $app_env && $(stat -c '%a:%u' "$app_env") == "600:$EUID" ]]
fi
if [[ -L $root/current ]]; then
  previous=$(readlink -f "$root/current")
  old_image=$(cat "$previous/image-id.txt")
  [[ $old_image =~ ^sha256:[a-f0-9]{64}$ ]]
  compose 20 "$previous" "$old_image" "$app_env" config --quiet
  timeout 10 docker image inspect "$old_image" >/dev/null
elif [[ -e $root/current ]]; then
  echo 'current 경로가 배포용 링크가 아닙니다.' >&2
  exit 1
fi

cd "$release"
sha256sum --check image.tar.gz.sha256
image=$(cat image-id.txt)
[[ $image =~ ^sha256:[a-f0-9]{64}$ ]]

# app.env와 같은 파일시스템의 root 전용 임시 디렉터리를 써서 rename을 원자적으로 수행한다.
# 파라미터가 없거나 타입·형식·복호화가 틀리면 이 단계에서 실패하고 기존 앱은 유지된다.
env_tmp=$(mktemp -d /etc/getddo/.deploy-env.XXXXXX)
python3 "$release/fetch-app-env.py" --region "$region" --output "$env_tmp/next.env"
compose 20 "$release" "$image" "$env_tmp/next.env" config --quiet
if [[ -z $previous && -n $(compose 10 "$release" "$image" "$env_tmp/next.env" ps --all --quiet app) ]]; then
  echo '복구 기록이 없는 기존 앱이 있습니다. 기존 배포를 먼저 확인하세요.' >&2
  exit 1
fi
# 새 이미지까지 검증한 뒤에만 실제 설정과 컨테이너를 교체한다.
timeout 300 docker image load --input image.tar.gz
[[ $(timeout 10 docker image inspect --format '{{.Id}}' "getddo-be:$source_sha") == "$image" ]]
revision=$(timeout 10 docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")
[[ $revision == "$source_sha" ]]
if [[ -f $app_env ]]; then
  install -m 0600 "$app_env" "$env_tmp/previous.env"
  had_env=1
fi
# 플래그를 먼저 설정해 rename 직후 중단돼도 EXIT에서 이전 상태를 복구한다.
env_changed=1
mv -f -- "$env_tmp/next.env" "$app_env"

# 짧은 중단을 동반하는 교체다. 환경변수 변경도 반영되도록 강제 재생성한다.
if compose 120 "$release" "$image" "$app_env" up -d --force-recreate --no-build --pull never app &&
   healthy "$release" "$image" && record_current "$release"; then
  committed=1
  echo "DEPLOY_OK: $source_sha $image"
else
  exit 1
fi
