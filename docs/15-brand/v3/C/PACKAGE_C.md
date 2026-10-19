# halo · Package C — Notebook · writing with halo · Canvas · files · Memory · saved chats · Study
Brand Book v3.0 track · chapter 17 · repo pinned `3f217ccb` · 2026-10-19
Follows `../USER_FACING.md` (nothing technical outside Advanced) and the 2027 light layer from Package B.
Covers **NB01–NB08, S01–S05**. The existing learning designs (DevKit 1.4: S40–S47 and the Kids K/H/C series) remain the detailed tutor and Kids references. S01–S05 are the finished adult screens, drawn in the same system as the rest of the app.
Design status: designed. Implementation status: **not verified on a device**.

## What's in the folder
| Folder | Contents |
|---|---|
| `screens/` | 134 PNGs at 2× (dark/light, every state, plus 412dp, 200% text, accents × tones) |
| `boards/` | Chapter 17: 13 screen boards, 2 journey pages, variants, motion + plain-words table |
| `motion/` | Films (1080×1920, 30 fps): write with halo (dark + light), think on a canvas, learn step by step, memory |
| `lottie/` | `halo_fx_apply_draft`, `halo_fx_memory_forget` (dark/light + stills) |
| `spec/package_c.json` | Interactions, states → next action, motion rows, journeys, plain-words fixes |
| `strings/` | Package C keys (English final, lv/ru/pl empty and ready) |

Source files and the clickable prototype (now 16 journeys covering Packages A–C) are in `B/source/`.

## Rules this package locks
1. **Editors are for focus.** The nav hides, the top bar keeps read aloud and ⋯, and a floating toolbar holds Add · Bold · List · Dictate · Ask halo · Undo. The aura stays off while you write and only appears while halo is drafting.
2. **halo never edits your note directly.** Drafts appear under the note, dashed, labelled "Draft by halo · not saved". The options are Apply to note (with Undo), Save as new note, Try again, or Discard. If your note changed meanwhile, Apply is replaced by "Write it again".
3. **Dictation is a preview.** Nothing is inserted until you tap "Add to note".
4. **One step ahead, gently.** The editor offers at most 2 suggestion chips (e.g. "Make packing a checklist"). The canvas offers one ("Group the 4 kitchen cards?"). Nothing is ever applied automatically.
5. **Voice memos and photos stay honest.** Text is transcribed on this phone, and words that were hard to hear or read are highlighted to check. "Keep audio only" is always offered.
6. **Memory is your call.** halo remembers nothing without a yes (Remember / Edit / Not true). Updates show old vs new. Every memory says why halo remembers it and where it's used. Forget takes effect immediately and keeps your notes.
7. **Study never chooses the cloud for you.** On the "Where should halo think?" step neither option is preselected, and Start stays disabled until you choose. "Show answer" asks first. Review cards flip; with reduced motion they cross-fade.
8. **Trash keeps items for 30 days.** Every deletion has Undo or Restore.

## Plain words (from today's source)
| Screen (file) | Today | Finished app |
|---|---|---|
| Study (`StudyScreen.kt`) | Memory decay · AI comparison · rate yourself · Configure vision | How well you remember · Compare with halo's answer · Let halo see photos |
| Study home (`StudyHomeScreen.kt`) | Image · vision required · PDF pages may need image AI | "halo can't read photos yet", with alternatives |
| File detail (`WorkspaceObjectDetailScreen.kt`) | Material AI access · File information | What halo can read · details described in words |
| Note editor (`NoteEditorScreen.kt`) | Markdown source… · Some syntax is not representable | "Plain text view" in ⋯, warning in words |
| Canvas (`CanvasEditorScreen.kt`) | Processing image locally… · Recognize source text (page 1) | Working on this phone… · Read the text on this page |
| Memory (`MemoryHubScreen.kt`) | All scopes · Prevents normal retention cleanup… | Everywhere · Keep this in history |

## Acceptance (per screen)
- [ ] Dark + light match `screens/` at 360 and 412dp, and nothing is clipped at 200% text
- [ ] Every interaction row fires its event, and Back goes where the board says (editors save first)
- [ ] Drafts are never applied without a tap. Undo works after every apply and delete
- [ ] Motion plays only on the listed real event, and reduced motion shows the still
- [ ] No technical words (see `USER_FACING.md` §2)
