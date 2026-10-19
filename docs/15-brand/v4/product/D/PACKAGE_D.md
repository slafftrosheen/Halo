# halo · Package D — Kids mode + voice & reading for everyone
Brand Book v3.0 track · chapter 18 · repo pinned `3f217ccb` · 2026-10-19
Follows `../USER_FACING.md` and the light layer. Covers **K01–K05** (Kids) and **VA01–VA03** (read-aloud bar, voice fallbacks, screen reader and captions).
Design status: designed. Implementation status: **not verified on a device**.

## Reconciled with DevKit 1.4
| Finished screen | Earlier detailed specs it supersedes visually (logic still valid) |
|---|---|
| K01 Kids home + gate | K00, G90 |
| K02 Learn journey | K10–K14 |
| K03 Homework | H20–H23 |
| K04 Create | C30–C34 |
| K05 Story reader | K15–K17 |
| VA01–VA03 | V06 (read-aloud bar), V07 (voice states), field kinds from V05 |

The 1.4 contracts (tutor stages, hint ladder, field kinds, `voiceMayApprove`) stay valid. These frames are the finished look in the same system as the rest of the app.

## Kids rules (locked)
- **Warm tone by default:** cream `#FFF3EA` light and plum `#160E1C` dark, so Kids mode is recognisable at a glance. Accents still work, and the logo ring stays Mint.
- **Sizing:** targets ≥ 56dp, the main mic 72dp, body text ≥ 18sp, **at most 3 choices** per screen, plus "or say anything".
- **Talk first.** The dock is Type · 72dp mic · "What can I say?". There's no nav pill.
- **The grown-up gate uses the phone's real screen lock.** If there's no lock: "This phone needs a screen lock". Never a guessable PIN. The grown-up gate guards the camera the first time, sharing, settings, and leaving Kids mode.
- **Honest homework.** Text halo isn't sure about is orange and must be checked. "Answer" asks first.
- **Create:** nothing is saved until **Keep**. Share always asks a grown-up. Cloud stays off unless a grown-up allows it.
- **Reading:** words glow only on real voice timing. "Read it myself" shows **What halo heard**, with no score. Larger text and more spacing are one tap. With no reading voice, the screen says "Read it together with a grown-up, or read it yourself".

## Voice & reading for everyone (VA01–VA03)
- **VA01 Read-aloud bar** (global): previous · pause · next sentence · progress · speed · stop. The sheet has speed, voice with preview, and "Highlight as it reads". You can play on this phone, headphones or glasses, but only connected devices can be chosen. Pressing the mic always stops reading.
- **VA02 When voice can't be used,** there's always a way forward:
  - Mic busy → Type instead.
  - No listening pack → Download, with the size shown and a note that it stays on the phone.
  - No reading voice → Choose a voice. Until then, text only.
  - Password fields never offer dictation, and the screen explains why.
- **VA03 Screen reader and captions:** numbered TalkBack focus order for Home and Kids (drawn from real element positions), and large captions with the current word lit.

## Folder
`screens/` 86 PNGs (Kids states in light + dark, 412dp, 200%, accents) · `boards/` chapter 18 (12 pages) · `motion/` films: kids learn (dark + light), kids create & read, kids homework, voice & reading · `lottie/` `halo_fx_kids_gate` (+ stills) · `spec/package_d.json` · `strings/` Package D keys (English, ready for translation).

## Plain words / copy fixes
"Full Halo" → "Full halo" · "Talk to Halo" → "Talk to halo" · "Share saved project · grown-up" → "Share · ask a grown-up" · the read-aloud bar's "Voice" opens the reading sheet, not technical speech settings.
