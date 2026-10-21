# Issue #40 — preserve the free route through model selection and fallback

Date: 2026-10-21. Base: `2bc74fab52f06ca738e781451346a2dfeb40d0e7`.
Status: **source implemented; not built or tested; device/provider acceptance pending**.

## Defect traced in current main

`OpenRouterProvider.selectedModelId` defaults to `openrouter/free`, but most runtime
requests first go through `DefaultAiProviderRouter.resolveTargetForProvider`.
With OpenRouter selected and no explicit model, that method previously ranked
all compatible advertised models, then supplied the chosen model ID to the
provider. This bypassed the adapter's free default. A paid model could win by
catalogue ordering/ranking. Automatic roles, measured automatic targets and
cross-provider fallback were separate ways to leave the free selection.

## Changes

- `OpenRouterFreeRouting` is a small, non-persisted routing policy. OpenRouter
  without a model selects exactly `openrouter/free`; the legacy `free` alias and
  whitespace are normalized only in OpenRouter's namespace. Other providers
  retain their existing selection behavior.
- The selected/preferred OpenRouter model is exact, not permission to substitute
  another advertised model. A missing/incompatible free route fails honestly;
  it cannot turn into the first paid model.
- A free OpenRouter conversation boundary propagates into inherited and
  automatic roles. A measured automatic target is rejected when it would change
  that boundary to a paid model or a different remote provider. Local targets and
  explicit independent role selections retain their existing behavior.
- Both initial resolution and fallback exclude other remote providers while the
  free boundary is active. A ready local target remains eligible **only when the
  existing routing/privacy/workload policy allows fallback**. Explicit selections
  still fail rather than switch providers. Local-only still excludes OpenRouter.
- A request-level paid model override cannot quietly replace an active free
  selection. It fails with `FREE_ROUTE_PAID_TARGET_BLOCKED` before model discovery
  or inference. An explicitly selected paid conversation/role remains possible.
- Standalone fallback carries the actual failed model ID, not only the original
  potentially model-less request. A failed free request stays protected even
  when settings change while it is running.
- No new account, proxy, SDK, credential storage, schema migration, UI screen,
  model download or background job is introduced. Tool/schema/permission gates
  and explicit pinned-target contracts are not replaced by this cost policy.

## Source regression cases — authored, NOT executed

`engines/ai/src/test/java/com/halo/engines/ai/OpenRouterFreeRoutingTest.kt` adds
18 cases against the real router with in-memory provider/storage fixtures:
provider-only default, restart without rewriting preferences, blank/legacy IDs,
missing free route, explicit paid and free variants, inherited/automatic roles,
measured target overrides, initial and generation fallback, mid-turn selection
changes, request overrides, local-only and unaffected non-OpenRouter selections.
The rejected paid override also checks that model discovery was not invoked.

This is test source, **not a passing test report**. No Gradle, compiler, test
runner, CI/Actions, live provider request or device experiment was run.
Review covered the changed call sites, SDK contracts, byte-identical base source
reconstruction, and patch whitespace. This is not runtime qualification.

## Deliberately outside this batch; keep #40 open

The issue also requires sourced live web retrieval, specialist/media feature-level
consent and preflight cost UX, spend estimates/provider-native limit guidance,
and real-key acceptance of unapproved paid operations. These are not completed
by a text-routing guard. Explicit pinned workloads and image-generation adapters
retain their existing authorization contracts and need their own admission audit.
No claim is made that every external operation is now free, that a third-party
price cannot change, or that an app-side budget is a provider-enforced limit.

Owner acceptance, when building/testing is authorized:

1. Choose OpenRouter with no model/legacy `free`; inspect the requested model ID
   and confirm a real reply. Try a paid-first catalogue and missing free route.
2. Exercise inherited and automatic text roles and a measured paid profile.
   Confirm no paid provider/model request; verify a separately selected paid role
   still requires that deliberate selection and uses that exact target.
3. Simulate free-route failure with only paid clouds, then with a local model.
   Verify no paid request, allowed local fallback only, no silent success.
4. Switch settings during a free request; check its fallback remains free/local.
5. Recheck local-only, cloud context restrictions, tools/vision eligibility,
   offline/rate-limit errors and the separate specialist-media consent paths.

## External contract reference

OpenRouter's official Free Models Router page identifies `openrouter/free` as a
route over free models, with changing resolved models and capability filtering:
https://openrouter.ai/openrouter/free/apps (reviewed 2026-10-21).
It is not a fixed model, an availability promise or a substitute for Halo's
capability evidence, consent and tool authorization.
