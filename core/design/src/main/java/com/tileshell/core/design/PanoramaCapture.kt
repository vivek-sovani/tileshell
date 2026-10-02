package com.tileshell.core.design

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Press images of a whole hub panorama (debug builds only; the app turns it
 * on from a debug intent, never in a release). While [enabled], a hub draws
 * every section side by side at full section width under the title, in one
 * wide layout off the screen's edge, and saves it as
 * `files/press/<title>.png` in the app's external files folder after
 * [delayMillis], when its data has loaded.
 */
object PanoramaCapture {
    @Volatile var enabled = false
    @Volatile var delayMillis = 6_000L
}

@Composable
internal fun PanoramaCaptureLayout(
    title: String,
    sections: List<String>,
    tokens: ColorTokens,
    titleStyle: TextStyle,
    sectionWidth: Dp,
    margin: Dp,
    modifier: Modifier,
    belowTitle: @Composable ColumnScope.() -> Unit,
    section: @Composable (index: Int) -> Unit,
) {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    Box(modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.Start, unbounded = true)) {
        Column(
            modifier = Modifier
                .requiredWidth(sectionWidth * sections.size)
                .fillMaxHeight()
                .drawWithContent {
                    layer.record {
                        drawRect(tokens.bg)
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(layer)
                },
        ) {
            Text(
                text = title,
                style = titleStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = margin - 4.dp, top = 6.dp),
            )
            belowTitle()
            Row(modifier = Modifier.weight(1f)) {
                sections.forEachIndexed { index, name ->
                    Column(modifier = Modifier.width(sectionWidth).fillMaxHeight()) {
                        Text(
                            text = name,
                            color = tokens.fg,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = margin, top = 2.dp, bottom = 8.dp),
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { section(index) }
                    }
                }
            }
        }
    }
    LaunchedEffect(title) {
        delay(PanoramaCapture.delayMillis)
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        withContext(Dispatchers.IO) {
            val dir = File(context.getExternalFilesDir(null), "press").apply { mkdirs() }
            File(dir, "${title.replace(Regex("[^\\p{L}\\p{N}]+"), "_")}.png").outputStream().use {
                bitmap.copy(Bitmap.Config.ARGB_8888, false).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
