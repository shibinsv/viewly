# Not yet exposed — supported by the office library

Features office-open-xml-viewer (`@silurus/ooxml`) already supports but this wrapper
doesn't expose. Underlying API names are from the vendored version's type definitions.

## Interaction

- **Text search** — `findText(query)`, `findNext()`, `findPrev()`, `clearFind()` on all
  three viewers. Needs a search field UI + result navigation wired through
  `OfficeViewerState` and the JS dispatch.
- **Element-level selection** — `enableElementSelection` (select shapes/tables/cells as
  objects with outlines and context menus). Text selection and clipboard copy are already
  wired (`selectedText`, `copySelection()`, `selectAll()`).
- **Hyperlinks** — `onHyperlinkClick` callback + `openExternalHyperlink` helper; currently
  links render but taps do nothing.
- **PPTX media playback** — `enableMediaPlayback` option (embedded video/audio).

## Navigation

- **xlsx programmatic navigation** — `scrollToCell(ref)`, `setViewportOffset`,
  `setSelection('B2:D5')`.
- **Paged (single page) mode for docx/pptx** — `DocxViewer`/`PptxViewer` canvas viewers
  are an alternative to the scroll column; could be a state flag.
- **Speaker notes (pptx)** — `getNotes(slideIndex)`.

## Fidelity

- **Custom font embedding** — bundle document fonts into the WebView `FontFaceSet` for
  exact layout; today device fonts are used as fallback.
- **Equation rendering** — MathJax engine chunks are already vendored in
  `assets/ooxml/`, but the `math` entry isn't wired into `viewer.html`.
- **3D charts** — `three-d` entry vendored, not wired (charts render flat).
- **Region maps** — `region-map` entry vendored, not wired.

## Robustness / config

- **Encrypted documents** — Agile Encryption unlock with a password via load options.
- **Resource limits** — `resourceLimits` (per-entry/total byte caps, entry count) are at
  library defaults; not configurable through the wrapper.
- **Worker render mode** — `mode: 'worker'` parses/renders off the WebView main thread;
  useful for very large workbooks. Needs the vendored `assets/render-worker-*.js`
  (already shipped) and URL plumbing.
- **Zoom bounds & appearance** — pinch clamps (0.25–6) and column styling
  (`background`, `pageShadow`, `gap`) are hardcoded in `viewer.html`; should become
  `OfficeViewerState`/composable parameters.
- **Dark theme** — column background is fixed light gray.

## Not planned (upstream out of scope)

- Editing / document round-tripping (upstream is view-only by design)
- Legacy binary formats (`.doc`, `.xls`, `.ppt`)
- PPTX animations and transitions
- Mail merge field rendering (docx)
