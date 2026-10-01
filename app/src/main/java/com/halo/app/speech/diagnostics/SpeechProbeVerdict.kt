package com.halo.app.speech.diagnostics

object SpeechProbeVerdict {
    fun recognitionPassed(result: SpeechDiagnosticResult): Boolean = !result.recognition?.text.isNullOrBlank()
}
