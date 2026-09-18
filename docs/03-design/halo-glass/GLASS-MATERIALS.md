# Glass materials: implementation map

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

The exact values are defined in
[HaloGlassMaterial](../../../app/src/main/java/com/halo/app/ui/glass/material/HaloGlassMaterial.kt),
[colors](../../../app/src/main/java/com/halo/app/ui/glass/material/HaloGlassColors.kt) and
[tokens](../../../app/src/main/java/com/halo/app/ui/glass/material/HaloGlassTokens.kt).

HaloGlassMaterials defines Background, Standard, Elevated, Strong and Interactive materials.
Fields include container/border/content color, blurRadius, elevation and highlightBrush.
A configured blur radius does not mean a rendering component uses it.
HaloGlassSurface currently draws shadow/background/border/highlights without backdrop blur.

Use source values rather than duplicated numeric tables that drift. Future visual changes
must retain readable fallbacks and be measured on supported Android versions.
