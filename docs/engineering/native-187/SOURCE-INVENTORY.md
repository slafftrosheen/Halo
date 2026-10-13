# HALO — Android native intent & framework capability inventory
Research date: 2026-10-13. Repository baseline: slafftrosheen/Halo main ad173cea7a32ed917cf8fdd9b3b7e06f9d7fc034 (observed at research time).

**Scope**: realistic, useful Android assistant features. This is a candidate inventory, not a claim that all entries are implemented or pass device tests. Android exposes thousands of intents; this covers major everyday assistant functions and related public framework APIs.
**Status**: `Declared` means present in Halo skill registry, **not** physical proof. `Partial` means matching implementation/design exists but coverage must be audited. `New` is an implementation candidate. `Restricted` requires elevated platform role/approval or is unavailable to ordinary third-party apps. `Conditional` is handler/OEM/app-specific.
**Priority**: P0 stabilize already declared; P1 high-value next; P2 optional/contract-dependent; P3 restricted or low ROI.
**Principle**: known deterministic speech intent -> native capability -> existing authorization/tool executor -> OS dispatch -> truthful receipt; never require an LLM. AppFunctions and app-specific contracts outrank generic UI automation. `DISPATCHED` does not imply `CONFIRMED`.

## Applications, navigation and UI (18 features)

| Feature / utterance target | Android mechanism | Halo status | Priority | Verification / caveat |
|---|---|---|---|---|
| Open installed app | PackageManager launcher intent / ACTION_MAIN | Declared: halo_open_app | P0 | Foreground package after dispatch; handle not-found/ambiguous/visibility |
| List/find installed apps | PackageManager queries scoped by manifest | Declared: halo_list_apps | P0 | Results filtered by package visibility; no broad inventory claim |
| Open exact app shortcut | LauncherApps / ShortcutManager or explicit exported activity | New | P1 | Shortcut identity and package validated; may require user selection |
| Open app feature | Verified App Link, app deep link or AppFunction | Partial: semantic app bridge | P1 | Feature reached, not merely app launched |
| Search inside a supporting app | ACTION_SEARCH or app-specific documented deep link | New/contract dependent | P2 | Only on a verified handler; no universal in-app search |
| Open recent apps | Accessibility GLOBAL_ACTION_RECENTS | Partial: screen global actions | P1 | Verify system state when observable |
| Go Home | Accessibility GLOBAL_ACTION_HOME | Partial: screen global actions | P0 | Verify foreground launcher |
| Go Back | Accessibility GLOBAL_ACTION_BACK | Partial: screen global actions | P0 | Verify screen transition; no blindly repeated back |
| Open notification shade | Accessibility GLOBAL_ACTION_NOTIFICATIONS | Partial: screen global actions | P1 | Verify shade if observable |
| Open Quick Settings shade | Accessibility GLOBAL_ACTION_QUICK_SETTINGS | Partial: screen global actions | P1 | Do not treat quick-settings tiles as public control APIs |
| Open app information page | Settings.ACTION_APPLICATION_DETAILS_SETTINGS + package URI | New/specific-settings | P1 | Settings screen launched; app not uninstalled/modified |
| Uninstall an app with user confirmation | ACTION_DELETE or ACTION_UNINSTALL_PACKAGE | New | P2 | System confirmation; never silent; protected-app exclusions |
| Open APK install flow | ACTION_VIEW content URI package archive / PackageInstaller | Restricted | P3 | User approves; unknown-sources and store policies apply |
| Select default app | Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS / RoleManager | New | P1 | Navigate or request role, never claim role held without check |
| Open launcher category (browser, music, gallery) | ACTION_MAIN with supported CATEGORY_APP_* | New/conditional | P2 | Use handler probe and fallback; categories vary by device |
| Lock screen | DevicePolicyManager (only with granted device-admin authority) or supported accessibility global action | Restricted | P3 | Role and Android API dependent; not an ordinary intent |
| Capture a system screenshot | Accessibility GLOBAL_ACTION_TAKE_SCREENSHOT / MediaProjection consent | Partial: accessibility | P2 | User-visible evidence; respect secure windows |
| Split-screen control | Accessibility GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN if available | Conditional | P3 | Only if getSystemActions advertises action |

