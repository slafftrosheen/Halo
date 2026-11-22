# Halo pack implementation review

The review used the 9.5 UX and instruments documents, the v6 brand book, the 9.5 pack, and the consolidated 9.5–11.0 pack and books. The two supplied 9.5 ZIPs held the same material. The extensionless brand book and numbered v6 PDF also matched. The 9.5 pack carries forward the 8.0 and 9.0 wallpaper, widget, and motion guides. Later flows take precedence where the packs disagree. The visuals and developer JSON describe expected behavior. The decisions below come from the project conversation.

## Visual and code changes

| Reference | Change in the app |
| --- | --- |
| 9.5 boards 80–81, instrument motion and guidance | **Watch, then try** plays a preview of the named instrument. **Try it** scrolls to the actual bound control. The preview respects the app's Still setting. The editor handles actual actions. |
| 9.5 board 82, article galleries | Reads and Today use actual saved lead pictures and the gallery strip on their headline cards. The source must be enabled, selected, unmuted, and on the matching version. Tapping the card opens the exact article. A text-only feed update can reuse the saved picture for the same article and image URL. Reads can speak the visible saved headlines, and Today can speak the generated report. |
| 10.0 boards 89 and 99, device setup | Four device categories use the supplied photos and localized prep instructions. Find opens the existing discovery screen. Device Controls previews only gestures reported by the connected endpoint. |
| 10.0 boards 96–97 and 10.5 board 109, first launch and routes | A named onboarding link opens the requested step. A plain onboarding link resumes prior progress. The link can only open the step. |
| 11.0 boards 114–115, coaching and one Library | Adult photo review uses Vision where available and keeps a local reading option. In Power, sending a reviewed adult photo to the configured Vision model takes a separate action. Private is on the Library page and keeps access to existing private papers. Search clears and reloads after locking or restoring items. |
| 11.0 board 116, Today | Pin and hide attach to the Planner occurrence. An older next-event setting is moved to its first concrete occurrence and stays there. New cards still append. |

## Flow cross-reference

This cross-reference records the paths and gaps found in source. The instrument catalogs have 147 and 39 entries with app metadata. Each action still needs a result check on the phone or accessory where it runs.

