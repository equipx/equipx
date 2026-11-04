# EquipX

EquipX is an Android app that teaches electronics lab equipment. Point the camera at a device and the app identifies it, then opens an interactive 3D model with tappable part hotspots, step-by-step procedures, troubleshooting simulations, quizzes, missions and badges. A Next.js admin panel lets teachers edit content and review student results.

Covers 10 devices: digital and analog multimeter, clamp meter, oscilloscope, function generator, DC power supply, soldering station, LCR meter, insulation tester, breadboard/digital trainer. Learning content works fully offline on the phone.

Repository: https://github.com/equipx/equipx

## Screenshots

_Placeholder: add images under `docs/screenshots/` and link them here._

| Home | Scan | 3D explorer | Quiz |
|---|---|---|---|
| _todo_ | _todo_ | _todo_ | _todo_ |

| Troubleshoot | Missions | Admin: equipment | Admin: results |
|---|---|---|---|
| _todo_ | _todo_ | _todo_ | _todo_ |

## Repository layout

| Path | What it is |
|---|---|
| `content/` | Single source of truth: `index.json`, one JSON per device (parts, 3D layout, procedures, faults, quizzes), `missions.json`, `schema/` |
| `tools/` | `content/` validator, `gen-models/` (layout to GLB + hotspots), `sync/` (copies content and models to Android and admin) |
| `android/` | Kotlin + Jetpack Compose app, package `com.equipx.app` |
| `admin/` | Next.js 16 admin panel (Tailwind 4, shadcn) |
| `supabase/` | Postgres migrations: schema, RLS, functions, views |
| `ml/` | YOLO device detector training (Colab notebook) and export notes |
| `docs/` | Contract, run guide, demo script, release steps, teacher review checklist |
| `TASKS.md` | Plan and task status |

## Quick start

```bash
npm run setup     # once: installs tool dependencies
npm run sync      # validate content, generate 3D models, copy to android + admin

# Android (debug build; phone with USB debugging)
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug

# Admin panel
cd admin && npm install && npm run dev    # http://localhost:3000
```

Demo accounts: student `student@equipx.app` / `student123`, teacher `teacher@equipx.app` / `teacher123` (admin panel accepts teachers only).

## Docs

- [docs/RUNNING.md](docs/RUNNING.md): full setup and troubleshooting
- [docs/DEMO.md](docs/DEMO.md): 6 to 8 minute demo script with backup plans
- [docs/RELEASE.md](docs/RELEASE.md): signed APK and admin deploy
- [docs/TEACHER_REVIEW.md](docs/TEACHER_REVIEW.md): content accuracy checklist for the reviewer
- [docs/CONTRACT.md](docs/CONTRACT.md): data shapes shared by all components
- [ml/README.md](ml/README.md), [supabase/README.md](supabase/README.md)
