# Skills, local models, and everyday workspace use

Reviewed 2026-10-11. This extends the current skill registry and host-owned action gateways.

## Functional changes

- Every runtime built-in skill has loadable operational guidance. Previously, most shipped manifests had no corresponding instruction body, so on-demand guidance could not load for those skills.
- Notebook guidance uses actual workspace object IDs and avoids duplicate creation. Study guidance preserves source identity and language. App guidance discovers actual supported capabilities; sharing and messaging distinguish platform dispatch from confirmed delivery.
- Existing detailed screen-control guidance remains intact. The runtime still owns tool exposure, approval, availability, and action receipts. Instructions do not grant Android permissions or declare new AppFunctions.
- Custom imports use synchronized [Android AtomicFile](https://developer.android.com/reference/android/util/AtomicFile) writes and bounded reads. Invalid schema values, built-in identity collisions, oversized packages, and duplicate IDs are rejected or reported. Interrupted legacy backup writes can be recovered. Imports remain disabled by default.
- Replacing an enabled custom package now persists disabled enablement before publishing the new instructions. Invalid packages leave the existing enabled skill intact; valid updates require a fresh user decision, including after a restart. A storage or write failure cannot publish an enabled update.
- Imports verify the committed bytes before reporting success because Android AtomicFile can log a failed final rename without throwing. Package tests use API 28's supported backup-based implementation on this Windows host; newer Android rename-over-existing assumes POSIX semantics and is not reproduced by Windows File.renameTo.

## UI changes

- Skills starts with search, local-model setup, and model-role selection. Global approval controls and technical capability detail are under Advanced details.
- Built-in and custom instructions can be reviewed in scrollable dialogs. Source instructions remain untranslated.
- Search matches names, descriptions, IDs, and registered tools, while legacy aggregate bundles remain hidden to avoid duplicate entries.
- Library's More tools now links to Skills. Everyday labels have EN/RU/LV translations.
- Built-in skill names are translated consistently in list and detail views, and search includes the displayed localized names alongside source names and tool IDs. Custom names and instruction bodies remain package-authored text.

## Platform reference

[Android AppFunctions documentation](https://developer.android.com/ai/appfunctions) confirms that discovery/execution depends on platform support, enabled functions, and caller permission. These changes preserve Halo's existing supported-interface discovery and approval gateways; they do not claim universal app control.

## Validation

Runtime cognition tests, app unit/interaction/screenshot tests, and a debug APK build are run for this change. Physical navigation, package installation, and inference/action acceptance are recorded separately below after validation completes.

The full gate passed: 273 runtime cognition tests and 598 app tests, all with zero failures, errors, or skips; the debug APK build succeeded. Screenshot review then led to a standalone themed Skills surface and immediate filtered results: model setup is hidden during search and the results list resets to the top. Focused package/search/interaction/screenshot checks and APK packaging are repeated after this layout correction.

The initial runtime run exposed an outdated envelope expectation: conflicting nested output was expected to replace the host's action truth. The test now verifies matching-envelope idempotence and isolation of conflicting nested claims. The implementation's authoritative receipt handling is unchanged.

The final import/layout checks passed (10 focused app tests, zero failures/errors/skips), and the debug APK was installed successfully on the connected Android API 36 phone. Physical navigation confirmed Settings → Skills, Skills → On-device models, Skills → Model roles, and Library → More tools → Skills. The Russian phone UI revealed missing built-in names; 26 additional labels and localized-name search were added.

The localized functional gate passed: 11 focused tests, zero failures/errors/skips, and a successful debug APK build. That APK was installed and phone search by `halo_open_app` immediately displayed the translated Apps & Navigation entry while hiding model setup. The final three translation-only labels are checked against all shipped manifest names, then packaged without repeating unchanged functional tests.

On the phone, the Apps & Navigation detail dialog displayed the same translated title and successfully loaded the new built-in guidance using Read instructions. The final catalogue check found zero shipped skill manifest names without translation entries.

The final translation-only APK build succeeded and was installed on the connected phone. UTF-8, nested-translation, and `git diff --check` checks passed. Changes remain local and uncommitted.

No model download, native multimodal inference, message dispatch, permission grant, or new phone/app action was performed during this UI validation. Installed-model inference and action effects remain separate device acceptance work.
