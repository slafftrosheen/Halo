package com.halo.app.study

/** Existing lesson/recovery storage joins deletion; it does not own Study's database. */
interface StudySessionLocalData {
    suspend fun version(sessionId: String): String

    /** Hold the local writer guard while the repository validates and deletes its records. */
    suspend fun deleteWith(
        sessionId: String,
        expectedVersion: String?,
        deleteRecords: suspend (clearLocalData: () -> Unit) -> Boolean,
    ): Boolean

    companion object { const val EMPTY_VERSION = "study-local:none" }
}
