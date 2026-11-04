# EquipX shared data contract (Phase 0)

All parallel tracks (Android, content/tools, admin, supabase) must agree on these shapes.
Source of truth: `/content/*.json`. The sync script copies them out; nobody hand-edits the copies.

## IDs
- snake_case, e.g. device `digital_multimeter`, part `rotary_selector`.
- glTF node name for a part == part ID.

## Files produced by `tools/sync` (do not hand-edit)
| Destination | Contents |
|---|---|
| `android/app/src/main/assets/content/index.json` | device index |
| `android/app/src/main/assets/content/<deviceId>.json` | full device content |
| `android/app/src/main/assets/models/<deviceId>.glb` | generated model |
| `android/app/src/main/assets/models/<deviceId>.hotspots.json` | generated hotspots |
| `admin/src/data/seed/index.json` + `admin/src/data/seed/<deviceId>.json` | same JSON for the admin MockStore |
| `admin/public/models/<deviceId>.glb` + `.hotspots.json` | for admin 3D preview later |

Ownership: the Android app owns `assets/web/` (model-viewer bundle, explorer.html, fonts). It must NOT write into `assets/content/` or `assets/models/`.

## `content/index.json`
```json
{ "devices": [ { "id": "digital_multimeter", "name": "Digital Multimeter", "category": "Measurement", "summary": "one line" } ] }
```

## `content/<deviceId>.json`
```json
{
  "id": "digital_multimeter",
  "name": "Digital Multimeter",
  "category": "Measurement",
  "summary": "One-line description.",
  "overview": "2-4 sentence paragraph.",
  "model": {
    "asset": "models/digital_multimeter.glb",
    "hotspots": "models/digital_multimeter.hotspots.json",
    "cameraOrbit": "0deg 75deg 0.45m",
    "credit": null
  },
  "layout": {
    "units": "m",
    "body": { "width": 0.09, "height": 0.18, "depth": 0.045, "cornerRadius": 0.008, "color": "#D9D7D2" },
    "parts": [
      { "id": "lcd_display", "type": "screen", "face": "front", "position": [0, 0.05], "size": [0.07, 0.04, 0.003], "color": "#9FB3A5" }
    ]
  },
  "parts": [
    {
      "id": "lcd_display",
      "number": 1,
      "name": "LCD Display",
      "function": "What it does.",
      "usage": "How to use it.",
      "safety": ["Tip 1"]
    }
  ],
  "safetyRules": ["Rule 1"],
  "procedures": [
    { "id": "measure_dc_voltage", "title": "Measure DC voltage", "steps": [ { "text": "Step text", "partId": "rotary_selector" } ] }
  ],
  "faults": [
    {
      "id": "no_display",
      "symptom": "Display is blank",
      "options": [ { "id": "dead_battery", "text": "Dead battery" } ],
      "correctOptionId": "dead_battery",
      "explanation": "Why.",
      "relatedPartIds": ["lcd_display"]
    }
  ],
  "quizzes": [
    {
      "id": "dmm_basics",
      "title": "Multimeter basics",
      "timeLimitSec": null,
      "questions": [ { "id": "q1", "prompt": "Question?", "options": ["A", "B", "C", "D"], "correctIndex": 0, "explanation": "Why." } ]
    }
  ]
}
```
Layout notes:
- Units are metres. Body is centred at the origin; front face is +Z at `z = depth/2`; +Y is up.
- `face`: `front` | `top` | `left` | `right` | `back`. `position` is the 2D centre `[u, v]` on that face, seen from outside: front `[x, y]`, back `[-x, y]`, right `[-z, y]`, left `[z, y]`, top `[x, -z]`.
- `size`: `[w, h, d]`; `d` is how far the part protrudes outward from the face.
- Part `type`: `screen`, `knob`, `button`, `switch`, `port`, `led`, `display`, `probe`, `slot`, `handle`.
- `parts[].id` and `layout.parts[].id` must match one-to-one.

## `<deviceId>.hotspots.json` (generated)
```json
{
  "deviceId": "digital_multimeter",
  "hotspots": [ { "partId": "lcd_display", "number": 1, "position": [0, 0.05, 0.0255], "normal": [0, 0, 1] } ]
}
```
`position` is the centre of the part's outward face plus a 2 mm offset along the normal (model space, metres), `normal` is the outward unit vector. `number` matches `parts[].number`.

## JS bridge (Android WebView explorer)
- JS → Kotlin: `EquipX.onHotspotTap(partId)`, `EquipX.onModelLoaded()`, `EquipX.onModelError(message)` (via `addJavascriptInterface(obj, "EquipX")`).
- Kotlin → JS: `selectPart(partId)`, `focusPart(partId)`, `resetCamera()`, `loadModel(glbUrl, hotspotsUrl, cameraOrbit)`.
- Assets served by `WebViewAssetLoader` at `https://appassets.androidplatform.net/assets/...`.

## Mock accounts
| Role | Email | Password |
|---|---|---|
| Student | `student@equipx.app` | `student123` |
| Teacher | `teacher@equipx.app` | `teacher123` |
(Admin panel accepts the teacher account only.)

## Design tokens (Notion-like)
bg `#FFFFFF`, surface `#F7F7F5`, border `#E9E9E7`, text `#37352F`, secondary `#787774`, accent `#2383E2`, success `#0F7B6C`, danger `#E03E3E`, callout `#FBF3DB`. Radius 6dp, 1dp borders, no shadows/gradients, Inter font.
