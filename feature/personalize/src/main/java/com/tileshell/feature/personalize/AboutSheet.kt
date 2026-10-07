package com.tileshell.feature.personalize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.KeyboardFeature
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

@Composable
fun AboutSheet(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onVersionTap: () -> Unit = {},
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "aboutSheetProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrDefault("0.9")
    }

    // Android back / back-gesture closes the sheet (it has no on-screen close).
    BackHandler(enabled = visible) { onDismiss() }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
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
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.sheet)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
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

            // Header — app name + version
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            ) {
                TileLogoMark()
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "tileshell",
                    color = tokens.fg,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.W200,
                    letterSpacing = (-1.2).sp,
                )
                Text(
                    text = "windows mobile-style launcher",
                    color = tokens.fgDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W300,
                    letterSpacing = 0.sp,
                )
                Spacer(Modifier.height(10.dp))
                // Tapping the version re-opens "what's new" on demand (user-
                // requested), rather than only ever showing once after an
                // actual update — a way back in once it's already been seen.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onVersionTap,
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "v$version",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W500,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))

            // ── USER FEATURES ──────────────────────────────────────────────
            SectionHeader("what you can do", tokens.fgDim)

            FeatureGroup(
                title = "start screen",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "first launch walks you through a short setup: tiles or icons, then theme (dark, light, or auto), tile colour (one colour or multicolour), and default or custom apps",
                    "custom apps is one list with the default apps already ticked — untick what you don't want, tick anything else you'd like on start; the hubs and live tiles are always included",
                    "the default start is built around the hubs, colour-grouped by purpose, with an \"essentials\" folder of everyday apps (youtube, google, chrome, calculator, files)",
                    "choose a home style: \"tiles\" for the classic windows-phone look, or \"icons\" for a normal android-style grid of shaped icons — revisit it any time in personalize · home style",
                    "in icons mode, small tiles render as shaped icons — circle, squircle, rounded, square, or original (your device's own, unmasked shape); live tiles, folders, and widget stacks look the same as in tiles mode",
                    "\"monochrome icons\" (personalize · home style) turns every app icon into a flat glyph — nothing-phone style — across start, the app list, folder mini-grids, and live-tile notification badges; choose whether the glyphs tint to your accent colour or a fixed black/white",
                    "growing an icon past its smallest size turns it into a live tile; shrinking one back down turns it back into an icon",
                    "tap a tile's resize handle to cycle small → medium → wide → large, or drag its corner to resize freely across 11 sizes, from a tiny icon up to a full 4×4 tile",
                    "large (3×3) tile available for any app, on any column count",
                    "choose grid density — 4, 5, or 6 tiles across a row (5 by default)",
                    "\"all apps →\" under your tiles opens the app list, just like windows phone",
                    "pin a default app (phone, mail, calendar…) from the app list with its real icon, alongside its built-in live tile",
                    "\"already on start\" tells you which page the tile is on and takes you there",
                    "choose how tiles arrange when one's removed or resized: \"sticky\" leaves the gap open, \"free\" lets you place tiles anywhere with nothing auto-moving (icons mode's default), or \"dense\" auto-packs everything tight",
                    "long-press any tile to enter edit mode",
                    "drag to reorder, resize, or unpin tiles",
                    "drag a tile into the empty space below to send it to the bottom",
                    "a moving tile reorders cleanly; pause it over another to merge",
                    "merge two same-size tiles by lining them up centre to centre",
                    "unread badge counts on app tiles",
                    "landscape mode shows feed and start side by side",
                    "hide apps you don't want cluttering the app list — bring them back any time from personalize",
                    "apps with a pending notification show up in the app list's recent section, even if they're not pinned to start",
                    "the settings tile opens home settings; it's locked in place so it can't be unpinned by accident",
                    "\"add live tiles\" brings back built-in tiles you removed — people, photos, mail, messages and the rest",
                    "start is organized into pages — swipe left/right between them; the first one is always \"main\", for anything you haven't grouped, with your named pages after it",
                    "a small dot row at the top shows how many pages there are and which one you're on, whenever there's more than one",
                    "in edit mode, tap the \"+\" at the top and name it to add a page; each page's own name (otherwise hidden) appears there too, with ←/→ to reorder it",
                    "while editing a named page (not main), a \"remove\" control in the top-right corner offers \"merge with main\" (ungroups its tiles back into main, nothing lost) or \"remove page & tiles\" (unpins the page and everything on it at once, with a confirmation first — apps stay installed)",
                    "pinning an app (or one of its shortcuts, via \"more from this app\") always lands it on whichever page you were last viewing — no picker to answer",
                    "drag a tile to the left or right edge of the screen to carry it onto a neighboring page — the page shifts right away and a floating preview of the tile lets you keep aiming before you let go",
                    "already-pinned tile? select it in edit mode, tap its colour dot, and use \"move to page\" at the bottom of the colour picker to move it without dragging",
                    "on a page with room below its tiles, drag down from that empty space to pull them closer to your thumb; tap one to open it, or tap elsewhere to let go",
                ),
            )

            FeatureGroup(
                title = "hubs",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "tap a weather, music, calendar, people, productivity, battery, money, markets, or sports hub tile to open its full-screen hub; swipe between each hub's pages, windows-phone style",
                    "music, people, productivity and money each have an apps page gathering the related apps on your phone in one place",
                    "weather — now, hourly, and daily forecast; max/min, feels like, wind, humidity, uv index, sunrise and sunset; a moon at night",
                    "music — your own music library with albums, playlists (create and edit real device playlists), shuffle, and background playback; podcasts and internet radio with favourites and recents; history of what you played in other apps",
                    "android auto — your music, playlists, favourite podcasts (with their episodes) and favourite radio stations show on the car screen, with recently played under each; ask the assistant to play a show, station, artist or song on tileshell",
                    "people, money and productivity apps pages: tap edit to add any installed app (pinterest, cred…) to a section, hold and drag an app to another section, or tap ✕ to take it off the page; added apps count for what's new and money tracking",
                    "music pauses by itself when bluetooth or headphones disconnect, and a headset's play, pause, next and previous buttons control it",
                    "now playing has a progress bar you can tap or drag, jumps of 10 seconds back and 30 forward for tracks and podcasts, and share in the corner of the favourites line — a track as its file, a podcast episode or radio station as its link",
                    "gapless playback for your own music, with an overlap of 1, 2 or 3 seconds so tracks cross-fade instead of pausing between them",
                    "next and previous on a radio station step through your favourite stations; a station that drops out stays selected, paused, until you play it again",
                    "calendar — your upcoming events by day; tap one to open it",
                    "people — all your contacts with quick call, message, and view; \"what's new\" gathers chats, messages, mail, and social notifications, with filters for each app",
                    "in what's new, tap a message to open its app, tap the small arrow to expand it and reply inline, mark read, or archive, and swipe it sideways to dismiss; bank and card messages stay out of it — they belong to money",
                    "people's favourites — your starred contacts, then who recently messaged you on whatsapp, sms and other chat apps, remembered by tileshell on this phone for 30 days",
                    "favourites in your own order (\"arrange\"), with recently messaged on its own switch; mark who shows on the pinned favourites tile with \"on tile\"; tap a person on the tile for call, message, their chat app or contact; \"+ n more\" opens the full list",
                    "people's apps page groups your chat, messages, mail, and social apps, most-used first (needs usage access) — pin it as a tile with inbox apps on the front and social apps on the back",
                    "productivity — today (next meeting with a join button, open tasks, latest note, quick actions), notes with optional titles, named task lists, and office, meeting and tool apps, most-used first",
                    "productivity's quick row is yours to arrange — long-press a note, list, or app to add it, long-press a shortcut to remove it; links to calendar and what's new sit there too",
                    "pin any note or task list from the hub as its own start tile",
                    "task reminders — tap the bell on a task for a date, time and repeat (daily, weekly, monthly, yearly or every few days); ticking a repeating task moves it to its next date",
                    "a reminder rings with tileshell's own chime: a banner over other apps, or a toast across the top while start is showing, with done and snooze; the tasks tile lists due tasks first and shows the next one, and productivity's today lists what's scheduled",
                    "on-time reminders need android's \"alarms & reminders\" access — without it, the task says its reminder is off",
                    "battery — today's charge curve with screen on and off, a week's history, per-app screen time, and details like temperature, health, and current",
                    "money — your bank and upi transactions, read from new bank sms and payment-app notifications and kept only on this phone, with monthly spent and received, filters per account, upi and cards, and the last balance your bank sent",
                    "money's transactions are locked behind your fingerprint, face or screen lock; its apps page lists your payment, banking, and credit and debit card apps, and the tile can show your last payment and receipt, with payment apps on the back",
                    "money splits transactions into accounts & upi and cards; cards have spends, bills due and payments — a statement and its reminders are one bill, listed by due date and marked paid once a card payment comes in; paying a card bill never counts as spending",
                    "in money's transactions, tap one to read the full message, swipe it away to remove it (with undo), or clear them all",
                    "markets — your watchlist split by stocks, commodities, currencies and crypto (add by search, edit to remove), the big indices with a day line, and the biggest movers among the sector baskets; tap the box beside a stock, commodity, currency or index to put it on its tile, and pin a stocks, commodities, currencies or markets (indices) tile from the pin button — each tile shows just what you marked",
                    "sports hub — two pages, live (today's matches: in play, still to come with the time, and finished) and results (last week or month), each for my teams or for all of my sports; the + in its bar picks any number of sports and teams",
                    "tap a match in the sports hub for its status and scorecard — batters at the crease, bowling figures and the scorecard in cricket, events and stats in football, a box score in basketball and the rest — with a link to espn for live commentary; \"tile\" in the picker makes a tile for a team",
                    "the sports hub only updates when you open it or press refresh in its bar (the sports tile on start refreshes itself); the markets hub refreshes while open, at your live data refresh rate, and rests when markets are shut",
                ),
            )

            FeatureGroup(
                title = "quick search",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "swipe up with two fingers on start to open quick search — or swipe up from either screen edge with one finger",
                    "slides up from the bottom, search box at the bottom of the screen — closer to your thumb",
                    "search apps, contacts, and the web from one box",
                    "before you type: jump back into a recent search or a frequently-used app",
                    "tap a contact to open their contact card",
                    "long-press a contact result to call, message, or pin them to start",
                    "pinned contacts show their real photo on the tile",
                    "ask chatgpt, gemini, claude, or perplexity — opens the app with your query ready to send",
                    "or send it to google, bing, duckduckgo, yahoo, or yandex instead",
                    "tap the search box on the feed page to jump straight into quick search",
                    "taps on results, recent searches, and the clear button give a light haptic buzz",
                ),
            )

            FeatureGroup(
                title = "quick panel",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "swipe down with two fingers on start to open the quick panel — or swipe down from the right screen edge with one finger",
                    "docks to and slides down from the top of the screen, like a real device's quick settings panel",
                    "background is a colour gradient synthesized from start's own wallpaper, same as the feed page — tile and slider colours switch to match it too",
                    "clock and date on the left of the header; wifi, bluetooth, cellular, and battery status on the right, just like the status bar you can hide",
                    "wifi and bluetooth icons brighten (dim when off) same as the cellular icon; airplane mode replaces the cellular icon with a plane",
                    "battery icon fills proportionately to the charge level and turns red/amber/green as it gets low",
                    "personalize, android settings, and lock screen icons sit in a second row under the status row",
                    "a grid of true square tiles, four across, just like windows phone's action center",
                    "wp-style coloured tiles toggle wi-fi, bluetooth, flashlight, dnd, airplane mode, location, and rotation lock",
                    "tap the pencil icon at the top of the panel to edit the grid — every tile shows a move handle (top) and a resize handle (right edge) at once, no long-press needed",
                    "drag a tile's move handle to reorder it, or drag its resize handle to switch it between square and wide",
                    "tap the checkmark where the pencil was to finish editing",
                    "brightness, ring volume, and media volume are real drag sliders below the tile grid",
                    "tap the bell or speaker icon on the ring/media sliders to mute or unmute",
                    "screen timeout tile cycles through presets with a tap",
                    "a single theme tile cycles dark → light → auto, tinted with your accent colour",
                    "drag the handle at the bottom of the panel upward to close it, or tap outside it",
                    "taps and gestures throughout the panel give a light haptic buzz",
                ),
            )

            FeatureGroup(
                title = "system shortcuts",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "swipe down from the left screen edge to open the system notification shade",
                    "swipe down from the right screen edge to open this app's own quick panel",
                    "swipe up from either screen edge to open quick search",
                    "works from anywhere on start, not just the very top of the screen",
                    "uses the same accessibility service as screen lock — enable it once from the prompt",
                ),
            )

            FeatureGroup(
                title = "edge strip",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "a quick-launch bar that lives at the bottom of the screen",
                    "search shortcut on the left, recents on the right",
                    "your pinned app shortcuts sit centred in between",
                    "tap an app to launch it; unread badge shows pending notifications",
                    "collapses to a thin sliver — tap the handle to expand or collapse",
                    "configure handle size (thin / thick) and background from personalize",
                    "pin or remove apps from the strip in personalize → edge strip",
                ),
            )

            FeatureGroup(
                title = "folders",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "merge two tiles together to create a folder",
                    "auto-create category folders from your installed apps",
                    "tap an app icon inside a folder tile to launch it directly",
                    "folder shows overflow count (+N) when more than 4 apps are inside",
                    "resize, reorder, and remove tiles inside an open folder",
                    "give a tile inside a folder its own colour, just like on start",
                    "drag an app out of a folder to place it exactly where you drop it on start — drop onto another tile to merge into it instead",
                    "tap × on an app inside a folder to unpin it straight to the end of start, without dragging",
                    "select a folder and tap its colour dot for two whole-folder actions: \"unfold folder\" dissolves it while keeping every app pinned to start, and \"remove folder & tiles\" unpins the folder and everything inside it at once (confirmed first — apps stay installed either way)",
                    "folder shows a combined badge; each app shows its own inside",
                    "music keeps playing controls and album art live inside a folder",
                ),
            )

            FeatureGroup(
                title = "widget stacks",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "merge two large tiles to create a widget stack directly",
                    "any existing folder with 2 or more children can become a stack too — select it, tap its colour dot, and use the \"show as stack\" toggle at the bottom of the colour picker",
                    "\"show as folder\" sits in that same spot once it's already a stack, so reverting is just as easy",
                    "works at any size wider than one column — medium, wide, large, wide small, banner, and the other roomier drag-resize sizes; only small and the single-column tall/column presets are too thin and excluded",
                    "drag a stack's corner to resize it — every member homogenizes to the new size at once",
                    "stack shows each member's full live tile — clock, music, notifications",
                    "each member keeps its own tile colour while the stack rotates",
                    "each member shows its own notification badge as it rotates into view",
                    "swipe up or down near the right edge to flip through stack members instantly",
                    "swipe anywhere else on the stack to scroll the start screen as normal",
                    "stack auto-rotates every 10 s; each stack runs on its own independent schedule",
                    "tap the stack to launch the current member's app",
                    "select a stack in edit mode and tap its folder icon to expand it in place and manage members",
                    "tap × on a member to send it back to start",
                    "live faces (music transport, clock flip) stay fully interactive inside a stack",
                ),
            )

            FeatureGroup(
                title = "feed & news",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "swipe right from start to open the feed",
                    "a personalized greeting, live clock, weather, calendar events, now-playing, widgets, and news, all in one continuous scroll",
                    "greeting auto-fills your name from your contact profile if available — edit or clear it any time in personalize",
                    "tap the calendar card to open your calendar app",
                    "tap the now-playing card to open the music app",
                    "select any number of news regions — india plus ~20 other countries",
                    "region defaults to your device's country automatically on first launch",
                    "add and manage your own custom RSS / Atom feeds",
                    "8 categories: nation, tech, sports, cricket, business and more",
                    "give the feed its own flat background, independent of start's wallpaper, from personalize",
                ),
            )

            FeatureGroup(
                title = "widgets",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "add any android app widget to the feed page",
                    "widget picker is grouped by app, and each app's group can be collapsed",
                    "tileshell widget settings open full screen in portrait, and over the glance half in landscape",
                    "tileshell's own widgets sit at the top of the picker, ahead of every other app",
                    "search the picker by app name or widget name to jump straight to one",
                    "narrow widgets automatically pair up side by side, like weather and today's agenda",
                    "weather, agenda, and now-playing share this same system too — resize and reorder them exactly like any hosted widget",
                    "tap \"edit\" next to \"widgets\" to turn on move/resize handles for every card at once — hosted widgets and weather/agenda/now-playing together, no more tapping into each one individually",
                    "drag a card's move handle (top) to reorder it",
                    "drag its edge handle to resize width or height independently, or drag the corner to resize both at once",
                    "tap \"done\" where \"edit\" was to finish",
                    "tileshell's own widgets: stock market, commodities & currencies, sports scores, extra calendar systems (including hindu panchang), countdown, sticky note, notes, tasks, battery, alarm, moon phase, flashlight, and steps — real, standalone widgets, placeable on any launcher, not just here",
                    "adding a weather widget asks for current location or a picked place — each weather widget you add can track its own location, independently of any other",
                    "stock, sports, and weather widgets have their own refresh button, with a quick flash and pulse so you can see the tap registered",
                    "stock and sports widgets refresh faster on their own while the market's open or a match is live",
                ),
            )

            FeatureGroup(
                title = "live tiles",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "clock — live time, weekday, and date; flips to alarm",
                    "weather — real forecasts via open-meteo, no account needed",
                    "weather as a small tile shows the current temperature",
                    "adding a weather tile asks for current location or a picked place — pin more than one, each tracking somewhere different",
                    "calendar — today's date always visible; flips to upcoming events",
                    "music — now-playing, album art, and transport controls",
                    "people — rotating mosaic of your contacts' photos",
                    "photos — cross-fading slideshow of photos you pick",
                    "photos tile flips to show a gallery notification when one's pending",
                    "mail & messages — latest sender and message snippet",
                    "any app tile shows its latest notification as a preview",
                    "wide and large tiles show more notification content — bigger photos, more lines",
                    "tiles with multiple notifications cycle through them one at a time on the flip side",
                    "clock back face leads with your next alarm time and names the app that set it; date shown below",
                    "weather shows a moon at night, and sunrise and sunset on the back",
                    "battery — drain rate, time left, and today's curve, with screen on/off on the back; recorded by tileshell itself every 15 minutes",
                    "productivity — next meeting, open tasks, and your latest note",
                    "hindu panchang — tithi as a big number with paksha, vara, masa, and sunrise/sunset plus moonrise/moonset, fitted to every tile size",
                    "panchang highlights the day in an amber strip on the tile and widget: sankashti (angaraki on a tuesday, with moonrise), ekadashi by name — smarta and vaishnava when they fall on different days — mahashivaratri, and any tithi you pick",
                    "panchang shows hindu festivals and solar and lunar eclipses (grahan), with sparsha and moksha times for where you are, or 'not visible here'",
                    "tap the panchang tile or widget for the panchang sheet, all in devanagari: today, this month and this year, with important days, festivals and grahan in separate sections, and a page to choose what's highlighted",
                    "what's new and people's apps pin as their own tiles",
                ),
            )

            FeatureGroup(
                title = "personalization",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "14 accent colours to choose from",
                    "per-tile colour — give any tile its own colour in edit mode",
                    "tile colour from app icon — auto-picks the dominant colour",
                    "tile colour from wallpaper — tints every tile with the same wallpaper-derived accent the feed page and quick panel already use",
                    "multicolour — every tile keeps its own colour, grouped by purpose, like the default start",
                    "dark, light, or follow-system theme — tap one of the three theme tiles at the top of personalize",
                    "the device's real settings app is one tap away, right at the top of personalize",
                    "glass (transparent) tiles with adjustable transparency",
                    "widget cards — every tile becomes its own rounded, translucent card floating over the wallpaper, like a home-screen gadget",
                    "widget cards carries onto the feed page too — weather, today, and now-playing match the same translucent card style",
                    "and onto quick panel — an active toggle shows in accent colour on its icon and label, since every tile now shares the same translucent card",
                    "quick panel's sliders match too — a frosted translucent track, not a solid bar",
                    "tile outline toggle — keep a glass / show-through tile's fill but drop its edge line",
                    "adjustable tile spacing — drag a slider to pack or spread tiles",
                    "6 built-in gradient wallpapers + custom photo",
                    "wallpaper slideshow — rotate through photos you pick, every 15 min to 3 hours",
                    "pinch to zoom, drag to reposition when framing a wallpaper photo",
                    "daily bing wallpaper, with a viewer for recent days",
                    "wallpaper visible through tiles (show-through mode)",
                    "after picking a wallpaper, optionally set it as your device's real home and/or lock screen wallpaper too, framed exactly as positioned",
                    "tile corner radius slider and gradient fill option",
                    "font style: outfit (default), nunito, or system",
                    "grid columns — pack 4, 5, or 6 tiles into a row",
                    "edge strip — choose which apps appear, handle style, and background",
                    "lock layout — long-pressing a tile stops opening edit mode, so nothing moves by accident",
                    "set tileshell as your default launcher right from personalize's system group",
                    "turn live tiles off as one master switch — clock/weather/notification flipping pauses, badges and counts keep updating",
                    "live data refresh — set how often weather, news, stocks, commodities, and sports update, from their default up to every 3 hours; home-screen widgets follow too",
                    "permissions — every permission in one list, what it's for, and whether it's on; tap one to allow it or open its android setting",
                    "enabling live tiles for the first time explains and asks before opening notification access, instead of jumping straight there",
                    "give the feed page its own \"no background\" option, independent of start's wallpaper",
                ),
            )

            if (KeyboardFeature.ENABLED) {
                FeatureGroup(
                    title = "tileshell keyboard (beta)",
                    accent = accent,
                    tokens = tokens,
                    items = listOf(
                        "a metro-style keyboard in your accent colour and theme — turn it on in personalize · system · tileshell keyboard",
                        "suggestions, autocorrect that learns your words, and wordflow — swipe across letters to type",
                        "next-word suggestions from what you've just typed and your own habits — \"good\" offers morning, afternoon, evening or night by the time of day",
                        "type मराठी or हिन्दी two ways: spell it in english letters (\"namaskar\" → नमस्कार, \"energy\" → एनर्जी), or use devanagari keys in varnamala order — the globe key steps through english, मराठी and हिन्दी",
                        "emoji with search, and the emoji for the word you type in the strip (\"pizza\" → 🍕), clipboard history, a number pad for number fields, one-handed mode, and hold space to move the cursor",
                        "everything you type, learn or copy stays on your phone — the keyboard never connects to the internet",
                        "beta: marathi and hindi typing is still improving",
                        "word lists from the android open source project (apache 2.0), tatoeba (cc-by 2.0 fr, also for next words), mozilla common voice (cc0) and marathi and hindi wikipedia (cc by-sa 4.0); english words in मराठी / हिन्दी spelling from the cmu pronouncing dictionary (bsd)",
                    ),
                )
            }

            FeatureGroup(
                title = "backup, restore & reset",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "auto-save your layout on a schedule — every 6h, 12h, or daily",
                    "layout history — browse and restore up to 10 past layouts with a visual preview",
                    "save a snapshot manually any time from personalize",
                    "export your layout, settings, notes, tasks and music favourites to a file",
                    "auto-export to a folder: pick a folder once (google drive works) and a dated backup is saved there daily or weekly; the newest 5 are kept",
                    "save that exported file to google drive so it's there on your next device",
                    "restore from a file to bring a layout back, on this device or a new one",
                    "reset start layout runs the setup again from the first step — style, theme, colour, and apps",
                ),
            )

            FeatureGroup(
                title = "screen lock",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "tap the \"lock screen\" tile in the quick panel to lock the device",
                    "double-tap an empty area of start to lock it too",
                    "preserves biometric unlock on android 9 and above",
                ),
            )

            FeatureGroup(
                title = "accessibility",
                accent = accent,
                tokens = tokens,
                items = listOf(
                    "full talkback support with labels and custom actions",
                    "48dp minimum touch targets throughout",
                    "respects system font scale, display cutouts, and rtl layouts",
                    "animations pause when system animations are disabled",
                    "live tiles pause in battery saver mode",
                ),
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = tokens.tileLine, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(16.dp))

            Text(
                text = "© 2026 vivek sovani",
                color = tokens.fgDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/**
 * Compact 2×2 tile grid mark representing the launcher. Public (not private) so
 * the feed's glance page and Personalize sheet can reuse it as a small
 * branding mark. Colours are the real launcher icon's own four fixed brand
 * accents (see `app/src/main/res/drawable/ic_launcher_foreground.xml`) —
 * deliberately fixed, not derived from the user's chosen tile accent, so this
 * mark reads as "the actual app icon" regardless of personalization
 * (user-requested: "it should be colorful just like app icon").
 */
@Composable
fun TileLogoMark(modifier: Modifier = Modifier, cell: Dp = 18.dp, gap: Dp = 3.dp) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.size(cell).background(Color(0xFF2B78E4)))
            Box(Modifier.size(cell).background(Color(0xFFE2A200)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.size(cell).background(Color(0xFFC4287E)))
            Box(Modifier.size(cell).background(Color(0xFF1F9E57)))
        }
    }
}

@Composable
internal fun SectionHeader(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
}

@Composable
internal fun FeatureGroup(
    title: String,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    items: List<String>,
    visual: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
        Text(
            text = title,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (visual != null) {
            visual()
            Spacer(Modifier.height(10.dp))
        }
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "·",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 8.dp, top = 1.dp),
                )
                Text(
                    text = item,
                    color = tokens.fg,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun DevInfoGroup(
    tokens: com.tileshell.core.design.ColorTokens,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.tileLine.copy(alpha = 0.3f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        content()
    }
}

@Composable
private fun DevRow(label: String, value: String, tokens: com.tileshell.core.design.ColorTokens) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tokens.fgDim,
            fontSize = 12.sp,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            color = tokens.fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.W400,
        )
    }
}
