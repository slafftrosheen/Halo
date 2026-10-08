# HALO NOTEBOOK — COGNITIVE WORKSPACE & BRAIN/DATA MANAGEMENT BLUEPRINT

> Status: PROPOSED USER BLUEPRINT. Incorporated 2026-09-18.
>
> User-supplied product/design input, preserved below. It describes desired behavior,
> not current implementation. Current AppShell has no Notebook route; the existing
> Room Memory declaration is not this proposed workspace schema. Kotlin snippets and
> module trees are conceptual and must be reconciled with actual SDKs/build layout.
> No code or shell instructions are executed merely because they appear in this document.
>
> See [delivery and reconciliation notes](NOTEBOOK-DELIVERY.md),
> [visual reference](../03-design/HALO-WORKSPACE-DESIGN.md), and
> [current storage](../04-architecture/DATABASE.md).

**Status:** PROPOSED
**Target:** Halo Android Host / portable Halo Runtime ecosystem
**Document type:** Product + architecture blueprint
**Suggested repository path:** `docs/02-product/NOTEBOOK-WORKSPACE-BLUEPRINT.md`
**Date:** 2026-09-18

---

## 1. Definition

**Notebook is Halo's persistent cognitive workspace.**

It is the primary user-facing surface for creating, organizing, inspecting, editing, connecting, and reusing the information Halo works with.

Notebook combines two responsibilities:

1. **Workspace**

   * notes
   * documents
   * files
   * conversations
   * artifacts
   * ideas
   * projects
   * canvases
   * captured media

2. **Brain / data management**

   * Halo memory
   * remembered facts
   * context sources
   * provenance
   * relationships
   * working context
   * retention controls
   * correction and deletion
   * import/export

Notebook must make Halo feel less like a chatbot with history and more like a **personal cognitive operating environment**.

The conceptual loop is:

```text
Capture
   ↓
Understand
   ↓
Work
   ↓
Remember
   ↓
Connect
   ↓
Reuse
   ↓
Act
```

Notebook is where this loop becomes visible and manageable by the user.

---

# 2. Product Position

Halo remains:

```text
Halo = Portable Cognitive Runtime for Wearable Computing
```

Notebook does not replace or redefine the runtime.

Instead:

```text
Runtime = cognition + orchestration + execution

Notebook = persistent human-facing cognitive workspace
```

The relationship is:

```text
                     HALO
                       │
       ┌───────────────┴────────────────┐
       │                                │
 Cognitive Runtime                  Workspace
       │                                │
 perception                         Notebook
 reasoning                          notes
 planning                           files
 execution                          memory
 reflection                         conversations
       │                             artifacts
       │                             canvases
       └───────────────┬────────────────┘
                       │
                   Context Layer
```

Notebook should expose useful information from Halo without leaking runtime internals into the UI.

---

# 3. Primary Navigation

Notebook becomes a first-class Halo destination.

Canonical primary navigation becomes:

```text
Home
Activity
Notebook
Devices
Settings
```

Responsibilities:

| Surface  | Responsibility                                              |
| -------- | ----------------------------------------------------------- |
| Home     | Conversation-first Halo presence, deterministic Today, working context |
| Activity | What happened                                               |
| Notebook | What persists and what the user is working with             |
| Devices  | Where Halo can perceive and act                             |
| Settings | How Halo behaves                                            |

The distinction between Activity and Notebook is intentional.

### Activity

Activity answers:

> What happened?

It is chronological.

Examples:

* user spoke to Halo
* file captured
* model responded
* device connected
* action executed
* task failed
* photo created

### Notebook

Notebook answers:

> What do I have, know, or want to continue working with?

It is persistent and organizational.

Examples:

* project note
* stored conversation
* PDF
* remembered preference
* working canvas
* image collection
* design idea
* generated artifact

Activity is history.

Notebook is workspace.

---

# 4. Core Product Principle

Notebook must not be implemented as:

```text
Chat history +
Notes tab +
Files tab
```

That would reproduce existing application silos.

The central abstraction should instead be:

```text
WorkspaceObject
```

Everything the user can work with becomes a workspace object with common identity, metadata, provenance, relationships, and lifecycle.

Examples:

