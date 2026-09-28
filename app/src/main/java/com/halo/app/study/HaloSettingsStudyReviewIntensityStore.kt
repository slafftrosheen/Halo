package com.halo.app.study

import com.halo.app.data.settings.HaloSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed review intensity.
 *
 * This adapter is the only Android-aware part of the preference: scheduling, mastery and the UI all
 * talk to [StudyReviewIntensityStore], so both can be exercised in a plain unit test.
 */
class HaloSettingsStudyReviewIntensityStore(
    private val settings: HaloSettingsRepository,
) : StudyReviewIntensityStore {

    override val stored: Flow<StudyReviewIntensity> = settings.settings
        .map { values -> StudyReviewIntensity.fromName(values.studyReviewIntensity) }

    override suspend fun store(value: StudyReviewIntensity) {
        settings.setStudyReviewIntensity(value.name)
    }
}
