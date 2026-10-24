# Interface 4.0 provider discovery and connection checks

Integrated test execution and exact device/artifact results: [verification ledger](INTERFACE-4-VERIFICATION.md). Sub-batch source notes below retain their original evidence boundaries.

`ProviderConfigurationTools` exposes four registered-operation candidates:
`halo_provider_catalog`, `halo_provider_state`, `halo_provider_refresh_models`,
and `halo_provider_test_connection`. The first two read the existing local
provider/catalogue projection without a network request. The latter two call
`ProviderControlRepository`'s existing discovery/authentication owners, after an
exact observed process-local configuration version and current foreground,
grown-up and interaction admission. Integration must register their operability
capability and canonical ALWAYS/NEVER_REPLAY policy for checks.

The repository compares versions under its existing configuration-write mutex,
consumes a check token before invoking the owner, and holds that mutex across
the check. Canonical credential/configuration writes share this mutex. Private
mode cannot obtain remote metadata authority: remote operations acquire the
existing dispatch policy with PROVIDER_CHECK and ACCOUNT_METADATA classification,
and the existing guarded provider HTTP transport retains final revocable network
authority. On-device inventory does not need remote authority. No new transport,
credential/OAuth edit, provider selection, inference or generation probe is added.

Results project bounded provider/model identities, enum readiness/catalogue state,
and enum diagnostic stage/outcome. They omit configuration fields, keys, endpoint
URLs, error bodies and free-form diagnostic summaries. Model labels are untrusted
data; URL-shaped IDs are omitted and display-name URLs are redacted. Catalogue
entries retain their actual live/cached source. Completed authentication failure
remains AUTH_FAILED evidence; completed metadata checks do not prove inference,
cost, spoken output or device qualification. Uncertain outcomes must not replay.

Regression sources authored for the integrated gate:

- `ProviderConfigurationToolsTest`: closed arguments, bounded/non-secret readback,
  foreground/grown-up callback, scoped interaction, exact token rejection/consumption,
  and honest completed authentication-failure projection.
- `ProviderMetadataPolicyTest`: remote dispatch blocked without authority, exact
  provider-check/account-metadata intent, ThreadLocal context restoration, and
  on-device inventory behavior.
- `ProviderControlSafetyTest`: live repository projection, missing-policy denial,
  and configuration changes invalidating observed check versions. Existing rotated
  credential and stale proof tests remain applicable.

This sub-batch ran no Gradle, installation, remote provider request or device test.
Compilation and test execution belong to the root integrated gate. Actual remote
provider, typed/STT-final device, privacy-switch cancellation and native foreground
acceptance are NOT_RUN here. This source report does not close #62.