## Clocks, timers, calendar and reminders (18 features)

| Feature / utterance target | Android mechanism | Halo status | Priority | Verification / caveat |
|---|---|---|---|---|
| Create one-time alarm | AlarmClock.ACTION_SET_ALARM | Declared: halo_set_alarm | P0 | Clock handoff; verify when possible; never infer saved from dispatch |
| Create repeating alarm | ACTION_SET_ALARM with EXTRA_DAYS | Extension | P1 | Day/time parsing and destination support |
| Label an alarm | ACTION_SET_ALARM with EXTRA_MESSAGE | Extension | P1 | Preserve user label |
| Show alarms | AlarmClock.ACTION_SHOW_ALARMS | New | P1 | Clock activity shown |
| Dismiss upcoming alarm | AlarmClock.ACTION_DISMISS_ALARM | New | P1 | Disambiguate if multiple matches |
| Snooze ringing alarm | AlarmClock.ACTION_SNOOZE_ALARM | New | P1 | Only applies when an alarm rings |
| Create timer | AlarmClock.ACTION_SET_TIMER | Declared: halo_timer | P0 | Timer accepted by clock app; no assumption that started |
| Show timers | AlarmClock.ACTION_SHOW_TIMERS | New | P1 | Clock timer page shown |
| Dismiss/stop timer | AlarmClock.ACTION_DISMISS_TIMER | New | P1 | Requires relevant supported clock handler |
| Internal Halo countdown | Halo runtime timer with notification | Product choice | P2 | Clear ownership, persistence and cancellation |
| Show upcoming calendar events | CalendarContract provider READ_CALENDAR | Declared: halo_calendar_upcoming | P0 | Read actual events with consent |
| Create calendar event via editor | ACTION_INSERT + CalendarContract.Events.CONTENT_URI | Declared/extend: halo_calendar_create | P0 | Editor opened; confirm event only if persisted/queried |
| Create event with calendar provider | CalendarContract ContentResolver WRITE_CALENDAR | Conditional | P2 | User permission, exact calendar identity, readback |
| Open particular event | ACTION_VIEW with event content URI | New | P1 | Requires permitted event URI |
| Edit a calendar event | ACTION_EDIT on permitted event URI | New | P2 | Destination user saves; don't claim mutation on launch |
| Show calendar date | ACTION_VIEW with time-oriented Calendar URI / supported app deep link | Conditional | P2 | No universal guarantee of requested date |
| Open reminder app | Launcher intent / known deep link | New | P2 | No universal cross-app reminder creation Intent |
| Create Halo-local reminder | Halo own reminder store + notification / AlarmManager | Existing product domain; audit | P1 | Only Halo-owned reminders are fully verifiable |

## Calls, contacts, messages and email (19 features)

| Feature / utterance target | Android mechanism | Halo status | Priority | Verification / caveat |
|---|---|---|---|---|
| Find contact by name | ContactsContract provider READ_CONTACTS | Declared: halo_find_contact | P0 | Ambiguity resolution; minimum exposed details |
| Pick contact interactively | ACTION_PICK with ContactsContract.Contacts.CONTENT_URI | New | P1 | User returns selected contact URI |
| Open contact card | ACTION_VIEW with permitted contact URI | New | P1 | Card visible |
| Create contact | ACTION_INSERT ContactsContract.RawContacts.CONTENT_TYPE | New | P1 | User review/save; no write permission needed for editor handoff |
| Edit existing contact | ACTION_EDIT with permitted contact URI | New | P1 | User edits; verified save if accessible |
| Find or create contact from number | ContactsContract.Intents.SHOW_OR_CREATE_CONTACT | New | P2 | Contact app decides matching/creation |
| Share contact card | ACTION_SEND text/vcard or granted vCard URI | New | P2 | Share sheet opened, delivery not proven |
| Dial a number/contact | ACTION_DIAL tel: URI | Declared: halo_dial_contact | P0 | Dialer open; not a placed call |
| Place call directly | ACTION_CALL with CALL_PHONE permission | Restricted optional | P2 | Explicit user confirmation; real call state as proof |
| Open call log | Dialer exported activity / documented Android call-log UI contract | Conditional | P2 | No universal third-party read without role/permission |
| Answer/reject call | Telecom roles / InCallService / restricted phone permissions | Restricted | P3 | Not generally available just from assistant role |
| Compose SMS | ACTION_SENDTO smsto: URI | Declared: halo_compose_sms | P0 | Composer open; user sends |
| Compose email | ACTION_SENDTO mailto: URI | Declared: halo_compose_email | P0 | Email composer open; not sent |
| Compose general chat message | Supported ACTION_SENDTO / provider-specific app function | Declared: halo_compose_message | P1 | Recipient and app capabilities checked |
| Share text to messenger | ACTION_SEND text/plain + chooser | Declared: halo_share_text | P0 | Handed to chooser; not delivered |
| Send attachment via email/chat | ACTION_SEND or ACTION_SEND_MULTIPLE with FileProvider grant | New | P1 | MIME and granted URI; no automatic send |
| Reply from notification | NotificationListenerService + RemoteInput PendingIntent | Declared: halo_reply_notification | P0 | DISPATCHED vs delivered distinction |
| Read SMS database | SMS/CallLog provider with role or scoped permission | Restricted | P3 | Avoid broad SMS permission unless approved core use case |
| Read email/inbox content | AppFunction, documented app API, or user-granted connector | App specific | P2 | Generic ACTION_VIEW cannot search mail content |