```text
WorkspaceObject
 ├── Note
 ├── Conversation
 ├── File
 ├── Memory
 ├── Canvas
 ├── Artifact
 ├── Media
 ├── Collection
 └── Project
```

This enables Halo to work across objects without hardcoded special cases.

---

# 5. Canonical Workspace Object

Conceptual model:

```kotlin
data class WorkspaceObject(
    val id: WorkspaceObjectId,
    val type: WorkspaceObjectType,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,

    val source: WorkspaceSource,
    val provenance: Provenance,

    val tags: Set<String>,
    val relations: Set<WorkspaceRelation>,

    val lifecycle: WorkspaceLifecycle,
    val indexing: IndexingState,

    val contentRef: ContentReference
)
```

Types:

```kotlin
enum class WorkspaceObjectType {
    NOTE,
    CONVERSATION,
    FILE,
    MEMORY,
    CANVAS,
    ARTIFACT,
    MEDIA,
    COLLECTION,
    PROJECT
}
```

The generic object contains metadata only.

Actual content remains behind type-specific repositories/contracts.

Do not create one giant database record containing every possible object field.

---

# 6. Object Types

## 6.1 Note

A lightweight editable document.

Supports:

* plain text
* Markdown
* headings
* lists
* checklists
* links
* images
* file references
* object references
* inline Halo actions

Example:

```text
Lightbox redesign

[ ] reduce extrusion count
[ ] test diffuser material

Reference:
→ warehouse-photo.jpg
→ supplier-spec.pdf

Halo:
Summarize these references
```

Notes should remain fast enough to replace a basic notes app.

---

## 6.2 Conversation

A persistent conversation with Halo.

Conversation is no longer treated as disposable chat history.

A conversation can:

* be renamed
* be tagged
* belong to a project
* reference files
* reference memories
* generate artifacts
* be converted into a note
* be added to working context
* be archived

Conversation history should preserve the distinction between:

```text
user input
Halo response
tool/action
device capture
generated artifact
```

---

## 6.3 File

A file known to Halo.

Possible types:

```text
PDF
TXT
MD
CSV
JSON
image
audio
video
CAD/export
other binary
```

Halo should retain metadata separately from file bytes.

Conceptually:

```kotlin
data class WorkspaceFile(
    val id: FileId,
    val uri: ContentUri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val hash: String?,
    val importedAt: Instant
)
```

Do not duplicate large files unless ownership or reliability requires it.

Where possible, preserve Android URI/document-provider semantics.

---

# 7. Memory

Memory is one of the most important Notebook surfaces.

It must be visible, inspectable, editable, attributable, and reversible.

Memory must never appear to the user as unexplained hidden model state.

A memory record should answer:

```text
What does Halo remember?

Where did this information come from?

When was it created?

Why is it retained?

Where has it been used?

Can I correct it?

Can I remove it?
```

Conceptual model:

```kotlin
data class MemoryRecord(
    val id: MemoryId,
    val content: String,

    val kind: MemoryKind,

    val createdAt: Instant,
    val updatedAt: Instant,

    val provenance: MemoryProvenance,

    val confidence: MemoryConfidence?,

    val scope: MemoryScope,

    val status: MemoryStatus
)
```

Possible kinds:

```text
Preference
Fact
ProjectContext
Person
Place
Device
Workflow
Instruction
LearnedAssociation
```

---

# 8. Memory Provenance

Every memory must preserve provenance.

Examples:

```text
From conversation
18 Sep 2026
"User prefers local-first providers."
```

```text
Imported from
HALO_PROJECT_NOTES.md
```

```text
Created manually
Notebook → Memory
```

```text
Derived from repeated interactions
Sources: 3 conversations
```

The Notebook UI should make provenance accessible without overwhelming normal use.

Example memory card:

```text
Local-first AI

Prefer local inference and avoid cloud
when a local provider is available.

Personal preference

Source
Conversation · Aug 12

Used recently
Halo provider selection

[ Edit ] [ Forget ]
```

---

# 9. Memory Is Not Notebook Storage

Critical architectural rule:

**Notebook storage and runtime memory are related but not identical.**

A note does not automatically become memory.

A PDF does not automatically become memory.

