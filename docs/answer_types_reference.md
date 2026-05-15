# Answer Types Reference

The questionnaire CSV/XLSX has 9 columns (A–I):

```
ID | Category | SubCategory | Question | AnswerType | AnswerOptions | Explanation | ParentID | TriggerValue
```

Options within a cell use `|` as a separator (pipe character).

---

## 1. `YES_NO`

Shorthand — auto-converted to `MULTIPLE_CHOICE_SINGLE` with `YES|NO`.

| Field | Value |
|---|---|
| AnswerType | `YES_NO` |
| AnswerOptions | *(leave blank — auto-filled)* |

**DB stored as:** `YES` or `NO`

---

## 2. `MULTIPLE_CHOICE_SINGLE`

Radio buttons — pick exactly one.

| Field | Value |
|---|---|
| AnswerType | `MULTIPLE_CHOICE_SINGLE` |
| AnswerOptions | `Good|Fair|Poor|Very Poor` |

**DB stored as:** the selected option text, e.g. `Fair`

---

## 3. `MULTIPLE_CHOICE_MULTI`

Checkboxes — pick one or more.

| Field | Value |
|---|---|
| AnswerType | `MULTIPLE_CHOICE_MULTI` |
| AnswerOptions | `Leakage|Corrosion|Blockage|Structural damage` |

**DB stored as:** pipe-separated selections, e.g. `Leakage|Blockage`

---

## 4. `INTEGER` / `DECIMAL` / `PERCENTAGE`

Shorthands for `NUMBER` — auto-converted.

| Type | AnswerOptions format | Example |
|---|---|---|
| `INTEGER` | `min\|max` or `min\|max\|unit1;unit2` | `0\|500` |
| `DECIMAL` | `min\|max` or `min\|max\|unit1;unit2` | `0\|100\|m;km` |
| `PERCENTAGE` | *(leave blank — bounds 0–100 auto-set)* | |

**DB stored as:** `42` or `3.5` or `7.2|km` (value|unit if units given)

---

## 5. `NUMBER`

Same as the shorthands above but the sub-format is specified explicitly in AnswerOptions.

| AnswerOptions format | Example |
|---|---|
| `INTEGER\|min\|max` | `INTEGER\|0\|200` |
| `DECIMAL\|min\|max` | `DECIMAL\|0.0\|9.9` |
| `DECIMAL\|min\|max\|unit1;unit2` | `DECIMAL\|0\|100\|m;km` |
| `PERCENTAGE` | `PERCENTAGE` |

**DB stored as:** `42` or `7.2|km`

---

## 6. `TEXT`

Free-form multi-line text. No options needed.

| Field | Value |
|---|---|
| AnswerType | `TEXT` |
| AnswerOptions | *(leave blank)* |

**DB stored as:** the typed text string

---

## 7. `DATE_YEAR`

Spinner of years.

| Field | Value |
|---|---|
| AnswerType | `DATE_YEAR` |
| AnswerOptions | `1950\|2030` (minYear\|maxYear) |

**DB stored as:** `2019`

---

## 8. `DATE_YEAR_MONTH`

Year + month spinners.

| Field | Value |
|---|---|
| AnswerType | `DATE_YEAR_MONTH` |
| AnswerOptions | `2000\|2030` |

**DB stored as:** `2023-06`

---

## 9. `DATE_FULLDATE`

Date picker dialog. No options.

| Field | Value |
|---|---|
| AnswerType | `DATE_FULLDATE` |
| AnswerOptions | *(leave blank)* |

**DB stored as:** `2024-03-15`

---

## 10. `TIME`

24-hour time picker. No options.

| Field | Value |
|---|---|
| AnswerType | `TIME` |
| AnswerOptions | *(leave blank)* |

**DB stored as:** `14:30`

---

## 11. `DATE_TIME`

Combined date + time picker.

| Field | Value |
|---|---|
| AnswerType | `DATE_TIME` |
| AnswerOptions | *(leave blank)* |

**DB stored as:** `2024-03-15 14:30`

---

## 12. `DURATION`

Numeric value + unit spinner.

Options format: `unit1|unit2;min;max` — semicolons separate the three sections; all parts optional.