## Web, mapping and location (11 features)

| Feature / utterance target | Android mechanism | Halo status | Priority | Verification / caveat |
|---|---|---|---|---|
| Open web address | ACTION_VIEW https: URI | Declared: halo_browser | P0 | Browser launched; validate scheme and URI |
| Web-search phrase | ACTION_WEB_SEARCH if supported, else HTTPS search URL | Declared: halo_search | P0 | Search launched, not proof results found |
| Open exact link in installed app | Verified Android App Link / ACTION_VIEW | New | P1 | No forced arbitrary package/deep link |
| Open map at coordinates | ACTION_VIEW geo:lat,long URI | Extension: halo_navigation | P1 | Map handler available |
| Find place/address on map | ACTION_VIEW geo:0,0?q=encoded-query | Extension: halo_navigation | P1 | Result shown by map app |
| Start driving directions | ACTION_VIEW supported geo: directions or app-specific URI | Extension/conditional | P1 | App-specific navigation scheme must be validated |
| Start walking/transit/cycling directions | Supported map-app-specific URI/deep link | Conditional | P2 | No universal cross-app transport-mode standard |
| Share location pin | ACTION_SEND text/plain HTTPS/geo URI | New | P2 | Explicit destination/share chooser |
| Show device current location | LocationManager/FusedLocation with location consent | New/permission | P1 | Actual timestamp/accuracy; never inferred from IP |
| Open location settings | Settings.ACTION_LOCATION_SOURCE_SETTINGS | New | P1 | User controls location switch |
| Nearby places search | Map app query or web search | New | P2 | Do not claim verified business information solely from launch |

## Camera, recording, media and audio (19 features)

