package com.yuno

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuno.backdrop.backdrops.layerBackdrop
import com.yuno.backdrop.backdrops.rememberLayerBackdrop
import com.yuno.icons.SfIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings, in BitChord's chrome: a floating back button, a large title, the pill
 * search field, and inset cards of rows. The switch is theirs (the thumb stretches
 * while it is held) and a short list of choices is their sliding pill.
 *
 * What the rows set is Yuno's: playback, captions, SponsorBlock, Shorts, appearance
 * (System, Light, Dark, and liquid glass), language, notifications, about, account.
 */
enum class SettingsPage(@StringRes val titleRes: Int, val icon: ImageVector) {
    Playback(R.string.settings_playback, SfIcons.PlayCircle),
    Captions(R.string.captions_title, Icons.Rounded.ClosedCaption),
    SponsorBlock(R.string.settings_sponsorblock, SfIcons.FastForward),
    Shorts(R.string.ui_shorts, SfIcons.Shorts),
    Appearance(R.string.ui_appearance, SfIcons.Palette),
    Language(R.string.language_title, SfIcons.Language),
    Notifications(R.string.settings_notifications, SfIcons.Notifications),
    About(R.string.settings_about, Icons.Rounded.Info),
    Account(R.string.settings_account, SfIcons.Person),
}

private const val SourceUrl = "https://github.com/danielkanan/Yuno"

/** BitChord's settings type scale, drawn in this app's face. */
private val SettingsTypography = Typography(
    displayLarge = TextStyle(fontFamily = SfProDisplay, fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = SfProDisplay, fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = SfProDisplay, fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.25).sp),
    titleLarge = TextStyle(fontFamily = SfProDisplay, fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = SfProDisplay, fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = 0.05.sp),
    bodyLarge = TextStyle(fontFamily = SfProDisplay, fontWeight = FontWeight.W400, fontSize = 16.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = SfProDisplay, fontWeight = FontWeight.W400, fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = SfProDisplay, fontWeight = FontWeight.W600, fontSize = 12.sp, letterSpacing = 0.25.sp),
    labelSmall = TextStyle(fontFamily = SfProDisplay, fontWeight = FontWeight.W600, fontSize = 11.sp, letterSpacing = 0.3.sp),
)

@Composable
fun SettingsScreen(
    player: PlayerState,
    active: Boolean,
    onClose: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    @Suppress("UNUSED_PARAMETER") wide: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val mainScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val focusManager = LocalFocusManager.current
    var firstSearch by remember { mutableStateOf(true) }
    LaunchedEffect(query) {
        if (firstSearch) firstSearch = false else mainScroll.scrollTo(0)
    }

    val back = {
        when {
            page != null -> page = null
            query.isNotEmpty() -> query = ""
            else -> {
                focusManager.clearFocus(force = true)
                onClose()
            }
        }
    }
    BackHandler(enabled = active, onBack = back)

    MaterialTheme(colorScheme = c, typography = SettingsTypography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge.merge(OwnDirectionText)) {
            Box(Modifier.fillMaxSize().background(c.background).overlayTouchBlock()) {
                val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
                val haze = remember { HazeState() }
                val pageColor = c.background
                val paintBackdrop = remember(pageColor) {
                    val block: ContentDrawScope.() -> Unit = {
                        drawRect(pageColor)
                        drawContent()
                    }
                    block
                }
                val backdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
                Box(
                    Modifier
                        .fillMaxSize()
                        .hazeSource(haze)
                        .then(if (glass) Modifier.layerBackdrop(backdrop) else Modifier),
                ) {
                    AnimatedContent(
                        targetState = page,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val dir = if (targetState != null) 1 else -1
                            if (reduced) fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                            else (slideInHorizontally(tween(280)) { dir * it / 8 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200)) { -dir * it / 10 } + fadeOut(tween(160)))
                        },
                        label = "settingsPage",
                    ) { shown ->
                        val scroll = if (shown == null) mainScroll else remember(shown) { ScrollState(0) }
                        val bottom = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(scroll)
                                .padding(top = topBarHeight() + TopBarContentGap, bottom = bottom + 24.dp),
                        ) {
                            SettingsPageTitle(stringResource(shown?.titleRes ?: R.string.settings_title))
                            if (shown == null) {
                                MainPage(player, query, { query = it }, { page = it })
                            } else {
                                PageBody(shown, player, onSignIn, onSignedOut)
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalTopChrome provides null,
                    LocalAppBackdrop provides backdrop,
                    LocalAppHaze provides haze,
                ) {
                    PageTopBar(onBack = back)
                }
            }
        }
    }
}

