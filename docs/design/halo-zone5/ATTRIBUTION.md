# Provenance and third-party notices

## Halo-specific assets and documentation

The semantic icon family, new state animations, static motion artwork, tokens, component contracts, reference presentation logic and implementation documents were created for this Halo Zone 5.0 handoff. No third-party icon collection is embedded. The project owner should apply the project's chosen distribution terms to these new materials.

The three approved mockups in `reference/mockups/` were generated in this collaboration and are included as visual references at the user's request. They contain illustrative content and do not prove runtime capability or exact layout dimensions.

Source inventories and excerpts refer to the user's Halo repository at commit `4a227e33eb8e6812ad30481b36e0ceb0f17f4cbd`. Repository code retains its existing ownership and licensing. No transfer of third-party or repository ownership is implied by this pack.

## Fonts

The exact existing Halo font binaries are included, with provenance hashes in `fonts/provenance.json`:

- Sora Variable — `fonts/sora_variable.ttf`; license `fonts/OFL-Sora.txt`.
- Atkinson Hyperlegible Next Variable, regular and italic — respective `.ttf` files; license `fonts/OFL-Atkinson-Hyperlegible-Next.txt`.
- Onest Cyrillic Variable — `fonts/onest_cyrillic_variable.ttf`; license `fonts/OFL-Onest.txt`.

These fonts use the SIL Open Font License. Retain the supplied license texts with redistribution and follow their reserved-name and modification terms. The fonts are not newly authored assets in this pack.

## Offline motion player

`preview/vendor/lottie_light-5.13.0.min.js` is the official lottie-web light player from the Airbnb lottie-web repository, release tag `v5.13.0`. It is included for the offline review catalog, under the MIT license in `preview/vendor/LICENSE-lottie-web.md`.

Source: https://github.com/airbnb/lottie-web/tree/v5.13.0

Git blob identifiers: player `0e5a5691c76b06afc08d0cd480683915d864d5ec`; license `9c1cd7cb84357f4aa3d57ff961cd37d993055ef5`.

The Android app should continue to use its existing native Lottie dependency. The browser player is not intended to be shipped inside the Android host.

## Validation dependencies

Python/Pillow/fontTools, Node, jsdom and sharp were used as build/validation tools. Their installed packages are not vendored into this ZIP. Keep any dependency notices required by your own build distribution. The package includes authored scripts and their outputs, not a `node_modules` directory.

## Primary implementation references

- Android VectorDrawable resources: https://developer.android.com/develop/ui/views/graphics/vector-drawable-resources
- Compose accessibility defaults and touch targets: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Lottie documentation: https://lottie.airbnb.tech/
- Lottie shape format: https://lottiefiles.github.io/lottie-docs/shapes/

These links support format and platform guidance. Halo-specific behavior and scope come from the pinned source and the user's accepted design decisions.