| Feature / utterance target | Android mechanism | Halo status | Priority | Verification / caveat |
|---|---|---|---|---|
| Open camera/photo mode | MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA | New | P1 | Camera foreground |
| Take photo via installed camera | MediaStore.ACTION_IMAGE_CAPTURE with result URI | New | P1 | Confirm returned URI or verified saved media |
| Open video-camera mode | MediaStore.INTENT_ACTION_VIDEO_CAMERA | New | P1 | Video camera foreground |
| Record video via installed camera | MediaStore.ACTION_VIDEO_CAPTURE with result URI | New | P1 | Capture complete, return URI |
| Launch sound recorder | MediaStore.Audio.Media.RECORD_SOUND_ACTION | New/conditional | P1 | Confirm returned recording URI; handler may be missing |
| Record Halo voice memo | Halo own RECORD_AUDIO foreground/session + notebook storage | Existing notebook concept; audit | P1 | Explicit user initiation, saved local file |
| Pick existing photo/video | Photo Picker / MediaStore.ACTION_PICK_IMAGES | New | P0 | Returned user-selected URI; no broad permission |
| Open gallery | ACTION_MAIN CATEGORY_APP_GALLERY / ACTION_VIEW media | New/conditional | P1 | Handler-dependent |
| View/play chosen media file | ACTION_VIEW content: with exact MIME | New | P1 | Uri permission grant and visible app |
| Share photo/video | ACTION_SEND / ACTION_SEND_MULTIPLE content URI | New | P1 | Chooser open, file accessible to target |
| Play/pause current media session | MediaSessionManager + active session transport controls | Declared: halo_media_control | P0 | Readback playback state where available |
| Next/previous/seek | MediaSession transport controls | Extend: halo_media_control | P1 | Unambiguous selected session |
| Select named media app | Media session package identity + app launch | Extension | P1 | No first-arbitrary-session control |
| Query track/player status | MediaSessionManager with notification-listener entitlement | Declared: halo_media_status | P0 | Source-backed state; not all apps expose metadata |
| Change media/call/alarm volume | AudioManager adjustStreamVolume / setStreamVolume | Declared: halo_volume | P0 | Readback level; avoid silent ringtone changes |
| Mute/unmute/ringer | AudioManager, DND policy where applicable | Declared: halo_ringer | P0 | State readback; respect DND restrictions |
| Play feedback on headset | AudioManager routing + Halo speech output | Existing audio owner | P0 | Actual selected device, audibility human checked |
| Screen recording | MediaProjection consent / screen-record app feature | Restricted/consent | P3 | Cannot silently start system-wide recording |
| Cast screen or media | Settings.ACTION_CAST_SETTINGS; app-specific Cast SDK | Conditional | P3 | Opening cast picker != starting cast |

## Files, documents and productivity (17 features)

| Feature / utterance target | Android mechanism | Halo status | Priority | Verification / caveat |
|---|---|---|---|---|
| Pick one document for Notebook | Intent.ACTION_OPEN_DOCUMENT | New/extend | P0 | Returned URI + persisted grant if needed |
| Import a one-off file | Intent.ACTION_GET_CONTENT | New/extend | P1 | Import copy, no long-term original ownership |
| Pick multiple documents | ACTION_OPEN_DOCUMENT with EXTRA_ALLOW_MULTIPLE | New | P1 | Returned ClipData URIs and grants |
| Pick accessible folder | ACTION_OPEN_DOCUMENT_TREE | New | P1 | User chooses permitted tree; Android 11+ restrictions |
| Create/export document | Intent.ACTION_CREATE_DOCUMENT with MIME and title | New | P0 | Write to returned URI, report write completion |
| Open PDF in viewer | ACTION_VIEW application/pdf content URI | New | P0 | Use FileProvider / read grant |
| Open DOCX/text/image/audio | ACTION_VIEW with supported MIME | New | P1 | Check compatible handler |
| Edit a document in another app | ACTION_EDIT with writable granted content URI | Conditional | P2 | Destination-specific; writeback not universally observable |
| Share a file | ACTION_SEND with MIME + content URI grant | New | P0 | Chooser received; sending not guaranteed |
| Share a bundle of files | ACTION_SEND_MULTIPLE + ClipData grants | New | P1 | All attachments accessible to target |
| Print Halo content | PrintManager.print with PrintDocumentAdapter | New | P2 | Print UI and job state; not an ACTION_PRINT catchall |
| Save as PDF using Android print UI | PrintManager.print / PDF export | New | P2 | User chooses Save as PDF; verify document URI if returned |
| Copy content to clipboard | ClipboardManager.setPrimaryClip | Declared: halo_clipboard_write | P0 | System reports new clip; privacy-safe |
| Read clipboard text | ClipboardManager.getPrimaryClip when permitted | Declared: halo_clipboard_read | P0 | Foreground/OS privacy and sensitive content limits |
| Share text selection into Halo | ACTION_PROCESS_TEXT intent-filter on Halo (receive) | New inbound | P2 | User explicitly chooses Halo; not system-wide text capture |
| Import shared file or URL into Halo | ACTION_SEND/ACTION_SEND_MULTIPLE intent-filter on Halo (receive) | New inbound | P1 | Review payload; untrusted input, persist grant where allowed |
| Scan paper document to PDF | Verified installed scanner app deep link or Halo own capture/OCR | Conditional | P2 | No universal scanner intent contract |

## System settings destinations (open, not toggle) (41 features)

