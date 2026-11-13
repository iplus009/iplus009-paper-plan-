# OCR Studio — OCR to Clean PDF Product & Implementation Plan

Status: **Planned — not implemented**  
Decision: One OCR project can generate **both** output styles from the same reviewed text.

## 1. Product goal

Turn a photo-based or scanned book PDF (including a 300-page document) into clean, searchable, selectable, newly typeset PDFs. The user can export either style or both:

- **Original Book Style:** book-like typography and page composition, with configurable page size, margins, font, paragraph spacing, headings, and page numbering. It preserves the reading structure where detectable; it does not promise an exact replica of the photographed page.
- **Clean Text Style:** reflowed, uncluttered text with consistent typography and spacing, optimized for comfortable reading, searching, and copying.

The two outputs must be generated from one canonical OCR text model, so corrections made once apply to both.

## 2. End-to-end user journey

1. **Capture or Import** — start the in-app Camera Scan for a new single-page or multi-page document, or import an existing scanned PDF/images through Android Storage Access Framework. Camera permission is requested only after the user taps Scan; it is not requested at app launch. If denied, PDF/image import remains available.
2. **Preflight** — inspect page count, dimensions, rotation, image quality, and estimated storage/time. Let the user choose all pages, a range, or selected pages.
3. **Image cleanup** — optional per-page auto-rotate, crop/edge detection, perspective correction/dewarp, deskew, shadow reduction, denoise, and contrast/brightness enhancement. Always keep the untouched source.
4. **Language setup** — choose Bengali, English, or mixed-language recognition. Detect likely language as a suggestion, not an unquestionable decision. Clearly show when an OCR language model is missing and whether it needs a one-time download.
5. **OCR queue** — process pages in small bounded batches, with page-level progress, pause, resume, cancel, and retry failed pages.
6. **Review workspace** — show original page and recognized text side by side. Highlight low-confidence words/regions, allow text correction, page rotation/crop fixes, re-run OCR for a page, and mark non-text pages (illustrations, blank pages, diagrams) to retain or skip.
7. **Structure editing** — allow user to mark or correct headings, paragraphs, columns, lists, page breaks, and reading order. Treat tables, equations, footnotes, and complex layouts as review-required when automatic reconstruction is uncertain.
8. **Preview** — preview representative pages and the full document outline before creating outputs.
9. **Dual export** — choose Book Style, Clean Text Style, or Both. Both exports use the same corrected OCR content but can have separate typography/layout settings.
10. **Result report** — show processed pages, skipped/blank pages, pages requiring review, OCR failures, output paths, and searchable-text check.

## 3. Feature specification

### A. In-app Camera Scan
- Provide a dedicated Scan to PDF entry point with Single Page and Multi-page modes.
- Use CameraX for an in-app capture experience, subject to source/dependency/license audit.
- Request Android CAMERA runtime permission only when the user starts scanning, never on first launch or for unrelated PDF tools.
- Explain why camera access is needed. If permission is denied, keep Import PDF/Images available; do not block the rest of the app. If permanently denied, offer a route to Android app settings without repeatedly prompting.
- Multi-page session: capture page → preview → retake or accept → add next page → reorder/rotate/crop/delete → finish.
- Single-page session: capture → preview/crop → create PDF.
- Include auto edge detection, perspective correction, deskew, flash toggle where supported, and optional color/grayscale/black-and-white processing.
- Save each accepted page safely as the session progresses; recover an interrupted scan session where feasible.
- Export captured pages directly to a normal image-based PDF, or continue into OCR Studio to create searchable PDF and Book Style/Clean Text outputs.
- Keep capture and OCR separate actions so users can create a PDF without running OCR.
- Do not add microphone, location, or broad storage permissions for camera scanning.
- Alternative system document-scanner integration may be evaluated, but must not be the only path if it adds a required Play Services dependency or compromises offline behavior.

### B. Scan cleanup toolkit
- Batch auto-orientation and deskew.
- Detect page boundaries and allow manual crop adjustment.
- Optional perspective correction for photographed pages.
- Optional shadow/background cleanup, denoise, contrast and brightness controls.
- Before/after preview and reset-to-original.
- Never destructively overwrite the source.

### C. OCR and language handling
- Bengali and English, including mixed paragraphs.
- Preserve Unicode text; test Bengali conjuncts, vowel signs, punctuation, numerals, and mixed-script lines.
- Per-page OCR confidence and low-confidence review queue.
- Re-run OCR on selected pages after cleanup or language changes.
- Preserve source page number and region coordinates for traceability.
- Offline-first processing. Do not upload documents to a server by default.

### D. Review and correction
- Original image ↔ extracted text split view.
- Tap a text block to locate its source region when coordinates are available.
- Edit text, split/join paragraphs, correct reading order, and add/remove page breaks.
- Mark headings, lists, columns, captions, tables, equations, and footnotes.
- Keep a correction history with undo/redo.
- Bulk find/replace for repeated OCR mistakes, with preview before applying.

### E. Book Style output
- A4, A5, Letter, and custom page sizes.
- Book-like font choices, margins, line spacing, paragraph indentation/alignment, heading hierarchy, and page numbering.
- Optional chapter title pages and table of contents generated from user-confirmed headings.
- Optional preservation of selected illustrations and diagrams.
- Reflow text to clean pages; do not stretch OCR text to mimic photographed page coordinates.

### F. Clean Text output
- Simple readable typography and consistent spacing.
- Normalize accidental line breaks and join wrapped lines where safe.
- Preserve paragraph boundaries, headings, lists, and user-approved page breaks.
- Optional compact layout and larger-text reading layout.
- Searchable/selectable real PDF text.

