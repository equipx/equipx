# Running EquipX

## Prerequisites
- macOS/Linux/Windows with Node 20+ and npm
- Android Studio (use its bundled JBR JDK) with Android SDK platform 34
- An Android 8.0+ phone (minSdk 26) with a USB cable, for real-device testing
- Optional: a Supabase project (admin Supabase mode), a Google account for Colab (ML training)

## 1. Content and 3D models
`content/` is the source of truth. Never hand-edit `android/app/src/main/assets/{content,models}/`, `admin/src/data/seed/` or `admin/public/models/`.

```bash
npm run setup     # once
npm run sync      # validate -> generate GLB + hotspots -> copy to android and admin
```
Other scripts: `npm run content:validate`, `npm run models:gen`. Pipe output with `< /dev/null` if piping.

## 2. Android app
Android Studio: open `android/`, set Gradle JDK to the bundled JBR (Settings > Build > Gradle), pick a device, Run.

Command line:
```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # domain unit tests
./gradlew installDebug           # install on the connected device
```
adb lives at `~/Library/Android/sdk/platform-tools/adb` (`adb devices` should list the phone).

Login: `student@equipx.app` / `student123`.

### Installing on a phone
1. Settings > About phone: tap Build number 7 times to enable Developer options.
2. Developer options: enable USB debugging, connect the cable, accept the RSA prompt.
3. `adb devices` shows the phone as `device`, then `./gradlew installDebug`. Or copy `app-debug.apk` to the phone and open it.

Xiaomi/MIUI/HyperOS extras: in Developer options also enable **Install via USB** and **USB debugging (Security settings)** (both need a Mi account/SIM and a data connection to toggle on). Without them `installDebug` fails with `INSTALL_FAILED_USER_RESTRICTED`. Also allow the camera permission when the app asks, and if the app is killed in the background, set Battery saver to No restrictions for EquipX.

### Scan / detector
The app currently uses `FakeDetector` (scripted device chip) via `AppContainer.createDetector()`. The Scan screen always offers "Choose device manually" as a fallback (manual opens do not award scan points). A real model replaces the fake one; see section 6.

## 3. Admin panel
```bash
cd admin
npm install
npm run dev        # http://localhost:3000
npm run build && npm run lint
```
Login `teacher@equipx.app` / `teacher123`. Pages: Dashboard, Equipment (with hotspot editor), Procedures, Faults, Quizzes, Missions, Results (with CSV export).

**Mock mode (default):** no env vars. Data comes from the seed JSON and is edited in the browser's localStorage; use the browser's site-data clear to reset.

**Supabase mode:** create `admin/.env.local` (gitignored) with
```
NEXT_PUBLIC_SUPABASE_URL=https://<project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<anon public key>
```
and restart `npm run dev`. Use the anon key only; never put the service-role key in the admin app. If either variable is missing the admin falls back to mock mode.

## 4. Supabase
Schema, RLS, functions and views are in `supabase/migrations/` and have been applied to the hosted project. Details: `supabase/README.md`.

- New migrations: `supabase link --project-ref <ref>` then `supabase db push`.
- Local stack: `supabase start` then `supabase db reset` (needs Docker).
- **Re-seeding content:** re-run the seed generated from `content/*.json` (see `supabase/README.md`), or edit through the admin panel in Supabase mode. Keep secrets in `supabase/.env` (gitignored); never commit it.
- **Promote a teacher:** new sign-ups are always `student`. In the Supabase SQL editor:
  ```sql
  update public.profiles set role = 'teacher'
  where id = (select id from auth.users where email = 'teacher@example.com');
  ```

## 5. Tests and checks
`npm run content:validate`, `./gradlew testDebugUnitTest`, `npm --prefix admin run build`.

## 6. ML detector
Training and export run in Google Colab, not locally. Short version: open `ml/train_colab.ipynb` in Colab, use a T4 GPU, add `ROBOFLOW_API_KEY` to Colab Secrets, Run all, download `equipx_export.zip`, and put `equipx_detector.tflite`, `labels.txt`, `model_info.json` in `ml/export/`. Then the model is bundled into the Android assets and a `TfliteDetector` replaces `FakeDetector` in `AppContainer`. See `ml/README.md` for steps, metrics and tensor format.

## Troubleshooting
- **3D view blank on the phone:** the explorer WebView must have MATCH_PARENT layout params inside a bounded Compose height; wrap-content collapses the page to 0px. Check any layout change around `ExplorerPane`.
- **3D view stuck loading / console errors:** `adb logcat -s EquipXWeb` shows the WebView console. Re-run `npm run sync` if models are missing from `assets/models/`.
- **Inspect the WebView:** open the app on the explorer, then `chrome://inspect` on desktop Chrome (USB debugging on). Or `adb forward tcp:9333 localabstract:webview_devtools_remote_<pid>`.
- **Explorer page in a desktop browser:** serve `android/app/src/main/assets` over HTTP, open `web/explorer.html`, call `loadModel('../models/<id>.glb','../models/<id>.hotspots.json')`.
- **Gradle JDK errors:** `JAVA_HOME` must point at the Android Studio JBR (path above).
- **`INSTALL_FAILED_USER_RESTRICTED`:** MIUI settings in section 2.
- **Admin shows old data:** clear site data for localhost:3000 (mock store is in localStorage).
- **Content validation fails:** fix the JSON in `content/` and re-run; do not patch generated copies.
