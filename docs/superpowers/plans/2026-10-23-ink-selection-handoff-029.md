# Ink selection handoff #29

Base main `9cb329f44a36de507cce47ca29451cf6b4286a06`. Existing portable strokes, bookmarks, selection UI, crop and Study/context flows remain authoritative. No new input engine, AI route, tool or permission.

- [x] Resolve/select exact expected bookmark and current source in one canonical Room transaction; reject inactive imported source objects.
- [x] Guard render-to-Ask/Study handoff and attachment ingest; propagate coroutine cancellation and release rejected derived media.
- [x] Add actual Room bookmark/source lifecycle/deselection and actual media-store delayed-ingest regressions; read-only source review.
- [x] Run focused app compilation/regressions once source is complete. Publish verified code; keep pen/tablet/spoken/provider acceptance separate.

Source audit: live #29 body predates existing selection IDs, v3 codec, bookmarks, region images, Ask, Study and working-set integration. This patch closes a render-time check/use gap in those existing flows. No new ordinary UI copy; existing EN/RU/LV messages are reused.

Review found the final guard also had to cover suspending context ownership transfer. The canonical source owner now publishes the synchronous context focus inside its validation transaction, through an ephemeral host-only commit callback. Known attachment/context owner IDs cover cancellation before reference delivery. Actual Room/media regressions pause that final transfer and test deselection, replacement and cancellation.

First focused app gate passed 37 cases, zero failures/errors/skips, in 6 minutes 17 seconds. No APK assembly or device install.
