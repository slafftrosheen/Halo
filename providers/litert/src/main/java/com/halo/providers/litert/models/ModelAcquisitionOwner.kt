package com.halo.providers.litert.models

/** Opaque setup transaction identity; never authority to delete an artifact without matching owner metadata. */
internal fun requireModelAcquisitionOwner(value: String) { require(java.util.UUID.fromString(value).toString() == value) }
