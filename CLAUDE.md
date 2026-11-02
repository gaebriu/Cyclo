# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Cyclo is a single-module (`:app`) Android app written in Kotlin with Jetpack Compose (package `com.gaebriu.cyclo`, minSdk 33, target/compileSdk 37, AGP 9.x). It is in alpha: the app has the onboarding tutorial (Introduction 0–6), which builds the user's first routine, and then the Feed, Saved routines and Settings screens. The same Introduction 2–5 pages are reused to create and edit routines. The UI is implemented to match a Figma design.

## Referências de design (Figma)

Estas são as referências de design que **devem ser seguidas a todo o custo, a 1:1**. Em caso de dúvida sobre medidas, cores, tipografia, espaçamentos ou comportamento visual, o Figma é a fonte de verdade.

- Introduction 0: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=1-2&t=CsxckgE7KCKq6Yc3-4
- Introduction 1: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=5-33&t=CsxckgE7KCKq6Yc3-4
- Introduction 2: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=8-146&t=CsxckgE7KCKq6Yc3-4
- Introduction 3: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=12-4&t=CsxckgE7KCKq6Yc3-4
- Introduction 4: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=13-75&t=CsxckgE7KCKq6Yc3-4
- Introduction 5: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=13-132&t=CsxckgE7KCKq6Yc3-4
- Introduction 6: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=9-232&t=CsxckgE7KCKq6Yc3-4
- Feed: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=24-10&t=piNHgRVGQuqYqvhf-4
- Saved routines: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=72-282&t=piNHgRVGQuqYqvhf-4
- Settings: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=46-140
- Componentes 1 (página de referências): https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=59-50&t=CsxckgE7KCKq6Yc3-4
- Componentes 2 (página de referências): https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=160-313&t=CsxckgE7KCKq6Yc3-4

## Commands

Use `./gradlew` from Git Bash, or `.\gradlew.bat` from PowerShell:

- Build debug APK: `./gradlew assembleDebug`
- Install on a connected device: `./gradlew installDebug`
- Release build (R8 enabled through the `optimization {}` block in `app/build.gradle.kts`): `./gradlew assembleRelease`
- Lint: `./gradlew lint`
- Unit tests: `./gradlew testDebugUnitTest`
- Single unit test: `./gradlew testDebugUnitTest --tests "com.gaebriu.cyclo.ExampleUnitTest.addition_isCorrect"`
- Instrumented tests (needs a device or emulator): `./gradlew connectedDebugAndroidTest`

The only tests are the template placeholders. Changes are checked by running the app on a device.

Dependency versions are in `gradle/libs.versions.toml`. Releases bump `versionCode` and `versionName` (`alpha-X.Y.Z`) in `app/build.gradle.kts`, and the commit message is the version name.

## Architecture

