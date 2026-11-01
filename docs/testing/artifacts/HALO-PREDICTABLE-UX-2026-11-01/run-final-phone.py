"""Authorized update and bounded Samsung regression; preserves app data and preferences."""
from pathlib import Path
import datetime, hashlib, json, subprocess
ROOT = Path(__file__).resolve().parents[4]
OUT = Path(__file__).resolve().parent / "phone"
ADB = r"C:\Users\slavd\AppData\Local\Android\Sdk\platform-tools\adb.exe"
SERIAL = "R5CTA0TKLZZ"
PKG = "com.aistudio.braintool.xkqzps"
def adb(*args, timeout=300):
    return subprocess.check_output([ADB, "-s", SERIAL, *args], timeout=timeout)
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
test = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
manifest_path = OUT.parent / "phone-final-capture-manifest.json"
if manifest_path.exists():
    expected_sha = json.loads(manifest_path.read_text(encoding="utf-8"))["apk"]["sha256"]
else:
    expected_sha = json.loads((OUT / "candidate-final-apk.json").read_text(encoding="utf-8"))["sha256"]
assert expected_sha == sha(apk)
for path, extra in ((apk, []), (test, ["-t"])):
    result = adb("install", "-r", *extra, str(path)).decode()
    assert "Success" in result, result
    print(path.name + ": " + result.strip(), flush=True)
installed = adb("shell", "pm", "path", PKG).decode().splitlines()
base = next(line.removeprefix("package:") for line in installed if line.endswith("/base.apk"))
installed_sha = hashlib.sha256(adb("exec-out", "cat", base)).hexdigest()
assert installed_sha == sha(apk)
record = {"utc": datetime.datetime.now(datetime.timezone.utc).isoformat(), "serial": SERIAL,
    "started_device_epoch": int(adb("shell", "date", "+%s").decode().strip()), "apk_sha256": installed_sha, "test_apk_sha256": sha(test), "data_preserved": True,
    "system_font_scale_before": adb("shell", "settings", "get", "system", "font_scale").decode().strip()}
classes = {
 "native-device-p05-p06": ["com.halo.app.ui.workspace.StudioDeviceTest", "com.halo.app.ui.workspace.WorkspaceFileToolsDeviceTest", "com.halo.app.ui.workspace.WorkspaceContinuityDeviceTest", "com.halo.app.ui.workspace.WorkspaceOperabilityDeviceTest", "com.halo.app.study.StudyLearnerControlsDeviceTest", "com.halo.app.ui.presence.Zone5OrbStyleDeviceTest", "com.halo.app.ui.screens.kids.Zone5KidsFrameDeviceTest"],
 "main-activity-p05-p06": ["com.halo.app.ui.shell.MainActivityComposerPhysicalTest", "com.halo.app.ui.shell.MainActivityStudioPreviewPhysicalTest"]}
for name, tests in classes.items():
    raw = adb("shell", "am", "instrument", "-w", "-r", "-e", "runId", "predictable-ux-20261101-p05-p06", "-e", "sourceStamp", installed_sha, "-e", "class", ",".join(tests), PKG + ".test/androidx.test.runner.AndroidJUnitRunner", timeout=480)
    (OUT / (name + ".log")).write_bytes(raw)
    output = raw.decode("utf-8", errors="replace")
    expected = "OK (10 tests)" if name.startswith("native") else "OK (3 tests)"
    record[name] = {"passed": expected in output, "log": name + ".log"}
    print(name + ": " + ("PASS" if expected in output else "FAILED — inspect saved log"), flush=True)
record["system_font_scale_after"] = adb("shell", "settings", "get", "system", "font_scale").decode().strip()
(OUT / "installed-final-apk.json").write_text(json.dumps(record, indent=2)+"\n", encoding="utf-8")
assert all(record[key]["passed"] for key in classes)
