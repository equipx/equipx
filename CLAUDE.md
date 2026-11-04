# EquipX

Android app (Kotlin + Compose) that teaches lab equipment: camera scan identifies a device → 3D explorer with tappable part hotspots → procedures, troubleshooting, quizzes. Plus a Next.js admin panel. Plan and scope: `TASKS.md`. Data shapes: `docs/CONTRACT.md` (binding for every track).

## Layout
- `content/` single source of truth: `index.json`, `<deviceId>.json` (incl. 3D `layout`), `schema/`
- `tools/` `content/` (validator), `gen-models/` (layout → GLB + hotspots.json, @gltf-transform), `sync/` (copies content + models out)
- `android/` app `com.equipx.app`: `ui/` → `domain/` → `data/` (interfaces + `Mock*`/`Asset*` impls), `ml/` (`Detector`, `FakeDetector`), `explorer/` (`ModelExplorer`, WebView + `<model-viewer>` in `assets/web/`)
- `admin/` Next.js 16 App Router, Tailwind 4, shadcn (base-nova on `@base-ui/react`: use `render` prop, not `asChild`), `lib/data` MockStore, `lib/auth` MockAuth
- `supabase/migrations` schema + RLS + functions (written, not applied)
- `ml/` detector training (Phase 5), `docs/`

## Commands
- Content: `npm run setup` (once), `npm run content:validate`, `npm run models:gen`, `npm run sync` (validate → generate → copy). Pipe with `< /dev/null` if piping output.
- Android (`cd android`): `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` then `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew installDebug`. adb: `~/Library/Android/sdk/platform-tools/adb`.
- Admin (`cd admin`): `npm run dev`, `npm run build`, `npm run lint`.
- Explorer page outside Android: serve `android/app/src/main/assets` over HTTP, open `web/explorer.html`, call `loadModel('../models/<id>.glb','../models/<id>.hotspots.json')`.

- Draft content: put a folder with its own `index.json` + device files under `content/drafts/<name>/`, then `EQUIPX_CONTENT_DIR=<abs path> npm run content:validate` and `EQUIPX_CONTENT_DIR=<abs> EQUIPX_OUT_DIR=<abs>/out npm run models:gen`.
- Debug the phone WebView: logcat tag `EquipXWeb` (console), or `adb forward tcp:9333 localabstract:webview_devtools_remote_<pid>` and use the DevTools protocol / chrome://inspect.

## Conventions
- The explorer WebView must have MATCH_PARENT layout params and a bounded Compose height; wrap-content collapses the page to 0px (blank screen on device).
- Never hand-edit `android/app/src/main/assets/{content,models}/`, `admin/src/data/seed/`, `admin/public/models/`: edit `/content` and run `npm run sync`.
- IDs snake_case; glTF node name and material name == part ID; body material `body`.
- Android: `compileSdk 34`, `targetSdk 33`, `minSdk 26`; AndroidX versions pinned to ones that support compileSdk 34 (see `gradle/libs.versions.toml`). Manual DI via `AppContainer`, no Hilt.
- Domain logic (points, badges, scoring, missions) goes in `domain/` as pure Kotlin with unit tests.
- Design: Notion-like tokens in CONTRACT.md; 1dp borders, 6dp radius, no shadows/gradients, Inter.
- Mock accounts: `student@equipx.app / student123`, `teacher@equipx.app / teacher123` (admin: teacher only).
