# Plugin SDK Specification

Plugins run within a restrictive sandbox. They do not have direct access to Android APIs, the SQLite database, or AI providers. 

## Plugin Sandbox
Plugins receive:
* `Context` (Read-only snapshot)
* `Commands` (Ability to execute actions via Command Bus)
* `Events` (Subscribe to system events via Event Bus)
* `Queries` (Read data via Query Bus)

**Never** Android Context. 
**Never** Activity. 
**Never** Fragment. 
**Never** Compose.

## Halo Plugin Store
Halo does not host plugins in a built-in marketplace. 
To remain vendor-neutral, Halo supports:
* Git repositories
* ZIP packages
* F-Droid
* GitHub Releases
* Local installation
* Enterprise repositories

The application only knows how to verify, install, and sandbox them.

## Plugin Lifecycle
1. **Install**: Plugin verified and registered.
2. **Validate**: Permissions and capabilities checked.
3. **Load**: Code loaded into memory.
4. **Initialize**: Setup hooks and commands.
5. **Ready/Running**: Active state.
6. **Unload**: Cleanup resources.
7. **Disabled**: Inactive.

## Manifest & Permissions
Every plugin must declare a manifest specifying the permissions it needs.
Examples: `Calendar`, `Notifications`, `Network`, `Location`, `Memory`, `Filesystem`.
The Privacy Engine enforces these restrictions at runtime.
