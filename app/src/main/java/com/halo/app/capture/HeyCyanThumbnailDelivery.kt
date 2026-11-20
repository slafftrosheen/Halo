package com.halo.app.capture

import com.halo.drivers.heycyan.thumbnail.HeyCyanCapturedThumbnail
import kotlinx.coroutines.CancellationException

/** Transfer success is not review acceptance. Keep rejection inside the operation's Result. */
internal suspend fun deliverHeyCyanThumbnail(
    transfer: Result<HeyCyanCapturedThumbnail>,
    receive: suspend (HeyCyanCapturedThumbnail) -> Unit,
): Result<HeyCyanCapturedThumbnail> = try {
    val thumbnail = transfer.getOrThrow()
    receive(thumbnail)
    Result.success(thumbnail)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
