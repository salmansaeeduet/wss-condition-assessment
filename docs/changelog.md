# Changelog — WSS Condition Assessment Balochistan

All notable changes to the Android app, newest first.

---

## 2026-05-16 (latest)

### Geometry picker: hide Edit when empty, remove Arrow checkbox
**Files modified:** `GeometryPickerActivity.java`, `activity_geometry_picker.xml`

- **Edit button** is now `GONE` in drawing mode when no shapes have been committed yet; it appears as soon as the first shape is saved. Previously it was always visible and showed a "no shapes to edit" toast when tapped empty.
- **Arrow at end checkbox** removed from the layout and all Java references (`cbArrow` field, `findViewById`, `setVisibility` calls, `item.arrow = cbArrow.isChecked()`). New lines no longer set `arrow = true`; the arrowhead rendering in `GeometryOverlay` is retained for backward compatibility with any previously saved data.

---

### Map geometry: shared context, default labels, UI cleanup, multi-point fix
**Files modified:** `QuestionnaireParser.java`, `QuestionFragment.java`, `GeometryPickerActivity.java`, `SurveyMapActivity.java`, `SurveyRepository.java`, `activity_geometry_picker.xml`, `activity_survey_map.xml`

**Bug fixes:**

- **XLSX sheet not found (absolute target path):** `QuestionnaireParser` built the ZIP entry key as `"xl/" + target`, but the relationship file stores absolute targets (`/xl/worksheets/sheet5.xml`). Combined key was `"xl//xl/…"` — never found, silently returned zero questions. Fixed: strip the leading `/` when target is absolute.
- **Only first question loaded:** Row IDs from row 3 onwards use Excel increment formulas (`A2+1`) whose cached `<v>` elements are empty. Parser saw an empty ID string and skipped every row after row 2. Fixed: detect formula cells (`<f>` element present, empty `<v>`), derive ID from the row's `r` attribute minus 1 (row 1 = header).
- **Multiple points — only first shown:** In POINT drawing mode, all taps accumulated into `currentDraft` before the user pressed Add, storing multiple coordinates in a single POINT `GeometryItem`. Rendering always used `points.get(0)`. Fixed: POINT mode now auto-commits on each tap so every tap creates its own named `GeometryItem` with exactly one coordinate.

**Geometry map improvements:**

- **Shared map context:** Both geometry answer pickers and sketch attachment pickers now show all geometry from the entire survey as read-only background overlays — covering both GEOMETRY answer items (keyed by question ID) and GEOMETRY media attachments (keyed by `att_<id>`). `SurveyRepository` exposes a new async `getAttachmentsForSurvey()`. `buildOtherGeomsJson()` helper in `QuestionFragment` consolidates both sources and correctly excludes the item currently being edited.
- **Default geometry labels:** When no `default_label` is set (unnamed GEOMETRY questions or sketch attachments), `GeometryPickerActivity` now auto-labels each committed item by type with a per-type counter: "Point 1", "Point 2", "Line 1", "Polygon 1", etc.
- **Always-visible labels in summary map:** `SurveyMapActivity` previously showed geometry item names only on tap (Toast). Now a canvas-draw `Overlay` renders every item's name as a dark rounded-rect chip at its centroid on every frame, matching the in-picker experience. Hint text updated to "Tap any feature for details".
- **LABEL mode removed from picker UI:** The LABEL button is hidden (`visibility="gone"`). Named POINTs serve the same purpose. Existing saved LABEL items still render correctly.
- **`loadOtherGeoms` key parsing:** Now accepts both integer question-ID keys and `"att_<id>"` string keys; falls back to `hashCode` for anything else, so future key formats won't silently drop overlays.

**Layout tweaks (carried over from previous session):**

- Attachment icon buttons in `fragment_question.xml` and `view_sub_question.xml` now use `layout_width="0dp"` + `layout_weight="1"` for even spacing across the row.
- `ic_map.xml` gains `android:tint="?attr/colorControlNormal"` so the map icon matches the theme colour.

**Geometry picker UI cleanup:**