| Feature / utterance target | Android mechanism | Halo status | Priority | Verification / caveat |
|---|---|---|---|---|
| Open main Settings | Settings.ACTION_SETTINGS | Declared: halo_open_settings | P0 | Settings UI opened |
| Wi-Fi settings | Settings.ACTION_WIFI_SETTINGS | Extend settings destinations | P0 | Settings shown, not Wi-Fi switched |
| Internet quick panel | Settings.Panel.ACTION_INTERNET_CONNECTIVITY | Declared/extend: system panels | P0 | Panel shown |
| Bluetooth settings | Settings.ACTION_BLUETOOTH_SETTINGS | Extend settings destinations | P0 | Settings shown, not radio switched |
| Airplane mode settings | Settings.ACTION_AIRPLANE_MODE_SETTINGS | New | P1 | No silent toggle |
| Mobile data/network settings | Settings.ACTION_NETWORK_OPERATOR_SETTINGS / ACTION_DATA_ROAMING_SETTINGS | New/conditional | P1 | OEM may omit handler |
| SIM and cellular preferences | Settings.ACTION_WIRELESS_SETTINGS or eSIM-specific supported settings | New/conditional | P2 | Check handler/API |
| Hotspot/tethering settings | Settings.ACTION_WIRELESS_SETTINGS or OEM-supported tethering screen | Conditional | P2 | No guaranteed public deep link to exact tethering page |
| Location settings | Settings.ACTION_LOCATION_SOURCE_SETTINGS | New | P1 | Explicit user action |
| NFC settings panel | Settings.Panel.ACTION_NFC / Settings.ACTION_NFC_SETTINGS | New | P1 | Open panel, do not silently toggle NFC |
| Volume system panel | Settings.Panel.ACTION_VOLUME | Declared/extend: system panels | P1 | Panel opened |
| Sound settings | Settings.ACTION_SOUND_SETTINGS | New | P1 | Ringtone/audio UI |
| Display settings | Settings.ACTION_DISPLAY_SETTINGS | New | P1 | Display UI |
| Brightness settings | Settings.ACTION_DISPLAY_SETTINGS | New | P1 | Open only; direct brightness separate |
| Auto rotate settings | Settings.ACTION_AUTO_ROTATE_SETTINGS if supported | New/conditional | P2 | Handler and API check |
| Accessibility settings | Settings.ACTION_ACCESSIBILITY_SETTINGS | New | P0 | Manual access grant, never automated |
| Keyboard/input settings | Settings.ACTION_INPUT_METHOD_SETTINGS | New | P1 | Input preferences screen |
| Language/locale settings | Settings.ACTION_LOCALE_SETTINGS | New | P1 | Device language screen |
| App-specific language | Settings.ACTION_APP_LOCALE_SETTINGS | New | P2 | API/OS support |
| Date/time settings | Settings.ACTION_DATE_SETTINGS | New | P1 | User edits clock settings |
| Battery saver | Settings.ACTION_BATTERY_SAVER_SETTINGS | New | P1 | No silent global toggle |
| Battery optimization list | Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS | New | P2 | Special access depends on Play policy |
| Storage management | Settings.ACTION_INTERNAL_STORAGE_SETTINGS | New | P1 | Storage screen |
| Data usage | Settings.ACTION_DATA_USAGE_SETTINGS | New | P1 | Usage details |
| VPN settings | Settings.ACTION_VPN_SETTINGS | New | P2 | User-managed connections |
| Privacy settings | Settings.ACTION_PRIVACY_SETTINGS when available | New/conditional | P2 | OS version dependent |
| Security settings | Settings.ACTION_SECURITY_SETTINGS | New | P2 | No bypass of device unlock |
| Biometric enrollment | Settings.ACTION_BIOMETRIC_ENROLL | New/conditional | P2 | User follows system UI |
| Accounts | Settings.ACTION_SYNC_SETTINGS / ACTION_ADD_ACCOUNT | New | P2 | User must authenticate |
| Default applications | Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS | New | P1 | User chooses default |
| App detail/permission page | Settings.ACTION_APPLICATION_DETAILS_SETTINGS for package | New | P0 | Verified package identity |
| Per-app notifications | Settings.ACTION_APP_NOTIFICATION_SETTINGS with package extra | New | P1 | Check OEM support |
| Notification listener access | Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS | New | P0 | Explicit manual grant |
| Special write-settings access | Settings.ACTION_MANAGE_WRITE_SETTINGS | New | P2 | Explicit manual grant |
| Overlay permission screen | Settings.ACTION_MANAGE_OVERLAY_PERMISSION | New | P2 | Explicit manual grant |
| Usage-access screen | Settings.ACTION_USAGE_ACCESS_SETTINGS | New | P2 | Explicit manual grant |
| Unknown-source install access | Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES | Restricted | P3 | No silent sideload, policy constraints |
| Notification policy/DND access | Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS | New | P2 | User-managed access |
| Cast settings | Settings.ACTION_CAST_SETTINGS | New | P2 | Device support varies |
| Developer options | Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS | Restricted | P3 | Never auto-enable developer mode |
| App settings internal to Halo | Explicit Halo navigation | Existing Halo UI | P0 | In-app page, not external Settings |

