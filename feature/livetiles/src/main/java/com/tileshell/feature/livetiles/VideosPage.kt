package com.tileshell.feature.livetiles

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.HubFilter
import com.tileshell.core.design.TileIcons

private enum class VideoFilter { RECENT, ALL }

/**
 * The music + videos hub's "videos" page: videos in folders the user chose
 * with the system folder picker (so no storage permission is involved and
 * only those folders are ever read), newest first, plus the ones opened from
 * here. A tap opens the video in the phone's own video player
 * ([LocalVideoLibrary.open]); TileShell has no player of its own for video, so
 * it cannot know how far a video was watched.
 */
@Composable
internal fun VideosPage(context: Context, accent: Color, tokens: ColorTokens) {
    var folders by remember { mutableStateOf(VideoPrefs.folders(context)) }
    var recents by remember { mutableStateOf(VideoPrefs.recents(context)) }
    var filterChoice by remember { mutableStateOf<VideoFilter?>(null) }
    val filter = filterChoice ?: if (recents.isNotEmpty()) VideoFilter.RECENT else VideoFilter.ALL

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            VideoPrefs.addFolder(context, tree)
            folders = VideoPrefs.folders(context)
        }
    }

    // Box so the page is one root node (the pager hit-tests a lone root per page).
    Box(modifier = Modifier.fillMaxSize()) {
        if (folders.isEmpty()) {
            ChooseFoldersGate(accent, tokens) { pickFolder.launch(null) }
            return@Box
        }
        val videos by produceState<List<LocalVideo>?>(initialValue = null, folders) {
            value = LocalVideoLibrary.scan(context, folders)
        }
        val shown = remember(videos, recents, filter) {
            val all = videos ?: return@remember null
            when (filter) {
                VideoFilter.ALL -> all
                VideoFilter.RECENT -> {
                    val byUri = all.associateBy { it.uri.toString() }
                    recents.mapNotNull { byUri[it.uri] }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HubFilter("recent", filter == VideoFilter.RECENT, tokens, accent) { filterChoice = VideoFilter.RECENT }
                    HubFilter("all", filter == VideoFilter.ALL, tokens, accent) { filterChoice = VideoFilter.ALL }
                }
            }
            when {
                shown == null -> item(span = { GridItemSpan(maxLineSpan) }) { VideoNote("looking for videos…", tokens) }
                shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    VideoNote(
                        if (filter == VideoFilter.RECENT) "videos you open from here show up here"
                        else "no videos found in your folders",
                        tokens,
                    )
                }
                else -> items(shown, key = { it.uri.toString() }) { video ->
                    VideoCell(video, tokens) {
                        LocalVideoLibrary.open(context, video)
                        recents = VideoPrefs.recents(context)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FoldersSection(context, folders, accent, tokens,
                    onAdd = { pickFolder.launch(null) },
                    onRemove = { tree ->
                        VideoPrefs.removeFolder(context, tree)
                        folders = VideoPrefs.folders(context)
                    },
                )
            }
        }
    }
}

@Composable
private fun ChooseFoldersGate(accent: Color, tokens: ColorTokens, onChoose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("videos on your phone", color = tokens.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "choose the folders your videos are in. tileshell reads only those folders, asks for no storage permission, and opens each video in your phone's video player. nothing leaves your device.",
            color = tokens.fgDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .background(accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onChoose,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("choose folders", color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun VideoNote(text: String, tokens: ColorTokens) {
    Text(text, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun VideoCell(video: LocalVideo, tokens: ColorTokens, onClick: () -> Unit) {
    val context = LocalContext.current
    val meta by produceState<VideoMeta?>(initialValue = null, video.uri) {
        value = LocalVideoLibrary.meta(context, video)
    }
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(6.dp))
                .background(tokens.chip),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = meta?.thumb
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(TileIcons["video"], contentDescription = null, tint = tokens.fgDim, modifier = Modifier.size(32.dp))
            }
            val duration = meta?.durationMs ?: 0L
            if (duration > 0) {
                Text(
                    formatVideoDuration(duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color(0xA6000000))
                        .padding(horizontal = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            video.name.substringBeforeLast('.'),
            color = tokens.fg,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(video.folder, color = tokens.fgDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FoldersSection(
    context: Context,
    folders: List<Uri>,
    accent: Color,
    tokens: ColorTokens,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text("folders", color = tokens.fg, fontSize = 18.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(4.dp))
        folders.forEach { tree ->
            val label = remember(tree) { LocalVideoLibrary.folderLabel(context, tree) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(label, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    "remove",
                    color = accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onRemove(tree) },
                        )
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        Text(
            "add a folder",
            color = accent,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAdd,
                )
                .padding(vertical = 8.dp),
        )
    }
}
