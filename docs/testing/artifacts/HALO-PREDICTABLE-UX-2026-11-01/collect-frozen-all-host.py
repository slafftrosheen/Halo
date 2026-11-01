"""Archive the full-app run exactly, preserving any failures and the prior focused evidence."""
from pathlib import Path
import datetime, hashlib, json, xml.etree.ElementTree as ET, zipfile
OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[3]
log = (OUT / "frozen-all-host.log").read_text(encoding="utf-8-sig")
assert "BUILD SUCCESSFUL" in log or "BUILD FAILED" in log, "Run is not terminal"
paths = sorted((ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
suites = [ET.parse(path).getroot() for path in paths]
assert len(suites) > 34, "Expected full-app results, not the focused archive"
counts = {"suites":len(suites), **{key:sum(int(s.get(key,0)) for s in suites) for key in ("tests","failures","errors","skipped")}}
failed=[]
for suite in suites:
    for case in suite.findall("testcase"):
        for kind in ("failure","error"):
            failure = case.find(kind)
            if failure is not None:
                failed.append({"class":case.get("classname"),"test":case.get("name"),"kind":kind,"message":failure.get("message")})
with zipfile.ZipFile(OUT / "frozen-all-host-junit-xml.zip","w",zipfile.ZIP_DEFLATED) as archive:
    for path in paths: archive.write(path,path.name)
focus = json.loads((OUT / "phone-final-capture-manifest.json").read_text(encoding="utf-8"))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
drift = [x["file"] for x in focus["changed_source_files"] if sha(ROOT/x["file"]) != x["sha256"]]
apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
result={"recorded_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),"scope":"All app debug unit tests; not all repository modules or physical/participant qualification.","results":counts,"failed_cases":failed,"gradle_build_successful":"BUILD SUCCESSFUL" in log,"source_manifest":"phone-final-capture-manifest.json","source_drift":drift,"apk_sha256":sha(apk),"apk_matches_focused_and_installed":sha(apk)==focus["apk"]["sha256"],"debug_assembly":"> Task :app:assembleDebug" in log,"android_test_kotlin_compilation":"> Task :app:compileDebugAndroidTestKotlin" in log}
(OUT/"frozen-all-host-results.json").write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
print(json.dumps(result,indent=2))
assert not drift, "Source changed during full-app verification"
