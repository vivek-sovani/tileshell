package com.tileshell.feature.personalize

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.AppCategories
import com.tileshell.core.data.AppEntry
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/**
 * The "category folders" bottom sheet (personalize → folders). Slides up over a
 * fading scrim, same tokens/animation as [AboutSheet]. Two screens:
 *
 *  - **list** — the shipped [AppCategories] that match at least one *installed*
 *    app, each with its match count; tapping one opens
 *  - **review** — an editable folder name (default = the category label) plus the
 *    full installed-app list with the matched apps pre-checked, so the user can
 *    add near-misses or untick false positives before creating the folder.
 *
 * Stateless beyond the in-sheet navigation/selection: the host receives the final
 * (name, apps) via [onCreate] and writes the folder.
 */
@Composable
fun CategoryFolderSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    apps: List<AppEntry>,
    onCreate: (name: String, apps: List<AppEntry>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Returns the set of package names currently in a folder whose name matches
     * the given string (case-insensitive). Empty set means no such folder exists.
     */
    existingFolderPackages: (String) -> Set<String> = { emptySet() },
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "categoryFolderSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)

    // null = category list; non-null = reviewing that category id.
    var reviewId by remember { mutableStateOf<String?>(null) }
    // Reset navigation each time the sheet opens fresh.
    LaunchedEffect(visible) { if (visible) reviewId = null }

    // Android back: in review → go to list; on list → dismiss back to personalize.
    BackHandler(enabled = visible) {
        if (reviewId != null) reviewId = null else onDismiss()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.sheet)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Grip
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.fgDim.copy(alpha = 0.5f)),
            )

            val reviewCategory = reviewId?.let { id -> AppCategories.ALL.firstOrNull { it.id == id } }
            if (reviewCategory == null) {
                CategoryList(
                    apps = apps,
                    accent = accent,
                    tokens = tokens,
                    existingFolderPackages = existingFolderPackages,
                    onPick = { reviewId = it },
                    onBack = onDismiss,
                )
            } else {
                CategoryReview(
                    category = reviewCategory,
                    apps = apps,
                    accent = accent,
                    tokens = tokens,
                    existingPackages = existingFolderPackages(reviewCategory.label),
                    modifier = Modifier.weight(1f),
                    onBack = { reviewId = null },
                    // After creating/updating, return to the category list.
                    onCreate = { name, picked -> onCreate(name, picked); reviewId = null },
                )
            }
        }
    }
}

@Composable
private fun CategoryList(
    apps: List<AppEntry>,
    accent: Color,
    tokens: ColorTokens,
    existingFolderPackages: (String) -> Set<String>,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val counts = remember(apps) { AppCategories.categorize(apps).mapValues { it.value.size } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        // Back button row — returns to personalize main screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹ back",
                color = accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }

        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp)) {
            Text(
                text = "category folders",
                color = tokens.fg,
                fontSize = 28.sp,
                fontWeight = FontWeight.W200,
                letterSpacing = (-0.8).sp,
            )
            Text(
                text = "group your installed apps into a folder",
                color = tokens.fgDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.W300,
            )
        }

        HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))

        val available = AppCategories.ALL.filter { (counts[it.id] ?: 0) > 0 }
        if (available.isEmpty()) {
            Text(
                text = "no matching apps found on this device",
                color = tokens.fgDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            available.forEach { category ->
                val n = counts[category.id] ?: 0
                val folderExists = existingFolderPackages(category.label).isNotEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(category.id) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = category.label, color = tokens.fg, fontSize = 15.sp)
                    if (folderExists) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "exists",
                            color = accent,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (n == 1) "1 app" else "$n apps",
                        color = tokens.fgDim,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "›", color = accent, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, tokens: ColorTokens) {
    Text(
        text = text,
        color = tokens.fgDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun CategoryReview(
    category: AppCategories.Category,
    apps: List<AppEntry>,
    accent: Color,
    tokens: ColorTokens,
    /**
     * Package names already in an existing folder with this category's name.
     * Empty when no such folder exists (new folder flow).
     */
    existingPackages: Set<String>,
    modifier: Modifier,
    onBack: () -> Unit,
    onCreate: (name: String, apps: List<AppEntry>) -> Unit,
) {
    val isUpdate = existingPackages.isNotEmpty()

    val matched = remember(category.id, apps) { AppCategories.match(category.id, apps) }
    // Order: apps already in the folder first (so they can be removed), then the
    // category matches not yet in it, then every other installed app.
    val ordered = remember(category.id, apps, existingPackages) {
        val existing = apps.filter { it.packageName in existingPackages }
        val existingKeys = existing.mapTo(HashSet()) { it.key }
        val matchedKeys = matched.mapTo(HashSet()) { it.key }
        val matchedNew = matched.filter { it.key !in existingKeys }
        val rest = apps.filter { it.key !in existingKeys && it.key !in matchedKeys }
        existing + matchedNew + rest
    }
    // Number of leading rows that are already in the folder (for the section split).
    val existingCount = remember(ordered, existingPackages) {
        ordered.count { it.packageName in existingPackages }
    }
    val checked = remember(category.id, existingPackages) {
        mutableStateMapOf<String, Boolean>().apply {
            // Pre-check: new category matches + apps already in the existing folder.
            matched.forEach { put(it.key, true) }
            if (isUpdate) {
                apps.filter { it.packageName in existingPackages }
                    .forEach { put(it.key, true) }
            }
        }
    }
    var name by remember(category.id) { mutableStateOf(category.label) }
    val selectedCount = ordered.count { checked[it.key] == true }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header: back + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹ back",
                color = accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }

        // Editable folder name
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(text = "folder name", color = tokens.fgDim, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(color = tokens.fg, fontSize = 18.sp, fontWeight = FontWeight.W300),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(tokens.tileLine.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))

        // App checklist (scrolls; the create bar stays pinned below)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            ordered.forEachIndexed { index, app ->
                // Section headers (update flow only): "in this folder" above the
                // current members, "suggested" above the rest.
                if (isUpdate && index == 0 && existingCount > 0) {
                    SectionHeader("in this folder", tokens)
                }
                if (isUpdate && index == existingCount) {
                    SectionHeader("add more", tokens)
                }

                val isOn = checked[app.key] == true
                val inFolder = app.packageName in existingPackages
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checked[app.key] = !isOn }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Members in the folder show a removable ✕ (tap to remove);
                    // everything else shows a checkbox ✓ (tap to add).
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isOn) accent else Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = if (isOn) accent else tokens.fgDim,
                                shape = RoundedCornerShape(5.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isOn) {
                            Text(
                                text = if (inFolder) "✕" else "✓",
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = app.label.ifBlank { app.packageName },
                        color = tokens.fg,
                        fontSize = 14.sp,
                    )
                    // Marked-for-removal hint on a member toggled off.
                    if (inFolder && !isOn) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "will be removed",
                            color = tokens.fgDim,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // Pinned create bar
        HorizontalDivider(color = tokens.tileLine)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            val enabled = selectedCount > 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (enabled) accent else tokens.tileLine)
                    .then(
                        if (enabled) {
                            Modifier.clickable {
                                onCreate(name, ordered.filter { checked[it.key] == true })
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        !enabled -> "select at least one app"
                        isUpdate -> "update folder ($selectedCount)"
                        else -> "create folder ($selectedCount)"
                    },
                    color = if (enabled) Color.White else tokens.fgDim,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W500,
                )
            }
        }
    }
}
