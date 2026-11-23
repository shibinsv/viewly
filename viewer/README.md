# viewer

Offline preview of Microsoft Office documents (`.docx`, `.xlsx`, `.pptx`) for Android.
Compose-first API, no network access, no `INTERNET` permission — everything renders on-device.

Rendering is powered by [office-open-xml-viewer](https://github.com/yukiyokotani/office-open-xml-viewer)
(npm `@silurus/ooxml`, MIT): Rust/WASM parsers + TypeScript canvas renderers, running inside a
WebView. The bundle is resolved at build time from the `org.webjars.npm:silurus__ooxml`
webjar on Maven Central and shipped as a single ~4.6 MB `ooxml.zip` asset that extracts to
app storage on first load (~14 MB on disk) — no JS lives in git and the stored APK stays
small. Features the office library supports but this wrapper doesn't expose yet are tracked
in [TODO.md](TODO.md).

## Quick start (Compose)

```kotlin
// Creates state and loads whenever the uri changes (null = nothing loaded):
val state = rememberOfficeViewerState(uri)   // overloads: (), (uri: Uri?), (file: File?)
// or manually: val state = rememberOfficeViewerState(); state.load(uri)

OfficeDocumentViewer(
    state = state,
    modifier = Modifier.fillMaxSize(),
    // Optional custom overlays — defaults shown:
    loadingContent = { /* @Composable BoxScope.() -> Unit */ },
    errorContent = { message, retry -> /* @Composable BoxScope.(String?, () -> Unit) */ },
)
```

`OfficeDocumentViewer` renders every format as a centered, scrollable page column on a gray
background (xlsx additionally gets its own sheet tabs and zoom slider) with pinch-to-zoom
anchored at the fingers. The default overlays are a centered "Rendering…" progress indicator
and an error panel with the failure message and a Retry button; both are replaceable via the
`loadingContent` / `errorContent` slots (they fill the viewer's `BoxScope`).

### State

| Property / method | Meaning |
|---|---|
| `status` | `Idle`, `Loading`, `Loaded`, `Failed` |
| `documentInfo` | `format`, `pageCount` (pages/sheets/slides), `pageIndex` (1-based, updates while scrolling) |
| `errorMessage` | failure detail when `Failed` |
| `debugMessages` | rolling JS-side diagnostics (some ROMs filter logcat) |
| `load(uri)` / `load(file)` | open a document; safe to call before the composable attaches |
| `retry()` | re-load the last source |
| `nextPage()` / `previousPage()` / `goToPage(index)` | page/sheet navigation |
| `zoomIn()` / `zoomOut()` / `fitPage()` / `fitWidth()` | zoom controls |
| `selectedText` | current text selection, or null |
| `copySelection()` / `selectAll()` | clipboard + whole-document selection |

**Security:** the system selection toolbar is replaced by a viewer-owned one (Copy /
Select All only — no system Share or Web Search); copying goes through the app's
`ClipboardManager`, never the WebView's default menu. `selectAll()` is hidden for xlsx,
where selection is cell-based.

## View-based fallback

Hosts without Compose can use the engine directly, or the ready-made activity:

```kotlin
// Engine (the Compose API is built on this)
val view = OfficeViewerView(context)
view.onLoadListener = … // OnLoadListener / OnPageListener / OnErrorListener / OnDebugListener
view.load(uri)

// Or one-shot screen
startActivity(Intent(context, OfficeViewerActivity::class.java).apply {
    putExtra(OfficeViewerActivity.EXTRA_DOCUMENT, uri) // needs a read grant
})
```

## Architecture

```
Build time (viewer/build.gradle.kts)
  └─ packageOoxmlZip: webjar dist/ → filtered ooxml.zip + ooxml-version.txt
                       (generated assets dir — nothing JS-related in git)

Runtime (OfficeViewerView, FrameLayout + WebView)
  ├─ OoxmlAssets.ensureExtracted()  → filesDir/officeviewer/ooxml/<version>/,
  │                                   once per version (zip-slip guarded, stale
  │                                   versions cleaned up)
  ├─ WebViewAssetLoader
  │    ├─ /assets/viewer.html      → bundled asset
  │    ├─ /assets/ooxml/...        → extracted files, custom MIME map
  │    │         (.mjs → text/javascript, .wasm → application/wasm;
  │    │          Android's MimeTypeMap misses both)
  │    └─ /document/<token>/file   → per-instance copy of the document in cacheDir,
  │                                   served same-origin (no file:// CORS issues)
  ├─ viewer.html  → module script; imports ./ooxml/{docx,xlsx,pptx}.mjs
  │    ├─ DocxScrollViewer / PptxScrollViewer / XlsxViewer in a scroll column
  │    ├─ pinch-to-zoom → viewer.setScale(), anchored via _pendingZoomAnchor
  │    └─ OfficeBridge (JS→Kotlin: load/page/error/debug) · ovDispatch (Kotlin→JS)
  └─ OfficeViewerState wires the engine's listeners into Compose state
```

Documents are copied to `cacheDir/officeviewer/<uuid>/file` before loading and deleted when the
view detaches. Each WebView instance only ever serves its own token.

## Dependency management

The renderer is a normal Gradle dependency — bump it in
[gradle/libs.versions.toml](../gradle/libs.versions.toml) (`ooxmlWebjar`):

```toml
ooxmlWebjar = "0.80.2"
```

On build, `packageOoxmlZip` unpacks the webjar (dist located automatically by its
`dist/docx.mjs` marker, so both classic-webjar and npm-package jar layouts work),
drops `types/`, `*.d.ts` and `node.mjs`, and zips the rest into the generated
`ooxml.zip` asset together with `ooxml-version.txt`. At runtime the version file
keys the extraction cache — a version bump re-extracts and drops stale copies.

Size impact: ~4.6 MB stored in the APK (the zip is already compressed, so it is
stored as-is) instead of ~14 MB of loose JS/WASM; first document open pays a
one-time ~1–2 s extraction.

## Requirements and limits

- System WebView ≥ 91 (checked at runtime; older providers produce a clear error instead of a
  blank screen). Auto-updated WebView fleets are fine.
- Read-only viewing. Editing and saving are out of scope for the underlying library.
- Legacy binary formats (`.doc`, `.xls`, `.ppt`) are not supported.
- Documents using fonts absent from the device render with system-font fallback: correct
  content, approximate metrics (see TODO.md — font embedding).
- Encrypted documents are not wired up yet (TODO.md).
