# Observed startup failure and recovery — 2 November 2026

The user showed the installed phone screen. Halo's recovery page displayed `Built-in skills missing manifests: halo.everyday`; the interface was not ready for manual testing. The earlier install/launch result and 306 focused tests were valid for their exact scope, but did not prove successful shell startup. The previous ready-for-testing statement missed this failure.

Root cause: Runtime registered the new Everyday built-in without shipping `assets/skills/halo.everyday.json`. `BuiltInSkillManifestLoader.load()` correctly fails closed when a Runtime built-in lacks its packaged manifest. The existing strict manifest tests would have detected the omission, but were outside the previous focused filter. The Screen asset also lacked its new per-card configuration query, though Runtime merging preserved that tool.

Correction: ship Everyday's complete22-tool declaration with matching per-tool risks and no implied Android permission grants; ship the Screen query with READ_ONLY risk; extend configuration parity/unique-owner/provenance checks to Everyday and all75 affected operations. Preserve strict Runtime validation. A lightweight `preBuild` presence/identity guard now rejects an APK build when a literal built-in declaration lacks its asset; full schema/tool/risk validation remains the Runtime/host-test authority.

The new build presence guard was first run against the broken asset set and reproduced the missing Everyday manifest failure (36s, expected nonzero exit). Targeted strict loader, parity, starter and assistant integration checks, corrected APK packaging and actual phone-screen verification are pending at this source checkpoint. No phone interaction beyond screen inspection has been performed for this correction yet. Phone captures remain outside Git.


Correction gate: PASS18 tests in four suites (strict bundled manifest policy, complete configuration parity/provenance/owner/risk coverage, starter package integration and assistant tools), zero failures/errors/skips. Combined test/assemble build succeeded in 9m37s from code commit892930a16. APK inspection confirms46 packaged skill manifests and byte-exact Everyday/Screen assets; Everyday declares22 tools, and Screen includes the eighth configuration query. The new presence guard also passed after correcting the asset set.

Reinstallation succeeded with data retained. Cold launch returned Status ok, Halo remained running and app-only fatal diagnostics were empty. Another app was foreground at the attempted screen check; visible-shell acceptance remains pending the user's requested foreground confirmation. No unrelated on-screen content was analyzed or retained in repository evidence. See HALO-STARTUP-MANIFEST-2026-11-02 artifacts for the bounded source/JUnit/APK result.


The user subsequently confirmed **“it opens”** and requested all local work pushed to Git. Startup recovery is therefore confirmed by the user. This is not a claim of independently inspected Home content or broader feature/assistive/launcher acceptance. The connected phone is left for the user's own testing.
