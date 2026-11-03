# Issue #58 — exact numeric tool-schema boundary (2026-11-02)

**Status:** code-only fix committed to `main`. Source inspection and diff readback only; no Gradle/JVM suite, Android APK build, ADB/device interaction, provider invocation or spoken command was run during this GitHub-connected pass.

## Defect

`ToolRegistry.validateSchema` previously coerced every `Number` and each numeric schema boundary to `Double` before checking integer constraints. A `Long` just over the `2^53` exact-double boundary could be rounded down and accepted as equal to a maximum; the inverse could occur at a minimum. Some tool schemas use a numeric revision or sequence precondition: the registry must not certify a stale or out-of-range exact value just because floating-point rounding erased one integer unit.

## Change

Both ordinary `ToolRegistry.validate` and saved-workflow `validateTemplate` now use `BigDecimal` derived from each finite `Number`'s string representation for exact range comparisons. The actual bound value is revalidated after a template's named inputs are supplied. Fractional input to an integer schema, non-finite values, unsupported numeric constraints and unsafe bounds are rejected. This change does not add a new tool, authorization policy or special workflow executor.

## Regression sources (NOT_RUN)

Five focused cases were added to `runtime/cognition/src/test/java/com/halo/runtime/cognition/tools/ToolTemplateValidationTest.kt`:

- Exact 2^53 maximum plus one, and 2^53+1 minimum minus one.
- Long.MAX_VALUE compared with an exact Long.MAX_VALUE-1 ceiling.
- Fractional, NaN and infinite numbers.
- Review-time named-input template accepted, but post-binding out-of-range revision rejected.

**Local verification:**

```bash
./gradlew :runtime:cognition:test --tests 'com.halo.runtime.cognition.tools.ToolTemplateValidationTest'
```

If the runtime module uses an Android/JVM variant task name, inspect Gradle tasks locally and run the corresponding focused test task. Follow with the #58 typed-workflow review/use and stale-source refusal tests. These authored cases are not evidence that those commands passed.