| Flow | Current path and remaining verification or implementation |
| --- | --- |
| A · Ask and act | Chat and the ring dispatch conversation and reviewable native actions. Check spoken and typed entry, source attribution, confirmation, and the operation's actual Activity receipt end to end. |
| B · Glasses photo | `ContextualCaptureController` owns capture review and its Keep, Ask, and Inbox handoffs. Verify supported hardware events, stack timing, and placement above the dock. |
| C · Device setup | The guide opens glasses discovery, Android Bluetooth settings for headphones, Follow me for supported displays, or BuddyBot Wi-Fi setup. Android owns its pairing prompts. BuddyBot waits for the existing pairing acknowledgment and discards the session token on disconnect. Vendor-specific spoken codes and automatic earbud fit checks need device protocol support. |
| D · Share anything | Existing workspace and tool reviews hand off to Android sharing. A common recipient suggestion and in-app undo across every tool are still open. |
| E · Pay | `HaloMerchantCheckout` uses the agreed reviewed merchant checkout handoff. The merchant owns payment and receipts. |
| F · Use a tool | `HaloToolkit` opens editors and `HaloInstrumentBar` uses their action bindings. Editor autosave, undo, shortcuts, and result across levels still need per-action verification. |
| G · Morning | Today, the Day report worker, and Follow Me cover local content and selected outputs. Scheduled delivery and rendering on the target ink device need verification. |
| H · Something needs you | The Devices badge and BuddyBot card follow live, connected obstacle and motor safety telemetry. The help screen gives the corresponding obstacle or fault instructions. Other device-specific events need documented protocol support. |
| K1 · Start | Family settings and Kids Home control the child session. Check layout selection and time left against stored limits. |
| K2 · Learn | Kids dynamic cards, child Study and My book are the owners. Check picture/audio playback and keeping a completed activity in My book. |
| K3 · What is this? | Kids Scan owns on-device object identification and the grown-up-approved cloud choice. Check permission revocation and keeping an approved result. |
| K4 · Needs a grown-up | Family approvals use the enrolled phone. The agreed family sharing path is reviewed file sharing. A request approval exchange with a separate parent phone remains open. |
| K5 · Time is up | The child time-limit path owns phone behavior. Physical buddy docking and ink sleep need a supported accessory command and a confirmed result. |
| K6 · Today's adventure | `KidsAdventure` owns the activities and progress. Check the three stops, saving to My book, and Still behavior together. |
| K7 · Homework helper | The scheduled child coach accepts supported questions from phone or approved glasses and uses a bounded local tutor. Remote parent delivery and physical buddy behavior are open. |
| S1 · Start a course | Study setup and course order own lesson plans. Verify creation with a configured model, reordering, and Today selection. |
| S2 · Lesson | Study owns lesson steps, help, and practice. Check live model answers, cloud speech, and keeping a card from each entry path. |
| S3 · Review | Study owns review scheduling. Review on an ink device, including writing or returning an interaction, requires an end-to-end device check. |
| S4 · Photo to cards | Capture review and Study extraction own the handoff. Verify full camera and glasses flows through editing and adding to the selected course. |
| S5 · Exam plan | Study previews 25-minute sessions and assigns ordered course lessons. The finite Planner series stores the exact lesson IDs and versions. Moving a session reflows colliding sessions in one transaction while keeping occurrence IDs and lesson order. Planner links each session to its lessons, and Today uses the moved occurrences. |
| S6 · Photo coach | `CoachPhotoIntake` and `LocalPhotoCoachSheet` own review, questions, help, and cards. The local coach covers supported arithmetic and acceleration. Broader worked subjects and physical live ink mirroring are unverified. |
| X1 · Offline answer | Local mode and the model router own on-phone inference. An on-device model must be installed to verify the offline fallback. |
| X2 · Key failure | Connections and setup diagnostics own provider checks and repair. Verify recovery and an actual local reply with a revoked key. |
| Check a key | Provider controls and setup diagnostics own validation. Validate with provider-specific live results. |
| Scan from computer | The pack specifies a one-time encrypted `halo.link/key` transfer. No configured relay or matching transfer integration was found. |
| Use a key | Provider router and policy own dispatch. Provider costs, limit review, and fallback still need a provider-by-provider check. |
| L1 · Find anything | Library search groups results and opens the source. Receipt questions total saved receipts by merchant and purchase month, report currencies separately, and link the contributing versions. OCR and search coverage are bounded and flagged. |
| L2 · Tidy up | Library naming, collection suggestions, and undo use the existing organization owner. Check original-name history and cross-tool display names on real data. |
| L3 · Make it private | The private store owns item and collection locking, including related backing data. Library is the user-facing entry. Validate restore and denial paths on a device with chats, linked automations, tool results, shared paths, and large attachments. |
| M1 · Go somewhere | Today and Planner open the route. Offline routing, the turn card, and selected output have app paths. The current offline route profiles are the agreed scope. Test that the glasses show only the current turn and that guidance stops correctly. |
| M2 · Share arrival | The route reviews a time-only estimate before Android sharing. Recipient-specific live updates remain open. |
| T1 · Today | The Today deck uses local projections and retains the order of visible cards. Phone Calendar events use the same card actions as Planner occurrences. The hourly report is a dedicated panel. Verify time-of-day transitions, report cadence, and appearance with realistic data. |

## Wallpaper, widgets, and tool presentation

- **Wallpapers.** `HaloLiveWallpaperService` draws the system wallpaper and `WallpaperControls` owns apply and previews. Each variant still needs a home and lock screen comparison after changes to theme, motion, or launcher.
- **Widgets.** Everyday and toolkit widgets use Android `RemoteViews`. Calendar, clocks, weather, reports, and other cards get their data and intents from their domain owners. `EverydayWidgetMotion` handles supported transitions. The pack's appearance, resize cases, update motion, and launcher-dependent lock screen placement still need a visual check for each variant. Live transit departures remain off by choice.
- **Tools.** The six 11.0 additions are Maps, Coach, Calc, Measure, Translate, and Focus. Readiness depends on the underlying state, including offline maps, configured model or local language pair, camera permissions, and actual wearable capabilities. A visible instrument or registry entry alone is insufficient evidence.

