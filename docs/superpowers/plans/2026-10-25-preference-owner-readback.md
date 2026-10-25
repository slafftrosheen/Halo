# Shared visual preference readback

The expanded Android gate exposed a confirmed visual-options write followed by a default-valued tool read. A real SharedPreferences namespace regression reproduced the mismatch: the injected visual owner held contrast=true while the adapter reopened a different context's namespace and reported false.

1. Preserve the existing parity assertion and add deterministic injected-owner namespace coverage; capture RED before source changes.
2. Add an awaitable persisted snapshot on VisualPreferences using its existing load barrier, private disk and write mutex. Read through that injected owner in the tool adapter.
3. Preserve write commit/publication, schema, singleton and permission/privacy policy. Do not reset the singleton to conceal the regression.
4. Run the expanded app gate and unchanged language/interpretation regressions, build both APKs, then rerun appropriate native boundaries. Do not claim a physical phone preference-loss reproduction from a desktop namespace fixture.
