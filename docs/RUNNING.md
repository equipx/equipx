# Running EquipX (stub, expanded in Phase 7)

## Prerequisites
- Android Studio (bundled JBR JDK), Android SDK platform 34
- Node 20+ and npm

## Content and 3D models
```bash
npm run setup
npm run sync
```

## Android app
Open `android/` in Android Studio (Gradle JDK: bundled jbr) and press Run, or:
```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug
```
Log in with `student@equipx.app` / `student123`.

## Admin panel
```bash
cd admin && npm install && npm run dev
```
Open http://localhost:3000 and log in with `teacher@equipx.app` / `teacher123`.

## Supabase (not applied yet)
See `supabase/README.md`.