- `btnUndo` and `btnAction` (Add) are now `GONE` rather than disabled when their action is unavailable (no draft points / insufficient draft points). They reappear as soon as the action becomes possible.
- In SELECTING mode both buttons are always `GONE`.
- The green `btnDone` is hidden during EDITING mode — "Done Edit" already exits that mode; Done belongs to the drawing/selecting context.

---

### Create parallel development fork — cref_wss_02
**Action:** Copied `cref_wss_01` to `C:\Users\Salman\AndroidStudioProjects\cref_wss_02` to allow parallel development on a separate app variant. The fork starts from v1.2 (all features up to the hybrid geometry system). Changes in the fork:
- `applicationId` → `com.wss.cref.assessment.v2` (allows both to install side-by-side on the same device)
- `app_name` → `"WSS Condition Assessment 2"`
- `rootProject.name` → `CREF_WSS_02`
- Java namespace (`com.example.cref_wss_01`) and all source files left unchanged
- Fresh git repo, no shared history; no GitHub remote yet

---

## 2026-05-16

### Hybrid geometry system: named GEOMETRY items + sketch attachments on all questions
**Files modified:** `GeometryUtils.java`, `GeometryPickerActivity.java`, `Question.java`, `QuestionnaireParser.java`, `QuestionFragment.java`, `SurveyMapActivity.java`, `SurveyRepository.java`, `MediaAttachmentDao.java`, `SurveyImporter.java`, `fragment_question.xml`, `view_sub_question.xml`

**Part 1 — Enhanced GEOMETRY answer type:**

- `GeometryItem` now has a `name` field (JSON key `"n"`). Items drawn in a GEOMETRY question are auto-named from the question's AnswerOptions column: the first option becomes the default label ("Pump"). A single item is named "Pump"; subsequent items are "Pump 2", "Pump 3", etc. If no AnswerOptions are set, items remain unnamed.
- **Rename dialog**: selecting an item in EDITING mode now shows a "Rename item" dialog (pre-filled with current name). Clearing the field removes the name.
- **Name label rendered on map**: `GeometryOverlay` draws a dark rounded-rect chip with white text at the item centroid whenever `item.name` is set (both in picker and in read-only overlays).
- `GeometryUtils.summary()` now lists named items by name ("Pump 1, Pump 2, 1 polygon saved") instead of counting them anonymously.
- `QuestionnaireParser` reads the first pipe-split value in the AnswerOptions column as `geomLabel` for GEOMETRY questions.
- **Survey map**: named LINE/POLYGON items get a centroid Marker in `SurveyMapActivity` — tapping shows the item name and parent question text.

**Part 2 — Sketch attachment on every question:**

- A "Draw on Map" button (`@drawable/ic_map`) appears in the attachment row of every question (and sub-question), regardless of answer type.
- Tapping launches `GeometryPickerActivity` in unlabelled mode (no `default_label`). The result is written to `getFilesDir()/geom/<surveyId>_<qId>_<ts>.geom` and saved as a `MediaAttachment` with `mediaType = "GEOMETRY"`.
- **Thumbnail**: GEOMETRY attachments appear as a map-icon tile with a summary line (e.g. "2 lines saved") at the bottom. Tapping reopens the picker to edit — on save the file is overwritten in-place and the DB record updated.
- **Survey map**: `SurveyMapActivity` now also renders all GEOMETRY MediaAttachments from Room DB alongside GEOMETRY answer items.
- **Export**: `.geom` files are already included via the existing `filePath` logic in `SurveyExporter`.
- **Import**: `SurveyImporter.guessMediaType()` detects `.geom` → "GEOMETRY"; `buildMergeBundle()` copies `.geom` files to `getFilesDir()/geom/` (private) instead of `getExternalFilesDir(null)` (media).
- `MediaAttachmentDao` gains an `@Update` method; `SurveyRepository` exposes `updateMediaAttachment()`.

---

### Flexible REQ / PREFIX tag system for export validation and filename
**Files modified:** `RequiredField.java`, `SurveyExporter.java`, `SurveyListActivity.java`, `ImportPreviewActivity.java`, `arrays.xml`, `strings.xml`, `Survey_Questionnaire_26050601.xlsx`

Required fields and filename parts are now fully configured in the questionnaire XLSX Tag column (column J) — no code changes are needed when the questionnaire changes.

