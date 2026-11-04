# EquipX: Task Plan (v2: 3D explorer)

> Supersedes the timeline and hotspot approach in SPEC.md. Deadline: **10 days**. Scope: **10 devices**.
> Items marked **[CONFIRM]** need a decision from the project owner.
> Changes in v2: hotspots now live on **3D models** (not on the camera feed). Camera scan only identifies the device.

## 1. Decisions locked

| Topic | Decision |
|---|---|
| App name | **EquipX** (package `com.equipx.app`) |
| Android target | `targetSdk 33` (Android 13), `compileSdk 34`, `minSdk 26` |
| UI toolkit | Kotlin + Jetpack Compose + Material 3, themed to look like Notion (see section 6) |
| Auth | **Mock** for now (hardcoded student and teacher accounts). Supabase auth later |
| Data | **Mock/local** for now (JSON seed files). Supabase swap later behind repository interfaces |
| Learning content | Written by Claude for now, reviewed by a teacher before the final demo |
| Scan | Camera identifies the **device** only (device-level label chip). Model training is **deferred**; a `FakeDetector` stands in |
| Part exploration | **3D explorer** with tappable hotspots on a GLB model of the device |
| 3D models | **Generated from code** per device (stylized, named part nodes). Optional realistic upgrade for 3 main devices |
| 3D viewer | `<model-viewer>` in a WebView, bundled offline. Wrapped behind an interface so SceneView can replace it later |
| Dev tool | Claude Code for everything |

## 2. Product flow

1. **Scan**: camera shows a live preview; the detector recognizes the device and shows a chip such as "Digital Multimeter".
2. **Tap the chip**: opens the **3D explorer** for that device (rotate, zoom, tap hotspots).
3. **Tap a hotspot / part**: bottom sheet with function, usage and safety tips. A parts list drawer lets students jump to any part.
4. From the explorer: **Procedures**, **Troubleshoot**, **Quiz**.
5. Optional (stretch, online only): **Place in AR** launches Google Scene Viewer with a web-hosted copy of the model.

Why this design: the camera only needs to answer "which device?", which is far more reliable than aligning labels to individual parts, and the hotspots never drift because they are fixed to the model.

**Later upgrades (not in the 10-day scope):** pin the device label with ARCore Augmented Images; true part-level detection on 3 to 4 main devices.

## 3. Public datasets (device recognition, electronics lab)

Used only to train the **device-level** detector (10 classes). Verify license, image count and label quality on each page before download.

| Dataset | Classes | License shown | Link | Notes |
|---|---|---|---|---|
| **TARDATA** | 27: digital/analog multimeter, clamp meter, function generator, oscilloscopes, benchtop power supplies, soldering stations, LCR meter, insulation tester, protoboard, hand tools | CC BY 4.0 | https://universe.roboflow.com/new-workspace-febsz/tardata | Best breadth; check image count |
| **objectdetection** (Univ. Malaysia Pahang) | 5: DC power supply, digital multimeter, digital trainer, function generator, oscilloscope | Public Domain | https://universe.roboflow.com/universiti-malaysia-pahang-qcvas/objectdetection-ngxjp | Clean class set |
| **Instrument-Lab Detection** (Univ. de los Andes) | 4: power supply, generator, multimeter, oscilloscope (Spanish labels) | CC BY 4.0 | https://universe.roboflow.com/universidad-de-los-andes-lzihh/instrument-lab-detection | Remap labels to English |
| **Oscilloscope Detection** (RoboticsLearning) | 1: oscilloscope | CC BY 4.0 | https://universe.roboflow.com/roboticslearning/oscilloscope-detection | Extra oscilloscope images |
| **Equipment Data** (senior-design-yolov7) | 5: AFG, digital oscilloscope, ELVIS, multimeter, power supply | not checked | https://universe.roboflow.com/senior-design-yolov7/equipment-data | Check license first |
| **testBW** | 10: meters, protoboard, cables, hand tools | CC BY 4.0 | https://universe.roboflow.com/new-workspace-febsz/testbw | Smaller subset |
| **Resistor Detection** (Circuits Project) | resistor, wire, breadboard | CC BY 4.0 | https://universe.roboflow.com/circuits-project/resistor-detection-5azes | For the breadboard device |

**Plan:** merge the relevant classes in Roboflow, standardize the 10 names, add your own photos for weak classes, export YOLO format. Include your own lab photos in the test split.

## 4. Proposed 10 devices [CONFIRM against your actual lab]

1. Digital Multimeter
2. Analog Multimeter
3. Digital Clamp Meter
4. Digital Oscilloscope
5. Function Generator
6. DC Power Supply (benchtop)
7. Soldering Station
8. LCR Meter
9. Insulation Tester
10. Breadboard / Digital Trainer

