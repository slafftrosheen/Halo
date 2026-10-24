package com.halo.app.ui.shell

/** Per-host transfer marker. It never starts, resumes, or owns a speech session. */
internal class ShellSpeechHandoff {
    private var transferring = false

    fun preservesSpeechOnStop(changingConfiguration: Boolean, finishing: Boolean): Boolean =
        changingConfiguration || transferring && finishing

    fun launchAndFinish(launch: () -> Unit, finish: () -> Unit, onFailure: () -> Unit): Boolean {
        transferring = true
        return try {
            launch()
            finish()
            true
        } catch (_: RuntimeException) {
            transferring = false
            onFailure()
            false
        }
    }
}
