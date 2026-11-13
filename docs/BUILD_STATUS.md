# Paper Plan Build Status

## Android foundation

- Android application module: `:app`
- Namespace / application ID: `com.paperplan.app`
- Minimum Android API: 26
- Compile SDK: 36
- Java target: 17
- UI: Kotlin + Jetpack Compose + Material 3
- PDF import: Android Storage Access Framework picker
- Brand identity: Paper Plan by iPlus
- Launcher icon: vector adaptive icon
- CI: GitHub Actions debug APK build and artifact upload

## Implemented in this foundation

- Launchable Compose home screen
- iPlus / Paper Plan brand line in the app
- PDF file selection through Android's document picker
- Tool catalogue separated from the Home screen
- Centralized Compose theme tokens
- Android launcher icon resources

## Not implemented yet

PDF merge/split/compression engines, CameraX scanner, OCR pipeline, PDF designer, Office conversion, and online translation backend. Tool cards remain entry-point placeholders until each feature is integrated and tested.

## Build verification

The initial Android Debug Build workflow completed successfully. The branding/resources and refactored source in the latest commit must pass CI again before this revision is considered build-verified.

## Source preservation

The upstream repositories remain separate and unchanged:
- https://github.com/Karna14314/Pdf_Tools
- https://github.com/potatameister/PaperKnifePlus