**Tag syntax (pipe-separated parts in column J):**

| Part | Effect |
|---|---|
| `REQ:Label` | Field must be answered before export; label shown in the missing-field dialog |
| `PREFIX:n` | Answer used as the n-th segment of the ZIP filename (sorted by n) |
| `REQ:Label\|PREFIX:n` | Both — required AND contributes to filename |
| _(blank)_ | No special behaviour |

Rules:
- `n` must be a positive integer; non-integer values are silently ignored.
- Empty PREFIX field values are skipped silently — only REQ fields block export.
- If all PREFIX fields are empty or none are defined, filename falls back to `R.string.export_filename_prefix` + timestamp (default: `"WSS Survey_timestamp.zip"`).
- `|` must not appear inside a REQ label; tag parsing is case-insensitive (`Prefix:1` and `PREFIX:1` both work).

**Survey card title** is now driven by the PREFIX:1 field's answer (the primary filename identifier). Falls back to the first REQ field, then "Unnamed Survey".

**`RequiredField.FilenamePrefix`** — new inner static class. `parseAll(List<Question>)` scans for `PREFIX:n` parts, returns a list sorted by n. `RequiredField.getDisplayValue(String raw, String answerType)` added as a static overload so PREFIX fields can apply COMPOUND-aware display extraction without a RequiredField instance.

**`arrays.xml`** — `export_required_fields` string-array removed; file kept as a comment placeholder.

**`strings.xml`** — added `export_filename_prefix` (`"WSS Survey"`) as the fallback filename prefix.

---

## 2026-05-15 (latest)

### Show exported filename in success dialog and survey card
**Files modified:** `SurveyListActivity.java`, `SurveyAdapter.java`, `survey_item.xml`

After a successful export the confirmation dialog now shows the exact ZIP filename (e.g. `"File: SchemeName_Provider_260515143022.zip"`) beneath the folder message. Each survey card in the list also shows a persistent `"Last export: <filename>"` line in italic below the date, so surveyors can always see which file was last sent without opening the export menu again.

`getDisplayName(Uri)` queries `OpenableColumns.DISPLAY_NAME` via the ContentResolver for `content://` URIs (MediaStore) and falls back to `File.getName()` for legacy `file://` URIs.

---

### Fix: export ZIP saved to inaccessible app-private directory on Android 10+
**Files modified:** `SurveyExporter.java`, `file_paths.xml`

Exports were previously written to `getExternalFilesDir()` (app-private external storage), which is invisible to the system file picker and to other apps — making import testing impossible and preventing manual file retrieval.

**Fix:** `SurveyExporter` now uses two paths based on API level:
- **API 29+ (Android 10+):** `MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)` with `RELATIVE_PATH = "Documents/"`. No storage permission needed; returns a `content://` URI usable for sharing directly.
- **API < 29:** `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS)` written directly; URI wrapped via FileProvider for sharing.

An earlier attempt used `MediaStore.Downloads.EXTERNAL_CONTENT_URI`, which targets the Downloads collection and returned null on Android 16 when `RELATIVE_PATH` was set to `"Documents/"`. Switching to `MediaStore.Files` resolved this.

`file_paths.xml` gained `<external-path name="public_documents" path="Documents/" />` to allow FileProvider to serve the public Documents path on API < 29.

---

### Survey import and merge
**New files:** `SurveyImporter.java`, `MergeConflict.java`, `ImportSession.java`, `ImportPreviewActivity.java`, `MergeConflictActivity.java`, `ConflictResolutionAdapter.java`, `activity_import_preview.xml`, `activity_merge_conflict.xml`, `item_merge_conflict.xml`, `item_survey_select.xml`, `ic_import.xml`  
**Files modified:** `SurveyExporter.java`, `SurveyRepository.java`, `SurveyListActivity.java`, `activity_survey_list.xml`, `AndroidManifest.xml`

Surveyors working the same scheme from different devices can now share a partially completed survey ZIP with a colleague who merges it into their own copy, resolving answer conflicts in a single review screen.

