package com.yuno

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.lang.ref.WeakReference
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.yuno.backdrop.backdrops.layerBackdrop
import com.yuno.backdrop.backdrops.rememberLayerBackdrop
import com.yuno.floatingtabbar.rememberFloatingTabBarScrollConnection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.yuno.icons.YunoIcons
import com.yuno.icons.SfIcons

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The system fades the splash out after the first frame, so the mark
            // stays up after the page is already drawn. Removing it is the whole
            // exit: nothing is held, and the page shows as soon as it is ready.
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        AppLanguage.refresh()
        // One window at a time (one player, one video texture): a newer one closes the one before.
        // That's a bubble opened while the app is open, or the app opened from Home while it sits
        // in a bubble (the bubble then goes away). Its task goes too, so Recents keeps no stale card;
        // PlaybackService.onTaskRemoved sees this window still open and plays on.
        current?.get()?.takeIf { it !== this && !it.isFinishing }?.finishAndRemoveTask()
        current = WeakReference(this)
        enableEdgeToEdge()
        // The media notification (and its lock-screen controls) needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        NewVideos.takeVideo(intent)?.let { NewVideos.opening = it }
        setContent { YunoTheme { YunoApp() } }
    }

    /** A new-video notification tapped while this window is open (singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        NewVideos.takeVideo(intent)?.let { NewVideos.opening = it }
    }

    override fun onStart() {
        super.onStart()
        AppUpdate.checkIfDue()
    }

    override fun onResume() {
        super.onResume()
        AppUpdate.onWindowResumed(this)
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        private var current: WeakReference<MainActivity>? = null

        /** The task of the app's open window; null when there's none (closed, or on its way out). */
        fun openTaskId(): Int? = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.taskId
    }
}

/**
 * The four tabs. Each glyph fills a different share of its box - [YunoIcons.Home]
 * covers about half of it, while the SF Symbols and font-drawn
 * [YunoIcons.TabLibrary] cover three quarters - so [iconScale] shrinks the
 * other three to Home's optical area instead of letting them run to the edges.
 */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val iconScale: Float = 1f) {
    Home("Home", YunoIcons.Home, YunoIcons.Home),
    Shorts("Shorts", SfIcons.Shorts, SfIcons.Shorts, 0.85f),
    Subscriptions("Subscriptions", SfIcons.Subscriptions, SfIcons.Subscriptions, 1.08f),
    Library("Library", YunoIcons.TabLibrary, YunoIcons.TabLibrary, 0.84f),
}

/** Backdrop the nav samples for liquid glass. */
internal val LocalAppHaze = staticCompositionLocalOf<HazeState?> { null }

// The mini-player floats 8 dp above the nav pill (NavBar.kt), capped at 368 dp, centered.
private val MiniWidth = 368.dp
private val MiniGap = 8.dp