### G. Both output mode
- Generate Book Style and Clean Text from one canonical corrected text document.
- Allow separate export settings for each output.
- Display both previews before export.
- Avoid running OCR twice just because the user selected Both.

### H. Large-document controls
- Support 300-page jobs through streaming/page-at-a-time or small-batch processing.
- Bounded worker count and memory-aware bitmap lifecycle.
- Persist job state and intermediate text so an interrupted job can resume.
- Pause/cancel safely; retry a failed page without restarting the whole book.
- Show estimated progress and actual processed/remaining pages; do not invent a completion-time promise.
- Manage temporary files and offer cleanup after successful export.

## 4. Proposed technical architecture

Keep this as a feature module integrated with the shared PDF/storage foundation; do not create a second independent PDF engine.

- `OcrProject`: source URI metadata, page count, selected pages, language settings, cleanup settings, output preferences, job state.
- `OcrPage`: source page index, rotation/crop transform, processing state, confidence summary, extracted blocks, error details.
- `OcrTextBlock`: Unicode text, bounding box, confidence, block type, reading order, source page reference.
- `DocumentStructure`: ordered pages/sections, paragraphs, headings, lists, tables/equations review markers, page breaks.
- `OcrPipeline`: render one page → cleanup → OCR → structure → persist → release bitmap.
- `OcrReview`: correction and structure editing with undo/redo.
- `PdfTextExporter`: shared canonical content → Book Style or Clean Text PDF.
- `OcrJobRepository`: resumable queue state and intermediate results using existing persistence patterns.

Use existing PDF rendering/storage/OCR components after source audit. Reuse dependencies where sound; do not add a second renderer or duplicate OCR engine without a measured reason. Keep processing off the UI thread and make cancellation cooperative.

## 5. Export correctness requirements

- PDF text must be actual selectable/searchable text, not a screenshot of the newly typeset page.
- Embed or correctly reference fonts where licensing and renderer support permit.
- Verify Bangla shaping and glyph coverage in independent PDF viewers.
- Preserve Unicode text extraction and reading order.
- Retain selected figures as image objects where practical; do not silently discard non-text content.
- Report OCR limitations for handwriting, low-resolution images, complex columns, tables, formulas, and decorative typography.
- The original source remains unchanged; exports are new files.

## 6. Delivery phases

### Phase 1 — Feasibility and source audit
- Inspect both repositories' OCR, renderer, storage, persistence, and tests.
- Confirm available OCR languages/engine, license, offline behavior, and minSdk compatibility.
- Benchmark representative Bengali, English, mixed-language, and image-heavy pages.
- Decide reuse vs replacement based on measured quality and license compatibility.
**Exit:** documented engine decision, dependency/license review, baseline benchmark.

### Phase 2 — Reliable OCR foundation
- Import PDF/images, render page-by-page, process bounded batches.
- Add Bengali/English language selection, OCR, page ordering, persistence, progress, pause/resume/cancel/retry.
- Add searchable text PDF export.
**Exit:** 300-page stress test completes without loading all pages into memory; job can resume after process interruption.

### Phase 3 — Image cleanup and review
- Add auto-rotate, crop, deskew, optional perspective/shadow/noise cleanup.
- Build side-by-side review, confidence flags, text correction, page retry, and undo/redo.
**Exit:** user can correct OCR output and rerun only selected pages.

### Phase 4 — Dual layout and export
- Implement canonical text/structure model.
- Build Book Style and Clean Text layout engines.
- Add Both mode, independent previews/settings, and optional illustrations.
**Exit:** one OCR run produces two valid searchable PDFs with corrected text shared across both.

### Phase 5 — Quality, accessibility, and performance
- Test Bengali shaping, mixed scripts, headings, columns, page breaks, font embedding, huge scans, low memory, cancellation, and corrupt pages.
- Add TalkBack/large-text support and clear progress/error messaging.
**Exit:** regression suite passes; no source overwrite; output validated in independent viewers.

## 7. Acceptance checklist

- [ ] Capture a single page and export it as PDF.
- [ ] Capture a multi-page document, reorder/retake/delete pages, and export one PDF.
- [ ] Request CAMERA permission only on explicit Scan action; confirm import remains usable when denied.
- [ ] Import scanned PDF and image sets using SAF.
- [ ] Process selected page ranges and preserve original page order.
- [ ] Bengali, English, and mixed-language test fixtures.
- [ ] OCR confidence and page-level retry.
- [ ] Side-by-side source/text review and corrections.
- [ ] Auto-rotate, crop, deskew, and optional cleanup.
- [ ] Pause/resume/cancel and recover interrupted 300-page jobs.
- [ ] Export Book Style, Clean Text Style, and Both.
- [ ] Search and copy extracted text from exported PDFs.
- [ ] Validate Bangla conjuncts, punctuation, and font rendering in multiple viewers.
- [ ] Keep selected images/figures or clearly mark them for review.
- [ ] Preserve source PDF and report failed/skipped pages.
- [ ] Verify memory usage and temporary-file cleanup.

## 8. Explicit non-goals for first release

- Guaranteed exact visual reconstruction of the original photographed book.
- Guaranteed OCR accuracy for handwriting, formulas, tables, or damaged pages.
- Cloud OCR or automatic document upload.
- Automatic rewriting, summarizing, translating, or changing the author's text.
- DOCX/EPUB export in the first release; keep the data model extensible for later formats.
