package com.halo.app.data.settings

import java.security.MessageDigest

/** Exact saved preference content token, shared by repository CAS and semantic adapters. */
fun notificationSettingsVersion(prefix: String, vararg values: String): String = "$prefix:" +
    MessageDigest.getInstance("SHA-256").digest(values.joinToString("\u0000").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

fun DigestDeliverySettings.sourceVersion(): String = notificationSettingsVersion("digest-delivery", channel, wearableId)
