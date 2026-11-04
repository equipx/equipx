-- EquipX: Row Level Security + table privileges.
--
-- Model
--   * Content tables: any authenticated user can read published content, teachers can read
--     everything and are the only ones who can write.
--   * Student activity: users insert/select their own rows; teachers can select all.
--     No UPDATE/DELETE policies => activity is immutable for students.
--   * points_ledger / user_badges: users read their own; there are NO insert policies and the
--     write privilege is revoked, so rows can only come from SECURITY DEFINER functions.
--   * anon (not logged in) gets nothing.

-- Safety net: RLS on every table in public, present and (by the check at the bottom) future-proofed.
do $$
declare t record;
begin
  for t in select tablename from pg_tables where schemaname = 'public' loop
    execute format('alter table public.%I enable row level security', t.tablename);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- Privileges (RLS still applies on top of these)
-- ---------------------------------------------------------------------------
revoke all on all tables in schema public from anon;
revoke insert, update, delete, truncate on public.points_ledger, public.user_badges from authenticated;
revoke insert, delete, truncate on public.profiles from authenticated;  -- rows come from the auth trigger
revoke update on public.scans, public.part_views, public.quiz_attempts, public.quiz_answers,
                 public.fault_attempts, public.procedure_completions, public.mission_progress
  from authenticated;
revoke delete on public.scans, public.part_views, public.quiz_attempts, public.quiz_answers,
                 public.fault_attempts, public.procedure_completions, public.mission_progress
  from authenticated;

-- ---------------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------------
create policy profiles_select on public.profiles
  for select to authenticated
  using (id = (select auth.uid()) or (select public.is_teacher()));

create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));   -- role changes are blocked by profiles_guard_role trigger

create policy profiles_update_teacher on public.profiles
  for update to authenticated
  using ((select public.is_teacher()))
  with check ((select public.is_teacher()));

-- ---------------------------------------------------------------------------
-- Content: readable when the owning equipment is published, teachers see + write all
-- ---------------------------------------------------------------------------

-- equipment itself
create policy equipment_select on public.equipment
  for select to authenticated
  using (published or (select public.is_teacher()));

-- tables that carry equipment_id directly
create policy parts_select on public.parts
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.equipment e where e.id = parts.equipment_id and e.published));

create policy safety_rules_select on public.safety_rules
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.equipment e where e.id = safety_rules.equipment_id and e.published));

create policy procedures_select on public.procedures
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.equipment e where e.id = procedures.equipment_id and e.published));

create policy procedure_steps_select on public.procedure_steps
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.equipment e where e.id = procedure_steps.equipment_id and e.published));

create policy fault_scenarios_select on public.fault_scenarios
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.equipment e where e.id = fault_scenarios.equipment_id and e.published));

create policy fault_related_parts_select on public.fault_related_parts
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.equipment e where e.id = fault_related_parts.equipment_id and e.published));

create policy quizzes_select on public.quizzes
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.equipment e where e.id = quizzes.equipment_id and e.published));

-- tables reached through a parent
create policy fault_options_select on public.fault_options
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.fault_scenarios s
                    join public.equipment e on e.id = s.equipment_id
                    where s.id = fault_options.scenario_id and e.published));

create policy quiz_questions_select on public.quiz_questions
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.quizzes q
                    join public.equipment e on e.id = q.equipment_id
                    where q.id = quiz_questions.quiz_id and e.published));

-- ---------------------------------------------------------------------------
-- Answer key protection (column-level privileges)
--   Students must not read quiz_questions.correct_index / fault_options.is_correct, otherwise
--   quiz/fault points and badges are free. Table-wide SELECT is revoked from `authenticated`
--   and re-granted on every column except the answer column (explanation stays readable).
--   Correctness is computed by SECURITY DEFINER triggers (functions.sql). Teachers are also
--   `authenticated`, so they read the answer columns through the SECURITY DEFINER functions
--   teacher_quiz_questions(quiz_id) / teacher_fault_options(scenario_id) below.
--   Consequences: `select *` on these two tables fails for everyone via the API (list columns);
--   teacher INSERT/UPDATE still work but should not use RETURNING of the answer column.
--   Any column added later to these tables needs an explicit GRANT SELECT.
-- ---------------------------------------------------------------------------
revoke select on public.quiz_questions, public.fault_options from authenticated;
grant select (id, quiz_id, slug, sort_order, prompt, options, explanation, created_at, updated_at)
  on public.quiz_questions to authenticated;
