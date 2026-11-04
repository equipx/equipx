# EquipX Supabase backend

The migrations and `seed.sql` are applied to the owner's hosted project (24 tables, RLS on, content seeded). The SQL was first verified against a throwaway local PostgreSQL 17 with stubbed `auth` objects, then smoke-tested on the hosted DB (see "Verification").

## Files

| File | Purpose |
|---|---|
| `config.toml` | Minimal Supabase CLI config for local dev (ports, Postgres 15, email signup without confirmation). |
| `migrations/20261104000000_init.sql` | Enums, all tables, constraints, FK indexes, `updated_at` triggers, and the badge catalog seed (6 badges). |
| `migrations/20261104000100_functions.sql` | `is_teacher()`, the `auth.users` -> `profiles` trigger, role-change guard, server-side `is_correct` triggers, `award_points(reason, ref)` and `check_badges(user)`. |
| `migrations/20261104000200_rls.sql` | Enables RLS on every table, revokes `anon`, defines all policies. |
| `migrations/20261104000300_views.sql` | `student_results` view (`security_invoker = true`) for the admin Results page. |

Migrations run in filename order; keep the order (functions need tables, policies need `is_teacher()`).

## Applying migrations

Hosted project: put the Postgres connection string in the gitignored `supabase/.env` as `DATABASE_URL=...` (and `SUPABASE_URL=...`). Never commit it.
```
set -a; . supabase/.env; set +a
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 --single-transaction -f supabase/migrations/<file>.sql   # new migrations only, in filename order
```
(Already applied: all four `2026110400*` migrations; they are recorded in `supabase_migrations.schema_migrations`.) With the Supabase CLI instead: `supabase link --project-ref <ref>` then `supabase db push`. Local (Docker + CLI): `supabase start && supabase db reset`.

## Seeding / re-seeding content

`supabase/seed.sql` is generated from `/content/*.json` + `content/missions.json` by `tools/supabase/seed.mjs`. Do not hand-edit it.
```
npm run db:seed-sql                                   # regenerate supabase/seed.sql from /content
set -a; . supabase/.env; set +a
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 --single-transaction -f supabase/seed.sql
```
(Or paste the file into the Supabase SQL editor, which runs it as one transaction.) It takes a minute or two over the network because it is ~700 statements.

Behaviour: idempotent upserts keyed on natural keys (`equipment.id`, `(equipment_id, part id)`, `(equipment_id, slug)`, `(scenario_id, slug)`, `(quiz_id, slug)`, mission `slug`, task `(mission_id, sort_order)`), so row uuids and any student activity or mission progress that references them survive a re-seed. Children that no longer exist in `/content` (parts, procedures, faults, options, quizzes, questions, extra tasks) are deleted; procedure steps, safety rules and fault-related parts are replaced. Equipment and missions from the seed are set `published = true`; `model_asset` is `models/<id>.glb`. Hotspot overrides (`parts.hotspot_position/normal`, set in the admin panel) are not touched. Devices or missions created only in the admin panel are never deleted by the seed, but a re-seed overwrites edits made in the admin panel to seeded content (content files are the source of truth).

## Making the first teacher

New sign-ups are always `student` (the role in signup metadata is ignored on purpose). The owner signs up once (Supabase dashboard: Authentication > Users > Add user, or via the app/admin sign-up), then runs in the SQL editor (or with `psql "$DATABASE_URL"`):
```sql
update public.profiles set role = 'teacher' where id = (select id from auth.users where email = 'teacher@example.com');
```
Only a service-role / SQL-editor connection (no JWT) or an existing teacher can change roles. The admin panel then accepts that account; any other account is signed out with a message.

## Schema notes

- **Ids:** `equipment.id` is the snake_case contract id. `parts` uses a **composite primary key `(equipment_id, id)`** because part ids (and glTF node names) are only unique per device. Everything that points at a part uses `(equipment_id, part_id)`, so a procedure step, fault, mission task or part view can never reference a part of another device. Other content tables (`procedures`, `fault_scenarios`, `quizzes`, `quiz_questions`, `fault_options`, `missions`) have a uuid primary key plus a `slug` (the contract id, unique within the parent).
- `parts.node_name` defaults to the part id via trigger. `hotspot_position` / `hotspot_normal` are optional `float8[3]` overrides.
- `fault_options`: a partial unique index allows at most one correct option per scenario. `fault_related_parts` stores the contract's `relatedPartIds`.
- `mission_tasks`: a CHECK enforces the target refs for each `task_type` (scan: equipment; identify_part: equipment + part; complete_procedure: procedure; pass_quiz: quiz + `min_score_pct`, default 70).
- `mission_progress`: one row per (student, task); a mission is complete when every task has a row.
- `quiz_attempts` has no stored score: accuracy is derived from `quiz_answers`. `is_correct` on `quiz_answers` and `fault_attempts` is **set by triggers** from the content tables, so clients cannot forge correctness.

## Security model (RLS)

