package com.halo.host.android.resolver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ContactResolveResult {
    data class Found(val name: String, val number: String) : ContactResolveResult
    data object NotFound : ContactResolveResult

    /**
     * AGENTIC-001: an ambiguous lookup must hand the model the choices it needs to
     * ask a disambiguating question. Names only: numbers are not needed to clarify,
     * and contact data should not be spread further than the action requires.
     */
    data class MultipleMatches(val candidateNames: List<String> = emptyList()) : ContactResolveResult
    data object NoPhoneNumber : ContactResolveResult
}

class AndroidContactResolver(private val context: Context) {

    suspend fun resolve(query: String): ContactResolveResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext ContactResolveResult.NotFound

        // If query is already a phone number (e.g., +37120000000 or digits)
        if (trimmed.startsWith("+") || trimmed.all { it.isDigit() || it == '-' || it == ' ' || it == '(' || it == ')' }) {
            return@withContext ContactResolveResult.Found(trimmed, trimmed)
        }

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext ContactResolveResult.NotFound
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$trimmed%")

        val matches = mutableListOf<Pair<String, String>>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else ""
                    val number = if (numberIdx >= 0) it.getString(numberIdx) else ""
                    if (number.isNotBlank()) {
                        matches.add(name to number)
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext ContactResolveResult.NotFound
        }

        when {
            matches.isEmpty() -> ContactResolveResult.NotFound
            matches.size == 1 -> ContactResolveResult.Found(matches[0].first, matches[0].second)
            else -> {
                val distinctNames = matches.map { it.first }.distinct()
                if (distinctNames.size == 1) {
                    ContactResolveResult.Found(matches[0].first, matches[0].second)
                } else {
                    ContactResolveResult.MultipleMatches(distinctNames.take(MAX_CLARIFY_CANDIDATES))
                }
            }
        }
    }

    private companion object {
        const val MAX_CLARIFY_CANDIDATES = 6
    }
}
