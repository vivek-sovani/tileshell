package com.tileshell.feature.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableIntStateOf
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

    /**
     * Height of the navigation bar Android draws inside the keyboard's own window
     * (back / hide / switch keyboard, Android 13+). Some phones (Samsung) draw it
     * over the input view without reporting it as an inset, which covered the
     * bottom key row and took its touches; the keyboard pads by this instead.
     */
    private val imeNavBarHeight = mutableIntStateOf(0)

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
            decor.viewTreeObserver.addOnGlobalLayoutListener {
                val bar = findNavigationBarFrame(decor)
                val h = if (bar != null && bar.isShown) bar.height else 0
                if (h != imeNavBarHeight.intValue) imeNavBarHeight.intValue = h
            }
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TileShellImeService)
            setViewTreeSavedStateRegistryOwner(this@TileShellImeService)
            setContent { KeyboardScreen(controller, prefs, imeNavBarHeight.intValue) }
        }
    }

    /** The platform's `NavigationBarFrame` in the IME window, found by class name (it's hidden API). */
    private fun findNavigationBarFrame(view: View): View? {
        if (view.javaClass.simpleName == "NavigationBarFrame") return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findNavigationBarFrame(view.getChildAt(i))?.let { return it }
        }
        return null
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
