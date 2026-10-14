package com.halo.app.ui.theme

import androidx.compose.ui.graphics.Color

// Exact appearance values supplied in BrandAssets/theme/halo_themes.json.
internal data class BrandSurfaces(val background: Color, val surface: Color, val raised: Color, val text: Color, val muted: Color)
internal data class BrandAccent(val fill: Color, val ink: Color, val onFill: Color)

internal fun brandSurfaces(dark: Boolean, tone: HaloTone): BrandSurfaces = when {
    true == dark && tone == HaloTone.NATURAL -> BrandSurfaces(Color(0xFF060330), Color(0xFF110E45), Color(0xFF1A1760), Color(0xFFFAFAF4), Color(0xFFB7B9D3))
    true == dark && tone == HaloTone.WARM -> BrandSurfaces(Color(0xFF160E1C), Color(0xFF22182A), Color(0xFF2E2238), Color(0xFFFBF6F0), Color(0xFFC9BCC4))
    true == dark && tone == HaloTone.COOL -> BrandSurfaces(Color(0xFF06142A), Color(0xFF0E2038), Color(0xFF172C48), Color(0xFFF4F8FC), Color(0xFFB3C2D4))
    false == dark && tone == HaloTone.NATURAL -> BrandSurfaces(Color(0xFFFAFAF4), Color(0xFFFFFFFF), Color(0xFFEFF0E8), Color(0xFF060330), Color(0xFF4A4F63))
    false == dark && tone == HaloTone.WARM -> BrandSurfaces(Color(0xFFFFF5EC), Color(0xFFFFFFFF), Color(0xFFF7E9DD), Color(0xFF1A0F14), Color(0xFF5E4F55))
    false == dark && tone == HaloTone.COOL -> BrandSurfaces(Color(0xFFF3F6FA), Color(0xFFFFFFFF), Color(0xFFE5ECF4), Color(0xFF06142A), Color(0xFF46566B))
    else -> error("Unknown brand tone")
}

internal fun brandAccent(dark: Boolean, accent: HaloAccent): BrandAccent = when {
    true == dark && accent == HaloAccent.MINT -> BrandAccent(Color(0xFF0CF2BA), Color(0xFF0CF2BA), Color(0xFF060330))
    false == dark && accent == HaloAccent.MINT -> BrandAccent(Color(0xFF0CF2BA), Color(0xFF047A5E), Color(0xFF060330))
    true == dark && accent == HaloAccent.BLUE -> BrandAccent(Color(0xFF8EC5FF), Color(0xFF8EC5FF), Color(0xFF06142A))
    false == dark && accent == HaloAccent.BLUE -> BrandAccent(Color(0xFF8EC5FF), Color(0xFF1F5FBF), Color(0xFF06142A))
    true == dark && accent == HaloAccent.LILAC -> BrandAccent(Color(0xFFC9B8FF), Color(0xFFC9B8FF), Color(0xFF160E3A))
    false == dark && accent == HaloAccent.LILAC -> BrandAccent(Color(0xFFC9B8FF), Color(0xFF6446D6), Color(0xFF160E3A))
    true == dark && accent == HaloAccent.CORAL -> BrandAccent(Color(0xFFFF9EB3), Color(0xFFFF9EB3), Color(0xFF2A0A14))
    false == dark && accent == HaloAccent.CORAL -> BrandAccent(Color(0xFFFF9EB3), Color(0xFFB8325A), Color(0xFF2A0A14))
    else -> error("Unknown brand accent")
}
