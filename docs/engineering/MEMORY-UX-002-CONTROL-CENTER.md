# MEMORY-UX-002 — Personal Memory control center

Memory is a Workspace tool, not another primary app tab. MemoryHubViewModel owns
list/search/filter/sort/count/review presentation and observes existing memory,
personal-learning and Workspace policy contracts. Search is debounced and calls
the same RoomMemoryRetriever as runtime tools; no competing scoring or embedding
model is introduced. Browse sorting does not override search ranking. The UI never
marks list/search rows used. Search remains current/eligible-scope retrieval; stale
records can still be inspected in ordinary browsing. Browse shows up to 100 rows.

Memory records, Notebook documents, historical episodes and transient working
memory are distinct. Learning permits suggestions; memory use permits automatic
context admission. Turning either policy off deletes nothing or changes the other.
Cloud-memory permission, retention, budgets, ranking and reflection are unchanged.

Manual creation first validates content and an exact eligible scope, then reviews
NEW / REINFORCES / CONFLICTS. Save rechecks conflict identities and content to
avoid applying stale review. Multiple conflicts cannot be silently chosen. Updates
retain canonical revisions; equivalent candidates reinforce the existing record.
Note promotion preserves source object/revision and never truncates long notes or
runs a model summary. Secret rejection occurs before any durable write.

Imported confirmation uses repository reinforce semantics on ACTIVE records only.
It preserves origin/content/scope/confidence/expiry and does not create a correction
revision. A stale confirmation does not invent an expiry extension. The UI explains
that expired records keep their existing expiry rules after confirmation.

Detail reuses WorkspaceObjectDetailScreen with MemoryDetailViewModel. Sources have
human type labels; exact available Workspace sources alone get navigation links.
Missing historical evidence is labeled unavailable without deleting approved facts.
No fake agent-run or episode deep link is invented. Approval does not change source
attribution. Usage is retrieval admission, not proof of a changed answer or action.
Revision history is collapsed, human dated and uses before/after truth.

Suggestions use existing PersonalLearningRepository.pending and acceptance paths.
Edited memory suggestions recompute conflicts before acceptance. Non-memory
suggestions remain procedures, automation review or capability setup, never an
automatic installation or execution. Never-again suppresses the existing fingerprint.
Learning history retains actual outcome/unknown-completion truth; Keep changes
retention importance, not current memory or fabricated retrieval use.

Learning settings preserve conservative Explicit only defaults. Suggest options are
shown only under Suggest; local model reflection remains opt-in. System Check is
passive and contains counts/policy/lexical availability only, never memory bodies.

## Verification

On 2026-10-07 the consolidated serial gate passed: `:runtime:cognition:test`
216 tests, `:app:testDebugUnitTest` 283 tests, `:app:assembleDebug` successful.
Total: 499 tests, zero failures, errors or skips. `git diff --check` passed.
The added checks cover origin/approval labels, human dates, stale visibility,
import confirmation preserving expiry, explicit conflicts/reinforcement, shared
search with no usage writes, hub review with no automatic mutation, secret
rejection, canonical forgetting/Working Set detachment, inferred acceptance,
never-again fingerprint suppression, exact note source/revision and no long-note
truncation. Unknown inferred sensitivity remains rejected by the existing guard.

ADB showed no connected device, so manual UI acceptance remains pending. No
installation, physical interaction or visual acceptance is claimed for this batch.
One eventual pass: Workspace → Memory; add a synthetic temperature preference;
correct it and inspect history; review a conflicting candidate; toggle memory use
off and verify rows remain; independently review Explicit only/Suggest learning;
review a pending suggestion if available; forget the synthetic test memory and
verify unrelated records are unchanged. Do not benchmark or repeat broad device
acceptance. Project creation options appear only for existing project scopes whose
exact Workspace target is selected; device and Workspace choices require valid
saved/selected targets. Missing targets never silently become global.
