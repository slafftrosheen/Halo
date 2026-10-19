# halo · Final batch — Appearance · Language · app-wide states · Storage & backup · Advanced (Lab style)
Brand Book v3.0 · chapter 22 · repo pinned `3f217ccb` · 2026-10-19 · follows `../USER_FACING.md`.
Covers **P05, P06, XS01, P04** (user) and **I06, X01** (Advanced), plus **X02, VO06** (Advanced › Developer tools).

## User screens
- **Appearance:**
  - **Theme:** Light / Dark / Same as phone, 4 accents × 3 tones, and a live preview.
  - **Text size** follows Android and can go bigger. Cards grow instead of cutting words.
  - **Easier to see:** higher contrast, less motion and a guide-light switch.
  - **24-theme matrix on a busy screen** (G11): Apricot, red and the Mint logo ring mean the same thing in every theme.
- **Language:** EN / LV / RU (PL marked "coming soon", because there's no `values-pl` folder yet). It applies instantly with Undo. The listening and answer languages are separate. A running task finishes in its original language.
- **App-wide states:**
  - Loading shows a skeleton, never a blank screen.
  - Offline says what still works.
  - When storage is full, downloads pause and resume later.
  - When a key expires, the request is kept.
  - If a device is lost mid-task, the step fails honestly.
  - Sheets ride above the keyboard.
  - Landscape switches to the side rail.
- **Storage & backup:**
  - Backups are locked with your password, and downloads are excluded.
  - Restore can add to what's on the phone or replace it, with undo for 7 days.
  - Conflicts offer: keep both, keep this phone's, or keep the backup's.
  - "Files that moved" has a Find the file button for each one.
  - Clearing temporary files asks to confirm.

## Advanced (Lab style)
A Lilac "Advanced" or "Developer tools · for testing" bar, monospace values and no animation, so these pages are never mistaken for the everyday app. Model roles and the Technical report are always in Advanced. Runtime status, Vision Lab, the sandboxes and Speech Lab only appear once Developer tools is on. The sandboxes simulate actions and never send anything. Speech Lab says "One sample, this phone, this moment. Not a general accuracy score."

## Folder
`screens/` 86 PNGs, plus 24 theme frames · `boards/` chapter 22 (12 pages) · `motion/` films: appearance and states (dark + light), Advanced · `spec/package_g.json` · `strings/`.
