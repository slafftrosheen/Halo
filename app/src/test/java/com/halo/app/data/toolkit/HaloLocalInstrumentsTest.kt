package com.halo.app.data.toolkit

import com.halo.app.di.tools.host.HaloLocalInstrumentTools
import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.ReplaySafety
import com.halo.runtime.cognition.tools.ToolFeatureOperability
import com.halo.runtime.cognition.tools.ToolOperabilityReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloLocalInstrumentsTest {
    @Test
    fun calculatorUsesRightAssociativePowersAndTracesActualSteps() {
        val calculation = HaloCalcEngine.calculate("-2^2 + 2^3^2")
        assertEquals("508", HaloCalcEngine.display(calculation.result))
        assertTrue("2 ^ 9 = 512" in calculation.steps)
        assertEquals("-4 + 512 = 508", calculation.steps.last())
        assertEquals("12.6", HaloCalcEngine.display(HaloCalcEngine.calculate("15% of 84").result))
        assertEquals("21", HaloCalcEngine.display(HaloCalcEngine.calculate("Раздели 63 на 3").result))
    }

    @Test
    fun plotLeavesUndefinedValuesAndCurrencyRequiresAReviewedRate() {
        val points = HaloCalcEngine.graph("1/x", -1.0, 1.0, 3)
        assertEquals(-1.0, requireNotNull(points.first().y), 0.0)
        assertNull(points[1].y)
        assertEquals(1.0, requireNotNull(points.last().y), 0.0)
        assertEquals("32", HaloCalcEngine.display(HaloCalcEngine.convert("0", CalcUnit.C, CalcUnit.F)))
        assertThrows(IllegalArgumentException::class.java) { HaloCalcEngine.convert("20", CalcUnit.EUR, CalcUnit.GBP) }
        assertEquals("16", HaloCalcEngine.display(HaloCalcEngine.convert("20", CalcUnit.EUR, CalcUnit.GBP, "0,8")))
    }

    @Test
    fun photoMeasurementAccountsForAspectRatioAndRejectsTinyReferences() {
        val marks = listOf(MeasurementPoint(0.0, 0.0), MeasurementPoint(.25, 0.0),
            MeasurementPoint(.2, 0.0), MeasurementPoint(.2, 1.0))
        assertEquals(20.0, HaloMeasurement.imageEstimate(200, 100, marks, 10.0), 0.0)
        assertThrows(IllegalArgumentException::class.java) { HaloMeasurement.imageEstimate(10.0, 3.0, 100.0) }
    }

    @Test
    fun roomOutlineClosesWhenTheMeasuredRectangleCloses() {
        val outline = HaloMeasurement.roomPoints(listOf(4.0, 3.0, 4.0, 3.0).map { RoomWall(it, false) })
        assertEquals(MeasurementPoint(4.0, 3.0), outline[2])
        assertEquals(outline.first(), outline.last())
    }

    @Test
    fun coordinatesAcceptDecimalCommasAndDistanceCrossesTheDateLine() {
        assertEquals(HaloMapCoordinate(56.95, 24.1), HaloMapsStore.coordinate("56,95; 24,10"))
        assertThrows(IllegalArgumentException::class.java) { HaloMapsStore.coordinate("91, 24") }
        val distance = HaloMapsStore.straightLineKm(HaloMapCoordinate(0.0, 179.9), HaloMapCoordinate(0.0, -179.9))
        assertEquals(22.239, distance, .001)
    }

    @Test
    fun arrivalMatchesExactPlaceEvidenceAndRejectsAmbiguousNames() {
        val place = HaloSavedPlace("saved-place", "1", "Café Rīts", HaloMapCoordinate(56.95, 24.1))
        assertTrue(HaloMapsStore.matchesPlace(place, "", listOf("saved-place"), false))
        assertTrue(HaloMapsStore.matchesPlace(place, "  CAFÉ   RĪTS ", emptyList(), true))
        assertTrue(HaloMapsStore.matchesPlace(place, "56.95, 24.1", emptyList(), false))
        assertTrue(!HaloMapsStore.matchesPlace(place, "Café Rīts", emptyList(), false))
        assertTrue(!HaloMapsStore.matchesPlace(place, "Near Café Rīts", emptyList(), true))
        assertTrue(!HaloMapsStore.matchesPlace(place, "56.96, 24.1", emptyList(), true))
    }

    @Test
    fun localToolsPublishTextAndVoiceContractsAndRequireConfirmationForSavingPlace() {
        val definitions = HaloLocalInstrumentTools().definitions()
        val descriptors = definitions.map { requireNotNull(HaloLocalInstrumentTools.descriptor(it)) }
        val feature = ToolFeatureOperability("halo-11-instruments", definitions.map { it.name }.toSet())
        assertEquals(10, feature.toolIds.size)
        assertEquals(emptyList<Any>(), ToolOperabilityReview.inspect(listOf(feature), descriptors))
        val keep = descriptors.single { it.name == "halo_maps_keep_place" }
        assertEquals(ConfirmationPolicy.ALWAYS, keep.security.confirmation)
        assertEquals(ReplaySafety.NEVER_REPLAY, keep.security.replaySafety)
    }
}
