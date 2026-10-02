package com.halo.host.android.appfunctions

import androidx.appfunctions.*
import com.halo.runtime.cognition.apps.*
import org.junit.Assert.*
import org.junit.Test

class AppFunctionErrorMappingTest {
    @Test fun errorsUseHaloVocabularyWithoutVendorMessages() {
        assertEquals(SemanticOperationFailure.CALLER_NOT_AUTHORIZED, AndroidAppFunctionConsumer.mapError(SecurityException("private content")))
        assertEquals(SemanticOperationFailure.INVALID_ARGUMENTS, AndroidAppFunctionConsumer.mapError(IllegalArgumentException("private arguments")))
        assertEquals(SemanticOperationFailure.PLATFORM_ERROR, AndroidAppFunctionConsumer.mapError(RuntimeException("private details")))
    }
}
