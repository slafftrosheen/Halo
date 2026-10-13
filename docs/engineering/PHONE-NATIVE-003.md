# PHONE-NATIVE-003 — user-reviewed Contacts and Calendar editors

Date: 2026-10-13.

## Delivered
- `halo_contact_editor`: opens Android's `ACTION_INSERT` contact editor, or `ACTION_INSERT_OR_EDIT` chooser. Optional name/phone/email are bounded; for existing-contact choice, prefilled name is **rejected** to avoid overwriting the wrong person's name. No blanket WRITE_CONTACTS request.
- `halo_calendar_editor`: opens `ACTION_INSERT` for calendar events with optional title and paired start/end milliseconds. No WRITE_CALENDAR permission needed to open the editor. Existing `halo_calendar_create` (direct, authorized provider write) is retained without changing behavior.
- Provider-independent spoken commands: create new contact, edit a contact, create a calendar event, open calendar event editor. Complex unresolved slots remain conversational/agentic rather than guessing dates or contact identity.
- Both editor tools require mandatory confirmation, cannot replay, and return DISPATCHED only. User saves or cancels in the target app.
- Android 11+ scoped package queries added for these editors and AlarmClock ACTION_* intents. No QUERY_ALL_PACKAGES.
- Existing tools, provider roles, journal, tool registry and architecture remain unchanged.

## Verification pending
No Gradle, emulator or physical device acceptance was executed. Test missing handlers, absent contacts/calendar permissions, blocked approvals, process restart, contact overwrite guard, and actual user save/cancel paths in the final acceptance pass.

Sources:
- https://developer.android.com/identity/providers/contacts-provider/modify-data
- https://developer.android.com/identity/providers/calendar-provider
- https://developer.android.com/reference/android/provider/AlarmClock
