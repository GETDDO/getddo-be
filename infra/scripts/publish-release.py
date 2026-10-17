#!/usr/bin/env python3
"""성공한 서버 배포 커밋에 버전 태그와 GitHub Release를 발행한다."""
import argparse
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import sys

# 같은 릴리스 자동화의 인증 정보 비출력 API 클라이언트를 재사용한다.
spec = importlib.util.spec_from_file_location('daily_release_api', Path(__file__).with_name('daily-release.py'))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
VERSION = re.compile(r'v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)')


def version(tag):
    match = VERSION.fullmatch(tag)
    return tuple(map(int, match.groups())) if match else None


def tag_commit(github, ref):
    """일반 태그와 주석 태그의 실제 커밋을 확인한다."""
    obj = ref['object']
    for _ in range(10):
        if obj['type'] == 'commit':
            return obj['sha']
        if obj['type'] != 'tag':
            break
        obj = github.api(f"git/tags/{obj['sha']}")['object']
    raise api.ReleaseError('버전 태그의 커밋을 확인하지 못했습니다.')


def publish(github, sha, dry_run=False):
    """첫 v0.1.0 이후 패치를 증가시키며 같은 커밋의 재실행은 재사용한다."""
    if not re.fullmatch(r'[a-f0-9]{40}', sha):
        raise api.ReleaseError('실제 배포한 SOURCE_SHA가 필요합니다.')
    refs = [ref for ref in github.api('git/matching-refs/tags/v')
            if version(ref['ref'][len('refs/tags/'):]) is not None]
    releases = []
    page = 1
    while True:
        batch = github.api(f'releases?per_page=100&page={page}')
        releases.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    names = {ref['ref'][len('refs/tags/'):] for ref in refs}
    names.update(r['tag_name'] for r in releases if version(r['tag_name']) is not None)
    same_commit = [ref['ref'][len('refs/tags/'):] for ref in refs if tag_commit(github, ref) == sha]
    if same_commit:
        tag = max(same_commit, key=version)
        existing = next((r for r in releases if r['tag_name'] == tag), None)
        if existing:
            if existing['draft'] or existing['prerelease']:
                raise api.ReleaseError('같은 커밋의 초안·사전 릴리스는 수동 확인이 필요합니다.')
            return f'RELEASE_EXISTS: {tag}, {sha}'
    elif names:
        major, minor, patch = max(map(version, names))
        tag = f'v{major}.{minor}.{patch + 1}'
    else:
        tag = 'v0.1.0'
    if dry_run:
        return f'RELEASE_VERSION_DRY_RUN: {tag}, {sha}. 태그·Release를 생성하지 않았습니다.'
    if not same_commit:
        # 태그를 먼저 원자적으로 생성한다. 동명 태그가 있으면 API가 거절하므로 다른 커밋을 릴리스하지 않는다.
        created = github.api('git/refs', 'POST', {'ref': f'refs/tags/{tag}', 'sha': sha})
        if tag_commit(github, created) != sha:
            raise api.ReleaseError('새 태그와 배포 커밋이 다릅니다.')
    # 태그 생성 이후 발행 실패 시 재실행은 같은 태그를 사용한다. 기존 태그를 이동하거나 삭제하지 않는다.
    published = github.api('releases', 'POST', {
        'tag_name': tag, 'target_commitish': sha, 'name': tag,
        'body': f'서버 배포 성공 커밋: `{sha}`\n',
        'draft': False, 'prerelease': False, 'generate_release_notes': True,
        'make_latest': 'true',
    })
    if published['tag_name'] != tag or published['draft'] or published['prerelease']:
        raise api.ReleaseError('발행된 Release 상태를 확인하세요.')
    return f'RELEASE_PUBLISHED: {tag}, {sha}'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true', help='다음 버전만 확인하고 태그·Release를 생성하지 않음')
    args = parser.parse_args()
    try:
        result = publish(api.GitHub(os.environ.get('GITHUB_REPOSITORY', '')),
                         os.environ.get('SOURCE_SHA', ''), args.dry_run)
    except (api.ReleaseError, KeyError, ValueError, TypeError, subprocess.SubprocessError):
        print('RELEASE_PUBLISH_FAILED: 태그·Release 상태와 Contents 쓰기 권한을 확인하세요. 서버 배포는 이미 성공했습니다.', file=sys.stderr)
        return 1
    print(result)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as summary:
            summary.write(result + '\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
