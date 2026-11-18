package com.yuno

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import com.yuno.icons.SfIcons

// ── Home / Subscriptions / Library ─────────────────────────────────────────────
// Signed in, these show the account's feeds. Signed out, Home still shows recommendations;
// Subscriptions and Library explain what signing in brings.

@Composable
fun HomeScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
    pullState: BarPullRefreshState? = null,
) {
    // Guest or signed in: same Home feed. Signed out uses an anonymous visitor; sign-in swaps
    // to the account's recommendations after [LibraryData.reset].
    val signedIn = Account.signedIn
    LaunchedEffect(signedIn) { lib.home.ensureLoaded() }
    val chips = lib.home.chips
    if (chips.size > 1) {
        HomeChipPager(lib, chips, contentPadding, onOpen, scrollToTop, pullState)
    } else {
        val listState = lib.home.scroll
        // The bar itself is the refresh control. Other feeds still use the circular indicator.
        BarPullRefresh(refreshing = lib.home.refreshing, onRefresh = lib.home::refresh, state = pullState) {
            Box(Modifier.fillMaxSize()) {
                FeedList(
                    lib.home,
                    contentPadding.withTop(pageContentTop()),
                    onOpen,
                    header = { item(key = "page-title") { LargePageBrandMark() } },
                    scrollToTop = scrollToTop,
                    pullToRefresh = false,
                    listState = listState,
                )
                PageTopFade(listState)
            }
        }
    }
}

/**
 * Home's categories, paged like a channel: the wordmark and the tabs stay put while the
 * lists swipe, and they scroll away with whichever list is on screen.
 */
