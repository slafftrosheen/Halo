package com.halo.app.everyday.collectors

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArticlePictureSettings(context: Context) {
    private val disk = context.getSharedPreferences("halo_article_pictures", Context.MODE_PRIVATE)
    private val visibleState = MutableStateFlow(disk.getBoolean("visible", true))
    val visible = visibleState.asStateFlow()

    fun setVisible(value: Boolean) {
        disk.edit().putBoolean("visible", value).apply()
        visibleState.value = value
    }
}
