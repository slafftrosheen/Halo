# FEATURE-065 / MCP-UI-001 — integrations control plane

Implemented after INBOX-001, on b4ce8346. No LiteRT, role routing, protocol-version,
transport, schema migration, remote shell/filesystem authority or second tool /
secret registry is introduced.

## Product surface

Settings → Integrations and Skills’ MCP servers/tools counts open `integrations`.
The secondary detail route is `integrations/mcp/{serverId}`; Settings remains the
selected primary tab. The list has local server/tool name/description search,
counts, connection labels, redacted endpoint display, an explanation and an empty
state. Add/edit use an ephemeral editor sheet. Server UUID identity survives edits.
Endpoints must be absolute HTTP/HTTPS URIs with a host and at most 2048 characters;
malformed endpoints are not repaired or extended. Embedded URI credentials are
rejected. HTTP is allowed with explicit unencrypted-transport disclosure. Endpoint
queries/fragments are omitted from passive display and never logged.

Detail separates enabled configuration, live connection, negotiated protocol,
last connected time and cached tool metadata. Protocol history is labelled as a
previous negotiation when disconnected. New servers show Saved · Disabled and
require enable plus explicit connect. Connect initializes the session only;
Refresh tools explicitly connects if required and lists/replaces metadata. Neither
operation calls a remote tool. Disable is the explicit stop; no extra Disconnect
control is introduced. Tools have descriptions, actual risk and enable switches,
with a detail sheet for remote name and Halo identity. Risk cannot be edited.

## Existing authority

McpSettingsService composes McpDao, McpIntegrationManager and SecretStore. Compose
receives safe UI models, not Room entities. The manager still owns sessions and
normal runtime execution; its configuration mutex remains the live authority.
Opening Settings, Integrations, Skills or System Check does not initialize, list
or call tools. UI coroutines own explicit discovery and propagate cancellation.
Operation states prevent conflicting/double-tap actions; cancellation clears a
partial CONNECTING state and synchronizes registry removal in a bounded cleanup.
Configuration/secrets cleanup is non-cancellable to finish local consistency.

Every mutation or explicit discovery synchronizes the same ToolRegistry overlay.
Disabled servers, disabled tools and remotely removed tools disappear without
restart. Skills’ passive capability summary refreshes after sync. Restart retains
metadata and last-connect/protocol history but starts with UNKNOWN live state;
it never fabricates a connected session or automatically reconnects.

The refresh persistence path now retains protocol/lastConnectedAt from its actual
handshake instead of writing the earlier disconnected row back over those facts.
The manager has an explicit replaceCredentialReference option: null can clear a
reference, while existing callers retain the original preserve-null contract.

## Credentials and threat boundaries

Only NONE and BEARER are supported. Existing bearer values are never loaded into
visible UI state; UI receives only credential presence. Replacement input is
masked ephemeral Compose state, not rememberSaveable, SavedStateHandle or Room.
A generated `mcp.server.<serverId>.bearer` reference points to the existing
Android keystore SecretStore. No secret goes through the manager as plaintext.
The old session is invalidated before secret changes. Replacing requires explicit
reconnection; BEARER → NONE removes the secret and clears the reference. Removing
only the credential leaves BEARER and displays Credential required. The existing
client rejects missing credentials before any HTTP request.

Removal disables the integration and synchronizes tools first, removes its secret,
then removes Room tools/configuration and synchronizes again. If secret removal
fails, the disabled configuration retains the reference so cleanup can be retried.
No remote deletion request is sent. Save failure cleans up a newly added record.

BrainSnapshot exports an explicit portable-content/settings projection, not the
SecretStore or raw database. MCP credentials are written exclusively to keystore files under Android’s
no-backup directory. Android backup rules include halo.db, datastore and
appearance preferences, with no secret files. No new MCP secret export is added. Existing backup
secret guards remain in force. Configuration provenance never grants authority.
Remote metadata/results stay untrusted; trust never downgrades SENSITIVE risk or
bypasses normal schema, availability, permissions, confirmation, journal or limits.
The settings service exposes no callTool operation. No delegated permissions or
AI router behavior change. Logs include only operation, ID prefix and live state;
error display permits bounded content-free client messages, never remote bodies,
headers, descriptions, credentials or stack traces.

## Validation

Focused fake-transport tests cover passive add/list (zero network), exact endpoint
validation, explicit one-handshake connect, refresh replacement and handshake
persistence, immediate server/tool removal, trust/risk invariants, credential
storage/replacement/removal, restart truth, cancellation and protocol recovery.
A source audit confirms no tools/call execution path in settings/UI.

Required serial gate: `:runtime:cognition:test :app:testDebugUnitTest
:app:assembleDebug --no-parallel --max-workers=1 --no-daemon`, 2 GB heap.
The required gate passed: 457 tests (212 cognition + 245 app), zero failures,
errors or skips; debug APK assembled. After the scrolling detail-sheet correction, all 17 focused MCP tests passed
again and the final debug APK assembled. `git diff --check` passed. Physical /
live-server feature acceptance is pending the compact manual sanity pass.

## Compact manual sanity pass (pending)

Open Integrations; add a server and confirm no connection occurs. Enable and
Connect; Refresh tools; disable/re-enable a tool and inspect Skills/catalogue.
Disable the server and confirm live tools disappear. Re-enable/connect; replace
and remove authentication; remove the integration and verify its secret and tools
are gone. Do not execute a discovered remote tool from Settings validation.