A conversation does not automatically become permanent memory.

Instead:

```text
Workspace object
      │
      ├── available to search
      │
      ├── temporarily added to context
      │
      └── explicitly/promoted into memory
```

This avoids polluting Halo memory with every piece of user data.

---

# 10. Working Set

Notebook introduces the concept of a **Working Set**.

The Working Set is Halo's currently relevant user-controlled context.

Example:

```text
WORKING SET

Halo UI redesign
├── Notebook blueprint
├── UI mockup.png
├── conversation: visual language
├── memory: design preference
└── PROJECT-DIRECTION.md
```

The Working Set should be visible to the user.

Halo may suggest additions:

```text
Add HeyCyan protocol notes to this working set?
```

But the user remains able to inspect and remove them.

Conceptual API:

```kotlin
interface WorkingSetRepository {

    val active: StateFlow<WorkingSet>

    suspend fun add(objectId: WorkspaceObjectId)

    suspend fun remove(objectId: WorkspaceObjectId)

    suspend fun clear()

    suspend fun createSnapshot(): WorkingSetSnapshot
}
```

---

# 11. Projects / Spaces

Notebook should support lightweight grouping.

Use a neutral internal abstraction such as:

```text
Workspace
Project
Collection
```

Do not force everything into projects.

Example:

```text
Halo
BuddyBot
Signage
Latvian ceramics
Press brake
Personal
Inbox
```

Objects may belong to zero, one, or multiple collections depending on implementation complexity.

Initial alpha may support one primary project per object.

---

# 12. Notebook Home

Notebook landing screen should prioritize useful work rather than storage hierarchy.

Recommended structure:

```text
Notebook

[ Search ]                     [ + ]

All   Notes   Memory   Files   Chats   Canvas

Quick Note
──────────────────────────────

Today

┌ Project: Halo UI ────────────┐
│ Visual language              │
│ 6 connected objects          │
└──────────────────────────────┘

┌ HeyCyan protocol ────────────┐
│ Conversation                 │
└──────────────────────────────┘

Working set
──────────────────────────────

Halo visual language
4 objects active

Memory
──────────────────────────────

Local-first providers
From conversation

Recent files
──────────────────────────────
```

Do not turn this into a dashboard full of metrics.

Notebook is a place to work.

---

# 13. Quick Capture

Notebook should make capture nearly frictionless.

Global actions:

```text
New note
New canvas
Import file
Capture image
Record audio
Paste clipboard
Start conversation
```

Home's composer should also support:

```text
+ → Note
+ → File
+ → Photo
+ → Canvas
```

Wearable capture should be able to land here.

Example:

```text
HeyCyan photo
       ↓
Inbox
       ↓
User/Halo:
"Add this to Lightbox redesign"
       ↓
Project workspace
```

---

# 14. Inbox

Notebook needs an Inbox.

Inbox contains captured material that has not yet been organized.

Sources may include:

```text
voice capture
camera
wearable capture
shared Android file
clipboard
quick note
generated artifact
import
```

This prevents Halo from forcing classification at capture time.

Inbox items can later become:

```text
note
memory
project item
file
canvas object
conversation context
```

---

# 15. Editor

Notebook requires a real editor.

Alpha editor scope:

```text
plain text
Markdown
headings
lists
checklists
inline links
object references
undo/redo
autosave
```

Later:

```text
tables
code blocks
annotations
drawings
structured blocks
embedded media
comments
```

The editor must work without AI.

Halo enhances editing but never becomes a dependency for editing.

---

# 16. Halo Inside the Editor

Halo should operate contextually.

Select text:

```text
[ Ask Halo ]
[ Rewrite ]
[ Explain ]
[ Turn into tasks ]
[ Remember ]
```

Document-level actions:

```text
Summarize
Continue writing
Find related material
Create tasks
Extract facts
Add to memory
Create canvas
```

Generated content should remain visibly attributable until accepted.

Do not silently overwrite user text.

---

# 17. Canvas

Canvas is Halo's spatial thinking surface.

It is not intended to reproduce a full design tool.

Initial canvas objects:

```text
text card
note
image
file
conversation
memory
link
group
connector
```

Example:

