# Yuno — keep on Skipii upstream merges

Edit when you want something new protected. Cursor rule: `.cursor/rules/yuno-upstream-preserve.mdc`.

## Product & branding

- App name **Yuno**; package **`com.yuno`**.
- Self-update: users always get APKs from **`danielkanan/Yuno`** (`GITHUB_RELEASES_REPO` in `app/build.gradle.kts`, read by `Updater.kt`) — merge upstream `Updater.kt` fixes freely, never point releases at Skipii.
- No upload/Create; tabs Home · Subscriptions · Shorts · Search · Library (Shorts optional).

## Look & feel (keep on merge)

- Liquid glass floating tab bar + **liquid glass setting** on Settings page.
- System / Light / Dark only — **no** colour palette chips or wallpaper themes.
- **Settings page** (`Settings.kt`) — not a legacy account settings sheet; don’t re-add appearance chip rows.
- Icons, font weights, `FadingLine` truncation, sharp thumbnails.
- Player: **on-video** controls + **edge scrub**; **no** player bar on tabs; **no** “controls in the bar” setting.
- **Mini-player:** glass accessory, fading titles, morph — keep Yuno.
- **Shorts:** keep Yuno’s glass rail. Drag-to-seek, the double-tap thumb burst, and the progress line are copied from upstream `Shorts.kt`, not rewritten.
- **Player:** on-video controls are upstream `VideoView` (tiles, seek step, fullscreen cards, tap waits so a double tap only seeks). Seek icons are upstream `seekIcon`, including 15/20/25, and they follow the setting. Zoom scales the picture only; captions stay on the unzoomed box. Portrait edge scrub stays. No PlayerBar and no “controls in the bar” setting.
- **Pages:** Settings uses the app background (`c.background`), the same as the rest of the shell.

## Shell & motion (keep Yuno)

- Mini-player ↔ player morph, headers, vignettes, no ripples, `pressable`.

## Your changes — ask only if an update touches them

When you change or add something in Yuno (a layout, a screen, a setting, a behavior), write it here **and** in `.cursor/rules/yuno-upstream-preserve.mdc` in the same turn, without being asked. One line: what changed, and where it lives. Leave it in this list until you decide.

On a later Skipii merge, ask only if that update **conflicts with or rewrites** the change. If Skipii does not touch it, keep it and do not ask. When it does collide, ask: **keep** it, or **let Skipii replace it**?

- **Keep** — move the line into **Look & feel** / **Always keep**.
- **Let Skipii replace it** — delete the line and take upstream there.