- **Entry point:** `MainActivity` turns on edge-to-edge with dark, transparent system bars and renders `ProvideVisualEffects { CycloApp(viewModel) }`. `CycloApp` draws nothing (just the #010101 background, the same as the splash) until `CycloViewModel.data` has been read, so the app opens straight on the tutorial or the Feed with no loading state. Don't hold the first frame with an `OnPreDrawListener`: combined with the splash exit fade it left the app black until the first touch. `CycloTheme`/`MaterialTheme` exist in `ui/theme/Theme.kt` but are **not** used: the UI is built from Compose foundation primitives and the app's own `Cyclo*` components.
- **Data (`data/`):** `CycloRepository` keeps everything in one Preferences DataStore: the `onboarded` flag, the routines as JSON (org.json; enums stored by name) and today's `DailyLog` (caffeine, sleep factors, mood). `CycloViewModel` exposes `data` (null until read) and `message` (the undo-action bar). Every write goes through `write {}`: if it fails, the bar shows an error with "Try again". Deleting a routine shows "Routine removed" with "Undo". The tutorial's routine and the `onboarded` flag are written in the same `edit {}` at the Introduction 5 Next, so a kill during Introduction 6 neither duplicates nor loses the routine. `Routine.nextWake()` decides what the Feed shows (the first wake within 24 h; "Show just this once" counts from `anchorMillis`, the time it was created, edited or switched on), and `bedtimesFor()` gives the bedtimes (wake − n × 90 min − 15 min, n = 6…1).
- **`ui/CycloApp.kt`** is the root. The Feed/Saved shell (`MainScreen`) is the base layer, and the tutorial or the routine editor is an overlay above it, driven by one `cover` Animatable: the overlay rises/falls like Introduction 1 over 0, and the base recedes (scale, blur, veil) underneath. The Feed's first appearance in a session plays a staggered reveal (`effects/StaggeredReveal.kt`) plus the top glow. A `PredictiveBackHandler` lets the editor follow the finger down on its first step; it is declared after `MainScreen` and before the overlay, so it beats the NavHost and loses to the flow's own handlers.
- **`ui/main/`:** `MainScreen` holds a Navigation Compose `NavHost` (string routes `feed` and `saved`, fade plus a slight slide) between fixed chrome: the top glow, the edge fades, the mini-menu and the navigation bar (`MainChrome.kt`). `FeedScreen` and `SavedRoutinesScreen` are stateless and get data and callbacks (`MainActions`). Search on Saved routines reuses the name field with another placeholder.
- **`ui/screens/IntroductionFlow.kt`** runs both the tutorial (`IntroductionMode.Tutorial`, steps 0–6) and the routine editor (`IntroductionMode.Editor`, steps 2–5, X on the first step, purple "Done" instead of Next on the last). It owns the flow state (`step`, wake time, routine name, repeat selection) with `rememberSaveable`, and drives three `Animatable`s:
  - `enter`: Introduction 1 rising over 0, while 0 recedes (scale, blur, dark veil). Tutorial only.
  - `position`: horizontal paging from the first page onward.
  - `progress`: the progress bar.

  The top button, progress bar and Next button are fixed chrome (`IntroductionChrome.kt`); only the pages slide. On Introduction 6 the top button and Next fade out and are disabled, and back is blocked. Each `IntroductionNPage` is stateless and gets state and callbacks from the flow. Back handling is layered: a `BackHandler` for step 1, a `PredictiveBackHandler` that moves `position` along with the gesture for the later steps, a no-op `BackHandler` on Introduction 6, and a final `BackHandler` that closes open overlays first. Declaration order sets the priority, and the last one declared wins. A page is composed only while it is on screen or animating (`derivedStateOf` gates).
- **Letter reveal:** `components/LetterReveal.kt` is the "Hello!" animation of Introduction 0 (letters fade in from blur 9 while the letter spacing closes), shared with the Introduction 6 text.
- **Settings (`ui/settings/SettingsScreen.kt`):** a third overlay on the same `cover` animation (opened by the mini-menu gear, closed by back-b or the back gesture). Stateless: it gets `CycloData` and callbacks from `CycloApp`. Rows: age (inline `tap-to-write-box-age`, 5–100, shake + reject haptic when invalid), chronotype / time to fall asleep / time format (compact `CycloActionMenu`, Componentes 3), Bedtime switch, language (display only), Export / Restore, View on GitHub (`CycloBigSheet` first). Time format: `use_24_hour` in DataStore (absent = system format), exposed through `ui/time/TimeFormat.kt` (`ProvideTimeFormat` / `LocalTimeFormat`), which every displayed time uses. The Introduction 2 wheel stays 24 h. The file pickers and the notification-permission launcher live in `CycloApp` (not in Settings) because the message bar's "Try again" can reopen them after Settings is closed; the ViewModel asks for them through `pickerRequests`.
- **Bedtime engine (`data/BedtimeEngine.kt`):** pure Kotlin, unit-tested. Bedtime = wake − cycles × 90 − time to fall asleep, cycles = max(5, enough for the age minimum). Daily inputs and chronotype only change the wind-down start (30–90 min) and the notification wording, never the bedtime. See the KDoc for the sources.
- **Reminder (`reminder/`):** `BedtimeReminder` schedules ONE inexact `AlarmManager.setAndAllowWhileIdle` broadcast at the wind-down start of the next routine wake (no exact alarms, no `setAlarmClock`, no alarm-app intents). `BedtimeAlarmReceiver` posts the notification and also handles boot / time / timezone / package-replaced. `CycloSync` (started by `CycloApplication`) watches DataStore with an adaptive debounce and reschedules the reminder and updates the widgets.
- **Widget (`widget/BedtimeWidgets.kt`):** RemoteViews home-screen widget per routine (bound when the launcher confirms `requestPinAppWidget`, stored in the `bedtime_widgets` SharedPreferences). Launcher-added or orphaned widgets show the next routine. Bedtime changes animate through a `ViewFlipper` with `partiallyUpdateAppWidget`.
- **Backup (`data/Backup.kt`):** versioned JSON (`format`, `version`, `exportedAt`, `appVersion`, `data`). `parseBackup` validates the whole file; `CycloRepository.restore` writes it in one `edit {}`.
- **Backdrop (`ui/effects/Backdrop.kt`):** glass surfaces draw what is behind them themselves. A zone records its content with `backdropSource`, declares it with `LocalBackdrop`, and each surface (`cycloCard`, `cycloGlassPill`, `cycloOverlaySurface`, the search box) draws it, clipped and optionally blurred (Figma background blur), before its Luminosity fill. This is what keeps Luminosity correct inside animating (offscreen) layers. A surface must never use a source that contains it. `ProgressiveBlur.kt` does the Feed/Saved/Settings top and bottom bands the same way.
- **Sky colours (`ui/main/TimeOfDay.kt`):** the Feed/Saved top glow and the wake-time card shadow follow the device hour (`skyColorsAt`).
- **`ui/effects/VisualEffects.kt`:** `LocalVisualEffects` exposes `blurEnabled` and `animationsEnabled`. `animationsEnabled` comes from the system animator duration scale. Every animation and blur must respect these flags: with animations off, movements become fades, and with blur off, blurs and Luminosity blends are dropped. Do **not** use `WindowManager.isCrossWindowBlurEnabled` for this: it returns false on many devices (for example Samsung) and used to hide the effects.
- **Design tokens (`ui/theme/`):** `CycloColors`, `CycloDimens`, `CycloMotion` (spring and tween specs, which are proposed values to be tuned by eye) and `Type.kt` (bundled Inter fonts). Figma blur values are stored as-is in `FigmaBlur` and must go through `Density.figmaBlurPx()`, because Figma's blur radius is twice Android's sigma.
- **`ui/routine/RepeatSelection.kt`:** the `RepeatOption` and `Weekday` enums. Selected days are stored as a bitmask (bit 0 = Sunday … bit 6 = Saturday). `rememberRepeatSelection()` also holds the open/close state of the overlays. `IntroductionFlow`, `Introduction4Page` and `RepeatOverlays.kt` read it, and `RepeatOverlays.kt` passes it to the generic `CycloActionMenu` and `CycloDaysSheet` components.
- **Other `ui/` packages:** `components/` (reusable Cyclo widgets, overlays, `Pressable`), `effects/` (glow and inner shadow), `dictation/` (speech-to-text input, which uses the `RECORD_AUDIO` permission and the `RecognitionService` query in the manifest), `haptics/`, `keyboard/` (`KeyboardLift`), `icons/` (`CycloIconPaths` for the intro, `CycloAppIconPaths` for the Feed and Saved routines, both raw Figma SVG paths drawn with `CycloIcon`), and `debug/BlurSelfTest.kt` (a diagnostic screen that is not wired into the app).
- **Baked assets:** `res/drawable-nodpi/fx_spotlight.png` is generated by `tools/bake_spotlight.py` (numpy, Pillow, scipy; run `python3 tools/bake_spotlight.py`). Figma's progressive blur can't be exported, so the image is computed. Change the script's parameters and regenerate rather than editing the PNG.

## Conventions

- Code comments and KDoc are in European Portuguese. Keep new comments in the same language and style, and reference the matching Figma element or value when relevant.
- The Kotlin source files start with a UTF-8 BOM. Keep it when editing, and don't re-encode files.
- User-facing strings go in `res/values/strings_introduction.xml` (intro flow) or `strings.xml`. Don't hardcode them in composables.
- Accessibility matters here: screens covered by another screen are hidden from TalkBack with `clearAndSetSemantics {}`, and touch targets are at least `CycloDimens.TouchTarget` (48 dp) even when the visible element is smaller.

## Active task: Settings, data management and intelligent bedtime (requirements)

Status: clarification done; implemented (see "Implementation notes"). Builds, unit tests pass. **Not yet verified on a device** (none was connected): check the items under "Still to verify on a device". Do not mark a feature complete because its UI exists; verify the behaviour.

Extra Figma references: Settings (updated) https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=46-140&t=piNHgRVGQuqYqvhf-4 · Componentes 3 https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=228-49&t=piNHgRVGQuqYqvhf-4 (Figma is the source of truth).

### 1. Settings page
- Implement action menus: `action-menu-chronotype`, `action-menu-time-format`, `action-menu-time-to-fall-asleep`, `action-menu-big-open-github`. Every option works, persists and changes app behaviour.
- Remove `sleep-cycle-length`, `send-feedback-b`, `morning-check-in` and its description, plus their now-unused functionality/strings, without breaking anything or leaving unused UI.
- Keep and correctly initialise the current default values. Age input uses the age component from Componentes 3. Fix the time-format menu padding to match Componentes 3. Follow Figma spacing, sizes, type, alignment, states. All settings must be functional.

### 2. Export my data
1. Button opens the native save dialog (user picks location). 2. Writes a clean, structured, **versioned** `.json` with the relevant app data. 3. Only after the file is really saved and the app has focus again, show the existing undo-action bar with a short success message and **no button**. 4. Cancel/no save: same bar, failure message and a retry button instead of Undo. Handle cancellation, write failures and permission errors; never show success unless saved. Messages: short, natural, Cyclo's writing style.

### 3. Restore my data
1. Button opens the native picker; user picks a `.json` backup. 2. No file selected: undo-action with a short "no file selected" message and retry. 3. Unreadable/corrupted/invalid/incompatible: error message in the same bar with retry. 4. Validate the **whole** file before touching existing data. 5. If valid, show the existing `action-menu-big`; the user must confirm with its confirmation button. 6. Only then restore, showing the loading-data overlay, whose animation reuses the Introduction 6 element animation (`LetterReveal`) adapted to the overlay. 7. On completion close the overlay and give success/failure feedback.
- Safe and atomic (single DataStore `edit {}`), no partial restore, handle incompatible versions, no duplicates or unintended overwrites, never silently discard existing data. An exported file must be restorable by this same app.

### 4. Intelligent bedtime reminder (notification, scheduling, recommendations)
Not a fixed-time notification. Base it on the real findings (prefer systematic reviews, meta-analyses, guidelines, consensus statements) of: CDC sleep duration https://www.cdc.gov/sleep/about/index.html and https://pmc.ncbi.nlm.nih.gov/articles/PMC4442216/ · chronotype https://pubmed.ncbi.nlm.nih.gov/33545116/ and https://onlinelibrary.wiley.com/doi/full/10.1002/ctm2.1131 · caffeine https://pubmed.ncbi.nlm.nih.gov/36870101/ and https://doi.org/10.1016/j.smrv.2023.101764 · physical activity https://pubmed.ncbi.nlm.nih.gov/30374942/ and https://pmc.ncbi.nlm.nih.gov/articles/PMC9760070/ · emotions/stress/winding down https://pubmed.ncbi.nlm.nih.gov/40840150/ and https://pmc.ncbi.nlm.nih.gov/articles/PMC7853203/ · light and circadian rhythm https://www.ncbi.nlm.nih.gov/books/NBK620865/
- Inputs: only data Cyclo already collects (age, chronotype, sleep/wake schedule, time to fall asleep, caffeine, exercise/sleep factors, mood, history if any). Never invent or assume missing data.
- **Minimum target: 5 estimated cycles (~90 min each, 7h30) per night**, more if the age recommendation is longer. Include time to fall asleep. 90 min is a planning estimate, not a fixed fact. Use history when available.
- Caffeine, stress, exercise, mood, evening habits move **wind-down timing, reminder timing and preparation time only**; they must NEVER reduce the sleep opportunity below the minimum. If the wake time makes 5 cycles impossible, recommend the earliest realistic bedtime, never fewer cycles.
- Priority: 1 age-appropriate duration, 2 ≥5 cycles, 3 required wake time, 4 chronotype/circadian, 5 caffeine/activities/emotions/stress, 6 wind-down and notification timing.
- Safeguards: never claim 5 cycles guarantees restorative sleep; don't assume exactly 90 min; don't turn emotions into arbitrary extra sleep; separate duration, quality and difficulty falling asleep; adjust gradually (stable recommendations); not medical advice.
- Notification: title and body in Cyclo's style, concise, personalised, explain why now is a good time; respect permission/settings; reschedule when inputs change; no duplicates or stale reminders.

### 5. Dynamic bedtime on the Feed
- Bedtime reminder widget built from the routine element on Saved routines (same structure, spacing, type, styling; reuse components).
- When caffeine/activities/emotions change: do not update at once; wait for a short adaptive debounce until input settles (no recalculation per keystroke), recalculate, then animate to the new bedtime smoothly in Cyclo's motion language (no flicker, lag or needless recomposition). Result must respect the section 4 minimum.

### 6–8. Cross-cutting
Reuse components/animations, keep Cyclo's identity and writing style, no new dependencies unless needed, don't redesign unrelated screens, handle loading/success/cancel/error consistently, persist everything, use real platform capabilities (SAF file pickers, notifications). Verification list: every new menu and removed setting; export (save, cancel, error); restore (validate, confirm, cancel, failures); loading overlay; bedtime maths across ages/chronotypes/caffeine/activity/mood and never below minimum; notification scheduling and permissions; Feed dynamic updates; widget vs Saved routine element; no regressions. Keep this file updated with decisions and technical constraints; never delete unrelated instructions.

### Decisions (answers to clarification questions)
These override the requirements above where they differ.
1. **Chronotype:** options and design exactly as in Componentes 3. No invented options.
2. **Age:** default as shown in the updated Settings Figma. Valid range 5–100. Out-of-range + check button (component from Componentes 3): the input container gently shakes with haptic feedback, and the value is rejected.
3. **Time to fall asleep:** options and design exactly as in Componentes 3.
4. **GitHub:** tapping the row first shows `action-menu-big-open-github` (Componentes 3), following its interaction. If the repo URL can't be found in the project, ask the user; never guess.
5. **Feedback:** remove the button and its code. Make the version label and the remaining Settings elements match the updated Settings Figma.
6. **Language:** exactly as in the updated Settings Figma; no new functionality.
7. **Export:** all stored user data (settings, routines, logs, preferences, everything in DataStore). File name `cyclo-backup-YYYY-MM-DD.json`.
8. **Restore:** replaces existing data (no merge), always after the confirmation step.
9. **Versions:** an older backup imports only what is compatible with the current version and ignores the rest; validate everything before any change; incompatible data must never corrupt state.
10. **DailyLog:** restore it even when its date is not today.
11. File name exactly `cyclo-backup-YYYY-MM-DD.json`.
12. **Retry** reopens the native picker. Copy written by Claude in Cyclo's style: calm, simple, clear, concise, non-technical.
13. **No new inputs or settings** for bedtime. Use only what Cyclo already collects (age, chronotype, time to fall asleep, routines/wake time, caffeine count, sleep factors, mood).
14. **No sleep history** feature.
15. **One notification only:** the bedtime reminder. Follows active routines' repeat/days; rescheduled when settings, routines or daily inputs change. No wind-down notification.
16. **No Android alarms** (no AlarmClock intents, no alarm-app integration). Use only Cyclo's own scheduling and notifications.
17. **Permission denied:** the switch stays off and the bar shows "Allow notifications to turn this on."
18. **Widget = native Android home-screen widget**, not a Feed element. Do not modify the Feed for it. Visual design based on the Saved routines routine element (structure, spacing, padding, type, dimensions). Shows the recommended bedtime and updates when inputs/routines change, after a debounce, with a smooth bedtime transition.
19. **Toggles:** polished, smooth ON/OFF animation for every toggle in the app (both directions equally refined), in Cyclo's motion language, respecting `animationsEnabled`.

General: Figma (latest Settings and Componentes 3) is the source of truth; reuse components; don't redesign unrelated parts; a Figma element does not imply unspecified functionality; avoid data loss in restore.

### Implementation notes
- **Widget scope (asked):** one widget per routine, pinned from the Saved routines card button and from the Introduction 5 "Add as a widget" button. When editing, Introduction 5 uses the routine's id. For a new routine it uses the predicted id (max + 1, which is what the repository assigns). If the routine is never saved, the widget falls back to the next routine.
- **Widget content:** routine name; sun + wake time; star (`CycloAppIconPaths.Star`, as on the Feed) + "In bed by HH:MM"; moon + "Wind down at HH:MM". The bedtime only moves with age, latency or routines. The wind-down line is the one that caffeine, the factors, mood and chronotype change, so that's the line that animates after the debounce. RemoteViews can't do Luminosity or blur, so the background is solid #101011.
- **One reminder per night:** wakes within 12 h of the last notified wake count as the same night (e.g. Work 07:00 + Gym 08:00 → one notification). Covered by `NextReminderTest`.
- **Notification copy:** worded as a plan, not a promise ("Aim to be in bed by … for a full night before …"). Factor-specific tips come first. Otherwise one of four default tips rotates by night.
- **GitHub URL:** from the git remote: `https://github.com/gaebriu/Cyclo` (`R.string.github_url`).
- **Settings defaults (Figma):** age unset ("Tap to write"), Hummingbird, 15 min, Bedtime ON, time format = system. Time-format menu lists 24-hour first (Componentes 3). Spacing from Figma: row→next section label 24 dp, row→description 15, description→next label 15. Version label: "Cyclo Version " + `versionLabel()` (Figma "Cyclo Version 1.0").
- **Fall-asleep values:** "Under 5 minutes" = 5, "15 minutes" = 15, "30 minutes or more" = 30 in the maths. The Feed's bedtimes (`bedtimesFor`) now use this setting instead of the fixed 15.
- **Bedtime switch:** shown ON only if the stored preference is on AND `POST_NOTIFICATIONS` is granted and app notifications are enabled. Tapping it when off asks for the permission. Denied: switch stays off and the bar shows "Allow notifications to turn this on." Re-checked on every resume. No prompt at launch, so on a fresh install the switch shows off until tapped.
- **"No Android alarms" reading:** Cyclo uses its own inexact `AlarmManager` broadcast only to time its notification. That is not a user-visible alarm: no `setAlarmClock`, no `AlarmClock` intents, no `SCHEDULE_EXACT_ALARM`. The notification may arrive a few minutes late. It isn't sent if the wind-down start has already passed (no catch-up notifications), and a night is never notified twice (`notified_wake`).
- **Wind-down minutes:** 30 base; +5 per caffeine (max +20); mood "Not great" +15, "Bad" +20; intense exercise +15; heavy late meal +15; Night owl +15 (dim lights earlier); long nap +0 (message only); cap 90, 5-min steps. Today's log only counts for the coming night. If even going to bed now is too late for the full target: "Time for bed" (no fewer cycles).
- **Backup:** device-specific state (widget bindings, last notified night) is not exported. Settings missing from an older backup keep their current values; routines and the daily log are always replaced. Unknown enum values are ignored; impossible values (hour 25, age 300, bad date) reject the whole file. Duplicate ids get new ids. Files over 2 MB are rejected. Export writes with mode `"wt"` and deletes the document if the write fails. The picker accepts `application/json`, `application/octet-stream` and `text/plain`.
- **Loading overlay:** `LoadingDataOverlay` reuses Introduction 6's `BlurredRing` (made `internal`) and `LetterRevealState`. It stays on screen for at least `MinRestoreMs` (2.6 s, the length of the letter animation) so it doesn't just flash.
- **Toggles:** the Settings switch now springs (a slight stretch at mid-travel, colour and opacity follow it, and it snaps when animations are off). The routine on/off button, day chips and sleep-factor chips already animate with springs.
- **Tests:** `org.json:json` was added as `testImplementation`, because Android's org.json is only a stub in local tests. `BedtimeEngineTest` checks that the sleep opportunity is always ≥ max(age minimum, 450) + latency across ages, chronotypes, latencies and logs. `BackupTest` covers the round trip, rejections, older and unknown values, and duplicate ids.
- **Lint:** `ProgressiveBlur.kt:113` (`RememberReturnType`) is in the user's own uncommitted WIP and was left untouched. It makes `./gradlew lint` fail.

### Still to verify on a device
The `Medium_Phone` AVD (API 37) draws nothing for Cyclo and doesn't react to taps. The committed alpha-0.7.0 build behaves the same, so it's the emulator's graphics, not this change. Use a physical device. Settings menus and the age shake; export (save, cancel, write error) and restore (cancel, invalid file, newer version, confirm, loading overlay); the permission prompt and denial message; `adb shell dumpsys alarm | grep cyclo` (one reminder and one refresh pending); notification content at the wind-down time; widget pinning (Saved card and Introduction 5), the look next to the Saved card, and the ViewFlipper animation of the "Wind down at" line after changing caffeine or mood (it should animate once, about 1–2 s after the last change).
