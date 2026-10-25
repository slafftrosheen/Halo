# Canvas selection and shell entry recovery — Android #55 / Interface4 #62

Baseline main d75a05e99b67b76204050c5a281e8a875c6c54b1; installed checkpoint 16. Actual named test Canvas contains one confirmed, cold-reopened text card from a genuine free-model note→Canvas journey.

Physical failure: after Select mode is enabled, tapping the text card body focuses its BasicTextField and opens the keyboard instead of selecting the card. The field remains interactive while onFocusChanged explicitly skips selectNode in selectionMode, consuming the parent Surface tap. Header/footer/empty Canvas also retain assistant launchers; the footer draws a second Halo face next to the shell-owned Orb, contrary to #62's sole canonical entry requirement.

1. Add failing UI regressions first: Select-mode body tap selects the actual node without editing/IME/write; normal editing remains supported. Cover equivalent group/tag inline editors if the same event-consumption cause applies. Use existing canonical ViewModel/repository fixtures.
2. Make card content nonediting in selection mode so the existing parent selectNode owner receives the tap. Keep ordinary edit behavior, persistence, tokens and version checks unchanged. No new selection owner or data model.
3. Suppress equivalent Canvas header/dock/empty assistant launchers when the shell owns the Orb; preserve standalone fallback, six local editing commands and existing context/global tools so supported functionality stays reachable. No second assistant/session/Orb owner.
4. Run focused RED, minimal fix, affected Notebook/Home/Shell/tools UI tests and both APK builds. Review source/feature reachability. Install and verify hashes; repeat body selection and the selected-card AI path on the physical board, check one global Orb, editing/export/readback and source-note preservation. Keep unrelated voice/provider/rapid-shell stress acceptance open.

Root owns plans/builds/device/publication; delegated work remains in Canvas screen/components and narrowly related tests. No provider, budget, permission, privacy, sharing, model-download or runtime authority changes. Experimental input diagnostics from checkpoint 16 remain archived; this is a separate confirmed Canvas event-routing defect.
