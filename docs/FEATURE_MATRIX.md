# Feature Integration Matrix

| Feature | PDF Toolkit | PaperKnifePlus | Paper Plan plan |
|---|---|---|---|
| Merge/split/rotate/reorder | Existing operations/screens | UX patterns | Keep one tested engine |
| Compress | Existing compressor | Savings estimate UX | Keep engine; port estimate UX if needed |
| Sign/watermark | Existing | Dedicated views | Keep foundation; compare UX |
| OCR/scan | Existing flavor-specific implementations | — | Preserve flavor-aware behavior; validate offline |
| Search/viewer/print | Existing viewer/search | Search/print/page-jump patterns | One unified viewer |
| Compare PDFs | Verify exact current coverage | Dedicated UI | Port if missing/incomplete |
| Grayscale | Verify exact current coverage | Dedicated operation | Port if missing |
| Bookmarks | Verify exact current coverage | Dedicated UI | Port if missing |
| PDF to ZIP | Verify exact current coverage | Dedicated tool | Port if missing |
| Embedded image extraction | Verify exact current coverage | Dedicated tool | Port if missing |
| Custom home tools | Inspect existing settings | Customizable grid | Port interaction |
| OCR Studio / OCR to Clean PDF | Existing flavor-specific OCR; audit engine, languages, and export path | — | Build a resumable OCR project with image cleanup, side-by-side correction, structure editing, and Book Style + Clean Text + Both export from one corrected text model; see [OCR plan](OCR_CLEAN_PDF_PLAN.md) |
| PDF Designer | Not target feature | Not present | Build new feature |
| Templates/layers/undo | — | — | Build new design model/editor |

“Verify” means inspect actual source and tests before implementation; absence from a README is not proof a feature is missing.