**Export format change:**  
`SurveyExporter` now writes a `Tag` column as the first CSV column (previously the first column was `Question`). The importer detects the format version from the header, so old ZIP exports remain importable via question-text fallback matching.

**Import entry points:**
- New FAB (download arrow icon) in `SurveyListActivity` opens a file picker filtered to `application/zip`.
- `ImportPreviewActivity` is registered as `ACTION_VIEW` and `ACTION_SEND` handler for `application/zip`, so a ZIP shared from Files, WhatsApp, or email opens the app directly.

**Import / merge flow:**
1. `SurveyImporter.parseZip()` extracts `survey_data.csv` + all media files to a temp directory under `getCacheDir()`.
2. `ImportPreviewActivity` shows a summary card (survey name, answer count, media count) and lets the user choose:
   - **Create new survey** — all imported answers applied automatically, no conflict check.
   - **Merge with existing** — user picks a local survey from a list; conflicts are computed.
3. If merging with no conflicts: a confirmation dialog shows the auto-merge count → one tap executes.
4. If true conflicts exist (both sides have different answers for the same question): `MergeConflictActivity` opens, listing every conflict with local vs imported values side-by-side.

**Conflict resolution (per question):**

| Radio option | When available |
|---|---|
| Keep Local | Always |
| Keep Imported | Always |
| Combine | `TEXT` (newline + `---` separator), `MULTIPLE_CHOICE_MULTI` (union of selections), `COMPOUND` (field-level: keep local value, fill blanks from imported), `GEOMETRY` (merge JSON geometry arrays) |

Attachments (PHOTO/VIDEO/AUDIO) are **always merged automatically** — both sides' files are copied and inserted regardless of the answer resolution choice.

**`SurveyRepository.executeBatchUpsert()`** added: upserts a list of `Answer` records and inserts a list of `MediaAttachment` records in a single executor-queued operation, used by both new activities.

**Question matching in importer:** tries tag match first (new CSV format), falls back to normalised question-text match (old CSV format or missing tag). Recurses into sub-questions.

**Temp file cleanup:** temp directory under `getCacheDir()` is deleted after successful merge execution. On cancellation (user backs out) it is cleaned up the next time a new import starts, or by the OS cache eviction policy.

---

## 2026-05-15

### Stable mandatory-field tags — decouple required fields from question IDs
**Files:** `Question.java`, `QuestionnaireParser.java`, `RequiredField.java`, `SurveyListActivity.java`, `SurveyExporter.java`, `res/values/arrays.xml`

Previously, `R.array.export_required_fields` used integer question IDs (e.g., `"9:Name of Scheme"`). These IDs are row-based and change whenever the questionnaire is modified, breaking export validation and the survey-list title.

**Changes:**

- Added a `Tag` column (column J, index 9) to the questionnaire XLSX. Any question can have a short stable identifier (e.g., `SCHEME_NAME`, `INFO_PROVIDER`). Most rows leave it blank.
- `Question` gains a `tag` field (String, empty if unset). `QuestionnaireParser.processRow` reads column 9 and passes it through; `Parcelable` serialisation updated accordingly.
- `RequiredField.parseAll` now takes a `List<Question>` argument and accepts entries in the form `"TAG:Label"`. It resolves each tag to an integer question ID at runtime by searching the question list. Questions with no matching tag are silently omitted — a misconfigured entry never crashes the export flow.
- `arrays.xml` entries updated from `"9:Name of Scheme"` / `"3:Information Provider Name"` to `"SCHEME_NAME:Name of Scheme"` / `"INFO_PROVIDER:Information Provider Name"`.
- Both call sites (`SurveyListActivity`, `SurveyExporter`) updated to pass the question list.

**XLSX change required:** Add column J header `Tag` and set `SCHEME_NAME` on the "Name of Scheme" question row and `INFO_PROVIDER` on the "Information Provider Name" question row. The app will not export until those tags are present and the required fields resolve.

---

## 2026-05-15

### Fix: photo attachment crash — FileProvider authority + path mismatch
**Files:** `QuestionFragment.java`, `SurveyListActivity.java`, `res/xml/file_paths.xml`

