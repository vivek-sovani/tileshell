package com.tileshell

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
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
import com.tileshell.feature.system.openOemBatterySettings
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
        handleOpenHubIntent(intent)
        com.tileshell.feature.livetiles.BatteryLog.ensureStarted(this)

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
                        if (LockAccessibilityService.isEnabledInSettings(ctx)) {
                            Toast.makeText(ctx, "still connecting — try again in a moment", Toast.LENGTH_SHORT).show()
                        } else {
                            showRecentsDisclosure = true
                        }
                    }
                },
                onLockScreen = {
                    // If the accessibility service is already connected, lock immediately.
                    // Otherwise show the prominent disclosure required by Google Play before
                    // sending the user to Accessibility Settings — but only when it's
                    // genuinely not enabled yet. isConnected() alone went stale after any
                    // process restart (a crash, or an OEM background-process kill) even
                    // with the service still enabled, re-showing this disclosure on an
                    // already-granted setup — user-reported as "accessibility setting
                    // asked frequently". isEnabledInSettings() checks the real system
                    // state instead, the same way notification-listener access already does.
                    if (LockAccessibilityService.isConnected()) {
                        lockScreen(ctx)
                    } else if (LockAccessibilityService.isEnabledInSettings(ctx)) {
                        Toast.makeText(ctx, "still connecting — try again in a moment", Toast.LENGTH_SHORT).show()
                    } else {
                        showLockDisclosure = true
                    }
                },
                onEnableAccessibility = { showRecentsDisclosure = true },
                onOpenNotifications = {
                    if (!LockAccessibilityService.expandNotifications()) {
                        if (LockAccessibilityService.isEnabledInSettings(ctx)) {
                            Toast.makeText(ctx, "still connecting — try again in a moment", Toast.LENGTH_SHORT).show()
                        } else {
                            showNotificationsDisclosure = true
                        }
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

    override fun onResume() {
        super.onResume()
        // While TileShell is on screen, a task reminder shows as its own toast.
        com.tileshell.core.data.reminders.TaskReminders.startVisible = true
    }

    override fun onPause() {
        com.tileshell.core.data.reminders.TaskReminders.startVisible = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleWallpaperTargetIntent(intent)) return
        startViewModel.goHome()
        handleOpenHubIntent(intent)
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
    /** A widget asking TileShell to open one of its hubs (the battery widget's tap). */
    private fun handleOpenHubIntent(intent: Intent) {
        handleDebugPressIntent(intent)
        // A task reminder's notification / "open" → that task's list.
        intent.getStringExtra(com.tileshell.core.data.reminders.TaskReminders.EXTRA_OPEN_TASK_LIST)?.let { listId ->
            startViewModel.openTasks(listId)
            intent.removeExtra(com.tileshell.core.data.reminders.TaskReminders.EXTRA_OPEN_TASK_LIST)
        }
        when (intent.getStringExtra(com.tileshell.feature.livetiles.widget.EXTRA_OPEN_HUB)) {
            "battery" -> startViewModel.openBatteryHub()
            "panchang" -> startViewModel.openPanchang()
            // Home-screen widgets open the hub they belong to.
            "weather" -> startViewModel.openWeatherHub(null)
            "markets" -> startViewModel.openMarketsHub()
            "sports" -> startViewModel.openSportsHub()
            "health" -> startViewModel.openHealthHub()
            // A running timer's notification or alarm icon.
            "clock" -> startViewModel.openClockHub()
            // The quick settings panel's "personalize" tile.
            PersonalizeTileService.OPEN_PERSONALIZE -> startViewModel.openPersonalize()
        }
        intent.removeExtra(com.tileshell.feature.livetiles.widget.EXTRA_OPEN_HUB)
    }

    /**
     * Debug builds only: press screenshots of the hubs. `debug.capture` turns
     * whole-panorama PNG capture on or off ([com.tileshell.core.design.PanoramaCapture]),
     * `debug.hub` opens a hub by name, `debug.demo_mail` shows made-up mail
     * notifications. Ignored in a release (not debuggable).
     */
    private fun handleDebugPressIntent(intent: Intent) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (intent.hasExtra("debug.capture")) {
            com.tileshell.core.design.PanoramaCapture.enabled = intent.getBooleanExtra("debug.capture", false)
        }
        if (intent.getBooleanExtra("debug.demo_mail", false)) {
            val now = System.currentTimeMillis()
            val gmail = "com.google.android.gm"
            val mail = listOf(
                Triple("Ananya Kulkarni", "Q3 launch deck — final slides attached for review", 4L),
                Triple("Rohan Mehta", "Flight to Bengaluru confirmed for Monday, 7:40 am", 38L),
                Triple("Priya Nair", "Re: Saturday lunch — booked a table for six", 95L),
                Triple("Kabir Shah", "Invoice #2041 paid, thanks!", 180L),
            )
            com.tileshell.feature.livetiles.NotificationCenter.setDemoNotifications(
                mail.mapIndexed { i, (who, text, minutesAgo) ->
                    com.tileshell.feature.livetiles.NotificationItem(
                        packageName = gmail, title = who, text = text, isClearable = true,
                        isGroupSummary = false, postTime = now - minutesAgo * 60_000, notificationKey = "demo-mail-$i",
                    )
                },
            )
        }
        // `debug.clock_steps=8,8,8` starts a timer set of those step lengths in seconds (alarm and buzz check).
        intent.getStringExtra("debug.clock_steps")?.let { spec ->
            val steps = spec.split(",").mapNotNull { it.trim().toLongOrNull() }.mapIndexed { i, sec ->
                com.tileshell.core.data.clock.SessionStep("test · step ${i + 1}", sec * 1000L)
            }
            com.tileshell.core.data.clock.ClockSessions.start(applicationContext, "test set", steps)
        }
        if (intent.getBooleanExtra("debug.demo_productivity", false)) {
            val appContext = applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val tasks = com.tileshell.core.data.TaskRepository.create(appContext)
                val launch = tasks.createList("launch")
                listOf("send the press kit to media", "final check of store screenshots", "book the launch venue", "reply to partner emails")
                    .forEach { tasks.addTask(launch, it) }
                val home = tasks.createList("home")
                listOf("buy diwali lights", "pay the electricity bill", "car service on the 20th")
                    .forEach { tasks.addTask(home, it) }
                val notes = com.tileshell.core.data.NoteRepository.create(appContext)
                listOf(
                    "launch talking points" to "live tiles, hubs, panorama\nmarathi and hindi keyboard\nprivacy: nothing leaves the phone",
                    "gift ideas" to "kavya: watercolour set\nrohan: running shoes",
                    "wifi at the office" to "network: guest-5g · ask reception for the code",
                ).forEach { (title, text) ->
                    val id = notes.createNote(text)
                    notes.updateTitle(id, title)
                }
            }
        }
        intent.getStringExtra("debug.gallery")?.let { com.tileshell.feature.start.TileGalleryState.request.value = it.ifBlank { null } }
        intent.getStringExtra("debug.calsys")?.split(":")?.let { parts ->
            val size = parts.getOrNull(1)?.let { n -> com.tileshell.core.data.TileSize.entries.firstOrNull { it.name == n } } ?: com.tileshell.core.data.TileSize.LARGE
            startViewModel.debugPinCalendarSystem(parts[0], size)
        }
        when (intent.getStringExtra("debug.hub")) {
            "weather" -> startViewModel.openWeatherHub(null)
            "music" -> startViewModel.openMusicHub()
            "calendar" -> startViewModel.openCalendarHub()
            "people" -> startViewModel.openPeopleHub()
            "productivity" -> startViewModel.openProductivityHub()
            "battery" -> startViewModel.openBatteryHub()
            "money" -> startViewModel.openMoneyHub()
            "markets" -> startViewModel.openMarketsHub()
            "news" -> startViewModel.openNewsHub()
            "shopping" -> startViewModel.openShoppingHub()
            "health" -> startViewModel.openHealthHub()
            "widgets" -> startViewModel.openAddWidgets()
            "sports" -> startViewModel.openSportsHub()
            "clock" -> startViewModel.openClockHub()
            "panchang" -> startViewModel.openPanchang()
            "about" -> startViewModel.openAbout()
            "guide" -> startViewModel.openPersonalizeGuide()
            "backup" -> startViewModel.openBackup()
            "backupfile" -> startViewModel.openBackup(com.tileshell.feature.personalize.BackupSection.FILE)
        }
    }

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
                    "locking the screen (the lock tile in the Quick Panel), opening recent " +
                    "apps (edge strip), and opening the system notification shade " +
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
                    "• Notification content — badges and message previews on live tiles, and " +
                    "the names of people who message you (kept 30 days for the People hub), if " +
                    "you enable notification access. Stays on this device.\n\n" +
                    "• Installed apps — read to display and launch them, as any home-screen " +
                    "launcher must. Stays on this device.\n\n" +
                    "• Which apps you tap — remembered locally to power the \"recent\" section " +
                    "of the App List and Quick Search. Never leaves this device.\n\n" +
                    "• App usage (how often apps are opened, screen time), if you allow usage " +
                    "access — sorts the People and Productivity apps pages and shows Battery hub " +
                    "screen time. Stays on this device.\n\n" +
                    "• Bank and payment notifications — new bank SMS and payment-app notices are " +
                    "read to list your transactions in the Money hub (amount, merchant, account " +
                    "last 4 digits, balance), and card statements and bill reminders (amount, " +
                    "due date) to list bills due. Stays on this device.\n\n" +
                    "• Music files on your device — Music hub library. Stays on this device.\n\n" +
                    "• Step count — Steps tile, from the phone's step sensor. Stays on this " +
                    "device.\n\n" +
                    "• Battery level, charging and screen on/off — recorded by TileShell for the " +
                    "Battery tile and hub, kept 8 days. Stays on this device.",
                )
                Text(
                    "\nTileShell has no analytics or ad SDKs, no account system, and never " +
                    "sells or shares this data. Full privacy policy: $PRIVACY_POLICY_URL\n\n" +
                    "Tap \"Go to Settings\" to enable the TileShell Accessibility Service, " +
                    "then return here.",
                )
                Text(
                    "\nSeeing this again after already enabling it once? Some phone makers " +
                    "(Samsung especially) include a battery-management feature that can quietly " +
                    "turn accessibility services back off over time — separate from Android's " +
                    "own battery optimization, which TileShell is already exempted from. Tap " +
                    "below to also check your device's own battery settings for TileShell.",
                )
                TextButton(onClick = { openOemBatterySettings(context) }) { Text("open battery settings") }
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
                    // ACTIVITY_RECOGNITION is deliberately NOT in this upfront batch —
                    // see StepsTile.kt's StepsPermissionGate doc comment: asking for
                    // activity-recognition access before the user has ever added a
                    // Steps tile/card has no obvious justification, which is exactly
                    // what Play's review flags. It's requested contextually instead,
                    // with its own rationale, the first time a steps face renders.
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
