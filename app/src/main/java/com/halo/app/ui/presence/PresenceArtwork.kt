package com.halo.app.ui.presence

import com.halo.app.R

enum class PresenceArtwork(val key: String) {
    ACTING("acting"),
    ACTING_PHONE("acting_phone"),
    APPROVAL_REQUIRED("approval_required"),
    CAPTURING_VISION("capturing_vision"),
    CAPTURING_VOICE("capturing_voice"),
    ERROR("error"),
    IDLE("idle"),
    LISTENING("listening"),
    MODEL_LOADING("model_loading"),
    OFFLINE("offline"),
    PERMISSION_MISSING("permission_missing"),
    PLANNING("planning"),
    PREPARING_CONTEXT("preparing_context"),
    RECOVERING("recovering"),
    SPEAKING("speaking"),
    STOPPED("stopped"),
    STREAMING_TEXT("streaming_text"),
    SUCCESS("success"),
    THINKING("thinking"),
    WAITING("waiting"),
    WARNING("warning"),
}

internal fun PresenceArtwork.staticResource(dark: Boolean, kids: Boolean, accent: Int): Int {
    val variant = if (kids) 8 + (if (dark) 0 else 1) else accent * 2 + (if (dark) 0 else 1)
    return when (this) {
        PresenceArtwork.ACTING -> intArrayOf(R.drawable.halo_presence_standard_acting_dark_mint, R.drawable.halo_presence_standard_acting_light_mint, R.drawable.halo_presence_standard_acting_dark_blue, R.drawable.halo_presence_standard_acting_light_blue, R.drawable.halo_presence_standard_acting_dark_lilac, R.drawable.halo_presence_standard_acting_light_lilac, R.drawable.halo_presence_standard_acting_dark_coral, R.drawable.halo_presence_standard_acting_light_coral, R.drawable.halo_presence_kids_acting_dark_mint, R.drawable.halo_presence_kids_acting_light_mint)[variant]
        PresenceArtwork.ACTING_PHONE -> intArrayOf(R.drawable.halo_presence_standard_acting_phone_dark_mint, R.drawable.halo_presence_standard_acting_phone_light_mint, R.drawable.halo_presence_standard_acting_phone_dark_blue, R.drawable.halo_presence_standard_acting_phone_light_blue, R.drawable.halo_presence_standard_acting_phone_dark_lilac, R.drawable.halo_presence_standard_acting_phone_light_lilac, R.drawable.halo_presence_standard_acting_phone_dark_coral, R.drawable.halo_presence_standard_acting_phone_light_coral, R.drawable.halo_presence_kids_acting_phone_dark_mint, R.drawable.halo_presence_kids_acting_phone_light_mint)[variant]
        PresenceArtwork.APPROVAL_REQUIRED -> intArrayOf(R.drawable.halo_presence_standard_approval_required_dark_mint, R.drawable.halo_presence_standard_approval_required_light_mint, R.drawable.halo_presence_standard_approval_required_dark_blue, R.drawable.halo_presence_standard_approval_required_light_blue, R.drawable.halo_presence_standard_approval_required_dark_lilac, R.drawable.halo_presence_standard_approval_required_light_lilac, R.drawable.halo_presence_standard_approval_required_dark_coral, R.drawable.halo_presence_standard_approval_required_light_coral, R.drawable.halo_presence_kids_approval_required_dark_mint, R.drawable.halo_presence_kids_approval_required_light_mint)[variant]
        PresenceArtwork.CAPTURING_VISION -> intArrayOf(R.drawable.halo_presence_standard_capturing_vision_dark_mint, R.drawable.halo_presence_standard_capturing_vision_light_mint, R.drawable.halo_presence_standard_capturing_vision_dark_blue, R.drawable.halo_presence_standard_capturing_vision_light_blue, R.drawable.halo_presence_standard_capturing_vision_dark_lilac, R.drawable.halo_presence_standard_capturing_vision_light_lilac, R.drawable.halo_presence_standard_capturing_vision_dark_coral, R.drawable.halo_presence_standard_capturing_vision_light_coral, R.drawable.halo_presence_kids_capturing_vision_dark_mint, R.drawable.halo_presence_kids_capturing_vision_light_mint)[variant]
        PresenceArtwork.CAPTURING_VOICE -> intArrayOf(R.drawable.halo_presence_standard_capturing_voice_dark_mint, R.drawable.halo_presence_standard_capturing_voice_light_mint, R.drawable.halo_presence_standard_capturing_voice_dark_blue, R.drawable.halo_presence_standard_capturing_voice_light_blue, R.drawable.halo_presence_standard_capturing_voice_dark_lilac, R.drawable.halo_presence_standard_capturing_voice_light_lilac, R.drawable.halo_presence_standard_capturing_voice_dark_coral, R.drawable.halo_presence_standard_capturing_voice_light_coral, R.drawable.halo_presence_kids_capturing_voice_dark_mint, R.drawable.halo_presence_kids_capturing_voice_light_mint)[variant]
        PresenceArtwork.ERROR -> intArrayOf(R.drawable.halo_presence_standard_error_dark_mint, R.drawable.halo_presence_standard_error_light_mint, R.drawable.halo_presence_standard_error_dark_blue, R.drawable.halo_presence_standard_error_light_blue, R.drawable.halo_presence_standard_error_dark_lilac, R.drawable.halo_presence_standard_error_light_lilac, R.drawable.halo_presence_standard_error_dark_coral, R.drawable.halo_presence_standard_error_light_coral, R.drawable.halo_presence_kids_error_dark_mint, R.drawable.halo_presence_kids_error_light_mint)[variant]
        PresenceArtwork.IDLE -> intArrayOf(R.drawable.halo_presence_standard_idle_dark_mint, R.drawable.halo_presence_standard_idle_light_mint, R.drawable.halo_presence_standard_idle_dark_blue, R.drawable.halo_presence_standard_idle_light_blue, R.drawable.halo_presence_standard_idle_dark_lilac, R.drawable.halo_presence_standard_idle_light_lilac, R.drawable.halo_presence_standard_idle_dark_coral, R.drawable.halo_presence_standard_idle_light_coral, R.drawable.halo_presence_kids_idle_dark_mint, R.drawable.halo_presence_kids_idle_light_mint)[variant]
        PresenceArtwork.LISTENING -> intArrayOf(R.drawable.halo_presence_standard_listening_dark_mint, R.drawable.halo_presence_standard_listening_light_mint, R.drawable.halo_presence_standard_listening_dark_blue, R.drawable.halo_presence_standard_listening_light_blue, R.drawable.halo_presence_standard_listening_dark_lilac, R.drawable.halo_presence_standard_listening_light_lilac, R.drawable.halo_presence_standard_listening_dark_coral, R.drawable.halo_presence_standard_listening_light_coral, R.drawable.halo_presence_kids_listening_dark_mint, R.drawable.halo_presence_kids_listening_light_mint)[variant]
        PresenceArtwork.MODEL_LOADING -> intArrayOf(R.drawable.halo_presence_standard_model_loading_dark_mint, R.drawable.halo_presence_standard_model_loading_light_mint, R.drawable.halo_presence_standard_model_loading_dark_blue, R.drawable.halo_presence_standard_model_loading_light_blue, R.drawable.halo_presence_standard_model_loading_dark_lilac, R.drawable.halo_presence_standard_model_loading_light_lilac, R.drawable.halo_presence_standard_model_loading_dark_coral, R.drawable.halo_presence_standard_model_loading_light_coral, R.drawable.halo_presence_kids_model_loading_dark_mint, R.drawable.halo_presence_kids_model_loading_light_mint)[variant]
        PresenceArtwork.OFFLINE -> intArrayOf(R.drawable.halo_presence_standard_offline_dark_mint, R.drawable.halo_presence_standard_offline_light_mint, R.drawable.halo_presence_standard_offline_dark_blue, R.drawable.halo_presence_standard_offline_light_blue, R.drawable.halo_presence_standard_offline_dark_lilac, R.drawable.halo_presence_standard_offline_light_lilac, R.drawable.halo_presence_standard_offline_dark_coral, R.drawable.halo_presence_standard_offline_light_coral, R.drawable.halo_presence_kids_offline_dark_mint, R.drawable.halo_presence_kids_offline_light_mint)[variant]
        PresenceArtwork.PERMISSION_MISSING -> intArrayOf(R.drawable.halo_presence_standard_permission_missing_dark_mint, R.drawable.halo_presence_standard_permission_missing_light_mint, R.drawable.halo_presence_standard_permission_missing_dark_blue, R.drawable.halo_presence_standard_permission_missing_light_blue, R.drawable.halo_presence_standard_permission_missing_dark_lilac, R.drawable.halo_presence_standard_permission_missing_light_lilac, R.drawable.halo_presence_standard_permission_missing_dark_coral, R.drawable.halo_presence_standard_permission_missing_light_coral, R.drawable.halo_presence_kids_permission_missing_dark_mint, R.drawable.halo_presence_kids_permission_missing_light_mint)[variant]
        PresenceArtwork.PLANNING -> intArrayOf(R.drawable.halo_presence_standard_planning_dark_mint, R.drawable.halo_presence_standard_planning_light_mint, R.drawable.halo_presence_standard_planning_dark_blue, R.drawable.halo_presence_standard_planning_light_blue, R.drawable.halo_presence_standard_planning_dark_lilac, R.drawable.halo_presence_standard_planning_light_lilac, R.drawable.halo_presence_standard_planning_dark_coral, R.drawable.halo_presence_standard_planning_light_coral, R.drawable.halo_presence_kids_planning_dark_mint, R.drawable.halo_presence_kids_planning_light_mint)[variant]
        PresenceArtwork.PREPARING_CONTEXT -> intArrayOf(R.drawable.halo_presence_standard_preparing_context_dark_mint, R.drawable.halo_presence_standard_preparing_context_light_mint, R.drawable.halo_presence_standard_preparing_context_dark_blue, R.drawable.halo_presence_standard_preparing_context_light_blue, R.drawable.halo_presence_standard_preparing_context_dark_lilac, R.drawable.halo_presence_standard_preparing_context_light_lilac, R.drawable.halo_presence_standard_preparing_context_dark_coral, R.drawable.halo_presence_standard_preparing_context_light_coral, R.drawable.halo_presence_kids_preparing_context_dark_mint, R.drawable.halo_presence_kids_preparing_context_light_mint)[variant]
        PresenceArtwork.RECOVERING -> intArrayOf(R.drawable.halo_presence_standard_recovering_dark_mint, R.drawable.halo_presence_standard_recovering_light_mint, R.drawable.halo_presence_standard_recovering_dark_blue, R.drawable.halo_presence_standard_recovering_light_blue, R.drawable.halo_presence_standard_recovering_dark_lilac, R.drawable.halo_presence_standard_recovering_light_lilac, R.drawable.halo_presence_standard_recovering_dark_coral, R.drawable.halo_presence_standard_recovering_light_coral, R.drawable.halo_presence_kids_recovering_dark_mint, R.drawable.halo_presence_kids_recovering_light_mint)[variant]
        PresenceArtwork.SPEAKING -> intArrayOf(R.drawable.halo_presence_standard_speaking_dark_mint, R.drawable.halo_presence_standard_speaking_light_mint, R.drawable.halo_presence_standard_speaking_dark_blue, R.drawable.halo_presence_standard_speaking_light_blue, R.drawable.halo_presence_standard_speaking_dark_lilac, R.drawable.halo_presence_standard_speaking_light_lilac, R.drawable.halo_presence_standard_speaking_dark_coral, R.drawable.halo_presence_standard_speaking_light_coral, R.drawable.halo_presence_kids_speaking_dark_mint, R.drawable.halo_presence_kids_speaking_light_mint)[variant]
        PresenceArtwork.STOPPED -> intArrayOf(R.drawable.halo_presence_standard_stopped_dark_mint, R.drawable.halo_presence_standard_stopped_light_mint, R.drawable.halo_presence_standard_stopped_dark_blue, R.drawable.halo_presence_standard_stopped_light_blue, R.drawable.halo_presence_standard_stopped_dark_lilac, R.drawable.halo_presence_standard_stopped_light_lilac, R.drawable.halo_presence_standard_stopped_dark_coral, R.drawable.halo_presence_standard_stopped_light_coral, R.drawable.halo_presence_kids_stopped_dark_mint, R.drawable.halo_presence_kids_stopped_light_mint)[variant]
        PresenceArtwork.STREAMING_TEXT -> intArrayOf(R.drawable.halo_presence_standard_streaming_text_dark_mint, R.drawable.halo_presence_standard_streaming_text_light_mint, R.drawable.halo_presence_standard_streaming_text_dark_blue, R.drawable.halo_presence_standard_streaming_text_light_blue, R.drawable.halo_presence_standard_streaming_text_dark_lilac, R.drawable.halo_presence_standard_streaming_text_light_lilac, R.drawable.halo_presence_standard_streaming_text_dark_coral, R.drawable.halo_presence_standard_streaming_text_light_coral, R.drawable.halo_presence_kids_streaming_text_dark_mint, R.drawable.halo_presence_kids_streaming_text_light_mint)[variant]
        PresenceArtwork.SUCCESS -> intArrayOf(R.drawable.halo_presence_standard_success_dark_mint, R.drawable.halo_presence_standard_success_light_mint, R.drawable.halo_presence_standard_success_dark_blue, R.drawable.halo_presence_standard_success_light_blue, R.drawable.halo_presence_standard_success_dark_lilac, R.drawable.halo_presence_standard_success_light_lilac, R.drawable.halo_presence_standard_success_dark_coral, R.drawable.halo_presence_standard_success_light_coral, R.drawable.halo_presence_kids_success_dark_mint, R.drawable.halo_presence_kids_success_light_mint)[variant]
        PresenceArtwork.THINKING -> intArrayOf(R.drawable.halo_presence_standard_thinking_dark_mint, R.drawable.halo_presence_standard_thinking_light_mint, R.drawable.halo_presence_standard_thinking_dark_blue, R.drawable.halo_presence_standard_thinking_light_blue, R.drawable.halo_presence_standard_thinking_dark_lilac, R.drawable.halo_presence_standard_thinking_light_lilac, R.drawable.halo_presence_standard_thinking_dark_coral, R.drawable.halo_presence_standard_thinking_light_coral, R.drawable.halo_presence_kids_thinking_dark_mint, R.drawable.halo_presence_kids_thinking_light_mint)[variant]
        PresenceArtwork.WAITING -> intArrayOf(R.drawable.halo_presence_standard_waiting_dark_mint, R.drawable.halo_presence_standard_waiting_light_mint, R.drawable.halo_presence_standard_waiting_dark_blue, R.drawable.halo_presence_standard_waiting_light_blue, R.drawable.halo_presence_standard_waiting_dark_lilac, R.drawable.halo_presence_standard_waiting_light_lilac, R.drawable.halo_presence_standard_waiting_dark_coral, R.drawable.halo_presence_standard_waiting_light_coral, R.drawable.halo_presence_kids_waiting_dark_mint, R.drawable.halo_presence_kids_waiting_light_mint)[variant]
        PresenceArtwork.WARNING -> intArrayOf(R.drawable.halo_presence_standard_warning_dark_mint, R.drawable.halo_presence_standard_warning_light_mint, R.drawable.halo_presence_standard_warning_dark_blue, R.drawable.halo_presence_standard_warning_light_blue, R.drawable.halo_presence_standard_warning_dark_lilac, R.drawable.halo_presence_standard_warning_light_lilac, R.drawable.halo_presence_standard_warning_dark_coral, R.drawable.halo_presence_standard_warning_light_coral, R.drawable.halo_presence_kids_warning_dark_mint, R.drawable.halo_presence_kids_warning_light_mint)[variant]
    }
}

