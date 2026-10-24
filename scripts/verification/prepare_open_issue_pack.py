"""Build a source-linked acceptance pack from a fresh GitHub issue snapshot. No device writes."""
import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "docs/testing/open-issues-2026-10-24"

# These are executable human protocols, not claims that their requirements passed.
# Each original requirement also gets its own separate evidence/review row below.
PROTOCOLS = {
    28: [
        ("Legacy/offline", "Prepare v1 sketches and a new blank board. Disable network and do not enable any model. Reopen, draw with finger and mouse, save/reopen, erase the final stroke, undo/redo, and Cancel an edit.", "Original v1 geometry survives untouched saves; edited geometry and empty existing sketches persist correctly; Cancel preserves the saved object. No provider, Bluetooth or proprietary-driver dependency."),
        ("Physical pen", "On an actual pressure pen/tablet, draw light/heavy strokes, vary supported tilt, hover, use eraser end and barrel button, place a palm, then pan/zoom/select with touch. Repeat after pen detach/reattach.", "Pressure and eraser have their documented meaning; unsupported axes fall back; no palm stroke, connected-pointer jump, accidental erase or blocked accessibility gesture. Record exact hardware/OS/driver."),
        ("Recovery/capacity", "Create completed and incomplete contacts. Rotate, background/foreground, terminate/restart the app, and restore a completed draft. Attempt a large drawing up to each quota; import corrupt/future input in the isolated fixture.", "Completed local recovery survives; incomplete contact is discarded; exact draft receipt prevents stale resurrection; bounded history/payload/points and truthful limit notice; no silent loss or crash."),
        ("Tablet/accessibility", "Repeat phone/tablet portrait/landscape, 100%/200% font, TalkBack, keyboard undo/redo and reduced motion in EN/LV/RU/PL. Run a 30-minute drawing session and record frame/memory trends.", "Workspace and controls stay usable, focus/order/labels work, geometry and transforms stay stable, memory remains bounded. Synthetic MotionEvents are separate evidence from the physical pen."),
    ],
    29: [
        ("Exact source", "Select two visibly distinct drawn regions and one annotated PDF page. Ask about each, save a bookmark, reopen/rotate/zoom, move the source, then edit/delete/relink it and retry the old selection.", "Every answer/bookmark references the exact object/page/strokes/revision; stale or deleted sources require reselection; a different selection cannot be substituted."),
        ("Real mixed voice", "Select a region physically, speak an instruction through the actual STT-final route, then change selection during a delayed request and repeat with an ambiguous referent.", "One authorized interaction binds speech and exact selected region; changed or ambiguous references clarify; no action or unrelated source is inferred from a pen gesture."),
        ("Privacy/failure", "Use Private/offline and unavailable OCR/vision, then deny provider consent; repeat with a permitted direct user-owned provider and cancel while cropping/transcribing.", "Local markup/selection still works; no unapproved content transfer; crop is bounded and source-scoped; failures/cancellation release resources and explain the actual limitation."),
        ("Study/localization", "Run question → learner attempt → hint → feedback → revision → save/resume from ink, image/PDF and a voice memo. Inspect source-linked transcription and EN/LV/RU/PL limitations at phone/tablet sizes.", "Guidance remains distinct from giving the answer; context/provenance and privacy decision remain inspectable; unsupported language/recognizer is not advertised as supported. Record latency/memory."),
    ],
    30: [
        ("Pair and draw", "First verify the production activation gate stays closed. In the explicit qualification host configuration, exchange public device files directly between an Android tablet and a second host; confirm trust locally on both. Start one exact saved sketch through the approved current interaction, export/open its connection file, draw pressure strokes and erase, and read back the remote editable sketch.", "No pairing/receive/write path bypasses the gate. Mutual TLS authenticates exact approved certificates; input/display/processing owners are distinct; strokes converge once and remain editable. A fixture loopback pass is not two-device acceptance."),
        ("Transport abuse", "Use the host test harness to repeat/reorder/drop complete checkpoints and previews, send oversized/future/stale/source/session/interaction/document mismatches, flood previews, and use an untrusted or replaced certificate. Cancel while another checkpoint holds the owner lock.", "No unauthenticated injection or tool invocation; bounded frames/rate/queues; duplicate gets the original receipt; stale/conflicting content is rejected; incomplete preview expires; cancellation closes sockets and removes session/context."),
        ("Recover/conflict", "Disconnect during a contact and after commit before receipt; sleep/wake and restart both hosts. Keep editing locally, explicitly resume using a fresh session, create simultaneous local editor/recovery edits, and repeat identical IDs under two trusted hosts.", "Local completed edits remain recoverable; no automatic external-action replay or duplicate stroke; explicit revision conflict instead of overwrite; recovery is scoped to authenticated host; revoked/expired grants cannot resume silently."),
        ("Voice and lifecycle", "Select on one endpoint and speak on another in the same authorized interaction. Retry in a different interaction, after edit, after Stop/forget, after background and after process death. Inspect Devices capabilities and use accessible Stop.", "Only the exact current authorized session/region resolves; pen input never grants app/tool/cloud permission. Connected is separate from started/saved; local drawing remains available after disconnect."),
        ("Platforms/performance", "Build each native host adapter on its actual target. Test one exact Windows pen and one explicitly selected Linux evdev pen, recording driver/architecture. Investigate each Huion/OpenTabletDriver/Pi configuration independently. Measure end-to-end pen-to-preview/commit latency, memory/queue ceiling, loss/duplication, 30-minute thermal and battery use on tablet + second host.", "Compatibility records distinguish SOURCE_ONLY/NOT_TESTED/PASS/FAIL/UNSUPPORTED. Preserve supported pressure/eraser; never claim Windows/Linux/Huion/Pi hardware support from Android, source review or loopback tests. Attach actual measurements and predeclared budgets."),
    ],
    33: [
        ("Controlled benchmark", "Inventory installed artifacts only. For each selected exact artifact/quantization/context/settings, collect five cold and at least thirty warm serial-baseline and Auto runs, plus CPU/GPU manual pins. Record actual effective backend, delegate placement/fallback, load/prefill/decode/TTFT, RSS, thermal, failure and stage trace IDs.", "Same-input comparison is reproducible. Compute/report median/p95 end-of-speech to actual first audible audio, full latency and success rate; Auto must improve both median/p95 without correctness loss. Init alone is not GPU placement proof."),
        ("Concurrency/safety", "Overlap STT partials, retrieval and useful warmup; issue a final native command; barge in during generation/playback; cancel during admission/load, force eviction/low-memory on a test device, and detach BT/glasses. Repeat local and permitted direct-cloud routes.", "Independent stages overlap and dependent/authorized effects serialize. Partial/ambiguous ASR never acts; no duplicate action or stale audio. Native fast path avoids heavy LLM; unsupported backends reject; residency/thermal/memory limits and permissions hold."),
        ("Consumer setup", "Install/repair a supported curated role pack through normal setup; inspect Advanced role/backend/residency controls and switch back to consumer defaults.", "Role-to-model and model-to-backend remain independent, no prewarm-all policy or RAM-only promise; existing providers/setup work. No CI/Actions are run."),
    ],
    35: [
        ("Native capability", "For the exact installed supported SHA/runtime, inspect native embedding task/modalities/backend/encoder packaging before allocation. Test invalid/imported/unsupported modality/backend requests and cancellation during admission/close.", "Only actual supported native combinations allocate; unknown/imported capability stays untrusted; text-only fallback is honest; resource/physical-artifact leases release on cancellation and failed close reconciles."),
        ("Multimodal quality", "Prelabel positive/negative text-image, image-image, text-audio and text-video fixtures. Run supported native requests on exact bytes and report similarity ranking, latency/RSS, cold/warm behavior and low-memory/model-switch cases.", "Expected positives beat declared hard negatives; report actual quality and failures. Unsupported inputs remain explicitly unqualified, never generative vision or a fabricated vector. Raw input never silently uploads."),
        ("Index/consumer", "Opt in a local working set; exercise Notebook picture search, Canvas visual find, Study retrieval and voice-memo search. Change encoder/SHA/dimension/normalization/preprocessing/frame policy; reindex, delete/revoke source and retry.", "Incompatible spaces never mix; source permissions/deletion are honored; safe migration/reindex and bounded decoders/frames. Consumer flows have genuine retrieval evidence, not only a diagnostic engine pass."),
        ("Privacy/languages", "Repeat offline, permission denial and interruption; inspect EN/RU/LV/PL copy and phone/tablet/large-text UI.", "No cloud fallback or unsupported language/vision promise; clear local-search benefits and truthful limitations. Metrics stay in Advanced."),
    ],
    36: [
        ("Real intent matrix", "Use intent-classification-v1.json: all 48 fixture IDs across EN/RU/LV/PL by keyboard and actual spoken STT-final input. Keep negations, quotes, indirect requests, questions and composite actions. Capture fixture ID, recognized-route correctness, expected/actual label, model SHA, warm/cold and p95.", "Labels conform without thinking leakage; final text alone cannot authorize an action. Record the known prior warm Qwen failure as historical FAIL until a changed implementation/configuration produces new evidence. Admission skips do not count as classifier success."),
        ("Action loop/markup", "Execute native and registered cross-tool requests with approvals denied/allowed, duplicated proposals and ambiguous/UNCERTAIN output. Inspect streamed chat, TTS and stored reply for malformed/partial HALO_PROMPT envelopes.", "No wrong/duplicate side effect, partial ASR action or hidden marker leakage; normal text still streams; uncertainty clarifies and missing planner setup is honest."),
        ("Selection/residency", "Enable a qualified intent model, restart, change/remove the package, compete with STT/TTS/chat and switch low-memory residency; deny setup/permissions and retry.", "Opt-in state is accurate; bounded intent admission never replaces a competing resident; stale/failed/unknown evidence cannot resurrect a positive recommendation; no cloud fallback."),
    ],
    38: [
        ("Ten real turns", "Explicitly start Voice Mode from Home, Chat, Study and Notebook. Conduct at least ten real spoken turns without tapping the mic again in each claimed supported language; confirm audible Supertonic and supported Kokoro fallback. Record cold/warm p50/p95 and actual heard replies.", "Listening/response loops work foreground; no stealth capture, missing-model false readiness, speaker echo command or language promise without actual recognition/audible evidence."),
        ("Interrupt/control", "Speak and touch Stop/Pause/Mute/Repeat/End while capturing, generating and playing. Interrupt offline and on a permitted hybrid turn; switch the TTS/reasoning model during playback and supersede queued controls.", "Old capture/generation/audio is cancelled, no duplicate action or stale resumed mic, mute is respected, and each control reflects observed state."),
        ("Audio/privacy/lifecycle", "Switch speaker/wired/BT/glasses routes; interrupt audio focus, background/foreground, rotate, sleep/restart and revoke permission. Repeat denied versus explicitly permitted cloud and 30-minute voice use.", "Route/focus/echo handling and visible mic state are correct; privacy gates hold; no surprise transmission; recovery, battery/thermal/RSS limits and accessibility are measured."),
    ],
    39: [
        ("Catalog truth", "Inspect each displayed candidate against exact SHA/bytes/revision/format/quantization/provenance/license/source and actual native backend. Compare Compatible/Tested/Recommended/limitation badges by exact device, role and EN/RU/LV/PL.", "No runtime-init-only recommendation, unsupported format, English-only model advertised multilingual, or embedding advertised as generative vision. Update/runtime/artifact changes invalidate positive proof; negative quarantine is retained."),
        ("First-run/recovery", "On an isolated tester profile/device, install a functional suggested pack without HF browsing; pause/resume/cancel, interrupt process, test low storage safely, rollback, switch/remove and restart. Include gated/access-denied/token/import errors with test fixtures.", "Exact-owned files and shared models survive correctly; no lost unrelated partials or stale receipts; clear recovery and honest space/support limitations. Initial benchmark does not download new models automatically."),
        ("Role/language proof", "For each intended recommendation run exact installed role/language/backend fixtures, real STT/VAD and human audible TTS (all claimed languages), intent/tools/conversation, supported embedding/vision. Export source-bound evidence and recreate process before recommendation/apply.", "Fixtures and human quality agree; latest same-scope failures/clock rollback/ties supersede prior passes; package/install build identity and role assignments match. Interrupted/unsupported stays NOT_TESTED/UNSUPPORTED."),
    ],
    42: [
        ("Actual paid listing", "In Play Console, review the paid base listing, intended tester availability, localized prices/taxes/countries and approximately USD4.99 target. Attach the released package/version/signature and reviewer evidence.", "Actual listing is paid and available in reviewed regions. A debug/sideload install or source price string is not store evidence. No feature pack or recurring Halo subscription is introduced."),
        ("Purchase/reinstall/refund", "An authorized human tester uses the actual purchasing Play account for initial install/access, uninstall/reinstall or replacement-device access, and applicable full-refund/support flow. Redact order/account/payment identifiers.", "Store delivery/access and refund/support behavior are demonstrated on the released build. No in-app base unlock, token/entitlement service or functional backend is invented. No automated payment or refund action."),
        ("Copy/legal/privacy", "Have the release owner review localized privacy, legal and support copy, provider-cost disclosure, support contact and current Play guidance.", "No recurring Halo charge or unlimited-third-party-credit promise; no assistant content/provider secret enters commerce. Record reviewer/date and approved copy, separately from legal advice."),
    ],
    43: [
        ("Three central journeys", "On the exact installed build demonstrate repeatable physical sessions for ten-turn voice, safe typed/spoken ask→act→respond, and genuine Kids plus adult Study question/file/photo→hint/practice→Notebook/Canvas save/resume.", "All three central journeys have saved object/version/readback and human observations; no high-severity data loss, unauthorized action or fabricated cloud/offline state. Link #33/#36/#38/#54 evidence without substituting unit fixtures."),
        ("Consumer/accessibility", "Inspect Home composer/attachments/tools/orb, Settings/Advanced, Screen and Notebook/Canvas/Studio on phone/tablet, EN/RU/LV/PL, portrait/landscape and 200% text/TalkBack/reduced motion. Test Back and interruption around first-block flows.", "Approved BrandBook UX remains usable; important actions require no long scroll; no developer components/IDs leak; supported/in-development/disabled surfaces tell the truth."),
        ("Resilience/privacy matrix", "Use 8GB and available12GB+ hardware; verified CPU/GPU combinations, WiFi/offline, BT on/off, foreground/background, rotation/process death, TTS switch and safe thermal/low-memory tests. Complete setup/model-removal/permission-denial recovery and permitted/denied direct-provider paths.", "One concise PASS/FAIL/BLOCKED/NOT_TESTED matrix records exact environment, source/APKs/models/language/provider/time and known limitations; no surprise functional server/cost or hidden request. Experimental unsupported surfaces are honestly gated."),
    ],
    54: [
        ("Three semantic workflows", "Use three controlled fixture workflows: (1) existing note→structured edit/attachment→Canvas exact card, (2) exact file/PDF region→Study learner attempt/hint→saved note/resume, (3) Studio source→edit/export→Canvas/working-set handoff. Execute each by typed request and actual speech on-device and on a permitted Power route.", "Record exact target/saved IDs, versions, source hashes, semantic operation/approval/result truth and restart readback. Spoken/typed resolve the requested same canonical targets/outcomes; no navigation-only parity or synthetic text presented as speech."),
        ("Safety and operability", "Inspect actual ToolRegistry feature contracts/discovery, then deny restricted/destructive/external actions, revoke source/permission/capability, race revisions, cancel/interrupt and restart before acknowledgement; repeat duplicate/uncertain requests and Private/Power transitions.", "Existing approvals/Kids/OS/privacy policies hold; exact closed schemas and meaningful domain operations; truthful receipts and no automatic uncertain replay. Private never silently uploads workspace/screen/memory."),
        ("New ink controls", "Review halo_ink_status/document/start/stop/selection registration, bundled/runtime risks, WORKSPACE provenance, output quotas and same-interaction region guards. Verify pairing/certificate approval stays a protected human security act.", "Start needs fresh direct authority + exact source revision + qualified activation. Stroke/selection cannot call tools or grant cloud/app permission. Gated ink does not masquerade as completed physical #30 acceptance."),
    ],
}

