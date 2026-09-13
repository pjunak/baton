import hashlib
from pathlib import Path
import tempfile
import unittest
from tested_release import build_identity, publish


class PublishTest(unittest.TestCase):
    def test_identity_is_commit_based_and_monotonic_without_version_bump(self):
        first = build_identity('a' * 40, 1)
        second = build_identity('b' * 40, 2)
        self.assertGreater(second['code'], first['code'])
        self.assertEqual(first['version'].split('-')[0], second['version'].split('-')[0])
        with self.assertRaises(ValueError): build_identity('main', 1)
        with self.assertRaises(ValueError): build_identity('a' * 40, 2100000000)

    def exercise(self, latest=None, existing=None, comparison='ahead'):
        identity = build_identity('a' * 40, 20)
        data = b'PK-test-package'
        digest = 'sha256:' + hashlib.sha256(data).hexdigest()
        calls = []
        def api(method, path, data=None, **kwargs):
            calls.append((method, path, data))
            if '/compare/' in path: return {'status': comparison}
            if '/git/ref/' in path: return None
            if '/releases/tags/' in path: return existing
            if path.endswith('/latest'): return latest
            if method == 'POST' and path.endswith('/releases'): return {'id': 1, 'draft': True, 'assets': []}
            if '/assets?' in path: return {'name': identity['file'], 'digest': digest, 'state': 'uploaded'}
            if method == 'PATCH': return {}
            raise AssertionError(path)
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory, 'app.apk'); package.write_bytes(data)
            publish(api, 'owner/baton', identity, package)
        return calls

    def test_upload_is_confirmed_before_publication(self):
        calls = self.exercise()
        self.assertIn('/assets?', calls[-2][1])
        self.assertEqual(calls[-1][2], {'draft': False, 'make_latest': 'true'})

    def test_old_rerun_cannot_replace_latest(self):
        calls = self.exercise(latest={'assets': [{'name': 'baton-100021-'+'b'*40+'.apk'}]})
        self.assertEqual(calls[-1][2]['make_latest'], 'false')

    def test_published_package_cannot_be_replaced(self):
        with self.assertRaisesRegex(ValueError, 'refusing to replace'):
            self.exercise(existing={'id': 1, 'draft': False, 'assets': [{'name':'wrong.apk'}]})

    def test_published_matching_rerun_has_no_writes(self):
        identity = build_identity('a' * 40, 20)
        asset = {'name': identity['file'], 'digest': 'sha256:' + hashlib.sha256(b'PK-test-package').hexdigest()}
        calls = self.exercise(existing={'id': 1, 'draft': False, 'assets': [asset]})
        self.assertTrue(all(method == 'GET' for method, _, _ in calls))

    def test_removed_commit_cannot_publish(self):
        with self.assertRaisesRegex(ValueError, 'no longer on main'):
            self.exercise(comparison='diverged')


if __name__ == '__main__': unittest.main()
