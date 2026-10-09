# Enlighten Study integration

Implementation date: 2026-10-09. User requested implementing all candidates from the Enlighten mining report before one combined physical acceptance pass. The reference snapshot is bf50c73ea7651cf0bcb3de1afe6644076b900863. The implementation is original Halo code; the upstream application was inspected, not executed.

## Delivered scope

| Candidate | Implementation |
|---|---|
| Local image OCR | Bundled ML Kit Latin recognition, bounded image preparation, cancellation-safe native cleanup |
| Capture to review to Study/Ask/Save | Existing capture retains the original; editable extraction review creates a local-only Notebook note and explicitly opens the selected existing workflow |
| PDF OCR fallback | Explicit selected page/range, at most eight pages, bounded dimensions/pixels, per-page locators and progress |
| DOCX text | Bounded ZIP/XML parser with external entities disabled, paragraphs/tabs/breaks retained; layout is not reproduced |
| Ordered multi-source import | Up to four sources, reorder reviewed parts, retained original files through existing Workspace owner |
| Progress and coverage | Per-source/page warnings, partial results retained on cancellation, explicit character limits |
| Small-model cards | Local target uses bounded source-qualified Q/A lines normalized to existing card drafts requiring Keep; remote JSON retained |
| Read-along offsets | Original sentence offsets and optional actual PCM playback-range callbacks; sentence fallback where timings are unavailable |
| Reader transport | Pause retains position, generation guards late completions, previous/next, persistent exact-version anchors and speed controls |
| Listen then explain | Explicit source narration with compact explanation generated concurrently; matching response narration follows source completion; Stop cancels handoff |
| Compact explanations | Source-grounded compact prompt and unanswered practice prompt, existing citation and reveal rules retained |
| Execution labels | Actual response provider/model metadata labels on-phone/off-phone/unknown; metadata failure does not discard the answer |
| Neural voice and cache | Explicit English Kokoro v0.19 ZIP import through existing Sherpa/native owner, routed PCM playback, private model/voice/language/speed/text cache with 64 MB quota and seven-day TTL |
| Saved study sets | Existing durable lesson store shows source/card/saved-response counts; no parallel lesson database |

## Boundaries

No automatic downloads, model changes, computer connections, or cloud-sharing changes. OCR supports Latin script; Russian/Cyrillic is unsupported. Latvian diacritic accuracy requires the physical pass. PDF extraction is OCR of selected rendered pages, not native PDF text extraction. DOCX formatting/embedded objects are not reconstructed. Imported sources are bounded to 32 MB; decoded extraction is bounded to 100,000 characters. Errors keep original sources and completed review text.

Kokoro import accepts an explicitly selected ZIP with model.onnx, voices.bin, tokens.txt and espeak-ng-data, optionally under kokoro-en-v0_19. Official tar archives must be extracted and repackaged as ZIP. Import validates paths, work budgets, required files and native initialization before replacing the prior pack. Model artifact licensing remains the user's supplied pack responsibility. No model pack was downloaded or native voice physically qualified during implementation.

Word ranges are emitted only during verified routed playback and only when the provider supplies timings for unchanged text. Kokoro has sentence highlighting without fabricated word timing. Pausing restarts the current sentence on Resume. Existing durable citations, review scheduling and model/provider selection remain the owners.

## Learner interface

User clarified that Study should welcome children and students. Source-level Design of Everyday Things audit initially scored 6/10: recovery and feedback were present, but crowded actions and technical wording weakened discoverability and mapping. The revised extraction flow uses three plain steps, stacked full-width actions with at least 48 dp targets, Move up/down labels and confirmation before removing a draft. Learning level and support style use familiar school labels and Guide me/Let me practise/Explain it. Learning setup remains explicit; no age is inferred or account profile changed. Reader controls keep standard accessibility descriptions, readable speed names and clear pause behavior. Original offsets remain in the reader state, not foreground copy. Existing Material typography scales with system font size; controls with multiple options scroll rather than squeeze. Source restrictions and AI uncertainty remain visible. A final usability score requires observing younger learners; no child/student usability acceptance is claimed from source review.

## Validation

Serial integration gate passed 807 tests with zero failures/errors/skips: provider SDK 26, runtime SDK 52, cognition 244, speech engine 21, Android host 20, ElevenLabs provider 9, app 435. Android TTS and provider-core test tasks contain no tests. Debug assembly passed. The student-focused UI refinement arrived during that gate; a final committed app test/assembly pass follows before installation. No physical acceptance is claimed for this change.

## Combined physical acceptance

After source validation and installation: verify image OCR against a Latvian photo, edit/reorder multiple imports, Save/Ask/Study restrictions, selected PDF pages and cancellation, DOCX paragraphs, local Gemma cards requiring Keep, compact source-grounded explanations, pause/resume/skip/speed and exact-version restoration, stop during listen/explain, actual location labels, and Sony headset narration with phone speaker silent. If an English Kokoro pack is supplied, verify import, voice/speed, cancellation, cache reuse and missing/invalid-pack recovery. No HeyCyan or Wear OS acceptance is inferred from the phone pass.
