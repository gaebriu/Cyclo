# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Cyclo is a single-module (`:app`) Android app written in Kotlin with Jetpack Compose (package `com.gaebriu.cyclo`, minSdk 33, target/compileSdk 37, AGP 9.x). It is in alpha: right now the app is only the onboarding/introduction flow (Introduction 0–4) that builds a user's first routine. The UI is implemented to match a Figma design.

## Referências de design (Figma)

Estas são as referências de design que **devem ser seguidas a todo o custo, a 1:1**. Em caso de dúvida sobre medidas, cores, tipografia, espaçamentos ou comportamento visual, o Figma é a fonte de verdade.

- Introduction 0: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=1-2&t=CsxckgE7KCKq6Yc3-4
- Introduction 1: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=5-33&t=CsxckgE7KCKq6Yc3-4
- Introduction 2: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=8-146&t=CsxckgE7KCKq6Yc3-4
- Introduction 3: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=12-4&t=CsxckgE7KCKq6Yc3-4
- Introduction 4: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=13-75&t=CsxckgE7KCKq6Yc3-4
- Introduction 5: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=13-132&t=CsxckgE7KCKq6Yc3-4
- Introduction 6: https://www.figma.com/design/gttEgaM66gADIZnxBm8iEs/Cyclo?node-id=9-232&t=CsxckgE7KCKq6Yc3-4
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

- **Entry point:** `MainActivity` turns on edge-to-edge with dark, transparent system bars and renders `ProvideVisualEffects { IntroductionFlow() }`. There is no navigation library, ViewModel or persistence layer yet. `CycloTheme`/`MaterialTheme` exist in `ui/theme/Theme.kt` but are **not** used: the UI is built from Compose foundation primitives and the app's own `Cyclo*` components.
- **`ui/screens/IntroductionFlow.kt`** is the hub. It owns all flow state (`step`, wake time, routine name, repeat selection) with `rememberSaveable`, and drives three `Animatable`s:
  - `enter`: Introduction 1 rising over 0, while 0 recedes (scale, blur, dark veil).
  - `position`: horizontal paging from step 1 onward.
  - `progress`: the progress bar.

  The top button, progress bar and Next button are fixed chrome (`IntroductionChrome.kt`); only the pages slide. Each `IntroductionNPage` is stateless and gets state and callbacks from the flow. Back handling is layered: a `BackHandler` for step 1, a `PredictiveBackHandler` that moves `position` along with the gesture for step 2 and later, and a final `BackHandler` that closes open overlays first. Declaration order sets the priority, and the last one declared wins. A page is composed only while it is on screen or animating (`derivedStateOf` gates).
- **`ui/effects/VisualEffects.kt`:** `LocalVisualEffects` exposes `blurEnabled` and `animationsEnabled`. `animationsEnabled` comes from the system animator duration scale. Every animation and blur must respect these flags: with animations off, movements become fades, and with blur off, blurs and Luminosity blends are dropped. Do **not** use `WindowManager.isCrossWindowBlurEnabled` for this: it returns false on many devices (for example Samsung) and used to hide the effects.
- **Design tokens (`ui/theme/`):** `CycloColors`, `CycloDimens`, `CycloMotion` (spring and tween specs, which are proposed values to be tuned by eye) and `Type.kt` (bundled Inter fonts). Figma blur values are stored as-is in `FigmaBlur` and must go through `Density.figmaBlurPx()`, because Figma's blur radius is twice Android's sigma.
- **`ui/routine/RepeatSelection.kt`:** the `RepeatOption` and `Weekday` enums. Selected days are stored as a bitmask (bit 0 = Sunday … bit 6 = Saturday). `rememberRepeatSelection()` also holds the open/close state of the overlays. `IntroductionFlow`, `Introduction4Page` and `RepeatOverlays.kt` read it, and `RepeatOverlays.kt` passes it to the generic `CycloActionMenu` and `CycloDaysSheet` components.
- **Other `ui/` packages:** `components/` (reusable Cyclo widgets, overlays, `Pressable`), `effects/` (glow and inner shadow), `dictation/` (speech-to-text input, which uses the `RECORD_AUDIO` permission and the `RecognitionService` query in the manifest), `haptics/`, `keyboard/` (`KeyboardLift`), `icons/`, and `debug/BlurSelfTest.kt` (a diagnostic screen that is not wired into the app).
- **Baked assets:** `res/drawable-nodpi/fx_spotlight.png` is generated by `tools/bake_spotlight.py` (numpy, Pillow, scipy; run `python3 tools/bake_spotlight.py`). Figma's progressive blur can't be exported, so the image is computed. Change the script's parameters and regenerate rather than editing the PNG.

## Conventions

- Code comments and KDoc are in European Portuguese. Keep new comments in the same language and style, and reference the matching Figma element or value when relevant.
- The Kotlin source files start with a UTF-8 BOM. Keep it when editing, and don't re-encode files.
- User-facing strings go in `res/values/strings_introduction.xml` (intro flow) or `strings.xml`. Don't hardcode them in composables.
- Accessibility matters here: screens covered by another screen are hidden from TalkBack with `clearAndSetSemantics {}`, and touch targets are at least `CycloDimens.TouchTarget` (48 dp) even when the visible element is smaller.