```text
                   ┌ HeyCyan ┐
                   │ glasses │
                   └────┬────┘
                        │
           ┌────────────┴────────────┐
           │                         │
      camera input               audio input
           │                         │
     ┌─────▼─────┐             ┌────▼─────┐
     │ vision AI │             │   STT    │
     └───────────┘             └──────────┘
```

Objects on canvas should reference underlying workspace objects rather than duplicate their content.

---

# 18. Relationships

Halo should support relationships between workspace objects.

Examples:

```text
conversation → produced → note

file → referenced-by → project

memory → sourced-from → conversation

image → captured-by → HeyCyan

note → related-to → canvas

artifact → generated-from → conversation
```

Conceptual model:

```kotlin
data class WorkspaceRelation(
    val from: WorkspaceObjectId,
    val type: RelationType,
    val to: WorkspaceObjectId,
    val createdAt: Instant
)
```

Relations are the foundation for future contextual retrieval.

Do not attempt to build a giant general-purpose knowledge graph in the first implementation.

---

# 19. Search

Notebook search should eventually cover:

```text
notes
conversations
memory
files
file metadata
artifacts
canvas text
projects
```

Search phases:

### Phase 1

Local lexical search.

```text
SQLite FTS
```

### Phase 2

Hybrid retrieval.

```text
lexical + embeddings
```

### Phase 3

Contextual query.

```text
"Show everything related to HeyCyan microphone decoding."
```

Embedding support must remain provider-independent.

---

# 20. Context Retrieval

Notebook data may provide cognition context through a stable interface.

Example:

```kotlin
interface KnowledgeContextSource {

    suspend fun search(
        query: ContextQuery
    ): List<ContextCandidate>
}
```

Runtime cognition receives context candidates.

It does **not** query Android Room tables or filesystem paths directly.

Dependency direction remains:

```text
UI
 ↓
Application use cases
 ↓
Workspace / memory interfaces
 ↓
Repositories
 ↓
Platform storage
```

The frozen Halo runtime architecture remains intact.

---

# 21. Storage Architecture

Recommended local architecture:

```text
Room / SQLite
│
├── workspace_object
├── note
├── conversation
├── memory
├── relation
├── collection
├── working_set
└── search_index

Filesystem / Android document storage
│
├── imported files
├── generated artifacts
├── thumbnails
└── canvas attachments
```

Large binary objects should not be stored as Room blobs.

Store references plus metadata.

---

# 22. Repository Contracts

Suggested interfaces:

```kotlin
interface WorkspaceRepository {

    fun observeObjects(
        filter: WorkspaceFilter
    ): Flow<List<WorkspaceObject>>

    suspend fun get(
        id: WorkspaceObjectId
    ): WorkspaceObject?

    suspend fun create(
        draft: WorkspaceObjectDraft
    ): WorkspaceObject

    suspend fun update(
        object: WorkspaceObject
    )

    suspend fun archive(
        id: WorkspaceObjectId
    )

    suspend fun delete(
        id: WorkspaceObjectId
    )
}
```

Type-specific contracts:

```text
NoteRepository
ConversationRepository
WorkspaceFileRepository
MemoryRepository
CanvasRepository
CollectionRepository
RelationRepository
WorkingSetRepository
```

Avoid a God repository.

---

# 23. Application Use Cases

UI must work through application-level use cases.

Examples:

```text
CreateNote
UpdateNote
ImportFile
CreateCanvas
SearchNotebook

AddToWorkingSet
RemoveFromWorkingSet

PromoteToMemory
UpdateMemory
ForgetMemory

AttachObjectToProject
LinkWorkspaceObjects

ConvertConversationToNote

BuildContextForRequest
```

Do not place this logic inside Compose ViewModels.

---

# 24. Memory Promotion

Conversion into memory must be explicit at the domain/application level.

Example:

```text
Selected note text
      ↓
"Remember this"
      ↓
MemoryCandidate
      ↓
validation / normalization
      ↓
MemoryRecord
      ↓
provenance stored
```

Conceptual request:

```kotlin
data class CreateMemoryRequest(
    val content: String,
    val sourceObjectId: WorkspaceObjectId?,
    val sourceRange: TextRange?,
    val kind: MemoryKind,
    val scope: MemoryScope
)
```

