# Source Audit — Paper Plan
Audit date: 2026-11-13. This is an architecture-level audit of repository metadata, recursive source trees, Gradle configuration, manifests, README and selected source layout—not a line-by-line audit or a verified merged build.

## PDF Toolkit
Repository: https://github.com/Karna14314/Pdf_Tools  
Observed license: Apache-2.0. Kotlin + Jetpack Compose; PdfBox-Android; Android PdfRenderer; CameraX; Room; DataStore; Coroutines/Flow; image utilities.

Source areas:
- `domain/operations/`: merge, split, compression, rotation, security, signing, watermark, OCR, metadata, text extraction, repair, forms, annotation and conversion.
- `data/`: file management, history, SAF URI management and Room persistence.
- `ui/navigation/` and `ui/screens/`: existing navigation and PDF tool/viewer screens.
- `util/`: cache, memory guard, file opening, image processing, output folders, theme and settings.
- Unit and instrumentation tests cover operations, viewer, navigation and UI.
- Product flavors observed: `playstore`, `fdroid`, `opensource`; some OCR/scanner/service implementations differ by flavor.

Build facts observed: minSdk 26, compileSdk 36, JDK 17. Upstream application ID is placeholder-style `com.yourname.pdftoolkit`; Paper Plan needs its own ID.

Recommendation: use PDF Toolkit as the processing/data foundation. Migrate its app navigation and branding into Paper Plan instead of blindly copying the app shell.

## PaperKnifePlus
Repository: https://github.com/potatameister/PaperKnifePlus  
Observed license: GPL-3.0-or-later. Kotlin + Compose, PdfBox-Android, Android PdfRenderer and Coil. Manifest is designed for offline use and has no INTERNET permission. minSdk 24, compileSdk 36, JDK 17.

Source areas:
- `MainActivity.kt`: central app entry/routing; do not copy wholesale.
- `ToolCatalog.kt`, `ToolState.kt`, `ToolPicker.kt`: useful tool catalogue and customizable home patterns.
- `PdfPageFetcher.kt`: PDF preview/fetcher with cache/resource management; inspect before reuse because PDF Toolkit already has viewer/rendering infrastructure.
- Dedicated components include compare, grayscale, bookmarks, PDF-to-ZIP, embedded-image extraction, page numbers, compress, rearrange, merge, rotate, protect/unlock, metadata and conversion.

Recommendation: port missing capabilities and UX patterns into the shared engine. Avoid duplicate renderers, PDFBox initialization, file pickers and navigation stacks.

## Integration risks
1. minSdk mismatch: combined app should initially use API 26.
2. Normalize PdfBox-Android to one dependency version and one initialization path.
3. Align Kotlin, AGP, Compose BOM and AndroidX versions in one version catalog.
4. Merge only required manifest components and permissions; preserve offline behavior.
5. Use one navigation graph and one viewer/rendering lifecycle.
6. Preserve attribution and audit transitive libraries, fonts, icons and assets.

## Deeper audit required before migration
Read operation classes and call sites; compare duplicates by behavior; inspect PdfBox initialization, cache eviction, SAF grants, manifest permissions, dependencies, assets and tests. Build/test upstream baselines where feasible. Record upstream commit SHAs and file-level provenance.