CORE = {
    "ink": ["com.halo.app.ink.AndroidInkInputTest", "com.halo.app.ink.RemoteInkPhoneTest", "com.halo.app.ui.screens.notebook.CanvasDraftReceiptDeviceTest"],
    "workspace": ["com.halo.app.ui.workspace.WorkspaceOperabilityDeviceTest", "com.halo.app.ui.workspace.WorkspaceContinuityDeviceTest", "com.halo.app.ui.workspace.WorkspaceFileToolsDeviceTest", "com.halo.app.ui.workspace.StudioDeviceTest"],
    "notes-study": ["com.halo.app.notebook.NoteEditorControlsDeviceTest", "com.halo.app.notebook.NotePurgeDeviceTest", "com.halo.app.notebook.NoteAttachmentControlsDeviceTest", "com.halo.app.study.StudyLearnerControlsDeviceTest"],
    "display": ["com.halo.app.language.PolishLanguageDeviceTest", "com.halo.app.ui.screens.notebook.CanvasPreviewDeviceTest", "com.halo.app.ui.screens.notebook.CanvasPdfInspectionDeviceTest", "com.halo.app.perf.MainThreadIoGuardTest"],
    "setup-privacy": ["com.halo.app.ai.LocalRolePackRecoveryDeviceTest", "com.halo.app.ai.PrivateDispatchOwnerTraceTest"],
}

