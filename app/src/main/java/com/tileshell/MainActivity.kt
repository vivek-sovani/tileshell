package com.tileshell

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.RatingPromptPrefs
import com.tileshell.core.data.isRatingPromptCheckWindowOpen
import com.tileshell.core.data.rollShowsPrompt
import com.tileshell.feature.livetiles.WeatherRefreshWorker
import com.tileshell.feature.start.StartScreen
import com.tileshell.feature.start.StartViewModel
import com.tileshell.feature.system.DefaultLauncher
import com.tileshell.feature.system.InAppReview
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://vivek-sovani.github.io/tileshell/"
private const val FEEDBACK_EMAIL = "vivek.sovani@kimayainfotech.com"

class MainActivity : ComponentActivity() {

    private val startViewModel: StartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        startViewModel.personalizeOpen.value -> startViewModel.closePersonalize()
                        startViewModel.expandedFolderId.value != null -> startViewModel.collapseFolder()
                        startViewModel.editMode.value -> startViewModel.exitEdit()
                        startViewModel.isAppList.value -> startViewModel.goHome()
                    }
                }
            },
        )

        handleWallpaperTargetIntent(intent)

        setContent {
            DefaultLauncherPrompt()
            RequestRuntimePermissionsOnStart()
            RatingPromptHost()
            val settings by startViewModel.settings.collectAsStateWithLifecycle()
            StatusBarVisibilityEffect(hide = settings.hideStatusBar)
            val ctx = LocalContext.current
            var showLockDisclosure by remember { mutableStateOf(false) }
            var showRecentsDisclosure by remember { mutableStateOf(false) }
            var showNotificationsDisclosure by remember { mutableStateOf(false) }

            StartScreen(
                viewModel = startViewModel,
                onRecents = {
                    if (!LockAccessibilityService.showRecents()) {
                        showRecentsDisclosure = true
                    }
                },
                onLockScreen = {
                    // If the accessibility service is already connected, lock immediately.
                    // Otherwise show the prominent disclosure required by Google Play before
                    // sending the user to Accessibility Settings.
                    if (LockAccessibilityService.isConnected()) {
                        lockScreen(ctx)
                    } else {
                        showLockDisclosure = true
                    }
                },
                onOpenNotifications = {
                    if (!LockAccessibilityService.expandNotifications()) {
                        showNotificationsDisclosure = true
                    }
                },
            )

            if (showLockDisclosure) {
                AccessibilityDisclosureDialog(
                    onConfirm = {
                        showLockDisclosure = false
                        lockScreen(ctx)
                    },
                    onDismiss = { showLockDisclosure = false },
                )
            }
            if (showRecentsDisclosure) {
                AccessibilityDisclosureDialog(
                    onConfirm = {
                        showRecentsDisclosure = false
                        openAccessibilitySettings(ctx)
                    },
                    onDismiss = { showRecentsDisclosure = false },
                )
            }
            if (showNotificationsDisclosure) {
                AccessibilityDisclosureDialog(
                    onConfirm = {
                        showNotificationsDisclosure = false
                        openAccessibilitySettings(ctx)
                    },
                    onDismiss = { showNotificationsDisclosure = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleWallpaperTargetIntent(intent)) return
        startViewModel.goHome()
        // Dismiss the keyboard when returning to Start via the Home button.
        // The search field in the app list / feed retains IME focus after
        // goHome() snaps the pager back, leaving the keyboard open on Start.
        currentFocus?.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    /**
     * Handles any intent that hands TileShell an image to become the Start wallpaper:
     * a share-sheet `ACTION_SEND` (e.g. Gallery/Photos' own "share"), or a wallpaper
     * app's "apply via" / "set wallpaper" chooser (`CROP_AND_SET_WALLPAPER`, issued by
     * `WallpaperManager.getCropAndSetWallpaperIntent()` and fired directly by most
     * third-party wallpaper apps — the same chooser slot Nova/Apex/etc. occupy) or the
     * system Photos "set as" chooser (`ACTION_ATTACH_DATA`). All three carry the image
     * differently (`EXTRA_STREAM` vs. `intent.data`) but converge on the same
     * [StartViewModel.receiveSharedImage] so `StartScreen` imports it and opens the
     * crop/reframe overlay, same as picking a wallpaper from within the app. Returns
     * true if this intent was one of the three (and was handled), so callers can skip
     * their own "just reopened" home-button handling for it.
     */
    private fun handleWallpaperTargetIntent(intent: Intent): Boolean {
        if (intent.type?.startsWith("image/") != true) return false
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            "android.service.wallpaper.CROP_AND_SET_WALLPAPER", Intent.ACTION_ATTACH_DATA -> intent.data
            else -> null
        } ?: return false
        startViewModel.receiveSharedImage(uri)
        return true
    }
}

/**
 * Prominent disclosure dialog shown before directing the user to enable
 * TileShell's Accessibility Service. Required by Google Play policy for apps
 * that declare an accessibility service — and, per a Play Console rejection
 * ("Accessibility API policy: Insufficient data use declaration in the
 * prominent disclosure"), the disclosure must spell out *all* data the app
 * collects anywhere, not just what the accessibility service itself touches
 * (the service only ever calls `performGlobalAction`; it never reads screen
 * content). The itemized list below mirrors `docs/PRIVACY_POLICY.md` /
 * [PRIVACY_POLICY_URL], condensed to the data types Play's reviewer actually
 * flagged: location, calendar, contacts, the installed-apps list, and the
 * locally-tracked "recent apps" tap history ("page views and taps in app").
 *
 * Used for screen-lock (gear long-press), recent-apps (edge strip), and the
 * left-edge-swipe-down notifications gesture (the right-edge sibling gesture
 * opens this app's own Quick Panel and needs no accessibility action) — all
 * three rely on the same single Accessibility Service.
 */
@Composable
private fun AccessibilityDisclosureDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you enable accessibility") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "TileShell's Accessibility Service is used for one narrow purpose only: " +
                    "locking the screen (long-press the settings icon), opening recent apps " +
                    "(edge strip), and opening the system notification shade " +
                    "(swipe down from the left screen edge). It never reads your screen " +
                    "content, other apps, or keystrokes.\n\n" +
                    "Separately from Accessibility — and only if you grant each permission — " +
                    "TileShell also collects this data. All of it below:",
                )
                Text(
                    "\n• Contacts (name + photo) — People tile, Quick Search. Stays on this " +
                    "device.\n\n" +
                    "• Calendar events (title + time) — Calendar tile's next-event display. " +
                    "Stays on this device.\n\n" +
                    "• Approximate location — Weather tile forecast. Sent to Open-Meteo as " +
                    "coordinates only; never precise/GPS-level location.\n\n" +
                    "• Notification content — badges and message previews on live tiles, if " +
                    "you enable notification access. Stays on this device.\n\n" +
                    "• Installed apps — read to display and launch them, as any home-screen " +
                    "launcher must. Stays on this device.\n\n" +
                    "• Which apps you tap — remembered locally to power the \"recent\" section " +
                    "of the App List and Quick Search. Never leaves this device.",
                )
                Text(
                    "\nTileShell has no analytics or ad SDKs, no account system, and never " +
                    "sells or shares this data. Full privacy policy: $PRIVACY_POLICY_URL\n\n" +
                    "Tap \"Go to Settings\" to enable the TileShell Accessibility Service, " +
                    "then return here.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Go to Settings") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Privacy policy") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        },
    )
}