The app's **application ID** (`com.wss.cref.assessment.v1`) and its **Java package namespace** (`com.example.cref_wss_01`) are different. Two separate places assumed they were the same, causing back-to-back crashes when tapping "Add Photo":

**Crash 1 — wrong authority string in code:**  
`FileProvider.getUriForFile()` was called with the hardcoded string `"com.example.cref_wss_01.provider"`, but the manifest registers the provider as `"${applicationId}.provider"` = `"com.wss.cref.assessment.v1.provider"`. Android couldn't find the provider registration → `IllegalArgumentException: Couldn't find meta-data for provider`.  
Fix: replaced all hardcoded authority strings in `QuestionFragment` (photo, video, audio capture + gallery import) and `SurveyListActivity` (survey export share) with `getPackageName() + ".provider"`.

**Crash 2 — wrong path root in file_paths.xml:**  
`file_paths.xml` used `<external-path path="Android/data/com.example.cref_wss_01/files/Pictures" />`, but the actual runtime path was `Android/data/com.wss.cref.assessment.v1/files/Pictures`. FileProvider couldn't match the file → `IllegalArgumentException: Failed to find configured root`.  
Fix: replaced `<external-path>` (requires full path from storage root, package name embedded) with `<external-files-path>` (maps directly to `Context.getExternalFilesDir()` at runtime, no app ID in path).

---

### Fix: unreported IOException in MapSearchHelper lambda
**File:** `MapSearchHelper.java`

`conn.getResponseCode()` was called inside a `MAIN.post(() -> ...)` lambda. `Runnable.run()` cannot declare checked exceptions, so the `IOException` from `getResponseCode()` was unreported — compile error. Fix: read the status code once in the surrounding `try` block and capture the `int` in the lambda.

---

### Place search in all map activities (Nominatim geocoding)
**New file:** `MapSearchHelper.java`  
**Files modified:** `MapPickerActivity.java`, `MapAreaDownloadActivity.java`, `GeometryPickerActivity.java`, `activity_map_picker.xml`, `activity_map_area_download.xml`, `activity_geometry_picker.xml`

Surveyors can now type a place name (city, district, village) and zoom directly to it in any map screen — eliminates the need to pan/pinch from the default Pakistan overview.

**Implementation:**
- `MapSearchHelper` wraps the Nominatim OpenStreetMap geocoding API (no API key, free). Returns up to 5 results with display name + bounding box. A `searchVersion` counter prevents stale network responses from overwriting newer results.
- Each map activity's top panel now contains a compact search row (EditText + button). Tapping a result calls `zoomToBoundingBox()` if Nominatim returned a bbox, otherwise `animateTo(center, zoom 14)`. Results collapse after selection.
- `MapPickerActivity` gained a top `CardView` panel for the first time (it previously had no top bar). The `ImageButton#btnBack` is included so the activity can be dismissed without confirming a location.
- `MapAreaDownloadActivity` top bar converted from a floating `LinearLayout#topBar` to the same `CardView#topPanel` + `LinearLayout#topPanelInner` pattern used by `GeometryPickerActivity`. The back button background changed from `@drawable/bg_circle_semi` (designed for floating over the map) to `selectableItemBackgroundBorderless` (matches the card background).

---

### Satellite label overlay — CartoDB dark_only_labels
**File modified:** `EsriTileSourceFactory.java`

The ESRI World Imagery base layer shows no place names; surveyors couldn't identify cities or districts while zoomed out. A transparent label overlay is now applied on top of every satellite view in every map activity.