---

# 25. Memory Correction

Editing a memory must preserve history.

Do not silently mutate provenance.

Example:

```text
Original:
User likes dark UI.

Corrected:
User prefers light/pastel UI with optional darker theme.

Revision history:
18 Sep 2026
```

A future memory system may use revisions for reasoning about conflicting data.

---

# 26. Deletion

Deletion semantics must be predictable.

Recommended lifecycle:

```text
ACTIVE
ARCHIVED
TRASHED
DELETED
```

`TRASHED` should be recoverable.

Permanent deletion should remove:

```text
database records
search index records
embeddings
generated previews
managed file copies
```

Where an external file URI is referenced, deleting from Notebook should not delete the original unless Halo explicitly owns the file and the user chooses permanent deletion.

---

# 27. Privacy Model

Notebook follows Halo's privacy-first philosophy.

Default assumptions:

```text
local-first
offline-capable
no Halo-operated cloud requirement
user-controlled data
provider-independent
```

AI providers should receive only context necessary for the active request.

Notebook storage must not automatically upload user content.

Provider APIs remain provider implementations, never storage architecture.

---

# 28. Import / Export

Halo data must remain portable.

Long-term export target:

```text
Halo Workspace Export
├── manifest.json
├── notes/
├── conversations/
├── memory/
├── canvases/
├── files/
└── relations.json
```

Notes should support Markdown export where possible.

Memory should have a human-readable export format.

The user's cognitive workspace must never become trapped inside an opaque database.

---

# 29. Visual Language

Notebook adopts the new Halo visual direction:

```text
spatial
fluid
restrained
tactile
persistent
contextual
alive
```

It should not look like:

```text
enterprise dashboard
developer console
generic chat history
Notion clone
file manager
Material settings screen
```

Primary theme:

```text
light / soft-neutral
pastel object colors
restrained pixel accents
large quiet content surfaces
```

Secondary theme:

```text
graphite / soft-dark
same semantic palette
same hierarchy
same object identity
```

Light and dark themes must represent the same system.

Dark mode must not become:

```text
neon
cyberpunk
RGB
glowing AI orb
```

---

# 30. Halo Presence

Notebook reinforces the replacement for the old Halo Orb.

Halo Presence is a living arrangement of soft spatial cells/pixels.

It communicates cognition through:

```text
composition
density
position
motion
persistence
```

Not through:

```text
robot eyes
glowing spheres
rainbow state colors
radial gauges
mechanical rings
```

The same primitive may appear at multiple scales:

```text
Home hero presence
navigation signal
loading indicator
memory activity
widget
watch
wearable
favicon
launcher mark
```

Notebook itself should use the Presence sparingly.

Content remains the hero.

---

# 31. Object Color Language

Pastel colors represent object identity, not runtime health.

Example:

```text
Mint       Halo / cognitive context
Blue       files / reference
Lavender   canvas / thinking
Yellow     notes / capture
Coral      task / attention
Pink       ideas / personal
```

Runtime semantics remain separate:

```text
Healthy    green
Warning    amber
Failure    red
Information blue
```

Never rely on color alone.

---

# 32. Notebook UI States

Required UI states:

```text
Loading
Empty
Populated
Searching
Filtering
Editing
Offline
Error
Importing
Indexing
```

Examples:

Empty:

```text
Notebook

Your workspace is empty.

Create a note, import a file,
or start working with Halo.

[ New note ]
```

Not:

```text
NO DATA AVAILABLE
```

---

# 33. Home Integration

Home may expose a small amount of Notebook context.

Example:

```text
Ready

Working on
Halo Visual Language

2 memories · 4 workspace items

[ Ask Halo... ]
```

Do not reproduce Notebook itself on Home.

Home = conversation-first presence + deterministic Today projection; Workspace remains the owner of its material.

Notebook = persistent work.

---

# 34. Activity Integration

Activity entries may link to Notebook objects.

Example:

```text
14:12
Captured photo

IMG_20260918_1412.jpg

[ Open in Notebook ]
```

Completed Halo output:

```text
16:21
Created note

Halo Notebook Blueprint

[ Open ]
```

Activity therefore becomes an audit trail into persistent workspace objects.