internal fun PresenceArtwork.microResource(): Int = when (this) {
    PresenceArtwork.ACTING -> R.drawable.halo_orb_micro_acting
    PresenceArtwork.ACTING_PHONE -> R.drawable.halo_orb_micro_acting_phone
    PresenceArtwork.APPROVAL_REQUIRED -> R.drawable.halo_orb_micro_approval_required
    PresenceArtwork.CAPTURING_VISION -> R.drawable.halo_orb_micro_capturing_vision
    PresenceArtwork.CAPTURING_VOICE -> R.drawable.halo_orb_micro_capturing_voice
    PresenceArtwork.ERROR -> R.drawable.halo_orb_micro_error
    PresenceArtwork.IDLE -> R.drawable.halo_orb_micro_idle
    PresenceArtwork.LISTENING -> R.drawable.halo_orb_micro_listening
    PresenceArtwork.MODEL_LOADING -> R.drawable.halo_orb_micro_model_loading
    PresenceArtwork.OFFLINE -> R.drawable.halo_orb_micro_offline
    PresenceArtwork.PERMISSION_MISSING -> R.drawable.halo_orb_micro_permission_missing
    PresenceArtwork.PLANNING -> R.drawable.halo_orb_micro_planning
    PresenceArtwork.PREPARING_CONTEXT -> R.drawable.halo_orb_micro_preparing_context
    PresenceArtwork.RECOVERING -> R.drawable.halo_orb_micro_recovering
    PresenceArtwork.SPEAKING -> R.drawable.halo_orb_micro_speaking
    PresenceArtwork.STOPPED -> R.drawable.halo_orb_micro_stopped
    PresenceArtwork.STREAMING_TEXT -> R.drawable.halo_orb_micro_streaming_text
    PresenceArtwork.SUCCESS -> R.drawable.halo_orb_micro_success
    PresenceArtwork.THINKING -> R.drawable.halo_orb_micro_thinking
    PresenceArtwork.WAITING -> R.drawable.halo_orb_micro_waiting
    PresenceArtwork.WARNING -> R.drawable.halo_orb_micro_warning
}
