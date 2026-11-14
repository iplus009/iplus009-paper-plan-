# Paper Plane by iPlus

**Make your own PDF better than anyone.**

Paper Plane is a GPU-accelerated, privacy-first Android PDF workspace from **iPlus**.

## Working in `v0.1.6` (Phase 0: GPU & Bangla Physics Spike)

- **Merge, Rotate & Split PDFs** — disk-backed `MemoryUsageSetting.setupTempFileOnly()` streaming (`PdfEngine`), `%PDF-`/`%%EOF` integrity check, and atomic SAF `"wt"` writes.
- **True Pixel-Cut Redact** — flattens redacted regions into opaque black pixels, strips metadata, and verifies `0` remaining extractable characters via `PDFTextStripper`.
- **OpenGL ES 2.0 Scanner Spike** — offscreen EGL FBO fragment shaders for 4-corner inverse homography warp, bright-biased shadow normalization, grayscale, and adaptive B&W.
- **Bangla Vector Shaping PDF** — native Android Minikin/HarfBuzz `StaticLayout` ➔ `PdfDocument` rendering (*Original Book Style* & *Clean Text Style*) with `PdfRenderer` preview and text extraction audit.

The source files are opened read-only; operations create a new output file. Planned Milestone 1–6 modules (CameraX GPU Live Viewfinder, Visual Page Organizer, Smart Compressor, Offline `tessdata_fast` Bangla/English OCR, Offline Translation) are tracked under active development.

## App identity

| Item | Value |
|---|---|
| Product | Paper Plane |
| Publisher | iPlus |
| Brand line | Paper Plane by iPlus |
| Platform | Android |
| Application ID | `com.paperplan.app` |
| Minimum Android | Android 8.0 (API 26) |
| Technology | Kotlin, Jetpack Compose, OpenGL ES 2.0, PDFBox-Android |

## Planned product areas

- **Milestone 1:** Visual Page Grid (drag-reorder, rotate, delete), PDF Viewer + E-Sign + Highlight + Real Compress.
- **Milestone 2:** CameraX + OpenGL ES live edge scanner & multi-page batch PDF export.
- **Milestone 3:** Offline Bangla & English OCR (`tessdata_fast` + XY-Cut column sorting) + Book Reader & Offline Translation.

## Build

Open [GitHub Actions](https://github.com/iplus009/iplus009-paper-plan-/actions), select a successful **Android Debug Build**, and download the `PaperPlane-v0.1.6-debug-apk` artifact.

## Source projects and licensing

The source audit considered [PDF Toolkit](https://github.com/Karna14314/Pdf_Tools) and [PaperKnifePlus](https://github.com/potatameister/PaperKnifePlus). Both upstream repositories remain unchanged. The current PDF operations are implemented in this repository using PDFBox-Android; no claim is made that either upstream app has been fully merged.

See [licensing notes](docs/LICENSING.md) before redistribution.

## Documentation

- [Source audit](docs/SOURCE_AUDIT.md) · [Architecture](docs/ARCHITECTURE.md) · [Roadmap](docs/ROADMAP.md)
- [Feature matrix](docs/FEATURE_MATRIX.md) · [Additional features](docs/ADDITIONAL_FEATURES.md)
- [OCR and scanner plan](docs/OCR_CLEAN_PDF_PLAN.md) · [Licensing](docs/LICENSING.md)
- [Branding](docs/BRANDING.md) · [Build status](docs/BUILD_STATUS.md)

**An iPlus app**
