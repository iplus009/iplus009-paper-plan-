# Paper Plan Build Status

## Initial Android foundation

- Android application module: `:app`
- Namespace / application ID: `com.paperplan.app`
- Minimum Android API: 26
- Compile SDK: 36
- Java target: 17
- UI: Kotlin + Jetpack Compose + Material 3
- First implemented interaction: Android Storage Access Framework PDF picker
- CI: GitHub Actions debug APK build and artifact upload

## Current limitations

This is the initial launchable app foundation, not the complete Paper Plan product. Tool cards are currently navigation placeholders. PDF processing engines, CameraX scanner, OCR pipeline, Designer, Office conversion, and online translation backend have not yet been integrated.

## Build verification

A GitHub Actions workflow is configured at `.github/workflows/android-debug.yml`. The first CI run must complete successfully before an APK can be described as build-verified.

## Source preservation

The upstream repositories remain separate and unchanged:
- https://github.com/Karna14314/Pdf_Tools
- https://github.com/potatameister/PaperKnifePlus
