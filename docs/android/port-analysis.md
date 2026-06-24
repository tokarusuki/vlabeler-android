# Android port analysis

The original repository is a Kotlin Multiplatform/JVM desktop application built with Compose Desktop. Its reusable core is concentrated in serializable models, label/project parsing, and numeric audio transforms, while the desktop-specific layer is concentrated in application startup, windows, menus, AWT/LWJGL file dialogs, JVM audio input, keyboard/mouse abstractions, VLC video integration, GraalVM JavaScript plugins, and desktop packaging.

## Desktop-specific areas

- `src/jvmMain/kotlin/com/sdercolin/vlabeler/Main.kt`, `ui/App.kt`, `ui/Menu.kt`, and starter/dialog classes create desktop windows, menus, shortcuts, and file pickers.
- `env/Awt.kt`, `env/Keyboard.kt`, `env/Mouse.kt`, and `env/Os.kt` wrap desktop input and operating-system behavior.
- `io/Wave.kt` uses `javax.sound.sampled.AudioSystem`, which is unavailable on Android.
- The Gradle root config packages native desktop distributions through Compose Desktop.
- Plugin execution depends on JVM/GraalVM JavaScript and filesystem-oriented resource loading, which needs a separate Android runtime strategy.
- Video playback depends on VLCJ/native desktop VLC and is not directly reusable.

## Reusable areas

- Model classes such as entries, projects, modules, parameters, filters, and localized strings can be moved to a future common module after removing Compose Desktop annotations or replacing them with multiplatform annotations.
- `oto.ini` parsing/writing semantics from the labeler resources are portable. The Android module includes a direct Kotlin implementation for UTAU `oto.ini` lines.
- Project-management concepts such as entry lists, sample names, and save/load flows are reused in simplified Android form.
- Numeric waveform rendering concepts are reusable, but Android needs its own reader because `javax.sound.sampled` is not available.

## Android replacement implemented here

The new `androidApp` module is a native Android APK project using Kotlin and Jetpack Compose. It uses Android Storage Access Framework document pickers instead of desktop file dialogs, bottom navigation buttons instead of menu-bar actions and keyboard shortcuts, Material text fields for parameter editing, and a small Android-compatible PCM WAV reader for waveform previews.

This is intentionally a functional first APK rather than a complete desktop feature clone. It opens and edits `oto.ini`, saves edited labels, previews WAV waveforms, and establishes an Android Studio project structure that can be expanded by gradually migrating more JVM logic into shared modules.