## 5. 3D models

### Approach: generate stylized models from a per-device layout spec
Each device gets a `layout` entry in its `/content/<device>.json`: body dimensions, then parts with a type, position, size and label. Typical part types: `screen`, `knob`, `button`, `switch`, `port`, `led`, `display`, `probe`, `slot`, `handle`.

- A generator script in `/tools/gen-models` (Node; three.js + GLTFExporter or gltf-transform, whichever builds cleanly) reads the spec and writes one **GLB** per device.
- **Every part is a named glTF node** whose name equals the part ID. Each part has its own material so it can be highlighted.
- The generator also writes a `hotspots.json` (part ID → position and outward normal, computed from each node's bounds), so hotspots need no manual placement.
- Targets: under 20k triangles and under 500 KB per model; validate with the glTF Validator.
- Layouts are based on **your photos of each real device's front panel**, so the model matches your lab.

### Optional upgrade: realistic models for the 3 main devices
Multimeter, oscilloscope and power supply can be swapped for downloaded models if one is good quality:
- Sources seen: Sketchfab (downloadable models are CC Attribution), Free3D, CGTrader ($2 to $20 range), Fab.
- Check each license. Some are marked editorial-only or "no AI"; do not use renders of "no AI" models to train the detector.
- Downloaded models usually need conversion to GLB, shrinking, and **manual hotspot placement** (parts are often fused into one mesh).
- Any CC BY model needs a line on the in-app **Credits** screen.

### Viewer
- `<model-viewer>` web component bundled inside the app assets and loaded through `WebViewAssetLoader` (works offline). GLB files are in `assets/models/`.
- Hotspots built from `hotspots.json` as small numbered circular markers (white fill, 1dp border). Selected part is highlighted by changing its material color.
- JS bridge (WebView ↔ Kotlin): `onHotspotTap(partId)`, `selectPart(partId)`, `focusPart(partId)`, `resetCamera()`.
- Kotlin side wraps this behind a `ModelExplorer` composable/interface, so SceneView (Compose + Filament) can replace it if the WebView misbehaves on the demo phone.
- "Place in AR" (stretch): Scene Viewer needs a **web-hosted** model URL, so it is only enabled when online and models are hosted (Phase 6).

## 6. Design direction: "Notion-like"

- **Palette**: white background `#FFFFFF`, surface `#F7F7F5`, border `#E9E9E7`, text `#37352F`, secondary text `#787774`, accent blue `#2383E2`, success `#0F7B6C`, danger `#E03E3E`.
- **Type**: Inter (Android: bundled font; admin: `next/font`). Mostly 14 to 16sp body, 24 to 30sp titles.
- **Shape**: 6dp radius, 1dp borders instead of shadows, no gradients, generous whitespace.
- **Components**: simple lists with icon + title + subtle meta, pill tags, callout blocks for safety tips (light yellow `#FBF3DB` with a left bar), checklists for procedure steps.
- **Icons**: Material Symbols (outlined). No custom image assets in this phase.
- **Camera screen**: translucent white label chip with 1dp border, thin 2dp outline on the detected box.
- **3D explorer**: flat `#F7F7F5` background, soft even lighting, muted neutral model colors with one accent color for the selected part, numbered hotspot markers.
- **Admin panel**: left sidebar, page-style tables, inline editing, shadcn/ui restyled to the same tokens.

## 7. Architecture notes (so mock to real is a swap, not a rewrite)

- **Single source of truth for content**: `/content/*.json` (one file per device, including its `layout`), validated by a JSON schema. A sync script copies content and generated GLBs to `android/app/src/main/assets/` and to the admin mock store, and later generates Supabase seed SQL.
- **Android layering**: `ui/` (Compose screens, ViewModels) → `domain/` (scoring, points, badges, mission rules; pure Kotlin with unit tests) → `data/` (repository interfaces + `Mock*` implementations now, `Supabase*` later).
- **ML layering**: `Detector` interface (`detect(frame) -> List<Detection>`), device-level. `FakeDetector` returns a scripted device box now. `TfliteDetector` is added when the model exists.
- **Auth**: `AuthRepository` interface; `MockAuthRepository` with a student and a teacher account.
- **Admin**: `lib/data` interface with a `MockStore` (localStorage + seed JSON) now, Supabase client later.
- **DI**: manual dependency wiring with ViewModel factories (no Hilt) to keep builds simple.
- **Points and badges**: computed in `domain/`, mirrored later by Postgres functions.

### Data model additions for 3D
- `equipment`: `model_asset` (GLB path/URL), `default_camera_orbit`, `model_credit` (nullable, for CC BY models), `layout` (JSON, generator input).
- `parts`: `node_name` (glTF node, default = part ID), optional `hotspot_position` `[x,y,z]` and `hotspot_normal` `[x,y,z]` overrides for downloaded models.
- Replaces the earlier 2D `hotspot_x/y/w/h` idea.

## 8. Task list

### Phase 0: Scaffold (Day 1), the immediate goal
- [x] **T0.1** Monorepo: `/android`, `/admin`, `/content`, `/tools`, `/supabase`, `/ml`, `/docs`, `SPEC.md`, `TASKS.md`, `CLAUDE.md`
- [x] **T0.2** Android project: Gradle (Kotlin DSL), version catalog, Compose, Navigation, CameraX, kotlinx.serialization, DataStore, WebKit. `targetSdk 33`, `compileSdk 34`, `minSdk 26`
- [x] **T0.3** EquipX theme (tokens from section 6), Inter font, reusable components (list row, pill, callout, buttons, top bar)
- [x] **T0.4** Navigation shell: Splash → mock login → bottom nav (**Scan**, **Learn**, **Missions**, **Profile**)
- [x] **T0.5** Domain + data layer: models, repository interfaces, `Mock*` implementations reading `/content` JSON, local progress in DataStore
- [x] **T0.6** Camera screen: permission flow, `Detector` interface, `FakeDetector` (device chip), tap chip → 3D explorer
- [x] **T0.7** `/tools/gen-models` skeleton + generated **Digital Multimeter** GLB + `hotspots.json` from its layout spec
- [x] **T0.8** 3D explorer: bundled `<model-viewer>` WebView, hotspots from JSON, JS bridge, part bottom sheet (placeholder content)
- [x] **T0.9** Admin app: Next.js (App Router, TypeScript) + Tailwind + shadcn/ui, Notion-like theme, sidebar layout, mock login, empty pages (Dashboard, Equipment, Procedures, Faults, Quizzes, Missions, Results)
- [x] **T0.10** `/content` schema + 1 fully filled sample device (Digital Multimeter) + sync script (content + GLBs → Android assets and admin mock store)
- [x] **T0.11** `/supabase`: migration SQL for the full schema + RLS policies (written, not applied)
- [x] **T0.12** `CLAUDE.md` (stack, commands, conventions) and `docs/RUNNING.md` (stub)
- [x] **T0.13** Verify: `./gradlew assembleDebug` succeeds, `npm run build` succeeds, content validates, **3D viewer renders on your physical phone**

**Done when:** the debug APK installs on your phone, mock login works, the fake scan shows a device chip, tapping it opens the multimeter 3D model, tapping a hotspot opens a part sheet, and the admin panel runs locally with mock login and a sidebar.

### Phase 1: Learning flow + 3D (Days 2 to 3)
- [x] **T1.1** Learn tab: equipment list → equipment detail (overview, 3D explorer entry, parts, procedures, quizzes)
- [x] **T1.2** 3D explorer polish: parts list drawer, part highlight/focus, reset view, loading and error states
- [x] **T1.3** Part detail (function, usage, safety callout), works offline
- [x] **T1.4** Procedure runner: step-by-step checklist; each step focuses and highlights the related part in 3D
- [x] **T1.5** Quiz engine: MCQ, optional timer, explanations, result screen, attempts saved locally
- [x] **T1.6** Unit tests for scoring and accuracy calculations
- [x] **T1.7** Credits screen (attribution for any CC BY models)

### Phase 2: Gamification + simulation (Days 4 to 5)
- [x] **T2.1** Troubleshooting simulation: symptom → inspect (tap parts in 3D) → choose fault → feedback and score
- [x] **T2.2** Points ledger and rules (scan +10, first part view +2, quiz correct +5, fault fixed +20, mission complete +50)
- [x] **T2.3** Badges (First Scan, Part Spotter, Safety First, Quiz Ace, Fault Finder, Mission Complete) with unlock toasts
- [x] **T2.4** Mission Mode: ordered task chain (scan → identify part in 3D → complete procedure → pass quiz), progress UI
- [x] **T2.5** Profile tab: points, badges, history, accuracy
- [x] **T2.6** Unit tests for points, badges and mission progression

### Phase 3: Admin panel (Days 5 to 6, parallel track)
- [x] **T3.1** Equipment CRUD with parts table
- [x] **T3.2** 3D preview in the admin panel; **hotspot editor** (click on the model to place or move a hotspot, pick a node)
- [x] **T3.3** Procedure, fault scenario, quiz and mission editors
- [x] **T3.4** Student results: scores, accuracy, time, per-device breakdown, charts, CSV export (mock data)
- [x] **T3.5** Mock data seeded with realistic fake students and attempts

### Phase 4: Content + models (Days 2 to 6, parallel track, Claude writes)
Per device: overview, 8 to 10 parts (function, usage, safety, **layout entry**), 1 to 2 procedures (6 to 10 steps), 3 fault scenarios, 10 quiz questions, 2 to 3 safety rules, generated GLB.
- [x] **T4.1** Devices 1 to 3 (multimeters, clamp meter)
- [x] **T4.2** Devices 4 to 6 (oscilloscope, function generator, power supply)
- [x] **T4.3** Devices 7 to 10 (soldering station, LCR meter, insulation tester, breadboard/trainer)
- [ ] **T4.4** Layout accuracy check against your front-panel photos
- [ ] **T4.5** Teacher review for technical and safety accuracy (**needs a human reviewer**)
- [ ] **T4.6** (Optional) Swap in realistic downloaded models for multimeter, oscilloscope and power supply; manual hotspots; credits

### Phase 5: Device recognition model (Days 5 to 8, mostly project owner + Colab; deferred until the scaffold is done)
- [ ] **T5.1** Download and merge public datasets in Roboflow; standardize the 10 class names
- [ ] **T5.2** Take your own photos in the lab (about 50 to 100 per device, varied angles and lighting)
- [ ] **T5.3** Train YOLO nano/small on Colab (10 device classes), evaluate per class, export TFLite
- [ ] **T5.4** Implement `TfliteDetector`, tune thresholds and smoothing, test at 15+ FPS on your phone
- [ ] **T5.5** Keep a manual "choose device" fallback in the Scan screen for the demo

### Phase 6: Backend swap (Days 8 to 9, stretch)
- [ ] **T6.1** Create Supabase project, apply migrations and seed
- [ ] **T6.2** Supabase auth (replace mock), role-based access via RLS
- [ ] **T6.3** Replace mock repositories (Android + admin), add sync and offline cache (Room)
- [ ] **T6.4** Server-side points/badge functions
- [ ] **T6.5** Host GLBs (Supabase Storage); enable "Place in AR" via Scene Viewer (online only)

### Phase 7: Delivery (Days 9 to 10)
- [ ] **T7.1** Polish: empty/error/loading states, accessibility basics, app icon and splash
- [ ] **T7.2** Release APK (signed), install check on a physical phone
- [ ] **T7.3** Deploy admin panel (Vercel) or ship local run instructions
- [ ] **T7.4** `docs/RUNNING.md` run guide + `docs/DEMO.md` demo script + backup demo video
- [ ] **T7.5** Final regression test pass

## 9. 10-day schedule

| Day | Focus |
|---|---|
| 1 | Phase 0 (full scaffold, multimeter 3D end to end on your phone) |
| 2 | Learn flow + 3D explorer polish; content and models for devices 1 to 3; project owner starts photos (front panels + device recognition) |
| 3 | Quiz engine; content and models for devices 4 to 6 |
| 4 | Troubleshooting + points/badges; content and models for devices 7 to 10 |
| 5 | Mission Mode + admin CRUD; project owner finishes dataset merge |
| 6 | Admin results + hotspot editor; training run 1 |
| 7 | Detector integration (`TfliteDetector`); training run 2 if needed; layout accuracy check |
| 8 | Tuning on real devices; start Supabase swap (stretch) |
| 9 | Polish, bug fixing, teacher review fixes |
| 10 | Release APK, run guide, demo rehearsal, buffer |

Rules: the app must stay demo-able at the end of every day (mock data and `FakeDetector` are the safety net). If the schedule slips, drop Phase 6 first.

## 10. Risks

| Risk | Mitigation |
|---|---|
| `<model-viewer>` WebView misbehaves on your phone | Test on day 1 (T0.13); `ModelExplorer` interface allows swapping to SceneView |
| Stylized models look too basic | Keep clean flat styling; optional realistic upgrade for 3 main devices (T4.6) |
| Generated layout differs from the real lab device | Build layouts from your front-panel photos; check in T4.4 |
| Small parts overlap hotspots | Numbered markers, parts list drawer, camera focus on selected part |
| Public datasets are small or inconsistent | Merge several, add own photos, include lab photos in the test split |
| 10 devices of content is a lot | Content/model generation is a parallel track with a strict schema |
| Technical or safety errors in AI-written content | Mandatory teacher review (T4.5) before the demo |
| Detector underperforms near the deadline | `FakeDetector` plus a manual "choose device" fallback |
| Third-party model licenses | Prefer generated models; credit CC BY authors; avoid "no AI" models for dataset use |

## 11. Needed from the project owner (when ready)

- Confirm the 10 devices (section 4)
- **Front-panel photos of each device** (straight-on, good light) and brand/model names; these define the 3D layouts
- A few more lab photos per device for the recognition model
- Roboflow account (dataset merge) and a Colab session for training
- Supabase and Vercel account details (Phase 6/7)
- A teacher or senior to review content (T4.5)
- Phone model + Android version for testing
