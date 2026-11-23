package com.halo.sdk.runtime.ink

enum class InkBrushBlend(val wire: String) {
    NORMAL("n"), MULTIPLY("m"), SCREEN("s"), COLOR("c"),
}

/** Width is one-millionth of the document width. Opacity and flow are thousandths. */
data class InkBrushStyle(
    val id: String,
    val version: Int,
    val argb: Int,
    val size: Int,
    val opacity: Int = 1000,
    val flow: Int = 1000,
    val blend: InkBrushBlend = InkBrushBlend.NORMAL,
    val seed: Int = 0,
) {
    init {
        require(id.matches(ID) && version in 1..1000)
        require(size in 1..250_000 && opacity in 0..1000 && flow in 0..1000)
    }

    internal fun encode(): String = "$id,$version,$argb,$size,$opacity,$flow," + blend.wire + ",$seed"

    companion object {
        private val ID = Regex("[a-z0-9.-]{1,80}")

        internal fun decode(value: String): InkBrushStyle? {
            val fields = value.split(',', limit = 9)
            if (fields.size != 8) return null
            return InkBrushStyle(
                fields[0], fields[1].toIntOrNull() ?: return null, fields[2].toIntOrNull() ?: return null,
                fields[3].toIntOrNull() ?: return null, fields[4].toIntOrNull() ?: return null,
                fields[5].toIntOrNull() ?: return null, InkBrushBlend.entries.firstOrNull { it.wire == fields[6] }
                    ?: return null, fields[7].toIntOrNull() ?: return null,
            )
        }
    }
}
