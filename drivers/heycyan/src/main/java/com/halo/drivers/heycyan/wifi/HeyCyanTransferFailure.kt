package com.halo.drivers.heycyan.wifi

/**
 * Structured error model for HeyCyan Wi-Fi media transfer operations.
 * 
 * Provides rich error classification for recovery and diagnostic logging.
 */
sealed interface HeyCyanTransferFailure {

    data object CredentialsUnavailable : HeyCyanTransferFailure

    data object TransferModeFailed : HeyCyanTransferFailure

    data class WifiConnectionFailed(
        val cause: Throwable?,
    ) : HeyCyanTransferFailure

    data object DeviceAddressUnavailable : HeyCyanTransferFailure

    data class HttpFailure(
        val statusCode: Int?,
        val message: String?,
    ) : HeyCyanTransferFailure

    data class ManifestParseFailure(
        val message: String,
    ) : HeyCyanTransferFailure

    data class DownloadFailure(
        val mediaId: String,
        val message: String,
    ) : HeyCyanTransferFailure

    data class IntegrityFailure(
        val mediaId: String,
        val expected: Long?,
        val actual: Long,
    ) : HeyCyanTransferFailure
}
