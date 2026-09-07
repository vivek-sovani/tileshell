package com.tileshell.feature.livetiles

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.StepsPrefs
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import java.time.LocalDate

/** Default daily goal the front-face progress bar tracks toward. */
const val DEFAULT_STEPS_GOAL = 10_000

/** 0..1 progress toward [goal] — pure, clamped so an over-goal day doesn't overflow the bar. */
fun stepsGoalProgress(steps: Int, goal: Int = DEFAULT_STEPS_GOAL): Float =
    (steps.toFloat() / goal).coerceIn(0f, 1f)

/**
 * Resolves today's step count against a persisted [StepsPrefs.Baseline], and
 * the baseline that should be saved back (unchanged unless today's reading
 * calls for a reset). Pure — no sensor/Context/clock call inside — so the
 * three reset cases are unit-testable directly:
 *  - no baseline yet (this device's very first sensor read ever);
 *  - the stored baseline is from a different day (yesterday's tally is done —
 *    today starts counting from whatever the sensor reads right now);
 *  - the raw counter is now *lower* than the baseline, meaning the phone
 *    rebooted since ([Sensor.TYPE_STEP_COUNTER] resets to 0 on boot) — today's
 *    pre-reboot steps are unrecoverable, a known, accepted limitation, so
 *    today's count restarts from 0 the same way a new day does.
 */
data class StepsResolution(val stepsToday: Int, val newBaseline: StepsPrefs.Baseline)

fun resolveSteps(currentCounter: Float, baseline: StepsPrefs.Baseline?, todayEpochDay: Long): StepsResolution {
    val needsReset = baseline == null || currentCounter < baseline.counter || baseline.epochDay != todayEpochDay
    return if (needsReset) {
        StepsResolution(stepsToday = 0, newBaseline = StepsPrefs.Baseline(currentCounter, todayEpochDay))
    } else {
        StepsResolution(
            stepsToday = (currentCounter - baseline.counter).toInt().coerceAtLeast(0),
            newBaseline = baseline,
        )
    }
}

/**
 * What the home-screen steps widget can actually show for one refresh. A
 * widget has no composable gate of its own — a `RemoteViews` tree can't host
 * a permission dialog — so the three outcomes have to be distinguishable in
 * the pushed views themselves: NEEDS_PERMISSION is the one that gets a
 * "tap to allow" label and a body tap into the widget's own configure
 * activity (which does the asking), while UNAVAILABLE (no step sensor on
 * this device, or the one-shot sensor read timed out) has nothing the user
 * could act on and so stays a plain dash.
 */
enum class StepsWidgetState { COUNT, NEEDS_PERMISSION, UNAVAILABLE }

/** Pure — [steps] is null whenever the count couldn't be read for any reason. */
fun stepsWidgetState(granted: Boolean, steps: Int?): StepsWidgetState = when {
    !granted -> StepsWidgetState.NEEDS_PERMISSION
    steps == null -> StepsWidgetState.UNAVAILABLE
    else -> StepsWidgetState.COUNT
}

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * How long the step-counter's own hardware (a Sensor Hub coprocessor on most
 * devices, confirmed via `dumpsys sensorservice` reporting a non-zero
 * `batching_period` for this sensor on real hardware) may batch readings
 * before waking the AP to deliver them. Passed as `maxReportLatencyUs` on
 * [SensorManager.registerListener] — this tile only ever needs "today's
 * count," never per-step timing, so there's no reason to wake for every
 * single sample the way [SensorManager.SENSOR_DELAY_NORMAL] alone implies.
 */
private const val STEPS_BATCH_LATENCY_US = 60_000_000

/**
 * Listens to the raw step-counter sensor while composed *and* [active],
 * resolving each reading against the persisted baseline (see [resolveSteps])
 * and writing the baseline back only when it actually changes (a fresh
 * reading on the same day never needs a write). Returns null before the
 * first sensor event arrives, or when there's no step sensor on this device.
 *
 * [active] mirrors every other live tile's own gate (edit mode, app list,
 * battery saver, animations off — see `rememberLiveTilesActive`), which this
 * one had never actually received: a real on-device battery diagnosis found
 * the sensor registered essentially the entire time on battery (6h57m of
 * 7h41m) regardless of whether Start — let alone a Steps tile specifically —
 * was ever on screen, since the listener was tied only to composition, not
 * to visibility. Measured cost on that device was negligible (this sensor is
 * hub-offloaded there), but a plain always-on AP-side registration is real,
 * unnecessary battery risk on any device without that offload, so this fixes
 * the actual bug rather than leaving it merely harmless-here.
 */
@Composable
private fun rememberStepsToday(active: Boolean): Int? {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val sensor = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    var steps by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(sensorManager, sensor, active) {
        if (sensorManager == null || sensor == null || !active) return@DisposableEffect onDispose {}
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val counter = event.values.firstOrNull() ?: return
                val baseline = StepsPrefs.readBaseline(context)
                val resolution = resolveSteps(counter, baseline, LocalDate.now().toEpochDay())
                if (resolution.newBaseline != baseline) {
                    StepsPrefs.saveBaseline(context, resolution.newBaseline)
                }
                steps = resolution.stepsToday
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        runCatching {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, STEPS_BATCH_LATENCY_US)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }
    return steps
}

private enum class PermissionStage { NONE, RATIONALE, BLOCKED }

