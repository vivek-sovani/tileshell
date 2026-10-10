package com.tileshell.feature.livetiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tileshell.core.data.PanchangLanguage
import com.tileshell.core.design.HubFilter
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/**
 * "Panchang language": the one choice a panchang tile asks when it is added (names, numerals and the festivals
 * of that language's region follow it). Each language is written in its own script so it can be found without
 * reading the others; the panchang's settings page changes it later.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanchangLanguageDialog(
    dark: Boolean,
    accentId: String,
    current: PanchangLanguage,
    onPick: (PanchangLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.background(tokens.bg).padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text("panchang · पंचांग", color = TileAccents.Amber, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("language · भाषा", color = tokens.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "names, numerals and festivals follow it · you can change it in the panchang's settings",
                color = tokens.fgDim,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PanchangLanguage.entries.forEach { l ->
                    HubFilter(l.nativeName, l == current, tokens, accent) { onPick(l) }
                }
            }
        }
    }
}
