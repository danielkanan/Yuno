# Yuno — keep on Skipii upstream merges

Edit when you want something new protected. Cursor rule: `.cursor/rules/yuno-upstream-preserve.mdc`.

## Product & branding

- App name **Yuno**; package **`com.yuno`**.
- Self-update: users always get APKs from **`V-M-B/Yuno`** (`GITHUB_RELEASES_REPO` in `app/build.gradle.kts`, read by `Updater.kt`) — merge upstream `Updater.kt` fixes freely, never point releases at Skipii.
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
- **Pages:** channel and Settings use the app background (`c.background`), the same as the rest of the shell.

## Shell & motion (keep Yuno)

- Mini-player ↔ player morph, headers, vignettes, no ripples, `pressable`.

## Your changes — ask only if an update touches them

When you change or add something in Yuno (a layout, a screen, a setting, a behavior), write it here **and** in `.cursor/rules/yuno-upstream-preserve.mdc` in the same turn, without being asked. One line: what changed, and where it lives. Leave it in this list until you decide.

On a later Skipii merge, ask only if that update **conflicts with or rewrites** the change. If Skipii does not touch it, keep it and do not ask. When it does collide, ask: **keep** it, or **let Skipii replace it**?

- **Keep** — move the line into **Look & feel** / **Always keep**.
- **Let Skipii replace it** — delete the line and take upstream there.

