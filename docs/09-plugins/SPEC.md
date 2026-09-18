# Plugins: implementation and limits

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

[PluginRegistry](../../runtime/plugins/src/main/java/com/halo/runtime/plugins/PluginRegistry.kt)
registers in-process objects, rejects duplicate IDs, contributes capabilities and rolls
back partial registration. Unregister removes contributed capabilities.
[PluginManager](../../runtime/plugins/src/main/java/com/halo/runtime/plugins/PluginManager.kt)
dispatches to the first registered plugin supporting an intent.

These contracts are not a security sandbox. An in-process context interface cannot by
itself prohibit arbitrary code from accessing APIs available to the application.
A verified Git/ZIP/F-Droid installer, package isolation and full permission enforcement
are not established by this implementation. Such a system requires a separate design.
