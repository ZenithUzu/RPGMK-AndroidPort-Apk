"""The constructor must include the current runtime, including when main advances mid-build."""
from pathlib import Path
import json
import re
import subprocess
import unittest


class TemplateFreshnessTest(unittest.TestCase):
    def test_compare_gate(self):
        workflow = (Path(__file__).resolve().parents[1] / '.github/workflows/apk.yml').read_text()
        expression = re.search(r"jq -e '(.*?)'", workflow, re.S)[1]
        cases = [
            ('identical', [], True),
            ('ahead', ['docs/info.md'], True),
            ('ahead', ['android/app/src/main/java/MainActivity.java'], False),
            ('ahead', ['android/app/jni/src/graphics.cpp'], False),
            ('ahead', ['android/patches/rmxp.rb'], False),
            ('behind', ['android/app/src/main/java/MainActivity.java'], True),
            ('diverged', [], False),
        ]
        for status, files, ready in cases:
            with self.subTest(status=status, files=files):
                comparison = {'status': status, 'files': [{'filename': p} for p in files]}
                result = subprocess.run(['jq', '-e', expression], input=json.dumps(comparison),
                                        text=True, capture_output=True)
                self.assertEqual(result.returncode, 0 if ready else 1, result.stderr)


if __name__ == '__main__':
    unittest.main()
