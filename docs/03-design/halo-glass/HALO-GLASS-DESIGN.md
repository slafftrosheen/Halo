# Halo Glass: current implementation and redesign bridge

Status: CURRENT SOURCE MAP / SUPERSEDED DESIGN TARGET. Updated 2026-09-18.

The user's [Halo cognitive workspace mockup and design guide](../HALO-WORKSPACE-DESIGN.md)
now guide the redesign. It introduces light and dark themes, a spatial translucent-cell
presence and a five-destination navigation target including Notebook.
Earlier dark-only, mint-only and polygon-orb-only rules are superseded.

Current code still uses shared glass components plus older component/theme families.
HaloGlassSurface draws shadow, background, border and highlights; its implementation
does not apply the material blurRadius as backdrop blur. Material3 primitives are used.

HaloStateOrb currently renders polygon shells, glyphs, pixel rings and gradients with
infinite transitions. Its palette includes cyan/purple/pink states and its input is a
state enum. Measured audio/progress binding and explicit reduced-motion control are not
present in this renderer. The spatial-cell presence is planned, not implemented.

Current source: [components](../../../app/src/main/java/com/halo/app/ui/glass/components),
[materials](../../../app/src/main/java/com/halo/app/ui/glass/material),
[route inventory](../../11-ui/UI-IMPLEMENTATION-STATUS.md).
Exact APIs and values live in source; accessibility and performance are acceptance targets,
not certified properties.
