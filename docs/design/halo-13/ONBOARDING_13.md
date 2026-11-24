# halo 13.0 · Batch 2 — Onboarding

**From 10 technical stages to 4 human steps.** This is checked against repo `slafftrosheen/Halo` at **99aac2f** (23 Nov 2026). It's a design and developer spec, not a build, and nothing has been tested on a device.

Boards: 143 brief and mapping · 144 the four steps · 145 asking when needed and setup cards · 146 kids and family · 147 moving phones, comfort and trouble paths · 148 icons, motion and files.
It builds on batch 1: depths (`ExperienceLevel`), plain words, and Looks.

---

## 1. Today vs 13.0

Today, `ui/onboarding/HaloSetupScreen.kt` walks through `SetupStage` in this order: WELCOME, USE, PERMISSIONS, AI, SPEECH, DEVICES, PRIVACY, EVERYDAY, READINESS, FIRST. The progress bar shows "Setup · n / 10", and the screens use words like "listening model", "provider" and "diagnostics".

| Today | 13.0 |
|---|---|
| Welcome · How would you like to start? | **Hello** + **Who is halo for?** |
| (new) | **How much do you want to see?** (Simple · Standard · Pro) |
| How Halo answers | **Where should halo think?** (On this phone · Online AI service · Decide later) |
| You choose the access | Asked the first time a feature is used (§3), never up front |
| Listening and speaking | First tap on the ring, or the setup card "Let halo hear you" |
| Your devices | When a device is nearby, or the card "Use halo on your glasses" |
| Your data, your choice | One plain line on step 4, plus Settings → **What halo can use** |
| Your day · Let's check what works · Try Halo for real | **The first real try on Home**, then one card a day |

**Targets:**
- at most 4 steps
- under 60 seconds to the first real answer
- **0** permission prompts during onboarding
- **0** tech words in onboarding strings

## 2. The four steps — `OnboardingFlow.kt`, `onboarding-13.json`

**1 · Hello**
- The halo ring, "Hi, I'm halo" and one line about what halo does.
- Language comes from the phone and can be changed from a small menu.
- **Make it easier to use** offers larger text, read aloud, stronger contrast and less motion. These apply live, straight away.
- The main button is **Let's start**. Below it is a small link: "I've used halo before" (§6).

**2 · Who is halo for?**
- Options: Me · My child · My family · Studying · Creating. More than one can be picked.
- The choice sets:
  - the dock and Home: Studying puts Study in the dock, Creating puts Canvas there
  - the first try on Home
  - which setup cards appear
- Choosing only "My child" goes to the hand-over (§5).

**3 · How much do you want to see?**
- Options: Keep it simple · A bit of everything · Show me everything.
- Three mini previews show the depth live.
- Larger text or read aloud makes Simple the starting depth.

**4 · Where should halo think?**
- **On this phone**: private and free. One download, on Wi-Fi only, in the background. The size is shown before it starts.
- **With an online AI service**: a faster start. Sign-in opens only after Home.
- **Decide later**: start with things that need no AI brain (notes, drawing, timers, Planner, calc).
- One privacy line: "Nothing you say or show leaves this phone unless you choose an online service."

**Every step has:**
- **Just open halo**, which fills in defaults (`withDefaults`): who = Me, depth = Standard (Simple with comfort settings), thinking = Decide later
- Back, which never loses a choice
- dots for progress, never "5 / 10"

**Then Home** shows "Try it: what's on today?" with 3–4 suggestions that fit who halo is for (`firstTry`), plus the composer.

## 3. Asking when needed — `AskWhenNeeded.kt`, `ask-when-needed.json`

All **17** access ids from `CapabilityGuideProgress.ids` get a feature name, a "why" and an "if not", in EN and RU.

How a request works:
1. The person starts a feature (taps the mic, opens the camera, adds a reminder).
2. halo shows its sheet: what for · what stays on the phone · what happens if you say no. The buttons are **Allow · Only this time** (where Android supports it) **· Not now**.
3. Android asks.
4. If Android has denied twice, it won't ask again. The feature then shows a quiet card: "Turn on in phone settings — two taps".
5. After three "Not now" answers, halo stops asking. A small "Turn on" button stays in that feature.

Never ask at app start, during onboarding, or when the person didn't start the feature. Keep `CapabilityGuideProgress.markCapabilityRequested` ("interrupted requests must never automatically re-prompt").

Settings → **What halo can use** shows 6 plain rows (Hear you, Camera, Your calendar, Reminders, Where you are, Nearby devices). The other 11 appear under "More for experts" (Pro).

## 4. Setup cards — `SetupCards.kt`, `setup-cards.json`

