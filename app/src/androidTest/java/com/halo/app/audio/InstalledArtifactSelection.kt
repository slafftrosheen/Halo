package com.halo.app.audio

import android.os.Bundle
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LiteRtCommunityModelGuide

/** The host snapshot and a fresh Kotlin-manager verification must agree before native inspection. */
internal fun requireSelectedInstalledArtifact(descriptor: LocalModelDescriptor, args: Bundle) {
    val sha = requireNotNull(args.getString("expectedSha256")) { "A verified installed snapshot fingerprint is required" }
    val bytes = requireNotNull(args.getString("expectedBytes")?.toLongOrNull()) { "A verified installed snapshot length is required" }
    require(descriptor.verified && descriptor.sha256.equals(sha, ignoreCase = true) && descriptor.bytes == bytes) {
        "Artifact fingerprint changed or is unverified; stop this run"
    }
    require(!LiteRtCommunityModelGuide.nonRunnableFingerprint(descriptor.sha256)) { "Artifact is known non-runnable" }
}
