# EquipX Admin

Next.js 16 (App Router) + Tailwind 4 + shadcn (base-nova) admin panel for teachers: equipment content, procedures, faults, quizzes, missions, student results.

```
npm install
npm run dev      # http://localhost:3000
npm run build
npm run lint
```

## Data source: mock or Supabase

The app picks its backend from two env vars (see `.env.example`; copy to the gitignored `.env.local`):

| `NEXT_PUBLIC_SUPABASE_URL` + `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Backend |
|---|---|
| unset (default) | Offline mocks: `MockStore` (localStorage over `src/data/seed`), `MockResultsStore` (generated results), `MockAuth` (`teacher@equipx.app` / `teacher123`) |
| both set | `SupabaseStore`, `SupabaseResultsStore`, `SupabaseAuth` |

Use the project's **publishable / anon** key only. Never put the service-role / secret key or the database URL in the admin app. Restart `npm run dev` after changing env vars (they are inlined at build time).

With Supabase:
- **Sign-in** is email + password through Supabase Auth. Only users whose `profiles.role = 'teacher'` get in; anyone else is signed out with a message. Promote the first teacher as described in `../supabase/README.md`.
- **Reads/writes** go through RLS as the signed-in teacher. Quiz answer keys and fault correct-options are read with the `teacher_quiz_questions()` / `teacher_fault_options()` functions.
- **Editors save to the database**: a `Device` maps to `equipment`, `parts` (hotspot overrides to `parts.hotspot_position` / `hotspot_normal`), `safety_rules`, `procedures` + `procedure_steps`, `fault_scenarios` + `fault_options` + `fault_related_parts`, `quizzes` + `quiz_questions`. Missions map to `missions` + `mission_tasks` (task ids are `t1..tn` by order). Devices and missions created in the panel are published immediately.
- **Results** come from `student_results`, the activity tables and the server-side `points_ledger` / `user_badges`. The schema has no "college" field, so that column is blank.
- "Reset to seed" is mock-only. To restore content in the database, re-run the seed (`../supabase/README.md`).
- The 3D preview still loads GLBs from `public/models/` (synced by `npm run sync`); hosting models in Supabase Storage is T6.5.

Content lives in `/content`; never hand-edit `src/data/seed/` or `public/models/` (run `npm run sync` from the repo root).