- RLS is enabled on every `public` table; `anon` has no privileges.
- Content: authenticated users read rows of **published** equipment (missions use their own `published`); teachers read everything and are the only writers. **Answer key is hidden from students:** `SELECT` on `quiz_questions` and `fault_options` is revoked from `authenticated` and re-granted per column for everything except `quiz_questions.correct_index` and `fault_options.is_correct` (`explanation` stays readable). So `select *` on those two tables fails; clients must list columns. Teachers (also `authenticated`) read the answer columns through the SECURITY DEFINER functions `teacher_quiz_questions(quiz_id default null)` and `teacher_fault_options(scenario_id default null)`, which raise for non-teachers and are not executable by `anon`. Teacher INSERT/UPDATE on the tables still works (avoid `RETURNING` of the answer columns). New columns added to these tables need an explicit `GRANT SELECT (col)`.
- **Scoring:** `is_correct` on `quiz_answers` / `fault_attempts` is set by SECURITY DEFINER triggers (`set search_path = ''`) that can read the answer key. The Android app scores offline from its bundled content (so it still has the answers locally); the **server copy is the authority for points and badges**, and a mismatch between the app's offline score and the server's `is_correct` is resolved in favor of the server.
- Student activity (`scans`, `part_views`, `quiz_attempts`, `quiz_answers`, `fault_attempts`, `procedure_completions`, `mission_progress`): students insert and select their own rows; teachers select all; no update/delete.
- `points_ledger`, `user_badges`: read own (teachers read all); **no insert policy and write privileges revoked**. Rows come only from the SECURITY DEFINER functions (`set search_path = ''`).
- `profiles`: read own (teachers all), update own (role changes blocked unless teacher/service role).

### Points rules (`award_points(reason, ref)`, T2.2)
| reason | points | `ref` | verified, granted once per |
|---|---|---|---|
| `scan` | +10 | `scans.id` | student + equipment + UTC day of the scan (dedupe key stored in the ledger is `<equipment_id>:<YYYY-MM-DD>`, not the scan id) |
| `first_part_view` | +2 | `'<equipment_id>:<part_id>'` | student + part |
| `quiz_correct` | +5 | `quiz_answers.id` (must be correct) | student + question |
| `fault_fixed` | +20 | `fault_attempts.id` (must be correct) | student + scenario |
| `mission_complete` | +50 | `missions.id` (published, all tasks done) | student + mission |

Returns the points granted (0 if already granted). Call it after inserting the activity row. `check_badges(user)` returns the newly unlocked badge codes; its thresholds (e.g. 10 parts for Part Spotter, 3 faults for Fault Finder, Safety First = one completed procedure) are placeholders to tune.

Repeat scans of the same device on the same UTC day still insert `scans` rows but award 0.

Known limits: `scans` and mission progress are client-reported (a determined student could insert fake rows); fine for a classroom app, tighten later with server-side checks if needed.

### student_results
One row per student per equipment: `quiz_attempts`, `questions_answered`, `correct_answers`, `accuracy_pct`, `avg_attempt_sec`, `points` (mission points count toward a device only when the mission has an `equipment_id`). Uses `security_invoker`, so teachers see all students and a student sees only their own rows.

## Verification (what was actually run)

Local PostgreSQL 17 (Homebrew, no Docker) with a test-only prelude (roles `anon`/`authenticated`/`service_role`, a stub `auth.users`, `auth.uid()` reading `request.jwt.claim.sub`), which is not part of the migrations. All four migrations applied cleanly with `ON_ERROR_STOP`. Second round (answer-key fix, scan limit, mission published check): the same prelude (plus default grants to `authenticated`, as Supabase does) and all four migrations applied cleanly; as a student `select correct_index` and `select *` on `quiz_questions`, `select is_correct` on `fault_options` and `teacher_quiz_questions()` fail, other columns work, `quiz_answers`/`fault_attempts` still get `is_correct` computed, a teacher reads answers via the functions and can still update/insert content, `anon` cannot call them, scan points are 10 then 0 on the same day/device and 10 again on another day, `mission_complete` is rejected while unpublished and grants 50 once published. Earlier round: a smoke test under `SET ROLE authenticated/anon` confirmed: teacher writes content, students only see published content and cannot write it, `anon` is denied, role self-promotion is blocked, direct `points_ledger` inserts are denied, `award_points` grants once and rejects unverified refs, server-side `is_correct`, `check_badges`, and `student_results` per role. Not tested: real Supabase (Auth/PostgREST/Storage).

### Hosted DB verification (2026-11-05)
Seed applied to the hosted project twice (idempotent), counts: 10 equipment (all published), 96 parts, 60 safety rules, 19 procedures / 162 steps, 30 fault scenarios / 120 options / 69 related parts, 10 quizzes / 100 questions, 12 missions / 52 tasks, 6 badges. RLS smoke test (rolled-back transaction, `set local role authenticated` + fake JWT claims): student reads published content, `correct_index` / `is_correct` / `select *` on the answer tables are denied, `teacher_*()` functions raise "teachers only", content writes and `points_ledger` inserts are denied, `anon` has no access; a (rolled-back) teacher reads everything, reads answers via `teacher_*()`, and can insert/update content. Note: `INSERT ... ON CONFLICT DO UPDATE` on `quiz_questions` / `fault_options` is denied even for teachers (Postgres needs SELECT on the answer columns), and so is an update that reads them (`set x = x`); use plain INSERT and `UPDATE ... SET col = <value>` (the admin `SupabaseStore` does).