---

# 35. Device Integration

Devices may create Notebook objects.

Examples:

```text
HeyCyan camera
→ Media object

HeyCyan voice note
→ Audio object
→ optional transcript

BuddyBot camera
→ Image object

Phone share sheet
→ File object
```

Device drivers must not know Notebook implementation details.

Flow:

```text
Device
 ↓
Host / driver
 ↓
Runtime observation or host media event
 ↓
Application use case
 ↓
Workspace repository
```

---

# 36. Wearable Workflow

Notebook must support Halo's wearable future.

Example:

```text
User wearing glasses

"Remember this design."

Camera capture
      +
speech context
      ↓
Halo
      ↓
Notebook Inbox
      ↓
Image + transcript + timestamp + device provenance
```

Later:

```text
"Open what I captured at the machine yesterday."

Notebook retrieval
      ↓
Halo response
      ↓
wearable output
```

This is one of the core reasons Notebook belongs in Halo rather than being an unrelated notes feature.

---

# 37. AI Independence

Notebook must remain fully useful with no LLM configured.

Without AI, user can still:

```text
create notes
edit notes
import files
organize projects
search text
browse memory
manage working set
use canvas
export data
```

AI enhances the workspace.

AI does not define it.

---

# 38. Failure Isolation

Failure of:

```text
LLM
STT
TTS
embedding model
device connection
network
```

must not prevent Notebook from opening or editing existing local content.

Notebook persistence belongs below provider availability.

---

# 39. Suggested Module Structure

Initial proposal:

```text
core/
  workspace-api/

runtime/
  memory/

app/
  notebook/
    presentation/
    application/

storage/
  workspace-room/
  workspace-files/
```

Exact module boundaries should follow existing repository conventions and dependency rules.

Do not introduce modules purely for symmetry.

A smaller initial structure is acceptable if dependencies remain clean.

---

# 40. Suggested Android Package Structure

Example:

```text
com.halo.app.notebook

├── ui
│   ├── NotebookScreen
│   ├── NotebookViewModel
│   ├── NoteEditorScreen
│   ├── MemoryScreen
│   ├── FileViewerScreen
│   └── CanvasScreen
│
├── application
│   ├── CreateNote
│   ├── SearchNotebook
│   ├── PromoteToMemory
│   └── ManageWorkingSet
│
└── mapper
```

Storage implementation should not live inside UI packages.

---

# 41. Initial Navigation Routes

Suggested routes:

```text
notebook

notebook/search

notebook/note/{id}

notebook/conversation/{id}

notebook/memory/{id}

notebook/file/{id}

notebook/canvas/{id}

notebook/project/{id}

notebook/inbox
```

Do not make every filter a route unless deep linking requires it.

---

# 42. Alpha Scope

The first useful Notebook release should include:

### Core

* Notebook primary navigation tab
* workspace object model
* Notes
* Conversations
* Files
* Memory browser
* Working Set
* Search
* Inbox
* local persistence

### Editing

* note editor
* autosave
* basic Markdown
* object titles
* tags
* archive/delete

### Context

* add/remove from Working Set
* promote text/object to memory
* provenance display

### Integration

* Activity → Notebook links
* Home → Working Set summary
* Android file import/share
* HeyCyan media → Inbox

---

# 43. Beta Scope

After alpha is stable:

* canvas
* collections/projects
* object relationships
* hybrid semantic search
* memory revision history
* richer file previews
* audio transcription attachment
* image understanding
* contextual Halo actions
* export/import
* automatic related-content suggestions

---

# 44. Later Scope

Potential later development:

```text
workspace sync between user-owned devices

collaborative spaces

versioned documents

structured tables

plugins manipulating workspace objects

automations

workspace templates

external folders

desktop Halo workspace
```

None of these belong in the initial implementation.

---

# 45. Explicit Non-Goals

Notebook Alpha is not:

```text
Google Drive replacement

Notion replacement

Obsidian clone

full office suite

Photos app

Dropbox

IDE

Figma

general database builder

cloud collaboration platform
```

It is Halo's cognitive workspace.

Every feature must strengthen that purpose.

---

# 46. Architecture Guardrails

