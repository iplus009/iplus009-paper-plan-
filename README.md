# Paper Plan
**Make your own PDF better than anyone.**

Paper Plan is a planned Android app combining a comprehensive offline PDF toolkit with a Canva-style PDF designer.

> Status: architecture and source-integration planning. This repository does not yet contain a merged Android app or a verified APK.

## Product scope
- PDF tools: merge, split, compress, rotate, reorder, extract/delete pages, annotate, sign, watermark, forms, flatten, protect/unlock, metadata, OCR, scan and conversions.
- Advanced utilities: compare PDFs, grayscale, bookmarks, PDF-to-ZIP, extract embedded images, page numbers, search, print and viewer controls.
- Designer: multi-page documents, templates, text, images, shapes, backgrounds, layers, undo/redo and PDF/PNG export.
- Offline-first, on-device document processing by default.

## Upstream projects
- [PDF Toolkit](https://github.com/Karna14314/Pdf_Tools) — Apache-2.0
- [PaperKnifePlus](https://github.com/potatameister/PaperKnifePlus) — GPL-3.0-or-later

Both upstream repositories must remain unchanged. Paper Plan is independent.

## Proposed baseline
- Kotlin + Jetpack Compose
- minSdk 26 (Android 8.0)
- compile/target SDK 36 initially; validate against selected toolchain
- JDK 17
- Package proposal: `com.paperplan.app`

## Planning documents
- [Source audit](docs/SOURCE_AUDIT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Feature matrix](docs/FEATURE_MATRIX.md)
- [Licensing](docs/LICENSING.md)

## Important
Because PaperKnifePlus is GPL-3.0-or-later, a combined derivative containing its covered code is planned for GPL-3.0-or-later distribution. Preserve all required upstream notices and third-party licenses. This is a technical planning note, not legal advice.
