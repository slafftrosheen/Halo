package com.halo.drivers.heycyan.capability

import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanController
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanExecutionResult
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation

class HeyCyanCapabilityControllerAdapter(
    private val capabilityController: HeyCyanCapabilityController
) : HeyCyanController {
    override suspend fun execute(operation: HeyCyanOperation): Result<HeyCyanExecutionResult> {
        val result = capabilityController.execute(operation)
        return result.map { HeyCyanExecutionResult.Acknowledged }
    }
}
