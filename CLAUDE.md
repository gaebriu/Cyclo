# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Cyclo is a single-module (`:app`) Android app written in Kotlin with Jetpack Compose (package `com.gaebriu.cyclo`, minSdk 33, target/compileSdk 37, AGP 9.x). It is in alpha: the app has the onboarding tutorial (Introduction 0–6), which builds the user's first routine, and then the Feed and Saved routines screens. The same Introduction 2–5 pages are reused to create and edit routines. The UI is implemented to match a Figma design.

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