@Composable
fun YunoApp() {
    val c = MaterialTheme.colorScheme
    var onboardingDone by remember { mutableStateOf(Onboarding.completed) }
    var signingIn by remember { mutableStateOf(false) }
    // First launch: finish setup before the tabs / player / feeds start.
    if (!onboardingDone) {
        OnboardingScreen(
            onFinished = { onboardingDone = true },
            onSignIn = { signingIn = true },
        )
        if (signingIn) {
            SignInScreen(
                onDone = { signingIn = false },
                onCancel = { signingIn = false },
            )
        }
        return
    }
    val player = rememberPlayerState()
    val scope = rememberCoroutineScope()
    val search = remember { SearchState(scope) }
    val appContext = LocalContext.current.applicationContext
    val lib = remember { LibraryData(scope, appContext) }
    LaunchedEffect(Account.signedIn) { if (Account.signedIn) lib.warmUp() }
    // BitChord's one back stack. Pushed pages (search, settings, a playlist, a
    // channel) sit on it; a tab is what is left when the stack is empty.
    val navigator = remember { AppNavigator(detailDepth = { 0 }, popDetail = { false }, clearDetails = {}) }
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    val homePull = remember { BarPullRefreshState() }
    val subscriptionsPull = remember { BarPullRefreshState() }
    val libraryPull = remember { BarPullRefreshState() }
    val channelStates = remember { mutableMapOf<String, ChannelState>() }
    val playlistFeeds = remember { mutableMapOf<String, FeedState>() }
    val pageStates = rememberSaveableStateHolder()
    val pageKeysSeen = remember { mutableSetOf<String>() }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // Shorts: its own feed and players; ‹ (and Back) return to the tab it was opened from.
    // One per process (YunoApplication), so its bank keeps filling with no window open.
    val shorts = (LocalContext.current.applicationContext as YunoApplication).shorts
    val host = LocalContext.current as Activity
    val windowLayout = rememberWindowLayout(host)
    val wide = windowLayout.wide
    DisposableEffect(shorts) { onDispose { if (!host.isChangingConfigurations) shorts.leave() } }
    var shortsFrom by rememberSaveable { mutableStateOf(Tab.Home) }
    var shortsBackTo by rememberSaveable { mutableStateOf<String?>(null) }
    var shortsListOnTab by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(NewVideos.opening) {
        val video = NewVideos.opening ?: return@LaunchedEffect
        NewVideos.opening = null
        navigator.clear()
        if (tab == Tab.Shorts) tab = shortsFrom
        player.open(video)
    }
    LaunchedEffect(AppRoute.Settings in navigator) {
        if (AppRoute.Settings !in navigator) settingsQuery = ""
    }
    val livePageKeys = navigator.entries.map { AppDestination.Route(it).key }
    LaunchedEffect(livePageKeys) {
        val gone = pageKeysSeen.filter { it !in livePageKeys }
        gone.forEach(pageStates::removeState)
        pageKeysSeen.removeAll(gone.toSet())
    }
    // Bumped when the already-selected tab is tapped; that tab's list scrolls to the top.
    val scrollToTop = remember { mutableStateMapOf<Tab, Int>() }
    val reduced = rememberReducedMotion()
    val playerDock = remember { PlayerDock() }
    var showNowPlaying by remember { mutableStateOf(false) }
    val playerSheetMotion = remember {
        PlayerSheetMotion(
            scope = scope,
            dock = playerDock,
            shown = { showNowPlaying },
            setShown = { up ->
                showNowPlaying = up
                player.expanded = up
            },
        )
    }
    val activeDock = playerDock
    val playerPull = playerSheetMotion.takeIf { !reduced }
    // Known before the sheet exists, so a pull starts at the bottom instead of
    // flashing the page fully open and then clearing it.
    playerSheetMotion.windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    LaunchedEffect(player.current?.id) { HeldFrame.image = null }

    // The screen stays on while a video plays
    val rootView = LocalView.current
    DisposableEffect(player.isPlaying, shorts.playing) {
        rootView.keepScreenOn = player.isPlaying || shorts.playing
        onDispose { rootView.keepScreenOn = false }
    }

    // Opening the player drops any text focus (the search box behind it), so the keyboard
    // can't come back over the video.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(player.expanded) {
        if (player.expanded) {
            if (navigator.visibleRoute() is AppRoute.Search) navigator.pop()
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    // Off screen: after ~2 s of background *playback*, audio-only; quick switches keep video loaded.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> if (!host.isChangingConfigurations) {
                    player.setBackground(true); shorts.setForeground(false)
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> { player.setBackground(false); shorts.setForeground(true) }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // floatingBarInsets + NAV_BAR_LIFT: the system bar, never less than a
    // gesture inset, then 6 dp more.
    val stackBottom = navBottom.coerceAtLeast(GestureBarFloor) + NavBarLift
    var optionsFor by remember { mutableStateOf<VideoItem?>(null) }
    var optionsHeld by remember { mutableStateOf<HeldItem?>(null) }
    // On Shorts the app's video waits, paused, out of sight (no mini-player).
    val glassOn = LiquidGlassSetting.enabled && isGlassSupported()
    val hasMini = player.current != null && tab != Tab.Shorts && !glassOn
    val contentPadding = if (wide) {
        PaddingValues(bottom = stackBottom + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 16.dp)
    } else {
        PaddingValues(bottom = if (player.current != null && tab != Tab.Shorts) ListBottomPlaying else ListBottomIdle)
    }
    val railStart = WindowInsets.safeDrawing.only(WindowInsetsSides.Start).asPaddingValues()
        .calculateStartPadding(LayoutDirection.Ltr)
    val showRail = wide && tab != Tab.Shorts
    val pagesStart = if (showRail) RailWidth + railStart else 0.dp

    // Fullscreen is whenever the open player is shown in landscape (rotation or button).
    val activity = LocalContext.current as Activity
    val orientation = rememberOrientationController(activity as ComponentActivity)
    val playerOpen = showNowPlaying && player.current != null
    val config = LocalConfiguration.current
    val windowLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val landscape = remember(windowLandscape, config.screenWidthDp, config.screenHeightDp, config.densityDpi, orientation.multiWindow) {
        if (!orientation.multiWindow && activity.onSquareScreen()) activity.turnedSideways() else windowLandscape
    }
    // Audio only has no picture to go fullscreen with: the open player stays portrait.
    orientation.Bind(playerOpen && !AudioOnly.enabled, landscape)
    val pip = remember(activity) { PipController(activity) }
    // Closing the picture-in-picture window pauses (expanding it just comes back).
    pip.Bind(onClosed = { player.controller?.pause() })
    pip.AutoEnter(AutoPip.on && player.isPlaying && player.current != null && !AudioOnly.enabled, player.videoAspect)
    // The PiP window can be any shape: hold whatever was showing when it opened.
    val fullscreenBeforePip = remember { mutableStateOf(false) }
    val tabletop = windowLayout.tabletopY != null && playerOpen && !pip.active
    val showFullscreen = !tabletop && if (pip.active) fullscreenBeforePip.value else orientation.showsFullscreen(playerOpen, landscape)
    SideEffect { if (!pip.active) fullscreenBeforePip.value = showFullscreen }
    LaunchedEffect(showFullscreen) { player.fullscreen = showFullscreen }
    SystemBarsHidden(showFullscreen || tabletop)
    // A vertical video's fullscreen stays upright (Fullscreen.kt).
    fun toggleFullscreen() = if (showFullscreen) orientation.exitFullscreen() else orientation.enterFullscreen(vertical = player.videoAspect < 1f)

    fun showChannel(url: String) {
        player.expanded = false
        showNowPlaying = false
        navigator.push(AppRoute.Channel(url))
    }

    fun openShorts(list: ShortsList) {
        shortsBackTo = (navigator.visibleRoute() as? AppRoute.Channel)?.url
        shortsListOnTab = tab == Tab.Shorts
        navigator.clear()
        shorts.openList(list)
        if (tab != Tab.Shorts) { shortsFrom = tab; tab = Tab.Shorts }
    }

    fun leaveShorts() {
        val back = shortsBackTo
        shortsBackTo = null
        when {
            back == null -> tab = shortsFrom
            !shortsListOnTab -> { tab = shortsFrom; showChannel(back) }
            else -> { showChannel(back); shorts.cover(true); shorts.closeList() }
        }
    }

    // One step back, from anywhere: the page on top of the stack, then any tab
    // but Home back to Home. Settings with a search typed in clears that first.
    // The gesture and the bar's back button both come here.
    val canGoBack = navigator.entries.isNotEmpty() || tab != Tab.Home
    val goBack: () -> Unit = {
        when (val route = navigator.visibleRoute()) {
            AppRoute.Settings -> if (settingsQuery.isNotEmpty()) settingsQuery = "" else navigator.pop()
            null -> if (tab == Tab.Shorts) leaveShorts() else tab = Tab.Home
            else -> navigator.pop()
        }
    }
    BackHandler(enabled = showNowPlaying) {
        if (reduced) showNowPlaying = false else playerSheetMotion.close()
    }
    BackHandler(enabled = !showNowPlaying && canGoBack, onBack = goBack)
    BackHandler(enabled = signingIn) { signingIn = false }
    BackHandler(enabled = tab == Tab.Shorts && shorts.commentsOpen) { shorts.commentsOpen = false }
    // Registered last so it wins: in fullscreen, Back returns to the portrait player first.
    BackHandler(enabled = showFullscreen) { orientation.exitFullscreen() }

    // The open player's controls live on the video (VideoView.kt); the nav pill stays the tabs.

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    // Shorts (Shorts.kt) play on their own players: the app's video pauses on the way in and
    // carries on, from the same spot in the mini-player, on the way out (Derek, 2026-10-28).
    // One progress turns the tab bar into the glass bar: 620 ms in, 460 ms out, both moving at once.
    val shortsMorph = remember { androidx.compose.animation.core.Animatable(if (tab == Tab.Shorts) 1f else 0f) }
    var resumeMain by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab == Tab.Shorts) {
            resumeMain = resumeMain || player.isPlaying
            player.controller?.pause()
            shorts.enter(mainPlayerActive = player.current != null)
        } else if (shorts.active) {
            shortsBackTo = null
            shorts.leave()
            if (resumeMain) player.controller?.play()
            resumeMain = false
        }
    }
    // Shorts turned off (account sheet) while it was the tab: back to where it was opened from.
    LaunchedEffect(ShortsSetting.enabled) { if (!ShortsSetting.enabled && tab == Tab.Shorts) tab = shortsFrom }
    LaunchedEffect(tab == Tab.Shorts) {
        if (tab == Tab.Shorts) shortsMorph.animateTo(1f, if (reduced) snap() else tween(ShortsEnterMs, easing = androidx.compose.animation.core.LinearEasing))
        else shortsMorph.animateTo(0f, if (reduced) snap() else tween(ShortsExitMs, easing = ShortsExitEasing))
    }
    SideEffect {
        StatusBarOverVideo.shorts = tab == Tab.Shorts && navigator.visibleRoute() !is AppRoute.Channel
        shorts.onMessage = { player.actionMessage = it }
        shorts.mainBusy = { player.isBuffering }
    }

    LaunchedEffect(player.expanded, player.current?.id) {
        if (player.current == null) {
            player.expanded = false
            showNowPlaying = false
        } else if (player.expanded && !showNowPlaying) {
            if (reduced) showNowPlaying = true else playerSheetMotion.open()
        } else if (!player.expanded && showNowPlaying && !playerSheetMotion.holding) {
            if (reduced) showNowPlaying = false else playerSheetMotion.close()
        }
    }

    // A channel opened from Shorts sits over the short, which waits paused.
    SideEffect { shorts.cover(tab == Tab.Shorts && navigator.visibleRoute() is AppRoute.Channel) }
    BackHandler(enabled = tab == Tab.Shorts && shorts.channelMenu) { shorts.channelMenu = false }

    val hazeState = remember { HazeState() }
    val navScroll = rememberFloatingTabBarScrollConnection()
    // Captures while the live surface is still up, so the fold scales that
    // frame instead of the TextureView. See GlassNav.
    SideEffect { navScroll.onBeforeToggle = { SharedVideoTexture.captureFrame() } }
    // Shorts is a vertical swipe, not a list: that swipe must not fold the glass bar.
    LaunchedEffect(tab) { if (tab == Tab.Shorts) navScroll.expand() }
    val windowBackground = c.background
    val paintBackdrop: ContentDrawScope.() -> Unit = remember(windowBackground) {
        {
            drawRect(windowBackground)
            drawContent()
        }
    }
    val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
    val topChrome = remember { TopChromeHost() }
    // One recording of everything on the page, so the glass matches the screen
    // during a transition instead of holding the page that just closed. The
    // top buttons are drawn by TopChromeLayer, outside this box.
    val pageSample = Modifier
        .hazeSource(hazeState)
        .then(if (glassOn) Modifier.layerBackdrop(appBackdrop) else Modifier)
        .then(if (glassOn && tab != Tab.Shorts) Modifier.nestedScroll(navScroll) else Modifier)
    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val stageFloor = maxOf(StageMinAspect, constraints.maxWidth / (constraints.maxHeight * StageMaxHeightShare))
        SideEffect { player.stageFloor = stageFloor }
        val sharedSurface = remember(player) {
            androidx.compose.runtime.movableContentOf { m: Modifier -> VideoSurface(player.controller, m) }
        }
        CompositionLocalProvider(
            LocalVideoSurface provides if (pip.active) NoVideoSurface else sharedSurface,
            LocalPictureInPicture provides pip,
            LocalVideoMenu provides { video ->
                optionsHeld = LongPressOrigin.consume()
                optionsFor = video
            },
            LocalChannelOpener provides { url -> showChannel(url) },
            LocalWindowLayout provides windowLayout,
            LocalAppHaze provides hazeState,
            LocalLiquidGlassEnabled provides glassOn,
            LocalAppBackdrop provides appBackdrop,
            LocalTopChrome provides topChrome,
        ) {
        Box(Modifier.fillMaxSize().then(pageSample).padding(start = pagesStart)) {
        // BitChord's page switcher (MainActivity, "content"). Tabs swap outright;
        // a pushed page crossfades. The top bar is not inside this switcher.
        val openSearchPage = {
            search.reset()
            navigator.push(AppRoute.Search)
        }
        val openSettingsPage = { navigator.push(AppRoute.Settings) }
        AnimatedContent(
            targetState = when (val entry = navigator.visibleEntry()) {
                null -> AppDestination.Tab(tab.ordinal)
                else -> AppDestination.Route(entry)
            },
            contentKey = { it.key },
            transitionSpec = {
                val initial = initialState
                val target = targetState
                val tabSwap = initial is AppDestination.Tab && target is AppDestination.Tab
                if (tabSwap) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "content",
        ) { destination ->
            val key = destination.key
            val pageRoute = (destination as? AppDestination.Route)?.entry?.route
            val pageContent: @Composable () -> Unit = {
                when (pageRoute) {
                    null -> when (Tab.entries[(destination as AppDestination.Tab).index]) {
                        Tab.Home -> HomeScreen(
                            lib, contentPadding, onOpen = player::open, onSearch = openSearchPage,
                            onSignIn = { signingIn = true }, onAccount = openSettingsPage,
                            scrollToTop = scrollToTop[Tab.Home] ?: 0, pullState = homePull,
                        )
                        Tab.Subscriptions -> SubscriptionsScreen(
                            lib, contentPadding, onOpen = player::open, onSearch = openSearchPage,
                            onSignIn = { signingIn = true }, onAccount = openSettingsPage,
                            scrollToTop = scrollToTop[Tab.Subscriptions] ?: 0, pullState = subscriptionsPull,
                        )
                        Tab.Shorts -> ShortsScreen(
                            shorts,
                            progress = { shortsMorph.value },
                            navClearance = stackBottom + NavHeight,
                            overlayBottom = stackBottom + NavHeight + 28.dp,
                            onChannel = ::showChannel,
                        )
                        Tab.Library -> LibraryScreen(
                            lib, contentPadding, onOpen = player::open,
                            onOpenPlaylist = { navigator.push(AppRoute.Playlist(it)) },
                            onSearch = openSearchPage,
                            onSignIn = { signingIn = true }, onAccount = openSettingsPage,
                            scrollToTop = scrollToTop[Tab.Library] ?: 0, pullState = libraryPull,
                        )
                    }
                    AppRoute.Search -> SearchScreen(search, contentPadding, onOpen = player::open)
                    AppRoute.Settings -> SettingsScreen(
                        player, section = null, query = settingsQuery, onQuery = { settingsQuery = it },
                        onOpenSection = { navigator.push(AppRoute.SettingsSection(it)) },
                        onSignIn = { signingIn = true },
                        onSignedOut = { lib.reset(); player.refreshAccountState() },
                        contentPadding = contentPadding,
                    )
                    is AppRoute.SettingsSection -> SettingsScreen(
                        player, section = pageRoute.page, query = settingsQuery, onQuery = { settingsQuery = it },
                        onOpenSection = { navigator.push(AppRoute.SettingsSection(it)) },
                        onSignIn = { signingIn = true },
                        onSignedOut = { lib.reset(); player.refreshAccountState() },
                        contentPadding = contentPadding,
                    )
                    is AppRoute.Playlist -> PlaylistScreen(
                        pageRoute.ref,
                        playlistFeeds.getOrPut(pageRoute.ref.id) { lib.playlistFeed(pageRoute.ref.id) },
                        contentPadding,
                        onOpen = player::open,
                        channelAvatarSeed = lib.channels.associate { it.id to it.avatar },
                    )
                    is AppRoute.Channel -> ChannelScreen(
                        channelStates.getOrPut(pageRoute.url) { ChannelState(pageRoute.url, scope) },
                        contentPadding,
                        onBack = goBack,
                        onOpen = { v -> if (tab == Tab.Shorts) tab = shortsFrom; player.open(v) },
                        onOpenShorts = ::openShorts,
                        onOpenPlaylist = { navigator.push(AppRoute.Playlist(it)) },
                        onMessage = { player.actionMessage = it },
                    )
                }
            }
            if (destination is AppDestination.Tab) {
                pageContent()
            } else {
                pageKeysSeen += key
                pageStates.SaveableStateProvider(key, pageContent)
            }
        }
        val route = navigator.visibleRoute()
        if (route !is AppRoute.Channel && !(route == null && tab == Tab.Shorts)) {
            PageTopBar(
                onBack = if (route != null) goBack else null,
                pull = when {
                    route != null -> null
                    tab == Tab.Home -> homePull
                    tab == Tab.Subscriptions -> subscriptionsPull
                    tab == Tab.Library -> libraryPull
                    else -> null
                },
                accessory = if (route is AppRoute.Search) {{ SearchTopAccessory(search) }} else null,
                beside = {
                    if (route == null && tab == Tab.Home) UpdateAction()
                },
            ) {
                if (route == null) HeaderActions(onSearch = openSearchPage, onAccount = openSettingsPage)
            }
        }
        } // page backdrop

        if (showRail) {
            NavRail(
                selected = tab,
                tabs = Tab.entries.filter { it != Tab.Shorts || ShortsSetting.enabled },
                onSelect = { t ->
                    player.expanded = false
                    showNowPlaying = false
                    val covered = navigator.entries.isNotEmpty()
                    navigator.clear()
                    when {
                        t == tab && covered -> Unit
                        t == tab -> scrollToTop[t] = (scrollToTop[t] ?: 0) + 1
                        else -> {
                            if (t == Tab.Shorts) shortsFrom = tab
                            tab = t
                        }
                    }
                },
            )
        }

        // Stays put while the player opens and closes. The player is its own window,
        // so it covers these the way it covers the tab bar; removing them for
        // showNowPlaying made them vanish at the start of the expand and return
        // only after the collapse.
        TopChromeLayer(topChrome, Modifier.align(Alignment.TopCenter).fillMaxWidth())

        // Ledgii's bottom vignette
        val pageColor = c.background
        val vignette = remember(pageColor) { vignetteBrush(pageColor, opaqueAtTop = false) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBottom + 130.dp)
                .graphicsLayer { alpha = 1f - shortsMorph.value }
                .drawBehind { drawRect(vignette) },
        )
        BottomGestureGuard(Modifier.align(Alignment.BottomCenter))

        val playerSheetContext = remember {
            android.view.ContextThemeWrapper(activity, R.style.Theme_Yuno_PlayerSheet)
        }
        CompositionLocalProvider(LocalContext provides playerSheetContext) {
            NowPlayingSheet(
                visible = showNowPlaying && !pip.active,
                fullscreen = showFullscreen,
                player = player,
                motion = playerSheetMotion,
                dock = playerDock,
                reducedMotion = reduced,
                onOpen = player::open,
                onDismiss = { showNowPlaying = false },
                onFullscreen = { toggleFullscreen() },
                bottomClearance = stackBottom + NavHeight + PageGutter,
                heldVideo = optionsFor.takeIf { showNowPlaying },
                heldOrigin = optionsHeld,
                onDismissHeld = { optionsFor = null },
            )
        }

        // The nav pill steps down out of the way of the comments panel (its composer sits there).
        val navAside by animateFloatAsState(
            if ((player.sheet.isPanel && playerOpen) || shorts.commentsOpen) 1f else 0f,
            if (reduced) snap() else tween(280, easing = Motion.Emphasized), label = "navAside",
        )
        // A tab is a fresh start: everything pushed over the old one goes with it.
        val onSelectTab: (Tab) -> Unit = { selected ->
            player.expanded = false
            val covered = navigator.entries.isNotEmpty()
            navigator.clear()
            when {
                selected == tab && covered -> Unit
                selected == tab -> scrollToTop[selected] = (scrollToTop[selected] ?: 0) + 1
                else -> {
                    if (selected == Tab.Shorts) shortsFrom = tab
                    tab = selected
                }
            }
        }
        val shownTabs = Tab.entries.filter { it != Tab.Shorts || ShortsSetting.enabled }
        val glassChrome = glassOn && !wide
        if (glassChrome) {
            GlassNavBar(
                tabs = shownTabs,
                selected = tab,
                onTabSelected = onSelectTab,
                scrollConnection = navScroll,
                player = player,
                onExpandPlayer = { player.expanded = true },
                dock = activeDock,
                pull = playerPull,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = FloatingBarMaxWidth)
                    .fillMaxWidth()
                    .padding(start = NavBarGutter, end = NavBarGutter, bottom = stackBottom)
                    .graphicsLayer {
                        translationY = navAside * (NavHeight + stackBottom + 24.dp).toPx()
                        alpha = 1f - navAside
                    },
            )
        } else {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = FloatingBarMaxWidth)
                    .fillMaxWidth()
                    .padding(bottom = stackBottom)
                    .graphicsLayer {
                        translationY = navAside * (NavHeight + stackBottom + 24.dp).toPx()
                        alpha = 1f - navAside
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (player.current != null && tab != Tab.Shorts) {
                    MiniPlayer(
                        player,
                        onExpand = { player.expanded = true },
                        showVideo = !showFullscreen && !playerDock.coversMiniArt,
                        dock = activeDock,
                        pull = playerPull,
                        modifier = Modifier.padding(horizontal = PageGutter).fillMaxWidth(),
                    )
                    Spacer(Modifier.height(MiniGap))
                }
                NavPill(
                    selected = tab,
                    onSelect = onSelectTab,
                    shorts = { 0f },
                    tabsEnabled = true,
                    modifier = Modifier.padding(horizontal = NavBarGutter).fillMaxWidth(),
                )
            }
        }

        if (signingIn) {
            SignInScreen(
                onDone = { signingIn = false; lib.reset(); player.refreshAccountState() },
                onCancel = { signingIn = false },
            )
        }

        when (player.sheet) {
            PlayerSheet.Quality -> QualitySheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Speed -> SpeedSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Chapters -> ChaptersSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Comments, PlayerSheet.Transcript -> Unit // a panel inside the player (PlayerScreen)
            PlayerSheet.Captions -> CaptionsSheet(player, onDismiss = { player.sheet = null }, onTranscript = { player.sheet = PlayerSheet.Transcript })
            PlayerSheet.Queue -> QueueSheet(player, onDismiss = { player.sheet = null })
            null -> Unit
        }
        player.playlistTarget?.let { video ->
            SaveToPlaylistSheet(player, video, maxHeight = maxHeight * 0.55f)
        }
        windowLayout.tabletopY?.takeIf { tabletop && showNowPlaying }?.let { fold ->
            with(density) { TabletopPlayer(player, foldTop = fold.start.toDp(), foldBottom = fold.endInclusive.toDp()) }
        }
        // Confirmations ("Added to queue") when the full player isn't showing its own.
        if (!showNowPlaying) {
            QueueMessage(player, Modifier.align(Alignment.BottomCenter).padding(bottom = stackBottom + NavHeight + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 12.dp))
        }
        if (pip.active) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().aspectFit(player.videoAspect)) {
                    sharedSurface(Modifier.fillMaxSize())
                    CaptionLayer(player, bottom = 4.dp)
                }
            }
        }
        VideoHeldMenu(
            video = optionsFor.takeIf { !showNowPlaying },
            held = optionsHeld,
            hazeState = hazeState,
            player = player,
            onOpen = player::open,
            onDismiss = { optionsFor = null },
        )
        } // CompositionLocalProvider
    }
}

@Composable
private fun QueueMessage(player: PlayerState, modifier: Modifier) {
    val message = player.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_000)
        player.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(50)).background(c.inverseSurface).padding(horizontal = 18.dp, vertical = 11.dp)) {
        Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