**Tile source:** CartoDB `dark_only_labels` (`@2x` = 512 px tiles). White text on a fully transparent PNG background — designed for satellite/dark base maps. Loads from three CartoDB CDN servers for parallelism. Uses standard `z/x/y` tile ordering (not ESRI's `z/y/x`).

Previously the overlay used ESRI World Boundaries and Places (256 px, opaque background at low zooms), which produced poor contrast and blocked the satellite imagery.

---

### GEOMETRY answer type — full map annotation tool
**Files:** `GeometryUtils.java`, `GeometryPickerActivity.java`, `QuestionFragment.java`, `SurveyMapActivity.java`, `QuestionnaireParser.java`, `activity_geometry_picker.xml`, `docs/answer_types_reference.md`

Replaced the old `LINE` and `POLYGON` answer types with a unified `GEOMETRY` type (no AnswerOptions) that supports four sub-types in a single answer:

| Sub-type | Description |
|---|---|
| **POINT** | Single location dot |
| **LINE** | Polyline; optional arrowhead at end |
| **POLYGON** | Closed filled shape |
| **LABEL** | Text annotation anchored to a map position |

**New JSON storage format:**
```json
[
  {"t":"POINT","c":[[lat,lng]]},
  {"t":"LINE","c":[[lat,lng],[lat,lng]],"arrow":true},
  {"t":"POLYGON","c":[[lat,lng],[lat,lng],[lat,lng]]},
  {"t":"LABEL","c":[[lat,lng]],"text":"Pump station"}
]
```

**Backward compatibility:** `QuestionnaireParser` converts old `LINE`/`POLYGON` answer types to `GEOMETRY`. `GeometryUtils.fromJson()` detects the old `[[[lat,lng],...],...]` format and assigns type `"LINE"` to each item.

**Geometry picker UI:**
- Mode selector at the top: Point / Line / Polygon / Label toggle buttons
- Arrow toggle checkbox appears when Line mode is active
- Three-mode state machine: DRAWING → SELECTING → EDITING
- EDITING: drag white point handles to reposition; tap midpoint handle to insert new point; Undo Pt removes last point; Delete removes entire shape
- Other questions' geometries displayed as faded read-only background overlays for spatial context
- Shape colors are deterministic per question ID (`GeometryUtils.colorForQuestion`)

**`SurveyMapActivity`** updated to render all four sub-types (previously only LINE/POLYGON).

---

### Fix: `GeometryPickerActivity` crash on launch — survey appeared to "close"
**Files:** `app/src/main/res/values/themes.xml`

`GeometryPickerActivity` was silently crashing in `onCreate` because `MaterialButtonToggleGroup` requires a Material Components theme, but the base theme inherited from `Theme.AppCompat.DayNight.NoActionBar`. The process was killed and Android restarted the app at `SurveyListActivity`, making it appear as though the survey had closed without error.

**Fix:** Changed `Base.Theme.CREF_WSS_01` parent from `Theme.AppCompat.DayNight.NoActionBar` to `Theme.MaterialComponents.DayNight.NoActionBar`. The Material Components library (`libs.material`) was already a dependency.

---

### Edge-to-edge system bar inset fix
**Files:** `activity_survey_list.xml`, `activity_offline_maps.xml`, `activity_main.xml`, `activity_map_area_download.xml`, `activity_survey_map.xml`, `activity_geometry_picker.xml`, `SurveyListActivity.java`, `OfflineMapsActivity.java`, `MapAreaDownloadActivity.java`, `SurveyMapActivity.java`, `GeometryPickerActivity.java`

With `targetSdk = 36`, Android enforces edge-to-edge on Android 15+ devices — app content draws behind both the status bar (top) and navigation bar (bottom). The app's v23 theme had already set both bars to transparent but no views applied window insets, causing:
- Toolbars and back buttons hidden under the status bar
- FABs and bottom controls obscured by or overlapping the navigation bar

**Fixes by activity:**

| Activity | Status bar fix | Nav bar fix |
|---|---|---|
| `SurveyListActivity` | `fitsSystemWindows="true"` on CoordinatorLayout + AppBarLayout | `ViewCompat.setOnApplyWindowInsetsListener` adds nav bar height to both FAB bottom margins |
| `OfflineMapsActivity` | Same | Same for `fabAddArea` |
| `MainActivity` | `fitsSystemWindows="true"` on AppBarLayout (DrawerLayout already had it) | — |
| `MapAreaDownloadActivity` | Back button + title wrapped in `LinearLayout#topBar`; top inset applied as padding | Bottom inset added to `bottomBar` CardView and `fabMyLocation` margins |
| `SurveyMapActivity` | Same `topBar` pattern | Bottom inset added to `tvHint` bottom margin |
| `GeometryPickerActivity` | Control panel moved to **top** of screen (was bottom); status bar inset applied to inner `LinearLayout#topPanelInner` | — (no bottom controls) |

Moving the geometry picker controls to the top was also a deliberate UX improvement — the full map canvas is now unobstructed at the bottom, and controls don't conflict with gesture navigation.

---

## 2026-05-15 (earlier)

### Fix: Blank map tiles on Android 10+ (commit `6a8cc67`)
**Files:** `App.java` (or `MapPickerActivity.java` / `EsriTileSourceFactory.java`)

OSMDroid's default tile cache path (`/sdcard/osmdroid/`) is blocked by scoped storage on Android 10+. Redirected cache to the app-private directory (`getExternalFilesDir(null)` or `getCacheDir()`), which requires no additional permissions and persists across sessions.

---

## 2026-05-14

### Standalone offline map management (commit `52d7110`)
**New files:** `OfflineMapsActivity.java`, `MapAreaDownloadActivity.java`, `OfflineMapArea.java` (Room entity), `activity_offline_maps.xml`, `activity_map_area_download.xml`

- `SurveyListActivity` gets a second FAB (map icon) → `OfflineMapsActivity`
- `OfflineMapsActivity`: lists saved download areas (name, date, zoom range), shows total tile cache size, allows deletion of area records
- `MapAreaDownloadActivity`: full-screen satellite map; surveyor pans to area of interest, sets max zoom via seekbar, names and downloads; progress shown via notification; area record saved to Room DB
- Room DB migrated v2 → v3 with explicit migration for `offline_map_areas` table
- Per-area tile deletion not available in osmdroid 6.1.20; tiles evicted by LRU or Android Settings > Clear Cache

---

### Replace fastexcel with built-in ZIP/XML parser (commit `dd32fe2`)
**Files:** `QuestionnaireParser.java`, `build.gradle.kts`, string resources

Removed `fastexcel` dependency. XLSX/XLSM files are ZIP archives containing `xl/worksheets/sheet*.xml`; the parser now uses `java.util.zip.ZipInputStream` + Android's `org.w3c.dom` XML DOM — no third-party library needed. Also updated app display strings and the navigation drawer header.

---

### Satellite map picker for LOCATION questions (commit `165c343`)
**New files:** `MapPickerActivity.java`, `EsriTileSourceFactory.java`, `activity_map_picker.xml`

**Files modified:** `QuestionFragment.java`

Replaced the GPS-coordinate-only input for LOCATION questions with a full-screen OSMDroid satellite map (ESRI World Imagery tile source — no API key, works offline when tiles are cached). The surveyor taps the map to place a pin or uses "Use My Location" to snap to GPS. Result flows back through `applyLocation()` and triggers `reverseGeocode()` for the address fields.

---

## 2026-05-13

### XLSX/XLSM questionnaire support, DURATION type, LOCATION answer type (commit `16b2dd7`)
**Files:** `QuestionnaireParser.java`, `QuestionFragment.java`, answer type handling

- Parser accepts both `.csv` and `.xlsx`/`.xlsm` questionnaire files
- New answer type `DURATION`: numeric value + unit spinner (seconds/minutes/hours/days/weeks/months/years); optional min/max bounds and restricted unit list via AnswerOptions
- New answer type `LOCATION`: GPS coordinates + address fields (PROVINCE, DISTRICT, TEHSIL, VILLAGE, LOCALITY); AnswerOptions controls which fields appear; stored as `lat|lng|village|tehsil|district|province|locality`

---

### Initial commit (commit `6a896f4`) — 2026-05-13
**Stack:** Android (minSdk 24, targetSdk 36, Java 17), Room DB, OSMDroid 6.1.20, Material Components

Core survey flow: `SurveyListActivity` → `MainActivity` (ViewPager2 + QuestionFragment per category) → answers persisted to Room. Questionnaire loaded from XLSX asset. Answer types: YES_NO, MULTIPLE_CHOICE_SINGLE, MULTIPLE_CHOICE_MULTI, INTEGER/DECIMAL/PERCENTAGE/NUMBER, TEXT, DATE_YEAR, DATE_YEAR_MONTH, DATE_FULLDATE, TIME, DATE_TIME, COMPOUND. Sub-question conditional display (ParentID + TriggerValue). Photo attachment support.