def primary(body):
    lines = body.splitlines()
    for index, line in enumerate(lines):
        if index and line.startswith("## ") and re.search(r"\b(?:20\d\d-\d\d-\d\d|coordination update|source progress|verification update)\b", line, re.I):
            return lines[:index]
    return lines

def generate(snapshot):
    original = json.loads(snapshot.read_text(encoding="utf-8-sig"))
    issues = sorted((row for row in original if not row.get("pull_request")), key=lambda row: row["number"])
    if {row["number"] for row in issues} != set(PROTOCOLS):
        raise ValueError("Open issue set changed; review and extend protocols before regenerating this pack")
    PACK.mkdir(parents=True, exist_ok=True)
    sources, requirements, scenarios = [], [], []
    for row in issues:
        number = row["number"]
        body = row["body"]
        sources.append({key: row[key] for key in ("number", "title", "body", "html_url", "updated_at")})
        heading, reqs = "", []
        for line_number, line in enumerate(primary(body), 1):
            if line.startswith("#"):
                heading = line.lstrip("# ")
            if re.match(r"^\s*(?:-\s|\d+\.\s)", line):
                identity = f"I{number}-R{len(reqs)+1:02d}"
                req = {"id": identity, "issue": number, "sourceLine": line_number, "section": heading,
                       "requirement": line.strip(), "priorIssueCheckboxChecked": bool(re.match(r"- \[[xX]\]", line.strip())),
                       "status": "NOT_TESTED", "evidence": [], "reviewer": "", "notes": ""}
                requirements.append(req); reqs.append(identity)
        # Paragraph-only acceptance (e.g. #38/#39) must not disappear from the checklist.
        heading = ""
        for line_number, line in enumerate(primary(body), 1):
            if line.startswith("#"):
                heading = line.lstrip("# ")
            elif line.strip() and not re.match(r"^\s*(?:-\s|\d+\.\s)", line) and re.search(r"accept|done when", heading, re.I):
                identity = f"I{number}-R{len(reqs)+1:02d}"
                requirements.append({"id": identity, "issue": number, "sourceLine": line_number, "section": heading,
                                     "requirement": line.strip(), "priorIssueCheckboxChecked": False,
                                     "status": "NOT_TESTED", "evidence": [], "reviewer": "", "notes": ""})
                reqs.append(identity)
        protocols = []
        for index, (title, steps, expected) in enumerate(PROTOCOLS[number], 1):
            identity = f"I{number}-P{index:02d}"
            case = {"id": identity, "issue": number, "title": title, "steps": steps, "expected": expected,
                    "status": "NOT_TESTED", "evidence": [], "measurements": {}, "reviewer": "", "notes": ""}
            scenarios.append(case)
            protocols += [f"### {identity}: {title}\n\n{steps}\n\n**Pass evidence:** {expected}\n\nStatus: NOT_TESTED. Evidence: pending.\n"]
        checklist = [f"- [ ] **{req['id']}** (original line {req['sourceLine']}, {req['section']}): {req['requirement']}"
                     for req in requirements if req["issue"] == number]
        (PACK / f"issue-{number}.md").write_text(f"# #{number}: {row['title']}\n\n"
            f"Original: {row['html_url']} · updated {row['updated_at']}. Full source is retained in source-issues.json.\n\n"
            "No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.\n\n"
            + "\n".join(protocols) + "\n## Requirement-by-requirement review\n\n" + "\n".join(checklist) + "\n", encoding="utf-8")
    suites = []
    for name, classes in CORE.items():
        count = 0
        files = []
        for cls in classes:
            path = ROOT / "app/src/androidTest/java" / (cls.replace(".", "/") + ".kt")
            text = path.read_text(encoding="utf-8-sig")
            methods = len(re.findall(r"@Test\b", text))
            if not methods:
                raise ValueError(f"No executable tests in {path}")
            count += methods
            files.append({"path": str(path.relative_to(ROOT)).replace("\\", "/"),
                          "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "expectedTests": methods})
        suites.append({"id": name, "classes": classes, "expectedTests": count, "sources": files,
                       "scope": "NATIVE_FIXTURE_ONLY", "timeoutSeconds": 900})
    write = lambda name, value: (PACK / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write("source-issues.json", sources)
    write("requirements.json", requirements)
    write("protocols.json", scenarios)
    write("native-suites.json", suites)
    write("pack-metadata.json", {"schema": 1, "preparedAtUtc": datetime.now(timezone.utc).isoformat(),
        "repository": "slafftrosheen/Halo", "openIssues": [row["number"] for row in issues],
        "originalSnapshotSha256": hashlib.sha256(snapshot.read_bytes()).hexdigest(),
        "requirements": len(requirements), "protocols": len(scenarios),
        "nativeFixtureTests": sum(row["expectedTests"] for row in suites),
        "requirementsSpecSha256": hashlib.sha256(json.dumps([
            {key: row[key] for key in ("id", "issue", "sourceLine", "section", "requirement")} for row in requirements
        ], sort_keys=True, ensure_ascii=False).encode()).hexdigest(),
        "protocolsSpecSha256": hashlib.sha256(json.dumps([
            {key: row[key] for key in ("id", "issue", "title", "steps", "expected")} for row in scenarios
        ], sort_keys=True, ensure_ascii=False).encode()).hexdigest(),
        "closure": "REQUIRES_ACTUAL_EVIDENCE_AND_REVIEW"})
    print(f"Prepared {len(issues)} issues, {len(requirements)} requirement rows, {len(scenarios)} protocols, "
          f"{sum(row['expectedTests'] for row in suites)} native fixture cases")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshot", type=Path, required=True)
    args = parser.parse_args()
    generate(args.snapshot.resolve())
