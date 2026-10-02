package com.tileshell.feature.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The TileShell keyboard: a Metro-style input method drawn with Compose.
 *
 * An InputMethodService isn't an Activity, so it supplies the lifecycle and
 * saved-state owners a ComposeView needs itself and sets them on the IME
 * window's decor view.
 */
class TileShellImeService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val scope = MainScope()
    private val prefs by lazy { KeyboardPrefs.get(this) }
    private val controller by lazy { KeyboardController(this, prefs, scope) }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        scope.launch { prefs.settings.drop(1).collect { controller.onSettingsChanged() } }
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TileShellImeService)
            setViewTreeSavedStateRegistryOwner(this@TileShellImeService)
            setContent { KeyboardScreen(controller, prefs) }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.onStartInput(info)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Our composing text ends at the cursor; anything else means it's gone.
        controller.onSelectionChanged(composing = candidatesStart >= 0 && candidatesEnd == newSelEnd)
    }

    /** Landscape keeps the keyboard under the app instead of a full-screen text box. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

}
