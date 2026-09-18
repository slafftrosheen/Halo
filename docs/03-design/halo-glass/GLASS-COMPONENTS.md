# Glass components: implementation map

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

The [component directory](../../../app/src/main/java/com/halo/app/ui/glass/components)
contains cards, buttons, chips, dialogs/sheets, navigation, status/empty/loading/error
components, text fields, Markdown and HaloStateOrb.

Read exact composable signatures from source. The old snippets did not consistently match
current parameters/types and were not a compiled API reference. Material3 is used by the
implementation; “no raw Material3” was not a repository-wide fact.

Older components coexist under app/ui/components. Consolidation and whole-screen adoption
remain next-phase work. Source existence does not establish route reachability, semantic
accessibility or finished interaction behavior.
