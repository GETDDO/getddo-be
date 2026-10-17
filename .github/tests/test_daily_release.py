"""자동 병합의 CI 조건·브랜치 변경·충돌·실패 경로를 대역으로 검증한다."""
import copy
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

SOURCE = Path(__file__).resolve().parents[2] / 'infra/scripts/daily-release.py'
spec = importlib.util.spec_from_file_location('daily_release', SOURCE)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
DEV, MAIN = 'a' * 40, 'b' * 40
RUN = {'id': 7, 'head_sha': DEV, 'head_branch': 'dev', 'event': 'push',
       'status': 'completed', 'conclusion': 'success', 'html_url': 'https://github.com/test/run/7'}
JOBS = [{'name': name, 'status': 'completed', 'conclusion': 'success'}
        for name in ['빌드·테스트', '이미지 / Docker 이미지']]
PULL = {'number': 12, 'state': 'open', 'draft': False, 'mergeable': True,
        'head': {'ref': 'dev', 'sha': DEV, 'repo': {'full_name': 'owner/repo'}},
        'base': {'ref': 'main'}}


class FakeGitHub:
    repository = 'owner/repo'

    def __init__(self):
        self.refs = {'dev': DEV, 'main': MAIN}
        self.runs = [copy.deepcopy(RUN)]
        self.jobs = copy.deepcopy(JOBS)
        self.pull = copy.deepcopy(PULL)
        self.existing = []
        self.ahead = 1
        self.calls = []
        self.merged = True

    def ref(self, branch):
        return self.refs[branch]

    def api(self, path, method='GET', data=None):
        self.calls.append((path, method, data))
        if path.startswith('compare/'):
            return {'ahead_by': self.ahead}
        if path.startswith('actions/workflows/'):
            return {'workflow_runs': self.runs}
        if path.startswith('actions/runs/'):
            return {'jobs': self.jobs}
        if path.startswith('pulls?'):
            return self.existing
        if path == 'pulls' and method == 'POST':
            return self.pull
        if path.endswith('/merge'):
            return {'merged': self.merged, 'sha': 'c' * 40}
        if path == 'pulls/12':
            return self.pull
        raise AssertionError(path)

    def mutations(self):
        return [c for c in self.calls if c[1] != 'GET']


class DailyReleaseTests(unittest.TestCase):
    def test_success_merges_exact_sha_and_only_dev_to_main(self):
        github = FakeGitHub()
        self.assertIn('RELEASE_MERGED', release.release(github))
        create, merge = github.mutations()
        self.assertEqual(('main', 'dev'), (create[2]['base'], create[2]['head']))
        self.assertEqual({'sha': DEV, 'merge_method': 'merge'}, merge[2])

    def test_no_changes_does_not_mutate(self):
        github = FakeGitHub(); github.ahead = 0
        self.assertIn('RELEASE_SKIPPED', release.release(github))
        self.assertFalse(github.mutations())

    def test_missing_failed_or_pending_ci_does_not_create_pr(self):
        for runs in [[], [{**RUN, 'status': 'in_progress'}], [{**RUN, 'conclusion': 'failure'}],
                     [{**RUN, 'head_sha': MAIN}], [{**RUN, 'event': 'pull_request'}]]:
            with self.subTest(runs=runs):
                github = FakeGitHub(); github.runs = runs
                self.assertIn('RELEASE_SKIPPED', release.release(github))
                self.assertFalse(github.mutations())

    def test_missing_failed_or_skipped_required_job_does_not_create_pr(self):
        for jobs in [JOBS[:1], [{**JOBS[0], 'conclusion': 'skipped'}, JOBS[1]],
                     [JOBS[0], {**JOBS[1], 'conclusion': 'failure'}]]:
            with self.subTest(jobs=jobs):
                github = FakeGitHub(); github.jobs = jobs
                self.assertIn('RELEASE_SKIPPED', release.release(github))
                self.assertFalse(github.mutations())

    def test_dry_run_never_mutates(self):
        github = FakeGitHub()
        self.assertIn('RELEASE_DRY_RUN', release.release(github, True))
        self.assertFalse(github.mutations())

    def test_existing_pr_is_reused(self):
        github = FakeGitHub(); github.existing = [PULL]
        self.assertIn('RELEASE_MERGED', release.release(github))
        self.assertEqual(1, len(github.mutations()))

    def test_draft_closed_wrong_branch_sha_or_repository_is_not_merged(self):
        changes = [{'draft': True}, {'state': 'closed'},
                   {'head': {**PULL['head'], 'sha': MAIN}},
                   {'head': {**PULL['head'], 'ref': 'feature'}},
                   {'head': {**PULL['head'], 'repo': {'full_name': 'fork/repo'}}},
                   {'base': {'ref': 'dev'}}]
        for change in changes:
            with self.subTest(change=change):
                github = FakeGitHub(); github.existing = [PULL]; github.pull.update(change)
                self.assertIn('RELEASE_SKIPPED', release.release(github))
                self.assertFalse(github.mutations())

    def test_conflict_never_merges(self):
        github = FakeGitHub(); github.existing = [PULL]; github.pull['mergeable'] = False
        self.assertIn('RELEASE_SKIPPED', release.release(github))
        self.assertFalse(github.mutations())

    def test_unknown_mergeability_times_out_without_merge(self):
        github = FakeGitHub(); github.existing = [PULL]; github.pull['mergeable'] = None
        with patch.object(release.time, 'sleep') as sleep:
            self.assertIn('RELEASE_SKIPPED', release.release(github))
        self.assertEqual(6, sleep.call_count)
        self.assertFalse(github.mutations())

    def test_changed_dev_or_main_is_not_merged(self):
        for branch in ['dev', 'main']:
            with self.subTest(branch=branch):
                github = FakeGitHub(); github.existing = [PULL]
                original = github.api
                def api(path, method='GET', data=None):
                    if path == 'pulls/12':
                        github.refs[branch] = 'd' * 40
                    return original(path, method, data)
                github.api = api
                self.assertIn('RELEASE_SKIPPED', release.release(github))
                self.assertFalse(github.mutations())

    def test_ci_rerun_after_validation_blocks_merge(self):
        github = FakeGitHub(); github.existing = [PULL]
        with patch.object(release, 'successful_build', side_effect=[RUN, None]):
            self.assertIn('RELEASE_SKIPPED', release.release(github))
        self.assertFalse(github.mutations())

    def test_merge_rejection_is_failure(self):
        github = FakeGitHub(); github.merged = False
        with self.assertRaises(release.ReleaseError):
            release.release(github)

    def test_api_failure_does_not_print_api_response_or_credentials(self):
        with patch.object(release.subprocess, 'run', return_value=Mock(returncode=1, stdout='secret', stderr='secret')):
            with self.assertRaises(release.ReleaseError) as error:
                release.GitHub('owner/repo').api('pulls')
        self.assertNotIn('secret', str(error.exception))

    def test_repository_is_validated(self):
        with self.assertRaises(release.ReleaseError):
            release.GitHub('owner/repo; echo secret')


if __name__ == '__main__':
    unittest.main()
