package com.halo.app.ui.theme.v13

import androidx.annotation.RawRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.halo.app.R
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.LocalHaloDarkTheme
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.app.ui.theme.rememberVisualPreferences

internal enum class Halo13Motion(@RawRes val dark: Int, @RawRes val light: Int) {
    HELLO(R.raw.halo13_motion_hello_dark, R.raw.halo13_motion_hello_light),
    READY(R.raw.halo13_motion_ready_dark, R.raw.halo13_motion_ready_light),
    MOVE_PHONE(R.raw.halo13_motion_move_phone_dark, R.raw.halo13_motion_move_phone_light),
    HANDOVER(R.raw.halo13_motion_handover_dark, R.raw.halo13_motion_handover_light),
    HAND_BACK(R.raw.halo13_motion_hand_back_dark, R.raw.halo13_motion_hand_back_light),
    ASK_ACCESS(R.raw.halo13_motion_ask_access_dark, R.raw.halo13_motion_ask_access_light),
    LEVEL_SHIFT(R.raw.halo13_motion_level_shift_dark, R.raw.halo13_motion_level_shift_light),
    PAGE_ASSEMBLES(R.raw.halo13_motion_page_assembles_dark, R.raw.halo13_motion_page_assembles_light),
    SKY_CYCLE(R.raw.halo13_motion_sky_cycle_dark, R.raw.halo13_motion_sky_cycle_light),
    TINT_FROM_WALLPAPER(R.raw.halo13_motion_tint_from_wallpaper_dark, R.raw.halo13_motion_tint_from_wallpaper_light),
    SOURCE_REFRESH(R.raw.halo13_motion_source_refresh_dark, R.raw.halo13_motion_source_refresh_light),
    ADD_SOURCE(R.raw.halo13_motion_add_source_dark, R.raw.halo13_motion_add_source_light),
    FIND_TIME(R.raw.halo13_motion_find_time_dark, R.raw.halo13_motion_find_time_light),
    TIMELINE_DRAG(R.raw.halo13_motion_timeline_drag_dark, R.raw.halo13_motion_timeline_drag_light),
    LEAVE_BY(R.raw.halo13_motion_leave_by_dark, R.raw.halo13_motion_leave_by_light),
    TIDY_UP(R.raw.halo13_motion_tidy_up_dark, R.raw.halo13_motion_tidy_up_light),
    JOIN_SHAPES(R.raw.halo13_motion_join_shapes_dark, R.raw.halo13_motion_join_shapes_light),
    SHEET_NEXT(R.raw.halo13_motion_sheet_next_dark, R.raw.halo13_motion_sheet_next_light),
    FACE_TO_FACE(R.raw.halo13_motion_face_to_face_dark, R.raw.halo13_motion_face_to_face_light),
    SCAN_EDGES(R.raw.halo13_motion_scan_edges_dark, R.raw.halo13_motion_scan_edges_light),
    EXPLAIN_BLOOM(R.raw.halo13_motion_explain_bloom_dark, R.raw.halo13_motion_explain_bloom_light),
    HELP_LADDER(R.raw.halo13_motion_help_ladder_dark, R.raw.halo13_motion_help_ladder_light),
    CARD_FLIP(R.raw.halo13_motion_card_flip_dark, R.raw.halo13_motion_card_flip_light),
    EXAM_PLAN(R.raw.halo13_motion_exam_plan_dark, R.raw.halo13_motion_exam_plan_light),
    KIDS_ADVENTURE(R.raw.halo13_motion_kids_adventure_dark, R.raw.halo13_motion_kids_adventure_light),
}

@Composable
internal fun Halo13Animation(motion: Halo13Motion, modifier: Modifier = Modifier, loop: Boolean = false) {
    val dark = LocalHaloDarkTheme.current ?: isSystemInDarkTheme()
    val preference = rememberVisualPreferences().motionLevel
    val still = rememberReducedMotion().value
    val resource = if (dark) motion.dark else motion.light
    key(resource) {
        val composition = rememberLottieComposition(LottieCompositionSpec.RawRes(resource)).value
        if (still) {
            LottieAnimation(composition = composition, progress = { 1f }, modifier = modifier)
        } else {
            val progress = animateLottieCompositionAsState(
                composition,
                iterations = if (loop && preference != HaloLookMotion.GENTLE) Int.MAX_VALUE else 1,
            )
            LottieAnimation(composition = composition, progress = { progress.value }, modifier = modifier)
        }
    }
}
