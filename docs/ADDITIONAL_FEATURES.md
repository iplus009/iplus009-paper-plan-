# Additional Feature Proposals

These additions extend Paper Plan beyond the two source projects. They are proposals for the Paper Plan product; they are **not implemented yet**. Each feature must be checked against both codebases before porting or building it.


## Priority 1 — OCR to Clean PDF (new)

Turn a photo-based or scanned PDF into a newly typeset, text-based PDF. The user can choose **both output styles** from the same OCR result:

1. **Original Book Style:** reconstruct a book-like layout with configurable page size, margins, font, paragraph spacing, headings, and page numbering. This aims to preserve the reading structure, but should not promise pixel-perfect reproduction of the photographed pages.
2. **Clean Text Style:** reflow recognized text into a simple, readable document with consistent typography, spacing, and paragraph layout.

### Workflow
- Import a multi-page scanned/photo PDF or a set of page images.
- Run batch, on-device OCR with Bengali and English language support where language data is installed.
- Preserve page order and keep a source-page reference for every recognized text block.
- Show OCR confidence and flag pages/regions that need review.
- Allow users to correct recognized text before export; support previewing a few pages before processing/exporting the full document.
- Provide a choice of output style: Original Book Style, Clean Text Style, or both.
- Offer page size, font, margins, line spacing, paragraph alignment, and page-number controls.
- Export selectable, searchable text as real PDF text—not a single flattened page image. Keep embedded images/figures as optional retained content where practical.
- Provide a completion report with pages processed, pages needing review, and OCR failures.

### Important limitations and engineering requirements
- OCR may misread blurred, skewed, shadowed, low-resolution, handwritten, mathematical, or complex multi-column content. Make uncertainty visible and let users correct it.
- Bengali conjuncts, punctuation, mixed Bangla-English text, and font shaping/embedding need dedicated test fixtures.
- Tables, equations, footnotes, diagrams, and complex book layouts may not be reconstructed exactly; preserve them as images or mark them for manual review rather than silently inventing structure.
- A 300-page job must run in bounded batches with progress, cancellation, resumability/recovery, and memory-aware image processing. Do not load all page bitmaps into memory at once.
- Keep the original PDF untouched and save the generated document as a separate output.
- OCR should work offline by default; explain if a language pack or OCR engine must be downloaded separately.
- Add regression tests for page order, text extraction/search, Bengali shaping, mixed-language text, low-confidence pages, and large-document memory use.

Camera Scan is the direct capture entry point for OCR Studio; users may also export a regular image-based PDF without OCR. Detailed OCR Studio specification, UX workflow, architecture, delivery phases, and acceptance tests: [OCR_CLEAN_PDF_PLAN.md](OCR_CLEAN_PDF_PLAN.md). The feature must generate Book Style, Clean Text Style, or Both from one corrected OCR project.

## Priority 1 — High-value additions

| Feature | What it does | Why it belongs |
|---|---|---|
| In-app Camera Scan to PDF | Capture one page or a multi-page document, review/reorder/crop/retake pages, and export a PDF or continue to OCR Studio | Removes the need for a separate scanning app; request camera permission only when Scan is tapped |
| Batch Processing Queue | Apply the same operation to multiple PDFs, show per-file progress, retry failures, and keep outputs separate | Saves time when users have many documents |
| Advanced Page Organizer | Thumbnail grid with drag-and-drop reorder, multi-select, rotate, duplicate, extract, and delete | Makes page-level editing visual and easier |
| Permanent Redaction | Mark and permanently remove selected text/regions from the exported PDF; warn that drawing a black box is not redaction | A meaningful privacy tool; requires export-level verification tests |
| Fill & Flatten Forms | Fill interactive PDF fields, save a copy, and optionally flatten fields for consistent viewing | Completes a common document workflow |
| Smart Export Presets | Saved profiles for small file size, print quality, sharing, and image-heavy documents | Makes compression/export understandable without exposing technical settings |
| Searchable Scan PDF | Scan/import pages, run on-device OCR, and export a searchable PDF with selectable text | Connects scanning and OCR into one useful workflow |

## Priority 2 — Differentiating features

| Feature | What it does | Notes |
|---|---|---|
| PDF Compare & Change Summary | Compare two versions and highlight changed pages/text where reliable | Reuse existing compare capability if present; avoid claiming pixel-perfect semantic comparison |
| PDF/A Export & Validation | Export archival-friendly PDF/A where supported and report validation results | Use a proven library or validator; never label an unvalidated file PDF/A |
| Document Cleanup | Remove selected metadata, hidden annotations, and unwanted attachments from a copy | Show exactly what will be removed; preserve originals |
| QR / Barcode Elements in Designer | Add editable QR codes and common barcodes to layouts | Useful for invoices, certificates, event passes, and flyers |
| Reusable Brand Kit | Save colors, fonts, logos, and common design elements for reuse in Designer | Store locally; make import/export explicit |
| Template Import & Sharing | Export/import Paper Plan templates as a documented package format | Validate imported assets and avoid executing embedded content |

## Priority 3 — Quality and trust

- **Local processing indicator:** clearly show when a task is processed on-device; do not imply cloud upload.
- **Document activity history:** show operation, timestamp, source name, and output location, with a clear history-delete action.
- **Accessibility checks:** flag missing document titles, low-contrast design choices, and images without alt text where detectable; present findings as guidance, not formal certification.
- **Crash-safe work recovery:** persist Designer drafts and recover interrupted edits without overwriting the source PDF.
- **Storage controls:** show cache and temporary-file usage, with a safe cleanup action that never deletes user documents.
- **Keyboard, TalkBack, and large-text support:** make tool flows and Designer controls accessible.

## Recommended implementation order

1. Batch queue and advanced page organizer.
2. Permanent redaction and form fill/flatten.
3. Searchable scan PDF and smart export presets.
4. Designer QR/barcode elements and reusable brand kit.
5. Compare improvements, PDF/A validation, and document cleanup.
6. Accessibility, recovery, and storage polish throughout every phase.

## Engineering and safety requirements

- Preserve the original input file; write edits to a new output unless the user explicitly chooses otherwise.
- Redaction must remove underlying content from the exported file, not merely cover it visually. Add tests that extract text and inspect rendered output after redaction.
- PDF/A support must be validated with a real conformance validator before displaying a compliance result.
- OCR, rendering, and batch operations must use bounded concurrency and memory-aware processing.
- Use Android Storage Access Framework permissions and avoid broad storage access unless genuinely required.
- Keep core document operations offline by default. Any future online feature must be optional, clearly disclosed, and separately approved.
- Imported templates and documents are untrusted input: validate file sizes, image dimensions, archive entries, and parser failures.
- Do not promise secure erasure on flash storage. Cache cleanup should be described accurately.
- Add unit, instrumentation, regression, and large-file tests for every new operation.

## Scope rule

Do not add every proposal blindly. First inspect both repositories and their tests, then mark each item as **already supported**, **needs UX integration**, **needs implementation**, or **not feasible with the current dependency set**. Avoid duplicate engines and duplicate implementations.
