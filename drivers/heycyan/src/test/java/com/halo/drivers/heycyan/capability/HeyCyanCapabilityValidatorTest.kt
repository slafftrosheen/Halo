package com.halo.drivers.heycyan.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanCapabilityValidatorTest {

    private val registry = HeyCyanCapabilityRegistry()
    private val validator = HeyCyanCapabilityValidator()

    @Test
    fun completeSurfaceValidationPassesWithNoErrors() {
        val errors = validator.validate(registry.all())
        assertTrue("Complete registry must pass validation without errors: $errors", errors.isEmpty())
    }

    @Test
    fun detectsDuplicateCapabilityIds() {
        val duplicateList = registry.all() + registry.all().first()
        val errors = validator.validate(duplicateList)
        assertEquals(1, errors.size)
        assertTrue(errors.first().contains("Duplicate capability IDs"))
    }

    @Test
    fun detectsMissingCapabilities() {
        val incompleteList = registry.all().drop(2)
        val errors = validator.validate(incompleteList)
        assertTrue(errors.any { it.contains("Missing expected source capabilities") })
    }
}