@Composable
private fun HomeChipPager(
    lib: LibraryData,
    chips: List<FeedChip>,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    scrollToTop: Int,
    pullState: BarPullRefreshState?,
) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(
        initialPage = lib.homePage.coerceIn(0, (chips.size - 1).coerceAtLeast(0)),
    ) { chips.size }
    fun openedScroll(chip: FeedChip) = lib.feedIfOpen(chip)?.scroll
    // A sideways move stays level. The page sliding in is put at the same scroll as the one
    // you left, including when that one is scrolled down past the wordmark.
    var capturedFrom by remember { mutableIntStateOf(-1) }
    var anchorIndex by remember { mutableIntStateOf(0) }
    var anchorOffset by remember { mutableIntStateOf(0) }
    SideEffect {
        val swiping = pager.isScrollInProgress
        if (!swiping) {
            capturedFrom = -1
            return@SideEffect
        }
        val fromPage = pager.settledPage.coerceIn(0, chips.lastIndex)
        if (capturedFrom != fromPage) {
            capturedFrom = fromPage
            val from = chips.getOrNull(fromPage)?.let { openedScroll(it) } ?: return@SideEffect
            anchorIndex = from.firstVisibleItemIndex
            anchorOffset = from.firstVisibleItemScrollOffset
        }
        chips.forEachIndexed { index, chip ->
            if (index == fromPage) return@forEachIndexed
            val list = openedScroll(chip) ?: return@forEachIndexed
            if (list.firstVisibleItemIndex != anchorIndex || list.firstVisibleItemScrollOffset != anchorOffset) {
                list.requestScrollToItem(anchorIndex, anchorOffset)
            }
        }
    }
    // The tab you stop on glides back to the top. A second swipe cancels the one in between,
    // so only the page you actually land on keeps scrolling.
    LaunchedEffect(chips) {
        var seen = pager.settledPage
        snapshotFlow { pager.isScrollInProgress to pager.settledPage }
            .collectLatest { (moving, page) ->
                if (moving || page == seen) return@collectLatest
                seen = page
                lib.homePage = page
                val chip = chips.getOrNull(page) ?: return@collectLatest
                val list = openedScroll(chip) ?: return@collectLatest
                if (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) return@collectLatest
                list.animateScrollToItem(0)
            }
    }
    val follow = if (pager.isScrollInProgress) pager.settledPage else pager.currentPage
    val followChip = chips.getOrNull(follow.coerceIn(0, chips.lastIndex))
    val active = chips.getOrNull(pager.settledPage.coerceIn(0, chips.lastIndex))
    val activeFeed = if (active == null || active.token == null) lib.home else lib.category(active)
    var headerPx by remember { mutableIntStateOf(0) }
    val followList = followChip?.let { if (it.token == null) lib.home.scroll else lib.category(it).scroll }
    val scrollPx = when {
        followList == null || headerPx <= 0 -> 0
        followList.firstVisibleItemIndex > 0 -> headerPx
        else -> followList.firstVisibleItemScrollOffset.coerceAtMost(headerPx)
    }
    val top = pageContentTop()
    BarPullRefresh(refreshing = activeFeed.refreshing, onRefresh = activeFeed::refresh, state = pullState) {
        // The list is composed only after the header is measured, so its spacer is never 0.
        // A spacer that grows after the videos have laid out shoves the scroll down for a frame.
        SubcomposeLayout(Modifier.fillMaxSize().clipToBounds()) { constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val header = subcompose("header") {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(top))
                    LargePageBrandMark(contentGap = 4.dp)
                    HomeChips(chips, pager.currentPage) { index ->
                        if (index != pager.currentPage) scope.launch { pager.animateScrollToPage(index) }
                    }
                }
            }.first().measure(loose)
            if (headerPx != header.height) headerPx = header.height
            val headerDp = header.height.toDp()
            val body = subcompose("body") {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    beyondViewportPageCount = 1,
                ) { page ->
                    val chip = chips[page]
                    val feed = if (chip.token == null) lib.home else lib.category(chip)
                    LaunchedEffect(chip.token) { feed.ensureLoaded() }
                    FeedList(
                        feed,
                        contentPadding.withTop(0.dp),
                        onOpen,
                        header = { item(key = "home-head") { Spacer(Modifier.height(headerDp)) } },
                        scrollToTop = scrollToTop,
                        pullToRefresh = false,
                        listState = feed.scroll,
                    )
                }
            }.first().measure(constraints)
            val fade = subcompose("fade") {
                PageTopFade(followList ?: lib.home.scroll)
            }.first().measure(loose)
            layout(constraints.maxWidth, constraints.maxHeight) {
                body.place(0, 0)
                header.place(0, -scrollPx)
                fade.place(0, 0)
            }
        }
    }
}

@Composable
fun SubscriptionsScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
    pullState: BarPullRefreshState? = null,
) {
    val signedIn = Account.signedIn
    val openChannel = LocalChannelOpener.current
    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.subscriptions.ensureLoaded()
            if (lib.channels.isEmpty()) lib.loadChannels()
        }
    }
    val listState = lib.subscriptions.scroll
    val top = pageContentTop()
    val showStrip = signedIn && lib.channels.isNotEmpty()
    if (!signedIn) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, contentPadding = contentPadding.withTop(top)) {
                item { LargePageTitle("Subscriptions", fontSize = LargePageTitleCompactFontSize) }
                item {
                    EmptyState(
                        icon = SfIcons.Subscriptions,
                        title = "Your subscriptions",
                        body = "Sign in with Google to see new videos from the channels you follow.",
                        action = { PillButton("Sign in with Google", onSignIn) },
                    )
                }
            }
            PageTopFade(listState)
        }
        return
    }
    BarPullRefresh(refreshing = lib.subscriptions.refreshing, onRefresh = lib.subscriptions::refresh, state = pullState) {
        Box(Modifier.fillMaxSize()) {
            FeedList(
                lib.subscriptions,
                contentPadding.withTop(top),
                onOpen,
                header = {
                    item(key = "page-title") { LargePageTitle("Subscriptions", fontSize = LargePageTitleCompactFontSize) }
                    if (showStrip) {
                        item(key = "channels") {
                            SubscriptionsChannelStrip(channels = lib.channels) { ch ->
                                openChannel("https://www.youtube.com/channel/${ch.id}")
                            }
                        }
                    }
                },
                scrollToTop = scrollToTop,
                pullToRefresh = false,
                listState = listState,
            )
            PageTopFade(listState)
        }
    }
}

