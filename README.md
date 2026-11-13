# Paper Plan by iPlus

**Make your own PDF better than anyone.**

Paper Plan is an Android document workspace from **iPlus**. The goal is to bring PDF tools, camera scanning, OCR, online translation, document conversion and a Canva-style PDF designer into one carefully designed app.

> **Development status:** Early Android foundation. The current build includes the Compose app shell, iPlus/Paper Plan identity and PDF file selection. Most document tools shown in the product plan are not implemented yet.

## App identity

| Item | Value |
|---|---|
| Product | Paper Plan |
| Publisher / Brand | iPlus |
| Brand line | Paper Plan by iPlus |
| Platform | Android |
| Application ID | `com.paperplan.app` |
| Minimum Android | Android 8.0 (API 26) |
| Main technology | Kotlin, Jetpack Compose, Material 3 |

## Product areas

- **PDF Toolkit:** merge, split, compress, organize, annotate, protect and inspect PDFs.
- **Camera Scanner:** single-page and multi-page capture, page detection, crop and scan filters.
- **OCR Studio:** Bengali and English OCR, searchable PDFs, and book-style or clean-text exports.
- **Online PDF Translation:** translate supported documents into a selected language using a secure online service.
- **PDF Designer:** multi-page layouts, templates, text, images, shapes, layers and export.
- **Conversion & Files:** PDF/image/Office conversion, import, history and file organization.

These are product goals, not a claim that every feature is already available.

## Source structure

```
app/
  src/main/
    java/com/paperplan/app/
      core/
      feature/
      ui/
    res/
      drawable/
      mipmap-anydpi-v26/
      values/
docs/
.github/workflows/
```

## Build

The repository includes a GitHub Actions workflow that builds a debug APK with JDK 17 and Gradle. Open **Actions → Android Debug Build** and download the `paper-plan-debug-apk` artifact after a successful run.

## Upstream projects

The initial source audit considered:
- [PDF Toolkit](https://github.com/Karna14314/Pdf_Tools)
- [PaperKnifePlus](https://github.com/potatameister/PaperKnifePlus)

They remain separate upstream projects. Their code is not represented as merged into this repository until individual components have been reviewed, integrated and tested.

## Project documentation

- [Source audit](docs/SOURCE_AUDIT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Feature matrix](docs/FEATURE_MATRIX.md)
- [Additional features](docs/ADDITIONAL_FEATURES.md)
- [OCR and scanner plan](docs/OCR_CLEAN_PDF_PLAN.md)
- [Licensing notes](docs/LICENSING.md)
- [Build status](docs/BUILD_STATUS.md)

## Maintainer

**iPlus** — Paper Plan is an iPlus-branded application.
