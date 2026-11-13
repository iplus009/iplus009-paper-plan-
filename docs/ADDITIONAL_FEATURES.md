# Additional Feature Proposals

These additions extend Paper Plan beyond the two source projects. They are proposals for the Paper Plan product; they are **not implemented yet**. Each feature must be checked against both codebases before porting or building it.

## Priority 1 — High-value additions

| Feature | What it does | Why it belongs |
|---|---|---|
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
