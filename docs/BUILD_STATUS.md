# Paper Plan Build Status

## Functional PDF tools implemented

- **Merge PDFs:** selects multiple source PDFs and imports their pages into a new PDF.
- **Rotate PDF:** rotates all pages 90° clockwise and saves a new PDF.
- **Split PDF:** exports each source page as an individual PDF in a ZIP archive.
- Processing uses PDFBox-Android on a background coroutine.
- Android Storage Access Framework is used for selecting source files and choosing output destinations.
- Source PDFs are opened read-only; outputs are separate files.

## App foundation

- Android application module: `:app`
- Namespace / application ID: `com.paperplan.app`
- Minimum Android API: 26
- Compile SDK: 36
- Java target: 17
- UI: Kotlin + Jetpack Compose + Material 3
- Brand identity: Paper Plan by iPlus
- Launcher icon: vector adaptive icon
- CI: GitHub Actions debug APK build and artifact upload

## Not implemented yet

CameraX scanner, OCR pipeline, searchable PDF generation, PDF designer, templates, PDF compare, compression optimization, redaction, form filling, Office conversion, translation backend, and full PDF viewer. These must not be presented as working features.

## Verification

The functional PDF implementation has been committed. Its GitHub Actions build is being checked; do not distribute an APK for this revision until the workflow succeeds. Device-level tests with sample PDFs are still required for merge, rotate and split.

## Source preservation

The upstream repositories remain separate and unchanged:
- https://github.com/Karna14314/Pdf_Tools
- https://github.com/potatameister/PaperKnifePlus
