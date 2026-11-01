"""Sample Android-reported frame timing for one authorized, reversible local journey.
This does not measure touch-to-visible-acknowledgement latency or human usability.
"""
from pathlib import Path
import subprocess, json, re, datetime, xml.etree.ElementTree as ET
ADB = r"C:\Users\slavd\AppData\Local\Android\Sdk\platform-tools\adb.exe"
SERIAL = "R5CTA0TKLZZ"
PKG = "com.aistudio.braintool.xkqzps"
OUT = Path(__file__).resolve().parent / "phone"
def adb(*args):
    return subprocess.check_output([ADB, "-s", SERIAL, *args], timeout=45)
def tree():
    raw = adb("exec-out", "uiautomator", "dump", "/dev/tty").decode("utf-8")
    return ET.fromstring(raw[raw.index("<?xml"):raw.rindex("</hierarchy>")+12])
def labels(root):
    return [n.get("text") or n.get("content-desc") for n in root.iter("node")]
start_pid = adb("shell", "pidof", PKG).decode().strip()
start = datetime.datetime.now(datetime.timezone.utc).isoformat()
(OUT / "frame-profile-reset.log").write_bytes(adb("shell", "dumpsys", "gfxinfo", PKG, "reset"))
cycles = []
for index in range(20):
    root = tree()
    assert "HALO-QA-Studio-20261101" in labels(root), "Expected QA project changed"
    target = next(n for n in root.iter("node") if n.get("text") == "Все действия")
    assert target.get("enabled") == "true"
    x1,y1,x2,y2 = map(int,re.findall(r"\d+",target.get("bounds")))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    opened = tree()
    assert "Выбрать закреплённые действия" in labels(opened), "Actions did not open"
    adb("shell", "input", "keyevent", "4")
    cycles.append({"cycle":index+1,"actions_opened":True,"back_sent":True})
    if (index+1)%5==0: print("Completed",index+1,"of 20 navigation cycles",flush=True)
root = tree()
assert "HALO-QA-Studio-20261101" in labels(root)
assert any(x and x.startswith("Сохранено ·") and "Слои: 3" in x for x in labels(root))
end_pid = adb("shell", "pidof", PKG).decode().strip()
assert end_pid == start_pid, "App process changed during sampling"
raw = adb("shell", "dumpsys", "gfxinfo", PKG, "framestats")
(OUT / "frame-profile-gfxinfo.log").write_bytes(raw)
text = raw.decode("utf-8",errors="replace")
summary = [line.strip() for line in text.splitlines() if any(term in line for term in ["Total frames rendered:","Janky frames:","percentile:","Number Missed Vsync:","Number High input latency:","Number Slow UI thread:","Number Slow bitmap uploads:","Number Slow issue draw commands:","Number Frame deadline missed:"])]
record={"started_utc":start,"finished_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),"pid":start_pid,"cycles":cycles,"returned_to_saved_project":True,"android_reported_summary":summary,"scope":"One Samsung phone, Russian UI, saved QA Studio project; 20 Actions/Back cycles. UIAutomator observation/ADB overhead and idle animation are included. Not touch-to-acknowledgement latency, a user study, or whole-app performance qualification."}
(OUT / "frame-profile-result.json").write_text(json.dumps(record,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print("\n".join(summary),flush=True)
