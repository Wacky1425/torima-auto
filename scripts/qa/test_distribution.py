import copy
import json
import tempfile
import unittest
from pathlib import Path
from prepare import resolve
from publish import manifest_for


class DistributionTest(unittest.TestCase):
    def setUp(self):
        self.registry = json.loads(Path('qa/apps.json').read_text())
        self.app = self.registry['torima']

    def test_only_registered_branch_app_and_unique_ids(self):
        self.assertEqual(resolve('torima', 'integration/staging', self.registry), self.app)
        for key, branch in [('torima', 'main'), ('unknown', 'integration/staging'), ('../torima', 'integration/staging')]:
            with self.assertRaises((ValueError, KeyError)):
                resolve(key, branch, self.registry)
        duplicate = copy.deepcopy(self.registry)
        duplicate['other'] = copy.deepcopy(self.app)
        with self.assertRaises(ValueError):
            resolve('torima', 'integration/staging', duplicate)

    def test_manifest_uses_actual_apk_identity_and_size(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / 'qa.apk'
            apk.write_bytes(b'x' * 2048)
            badging = "package: name='com.neru.powlautotest.qa' versionCode='100001' versionName='3.14-qa.100001'\nsdkVersion:'24'"
            def manifest(text=badging, notes='Changed updater'):
                return manifest_for(apk, 'torima', self.app, 'Wacky1425/torima-auto', 'a' * 40, 100001, text, notes)
            result = manifest()
            self.assertEqual(result['sizeBytes'], 2048)
            self.assertEqual(result['channel'], 'qa')
            self.assertIn('/qa-torima-100001/torima-qa-100001.apk', result['apkUrl'])
            for text in [badging.replace('.qa', ''), badging.replace('100001', '47'), badging.replace("sdkVersion:'24'", "sdkVersion:'26'")]:
                with self.assertRaises(ValueError):
                    manifest(text)
            with self.assertRaises(ValueError):
                manifest(notes='')


if __name__ == '__main__':
    unittest.main()
