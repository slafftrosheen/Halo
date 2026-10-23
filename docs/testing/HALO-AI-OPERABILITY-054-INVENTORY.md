# Assistant operability integration inventory #54

Source integration inventory; device, actual STT and permitted Power acceptance are separate.

| Feature | Functional owners/actions | Existing execution/policy boundary |
|---|---|---|
| Notes and selected context | NotebookRepository, WorkspaceRepository, WorkspaceContextStore; create/search/read/select | Workspace executor, native Workspace skill, workspace.read/write capability; explicit Memory review stays separate |
| Literal documents, CSV, images, PDF page annotations | WorkspaceFileTools; 18 typed operations from #52 | File executor and Files skill; exact source revisions, original preservation, managed receipts; external file handoff retains ALWAYS confirmation |
| Studio | StudioService and StudioProjectRepository; list/read/create/edit/save/export/insert; AI review/open/status | Existing Workspace skill; exact project/source/target versions; AI draft only dispatches existing provider/privacy/spend review, no automatic provider request |
| Canvas | NotebookCanvasRepository; create/read/text/move/connect/clear sketch, rename, groups/tags, duplicate, grid/layer arrangement, remove cards/groups/connections, plus source insertion | Same canonical owner as editor; exact board version in Room transaction and bounded explicit selections; destructive approval for sketch clearing/card/group/connection removal; linked notes/files retain identity/content; no pen/handwriting simulation or new undo store |
| Study | StudyUseCases/StudyRepository; start exact selected material/read saved session | Context identity and resolved Study revision both checked; source guard inside session transaction; no automatic replay of keyless create |
| Cross-tool continuity | WorkspaceContinuity; describe/find Canvas/open-with/status/insert/select | Six typed operations and existing host lifecycle; dispatch is distinct from saved result; no restored command replay |
| Phone intents and host controls | Existing typed native executors, CapabilityCatalogue, AndroidCapabilityPublisher, PolicyToolAuthorizer | Prior #36 phone qualification remains authoritative; Android permissions and external/destructive confirmations unchanged. This batch does not claim new physical/native/voice acceptance for them |
| Hands-free voice / ordinary Chat | Existing STT-final -> CognitiveSession -> ToolRegistry / AgentStep journal | Same registered operation schemas and owners. Voice transport availability and actual recognition still require #38/#44/device evidence |
| Image generation/editing with a provider | Existing Studio AI review + configured image owner | Assistant can request exact project review; user authorizes existing provider/privacy/billing flow. Home remains limited/review-required, never claims automatic generation or payment |
| Calculator and Python | No shipped callable functional owner | Home remains in development; no invented executor or arbitrary code execution |
| Drawing/pen and protected hardware | Human pen gestures / device-specific existing host adapters | Explicit exception: physical strokes and protected hardware are not simulated. Semantic document/card operations are callable; absent hardware remains unavailable |

The additive ToolOperability contract is integrated for the high-value Workspace/Studio/File/Canvas/Study actions above. The review helper inspects actual registered schemas and refuses a completed-feature claim without a contract or explicit exception. Legacy host contracts continue through their existing ToolDescriptors; new or changed features must complete the reusable verification template. Contract metadata neither grants permission nor proves spoken/provider success.

No new execution/approval engine, Memory store, provider backend or GitHub Actions. Existing exact source/selection resolution, ambiguity clarification, cancellation, policy and truthful ActionTruth receipts stay authoritative.