/**
 * The contextual ACTIVITY_RECOGNITION rationale + request, shown the first
 * time a steps face is actually composed with the permission not yet granted
 * — i.e. the moment a Steps tile/card exists on Start or the glance page,
 * whichever the user added it from ([StepsTileFace]/[StepsSmallFace] are the
 * one shared render path for both surfaces, so gating here covers both).
 * Deliberately *not* bundled into `MainActivity`'s upfront permission batch
 * (contacts/calendar/location) the way it used to be — asking for activity-
 * recognition access before the user has ever added anything steps-related
 * has no obvious justification, which is exactly what Play's review flags
 * (and separately, ACTIVITY_RECOGNITION routes any app declaring it through
 * Play Console's mandatory Health-apps declaration regardless of *when* it's
 * requested, so this doesn't remove that step — it only fixes the in-app
 * side: an unexplained ask with no connection to what the user just did).
 * [StepsPrefs.markPermissionAsked] keeps it from nagging: declining ("not
 * now") never re-asks here, the same as every other permission-rationale
 * dialog in this app — personalize → permissions → "physical activity" is
 * the permanent way back in afterwards.
 *
 * The [PermissionStage.BLOCKED] branch exists because a *permanently* denied
 * runtime permission makes `launch` a silent no-op — the system dialog never
 * appears and the result is "denied" instantly, so tapping "allow" looks like
 * a dead button (user-reported: "it asked but was non responsive"). Detected
 * via [canShowSystemPermissionDialog], and answered by pointing at the one
 * place it can still be turned on ([openAppPermissionSettings]) instead of
 * leaving the tile silently static.
 */
@Composable
private fun StepsPermissionGate(granted: Boolean) {
    if (granted) return
    val context = LocalContext.current
    var stage by remember {
        mutableStateOf(if (StepsPrefs.permissionAsked(context)) PermissionStage.NONE else PermissionStage.RATIONALE)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        StepsPrefs.markPermissionAsked(context)
        stage = if (
            !isGranted &&
            !canShowSystemPermissionDialog(context, Manifest.permission.ACTIVITY_RECOGNITION, asked = true)
        ) {
            PermissionStage.BLOCKED
        } else {
            PermissionStage.NONE
        }
    }

    when (stage) {
        PermissionStage.NONE -> Unit
        PermissionStage.RATIONALE -> AlertDialog(
            onDismissRequest = {
                stage = PermissionStage.NONE
                StepsPrefs.markPermissionAsked(context)
            },
            title = { Text("show today's steps?") },
            text = {
                Text(
                    "reads your phone's built-in step-counter sensor to show today's count on this card. " +
                        "stays on your device — nothing is sent anywhere.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val asked = StepsPrefs.permissionAsked(context)
                    if (canShowSystemPermissionDialog(context, Manifest.permission.ACTIVITY_RECOGNITION, asked)) {
                        stage = PermissionStage.NONE
                        requestPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    } else {
                        stage = PermissionStage.BLOCKED
                    }
                }) { Text("allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    stage = PermissionStage.NONE
                    StepsPrefs.markPermissionAsked(context)
                }) { Text("not now") }
            },
        )
        PermissionStage.BLOCKED -> AlertDialog(
            onDismissRequest = { stage = PermissionStage.NONE },
            title = { Text("steps permission is turned off") },
            text = {
                Text(
                    "android won't ask again once \"physical activity\" has been denied. turn it on in " +
                        "settings → permissions → physical activity, and the count appears on its own.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    stage = PermissionStage.NONE
                    openAppPermissionSettings(context)
                }) { Text("open settings") }
            },
            dismissButton = {
                TextButton(onClick = { stage = PermissionStage.NONE }) { Text("not now") }
            },
        )
    }
}

/**
 * The live steps tile: today's step count, from the device's own step-counter
 * sensor (see [rememberStepsToday]). Degrades to [fallback] — same slot every
 * other opt-in tile uses — when [Manifest.permission.ACTIVITY_RECOGNITION]
 * isn't granted, the device has no step sensor, or no reading has arrived
 * yet. Never flips: a progress bar toward [DEFAULT_STEPS_GOAL] is already a
 * second piece of information on the one face, so there's little left worth
 * putting on a back side.
 */
@Composable
fun StepsTileFace(
    size: TileSize,
    fallback: @Composable () -> Unit,
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val granted = rememberPermissionGranted(Manifest.permission.ACTIVITY_RECOGNITION)
    StepsPermissionGate(granted)
    if (!granted) return fallback()
    val steps = rememberStepsToday(active) ?: return fallback()

    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    Column(
        modifier = modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Icon(
            imageVector = TileIcons["steps"],
            contentDescription = null,
            tint = FaceText,
            modifier = Modifier
                .size(if (short) 18.dp else if (big) 28.dp else 22.dp)
                .padding(bottom = 2.dp),
        )
        Text(
            text = steps.toString(),
            color = FaceText,
            fontSize = if (short) 26.sp else if (narrow) 28.sp else if (big) 60.sp else 42.sp,
            lineHeight = if (short) 26.sp else if (narrow) 28.sp else if (big) 60.sp else 42.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-1).sp,
            maxLines = 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = "steps",
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (short) 10.sp else if (narrow) 11.sp else 13.sp,
            maxLines = 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (!narrow) {
            Spacer(Modifier.height(if (big) 12.dp else 8.dp))
            StepsGoalBar(progress = stepsGoalProgress(steps))
        }
        if (big) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun StepsGoalBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(FaceText.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(FaceText),
        )
    }
}

/** The compact 1×1 face (ICONS home style / SMALL tile): just the step count. */
@Composable
fun StepsSmallFace(
    fallback: @Composable () -> Unit,
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val granted = rememberPermissionGranted(Manifest.permission.ACTIVITY_RECOGNITION)
    StepsPermissionGate(granted)
    if (!granted) return fallback()
    val steps = rememberStepsToday(active) ?: return fallback()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = steps.toString(),
            color = FaceText,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-1).sp,
            maxLines = 1,
        )
    }
}
