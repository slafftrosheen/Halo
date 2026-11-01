"""Collect matching successful host cases and native captures; never qualifies a device."""
from pathlib import Path
import datetime
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

out = Path(__file__).resolve().parent
root = out.parents[3]
baseline = '261e7d68174758c1708e5f633974903f8bd479a3'
log = (out / 'final-recovery-shell-host.log').read_text(encoding='utf-8-sig')
assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
assert '> Task :app:assembleDebug' in log and '> Task :app:compileDebugAndroidTestKotlin' in log
results = sorted((root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
suites = [ET.parse(path).getroot() for path in results]
totals = {'suites': len(suites), **{key: sum(int(suite.get(key, 0)) for suite in suites)
    for key in ('tests', 'failures', 'errors', 'skipped')}}
assert totals['tests'] > 0 and totals['failures'] == totals['errors'] == totals['skipped'] == 0
totals['debug_assembly'] = 'passed'
totals['android_test_kotlin_compilation'] = 'passed'
(out / 'final-host-results.json').write_text(json.dumps(totals, indent=2) + '\n', encoding='utf-8')
with zipfile.ZipFile(out / 'final-junit-xml.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in results:
        archive.write(path, path.name)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

captures = []
(out / 'renders').mkdir(exist_ok=True)
for suite in suites:
    for case in suite.findall('testcase'):
        name = case.get('classname') + '.' + case.get('name') + '.png'
        source = root / 'app/build/outputs/roborazzi' / name
        if source.is_file():
            target = out / 'renders' / name
            shutil.copy2(source, target)
            captures.append({'file': 'renders/' + name, 'sha256': sha(target),
                'test_class': case.get('classname'), 'test_method': case.get('name'),
                'successful_suite_timestamp_utc': suite.get('timestamp'),
                'source_test': 'app/src/test/java/' + case.get('classname').replace('.', '/') + '.kt'})
changed = subprocess.check_output(['git', 'diff', '--name-only', baseline, '--', 'app/src'], cwd=root).decode().splitlines()
added = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '--', 'app/src'], cwd=root).decode().splitlines()
files = [{'file': name, 'sha256': sha(root / name)} for name in sorted(set(changed + added))]
apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
release = root / 'releases/halo-alpha-debug.apk'
manifest = {
    'recorded_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'scope': 'Synthetic host/native evidence only; phone, assistive and participant acceptance pending',
    'baseline_commit': baseline,
    'results': totals,
    'captures': captures,
    'changed_source_files': files,
    'changed_source_manifest_sha256': hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest(),
    'apk': {'path': str(apk), 'bytes': apk.stat().st_size, 'sha256': sha(apk),
        'release_copy_matches': release.is_file() and sha(release) == sha(apk)},
}
(out / 'final-capture-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'results': totals, 'matching_captures': len(captures), 'apk': manifest['apk']}, indent=2))