@Composable
private fun MainPage(
    player: PlayerState,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    val search = SettingsSearch(query)
    val searching = query.isNotBlank()
    SearchField(
        query = query,
        onQueryChange = onQuery,
        onSubmit = {},
        placeholder = stringResource(R.string.settings_search),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
    )
    AccountRow(search, onOpen)
    if (!searching) {
        SettingsGroup {
            SettingsPage.entries.filter { it != SettingsPage.Account }.forEachIndexed { index, category ->
                if (index > 0) RowDivider()
                SettingsRow(
                    icon = category.icon,
                    title = stringResource(category.titleRes),
                    onClick = { onOpen(category) },
                )
            }
        }
    }
    // A query lays the matching rows out here, on the main page.
    if (searching) {
        PlaybackBody(player, search)
        CaptionsBody(search)
        SponsorBlockBody(search)
        ShortsBody(search)
        AppearanceBody(search)
        LanguageBody(search)
        NotificationsBody(player, search)
        AboutBody(player, search)
    }
    DeveloperBody(search) { player.actionMessage = it }
    if (searching && !search.anyMatch) {
        Text(
            stringResource(R.string.settings_no_results, query.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        )
    }
}

@Composable
private fun PageBody(
    page: SettingsPage,
    player: PlayerState,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val search = SettingsSearch("", inPage = true)
    when (page) {
        SettingsPage.Playback -> PlaybackBody(player, search)
        SettingsPage.Captions -> CaptionsBody(search)
        SettingsPage.SponsorBlock -> SponsorBlockBody(search)
        SettingsPage.Shorts -> ShortsBody(search)
        SettingsPage.Appearance -> AppearanceBody(search)
        SettingsPage.Language -> LanguageBody(search)
        SettingsPage.Notifications -> NotificationsBody(player, search)
        SettingsPage.About -> AboutBody(player, search)
        SettingsPage.Account -> AccountBody(onSignIn, onSignedOut)
    }
}

@Composable
private fun AccountRow(search: SettingsSearch, onOpen: (SettingsPage) -> Unit) {
    val profile = Account.profile
    SearchableSettingsGroup(search) {
        val title = profile?.name ?: stringResource(R.string.ui_sign_in)
        val subtitle = profile?.handle
            ?: stringResource(if (profile != null) R.string.settings_full_sync else R.string.ui_account_pitch_title)
        row(title, subtitle, stringResource(R.string.settings_account), "google", "sign") {
            SettingsRow(
                icon = if (profile == null) SfIcons.Person else null,
                iconContent = profile?.let { p ->
                    { ChannelAvatar(p.avatar, p.name, size = 22) }
                },
                title = title,
                subtitle = subtitle,
                onClick = { onOpen(SettingsPage.Account) },
            )
        }
    }
}

@Composable
private fun PlaybackBody(player: PlayerState, search: SettingsSearch) {
    val pipSupported = LocalPictureInPicture.current?.supported != false
    SearchableSettingsGroup(search, header = stringResource(R.string.settings_playback)) {
        val seekTitle = stringResource(R.string.settings_seek_step_title)
        val seekLine = stringResource(R.string.settings_seek_step_line)
        row(seekTitle, seekLine, "seconds", "double tap") {
            ChoiceRow(
                seekIcon(forward = true),
                seekTitle,
                seekLine,
                SeekStep.choices.map { stringResource(R.string.settings_seconds_short, it.toString()) },
                SeekStep.choices.indexOf(SeekStep.seconds).coerceAtLeast(0),
            ) { SeekStep.set(SeekStep.choices[it]) }
        }
        val chaptersTitle = stringResource(R.string.settings_chapters_open_title)
        val chaptersLine = stringResource(R.string.settings_chapters_open_line)
        row(chaptersTitle, chaptersLine) {
            ToggleRow(Icons.AutoMirrored.Rounded.FormatListBulleted, chaptersTitle, chaptersLine, ChaptersStayOpen.on) {
                ChaptersStayOpen.set(it)
            }
        }
        if (pipSupported) {
            val pipTitle = stringResource(R.string.settings_auto_pip_title)
            val pipLine = stringResource(R.string.settings_auto_pip_line)
            row(pipTitle, pipLine, "picture") {
                ToggleRow(Icons.Rounded.PictureInPictureAlt, pipTitle, pipLine, AutoPip.on) { AutoPip.set(it) }
            }
        }
        val speedTitle = stringResource(R.string.settings_speed_remember_title)
        val speedLine = if (PlaybackSpeed.remember) {
            stringResource(R.string.settings_speed_remember_on, PlaybackSpeed.label(PlaybackSpeed.saved))
        } else {
            stringResource(R.string.settings_speed_remember_off)
        }
        row(speedTitle, speedLine, stringResource(R.string.settings_speed_keep)) {
            ToggleRow(Icons.Rounded.Speed, speedTitle, speedLine, PlaybackSpeed.remember) {
                if (it != PlaybackSpeed.remember) player.toggleRememberSpeed()
            }
        }
        val musicTitle = stringResource(R.string.settings_music_normal_title)
        val musicLine = stringResource(R.string.settings_music_normal_line)
        row(musicTitle, musicLine) {
            ToggleRow(Icons.Rounded.MusicNote, musicTitle, musicLine, PlaybackSpeed.musicAtNormal) {
                PlaybackSpeed.chooseMusicAtNormal(it)
            }
        }
        val zoomTitle = stringResource(R.string.settings_zoom_fill_title)
        val zoomLine = stringResource(if (FullscreenZoom.fill) R.string.settings_zoom_fill_on else R.string.settings_zoom_fill_off)
        row(zoomTitle, zoomLine, "pinch", "fullscreen") {
            ToggleRow(Icons.Rounded.ZoomOutMap, zoomTitle, zoomLine, FullscreenZoom.fill) { player.setZoomFillSetting(it) }
        }
        val audioTitle = stringResource(R.string.bar_audio_only)
        val audioLine = stringResource(R.string.settings_audio_only_line)
        row(audioTitle, audioLine) {
            ToggleRow(Icons.Rounded.Headphones, audioTitle, audioLine, AudioOnly.enabled) { player.setAudioOnly(it) }
        }
    }
}

@Composable
private fun CaptionsBody(search: SettingsSearch) {
    val kept = CaptionTranslator.downloaded.filter { it != "en" }.sortedBy { CaptionNames.language(it) }
    SearchableSettingsGroup(search, header = stringResource(R.string.captions_title)) {
        row(stringResource(R.string.captions_title), "preview") {
            Box(Modifier.padding(8.dp)) { CaptionPreview() }
        }
        val showTitle = stringResource(R.string.captions_show)
        row(showTitle, captionShowLabel(CaptionSettings.show)) {
            ChoiceRow(
                Icons.Rounded.ClosedCaption,
                showTitle,
                null,
                CaptionSettings.Show.entries.map { captionShowLabel(it) },
                CaptionSettings.Show.entries.indexOf(CaptionSettings.show),
            ) { CaptionSettings.chooseShow(CaptionSettings.Show.entries[it]) }
        }
        val lookTitle = stringResource(R.string.captions_look)
        row(lookTitle) {
            ChoiceRow(
                Icons.Rounded.ClosedCaption,
                lookTitle,
                null,
                CaptionSettings.Look.entries.map { stringResource(it.labelRes) },
                CaptionSettings.Look.entries.indexOf(CaptionSettings.look),
            ) { CaptionSettings.chooseLook(CaptionSettings.Look.entries[it]) }
        }
        val sizeTitle = stringResource(R.string.captions_size)
        row(sizeTitle) {
            ChoiceRow(
                Icons.Rounded.ClosedCaption,
                sizeTitle,
                null,
                CaptionSettings.sizes.map { stringResource(it.second) },
                CaptionSettings.sizes.indexOfFirst { it.first == CaptionSettings.size }.coerceAtLeast(0),
            ) { CaptionSettings.chooseSize(CaptionSettings.sizes[it].first) }
        }
        if (kept.isNotEmpty()) {
            val keptTitle = stringResource(R.string.captions_kept_languages)
            row(keptTitle, *kept.map { CaptionNames.language(it) }.toTypedArray()) {
                Column(Modifier.padding(horizontal = ROW_INSET, vertical = 12.dp)) {
                    Text(keptTitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        kept.forEach { lang ->
                            val name = CaptionNames.language(lang)
                            Row(
                                Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.outline)
                                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .pressable { CaptionTranslator.delete(lang) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(SfIcons.Close, stringResource(R.string.captions_remove_language, name), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsorBlockBody(search: SettingsSearch) {
    SearchableSettingsGroup(
        search,
        header = stringResource(R.string.settings_sponsorblock),
        footer = stringResource(R.string.ui_sponsorblock_credit),
    ) {
        val title = stringResource(R.string.settings_sponsorblock)
        val line = stringResource(R.string.ui_sponsorblock_line)
        row(title, line) {
            ToggleRow(SfIcons.FastForward, title, line, SponsorBlock.enabled) { SponsorBlock.updateEnabled(it) }
        }
        SponsorBlock.Category.entries.forEach { category ->
            row(category.label, category.short, "sponsorblock") {
                ChoiceRow(
                    icon = SfIcons.FastForward,
                    title = category.label,
                    subtitle = null,
                    options = SponsorBlock.Action.entries.map { it.label },
                    selectedIndex = SponsorBlock.Action.entries.indexOf(SponsorBlock.actionFor(category)).coerceAtLeast(0),
                    enabled = SponsorBlock.enabled,
                    iconContent = {
                        Box(Modifier.size(ICON_SIZE), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(category.color))
                        }
                    },
                ) { SponsorBlock.setAction(category, SponsorBlock.Action.entries[it]) }
            }
        }
    }
}

@Composable
private fun ShortsBody(search: SettingsSearch) {
    val context = LocalContext.current
    SearchableSettingsGroup(search, header = stringResource(R.string.ui_shorts)) {
        val title = stringResource(R.string.ui_shorts)
        val line = stringResource(if (ShortsSetting.enabled) R.string.ui_shorts_on_line else R.string.ui_shorts_off_line)
        row(title, line) {
            ToggleRow(SfIcons.Shorts, title, line, ShortsSetting.enabled) { ShortsSetting.set(context, it) }
        }
    }
}

@Composable
private fun AppearanceBody(search: SettingsSearch) {
    SearchableSettingsGroup(search, header = stringResource(R.string.ui_appearance)) {
        val modeTitle = stringResource(R.string.settings_mode)
        row(modeTitle, stringResource(R.string.ui_mode_system), stringResource(R.string.ui_mode_light), stringResource(R.string.ui_mode_dark)) {
            ChoiceRow(
                Icons.Rounded.DarkMode,
                modeTitle,
                null,
                listOf("system", "light", "dark").map { modeLabel(it) },
                listOf("system", "light", "dark").indexOf(ThemeSettings.mode).coerceAtLeast(0),
            ) { ThemeSettings.chooseMode(listOf("system", "light", "dark")[it]) }
        }
        if (isGlassSupported()) {
            val glassTitle = stringResource(R.string.settings_liquid_glass)
            val glassLine = stringResource(R.string.settings_liquid_glass_line)
            row(glassTitle, glassLine, "blur") {
                ToggleRow(SfIcons.Appearance, glassTitle, glassLine, LiquidGlassSetting.enabled, onCheckedChange = LiquidGlassSetting::set)
            }
        }
    }
}

@Composable
private fun LanguageBody(search: SettingsSearch) {
    val context = LocalContext.current
    val chosen = AppLanguage.pickedChoice
    val options = listOf("" to phoneLanguageLabel()) + AppLanguage.choices.map { it.tag to it.name }
    if (search.inPage) {
        ChoiceGroup(
            options = options,
            selected = options.firstOrNull { it.first == chosen } ?: (chosen to ""),
            label = { it.second },
            footer = stringResource(R.string.language_line),
        ) { (tag, _) -> context.findActivity()?.let { AppLanguage.pick(it, tag) } }
        return
    }
    SearchableSettingsGroup(search, header = stringResource(R.string.language_title)) {
        options.forEach { (tag, label) ->
            row(label, stringResource(R.string.language_title)) {
                SettingsRow(
                    icon = SfIcons.Language,
                    title = label,
                    showChevron = false,
                    onClick = { context.findActivity()?.let { AppLanguage.pick(it, tag) } },
                )
            }
        }
    }
}

@Composable
private fun NotificationsBody(player: PlayerState, search: SettingsSearch) {
    val context = LocalContext.current
    val toggle = rememberBellToggle(onMessage = { player.actionMessage = it })
    val channels = remember { Bells.on.values.sortedBy { it.name?.lowercase() ?: it.id } }
    SearchableSettingsGroup(search, header = stringResource(R.string.settings_notifications)) {
        if (channels.isEmpty()) {
            row(stringResource(R.string.settings_bells_empty)) {
                Text(
                    stringResource(R.string.settings_bells_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ROW_INSET, vertical = 14.dp),
                )
            }
        }
        channels.forEach { channel ->
            val on = Bells.isOn(channel.id)
            val name = channel.name ?: channel.id
            row(name, stringResource(R.string.settings_bell_line), "bell") {
                SettingsRow(
                    title = name,
                    subtitle = stringResource(if (on) R.string.settings_bell_line else R.string.settings_off),
                    iconContent = { ChannelAvatar(channel.avatar, name, size = 22) },
                    trailing = {
                        IosSwitch(checked = on, onCheckedChange = { toggle(channel) })
                    },
                    onClick = { toggle(channel) },
                )
            }
        }
        val systemTitle = stringResource(R.string.settings_system_notifications)
        val systemLine = stringResource(R.string.settings_system_notifications_line)
        row(systemTitle, systemLine) {
            SettingsRow(icon = Icons.Rounded.Notifications, title = systemTitle, subtitle = systemLine, onClick = {
                context.openOrSay(
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                ) { player.actionMessage = it }
            })
        }
    }
}

@Composable
private fun AboutBody(player: PlayerState, search: SettingsSearch) {
    val context = LocalContext.current
    var whatsNew by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.settings_crash_share)
    SearchableSettingsGroup(search, header = stringResource(R.string.settings_about)) {
        val versionTitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
        row(versionTitle, updateStatus(), "update") {
            SettingsRow(icon = Icons.Rounded.SystemUpdateAlt, title = versionTitle, subtitle = updateStatus(), onClick = {
                if (AppUpdate.available != null) whatsNew = true else AppUpdate.checkNow()
            })
        }
        val crashTitle = stringResource(R.string.settings_crash_title)
        val crashLine = stringResource(if (CrashLog.newest > 0L) R.string.settings_crash_saved else R.string.settings_crash_none)
        row(crashTitle, crashLine) {
            SettingsRow(icon = Icons.Rounded.BugReport, title = crashTitle, subtitle = crashLine, onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { CrashLog.report(context) }
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Yuno crash report")
                        .putExtra(Intent.EXTRA_TEXT, text)
                    context.openOrSay(Intent.createChooser(send, shareTitle)) { player.actionMessage = it }
                    CrashLog.markShared()
                }
            })
        }
        val sourceTitle = stringResource(R.string.settings_source)
        val sourceLine = stringResource(R.string.settings_source_line)
        row(sourceTitle, sourceLine, "github") {
            SettingsRow(icon = Icons.Rounded.Code, title = sourceTitle, subtitle = sourceLine, onClick = {
                context.openOrSay(Intent(Intent.ACTION_VIEW, Uri.parse(SourceUrl))) { player.actionMessage = it }
            })
        }
    }
    if (whatsNew) AppUpdate.available?.let { WhatsNewSheet(it, onDismiss = { whatsNew = false }) }
}

@Composable
private fun AccountBody(onSignIn: () -> Unit, onSignedOut: () -> Unit) {
    val profile = Account.profile
    if (profile == null) {
        SettingsGroup(footer = stringResource(R.string.ui_account_pitch_body), topSpacing = 0.dp) {
            SettingsRow(
                icon = SfIcons.Person,
                title = stringResource(R.string.ui_sign_in_with_google),
                onClick = onSignIn,
            )
        }
        return
    }
    SettingsGroup(footer = stringResource(R.string.settings_sync_note), topSpacing = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(profile.avatar, profile.name, size = 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                profile.handle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        RowDivider()
        DestructiveRow(stringResource(R.string.ui_sign_out)) {
            Account.signOut()
            onSignedOut()
        }
    }
}

@Composable
private fun DeveloperBody(search: SettingsSearch, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val playerMessage = onMessage
    val instagram = rememberBrandIcon("Instagram", InstagramPath)
    val threads = rememberBrandIcon("Threads", ThreadsPath)
    val x = rememberBrandIcon("X", XPath)
    SearchableSettingsGroup(
        search,
        header = stringResource(R.string.settings_made_by, "@y7esdii"),
        footer = stringResource(R.string.settings_say_hi),
    ) {
        row("Instagram") {
            SettingsRow(icon = instagram, title = "Instagram", onClick = {
                context.openOrSay(Intent(Intent.ACTION_VIEW, Uri.parse(InstagramUrl)), playerMessage)
            })
        }
        row("Threads") {
            SettingsRow(icon = threads, title = "Threads", onClick = {
                context.openOrSay(Intent(Intent.ACTION_VIEW, Uri.parse(ThreadsUrl)), playerMessage)
            })
        }
        row("X", "twitter") {
            SettingsRow(icon = x, title = "X", onClick = {
                context.openOrSay(Intent(Intent.ACTION_VIEW, Uri.parse(XUrl)), playerMessage)
            })
        }
        val email = stringResource(R.string.settings_email_developer)
        row(email, DeveloperEmail, "email") {
            SettingsRow(icon = Icons.Rounded.MailOutline, title = email, onClick = {
                context.openOrSay(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DeveloperEmail")).putExtra(Intent.EXTRA_SUBJECT, EmailSubject),
                    playerMessage,
                )
            })
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        trailing = { IosSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        onClick = if (enabled) {{ onCheckedChange(!checked) }} else null,
    )
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    iconContent: (@Composable () -> Unit)? = null,
    onSelect: (Int) -> Unit,
) {
    SettingsRow(icon = icon, title = title, subtitle = subtitle, enabled = enabled, iconContent = iconContent)
    SegmentedControl(
        options = options,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        enabled = enabled,
        modifier = Modifier.padding(start = ROW_INSET, end = ROW_INSET, bottom = 14.dp),
    )
}

@Composable
private fun captionShowLabel(show: CaptionSettings.Show): String = when (show) {
    CaptionSettings.Show.Never -> stringResource(R.string.captions_show_never)
    CaptionSettings.Show.Always -> stringResource(R.string.captions_show_always)
    CaptionSettings.Show.Foreign -> stringResource(R.string.captions_show_foreign, CaptionNames.language(CaptionSettings.appLanguage))
}

@Composable
private fun modeLabel(mode: String): String = when (mode) {
    "light" -> stringResource(R.string.ui_mode_light)
    "dark" -> stringResource(R.string.ui_mode_dark)
    else -> stringResource(R.string.ui_mode_system)
}

@Composable
private fun phoneLanguageLabel(): String {
    val phone = AppLanguage.phoneLocale
    val name = AppLanguage.choices.firstOrNull { java.util.Locale.forLanguageTag(it.tag).language == phone.language }?.name
        ?: phone.getDisplayLanguage(phone).replaceFirstChar { it.titlecase(phone) }
    return stringResource(R.string.language_phone_named, name)
}

@Composable
private fun updateStatus(): String = AppUpdate.available?.let { stringResource(R.string.settings_update_available, it.version) }
    ?: stringResource(if (AppUpdate.checking) R.string.settings_checking else R.string.settings_up_to_date)

@Composable
private fun rememberBrandIcon(name: String, path: String): ImageVector = remember(name) {
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(path), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        .build()
}

private fun android.content.Context.openOrSay(intent: Intent, onMessage: (String) -> Unit) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: android.content.ActivityNotFoundException) {
        onMessage(str(R.string.settings_no_app))
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val InstagramUrl = "https://www.instagram.com/y7esdii?igsh=MWdydmU1a216NDlydA=="
private const val ThreadsUrl = "https://www.threads.com/@y7esdii"
private const val XUrl = "https://x.com/y7esdii"
private const val DeveloperEmail = "decioarruda7c@gmail.com"
private const val EmailSubject = "Yuno feedback"

private const val InstagramPath =
    "M12 0C8.74 0 8.333.015 7.053.072 5.775.132 4.905.333 4.14.63c-.789.306-1.459.717-2.126 1.384S.935 3.35.63 4.14C.333 4.905.131 5.775.072 7.053.012 8.333 0 8.74 0 12s.015 3.667.072 4.947c.06 1.277.261 2.148.558 2.913.306.788.717 1.459 1.384 2.126.667.666 1.336 1.079 2.126 1.384.766.296 1.636.499 2.913.558C8.333 23.988 8.74 24 12 24s3.667-.015 4.947-.072c1.277-.06 2.148-.262 2.913-.558.788-.306 1.459-.718 2.126-1.384.666-.667 1.079-1.335 1.384-2.126.296-.765.499-1.636.558-2.913.06-1.28.072-1.687.072-4.947s-.015-3.667-.072-4.947c-.06-1.277-.262-2.149-.558-2.913-.306-.789-.718-1.459-1.384-2.126C21.319 1.347 20.651.935 19.86.63c-.765-.297-1.636-.499-2.913-.558C15.667.012 15.26 0 12 0zm0 2.16c3.203 0 3.585.016 4.85.071 1.17.055 1.805.249 2.227.415.562.217.96.477 1.382.896.419.42.679.819.896 1.381.164.422.36 1.057.413 2.227.057 1.266.07 1.646.07 4.85s-.015 3.585-.074 4.85c-.061 1.17-.256 1.805-.421 2.227-.224.562-.479.96-.899 1.382-.419.419-.824.679-1.38.896-.42.164-1.065.36-2.235.413-1.274.057-1.649.07-4.859.07-3.211 0-3.586-.015-4.859-.074-1.171-.061-1.816-.256-2.236-.421-.569-.224-.96-.479-1.379-.899-.421-.419-.69-.824-.9-1.38-.165-.42-.359-1.065-.42-2.235-.045-1.26-.061-1.649-.061-4.844 0-3.196.016-3.586.061-4.861.061-1.17.255-1.814.42-2.234.21-.57.479-.96.9-1.381.419-.419.81-.689 1.379-.898.42-.166 1.051-.361 2.221-.421 1.275-.045 1.65-.06 4.859-.06l.045.03zm0 3.678c-3.405 0-6.162 2.76-6.162 6.162 0 3.405 2.76 6.162 6.162 6.162 3.405 0 6.162-2.76 6.162-6.162 0-3.405-2.76-6.162-6.162-6.162zM12 16c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4zm7.846-10.405c0 .795-.646 1.44-1.44 1.44-.795 0-1.44-.646-1.44-1.44 0-.794.646-1.439 1.44-1.439.793-.001 1.439.645 1.439 1.439z"
private const val XPath =
    "M18.901 1.153h3.68l-8.04 9.19L24 22.846h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932ZM17.61 20.644h2.039L6.486 3.24H4.298Z"
private const val ThreadsPath =
    "M12.186 24h-.007c-3.581-.024-6.334-1.205-8.184-3.509C2.35 18.44 1.5 15.586 1.472 12.01v-.017c.03-3.579.879-6.43 2.525-8.482C5.845 1.205 8.6.024 12.18 0h.014c2.746.02 5.043.725 6.826 2.098 1.677 1.29 2.858 3.13 3.509 5.467l-2.04.569c-1.104-3.96-3.898-5.984-8.304-6.015-2.91.022-5.11.936-6.54 2.717C4.307 6.504 3.616 8.914 3.589 12c.027 3.086.718 5.496 2.057 7.164 1.43 1.783 3.631 2.698 6.54 2.717 2.623-.02 4.358-.631 5.8-2.045 1.647-1.613 1.618-3.593 1.09-4.798-.31-.71-.873-1.3-1.634-1.75-.192 1.352-.622 2.446-1.284 3.272-.886 1.102-2.14 1.704-3.73 1.79-1.202.065-2.361-.218-3.259-.801-1.063-.689-1.685-1.74-1.752-2.964-.065-1.19.408-2.285 1.33-3.082.88-.76 2.119-1.207 3.583-1.291a13.853 13.853 0 0 1 3.02.142c-.126-.742-.375-1.332-.75-1.757-.513-.586-1.308-.883-2.359-.89h-.029c-.844 0-1.992.232-2.721 1.32L7.734 7.847c.98-1.454 2.568-2.256 4.478-2.256h.044c3.194.02 5.097 1.975 5.287 5.388.108.046.216.094.321.142 1.49.7 2.58 1.761 3.154 3.07.797 1.82.871 4.79-1.548 7.158-1.85 1.81-4.094 2.628-7.277 2.65Zm1.003-11.69c-.242 0-.487.007-.739.021-1.836.103-2.98.946-2.916 2.143.067 1.256 1.452 1.839 2.784 1.767 1.224-.065 2.818-.543 3.086-3.71a10.5 10.5 0 0 0-2.215-.221Z"
