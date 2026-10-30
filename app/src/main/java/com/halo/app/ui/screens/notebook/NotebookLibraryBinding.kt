package com.halo.app.ui.screens.notebook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry

/** Only the resumed, visible Library entry supplies its live owner.
 * Tools, nested routes and covered modal states never retain a Library lease. */
@Composable
internal fun BindNotebookLibrary(session: NotebookLibrarySession?, model: NotebookViewModel, libraryVisible: Boolean) {
    val entry = LocalLifecycleOwner.current as? NavBackStackEntry
    DisposableEffect(session, model, entry, libraryVisible) {
        var lease: NotebookLibrarySession.Lease? = null
        fun release() { lease?.let { session?.detach(it) }; lease = null }
        fun acquire() {
            release()
            if (session != null && libraryVisible && entry?.destination?.route == "notebook")
                lease = session.attach(entry.id, model)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) acquire()
            else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) release()
        }
        entry?.lifecycle?.addObserver(observer)
        if (entry?.lifecycle?.currentState == Lifecycle.State.RESUMED) acquire()
        onDispose { entry?.lifecycle?.removeObserver(observer); release() }
    }
}
