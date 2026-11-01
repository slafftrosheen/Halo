# Halo Workspace tool contract

Status: Implemented source contract; physical and participant acceptance remains separate.

Updated 31 October 2026 from the user's workspace and orb direction. This extends Zone 5; the user's choices override the asset pack's thin orb artwork and fixed instrument colours.

| Instrument | Purpose | Canonical content |
|---|---|---|
| HaloStudio | Manual graphics: images, drawing, text layers, shapes, composition and export | Revisioned Studio projects and source-pinned image layers |
| HaloNotes | Text, structured notes, supported documents and tables | Existing notes, text/CSV documents and bounded PDF workflows |
| HaloStudy | Gather source material, learn, practise and review | Existing source references, lessons and practice sessions |
| HaloMemory | Review and manage saved context | Existing facts, suggestions, scopes, sources and learning history |
| HaloCanvas | Bring material from the other instruments together to visualise an idea | Existing cards, file references, groups, edges and canvas layouts |

Library contains saved objects. Tools opens the five instruments directly. A tool is not a file: tool symbols use the active accent; document/file symbols use a distinct companion ink derived from that same theme. Names, document shapes and type labels remain visible independently of colour. Existing IDs, routes, versions and compatibility aliases remain valid.

## Shared placement

1. Normal portrait reserves exactly one quarter of usable height (after status/navigation insets) for the Halo Zone. The other three quarters contain the document, artboard, lesson or saved content. File identity, tool menus and permanent editing docks do not consume that working area.
2. File/document identity and truthful state live in the Zone utility row. File, Edit, Add, View and More follow, using the same current-entry action lease. Menus and temporary inspectors open from the Zone. Opening them never edits content.
3. Studio's upper area contains only its fitted artboard. Drawing, text, shapes, image import, layers, transforms, undo/redo, save, export and continuity use the Zone menus. Notes and Canvas open their existing editing panels from those same menu positions; their permanent body docks are removed in the shared shell.
4. System Back owns return navigation. Duplicate Back buttons and Back entries do not occupy the Zone. Existing page BackHandlers still flush notes, leave canvases and close Studio/Study subviews through their canonical owners.
5. Settings, keyboard-visible and short windows use a single compact navigation row. All actions retains Library/Tools switching and every Studio overflow operation; compact utility controls remain available inside that sheet. Home retains the focused composer at one composition call site. Large text uses an accessible, content-sized fallback. Normal layouts measure identity, menus and navigation: menus move into grouped Actions first, then identity/secondary controls if necessary. Full identity and state remain readable in Actions without essential ellipsis; the normal whole Zone stays one quarter.
6. Workspace Library/Tools switching belongs to the Zone. Tool/file choices, document text, graphics, lesson interactions and form fields remain working content. Supported formats and operations stay truthful; the interface claims no new round-trip format support.

## Kids parity

The same quarter-height Zone, compact keyboard row, active palette, classic thick orb, wallpaper and safe guidance apply in Kids mode and permitted child editors. Child choices, learning answers and manual creation remain working content. Page identity, status, Type, Help, Stop and parent options use a narrow Kids Home projection; system Back closes input and returns to the existing choices. Hosted typing uses the working area so the Zone stays above the keyboard. Child footer and action targets retain at least 56dp.

Presentation parity does not widen child access. The Kids menu retains only Home and Study; adult privacy/mode controls are omitted. Parent options still open the original Android device-credential gate, and no parent or destructive action receives a next-step hint. Hints pause during child activity, input, help and parent disclosure. Sharing, source restrictions, pre-reader/TalkBack behavior and Keep/Discard ownership remain unchanged.

## HaloStudio workbench

The fitted artboard owns the full upper working area of the graphics editor. File names, dimensions and menu controls live in the Halo Zone. Manual image import, drawing, text, shapes, layers and transforms take precedence. File/save/export and history are grouped in the shared menus. The layer inspector identifies selection, visibility, locking and editable properties. AI work remains an explicitly selected secondary action with the existing review/consent boundary. It is not the default graphics workflow.

## Guidance and effects

A next-step hint identifies a currently enabled non-destructive action through the current entry lease. The cue requires a visible target. Its optional explanation appears only when the measured spare Zone budget fits the complete text and a real Dismiss control; accessibility timeout policy applies. Guidance never grows the normal quarter-height Zone. A single halo lap settles into a static cue. Invoking a command is not completion: actual owner receipts suppress completed hint versions. Explicit dismissal persists separately.

Actions offers contextual Help and requested step-by-step tasks. Help explains purpose, examples, current steps and unavailable reasons. It never starts a microphone or invokes a task automatically. Optional reading uses the existing speech owner and is omitted when TalkBack touch exploration is active. Requested guidance stays usable in compact layouts or with Guide light off; real inspectors temporarily own input, and guided Help can resume after they close. Pause, Exit and explicit Close stop that continuation. Entry/object changes fence the presentation. Guidance amount, Guide light, font, contrast and motion remain independent preferences.

Proactive hints are suppressed during typing, voice, task/recovery, menus, inspectors, review and parent disclosure. Real feedback and user interaction take priority over guidance, and both pause ambient decoration. No effect changes storage, permission, completion or child authority.

Orb animations use the older thick rounded ring, layered glow and state effects at both Zone and larger sizes. Static/reduced-motion paint matches that geometry. Idle settles; active motion comes from the actual state. All paint follows the selected theme's semantic roles.

Dynamic backgrounds remain decorative and sit behind readable surfaces. Halo uses open-ring fragments; Mist uses soft atmosphere. Motion is slow, bounded and paused outside the foreground or under the existing restrictions. Colour changes repaint the wallpaper and controls together.

## Extension rule and Screen roadmap

New functionality is an instrument or command group inside its existing tool family, not another top-level tool. New text operations belong in HaloNotes; graphics in HaloStudio; learning/source work in HaloStudy; saved context in HaloMemory; cross-file idea visualisation in HaloCanvas. Keep the same menu groups, inspector placement and scoped action owner.

Screen owns the planned Halo calendar, plans/time management and notifications, weather, clock and alarms. These are to be user-managed Halo capabilities with explicit source, location, scheduling and notification controls. OEM calendar/clock differences must not become hidden dependencies. This is a functionality roadmap: the present calendar projection, unavailable weather state and clock must remain truthful until their replacements have real storage, scheduling, permission and recovery owners. A visual update must not imply an alarm is scheduled or a weather source exists.

## Acceptance evidence

Measure the 75/25 working/Zone split on the phone and verify no persistent file/menu chrome in the upper editor. Verify the five launchers and system-Back return paths, shared menu groups and scoped commands, manual Studio editing and export, file/tool distinction in light/dark accents, focused keyboard transition, compact Settings, large text/landscape, persisted guidance dismissal and suppression, and classic orb rendering. Host, synthetic native-renderer fixtures, actual phone navigation, real speech/provider and hardware evidence must be reported separately.

## Planned usability and clarity work

The [roadmap to 10/10](ROADMAP-TO-10-2026-11-01.md) defines proposed improvements, delivery batches and acceptance evidence. Implementation and current verification are recorded in [the implementation register](PREDICTABLE-UX-IMPLEMENTATION.md). Physical, assistive and participant acceptance remain separately recorded.
