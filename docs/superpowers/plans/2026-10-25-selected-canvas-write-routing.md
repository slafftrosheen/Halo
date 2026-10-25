# Selected Canvas replacement routing recovery

Physical request: `Change only the selected text card on this Canvas to exactly: Silver kites rest at noon. They fly west at dusk.` It became ConversationIntent/DirectResponse with tools disabled; no native write happened, and the existing card stayed unchanged. This is a routing failure, separate from the card-body selection repair.

Canonical owners are `ConversationSemantics.workspaceWrite` for bounded positive action evidence and `SpeechRule` for classifier-absent/failed fallback. The current English cue omits `change` and `only`; `only the selected text` also exceeds its three-modifier limit. Matching must remain anchored to an explicit supported workspace object. It must not resolve a selected card, invent tool arguments, authorize a write, or bypass existing source/consent checks.

1. RED: run the two new standalone language/interpretation suites against unchanged production. Cover the exact two-sentence phone goal, bounded polite/verb/modifier variants, typed and STT-final parity, unavailable classifier, no fabricated entities, conversation/quotation/negation/model-only guards, bounded modifiers and exact object-word boundaries.
2. After root confirms valid RED, minimally extend the canonical English workspace cue for `change` and `only` with a finite modifier allowance. Preserve SpeechRule's existing explanation/model-only precedence and tool authorization. No generic conversation-to-action fallback or execution changes.
3. Run both new suites plus existing WorkspaceActionRoutingTest, PoliteCommandTest and SmallIntentSpeechRuleTest. Root owns build/device validation: select the real existing card, repeat the exact goal, inspect native typed edit/version evidence and reopen persisted content. A tool-capable intent alone proves neither a write nor model/provider success.

RED command (root only):

```powershell
.\gradlew.bat :libraries:halo-cognition-language:test --tests com.halo.cognition.language.SelectedCanvasWriteRoutingTest :engines:interpretation:test --tests com.halo.engines.interpretation.rules.SelectedCanvasWriteSpeechRuleTest
```

This batch initially changes tests and this plan only. Production, model configuration, device state and execution permissions remain frozen until root authorizes GREEN.