## Device state, quick controls and connectivity (23 features)

| Feature / utterance target | Android mechanism | Halo status | Priority | Verification / caveat |
|---|---|---|---|---|
| Device make/model/OS/screen | Build/DisplayManager/Configuration | Declared: halo_device_info | P0 | Local read-only |
| Battery level/charging | BatteryManager and battery state broadcasts | Declared: halo_battery_status | P0 | Local read-only |
| Connection type and state | ConnectivityManager | Declared: halo_network_status | P0 | Privacy-safe network summary |
| Wi-Fi enable/disable | WifiManager.setWifiEnabled | Restricted API29+ | P3 | Ordinary target apps cannot toggle; open system panel |
| Wi-Fi network selection | Settings.Panel internet / WifiNetworkSpecifier | New/user-mediated | P1 | Network connect outcome callback; do not claim until connected |
| Suggest Wi-Fi network | Settings.ACTION_WIFI_ADD_NETWORKS / WifiNetworkSuggestion | New/user-mediated | P2 | User approves network save; API30+ |
| Bluetooth enable request | BluetoothAdapter.ACTION_REQUEST_ENABLE | New/user-mediated | P1 | System prompt and adapter state |
| Bluetooth disable/enable silently | BluetoothAdapter.disable/enable | Restricted API33+ | P3 | Not available to regular modern target apps |
| Pair Bluetooth accessory | CompanionDeviceManager association UI / Bluetooth pairing | New/user-mediated | P2 | Explicit association, BLUETOOTH_CONNECT/SCAN rules |
| Show connected audio endpoint | AudioManager/BluetoothProfile | Existing device/audio ownership | P0 | Actual endpoint evidence |
| Switch Halo voice output | Halo AudioManager route selection | Existing audio ownership | P0 | Respect input origin and no-phone fallback |
| Switch system-wide audio device | Platform audio/output selector, app/media route-specific APIs | Conditional | P3 | Not a general universal intent |
| Flashlight on/off | CameraManager.setTorchMode | Declared: halo_flashlight | P0 | Torch callback/state |
| Brightness change | Settings.System with special WRITE_SETTINGS grant | Declared: halo_brightness | P0 | Readback with OS range and ownership |
| Ringer silent/vibrate/normal | AudioManager.setRingerMode with policies | Declared: halo_ringer | P0 | Readback; DND access may apply |
| Do Not Disturb changes | NotificationManager with granted policy access, Android 15+ Zen rules | Conditional | P2 | Rules/permission; cannot assume global direct toggle |
| Vibrate/haptic ping | VibratorManager / Vibrator with permission | New | P2 | Local confirmation limited; explicit consent in quiet hours |
| Screen wake within Halo session | Window flags / wake lock scoped to Halo | Conditional | P2 | Not force power button or bypass lockscreen |
| Device lock (admin-authorized) | DevicePolicyManager.lockNow where valid | Restricted | P3 | Only granted administrative role or legal global action |
| Current location | LocationManager with location permission | New | P1 | Timestamp/accuracy; no unauthorized background tracking |
| Nearby BLE device discovery | BluetoothLeScanner with proper permissions | Existing HeyCyan bridge | P1 | Only user-authorized scan and OS restrictions |
| Sensor readings | SensorManager (orientation/proximity/ambient light/etc.) | New read-only | P2 | Hardware availability, sampling and privacy policy |
| Show internet speed/connection quality | ConnectivityManager and app-owned measurements | New/conditional | P2 | No inferred carrier quality; metered and network costs |

