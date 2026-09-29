# Build / Development

## Android Studio
1. Extract the ZIP.
2. Open the folder containing `settings.gradle.kts`.
3. Let Android Studio sync Gradle.
4. Install an Android SDK platform matching compileSdk 35.
5. Run the `app` configuration on an emulator or Android device.

## Android-only editing
A phone can edit the source with a compatible Android code editor, but a full native Android build is much easier and more reliable in Android Studio. The source is standard Gradle/Kotlin and is intentionally portable.

## Before shipping
- Run unit tests.
- Profile AI latency on the target device.
- Move expensive search off the UI thread.
- Add persistence and error reporting.
- Test every game rule against deterministic fixtures.