/**
 * Hides/shows the system status bar per the "hide status bar" Personalize toggle.
 * [WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE] keeps it reachable
 * with a swipe from the top edge even while hidden, rather than a fully locked-down
 * immersive mode.
 *
 * That "transient reveal" is normally expected to auto-hide itself again after a
 * few seconds — but on at least one real device it stayed shown permanently once
 * swiped into view, never re-hiding on its own. Rather than trust the system to
 * time it out, a [OnApplyWindowInsetsListener] on the decor view (observing, never
 * consuming, so Compose's own insets handling downstream is untouched) explicitly
 * re-hides the status bar [REHIDE_DELAY_MS] after it's *reported visible* while
 * this setting is on — covering both the swipe-reveal case and any other way the
 * system might have shown it back (e.g. after a notification).
 */
@Composable
private fun StatusBarVisibilityEffect(hide: Boolean) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(hide) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    DisposableEffect(hide, view) {
        val window = (view.context as? Activity)?.window
        val decorView = window?.decorView
        if (!hide || window == null || decorView == null) return@DisposableEffect onDispose {}

        val controller = WindowCompat.getInsetsController(window, view)
        var reHideJob: Job? = null
        val listener = OnApplyWindowInsetsListener { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.statusBars())) {
                reHideJob?.cancel()
                reHideJob = scope.launch {
                    delay(REHIDE_DELAY_MS)
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                }
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(decorView, listener)
        onDispose {
            reHideJob?.cancel()
            ViewCompat.setOnApplyWindowInsetsListener(decorView, null)
        }
    }
}

private const val REHIDE_DELAY_MS = 2500L