Notebook implementation must preserve the existing Halo constitution.

Required:

```text
No Android SDK inside runtime.

No UI logic inside runtime.

No provider-specific types inside workspace contracts.

No direct Room access from Compose.

No global mutable Notebook state.

No automatic conversion of all content into memory.

No hidden destructive memory mutation.

No driver dependency on Notebook UI.

No LLM dependency for basic workspace functionality.
```

The expected dependency flow remains:

```text
Compose
 ↓
ViewModel
 ↓
UseCase
 ↓
Workspace / Memory Interface
 ↓
Repository
 ↓
Storage implementation
```

---

# 47. PROJECT-DIRECTION Update

When Notebook development begins, `PROJECT-DIRECTION.md` should be amended.

Current primary navigation:

```text
Home
Activity
Devices
Settings
```

New primary navigation:

```text
Home
Activity
Notebook
Devices
Settings
```

Canonical definition to add:

> **Notebook** is Halo's persistent cognitive workspace and user-facing data management surface for notes, conversations, memory, files, artifacts, canvases, working context, and related information.

The previous statement that UI is fundamentally "Halo Glass" should also be revised alongside the new visual-language specification rather than allowing implementation and authoritative documentation to diverge.

---

# 48. Minimum Useful End-to-End Flow

The feature should not be considered implemented until this flow works:

```text
1. User opens Notebook.

2. User creates:
   "Halo UI ideas"

3. User writes:
   "Use soft pixel cells for Halo Presence."

4. User selects that text.

5. User chooses:
   Remember

6. Halo creates a MemoryRecord.

7. Memory provenance points to:
   Note → Halo UI ideas

8. User adds the note to Working Set.

9. User goes Home.

10. Home shows:
    Working on Halo UI ideas

11. User asks:
    "Continue the visual language work."

12. Context retrieval exposes the note and relevant memory.

13. Halo responds using them.

14. Activity records the interaction.

15. User can return from Activity to the source note.
```

That closes the complete loop:

```text
create
→ remember
→ contextualize
→ reason
→ act
→ record
→ continue
```

Anything less is disconnected UI.

---

# 49. Acceptance Criteria

Notebook Alpha is successful when:

* Notebook is a primary destination.
* Notes survive process death and device restart.
* User can create/edit/delete/archive notes.
* User can browse stored conversations.
* User can import and reference files.
* User can inspect Halo memory.
* Every memory exposes provenance.
* User can edit or forget memory.
* User can explicitly add objects to the Working Set.
* Halo can consume Working Set context through a stable interface.
* Activity can link to persistent workspace objects.
* Device-created media can enter Notebook Inbox.
* Search works locally without an AI provider.
* Notebook remains usable with providers offline.
* No runtime architectural guardrail is violated.
* Data can eventually be exported without proprietary lock-in.

---

# 50. Product Test

Every Notebook feature should answer:

> Does this help the user capture, understand, organize, remember, connect, or continue meaningful work?

If not, it probably does not belong in Notebook.

And every memory-related feature should answer:

> Can the user understand what Halo knows, why it knows it, and change it?

If not, it does not meet Halo's trust model.

---

# 51. Final Product Model

The complete five-surface Halo model becomes:

```text
HOME
Halo now.

ACTIVITY
What Halo did.

NOTEBOOK
What you and Halo know and are working on.

DEVICES
Where Halo can perceive and act.

SETTINGS
How Halo behaves.
```

Notebook is therefore not an accessory.

It is the bridge between Halo's runtime intelligence and the user's persistent world.

Without Notebook, Halo can think and act.

With Notebook, Halo can **continue**.

---

**End of Blueprint**

## MINING-P7-001 — personal intelligence and reflection (2026-09-30)

P7 makes Notebook Memory and Inbox the review surface for personal learning. Confirmed records stay canonical Workspace MEMORY objects; inferred candidates are learning projections, never current facts or Working Set context. Episodes show public historical evidence and can be explicitly kept in Brain. Inline conflict review preserves user control. Procedure/routine suggestions open existing reviewed owners; Home only links to Inbox and Quiet does not surface learning.

See [P7 implementation ledger](../engineering/PERSONAL-INTELLIGENCE-REFLECTION.md).