## Notifications, assistant and interoperable routines (21 features)

| Feature / utterance target | Android mechanism | Halo status | Priority | Verification / caveat |
|---|---|---|---|---|
| List allowed notifications | NotificationListenerService | Declared: halo_list_notifications | P0 | Policy/privacy-filtered current entries |
| Summarize notification counts | Halo local deterministic digest | Declared: halo_notification_digest | P0 | No cloud/inference unless chosen |
| Open notification origin | Notification.contentIntent PendingIntent | New/conditional | P1 | System may remove/stale target; send dispatch only |
| Reply to notification | Notification Action + RemoteInput | Declared: halo_reply_notification | P0 | Dispatched != delivered |
| Dismiss notification | NotificationListenerService.cancelNotification | Declared: halo_dismiss_notification | P0 | Wait for removal callback |
| List reply-capable notifications | Notification actions metadata | Declared: halo_replyable_notifications | P0 | Only live enabled actions |
| Create Halo notification | NotificationManager / channels | Existing infrastructure | P1 | App-owned notification only |
| Open notification settings | Settings notification actions | New | P1 | User controls permissions |
| Set Halo as default assistant | RoleManager.ROLE_ASSISTANT / VoiceInteractionService | Existing assistant implementation | P0 | Verify role held and active service |
| Receive system assistant invocation | VoiceInteractionService + session | Existing | P0 | Physical gesture/assistant session evidence |
| Voice continuation/confirmation | VoiceInteractor requests | Existing | P1 | Explicit user decision and cancellation |
| Expose Halo tools to other assistants | Jetpack AppFunctions (device support, SDK36+) | Existing limited functions | P2 | Verified supported platform and permissions |
| Invoke other app functions | AppFunctionManager and app-provided schema | Partial semantic app bridge | P2 | Only exposed schemas, role/consent checks |
| Respond to direct actions | VoiceInteractionSession app DirectActions API29+ | Existing semantic bridge | P2 | Active app handles expire; no arbitrary extras |
| Trigger app-provided shortcut | ShortcutManager/LauncherApps and verified package | New/conditional | P2 | Shortcut ID and permission checks |
| Perform explicit user-defined multi-step routine | Halo deterministic workflow owner | Existing native sequenced operations | P1 | Stop on unconfirmed effect; no stale replay |
| Receive intent-driven Halo task from other apps | Explicit exported activity/action with permissions | New inbound | P2 | Avoid broad exported executors; security validation |
| Home-screen shortcut to Halo workflow | ShortcutManager requestPinShortcut / dynamic shortcuts | New | P2 | User approval and revocable routine target |
| Quick Settings tile for Halo | TileService | New inbound | P2 | User adds tile; it doesn't confer privileged settings control |
| Android widget for Halo status/actions | AppWidget/Glance | New product integration | P2 | User-initiated actions and fresh permissions |
| Wearable button mapped to safe native command | HeyCyan host event -> deterministic dispatch | Existing device ecosystem | P1 | Input origin, user mapping, route-bound feedback |

**Total cataloged: 187 user-facing capability targets** across 9 categories. The counts include alternate entry points and permission-gated actions; not all should become distinct tool IDs.

## Architecture and safety requirements

