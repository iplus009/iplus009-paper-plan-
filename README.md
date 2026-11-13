# Paper Plan by iPlus

**Make your own PDF better than anyone.**

Paper Plan is an Android document workspace from **iPlus**. Its product goal is to bring PDF tools, camera scanning, OCR, online translation, document conversion and a Canva-style PDF designer into one app.

> **Development status:** Early Android foundation. The current build includes the Compose app shell, iPlus/Paper Plan identity and PDF file selection. Most document tools in the product plan are not implemented yet.

## App identity

| Item | Value |
|---|---|
| Product | Paper Plan |
| Publisher / Brand | iPlus |
| Brand line | Paper Plan by iPlus |
| Platform | Android |
| Application ID | `com.paperplan.app` |
| Minimum Android | Android 8.0 (API 26) |
| Technology | Kotlin, Jetpack Compose, Material 3 |

## Product areas

- **PDF Toolkit:** merge, split, compress, organize, annotate, protect and inspect PDFs.
- **Camera Scanner:** single/multi-page capture, page detection, crop and scan filters.
- **OCR Studio:** Bengali and English OCR, searchable PDFs, book-style and clean-text exports.
- **Online PDF Translation:** translate supported documents through a secure online service.
- **PDF Designer:** multi-page layouts, templates, text, images, shapes, layers and export.
- **Conversion & Files:** PDF/image/Office conversion, import, history and file organization.

These are product goals, not a claim that every feature is already available.

## Source structure

```
app/src/main/java/com/paperplan/app/
  core/ui/theme/       Compose theme tokens
  feature/home/        Home screen and tool catalog
  feature/scanner/     Scanner implementation (planned)
  feature/ocr/         OCR implementation (planned)
  feature/translation/ Online translation (planned)
  feature/designer/    PDF designer (planned)
docs/                  Audits, architecture and product plans
.github/workflows/     Android CI
```

## Build

Open **Actions → Android Debug Build** and download the `paper-plan-debug-apk` artifact after a successful run.

## Upstream projects

The source audit considered [PDF Toolkit](https://github.com/Karna14314/Pdf_Tools) and [PaperKnifePlus](https://github.com/potatameister/PaperKnifePlus). Both upstream repositories remain unchanged. Their code is not described as merged here until individual components are reviewed, integrated and tested.

## Documentation

- [Source audit](docs/SOURCE_AUDIT.md) · [Architecture](docs/ARCHITECTURE.md) · [Roadmap](docs/ROADMAP.md)
- [Feature matrix](docs/FEATURE_MATRIX.md) · [Additional features](docs/ADDITIONAL_FEATURES.md)
- [OCR and scanner plan](docs/OCR_CLEAN_PDF_PLAN.md) · [Licensing](docs/LICENSING.md)
- [Branding](docs/BRANDING.md) · [Repository layout](docs/REPOSITORY_LAYOUT.md) · [Build status](docs/BUILD_STATUS.md)

**Maintainer / publisher: iPlus**