| AnswerOptions | Behaviour |
|---|---|
| *(blank)* | All 7 default units (seconds/minutes/hours/days/weeks/months/years), no bounds |
| `hours\|days\|weeks` | Only these 3 units, no bounds |
| `minutes\|hours;1;240` | minutes or hours, bounded 1–240 |

**DB stored as:** `5|minutes`

---

## 13. `LOCATION`

GPS coordinates + address fields. AnswerOptions controls which fields appear.

Available field names: `COORDINATES`, `PROVINCE`, `DISTRICT`, `TEHSIL`, `VILLAGE`, `LOCALITY`

| AnswerOptions | Fields shown |
|---|---|
| *(blank)* | All: coordinates + province/district/tehsil/village/locality |
| `COORDINATES` | Lat/lng + map picker only |
| `COORDINATES\|DISTRICT\|TEHSIL` | Coordinates + district + tehsil |

**DB stored as:** 7 pipe-separated slots in fixed order:

```
lat|lng|village|tehsil|district|province|locality
```

Example: `33.9891|71.5234|Hayatabad|Peshawar|Peshawar|KPK|`

---

## 14. `GEOMETRY`

A full map annotation tool — allows any mix of points, lines (with optional arrows), polygons, and text labels in a single answer. No AnswerOptions needed.

> **Note:** The old `LINE` and `POLYGON` answer types are automatically converted to `GEOMETRY` by the parser. Old DB values are read transparently.

| Field | Value |
|---|---|
| AnswerType | `GEOMETRY` |
| AnswerOptions | *(leave blank — always all types)* |

### Drawing modes in the map picker

| Mode | Description | Min points to commit |
|---|---|---|
| **Point** | Single location marker | 1 tap |
| **Line** | Polyline; enable "Arrow at end" toggle for an arrowhead | 2 taps |
| **Polygon** | Closed filled shape | 3 taps |
| **Label** | Text annotation at a map position — dialog appears on tap | Dialog confirm |

### Editing existing shapes

Press **Edit** to enter select mode, tap a shape, then:
- Drag white handles to reposition individual points
- Tap a midpoint handle (smaller dot) to insert a new point between two existing ones
- **Undo Pt** removes the last point (down to the minimum for that type)
- **Delete** removes the entire shape
- **Done Edit** accepts changes

Shapes from other questions in the same survey are displayed as faded read-only overlays for context.

### DB storage format

```json
[
  {"t":"POINT","c":[[lat,lng]]},
  {"t":"LINE","c":[[lat,lng],[lat,lng],[lat,lng]],"arrow":true},
  {"t":"LINE","c":[[lat,lng],[lat,lng]]},
  {"t":"POLYGON","c":[[lat,lng],[lat,lng],[lat,lng]]},
  {"t":"LABEL","c":[[lat,lng]],"text":"Pump station"}
]
```

Fields: `t` = sub-type, `c` = coordinates, `arrow` = boolean (LINE only), `text` = string (LABEL only)

---

## 15. `COMPOUND`

Multiple typed sub-fields grouped inside one question card.

Options format (pipe-separated field definitions, colon-separated tokens within each):

```
label:type[:subtype[:min[:max[:unit1;unit2]]]]
```

| AnswerOptions example | Result |
|---|---|
| `Pipe Diameter:NUMBER:INTEGER:0:1000\|Material:TEXT\|Age:NUMBER:INTEGER:0:100` | Three sub-fields |

Sub-field types supported inside COMPOUND: `TEXT`, `NUMBER` (with optional `INTEGER`/`DECIMAL`/`PERCENTAGE` subtype, bounds, and units)

**DB stored as:** `label=value||label2=value2||...`

Example: `Pipe Diameter=150||Material=Steel||Age=12`

---

## Sub-questions (conditional follow-ups)

Any row with a `ParentID` and `TriggerValue` becomes a sub-question, shown only when the parent answer matches the trigger.

```
ID  | Cat | SubCat | Question           | AnswerType             | AnswerOptions | Explanation | ParentID | TriggerValue
101 |  …  |   …    | Pipe condition?    | MULTIPLE_CHOICE_SINGLE | Good|Bad      |             |          |
102 |  …  |   …    | Describe the issue | TEXT                   |               |             | 101      | Bad
```

Sub-questions can use any answer type. Multiple sub-questions can share the same `TriggerValue`.