private fun openAccessibilitySettings(context: Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * Asks the user to make TileShell the default launcher every time the app
 * opens fresh (not just the very first run) while it still isn't one —
 * `LaunchedEffect(Unit)` runs once per [MainActivity] composition, i.e. once
 * per process/open, not on every resume, so switching away and back doesn't
 * re-trigger it mid-session.
 */
@Composable
private fun DefaultLauncherPrompt() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* outcome read via DefaultLauncher.isDefault when needed */ }

    LaunchedEffect(Unit) {
        if (DefaultLauncher.isDefault(context)) return@LaunchedEffect
        val intent = DefaultLauncher.createPromptIntent(context) ?: return@LaunchedEffect
        runCatching { launcher.launch(intent) }
    }
}

@Composable
private fun RequestRuntimePermissionsOnStart() {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            WeatherRefreshWorker.refreshNow(context)
        }
    }

    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                ),
            )
        }
    }
}

private enum class RatingPromptStep { HIDDEN, ASK, FEEDBACK }

/**
 * Occasionally asks "enjoying tileshell?" on Start — not a nag on every
 * resume. There's no "app open count" to gate on here: TileShell *is* the
 * launcher, so it has no discrete launch events the way a normal app does —
 * it's simply resumed whenever the user returns to Start, which can happen
 * dozens of times a day. Gating is purely day-interval based instead
 * ([isRatingPromptCheckWindowOpen]): a minimum age since first launch, then a
 * multi-day interval between check windows, each with only a
 * [rollShowsPrompt] chance of actually showing — re-checked on every
 * `ON_RESUME` (mirrors [rememberAppUpdateState]'s re-check pattern), with the
 * "last asked" clock advanced the moment a window opens regardless of the
 * roll's outcome, so a resume storm within one window can't turn a single
 * multi-day interval into several rolls.
 *
 * Answering either way marks the user as responded (never asked again);
 * dismissing the initial ask without answering does not, so it can resurface
 * at the next check window. "Enjoying it" launches Play's in-app review flow
 * directly — Play never reports back whether the user actually rated, by
 * design, so this is the only signal available. "Not really" offers an email
 * feedback channel instead of pushing toward a public review.
 */
@Composable
private fun RatingPromptHost() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var step by remember { mutableStateOf(RatingPromptStep.HIDDEN) }

    fun check() {
        RatingPromptPrefs.ensureFirstLaunchSeeded(context)
        val windowOpen = isRatingPromptCheckWindowOpen(
            nowMs = System.currentTimeMillis(),
            firstLaunchMs = RatingPromptPrefs.firstLaunchMs(context),
            hasResponded = RatingPromptPrefs.hasResponded(context),
            lastAskedMs = RatingPromptPrefs.lastAskedMs(context),
        )
        if (!windowOpen) return
        RatingPromptPrefs.markAsked(context)
        if (rollShowsPrompt(Random.nextFloat())) step = RatingPromptStep.ASK
    }

    DisposableEffect(lifecycleOwner) {
        check()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) check()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (step == RatingPromptStep.ASK) {
        RatingAskDialog(
            onEnjoying = {
                RatingPromptPrefs.markResponded(context)
                step = RatingPromptStep.HIDDEN
                (context as? Activity)?.let { InAppReview.launch(it) }
            },
            onNotReally = {
                RatingPromptPrefs.markResponded(context)
                step = RatingPromptStep.FEEDBACK
            },
            onDismiss = { step = RatingPromptStep.HIDDEN },
        )
    }
    if (step == RatingPromptStep.FEEDBACK) {
        RatingFeedbackDialog(
            onSendFeedback = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_EMAIL"))
                            .putExtra(Intent.EXTRA_SUBJECT, "tileshell feedback")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                step = RatingPromptStep.HIDDEN
            },
            onDismiss = { step = RatingPromptStep.HIDDEN },
        )
    }
}

@Composable
private fun RatingAskDialog(onEnjoying: () -> Unit, onNotReally: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("enjoying tileshell?") },
        text = { Text("let us know what you think — it only takes a second.") },
        confirmButton = {
            TextButton(onClick = onEnjoying) { Text("yes!") }
        },
        dismissButton = {
            TextButton(onClick = onNotReally) { Text("not really") }
        },
    )
}

@Composable
private fun RatingFeedbackDialog(onSendFeedback: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("sorry to hear that") },
        text = { Text("mind telling us what's missing or not working? it helps a lot.") },
        confirmButton = {
            TextButton(onClick = onSendFeedback) { Text("send feedback") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("no thanks") }
        },
    )
}