## Follow-up implementation

- **Today and launcher.** Today has a launcher provider and an Add Today widget entry. The host gets aggregate calendar information and public weather or headlines. It opens Today for private detail. Widget rendering uses the actual positive dimensions reported by the launcher, with legacy orientation dimensions as fallback. The reported 5×9 issue awaits a launcher check.
- **Sources and Planner.** The assistant's source-save contract uses an exact public HTTP or HTTPS address and retains separate preview, registration and refresh operations. Public HTTP uses credential-free GET, public DNS and redirect checks. Model and provider transports retain their existing policies. Today groups event, task, reminder, alarm and timer creation in the existing Planner editor.
- **Scan and translation.** Scan imports photos or up to eight PDF pages into page review, reads a selected page through the configured Vision route, and can pass a saved scan to Study. In conversation translation, either speaker can translate a completed microphone turn immediately through the shared speech input path and the installed language pair. Cloud speech for translations has a separate opt-in.
- **Chat and homework.** Continue in Chat waits for the saved conversation to activate. Explicit plan, step and checklist sections can become interactive cards while the full reply stays accessible. Kids homework retains the current set of parsed questions and progress while the authorized homework window stays open. Image retention still requires its existing separate decision.

## Verification state

- **Exam prep.** The UI saves through `OrganizerUseCases.create` under revocable adult and pause authority. The registered `halo_organizer_create`, `halo_organizer_occurrence`, and `halo_planner_reschedule` operations cover the same persisted event series and exact occurrence edits. The registry gate and the date and reflow regressions await execution.
- **Receipt totals.** Library Search and `halo_library_receipt_totals` share the local totals owner. It calculates amounts from readable evidence, excludes protected items, and checks every contributing version before disclosure. Power results also follow per-item cloud policy. Receipt parser and device verification await execution.
- **Device setup.** Android and vendor handshakes remain owned by their supported adapters. The BuddyBot pairing regression covers the already implemented acknowledgment and token lifetime. Physical confirmation awaits the robot. Docking, screen sleep, and vendor fit checks require the device command and observed result for the exact hardware.
- **Reads and photos.** Existing `halo_source_query`, `halo_source_get`, and `halo_source_image` supply the selected source, revision, article, and exact saved image to the assistant. The new controls use the existing reading and Vision owners. Sharing a reviewed adult coach photo still follows Power and image policy. The child coach stays local. Physical playback and model output await verification.
- **Presentation and privacy.** The new onboarding route opens a screen. Gesture previews are demonstrations. Private locking and restoring continue through the protected Library store. The regression sources cover stage routing, event pin migration, and exam date projection. Compiled, physical-device, live speech, and provider evidence remain pending for this pass.
- **Built-in sources and knowledge.** Collector catalogue revision 2 adds opt-in feeds without resetting existing selections. Reads, Sources and the assistant use the same offline knowledge owner. Pack download uses the public collector transport and commits an indexed revision before reporting success. Search returns the pack revision, retrieval time and publisher URL. Kiwix, Wikimedia, Wikidata and GDELT archive entries open their publisher downloads outside the index.
- **Grounded web search.** The existing search tools and review dialog bind the query, language, credentials, provider and model to a one-use approval. Selected supported OpenAI API models use Responses web search. OpenRouter uses the selected OpenRouter model where available, with native grounding or Exa. The external request contains the query and response language. A grounded result requires structured citations. Charges are recorded as provider-reported or unconfirmed.
- **Evidence for sources and search.** Source changes and regression cases cover persisted pack readback, a revoked update, native citation parsing and approved route changes. Registry runtime review, typed and spoken acceptance in Local and Power, APK rendering and live provider results are pending.
