# Paper Plan by iPlus

**Make your own PDF better than anyone.**

Paper Plan is an Android PDF workspace from **iPlus**.

## Working in the current build

The current implementation includes on-device PDF processing:

- **Merge PDFs** — combine two or more selected PDFs into one new PDF.
- **Rotate PDF** — rotate every page 90° clockwise and save a separate output.
- **Split PDF** — export each page as an individual PDF inside a ZIP archive.

The source files are opened read-only; operations create a new output file. These tools use PDFBox-Android and Android's Storage Access Framework.

> The app is actively under development. Features listed in the roadmap are not available until their processing, export and tests are integrated. The current build is an early functional PDF-tools milestone, not the complete planned product.

## App identity

| Item | Value |
|---|---|
| Product | Paper Plan |
| Publisher | iPlus |
| Brand line | Paper Plan by iPlus |
| Platform | Android |
| Application ID | `com.paperplan.app` |
| Minimum Android | Android 8.0 (API 26) |
| Technology | Kotlin, Jetpack Compose, PDFBox-Android |

## Planned product areas

- PDF toolkit: organize, compress, annotate, protect, redact, forms and metadata.
- Camera scanner: multi-page capture, edge detection, crop and scan filters.
- OCR Studio: Bengali/English OCR, searchable PDF, book-style and clean-text exports.
- Online PDF translation.
- Canva-style PDF Designer with templates, layers, brand kit and QR/barcode elements.
- Conversion, history and file management.

## Build

Open [GitHub Actions](https://github.com/iplus009/iplus009-paper-plan-/actions), select a successful **Android Debug Build**, and download the `paper-plan-debug-apk` artifact.

## Source projects and licensing

The source audit considered [PDF Toolkit](https://github.com/Karna14314/Pdf_Tools) and [PaperKnifePlus](https://github.com/potatameister/PaperKnifePlus). Both upstream repositories remain unchanged. The current PDF operations are implemented in this repository using PDFBox-Android; no claim is made that either upstream app has been fully merged.

See [licensing notes](docs/LICENSING.md) before redistribution.

## Documentation

- [Source audit](docs/SOURCE_AUDIT.md) · [Architecture](docs/ARCHITECTURE.md) · [Roadmap](docs/ROADMAP.md)
- [Feature matrix](docs/FEATURE_MATRIX.md) · [Additional features](docs/ADDITIONAL_FEATURES.md)
- [OCR and scanner plan](docs/OCR_CLEAN_PDF_PLAN.md) · [Licensing](docs/LICENSING.md)
- [Branding](docs/BRANDING.md) · [Build status](docs/BUILD_STATUS.md)

**An iPlus app**