Rules:
- At most **one card a day** on Today, starting on **day two**. Day one is for trying, not configuring.
- Each card is a benefit, not a chore: "Let halo hear you", "Make halo work offline", "See your plans in one place", "Make it yours", "Use halo on your glasses", "Set up halo for your child", "Add your course books".
- Buttons: Try it · Maybe later. After two "Maybe later" answers, the card is gone for good. It stays reachable in Settings → What halo can do.

## 5. Kids & family — `KidsHandOver.kt`

The flow runs: grown-up check → about the child → rules → hand-over → child's hello → child picks three things.

- **Grown-up check:** the device PIN or fingerprint, via the existing `KidsGrownUpGate`.
- **About the child:**
  - a name or nickname
  - an age band: 4–6, 7–9 or 10–12
  - plain defaults the grown-up can change: homework time 16:00–18:00, homework photos ("Ask me first"), kids-safe sources, and "No ads, no points, no chat with strangers"
- **Hand-over:** a big "Now hand the phone to Mia" screen with the hand-over animation and an "I'm Mia!" button. Grown-ups get back by holding the corner for 3 seconds.
- **Child's first minute:** the buddy speaks every line. The child picks three things they like (pictures with labels; pictures only for ages 4–6). These seed KidsWorld islands.
- No points, scores or streaks. Stars and "Let's look again" are the only feedback. The camera and mic are asked for the first time the child taps them, and a grown-up confirms with the PIN.
- **Family view:** who's here, the shared Planner, "Set up Leo", and what grown-ups can see. They see what the children made and learned, never the buddy chats word for word.

## 6. Moving from an old phone — `MoveFromOldPhone.kt`

Three ways in: **Old phone is here** (scan a code) · **Use a backup file** (`backup/EverydayArchive.kt`) · **Start fresh**.

**Phone to phone:**
- A one-time 16-byte code in the QR, valid for **10 minutes**.
- Both screens show the same **3 words**, and the person confirms they match.
- Transfer is end-to-end encrypted and stays on the local network.
- **This needs a security review before it ships.**

Things to bring over are tick boxes: notes and drawings, Planner, sources, looks and wallpaper, kids profiles, study, settings. **Never moved:** AI service keys and the kids' grown-up PIN.

## 7. Comfort and trouble paths
- With **comfort on** (larger text, read aloud, stronger contrast), the whole flow is big and spoken, the depth starts on Simple, and the look becomes "Clear & big".
- **No internet:** "That's fine. halo still works for notes, drawing, timers and your planner." The download waits for Wi-Fi.
- **Download:** a progress card on Home with the real time left, plus Pause.
- **Not enough space:** the needed and free size, with Use online instead · Free up space · Later. Sizes on the boards are examples.
- Every error follows the batch 1 rule: what happened → what halo did → what you can do.

## 8. Icons and motion

**18 icons:** hello, for_me, for_child, family, studying, creating, try_it, ask_access, allow_once, not_now, move_phone, restore, qr_code, hand_over, age_band, setup_card, finish_later, buddy.

**5 Lottie animations** (256 × 256, 30 fps, dark and light):

| Name | Frames | Use |
|---|---|---|
| hello | 90 | the ring draws in and the gap sparks |
| handover | 90 | the phone moves to the child's hand and a star appears |
| ask_access | 75 | a shield with a soft pulse, then a tick |
| move_phone | 90 | dots travel from the old phone and the new phone fills |
| ready | 75 | a calm bloom with no confetti |

## 9. Strings
`strings_onboarding_13.xml` has 54 strings in EN and RU. Adults are addressed with «вы» and kids with «ты»; buddy is «бадди», and halo stays in Latin letters. Russian avoids gendered past tense: "I've used halo before" → «У меня уже был halo».

## 10. Rollout and QA
- Ship behind the flag `onboarding_v13`, for new installs only. Existing users never see onboarding again.
- Keep `HaloSetupScreen` until the people tests pass. Each person must reach a real answer without help:
  - a 70-year-old using large text
  - a parent setting up a 6-year-old
  - a student who skips everything
  - a TalkBack user with eyes closed
- [ ] No Android permission dialog appears before Home.
- [ ] "Just open halo" from every step gives a working Home.
- [ ] Back never loses a choice. Leaving mid-way opens Home with defaults.
- [ ] Setup cards: none on day one, at most one a day, gone after two dismissals.
- [ ] Kids: the gate is required, the buddy speaks every line, and nothing scores.
- [ ] Moving: the code expires after 10 minutes and a word mismatch stops the transfer. Security review signed off.
- [ ] The plain-words ratchet test passes. A native Russian speaker reviews the strings.

## 11. Open decisions for the team
1. Should "Decide later" ask again on day 3 as a card, or only when the person asks something that needs AI?
2. Should the online sign-in happen inside halo (an account) or with the person's own service key (Pro)?
3. Should the family view live in Settings or on its own page?
4. Is the backup file a halo format (`EverydayArchive`) only, or should it also import from other notes apps?
