# EquipX demo script (6 to 8 minutes)

Audience: faculty. Goal: show that a student can scan a lab device, explore it in 3D, practice and be assessed, and that a teacher can manage content and see results. Content is bundled, so the phone demo works in airplane mode.

## Pre-demo checklist
- [ ] Phone charged above 80% and charger in bag; screen timeout set to 10 minutes; brightness high
- [ ] Latest debug/release APK installed; app opened once; camera permission granted
- [ ] Airplane mode on (Wi-Fi off is fine too); Do Not Disturb on
- [ ] Progress reset: Settings > Apps > EquipX > Storage > Clear data, then log in fresh as the student (points 0, no badges)
- [ ] Physical device nearby for scanning (a multimeter works best); good lighting
- [ ] Phone screen mirrored (scrcpy/cast) and tested; backup: hold the phone to the camera
- [ ] Admin running: `cd admin && npm run dev`, logged out, localhost:3000 open in the browser; site data cleared so it starts from seed content
- [ ] Backup screen recording of the full flow on laptop and on the phone
- [ ] Printed or slide copy of the login credentials (student@equipx.app / student123, teacher@equipx.app / teacher123)

## Phone: student flow

| Time | Step | Say / do | Backup plan |
|---|---|---|---|
| 0:00 | Intro | One sentence: scan a device, learn it in 3D, practice, get assessed. | Skip to demo. |
| 0:30 | Login | Log in as the student. | Credentials slide; if login fails, clear app data. |
| 0:50 | Home | Show progress, suggested next steps. | Skip, go to Scan. |
| 1:10 | Scan | Point at the multimeter; the chip appears; tap it. Mention: detector is a stand-in until the trained model is bundled (or "the trained model" if bundled). | Tap "Choose device manually" and pick Digital Multimeter. Camera denied: same fallback. |
| 1:50 | 3D explorer | Rotate, zoom, tap a hotspot, show the part sheet (function, usage, safety tip). | Blank 3D view: back out and reopen; else switch to the backup video for this step. |
| 2:40 | Parts drawer | Open the parts list, jump to another part, reset view. | Tap hotspots directly. |
| 3:10 | Procedure | Run "measure voltage"; each step highlights the relevant part in 3D. Mark a few steps done. | Show the checklist only. |
| 4:00 | Quiz | Answer 2 to 3 questions, show explanations and the result screen. | Use a different device quiz if one fails. |
| 4:50 | Troubleshoot | Pick a symptom, inspect parts in 3D, choose the fault, show feedback and score. | Narrate over the screen only. |
| 5:40 | Missions and badges | Show the mission chain and a badge unlock toast. | Open Profile instead. |
| 6:10 | Profile | Points, badges, history, accuracy. | Skip. |

## Admin: teacher flow (about 1.5 minutes)

| Step | Say / do | Backup plan |
|---|---|---|
| Login | Log in as `teacher@equipx.app` (a student account is rejected). | Clear site data and reload. |
| Equipment + hotspot editor | Open Digital Multimeter, show parts, the 3D preview and moving a hotspot; edit a part's text and save. | Show the equipment list and a part table only. |
| Quiz editor | Open a quiz, edit a question, mark the correct answer. | Show the quiz list. |
| Results + CSV | Open Results, filter, click export CSV, open the file. | Show a pre-downloaded CSV. |

Close with: content comes from one source of truth, so a teacher edit reaches every student device through sync; the Supabase backend (RLS, server-side scoring) is ready behind the same interfaces.

## If everything fails
Play the backup video, then walk through the admin panel live (it only needs a laptop).

## After the demo
Questions to expect: accuracy of content (see `docs/TEACHER_REVIEW.md`), detector accuracy (`ml/README.md`), offline behaviour (bundled content), adding a device (add `content/<id>.json`, run `npm run sync`).
