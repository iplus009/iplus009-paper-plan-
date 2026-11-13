# Paper Plan — Implementation Roadmap

## Phase 0: foundation and provenance
- Establish standalone repository, package `com.paperplan.app`, upstream commit SHAs and file-level attribution.
- Preserve license/copyright notices.
- Align AGP, Kotlin, Compose BOM, JDK 17 and PdfBox-Android.
- Establish reproducible debug build.
Exit: clean checkout builds and launches; upstream projects untouched.

## Phase 1: PDF Toolkit foundation
- Port processing/data foundation; replace package IDs, branding and metadata.
- Retain/adapt operation tests.
- Create unified navigation/viewer.
- Validate SAF import/export and history.
Exit: baseline operations pass tests on API 26+.

## Phase 2: PaperKnifePlus contributions
Prioritize missing/incomplete compare, grayscale, bookmarks, PDF-to-ZIP, embedded-image extraction, home tool customization, compression estimate UX and viewer search/print/page-jump patterns. Inspect each implementation and port into shared engine; do not duplicate existing features.
Exit: feature matrix updated; no duplicate engine/navigation stack.

## Phase 3: Designer core
Serializable document model; persistence; canvas renderer/hit testing; text/image/shape/line elements; backgrounds; page sizing; selection/move/resize/rotate; layers; undo/redo; multi-page operations.
Exit: create, save, reopen and edit a multi-page design.

## Phase 4: OCR to Clean PDF
- Extend the existing OCR pipeline after source-level verification.
- Support both Original Book Style and Clean Text Style outputs from one corrected OCR text model.
- Add Bengali/English recognition validation, per-page review/correction, progress, cancellation, and resumable processing for large scans.
- Export selectable/searchable PDF text and preserve the original input; document limitations for tables, equations, and complex layouts.
Exit: a multi-page scan can be OCR-processed, reviewed, and exported in both styles with page order and text-search tests passing.

## Phase 5: templates and export
Template browser; Bengali/English typography; vector PDF and PNG export; font embedding/fallback; export preview and error recovery.
Exit: exported PDF validated in independent viewers.

## Phase 6: QA and release
Unit/instrumentation tests; corrupt/encrypted/large PDF fixtures; memory/cancellation/rotation/process-death tests; offline and permission audit; GitHub Actions debug APK; release signing kept out of source control; release notes and attribution.
Exit: clean CI build, installable APK and no known critical core-workflow crash.

## Checklist
- [ ] `./gradlew test`
- [ ] `./gradlew lint`
- [ ] `./gradlew assembleDebug`
- [ ] Install/launch API 26+
- [ ] SAF import/export
- [ ] PDF merge/split/compress/rotate/annotate/sign/watermark
- [ ] Compare/grayscale/bookmarks/PDF-to-ZIP/image extraction
- [ ] Designer save/reopen/undo/redo/multi-page
- [ ] Bangla and English text export
- [ ] Large-file memory/cancellation
- [ ] Offline/no unexpected network traffic
