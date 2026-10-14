package com.tileshell.feature.start

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tileshell.core.design.ColorTokens

/** True when the photo at [uri] is wider than it is tall (read from its header, no pixels decoded). */
internal fun isWideImage(context: Context, uri: String): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
    bounds.outWidth > bounds.outHeight
}.getOrDefault(false)

/**
 * Asked when a landscape photo is set while Start has more than one page: show its
 * landscape version, scrolling across the pages, or the portrait crop on each page.
 * Same two-card shape as the wallpaper target chooser.
 */
@Composable
internal fun WallpaperSpanChoiceDialog(
    tokens: ColorTokens,
    onChoose: (span: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth(0.84f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.sheet),
            ) {
                Text(
                    text = "this photo is landscape",
                    color = tokens.fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                )
                Text(
                    text = "you have more than one start page",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                listOf(
                    "landscape · scrolls across the pages" to true,
                    "portrait · cropped on every page" to false,
                ).forEach { (label, span) ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.tileLine))
                    Text(
                        text = label,
                        color = tokens.fg,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(span) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "cancel",
                color = tokens.fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.sheet)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
            )
        }
    }
}
