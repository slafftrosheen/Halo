package com.halo.providers.sherpa.stt

import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import com.halo.sdk.runtime.NativeResourceCoordinator
import java.io.File

object SherpaSpeechRecognizerFactory {
    fun create(directory: File, spec: SherpaSpeechModelCatalog.ModelSpec, language: String,
        coordinator: NativeResourceCoordinator?): BufferedSpeechRecognitionProvider {
        require(spec.supportsLanguage(language)) { "${spec.displayName} does not support $language" }
        return SherpaOfflineSpeechRecognitionProvider(directory, language = language, spec = spec,
            nativeResourceCoordinator = coordinator)
    }
}
