# Native Android 187-target implementation ledger

Audit base: 45c056b0, with the 2026-10-14 continuation. This is a tracked implementation audit, not a device acceptance claim.
Every source row has a stable ID; repeated features remain separate acceptance targets.
Restricted features need a lawful role or user-mediated fallback. Pending means unfinished.
There are 187 targets and 10 explicit Pending rows after this continuation. Source-path rows may still be partial, permission/role dependent, or require external setup; they are not 177 fully accepted features.

| ID | Category | Target | Source status | Implementation audit |
|---|---|---|---|---|
| NATIVE-001 | Applications, navigation and UI | Open installed app | Declared: halo_open_app | Source path: ApplicationTools / AndroidApplicationResolver; device acceptance pending |
| NATIVE-002 | Applications, navigation and UI | List/find installed apps | Declared: halo_list_apps | Source path: ApplicationTools scoped launcher queries; device acceptance pending |
| NATIVE-003 | Applications, navigation and UI | Open exact app shortcut | New | Source path: NativeShortcutTools run; launcher shortcut host role required; device acceptance pending |
| NATIVE-004 | Applications, navigation and UI | Open app feature | Partial: semantic app bridge | Source path: SemanticAppActionBroker: exposed AppFunctions/DirectActions only; device acceptance pending |
| NATIVE-005 | Applications, navigation and UI | Search inside a supporting app | New/contract dependent | Source path: SemanticAppActionBroker: requires app-provided schema; device acceptance pending |
| NATIVE-006 | Applications, navigation and UI | Open recent apps | Partial: screen global actions | Source path: AccessibilityTools user-reviewed global action; device acceptance pending |
| NATIVE-007 | Applications, navigation and UI | Go Home | Partial: screen global actions | Source path: NativeScreenAction HOME; device acceptance pending |
| NATIVE-008 | Applications, navigation and UI | Go Back | Partial: screen global actions | Source path: NativeScreenAction BACK; device acceptance pending |
| NATIVE-009 | Applications, navigation and UI | Open notification shade | Partial: screen global actions | Source path: AccessibilityTools notification global action; device acceptance pending |
| NATIVE-010 | Applications, navigation and UI | Open Quick Settings shade | Partial: screen global actions | Source path: AccessibilityTools quick settings global action; device acceptance pending |
| NATIVE-011 | Applications, navigation and UI | Open app information page | New/specific-settings | Source path: NativeTargetTools app_info; device acceptance pending |
| NATIVE-012 | Applications, navigation and UI | Uninstall an app with user confirmation | New | Source path: NativeTargetTools uninstall; protected exclusions; system confirmation; device acceptance pending |
| NATIVE-013 | Applications, navigation and UI | Open APK install flow | Restricted | Source path: FileToolExecutor install_apk; owned imported APK, special access and system review; device acceptance pending |
| NATIVE-014 | Applications, navigation and UI | Select default app | New | Source path: SystemTools default_apps; device acceptance pending |
| NATIVE-015 | Applications, navigation and UI | Open launcher category (browser, music, gallery) | New/conditional | Source path: NativeDestination launcher categories; handler-dependent; device acceptance pending |
| NATIVE-016 | Applications, navigation and UI | Lock screen | Restricted | Source path: NativeGlobalTools lock_screen; manually granted accessibility; device acceptance pending |
| NATIVE-017 | Applications, navigation and UI | Capture a system screenshot | Partial: accessibility | Source path: NativeGlobalTools screenshot; dispatch only, saved image unconfirmed; device acceptance pending |
| NATIVE-018 | Applications, navigation and UI | Split-screen control | Conditional | Source path: NativeGlobalTools split_screen; advertised Android global action only; device acceptance pending |
| NATIVE-019 | Clocks, timers, calendar and reminders | Create one-time alarm | Declared: halo_set_alarm | Source path: AndroidAlarmAction; device acceptance pending |
| NATIVE-020 | Clocks, timers, calendar and reminders | Create repeating alarm | Extension | Source path: AndroidAlarmAction repeatDays; device acceptance pending |
| NATIVE-021 | Clocks, timers, calendar and reminders | Label an alarm | Extension | Source path: AndroidAlarmAction label; device acceptance pending |
| NATIVE-022 | Clocks, timers, calendar and reminders | Show alarms | New | Source path: AlarmTools show_alarms; device acceptance pending |
| NATIVE-023 | Clocks, timers, calendar and reminders | Dismiss upcoming alarm | New | Source path: AlarmTools dismiss_next_alarm; device acceptance pending |
| NATIVE-024 | Clocks, timers, calendar and reminders | Snooze ringing alarm | New | Source path: AlarmTools snooze_alarm; device acceptance pending |
| NATIVE-025 | Clocks, timers, calendar and reminders | Create timer | Declared: halo_timer | Source path: AlarmTools timer; device acceptance pending |
| NATIVE-026 | Clocks, timers, calendar and reminders | Show timers | New | Source path: AlarmTools show_timers; device acceptance pending |
| NATIVE-027 | Clocks, timers, calendar and reminders | Dismiss/stop timer | New | Pending |
| NATIVE-028 | Clocks, timers, calendar and reminders | Internal Halo countdown | Product choice | Pending |
| NATIVE-029 | Clocks, timers, calendar and reminders | Show upcoming calendar events | Declared: halo_calendar_upcoming | Source path: CalendarTools upcoming; READ_CALENDAR; device acceptance pending |
| NATIVE-030 | Clocks, timers, calendar and reminders | Create calendar event via editor | Declared/extend: halo_calendar_create | Source path: CalendarTools editor; saved state unconfirmed; device acceptance pending |
| NATIVE-031 | Clocks, timers, calendar and reminders | Create event with calendar provider | Conditional | Source path: CalendarTools create + readback; exact calendar; device acceptance pending |
| NATIVE-032 | Clocks, timers, calendar and reminders | Open particular event | New | Source path: NativeTargetTools calendar_view; accessible event required; device acceptance pending |
| NATIVE-033 | Clocks, timers, calendar and reminders | Edit a calendar event | New | Source path: NativeTargetTools calendar_edit; save unconfirmed; device acceptance pending |
| NATIVE-034 | Clocks, timers, calendar and reminders | Show calendar date | Conditional | Source path: NativeTargetTools calendar_date; handler-dependent; device acceptance pending |
| NATIVE-035 | Clocks, timers, calendar and reminders | Open reminder app | New | Source path: NativeDestination REMINDERS; Google Tasks launcher, installed handler required, no reminder creation claimed; device acceptance pending |
| NATIVE-036 | Clocks, timers, calendar and reminders | Create Halo-local reminder | Existing product domain; audit | Source path: HaloReminder runtime owner; device acceptance pending |
| NATIVE-037 | Calls, contacts, messages and email | Find contact by name | Declared: halo_find_contact | Source path: CommunicationTools contact resolution; device acceptance pending |
| NATIVE-038 | Calls, contacts, messages and email | Pick contact interactively | New | Source path: NativeResultActivity contact picker; device acceptance pending |
| NATIVE-039 | Calls, contacts, messages and email | Open contact card | New | Source path: NativeTargetTools contact_view; selected handle; device acceptance pending |
| NATIVE-040 | Calls, contacts, messages and email | Create contact | New | Source path: CommunicationTools contact editor; device acceptance pending |
| NATIVE-041 | Calls, contacts, messages and email | Edit existing contact | New | Source path: NativeTargetTools contact_edit; selected handle; device acceptance pending |
| NATIVE-042 | Calls, contacts, messages and email | Find or create contact from number | New | Source path: NativeTargetTools contact_from_number; device acceptance pending |
| NATIVE-043 | Calls, contacts, messages and email | Share contact card | New | Source path: NativeTargetTools contact_share; selected provider handle and vCard content grant; device acceptance pending |
| NATIVE-044 | Calls, contacts, messages and email | Dial a number/contact | Declared: halo_dial_contact | Source path: CommunicationTools dialer; user places call; device acceptance pending |
| NATIVE-045 | Calls, contacts, messages and email | Place call directly | Restricted optional | Source path: NativeDirectCallActivity; fresh local review and CALL_PHONE permission; device acceptance pending |
| NATIVE-046 | Calls, contacts, messages and email | Open call log | Conditional | Source path: NativeDestination CALL_LOG; fixed provider UI handoff, no call-log database read, handler/role dependent; device acceptance pending |
| NATIVE-047 | Calls, contacts, messages and email | Answer/reject call | Restricted | Pending |
| NATIVE-048 | Calls, contacts, messages and email | Compose SMS | Declared: halo_compose_sms | Source path: MessagingTools SMS composer; device acceptance pending |
| NATIVE-049 | Calls, contacts, messages and email | Compose email | Declared: halo_compose_email | Source path: MessagingTools email composer; device acceptance pending |
| NATIVE-050 | Calls, contacts, messages and email | Compose general chat message | Declared: halo_compose_message | Source path: MessagingTools supported communication contract; device acceptance pending |
| NATIVE-051 | Calls, contacts, messages and email | Share text to messenger | Declared: halo_share_text | Source path: SystemTools share chooser; device acceptance pending |
| NATIVE-052 | Calls, contacts, messages and email | Send attachment via email/chat | New | Source path: FileToolExecutor file_handoff share; imported content only; device acceptance pending |
| NATIVE-053 | Calls, contacts, messages and email | Reply from notification | Declared: halo_reply_notification | Source path: NotificationReplyToolExecutor RemoteInput; device acceptance pending |
| NATIVE-054 | Calls, contacts, messages and email | Read SMS database | Restricted | Pending |
| NATIVE-055 | Calls, contacts, messages and email | Read email/inbox content | App specific | Source path: NativeGoogleMailActivity / GoogleMailReader; Gmail OAuth setup required; private local preview, no content in tool results; device acceptance pending |
| NATIVE-056 | Web, mapping and location | Open web address | Declared: halo_browser | Source path: NavigationTools browser; device acceptance pending |
| NATIVE-057 | Web, mapping and location | Web-search phrase | Declared: halo_search | Source path: NavigationTools search and HTTPS fallback; device acceptance pending |
| NATIVE-058 | Web, mapping and location | Open exact link in installed app | New | Source path: NavigationTools browser HTTPS OS link resolution; no forced package; device acceptance pending |
| NATIVE-059 | Web, mapping and location | Open map at coordinates | Extension: halo_navigation | Source path: NativeMapTools coordinates; fixed geo URI; device acceptance pending |
| NATIVE-060 | Web, mapping and location | Find place/address on map | Extension: halo_navigation | Source path: AndroidNavigationCapability geo query; device acceptance pending |
| NATIVE-061 | Web, mapping and location | Start driving directions | Extension/conditional | Source path: NativeMapTools directions driving; Maps review, navigation start unconfirmed; device acceptance pending |
| NATIVE-062 | Web, mapping and location | Start walking/transit/cycling directions | Conditional | Source path: NativeMapTools walking/transit/bicycling; Maps review; device acceptance pending |
| NATIVE-063 | Web, mapping and location | Share location pin | New | Source path: SystemTools share_text; user-supplied location pin; device acceptance pending |
| NATIVE-064 | Web, mapping and location | Show device current location | New/permission | Source path: NativeObservationTools location; permission and bounded fresh fix; device acceptance pending |
| NATIVE-065 | Web, mapping and location | Open location settings | New | Source path: SystemTools location; device acceptance pending |
| NATIVE-066 | Web, mapping and location | Nearby places search | New | Source path: AndroidNavigationCapability place query; results unverified; device acceptance pending |
| NATIVE-067 | Camera, recording, media and audio | Open camera/photo mode | New | Source path: NativeDestination CAMERA; device acceptance pending |
| NATIVE-068 | Camera, recording, media and audio | Take photo via installed camera | New | Source path: NativeResultActivity owned full-size photo + capture validation; device acceptance pending |
| NATIVE-069 | Camera, recording, media and audio | Open video-camera mode | New | Source path: NativeDestination VIDEO_CAMERA; device acceptance pending |
| NATIVE-070 | Camera, recording, media and audio | Record video via installed camera | New | Source path: NativeResultActivity video URI import; device acceptance pending |
| NATIVE-071 | Camera, recording, media and audio | Launch sound recorder | New/conditional | Source path: NativeResultActivity sound recorder URI import; device acceptance pending |
| NATIVE-072 | Camera, recording, media and audio | Record Halo voice memo | Existing notebook concept; audit | Source path: InboxCaptureDialog / CanvasVoiceMemoRecorder; device acceptance pending |
| NATIVE-073 | Camera, recording, media and audio | Pick existing photo/video | New | Source path: NativeResultActivity photo picker; photo only currently; device acceptance pending |
| NATIVE-074 | Camera, recording, media and audio | Open gallery | New/conditional | Source path: NativeDestination GALLERY; device acceptance pending |
| NATIVE-075 | Camera, recording, media and audio | View/play chosen media file | New | Source path: FileToolExecutor file_handoff view; device acceptance pending |
| NATIVE-076 | Camera, recording, media and audio | Share photo/video | New | Source path: FileToolExecutor file_handoff share; device acceptance pending |
| NATIVE-077 | Camera, recording, media and audio | Play/pause current media session | Declared: halo_media_control | Source path: MediaTools selected live session; device acceptance pending |
| NATIVE-078 | Camera, recording, media and audio | Next/previous/seek | Extend: halo_media_control | Source path: MediaTools seek / next / previous; live session action support required; device acceptance pending |
| NATIVE-079 | Camera, recording, media and audio | Select named media app | Extension | Source path: MediaTools targetPackage/targetApp disambiguation; device acceptance pending |
| NATIVE-080 | Camera, recording, media and audio | Query track/player status | Declared: halo_media_status | Source path: MediaTools metadata/status; device acceptance pending |
| NATIVE-081 | Camera, recording, media and audio | Change media/call/alarm volume | Declared: halo_volume | Source path: DeviceTools media/call/alarm volume; system stream readback; device acceptance pending |
| NATIVE-082 | Camera, recording, media and audio | Mute/unmute/ringer | Declared: halo_ringer | Source path: DeviceTools ringer; device acceptance pending |
| NATIVE-083 | Camera, recording, media and audio | Play feedback on headset | Existing audio owner | Source path: HaloSpeechOutputCoordinator route owner; audible test pending; device acceptance pending |
| NATIVE-084 | Camera, recording, media and audio | Screen recording | Restricted/consent | Pending |
| NATIVE-085 | Camera, recording, media and audio | Cast screen or media | Conditional | Source path: SystemTools cast; picker only; device acceptance pending |
| NATIVE-086 | Files, documents and productivity | Pick one document for Notebook | New/extend | Source path: NativeResultActivity document picker/import; device acceptance pending |
| NATIVE-087 | Files, documents and productivity | Import a one-off file | New/extend | Source path: NativeResultActivity GET_CONTENT copy; device acceptance pending |
| NATIVE-088 | Files, documents and productivity | Pick multiple documents | New | Source path: NativeResultActivity multiple document copy; device acceptance pending |
| NATIVE-089 | Files, documents and productivity | Pick accessible folder | New | Source path: NativeResultActivity tree grant; no file mutation; device acceptance pending |
| NATIVE-090 | Files, documents and productivity | Create/export document | New | Source path: NativeFileActionActivity export; existing owned workspace object, user-selected destination, no new document authoring; device acceptance pending |
| NATIVE-091 | Files, documents and productivity | Open PDF in viewer | New | Source path: FileToolExecutor file_handoff view PDF; device acceptance pending |
| NATIVE-092 | Files, documents and productivity | Open DOCX/text/image/audio | New | Source path: FileToolExecutor file_handoff view content MIME; device acceptance pending |
| NATIVE-093 | Files, documents and productivity | Edit a document in another app | Conditional | Source path: FileToolExecutor edit; owned copy, write grant, external save unconfirmed; device acceptance pending |
| NATIVE-094 | Files, documents and productivity | Share a file | New | Source path: FileToolExecutor file_handoff share; device acceptance pending |
| NATIVE-095 | Files, documents and productivity | Share a bundle of files | New | Source path: FileToolExecutor bundle + ClipData grants; device acceptance pending |
| NATIVE-096 | Files, documents and productivity | Print Halo content | New | Source path: NativeFileActionActivity print; imported PDF, print UI dispatch, physical printing unconfirmed; device acceptance pending |
| NATIVE-097 | Files, documents and productivity | Save as PDF using Android print UI | New | Source path: NativeFileActionActivity print; Android PDF destination user choice, saved PDF unconfirmed; device acceptance pending |
| NATIVE-098 | Files, documents and productivity | Copy content to clipboard | Declared: halo_clipboard_write | Source path: ClipboardTools write; device acceptance pending |
| NATIVE-099 | Files, documents and productivity | Read clipboard text | Declared: halo_clipboard_read | Source path: ClipboardTools foreground read; device acceptance pending |
| NATIVE-100 | Files, documents and productivity | Share text selection into Halo | New inbound | Source path: MainActivity ACTION_PROCESS_TEXT; bounded selected text imported through Inbox; device acceptance pending |
| NATIVE-101 | Files, documents and productivity | Import shared file or URL into Halo | New inbound | Source path: MainActivity / SendIntentHandler content-only bounded import; device acceptance pending |
| NATIVE-102 | Files, documents and productivity | Scan paper document to PDF | Conditional | Source path: NativeDocumentScanActivity; Google ML Kit module, user scan, PDF import; device acceptance pending |
| NATIVE-103 | System settings destinations (open, not toggle) | Open main Settings | Declared: halo_open_settings | Source path: SystemTools general; handler/API gated; opens only; device acceptance pending |
| NATIVE-104 | System settings destinations (open, not toggle) | Wi-Fi settings | Extend settings destinations | Source path: SystemTools wifi; handler/API gated; opens only; device acceptance pending |
| NATIVE-105 | System settings destinations (open, not toggle) | Internet quick panel | Declared/extend: system panels | Source path: SystemTools internet; handler/API gated; opens only; device acceptance pending |
| NATIVE-106 | System settings destinations (open, not toggle) | Bluetooth settings | Extend settings destinations | Source path: SystemTools bluetooth; handler/API gated; opens only; device acceptance pending |
| NATIVE-107 | System settings destinations (open, not toggle) | Airplane mode settings | New | Source path: SystemTools airplane; handler/API gated; opens only; device acceptance pending |
| NATIVE-108 | System settings destinations (open, not toggle) | Mobile data/network settings | New/conditional | Source path: SystemTools mobile_network; handler/API gated; opens only; device acceptance pending |
| NATIVE-109 | System settings destinations (open, not toggle) | SIM and cellular preferences | New/conditional | Source path: SystemTools sim; handler/API gated; opens only; device acceptance pending |
| NATIVE-110 | System settings destinations (open, not toggle) | Hotspot/tethering settings | Conditional | Source path: SystemTools hotspot; handler/API gated; opens only; device acceptance pending |
| NATIVE-111 | System settings destinations (open, not toggle) | Location settings | New | Source path: SystemTools location; handler/API gated; opens only; device acceptance pending |
| NATIVE-112 | System settings destinations (open, not toggle) | NFC settings panel | New | Source path: SystemTools nfc_panel; handler/API gated; opens only; device acceptance pending |
| NATIVE-113 | System settings destinations (open, not toggle) | Volume system panel | Declared/extend: system panels | Source path: SystemTools volume; handler/API gated; opens only; device acceptance pending |
| NATIVE-114 | System settings destinations (open, not toggle) | Sound settings | New | Source path: SystemTools sound; handler/API gated; opens only; device acceptance pending |
| NATIVE-115 | System settings destinations (open, not toggle) | Display settings | New | Source path: SystemTools display; handler/API gated; opens only; device acceptance pending |
| NATIVE-116 | System settings destinations (open, not toggle) | Brightness settings | New | Source path: SystemTools brightness; handler/API gated; opens only; device acceptance pending |
| NATIVE-117 | System settings destinations (open, not toggle) | Auto rotate settings | New/conditional | Source path: SystemTools auto_rotate; handler/API gated; opens only; device acceptance pending |
| NATIVE-118 | System settings destinations (open, not toggle) | Accessibility settings | New | Source path: SystemTools accessibility; handler/API gated; opens only; device acceptance pending |
| NATIVE-119 | System settings destinations (open, not toggle) | Keyboard/input settings | New | Source path: SystemTools keyboard; handler/API gated; opens only; device acceptance pending |
| NATIVE-120 | System settings destinations (open, not toggle) | Language/locale settings | New | Source path: SystemTools language; handler/API gated; opens only; device acceptance pending |
| NATIVE-121 | System settings destinations (open, not toggle) | App-specific language | New | Source path: SystemTools app_language; handler/API gated; opens only; device acceptance pending |
| NATIVE-122 | System settings destinations (open, not toggle) | Date/time settings | New | Source path: SystemTools date_time; handler/API gated; opens only; device acceptance pending |
| NATIVE-123 | System settings destinations (open, not toggle) | Battery saver | New | Source path: SystemTools battery; handler/API gated; opens only; device acceptance pending |
| NATIVE-124 | System settings destinations (open, not toggle) | Battery optimization list | New | Source path: SystemTools battery_optimization; handler/API gated; opens only; device acceptance pending |
| NATIVE-125 | System settings destinations (open, not toggle) | Storage management | New | Source path: SystemTools storage; handler/API gated; opens only; device acceptance pending |
| NATIVE-126 | System settings destinations (open, not toggle) | Data usage | New | Source path: SystemTools data_usage; handler/API gated; opens only; device acceptance pending |
| NATIVE-127 | System settings destinations (open, not toggle) | VPN settings | New | Source path: SystemTools vpn; handler/API gated; opens only; device acceptance pending |
| NATIVE-128 | System settings destinations (open, not toggle) | Privacy settings | New/conditional | Source path: SystemTools privacy; handler/API gated; opens only; device acceptance pending |
| NATIVE-129 | System settings destinations (open, not toggle) | Security settings | New | Source path: SystemTools security; handler/API gated; opens only; device acceptance pending |
| NATIVE-130 | System settings destinations (open, not toggle) | Biometric enrollment | New/conditional | Source path: SystemTools biometric_enrollment; handler/API gated; opens only; device acceptance pending |
| NATIVE-131 | System settings destinations (open, not toggle) | Accounts | New | Source path: SystemTools accounts; handler/API gated; opens only; device acceptance pending |
| NATIVE-132 | System settings destinations (open, not toggle) | Default applications | New | Source path: SystemTools default_apps; handler/API gated; opens only; device acceptance pending |
| NATIVE-133 | System settings destinations (open, not toggle) | App detail/permission page | New | Source path: SystemTools app; handler/API gated; opens only; device acceptance pending |
| NATIVE-134 | System settings destinations (open, not toggle) | Per-app notifications | New | Source path: SystemTools notifications; handler/API gated; opens only; device acceptance pending |
| NATIVE-135 | System settings destinations (open, not toggle) | Notification listener access | New | Source path: SystemTools notification_access; handler/API gated; opens only; device acceptance pending |
| NATIVE-136 | System settings destinations (open, not toggle) | Special write-settings access | New | Source path: SystemTools write_settings; handler/API gated; opens only; device acceptance pending |
| NATIVE-137 | System settings destinations (open, not toggle) | Overlay permission screen | New | Source path: SystemTools overlay; handler/API gated; opens only; device acceptance pending |
| NATIVE-138 | System settings destinations (open, not toggle) | Usage-access screen | New | Source path: SystemTools usage_access; handler/API gated; opens only; device acceptance pending |
| NATIVE-139 | System settings destinations (open, not toggle) | Unknown-source install access | Restricted | Source path: SystemTools unknown_sources; handler/API gated; opens only; device acceptance pending |
| NATIVE-140 | System settings destinations (open, not toggle) | Notification policy/DND access | New | Source path: SystemTools dnd_access; handler/API gated; opens only; device acceptance pending |
| NATIVE-141 | System settings destinations (open, not toggle) | Cast settings | New | Source path: SystemTools cast; handler/API gated; opens only; device acceptance pending |
| NATIVE-142 | System settings destinations (open, not toggle) | Developer options | Restricted | Source path: SystemTools developer; handler/API gated; opens only; device acceptance pending |
| NATIVE-143 | System settings destinations (open, not toggle) | App settings internal to Halo | Existing Halo UI | Source path: HaloScreenCatalog internal navigation; device acceptance pending |
| NATIVE-144 | Device state, quick controls and connectivity | Device make/model/OS/screen | Declared: halo_device_info | Source path: DeviceInsightTools Build/display; device acceptance pending |
| NATIVE-145 | Device state, quick controls and connectivity | Battery level/charging | Declared: halo_battery_status | Source path: DeviceInsightTools battery; device acceptance pending |
| NATIVE-146 | Device state, quick controls and connectivity | Connection type and state | Declared: halo_network_status | Source path: DeviceInsightTools network; device acceptance pending |
| NATIVE-147 | Device state, quick controls and connectivity | Wi-Fi enable/disable | Restricted API29+ | Source path: SystemTools internet panel; silent toggling unavailable; device acceptance pending |
| NATIVE-148 | Device state, quick controls and connectivity | Wi-Fi network selection | New/user-mediated | Source path: SystemTools internet panel; connection state not claimed; device acceptance pending |
| NATIVE-149 | Device state, quick controls and connectivity | Suggest Wi-Fi network | New/user-mediated | Source path: NativeWifiNetworkActivity; API30+ user-mediated add-network result; device acceptance pending |
| NATIVE-150 | Device state, quick controls and connectivity | Bluetooth enable request | New/user-mediated | Source path: NativeDestination Bluetooth system enable prompt; state unconfirmed; device acceptance pending |
| NATIVE-151 | Device state, quick controls and connectivity | Bluetooth disable/enable silently | Restricted API33+ | Pending |
| NATIVE-152 | Device state, quick controls and connectivity | Pair Bluetooth accessory | New/user-mediated | Source path: Existing companion-device pairing flow; device acceptance pending |
| NATIVE-153 | Device state, quick controls and connectivity | Show connected audio endpoint | Existing device/audio ownership | Source path: Audio route owner observed endpoint; device acceptance pending |
| NATIVE-154 | Device state, quick controls and connectivity | Switch Halo voice output | Existing audio ownership | Source path: SpeechOutput settings route owner; device acceptance pending |
| NATIVE-155 | Device state, quick controls and connectivity | Switch system-wide audio device | Conditional | Pending |
| NATIVE-156 | Device state, quick controls and connectivity | Flashlight on/off | Declared: halo_flashlight | Source path: DeviceTools torch; device acceptance pending |
| NATIVE-157 | Device state, quick controls and connectivity | Brightness change | Declared: halo_brightness | Source path: DeviceTools brightness permission/readback; device acceptance pending |
| NATIVE-158 | Device state, quick controls and connectivity | Ringer silent/vibrate/normal | Declared: halo_ringer | Source path: DeviceTools ringer permission/readback; device acceptance pending |
| NATIVE-159 | Device state, quick controls and connectivity | Do Not Disturb changes | Conditional | Pending |
| NATIVE-160 | Device state, quick controls and connectivity | Vibrate/haptic ping | New | Source path: NativeDestination HAPTIC; bounded vibration request, physical effect unverified; device acceptance pending |
| NATIVE-161 | Device state, quick controls and connectivity | Screen wake within Halo session | Conditional | Pending |
| NATIVE-162 | Device state, quick controls and connectivity | Device lock (admin-authorized) | Restricted | Pending |
| NATIVE-163 | Device state, quick controls and connectivity | Current location | New | Source path: NativeObservationTools location; permission and bounded fresh fix; device acceptance pending |
| NATIVE-164 | Device state, quick controls and connectivity | Nearby BLE device discovery | Existing HeyCyan bridge | Source path: HeyCyan authorized BLE discovery; device acceptance pending |
| NATIVE-165 | Device state, quick controls and connectivity | Sensor readings | New read-only | Source path: NativeObservationTools one-shot ambient light/proximity/accelerometer; device acceptance pending |
| NATIVE-166 | Device state, quick controls and connectivity | Show internet speed/connection quality | New/conditional | Source path: NativeObservationTools advertised bandwidth; throughput NOT implemented; device acceptance pending |
| NATIVE-167 | Notifications, assistant and interoperable routines | List allowed notifications | Declared: halo_list_notifications | Source path: NotificationControlTools permitted live entries; device acceptance pending |
| NATIVE-168 | Notifications, assistant and interoperable routines | Summarize notification counts | Declared: halo_notification_digest | Source path: Notification digest local owner; device acceptance pending |
| NATIVE-169 | Notifications, assistant and interoperable routines | Open notification origin | New/conditional | Source path: NotificationControlTools permitted live contentIntent; device acceptance pending |
| NATIVE-170 | Notifications, assistant and interoperable routines | Reply to notification | Declared: halo_reply_notification | Source path: Notification RemoteInput owner; device acceptance pending |
| NATIVE-171 | Notifications, assistant and interoperable routines | Dismiss notification | Declared: halo_dismiss_notification | Source path: NotificationControlTools dismiss; live notification disappearance readback; device acceptance pending |
| NATIVE-172 | Notifications, assistant and interoperable routines | List reply-capable notifications | Declared: halo_replyable_notifications | Source path: Notification reply discovery; device acceptance pending |
| NATIVE-173 | Notifications, assistant and interoperable routines | Create Halo notification | Existing infrastructure | Source path: Existing Halo-owned notification infrastructure; device acceptance pending |
| NATIVE-174 | Notifications, assistant and interoperable routines | Open notification settings | New | Source path: SystemTools app notifications; device acceptance pending |
| NATIVE-175 | Notifications, assistant and interoperable routines | Set Halo as default assistant | Existing assistant implementation | Source path: AssistantRoleController user-mediated role request; device acceptance pending |
| NATIVE-176 | Notifications, assistant and interoperable routines | Receive system assistant invocation | Existing | Source path: VoiceInteractionService/session; device acceptance pending |
| NATIVE-177 | Notifications, assistant and interoperable routines | Voice continuation/confirmation | Existing | Source path: VoiceInteractor owned continuation; device acceptance pending |
| NATIVE-178 | Notifications, assistant and interoperable routines | Expose Halo tools to other assistants | Existing limited functions | Source path: HaloAppFunctionService limited schemas; device acceptance pending |
| NATIVE-179 | Notifications, assistant and interoperable routines | Invoke other app functions | Partial semantic app bridge | Source path: SemanticAppActionBroker exposed schemas only; device acceptance pending |
| NATIVE-180 | Notifications, assistant and interoperable routines | Respond to direct actions | Existing semantic bridge | Source path: NativeAssistantActions scoped tokens; device acceptance pending |
| NATIVE-181 | Notifications, assistant and interoperable routines | Trigger app-provided shortcut | New/conditional | Source path: NativeShortcutTools run; shortcut host role required; device acceptance pending |
| NATIVE-182 | Notifications, assistant and interoperable routines | Perform explicit user-defined multi-step routine | Existing native sequenced operations | Source path: NativeCapabilityDispatcher confirmed-step sequences; device acceptance pending |
| NATIVE-183 | Notifications, assistant and interoperable routines | Receive intent-driven Halo task from other apps | New inbound | Source path: MainActivity exact internal page handoff; arbitrary task execution unsupported; device acceptance pending |
| NATIVE-184 | Notifications, assistant and interoperable routines | Home-screen shortcut to Halo workflow | New | Source path: NativeShortcutTools pin_halo_page; user pin request, actual placement unconfirmed; device acceptance pending |
| NATIVE-185 | Notifications, assistant and interoperable routines | Quick Settings tile for Halo | New inbound | Source path: HaloTalkTileService user-added tile; device acceptance pending |
| NATIVE-186 | Notifications, assistant and interoperable routines | Android widget for Halo status/actions | New product integration | Source path: HaloNativeWidget Talk/Notebook entry points; device acceptance pending |
| NATIVE-187 | Notifications, assistant and interoperable routines | Wearable button mapped to safe native command | Existing device ecosystem | Source path: HeyCyan input origin and native dispatch owner; device acceptance pending |