1. One canonical typed registry; translate English/Latvian/Russian utterances to the same semantic command ID. Do not register one separate tool for every settings page or every alarm variant. Use validated enum parameters.
2. Three truth levels: system intent dispatched; OS handler returned/acknowledged; real externally observed user effect. Honest UX must distinguish them.
3. Resolve app visibility with bounded `<queries>` declarations and launch with try/catch ActivityNotFoundException; package query failure is not proof no handler exists.
4. Use modern Activity Result API, explicit MIME type, `content://` and URI permissions for files, camera, documents and attachments; never arbitrary `file://`.
5. Gate by API level, active role, granted Android permissions, foreground state, lock state, OEM handler resolution, destination policy and Play policy.
6. Cross-app messages, edits, calls, installs, notifications and purchases require review and result-aware UX. Auth, CAPTCHA, banking and system permission dialogs remain user-controlled.
7. No arbitrary generic Intent URI executor, arbitrary exported broadcasts/services, unvalidated component names or shell/root/Shizuku bypass for speech-derived requests.
8. Never assume Wi-Fi or Bluetooth can be silently toggled: Wi-Fi APIs restricted for target API29+ and Bluetooth for target API33+.
9. Android 10+ limits background activity launch. A visible assistant session and proper user initiation do not automatically exempt all background starts.
10. Google Play accessibility policy limits autonomous AI-driven accessibility execution; deterministic user-defined routines are explicitly distinguished. Do an app-distribution policy review before expanding screen automation.
11. Keep cloud screen context behind consent; no model selection should influence deterministic native actions.
12. Acceptance: execute physical device command, verify effect, deny permission, missing handler, stale target, locked screen, process death, Stop, API/OEM variants, no-model mode.

## Suggested implementation batches

**Batch NATIVE-INTENTS-01** — Audit existing skills and map all tool IDs to Android mechanisms. Fix resolution and truth receipts. Add negative cases. Device tests for P0.

**Batch NATIVE-INTENTS-02** — Clock lifecycle + system settings destination catalog with capability probes, route validation and EN/LV/RU phrases.

**Batch NATIVE-INTENTS-03** — Contact view/pick/create/edit, calendar editor/view and communication attachment composers, all explicitly user-mediated.

**Batch NATIVE-INTENTS-04** — Camera/recording + Photo Picker + Storage Access Framework + FileProvider, expose entry/exit flows in Halo Notebook/Canvas/Study.

**Batch NATIVE-INTENTS-05** — App shortcuts, App Links, AppFunctions, media/notification deep actions and cross-app deterministic follow-up integration; specific-handler adapters only.

**Batch NATIVE-INTENTS-06** — Physical matrix: no LLM, LiteRT selected/unavailable, OpenRouter Free; Xiaomi + stock Android where possible; permission/role and wearable route matrix.

## Source references (checked 2026-10-13)

- Halo repo: https://github.com/slafftrosheen/Halo
- Halo installed skills: https://github.com/slafftrosheen/Halo/tree/main/app/src/main/assets/skills
- Halo host-first: https://github.com/slafftrosheen/Halo/blob/main/docs/engineering/PHONE-ASSISTANT-002-HOST-FIRST-RUNTIME.md
- Halo semantic app bridge: https://github.com/slafftrosheen/Halo/blob/main/docs/engineering/PHONE-ASSISTANT-003-SEMANTIC-APP-BRIDGE.md
- Android common intents: https://developer.android.com/guide/components/intents-common
- Android settings: https://developer.android.com/reference/android/provider/Settings
- Android settings panels: https://developer.android.com/reference/android/provider/Settings.Panel
- Android alarm intents: https://developer.android.com/reference/android/provider/AlarmClock
- Android media intents: https://developer.android.com/reference/android/provider/MediaStore
- Android camera intents: https://developer.android.com/media/camera/camera-intents
- Android contacts provider: https://developer.android.com/identity/providers/contacts-provider
- Android SAF documents: https://developer.android.com/training/data-storage/shared/documents-files
- Android package visibility: https://developer.android.com/training/package-visibility/use-cases
- Android accessibility global actions: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Android app functions: https://developer.android.com/ai/appfunctions/add-appfunctions
- Android app roles: https://developer.android.com/reference/android/app/role/RoleManager
- Android activity/background restrictions: https://developer.android.com/guide/components/activities/secure-bal
- Google Play Accessibility Policy: https://support.google.com/googleplay/android-developer/answer/10964491
- Dicio offline assistant (skill and confirmation examples): https://github.com/DicioTeam/dicio-android
- Key Mapper (typed intent target/extras execution and user-defined macros): https://github.com/keymapperorg/KeyMapper
- Home Assistant Android companion (shortcuts, deep links, device state): https://github.com/home-assistant/android
- OpenTasker (capability taxonomy; do not treat privileged actions as normal-app APIs): https://github.com/SysAdminDoc/OpenTasker