grant select (id, scenario_id, slug, text, sort_order, created_at, updated_at)
  on public.fault_options to authenticated;

create function public.teacher_quiz_questions(p_quiz_id uuid default null)
returns setof public.quiz_questions
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not public.is_teacher() then
    raise exception 'teachers only' using errcode = '42501';
  end if;
  return query select q.* from public.quiz_questions q
    where p_quiz_id is null or q.quiz_id = p_quiz_id order by q.quiz_id, q.sort_order;
end;
$$;

create function public.teacher_fault_options(p_scenario_id uuid default null)
returns setof public.fault_options
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not public.is_teacher() then
    raise exception 'teachers only' using errcode = '42501';
  end if;
  return query select o.* from public.fault_options o
    where p_scenario_id is null or o.scenario_id = p_scenario_id order by o.scenario_id, o.sort_order;
end;
$$;

revoke all on function public.teacher_quiz_questions(uuid), public.teacher_fault_options(uuid) from public, anon;
grant execute on function public.teacher_quiz_questions(uuid), public.teacher_fault_options(uuid) to authenticated;

-- missions carry their own published flag
create policy missions_select on public.missions
  for select to authenticated
  using (published or (select public.is_teacher()));

create policy mission_tasks_select on public.mission_tasks
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.missions m where m.id = mission_tasks.mission_id and m.published));

-- badge catalog is public to signed-in users
create policy badges_select on public.badges
  for select to authenticated
  using (true);

-- Teacher write access (insert/update/delete) on every content table
do $$
declare t text;
begin
  foreach t in array array[
    'equipment', 'parts', 'safety_rules', 'procedures', 'procedure_steps',
    'fault_scenarios', 'fault_options', 'fault_related_parts',
    'quizzes', 'quiz_questions', 'missions', 'mission_tasks', 'badges'
  ] loop
    execute format('create policy %I on public.%I for insert to authenticated with check ((select public.is_teacher()))', t || '_teacher_insert', t);
    execute format('create policy %I on public.%I for update to authenticated using ((select public.is_teacher())) with check ((select public.is_teacher()))', t || '_teacher_update', t);
    execute format('create policy %I on public.%I for delete to authenticated using ((select public.is_teacher()))', t || '_teacher_delete', t);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- Student activity: insert/select own, teachers select all
-- ---------------------------------------------------------------------------
do $$
declare t text;
begin
  foreach t in array array[
    'scans', 'part_views', 'quiz_attempts', 'fault_attempts',
    'procedure_completions', 'mission_progress', 'points_ledger', 'user_badges'
  ] loop
    execute format('create policy %I on public.%I for select to authenticated using (user_id = (select auth.uid()) or (select public.is_teacher()))', t || '_select', t);
  end loop;

  -- insert-own on the activity tables (points_ledger / user_badges deliberately excluded)
  foreach t in array array[
    'scans', 'part_views', 'quiz_attempts', 'fault_attempts',
    'procedure_completions', 'mission_progress'
  ] loop
    execute format('create policy %I on public.%I for insert to authenticated with check (user_id = (select auth.uid()))', t || '_insert_own', t);
  end loop;
end;
$$;

-- quiz_answers has no user_id: ownership comes from the attempt
create policy quiz_answers_select on public.quiz_answers
  for select to authenticated
  using ((select public.is_teacher())
         or exists (select 1 from public.quiz_attempts a
                    where a.id = quiz_answers.attempt_id and a.user_id = (select auth.uid())));

create policy quiz_answers_insert_own on public.quiz_answers
  for insert to authenticated
  with check (exists (select 1 from public.quiz_attempts a
                      where a.id = quiz_answers.attempt_id and a.user_id = (select auth.uid())));