- The Home update pill keeps 8dp before the search and account buttons (`PageTopBar` in `Header.kt`).
- There is no audio only mode: no setting, no headphones button, and the player does not drop the video track for it (`Settings.kt`, `VideoView.kt`, `Tabletop.kt`, `PlayerState.kt`).
- A video wider than 16:9 keeps the normal stage and letterboxes (`StageMaxAspect` in `PlayerUi.kt`). A Short fits inside the screen with black bars (`ShortPage` in `Shorts.kt`) instead of being enlarged to fill it.
- Search is Flow's search (`SearchPage.kt`, `SearchFilter.kt`, `YouTubeSearchParams.kt`): while typing, history and suggestions share the page, a suggestion's corner puts the text in the field, and Back returns to the results already open. Submitted results have Flow's type chips (All, Shorts, Videos, Live, Channels, Playlists, Movies, then recently uploaded, unwatched and watched), drawn with the top bar's liquid glass (`floatingChrome`), in the top chrome just under the bar, with the results scrolling behind that bar and the chips. Scrolling down folds that row into the bar, to the right of the field, on the mini player's scroll (`FloatingTabBarScrollConnection`) and the same spring. Only the content-type chip stays, at the same height as the bar buttons, and a tap on it brings the row back the way a tap on the minimised nav does; any other chip, including a second selected one, leaves with the nav tab's fade and 12dp blur, and scrolling up brings them back, and the filter button opens BitChord's menu (`SearchFilterMenu`, `IosMenuPopover`) just under that button, grown from its top-right corner: type, duration, upload date, watched and sort, then features, each group a list with a check on the chosen line. Videos are the home card. There is no layout switch. A search that is still loading keeps the skeletons, so a cancelled request cannot flash "Search didn't load" (`SearchState` in `Screens.kt`). Results follow Flow's search page (`parseSearch`, `foldChannelLatest`, `asShortsResults`): a Shorts, video, or posts shelf stays a shelf instead of being flattened into the list, the video shelf directly after a channel is that channel's latest row, and the Shorts chip is only the shorts from the shorts shelves. Watched keeps videos and drops channels, playlists and empty shelves; Unwatched keeps the channels and playlists. Channel results are a full-width row (`SearchChannelRow`) with 12dp top and 12dp bottom, the picture and name filling the row, and the latest videos under it when YouTube sent them. Playlists are the grid card (`SearchPlaylistCard`). Video shelves use the home card, collapsed to YouTube's count, with show more. A shelf draws one rule, so two shelves in a row share that line (`SearchShelfBlock`). The Shorts strip has 16dp above its heading and 16dp under the posters, and that heading is the title with no Shorts icon. That rule keeps 16dp before the next video, channel, or playlist, so it does not sit on that row. A posts card follows the channel post (`SearchPostCard`): author and time, the text, a full-width picture, then likes and comments. Cards in that row share the tallest card's height, with the likes row along the bottom. Channels, playlists and Shorts in the results open those pages. A pasted YouTube link opens its video, Short, channel or playlist. Pull to refresh is not on this page. The field stays in the shared top bar.
- Playlist pages use BitChord's release header (`PlaylistPage.kt`): the page is the artwork's colour when channel colors are on in Settings › Appearance (the same switch as channel pages, off by default) and the plain background when that switch is off, the cover is a 16:9 thumbnail the width of the page inside `PageGutter`, then Flow's title block (`PlaylistTitleBlock`): the name and the line under it are left aligned, and that line is the channel, a bullet, the video count, a bullet, and the running time (`mediaLengthLabel`, once every page is in). A description, when YouTube sent one (`parsePlaylistHeader`), shows two lines and opens on a tap. Then a white Play pill and a glass Shuffle pill, each half of the page width inside `PageGutter`. Play starts the queue at the tapped video; Shuffle plays a shuffled copy. Videos under that are the channel list rows, or the home card when the grid is on. The layout switch starts on the grid. The top bar's `LayoutAction` switches them, and `ShareAction` shares the playlist. Pull to refresh stays the circular indicator.
- Loading skeletons (`Skeleton.kt`) on feeds, the channel page, and comments. Channel placeholders match the tab they stand in for: the opening page is the header, the tab row, a `PageGutter`, then the home cards; Videos and Live stay those rows, or the home card when the grid is on; Shorts is the 160dp grid with the title under the poster and the 12dp gaps; Playlists, Shows and Podcasts stay the 142dp rows, or the inset card in the grid; Posts keep the 12dp gap above each one. Spinners stay for playback, sign-in, and posting.
- Settings has no developer card: no "Made by @…", "Come say hi", or social links (`Settings.kt`).
- Liquid glass stays on across launches (`LiquidGlassSetting.init` in `YunoApplication`).
- Page changes use BitChord's switcher (`AppNavigator`, wired in `MainActivity`): tabs cut with no fade, and search, settings, a settings section, a playlist, and a channel crossfade in 180ms. The top bar stays outside that switcher. Its actions pill is BitChord's `ArtworkPageActions` (`PageActions` in `Header.kt`): the glass sits outside their no-bounce spring, and `ActionsRow` opens each gap with the action after it, so the pill shrinks and grows instead of the buttons clipping. The tab bar stays up on those pages.
- The hold menu lifts BitChord's picture (`HoldCapture` in `HeldMenu.kt`, wired from `pressable`): the row is not press-scaled, and its horizontal gutter is trimmed off the picture (`liftPadding`, `PageGutter` on `VideoCard`).
- Yuno's own version, not Skipii's. First release is `versionName` 1.0.0 and `versionCode` 1 in `app/build.gradle.kts`. A Skipii merge must not put their version back.
- Settings uses BitChord's settings chrome throughout (`Settings.kt`, `SettingsChrome.kt`, `IosSwitch.kt`, `PillSegmentedControl.kt`): floating top bar, their padding and grouped cards, the press-stretch switch, and the sliding segment pill. Yuno's own settings stay (playback, captions, SponsorBlock, Shorts, appearance as System/Light/Dark plus channel and playlist colors and liquid glass, language, notifications, about, account). The settings page draws `PageTopFade` in the page colour once its title has scrolled away (`PageTopFade(cover = …)` in `Header.kt`).
- Signed-out Home still shows recommendations (`InnerTube.guestHome` in `Account.kt`, chosen in `LibraryData` in `Feeds.kt`). Anonymous home browse is a sign-in wall, so guests search for popular videos.
- Channel pages use BitChord's artist header (`Channel.kt`, `ArtworkPalette.kt`): the page is the colour that shows up most in the profile picture and the banner the whole way down, any hue, light enough to read, and the plain background when Settings › Appearance has channel colors off (off by default) or both pictures are white, black, or grey (`ChannelColorSetting`); the banner sits below the top bar, a bit taller than the 35% step, with a smaller corner radius and no dissolve; the profile picture sits on the left of the name, the @handle and the subscriber count, and those three share the picture's height, spaced evenly; Subscribe is a full-width glass pill that says Subscribe, and the bell is the artist page's glass circle at the end of that row once subscribed; the description lives on the About page; the tabs are Flow's scrollable tab row and the pages swipe (`ChannelFlow.kt`, `Channel.kt`); Live, Shows and Podcasts are included when the channel publishes them, read from the tab URL the way Flow does (`toChannelTab` in `Account.kt`) — Live is the videos list with streams kept, and Shows and Podcasts are the playlist rows; About (with the channel's links) and Posts are their own pages; Latest / Popular / Oldest is a menu and the other filters are chips; videos are 168dp rows, shorts are a 160dp grid with the title under the poster, and playlists are 142dp rows; the line under a video title, the tabs, See all, and those chips use BitChord's artist-line secondary (`ArtworkPalette.onBackgroundVariant`); the bar is the shared floating top bar with share. An empty tab, playlist, or search is one centred line. The header collapses with that page's own scroll (`channelScrollHead`), one motion back to the top. A swipe stays level with the list you left, then the tab you stop on glides back to the top. Another swipe cancels that glide, so a tab you pass through does not keep scrolling; a channel with no Home opens on Videos at the top. Every tab's content is inset by `PageGutter`, and the gap under the tab row — and under the filter chips — is `PageGutter` too, with or without chips. Home is Flow's shelf list (`homeTab`): the featured video is the home page's card (`ChannelFeedVideo`), vertical rows with four showing and an expander, and a horizontal Shorts strip whose heading is the title, with no Shorts icon. Videos and Live list view is the 168dp rows (`ChannelVideoRow`); their grid is the home page's card (`ChannelFeedVideo`). Playlists, Shows and Podcasts stay 142dp rows in list; their grid is that same inset card with the count badge (`ChannelPlaylistCard`). The header button (`LayoutAction`) switches list and grid, and it starts on the grid. The filter chips, the sort menu and the search button scroll away with the videos (they are not sticky) and travel with the Videos page while you swipe. They are the subscribe pill, liquid glass while that is on. Latest / Popular / Oldest is Flow's sheet dropdown (`parseChannelFilters`), including channels that also have Members only and Public. The sort menu is BitChord's song ⋮ popup (`IosMenuPopover` in `IosMenu.kt`, hung off the chip in `ChannelSortMenuOverlay`): the frost panel, continuous corner, 17sp row and check, sitting just under the chip and grown from its top-left corner. Videos search is that circle at the end of the filter row. Tapping it clears the chips, puts the field to the left of the circle, and turns the icon into an X that closes it. Posts keep the gap between them.
- Home pull-to-refresh stretches the top bar (more top and bottom padding — about 6dp more at the reload point, and it still grows if you pull past while the wordmark size and fill stay put — wordmark grows to the right from the gutter, fades to 20% with a white left-to-right fill that finishes at the size limit, haptic when full, then the wordmark fills and empties starting fill-unfill-fill, then unfill-fill with a half-second hold between those, and after reload it finishes that pass through to a full fill before returning to normal with a click and a quick scale jump; a pull during that animation still grows the bar and clicks, and letting go of a pull that started during the reload does not start another one) via `BarPullRefresh.kt`, wired in `HomeScreen` (wordmark), `SubscriptionsScreen` and `LibraryScreen` (page titles), `PageTopBar`, `LargePageBrandMark`, and `LargePageTitle`. Playlist pages still use the circular indicator.
- Autoplay (Settings › Playback, `Autoplay` in `PlaybackModes.kt`, on by default): when the last video in the queue ends, `Autoplayer` in `PlaybackService.kt` adds and starts the first related video (`YouTube.related`, no Shorts or live) that hasn't played in the last 30.
- Mini-player sideways swipes (`miniPlayerPullGestures` in `MiniPlayerGestures.kt`, wired in `GlassNav.kt` and `PlayerUi.kt`): swipe right closes the video (the bar slides off and fades), swipe left plays/pauses it (the bar nudges and springs back), with a haptic tick. Up still opens, down still closes. As the bar slides, the action shows in the gap it leaves (`drawBehind` in `miniPlayerPullGestures`): like swiping an email away, the bar's shape fills behind it in green with a white pause/play icon on the right (sliding left) or red with a white close icon on the left (sliding right), deepening as the swipe grows, then going full colour with an icon pop and a tick once letting go would act. After a close the bar holds where it left while it exits instead of snapping back.
- YouTube links open in Yuno (`YouTubeLinks` in `Links.kt`, intent filters in `AndroidManifest.xml`, read in `MainActivity` through `NewVideos.opening`): youtube.com / youtu.be / m. / music. video and Shorts links, by "Open with" or the share sheet.
- Dislike counts from Return YouTube Dislike (`ReturnDislike.kt`, `PlayerState.displayDislikes`, label on the dislike pill in `PlayerUi.kt`); switch in Settings › Playback, on by default.
- Loop button on the video (`OverlayTile` in `VideoView.kt`, `PlayerState.toggleLoop`): repeat-one for the open video, turned off when another video opens.
- Quality on mobile data (`QualityPreference.mobile` in `YouTube.kt`, row in Settings › Playback): Same · Auto · 360p · 480p · 720p; the quality sheet's pick is kept for the network it was made on.
- Signed in, "More videos" and autoplay come from the account (`InnerTube.personalUpNext` in `Account.kt`, used by `PlayerState.loadPersonalRelated` and `Autoplayer.pickNext`): a song gets the account's Mix (`RD` + id), other videos YouTube's own signed-in up next; NewPipe's related list is the fallback.
- Downloads and Incognito (`Downloads.kt` with `DownloadService`, offline paths in `YouTubeMediaSourceFactory`, `SessionCallback.onAddMediaItems` and `PlayerState.show`; `Incognito` in `PlaybackModes.kt`): save a video (Audio only / 360p / 720p / 1080p) from the player's Download pill or the long-press menu, play it with no connection from Library › Downloads; Incognito (Settings, Library banner) stops watch-time pings and Yuno's watch and search history.
- "More videos" beside the video on a wide window held sideways (`PlayerScreen` in `PlayerUi.kt`, `SideListMinWindow` 840dp and wider than tall): the related list is a column of `VideoCard`s on the right, 30% of the width (320–420dp), and the details stay under the video. Narrower or upright, the list stays under the details.
- Audio language (`AudioLanguage` and `StreamSelection.audioTracks` / `pick` in `YouTube.kt`, `PlayerState.setAudioLanguage`, rows in `QualitySheet` and `QualityDrop` in `VideoView.kt`): a video with more than one audio language lists them under Quality, the original first; the pick is kept and used on every video that has it, and the original's row clears it. Described-audio tracks are not listed.
- Fullscreen speed slider (`DropSpeedSlider` in `SpeedDrop`, `VideoView.kt`): a line from 0.5× to 3× above the presets, 0.05× steps, live while sliding. Portrait keeps the ruler.
- The fullscreen brightness / volume level is a horizontal pill at the top of the video, in the middle, for both gestures (`SliderLevel` in `FullscreenSliders.kt`): not under the hand making the swipe, and clear of the play and seek buttons.
- A livestream plays from its DASH manifest URL, or HLS when that URL is missing, using MeeTube's live manifest parser (`LiveDashManifestParser`, wired in `YouTubeMediaSourceFactory`). The watch page shows the watching count and live chat (`Live.kt`). A finished livestream stays on the ordinary player. A live thumbnail shows LIVE in the duration's corner (`DurationPill`, `VideoItem.live`). A live bar with no rewind stays full and says LIVE; one you can rewind shows the time and MeeTube's red LIVE chip (`VideoView.kt`, `jumpToLiveEdge`). A refusal mid-watch reloads the manifest at the live edge (`StreamRecoverer`).
- Home recommendation tabs use the channel page's tab row (`ChannelTabRow`, from `HomeChips` in `Screens.kt`). They scroll away with the open list and clear the top of the screen, with the hairline and a `PageGutter` before the videos. The gap under the wordmark before the tabs is 8dp tighter than under a page title (`contentGap` 4.dp in `HomeChipPager`). A long name keeps 16dp on each side so it does not meet the next tab. Swiping the feed pages between them (`HomeChipPager`); each category keeps its own list (`LibraryData.category`). A swipe stays level with the list you left, then the tab you stop on glides back to the top (`animateScrollToItem`). Another swipe cancels that glide, so a page you pass through does not keep scrolling. A fresh open lays the list out with the header already measured, so the wordmark and tabs are on screen on the first frame (`SubcomposeLayout` in `HomeChipPager`).
- Back from a video, Short, channel, or playlist returns to the page it was opened from, still at the same scroll. A Short no longer clears the stack (`shortsCovering` in `MainActivity.kt`); the player no longer closes Search. Scroll lives on the feed (`FeedState.scroll`, `LibraryData.libraryScroll` and `homePage`), on search (`SearchState.resultsList` / `shortsGrid`), and on each channel tab (`ChannelState`).
- Tapping a video opens the full player from that thumbnail, in a straight line. Tapping the video already in the mini player just expands it. The mini player stays hidden until you minimise (`VideoLaunch` in `PlayerDock.kt`, `PlayerState.openRequest`, thumbnails in `UiKit.kt`, `ChannelFlow.kt`, `Screens.kt`, `SearchPage.kt`, morph in `PlayerUi.kt`). Switching videos takes the status-bar blur from the new thumbnail until that video's own frame (`VideoTopSampler` in `VideoStatusBlend.kt`).

## Ask on each merge

- Playback, extraction, bot-check, SponsorBlock **service**, Media3/NewPipe/Gradle deps.
- Large Shorts bank changes.
- A line under **Your changes** only when this update conflicts with it or rewrites that spot.

## OK to follow upstream

| Topic | Your choice |
|--------|----------------|
| Queue clear animation | Upstream OK |
| Updater implementation | Upstream OK (releases stay **Yuno** repo) |
| SponsorBlock settings UI | Upstream OK |
| Account / settings structure | Keep Yuno page + liquid glass; no colour chips |
| Related list wording | Upstream OK |
| RTL / `supportsRtl` / bidi text | Upstream OK |
| Playful.kt / Comments panel | Upstream OK unless veto |
| Shorts disk bank | Upstream OK (ask if large diff) |
| Subscriptions channel tap | Opens the channel page |
| Hold menu | iOS-style hold menu only; new video actions go there |
| Playback settings rows | Skip step, chapters stay open, auto PiP, zoom to fill, music speed — must be visible in Settings › Playback |
| Fullscreen zoom | Pinch and the setting both fill the screen |

## Never remake a Skipii feature

Port the upstream code (`com.decio.skipii` → `com.yuno`, user-facing Skipii → Yuno). Do not write a new gesture, animation, layout, or screen that only “does the same thing.” Chrome this file says to keep stays; the upstream behavior is wired into it.

## End of every upstream update

Do this without being asked, after the merge builds. Two lists, one number sequence:

1. **Left out** — Skipii changes the user would notice that Yuno did not ship.
2. **Added** — Skipii changes the user would notice that did land. Continue the numbers (if left-out ends at 7, added starts at 8).

You answer `1. agree`, `2. disagree`, and so on. Then update this file and `.cursor/rules/yuno-upstream-preserve.mdc`:

| List | Agree | Disagree |
|------|--------|----------|
| Left out | Stay out. Add to **Always keep** / Look & feel if it is not already written there. | Port it in. Move it to **OK to follow upstream** and drop any keep-rule that blocks it. |
| Added | Stay in. Add to **OK to follow upstream** if it is not already written there. If it is only in code and not on screen, wire it before calling it added. | Remove it. Add the Yuno behavior to **Always keep**. |

After those votes are applied, post a **fresh** numbered left-out / added list. That list is the new state, not a repeat of the one just voted on. **Keep posting a new list after every fix pass until you say all is good.**

## Open questions

Add checkboxes when something new comes up:

- [ ] _