/** Home's recommendation tabs. The channel page's tab row. It scrolls away with the open list. */
@Composable
private fun HomeChips(chips: List<FeedChip>, selected: Int, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val palette = ArtworkPalette(
        background = scheme.background,
        wash = scheme.background,
        elevated = scheme.surfaceVariant,
        accent = scheme.primary,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        divider = scheme.outlineVariant,
    )
    Column(Modifier.fillMaxWidth()) {
        ChannelTabRow(selected.coerceIn(0, chips.lastIndex), chips.map { it.title }, palette, onPick)
        Spacer(Modifier.height(PageGutter))
    }
}

@Composable
private fun SubscriptionsChannelStrip(
    channels: List<ChannelRef>,
    onOpen: (ChannelRef) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = PageGutter),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
    ) {
        items(channels, key = { it.id }) { ch ->
            Column(
                Modifier
                    .width(68.dp)
                    .pressable { onOpen(ch) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChannelAvatar(ch.avatar, ch.name, size = 56, selected = false, ringSlot = true)
                Spacer(Modifier.height(6.dp))
                FadingLine(
                    text = ch.name,
                    style = TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
                    color = c.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
    pullState: BarPullRefreshState? = null,
) {
    val signedIn = Account.signedIn
    val localHistory = WatchHistory.videos
    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.history.ensureLoaded()
            if (lib.playlists.isEmpty()) lib.loadPlaylists()
            if (lib.channels.isEmpty()) lib.loadChannels()
        }
    }
    val listState = lib.libraryScroll
    val libraryList: @Composable () -> Unit = {
        LazyColumn(
            state = rememberScrollToTop(scrollToTop, listState),
            contentPadding = contentPadding.withTop(pageContentTop()),
        ) {
            item(key = "page-title") { LargePageTitle("Library", fontSize = LargePageTitleCompactFontSize) }
            if (Incognito.on) item(key = "incognito") { IncognitoBanner() }
            val downloads = Downloads.all
            if (downloads.isNotEmpty()) {
                item(key = "downloadsTitle") { SectionTitle("Downloads") }
                item(key = "downloads") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PageGutter),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(downloads, key = { "d-" + it.item.id }) { d -> DownloadCard(d) { onOpen(d.item) } }
                    }
                }
            }
            item(key = "historyTitle") { SectionTitle("History") }
            item(key = "history") {
                val history = if (signedIn) lib.history.videos else localHistory
                when {
                    history.isNotEmpty() -> LazyRow(
                        contentPadding = PaddingValues(horizontal = PageGutter),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(history.take(20), key = { "h-" + it.id }) { video -> HistoryCard(video) { onOpen(video) } }
                    }
                    signedIn && lib.history.loading -> HistorySkeletons()
                    else -> Text(
                        if (signedIn) lib.history.error ?: "Nothing watched yet."
                        else "Videos you watch on this phone show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = PageGutter),
                    )
                }
            }
            if (signedIn) {
                item(key = "playlistsTitle") { SectionTitle("Playlists") }
                val pinned = listOf(
                    PlaylistRef("WL", "Watch later", null, null),
                    PlaylistRef("LL", "Liked videos", null, null),
                )
                items((pinned + lib.playlists).chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                    Row(Modifier.padding(horizontal = PageGutter, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { pl -> PlaylistTile(pl, Modifier.weight(1f)) { onOpenPlaylist(pl) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                item(key = "signInNudge") {
                    Column(Modifier.padding(horizontal = PageGutter, vertical = 16.dp)) {
                        Text(
                            "Sign in for Watch later, liked videos and playlists.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        PillButton("Sign in with Google", onSignIn)
                    }
                }
            }
        }
    }
    if (!signedIn) {
        Box(Modifier.fillMaxSize()) {
            libraryList()
            PageTopFade(listState)
        }
        return
    }
    BarPullRefresh(
        refreshing = lib.history.refreshing,
        onRefresh = { lib.history.refresh(); lib.loadPlaylists() },
        state = pullState,
    ) {
        Box(Modifier.fillMaxSize()) {
            libraryList()
            PageTopFade(listState)
        }
    }
}

@Composable
private fun HistoryCard(video: VideoItem, onClick: () -> Unit) {
    val menu = LocalVideoMenu.current
    val anchor = rememberVideoAnchor(FeedVideoThumbRadius.dp)
    Column(Modifier.width(168.dp).prefetchOnPress(video.id).pressable(pressedScale = 0.97f, lift = true, onLongClick = { menu(video) }, onClick = { anchor.play(onClick) })) {
        Box(Modifier.videoAnchor(anchor)) {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = FeedVideoThumbRadius, videoId = video.id)
            DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true, live = video.live)
        }
        Spacer(Modifier.height(7.dp))
        FadingTitle(
            text = video.title,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp),
        )
    }
}

/** "You're incognito": history is paused, with a way to turn it back on. */
@Composable
private fun IncognitoBanner() {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = PageGutter, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SfIcons.VisibilityOff, null, tint = c.onSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("You're incognito", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("What you watch and search isn't saved.", color = c.onSurfaceVariant, fontSize = 13.sp)
        }
        TextLink("Turn off") { Incognito.set(false) }
    }
}

/** A saved video: plays offline when done, shows progress while saving, retries when stopped. */
@Composable
private fun DownloadCard(d: Download, onPlay: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val video = d.item
    Column(
        Modifier.width(168.dp).pressable(pressedScale = 0.97f, lift = true) {
            when {
                d.done -> onPlay()
                d.state == Download.State.Failed -> Downloads.retry(video.id)
                else -> Unit
            }
        },
    ) {
        Box {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = FeedVideoThumbRadius)
            if (d.done) DurationPill(video.durationSec, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true)
            else Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(FeedVideoThumbRadius.dp)).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (d.state) {
                        Download.State.Failed -> "Tap to retry"
                        Download.State.Queued -> "Waiting…"
                        else -> "${(d.progress * 100).toInt()}%"
                    },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pressable { Downloads.delete(video.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SfIcons.Close, "Delete download", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        FadingTitle(
            text = video.title,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp),
        )
        Text(
            if (d.quality == DownloadQuality.AudioOnly) "Audio only" else d.quality.label,
            color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1,
        )
    }
}

@Composable
private fun PlaylistTile(playlist: PlaylistRef, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    val (icon, tone) = when (playlist.id) {
        "WL" -> SfIcons.Clock to c.primaryContainer
        "LL" -> SfIcons.ThumbUpFill to c.tertiaryContainer
        else -> SfIcons.PlaylistPlay to c.secondaryContainer
    }
    Column(
        modifier = modifier
            .height(132.dp)
            .pressable(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(c.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (playlist.thumbnail != null) {
            Thumbnail(playlist.thumbnail, Modifier.size(width = 72.dp, height = 44.dp), radius = 12)
        } else {
            Box(Modifier.size(52.dp).clip(CookieShape).background(tone), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = contentColorFor(tone))
            }
        }
        Column {
            Text(playlist.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            playlist.subtitle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        modifier = Modifier.pressable(onClick = onClick).padding(8.dp),
    )
}

// ── Search ─────────────────────────────────────────────────────────────────────

/** Lives above the tab switcher so results survive switching tabs. */
@Stable
class SearchState(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var submitted by mutableStateOf("")
        private set
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var suggestionsLoading by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var generation by mutableStateOf(0)
        private set
    var filter by mutableStateOf(SearchFilter())
        private set
    /** Typing over a finished search. Back returns to those results before leaving the page. */
    var typing by mutableStateOf(true)
        private set
    /** Hiding the keyboard on submit delivers a back, and a rebuilt field refocuses. Both would leave the page. */
    private var submittedAt = 0L
    var filtersOpen by mutableStateOf(false)
    /** The field in the top bar has focus. The page reads it; the bar writes it. */
    var fieldFocused by mutableStateOf(false)
    val showingResults get() = submitted.isNotBlank() && !typing
    val hasMore get() = next != null
    private var next: String? = null
    private var job: Job? = null
    /** Bumped on each new search, so a cancelled page cannot write the error or the hits. */
    private var request = 0
    private var suggestJob: Job? = null
    /** A pasted YouTube link, opened by the screen instead of searched. */
    var onLink: ((YoutubeTarget) -> Unit)? = null

    /** The results list. Kept here so a video, short, channel, or playlist comes back to the same row. */
    val resultsList = LazyListState()

    /** The Shorts-chip grid. Same idea as [resultsList]. */
    val shortsGrid = LazyGridState()

    fun reset() {
        request++
        job?.cancel()
        suggestJob?.cancel()
        query = ""
        submitted = ""
        hits = emptyList()
        next = null
        suggestions = emptyList()
        suggestionsLoading = false
        loading = false
        error = null
        filter = SearchFilter()
        typing = true
        filtersOpen = false
        resultsList.requestScrollToItem(0)
        shortsGrid.requestScrollToItem(0)
    }

    fun startTyping() {
        if (android.os.SystemClock.elapsedRealtime() - submittedAt < 500) return
        typing = true
    }

    /** True for the moment after submit, while the keyboard's back is still arriving. */
    fun swallowBack(): Boolean = android.os.SystemClock.elapsedRealtime() - submittedAt < 500

    /** Back to the search already on screen. The field returns to what was submitted. */
    fun stopTyping() {
        typing = false
        suggestJob?.cancel()
        suggestions = emptyList()
        suggestionsLoading = false
        if (submitted.isNotEmpty()) query = submitted
    }

    /** True when Back should return to the results instead of leaving. */
    fun returnToResults(): Boolean {
        if (!typing || submitted.isEmpty()) return false
        stopTyping()
        return true
    }

    fun updateQuery(text: String) {
        query = text
        typing = true
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            suggestionsLoading = false
            if (submitted.isNotEmpty() || hits.isNotEmpty() || error != null || loading) {
                submitted = ""
                hits = emptyList()
                next = null
                error = null
                loading = false
                job?.cancel()
            }
            return
        }
        suggestJob = scope.launch {
            suggestionsLoading = true
            runCatching { YouTube.searchSuggestions(text) }
                .onSuccess { suggestions = it }
                .onFailure { suggestions = emptyList() }
            suggestionsLoading = false
        }
    }

    fun updateFilter(nextFilter: SearchFilter) {
        val refetch = nextFilter.toSearchParams() != filter.toSearchParams()
        filter = nextFilter
        if (refetch && submitted.isNotEmpty()) load(record = false)
    }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        parseYoutubeTarget(q)?.let { target ->
            typing = false
            suggestJob?.cancel()
            suggestions = emptyList()
            onLink?.invoke(target)
            return
        }
        load(record = true)
    }

    fun loadMore() {
        val token = next ?: return
        if (loading || submitted.isEmpty()) return
        loading = true
        val ticket = request
        job?.cancel()
        val q = submitted
        val params = filter.toSearchParams()
        job = scope.launch {
            val result = runCatching { InnerTube.search(q, params, token) }
            if (!applySearch(ticket, result) { page ->
                    val more = page.hits.presentedFor(filter.contentType)
                    val seen = hits.mapTo(HashSet()) { it.identityKey() }
                    hits = hits + more.filter { seen.add(it.identityKey()) }
                    next = page.next
                }
            ) return@launch
        }
    }

    private fun load(record: Boolean) {
        val q = query.trim()
        if (q.isEmpty()) return
        suggestJob?.cancel()
        suggestions = emptyList()
        suggestionsLoading = false
        if (record) SearchHistory.add(q)
        submitted = q
        submittedAt = android.os.SystemClock.elapsedRealtime()
        typing = false
        val ticket = ++request
        loading = true
        error = null
        if (hits.isNotEmpty()) generation++
        hits = emptyList()
        next = null
        job?.cancel()
        val params = filter.toSearchParams()
        job = scope.launch {
            val result = runCatching { InnerTube.search(q, params, null) }
            if (!applySearch(ticket, result) { page ->
                    hits = page.hits.presentedFor(filter.contentType)
                    next = page.next
                    error = null
                }
            ) return@launch
        }
    }

    /**
     * Writes a finished page only when it still belongs to the search on screen.
     * A cancelled call used to be caught and shown as "Search didn't load", then the newer page replaced it.
     * Returns false when this coroutine should stop without touching [loading].
     */
    private suspend fun CoroutineScope.applySearch(
        ticket: Int,
        result: Result<SearchPage>,
        onPage: (SearchPage) -> Unit,
    ): Boolean {
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (ticket != request) return false
        ensureActive()
        result.onSuccess(onPage).onFailure { error = YouTube.friendlyError(it) }
        loading = false
        return true
    }
}

/** The Shorts chip lays out single shorts. Every other chip keeps Flow's shelves and channel block. */
private fun List<SearchHit>.presentedFor(type: ContentType): List<SearchHit> =
    if (type == ContentType.SHORTS) asShortsResults() else this

/** The search field, in the shared top bar. Focus and the keyboard live with the field. */
@Composable
fun SearchTopAccessory(state: SearchState) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    fun showSearchKeyboard() {
        scope.launch {
            delay(80)
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(Unit) { if (!state.showingResults) showSearchKeyboard() }
    DisposableEffect(state) { onDispose { state.fieldFocused = false } }
    SearchBarField(
        query = state.query,
        onQueryChange = state::updateQuery,
        onSubmit = { dismissAndSearch(state, focus, keyboard, view) },
        onClear = {
            state.updateQuery("")
            showSearchKeyboard()
        },
        focusRequester = searchFocus,
        onFocusChanged = {
            state.fieldFocused = it
            if (it) state.startTyping()
        },
    )
}

private fun dismissAndSearch(
    state: SearchState,
    focus: androidx.compose.ui.focus.FocusManager,
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
    view: android.view.View,
) {
    // A hide issued while the key event is still being dispatched can be undone by the IME,
    // so the window-level hide is posted to run after it.
    focus.clearFocus(force = true)
    keyboard?.hide()
    view.postDelayed({
        view.context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }, 120)
    state.submit()
}

/** The search field, drawn inside the top bar's pill on a surface tone. */
@Composable
internal fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    placeholder: String = "Search YouTube",
) {
    val c = MaterialTheme.colorScheme
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val hint = if (glass) glassContentColor().copy(alpha = 0.6f) else c.onSurfaceVariant
    val typed = if (glass) glassContentColor() else c.onBackground
    val style = OwnDirectionText.merge(
        TextStyle(
            fontFamily = SfProDisplay,
            fontWeight = FontWeight.W400,
            fontSize = 16.sp,
            letterSpacing = 0.2.sp,
        ),
    )
    Row(
        Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SfIcons.Search,
            contentDescription = "Search",
            tint = hint,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .pressable(enabled = query.isNotBlank(), onClick = onSubmit)
                .padding(6.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, style = style, color = hint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = style.merge(TextStyle(color = typed)),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }, onDone = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .onPreviewKeyEvent { e ->
                        if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                            if (e.type == KeyEventType.KeyDown) onSubmit()
                            true
                        } else false
                    },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).pressable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SfIcons.Close, "Clear", tint = hint, modifier = Modifier.size(18.dp))
            }
        }
    }
}
