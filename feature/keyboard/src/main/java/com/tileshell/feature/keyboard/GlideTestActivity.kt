package com.tileshell.feature.keyboard

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/** The words of the glide test: short and long, doubled letters, turns, and look-alikes of each other. */
internal val GLIDE_TEST_WORDS = listOf(
    "the", "would", "people", "through", "because", "between", "something", "together", "about", "school",
    "family", "started", "important", "number", "little", "another", "morning", "please", "thanks", "tomorrow",
    "weekend", "dinner", "reach", "coming", "today", "phone", "message", "office", "station", "meeting",
    "too", "there", "these", "form", "from", "great", "write", "quick", "enjoy", "village",
)

internal class GlideRound(val word: String, val sample: GlideSample) {
    val decoded: List<String> get() = sample.decoded.orEmpty()
    val first get() = decoded.firstOrNull() == word
    val topThree get() = word in decoded.take(3)
}

/**
 * Records real swipes for known words, to see how glide typing does on this
 * person's thumbs and to tune it on those. Swipe each word shown, on TileShell's
 * keyboard in English; the word is put in the field and the next one appears.
 * At the end the result and the recordings can be shared as text, and replayed
 * against the decoder on a computer (`GlideReplayTest`).
 */
class GlideTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlideTestScreen() }
    }

    override fun onResume() {
        super.onResume()
        GlideLog.clear()
        GlideLog.recording = true
    }

    override fun onPause() {
        GlideLog.recording = false
        super.onPause()
    }
}

@Composable
private fun GlideTestScreen() {
    val context = LocalContext.current
    val tokens = colorTokens(isSystemInDarkTheme())
    val accent = TileAccents.forId(null)
    var index by remember { mutableIntStateOf(0) }
    var rounds by remember { mutableStateOf(listOf<GlideRound>()) }
    var last by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<EditText?>(null) }
    val done = index >= GLIDE_TEST_WORDS.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText("glide test", style = TextStyle(color = tokens.fg, fontSize = 34.sp, fontWeight = FontWeight.Light))
        if (!done) {
            val target = GLIDE_TEST_WORDS[index]
            BasicText(
                "${index + 1} of ${GLIDE_TEST_WORDS.size}  ·  swipe this word on TileShell's keyboard (English), the way you usually would",
                style = TextStyle(color = tokens.fgDim, fontSize = 14.sp),
            )
            BasicText(target, style = TextStyle(color = accent, fontSize = 44.sp, fontWeight = FontWeight.Light))
            if (last.isNotEmpty()) BasicText(last, style = TextStyle(color = tokens.fgDim, fontSize = 15.sp))
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    EditText(ctx).apply {
                        // An ordinary text field, so swiping is on (it is off where no suggestions are asked for).
                        inputType = InputType.TYPE_CLASS_TEXT
                        textSize = 20f
                        setTextColor(tokens.fg.toArgb())
                        setSingleLine(true)
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                            override fun afterTextChanged(s: Editable?) {
                                if (s.isNullOrBlank()) return
                                val goal = GLIDE_TEST_WORDS.getOrNull(index) ?: return
                                val sample = GlideLog.latest
                                // Only a swipe counts: letters tapped in leave no recording and are cleared.
                                if (sample?.decoded != null) {
                                    val round = GlideRound(goal, sample)
                                    rounds = rounds + round
                                    saveGlideRecording(ctx, rounds)
                                    last = "got “${round.decoded.firstOrNull() ?: "nothing"}”" +
                                        (if (round.first) "  ✓" else if (round.topThree) "  (in the top three)" else "  ✗")
                                    index++
                                } else {
                                    last = "that was typed, not swiped: try again"
                                }
                                GlideLog.clear()
                                s.clear()
                            }
                        })
                        editor = this
                        post {
                            requestFocus()
                            ctx.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
                        }
                    }
                },
            )
            BasicText(
                "skip this word",
                modifier = Modifier
                    .clickable {
                        index++
                        last = ""
                        GlideLog.clear()
                        editor?.setText("")
                    }
                    .padding(vertical = 10.dp),
                style = TextStyle(color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
        } else {
            val report = remember(rounds) { glideReport(rounds) }
            BasicText(report, style = TextStyle(color = tokens.fg, fontSize = 16.sp, lineHeight = 24.sp))
            BasicText(
                "share the result and recordings  ›",
                modifier = Modifier
                    .clickable {
                        val text = report + "\n\n" + GlideLog.export(rounds.map { it.word to it.sample })
                        Log.i("GlideTest", text)
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                "share glide test",
                            ),
                        )
                    }
                    .padding(vertical = 12.dp),
                style = TextStyle(color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            )
            BasicText(
                "start again",
                modifier = Modifier
                    .clickable {
                        rounds = emptyList()
                        index = 0
                        last = ""
                        GlideLog.clear()
                        editor?.setText("")
                    }
                    .padding(vertical = 12.dp),
                style = TextStyle(color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * Keeps the swipes so far in the app's folder on the phone (Android/data/com.tileshell/files/glide-last.txt),
 * after every swipe, so a half-finished test is kept and the file can be copied off by cable
 * (a shared text may be cut short by the app it is pasted into).
 */
internal fun saveGlideRecording(context: android.content.Context, rounds: List<GlideRound>) {
    runCatching {
        val dir = context.getExternalFilesDir(null) ?: return
        java.io.File(dir, "glide-last.txt").writeText(glideReport(rounds) + "\n\n" + GlideLog.export(rounds.map { it.word to it.sample }))
    }
}

/** The glide test's result as plain text. */
internal fun glideReport(rounds: List<GlideRound>): String {
    if (rounds.isEmpty()) return "no word swiped"
    val first = rounds.count { it.first }
    val three = rounds.count { it.topThree }
    val sb = StringBuilder()
    sb.appendLine("${rounds.size} words swiped")
    sb.appendLine("right word first: $first  (${"%.0f".format(first * 100.0 / rounds.size)}%)")
    sb.appendLine("right word in the top three: $three  (${"%.0f".format(three * 100.0 / rounds.size)}%)")
    val misses = rounds.filter { !it.first }
    if (misses.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("misses (meant → got):")
        misses.forEach { sb.appendLine("  ${it.word} → ${it.decoded.take(3).joinToString(", ").ifEmpty { "nothing" }}") }
    }
    return sb.toString().trimEnd()
}
