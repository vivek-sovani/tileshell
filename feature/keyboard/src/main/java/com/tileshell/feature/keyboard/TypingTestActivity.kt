package com.tileshell.feature.keyboard

import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/** The sentences of the typing test: lower-case letters and spaces only, so no shift or punctuation is needed. */
internal val TYPING_TEST_SENTENCES = listOf(
    "the quick brown fox jumps over the lazy dog",
    "please send me the report by tomorrow morning",
    "we are meeting at the station before noon",
    "my brother bought a new phone yesterday",
    "could you call me back when you are free",
    "the weather looks lovely today so let us go out",
    "i will reach home in about twenty minutes",
    "thanks a lot for your kind help with this",
)

/** One finished sentence of the test. */
internal class TestRound(val target: String, val taps: List<TapRecord>, val millis: Long, val deletions: Int)

/**
 * A typing test for comparing keyboards: type each sentence as it appears (no
 * suggestions, so it is touch accuracy that is measured, not prediction).
 * For any keyboard it reports speed and how often letters were deleted; with
 * TileShell's own keyboard it also reads every tap, so it can say how far from
 * the intended key's centre taps land and which keys get mixed up. Opened from
 * the keyboard settings.
 */
class TypingTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TypingTestScreen() }
    }

    override fun onResume() {
        super.onResume()
        TapLog.clear()
        TapLog.recording = true
    }

    override fun onPause() {
        TapLog.recording = false
        super.onPause()
    }
}

@Composable
private fun TypingTestScreen() {
    val tokens = colorTokens(isSystemInDarkTheme())
    val accent = TileAccents.forId(null)
    var index by remember { mutableIntStateOf(0) }
    var rounds by remember { mutableStateOf(listOf<TestRound>()) }
    var typed by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<EditText?>(null) }
    // Clock and deletions of the sentence in progress; the watcher below outlives recompositions.
    val progress = remember { object { var startedAt = 0L; var deletions = 0; var length = 0 } }
    val done = index >= TYPING_TEST_SENTENCES.size

    fun resetSentence() {
        progress.startedAt = 0L
        progress.deletions = 0
        progress.length = 0
        typed = ""
        TapLog.clear()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText("typing test", style = TextStyle(color = tokens.fg, fontSize = 34.sp, fontWeight = FontWeight.Light))
        if (!done) {
            val target = TYPING_TEST_SENTENCES[index]
            BasicText(
                "${index + 1} of ${TYPING_TEST_SENTENCES.size}  ·  type this, with the keyboard you want to test",
                style = TextStyle(color = tokens.fgDim, fontSize = 14.sp),
            )
            BasicText(
                androidx.compose.ui.text.buildAnnotatedString {
                    val good = target.commonPrefixWith(typed).length
                    withStyle(androidx.compose.ui.text.SpanStyle(color = accent)) { append(target.substring(0, good)) }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = tokens.fg)) { append(target.substring(good)) }
                },
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Light, lineHeight = 32.sp),
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    EditText(context).apply {
                        // No suggestions or autocorrect: what is tested is where the fingers land.
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        textSize = 20f
                        setTextColor(tokens.fg.toArgb())
                        setSingleLine(true)
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                            override fun afterTextChanged(s: Editable?) {
                                val text = s?.toString().orEmpty()
                                if (text.isNotEmpty() && progress.startedAt == 0L) progress.startedAt = SystemClock.elapsedRealtime()
                                if (text.length < progress.length) progress.deletions += progress.length - text.length
                                progress.length = text.length
                                typed = text
                                val goal = TYPING_TEST_SENTENCES.getOrNull(index) ?: return
                                if (text == goal) {
                                    rounds = rounds + TestRound(
                                        goal,
                                        TapLog.snapshot(),
                                        SystemClock.elapsedRealtime() - progress.startedAt,
                                        progress.deletions,
                                    )
                                    index++
                                    resetSentence()
                                    s?.clear()
                                }
                            }
                        })
                        editor = this
                        post {
                            requestFocus()
                            (context.getSystemService(InputMethodManager::class.java))?.showSoftInput(this, 0)
                        }
                    }
                },
            )
            BasicText(
                "skip this sentence",
                modifier = Modifier
                    .clickable {
                        index++
                        resetSentence()
                        editor?.setText("")
                    }
                    .padding(vertical = 10.dp),
                style = TextStyle(color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
        } else {
            val report = remember(rounds) { typingTestReport(rounds) }
            LaunchedReport(report)
            BasicText(report, style = TextStyle(color = tokens.fg, fontSize = 16.sp, lineHeight = 24.sp))
            BasicText(
                "start again",
                modifier = Modifier
                    .clickable {
                        rounds = emptyList()
                        index = 0
                        resetSentence()
                        editor?.setText("")
                    }
                    .padding(vertical = 12.dp),
                style = TextStyle(color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun LaunchedReport(report: String) {
    androidx.compose.runtime.LaunchedEffect(report) { Log.i("TypingTest", report) }
}

/** The result of a finished test as plain text. */
internal fun typingTestReport(rounds: List<TestRound>): String {
    if (rounds.isEmpty()) return "no sentence finished"
    val chars = rounds.sumOf { it.target.length }
    val seconds = rounds.sumOf { it.millis } / 1000.0
    val wpm = if (seconds > 0) chars / 5.0 / (seconds / 60.0) else 0.0
    val deletions = rounds.sumOf { it.deletions }
    val sb = StringBuilder()
    sb.appendLine("${rounds.size} sentences, $chars letters in ${"%.0f".format(seconds)} s  ·  ${"%.0f".format(wpm)} words a minute")
    sb.appendLine("letters deleted: $deletions  (${"%.1f".format(deletions * 100.0 / chars)}% of the letters)")
    val withTaps = rounds.filter { it.taps.isNotEmpty() }
    if (withTaps.isEmpty()) {
        sb.append("\nNo taps were recorded: only TileShell's own keyboard records them, so this result is speed and deletions alone.")
        return sb.toString()
    }
    val stats = TapReplay.analyseAll(withTaps.map { it.taps to it.target })
    sb.appendLine()
    sb.appendLine("taps counted: ${stats.taps}")
    sb.appendLine("wrong key: ${stats.wrong}  (${"%.1f".format(stats.wrongRate * 100)}%)")
    sb.appendLine("landing, in key widths from the intended key's centre:")
    sb.appendLine("  mean   ${"%+.2f".format(stats.meanDx)} across (+ = right),  ${"%+.2f".format(stats.meanDy)} down")
    sb.appendLine("  spread ${"%.2f".format(stats.sigmaX)} across,  ${"%.2f".format(stats.sigmaY)} down")
    if (stats.confusions.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("keys mixed up (meant → given):")
        stats.confusions.take(8).forEach { (pair, n) -> sb.appendLine("  ${pair.first} → ${pair.second}  ×$n") }
    }
    return sb.toString().trimEnd()
}
