# Paper Plan — Proposed Architecture

## Principles
- Offline-first; document processing stays on-device by default.
- One PDF engine, one renderer lifecycle and one navigation graph.
- Designer documents remain editable until export.
- Reliability and test coverage before feature count.

## Proposed modules
```
:app
:core:common
:core:storage
:core:database
:core:pdf-engine
:core:pdf-renderer
:core:design-model
:core:design-renderer
:core:pdf-exporter
:feature:home
:feature:pdf-tools
:feature:pdf-viewer
:feature:scanner
:feature:ocr
:feature:designer
:feature:templates
:feature:history
:feature:settings
```
Introduce modules as boundaries stabilize; avoid empty modules created only for appearance.

## Responsibilities
- storage: SAF, URI grants, streams and safe temporary files.
- database: Room for recent files, jobs, templates and saved designs.
- pdf-engine: single PdfBox initialization and operation interfaces.
- pdf-renderer: page rendering, bounded bitmap cache and cancellation.
- design-model: serializable document/page/element models without Compose dependency.
- design-renderer: canvas rendering, selection and hit testing.
- pdf-exporter: vector/text/image PDF export and PNG raster export.
- features: UI and ViewModels over core interfaces.

## Designer model
```
DesignDocument
  id, title
  documentSettings(pageSize, orientation, background)
  pages[]
    id
    elements[]
      TextElement
      ImageElement
      ShapeElement
      LineElement
      IconElement
  exportSettings
```
Elements need stable IDs, page-coordinate position/size, rotation, z-order, opacity and lock/visibility state.

## Designer scope
A4/A5/Letter/Legal/custom page sizes; portrait/landscape; CV, poster, flyer, certificate, invoice and blank templates; text styling; gallery images/crop/resize; shapes/lines/icons; solid/gradient/image backgrounds; layer reorder/duplicate/lock/hide/delete; undo/redo; multi-select; add/duplicate/delete/reorder pages; PDF and PNG export.

## Bengali typography and export
Test Bangla shaping and conjuncts early. Embed fonts only where licenses permit. Preserve text and vector shapes in PDF where possible; do not rasterize the whole page by default. Validate exported files in Android and desktop PDF readers.

## Navigation
Home (recent, quick actions, favorites, create design); searchable PDF Tools; Viewer (search, page navigation, print/share, launch tools); Designer (canvas, properties, layers, templates); History; Settings.

## Performance and privacy
Use SAF instead of broad storage permissions. Process large files with streams/temp files. Bound bitmap caches and close PDF resources deterministically. Run expensive work off main thread with progress/cancellation. No INTERNET permission unless a separately approved feature needs it. Never upload documents or collect their contents for analytics.
