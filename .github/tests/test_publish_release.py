"""배포 버전의 커밋 일치·재시도·태그 충돌을 AWS/GitHub 대역으로 검증한다."""
import copy
import importlib.util
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[2] / 'infra/scripts/publish-release.py'
spec = importlib.util.spec_from_file_location('publish_release', SOURCE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
SHA, OLD = 'a' * 40, 'b' * 40


def ref(tag, sha=OLD, kind='commit'):
    return {'ref': 'refs/tags/' + tag, 'object': {'type': kind, 'sha': sha}}


def published(tag, **changes):
    return {'tag_name': tag, 'draft': False, 'prerelease': False, **changes}


class GitHub:
    def __init__(self, refs=None, releases=None):
        self.refs = copy.deepcopy(refs or [])
        self.releases = copy.deepcopy(releases or [])
        self.calls = []
        self.fail_publish = False
        self.tag_collision = False
        self.annotated = {}

    def api(self, path, method='GET', data=None):
        self.calls.append((path, method, data))
        if path == 'git/matching-refs/tags/v':
            return self.refs
        if path.startswith('git/tags/'):
            return {'object': self.annotated[path.rsplit('/', 1)[1]]}
        if path.startswith('releases?'):
            page = int(path.rsplit('=', 1)[1])
            return self.releases[(page - 1) * 100:page * 100]
        if path == 'git/refs' and method == 'POST':
            if self.tag_collision:
                raise m.api.ReleaseError('tag exists')
            result = ref(data['ref'][len('refs/tags/'):], data['sha'])
            self.refs.append(result)
            return result
        if path == 'releases' and method == 'POST':
            if self.fail_publish:
                raise m.api.ReleaseError('publication unavailable')
            result = published(data['tag_name'])
            self.releases.append(result)
            return result
        raise AssertionError(path)

    def mutations(self):
        return [call for call in self.calls if call[1] != 'GET']


class PublishReleaseTests(unittest.TestCase):
    def test_initial_version_tags_deployed_commit_then_publishes_notes(self):
        github = GitHub()
        self.assertIn('v0.1.0', m.publish(github, SHA))
        tag, release = github.mutations()
        self.assertEqual('git/refs', tag[0])
        self.assertEqual({'ref': 'refs/tags/v0.1.0', 'sha': SHA}, tag[2])
        self.assertEqual(SHA, release[2]['target_commitish'])
        self.assertTrue(release[2]['generate_release_notes'])
        self.assertFalse(release[2]['draft'])

    def test_next_patch_uses_numeric_version_order(self):
        github = GitHub([ref('v0.1.9'), ref('v0.1.10')])
        self.assertIn('v0.1.11', m.publish(github, SHA))

    def test_manual_minor_or_major_version_is_respected(self):
        for tag, expected in [('v0.2.0', 'v0.2.1'), ('v1.0.0', 'v1.0.1')]:
            with self.subTest(tag=tag):
                self.assertIn(expected, m.publish(GitHub([ref(tag)]), SHA))

    def test_same_deployed_commit_never_creates_another_version(self):
        github = GitHub([ref('v0.1.0', SHA)], [published('v0.1.0')])
        self.assertIn('RELEASE_EXISTS', m.publish(github, SHA))
        self.assertFalse(github.mutations())

    def test_retry_after_tag_created_reuses_that_tag(self):
        github = GitHub([ref('v0.1.0', SHA)])
        self.assertIn('v0.1.0', m.publish(github, SHA))
        self.assertEqual(['releases'], [c[0] for c in github.mutations()])

    def test_publish_failure_then_retry_does_not_increment_version(self):
        github = GitHub(); github.fail_publish = True
        with self.assertRaises(m.api.ReleaseError):
            m.publish(github, SHA)
        github.fail_publish = False
        self.assertIn('v0.1.0', m.publish(github, SHA))
        self.assertEqual(1, sum(c[0] == 'git/refs' and c[1] == 'POST' for c in github.calls))

    def test_dry_run_creates_nothing(self):
        github = GitHub()
        self.assertIn('RELEASE_VERSION_DRY_RUN', m.publish(github, SHA, True))
        self.assertFalse(github.mutations())

    def test_existing_annotated_tag_is_resolved(self):
        github = GitHub([ref('v0.1.0', 'c' * 40, 'tag')], [published('v0.1.0')])
        github.annotated['c' * 40] = {'type': 'commit', 'sha': SHA}
        self.assertIn('RELEASE_EXISTS', m.publish(github, SHA))
        self.assertFalse(github.mutations())

    def test_non_version_tags_do_not_affect_initial_version(self):
        github = GitHub([ref('v-test'), ref('v0.2.0-beta'), ref('v01.2.3')])
        self.assertIn('v0.1.0', m.publish(github, SHA))

    def test_same_commit_draft_or_prerelease_requires_manual_review(self):
        for change in [{'draft': True}, {'prerelease': True}]:
            with self.subTest(change=change):
                github = GitHub([ref('v0.1.0', SHA)], [published('v0.1.0', **change)])
                with self.assertRaises(m.api.ReleaseError):
                    m.publish(github, SHA)
                self.assertFalse(github.mutations())

    def test_invalid_commit_never_mutates(self):
        github = GitHub()
        for value in ['', 'main', 'abc', '../dev']:
            with self.subTest(value=value), self.assertRaises(m.api.ReleaseError):
                m.publish(github, value)
        self.assertFalse(github.calls)

    def test_tag_collision_fails_without_publishing_other_commit(self):
        github = GitHub(); github.tag_collision = True
        with self.assertRaises(m.api.ReleaseError):
            m.publish(github, SHA)
        self.assertFalse(any(c[0] == 'releases' and c[1] == 'POST' for c in github.calls))

    def test_paginated_releases_are_checked_before_publication(self):
        github = GitHub([ref('v0.1.0', SHA)],
                        [published('test-' + str(i)) for i in range(100)] + [published('v0.1.0')])
        self.assertIn('RELEASE_EXISTS', m.publish(github, SHA))
        self.assertFalse(github.mutations())

    def test_reserved_release_name_is_not_reused_if_tag_was_removed(self):
        github = GitHub([], [published('v0.1.0')])
        self.assertIn('v0.1.1', m.publish(github, SHA))

    def test_non_commit_tag_target_is_rejected(self):
        github = GitHub([ref('v0.1.0', OLD, 'tree')])
        with self.assertRaises(m.api.ReleaseError):
            m.publish(github, SHA)
        self.assertFalse(github.mutations())


if __name__ == '__main__':
    unittest.main()