- Loading skeletons (`Skeleton.kt`) on feeds, the channel page, and comments. Spinners stay for playback, sign-in, and posting.
- Settings has no developer card: no "Made by @…", "Come say hi", or social links (`Settings.kt`).
- Liquid glass stays on across launches (`LiquidGlassSetting.init` in `YunoApplication`).
- Page changes use BitChord's switcher (`AppNavigator`, wired in `MainActivity`): tabs cut with no fade, and search, settings, a settings section, a playlist, and a channel crossfade in 180ms. The top bar stays outside that switcher. Its actions pill is BitChord's `ArtworkPageActions` (`PageActions` in `Header.kt`): the glass sits outside their no-bounce spring, and `ActionsRow` opens each gap with the action after it, so the pill shrinks and grows instead of the buttons clipping. The tab bar stays up on those pages.
- The hold menu lifts BitChord's picture (`HoldCapture` in `HeldMenu.kt`, wired from `pressable`): the row is not press-scaled, and its horizontal gutter is trimmed off the picture (`liftPadding`, `PageGutter` on `VideoCard`).
- Yuno's own version, not Skipii's. First release is `versionName` 1.0.0 and `versionCode` 1 in `app/build.gradle.kts`. A Skipii merge must not put their version back.
- Settings uses BitChord's settings chrome throughout (`Settings.kt`, `SettingsChrome.kt`, `IosSwitch.kt`, `PillSegmentedControl.kt`): floating top bar, their padding and grouped cards, the press-stretch switch, and the sliding segment pill. Yuno's own settings stay (playback, captions, SponsorBlock, Shorts, appearance as System/Light/Dark plus liquid glass, language, notifications, about, account). The settings page does not draw `PageTopFade`.
- Signed-out Home still shows recommendations (`InnerTube.guestHome` in `Account.kt`, chosen in `LibraryData` in `Feeds.kt`). Anonymous home browse is a sign-in wall, so guests search for popular videos.
- Home pull-to-refresh stretches the top bar (more top and bottom padding — about 6dp more at the reload point, and it still grows if you pull past while the wordmark size and fill stay put — wordmark grows to the right from the gutter, fades to 20% with a white left-to-right fill that finishes at the size limit, haptic when full, then the wordmark fills and empties starting fill-unfill-fill, then unfill-fill with a half-second hold between those, and after reload it finishes that pass through to a full fill before returning to normal with a click and a quick scale jump; a pull during that animation still grows the bar and clicks, and letting go of a pull that started during the reload does not start another one) via `BarPullRefresh.kt`, wired in `HomeScreen` (wordmark), `SubscriptionsScreen` and `LibraryScreen` (page titles), `PageTopBar`, `LargePageBrandMark`, and `LargePageTitle`. Playlist pages still use the circular indicator.
- Autoplay (Settings › Playback, `Autoplay` in `PlaybackModes.kt`, on by default): when the last video in the queue ends, `Autoplayer` in `PlaybackService.kt` adds and starts the first related video (`YouTube.related`, no Shorts or live) that hasn't played in the last 30.
- Mini-player sideways swipes (`miniPlayerPullGestures` in `MiniPlayerGestures.kt`, wired in `GlassNav.kt` and `PlayerUi.kt`): swipe right closes the video (the bar slides off and fades), swipe left plays/pauses it (the bar nudges and springs back), with a haptic tick. Up still opens, down still closes. After a close the bar holds where it left while it exits instead of snapping back.
- YouTube links open in Yuno (`YouTubeLinks` in `Links.kt`, intent filters in `AndroidManifest.xml`, read in `MainActivity` through `NewVideos.opening`): youtube.com / youtu.be / m. / music. video and Shorts links, by "Open with" or the share sheet.
- Dislike counts from Return YouTube Dislike (`ReturnDislike.kt`, `PlayerState.displayDislikes`, label on the dislike pill in `PlayerUi.kt`); switch in Settings › Playback, on by default.
- Loop button on the video (`OverlayTile` before Audio only in `VideoView.kt`, `PlayerState.toggleLoop`): repeat-one for the open video, turned off when another video opens.
- Quality on mobile data (`QualityPreference.mobile` in `YouTube.kt`, row in Settings › Playback): Same · Auto · 360p · 480p · 720p; the quality sheet's pick is kept for the network it was made on.
- Signed in, "More videos" and autoplay come from the account (`InnerTube.personalUpNext` in `Account.kt`, used by `PlayerState.loadPersonalRelated` and `Autoplayer.pickNext`): a song gets the account's Mix (`RD` + id), other videos YouTube's own signed-in up next; NewPipe's related list is the fallback.
- Search without the "videos only" filter (`YouTube.search` / `searchMore`, `SearchState` in `Screens.kt`): YouTube still finds misspelt queries, more results load on scroll, and its "Did you mean" shows above the results. NewPipe gets the phone's country (`YunoApplication`); the language stays English.
- Playlist page (`PlaylistScreen` in `Screens.kt`, `PlaylistView.kt`, `PlayerState.playAll`, `FeedState.loadAll`): Play all and Shuffle buttons; sort chips (Playlist order, Most popular, Newest, Oldest) and Videos / Shorts filters over the whole playlist; a tapped video plays on through the rest of the playlist.
- Downloads and Incognito (`Downloads.kt` with `DownloadService`, offline paths in `YouTubeMediaSourceFactory`, `SessionCallback.onAddMediaItems` and `PlayerState.show`; `Incognito` in `PlaybackModes.kt`): save a video (Audio only / 360p / 720p / 1080p) from the player's Download pill or the long-press menu, play it with no connection from Library › Downloads; Incognito (Settings, Library banner) stops watch-time pings and Yuno's watch and search history.

## Ask on each merge

- Playback, extraction, bot-check, SponsorBlock **service**, Media3/NewPipe/Gradle deps.
- Large Shorts bank changes.
- A line under **Your changes** only when this update conflicts with it or rewrites that spot.

## OK to follow upstream

| Topic | Your choice |
|--------|----------------|
| Audio-only stage | Upstream OK |
| Queue clear animation | Upstream OK |
| Updater implementation | Upstream OK (releases stay **Yuno** repo) |
| SponsorBlock settings UI | Upstream OK |
| Account / settings structure | Keep Yuno page + liquid glass; no colour chips |
| Related list wording | Upstream OK |
| RTL / `supportsRtl` / bidi text | Upstream OK |
| Playful.kt / Comments panel | Upstream OK unless veto |
| Shorts disk bank | Upstream OK (ask if large diff) |
| Home category chips | Upstream `HomeChips` / `UnderlineTabs` — underlined words, not filled chips |
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
