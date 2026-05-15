# Changelog — WSS Condition Assessment Balochistan

All notable changes to the Android app, newest first.

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
