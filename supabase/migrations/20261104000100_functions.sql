-- EquipX: helper functions, auth trigger, integrity triggers, points + badges functions.
-- All functions set `search_path = ''` and use fully qualified names.

-- ---------------------------------------------------------------------------
-- is_teacher(): used by RLS policies. SECURITY DEFINER so it can read profiles
-- without recursing into profiles' own RLS.
-- ---------------------------------------------------------------------------
create function public.is_teacher()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid()) and p.role = 'teacher'
  );
$$;

revoke all on function public.is_teacher() from public, anon;
grant execute on function public.is_teacher() to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- New auth user -> profiles row. Role is ALWAYS 'student': signup metadata is
-- client-controlled, so it is never trusted for the role. Promote teachers with
-- an UPDATE from the SQL editor / service role (see README).
-- ---------------------------------------------------------------------------
create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, role, display_name)
  values (
    new.id,
    'student',
    coalesce(nullif(new.raw_user_meta_data ->> 'display_name', ''), split_part(coalesce(new.email, ''), '@', 1))
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

revoke all on function public.handle_new_user() from public, anon, authenticated;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- Only a teacher (or a privileged connection with no JWT, e.g. the SQL editor /
-- service role) may change a profile's role.
-- ---------------------------------------------------------------------------
create function public.profiles_guard_role()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.role is distinct from old.role
     and (select auth.uid()) is not null
     and not public.is_teacher() then
    raise exception 'only teachers can change roles' using errcode = '42501';
  end if;
  return new;
end;
$$;

create trigger profiles_guard_role
  before update of role on public.profiles
  for each row execute function public.profiles_guard_role();

-- ---------------------------------------------------------------------------
-- Server-side correctness: the client reports WHAT was chosen, the database
-- decides whether it was correct (so points/badges cannot be forged by sending
-- is_correct = true).
-- ---------------------------------------------------------------------------
create function public.quiz_answers_set_correct()
returns trigger
language plpgsql
security definer  -- students have no SELECT on quiz_questions.correct_index (see rls.sql)
set search_path = ''
as $$
declare
  v_correct integer;
  v_quiz_ok boolean;
begin
  select q.correct_index,
         q.quiz_id = a.quiz_id
    into v_correct, v_quiz_ok
  from public.quiz_questions q
  join public.quiz_attempts a on a.id = new.attempt_id
  where q.id = new.question_id;

  if v_quiz_ok is distinct from true then
    raise exception 'question does not belong to the quiz of this attempt' using errcode = '23514';
  end if;

  new.is_correct := (new.selected_index is not null and new.selected_index = v_correct);
  return new;
end;
$$;

create trigger quiz_answers_set_correct
  before insert or update of selected_index, question_id on public.quiz_answers
  for each row execute function public.quiz_answers_set_correct();

create function public.fault_attempts_set_correct()
returns trigger
language plpgsql
security definer  -- students have no SELECT on fault_options.is_correct (see rls.sql)
set search_path = ''
as $$
begin
  select o.is_correct into new.is_correct
  from public.fault_options o
  where o.id = new.option_id and o.scenario_id = new.scenario_id;
  new.is_correct := coalesce(new.is_correct, false);
  return new;
end;
$$;

create trigger fault_attempts_set_correct
  before insert or update of option_id, scenario_id on public.fault_attempts
  for each row execute function public.fault_attempts_set_correct();

-- ---------------------------------------------------------------------------
-- award_points(reason, ref) -> points granted (0 if already granted before)
--
-- Called by the signed-in student after recording the activity row. The
-- function verifies the activity exists, belongs to the caller and (where it
-- matters) was correct, then inserts into points_ledger. Amounts come from
-- TASKS.md T2.2 and live only here.
--
--   reason            amount  ref (text)                         granted once per
--   scan              +10     scans.id                           user + equipment + UTC day
--   first_part_view   +2      '<equipment_id>:<part_id>'         user + part
--   quiz_correct      +5      quiz_answers.id (a correct answer) user + question
--   fault_fixed       +20     fault_attempts.id (a correct one)  user + scenario
--   mission_complete  +50     missions.id (published, all done)  user + mission
-- ---------------------------------------------------------------------------
create function public.award_points(p_reason public.points_reason, p_ref text)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user      uuid := (select auth.uid());
  v_amount    integer;
  v_key       text := p_ref;       -- ledger ref actually stored (dedupe key)
  v_equipment text;
  v_id        uuid;
  v_inserted  integer;
begin
  if v_user is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  case p_reason
    when 'scan' then
      v_amount := 10;
      v_id := p_ref::uuid;
      select s.equipment_id,
             s.equipment_id || ':' || ((s.created_at at time zone 'utc')::date)::text
        into v_equipment, v_key
      from public.scans s where s.id = v_id and s.user_id = v_user;
      if not found then
        raise exception 'scan not found' using errcode = 'P0002';
      end if;
      -- dedupe key = equipment + UTC day of the scan: +10 once per user, equipment and day

    when 'first_part_view' then
      v_amount := 2;
      v_equipment := split_part(p_ref, ':', 1);
      if not exists (
        select 1 from public.part_views pv
        where pv.user_id = v_user
          and pv.equipment_id = v_equipment
          and pv.part_id = substr(p_ref, length(v_equipment) + 2)
      ) then
        raise exception 'part view not found' using errcode = 'P0002';
      end if;

    when 'quiz_correct' then
      v_amount := 5;
      v_id := p_ref::uuid;
      select qa.question_id::text, qz.equipment_id into v_key, v_equipment
      from public.quiz_answers qa
      join public.quiz_attempts att on att.id = qa.attempt_id
      join public.quizzes qz on qz.id = att.quiz_id
      where qa.id = v_id and att.user_id = v_user and qa.is_correct;
      if not found then
        raise exception 'correct quiz answer not found' using errcode = 'P0002';
      end if;

    when 'fault_fixed' then
      v_amount := 20;
      v_id := p_ref::uuid;
      select fa.scenario_id::text, fs.equipment_id into v_key, v_equipment
      from public.fault_attempts fa
      join public.fault_scenarios fs on fs.id = fa.scenario_id
      where fa.id = v_id and fa.user_id = v_user and fa.is_correct;
      if not found then
        raise exception 'correct fault attempt not found' using errcode = 'P0002';
      end if;

    when 'mission_complete' then
      v_amount := 50;
      v_id := p_ref::uuid;
      select m.equipment_id into v_equipment
      from public.missions m
      where m.id = v_id
        and m.published
        and exists (select 1 from public.mission_tasks t where t.mission_id = m.id)
        and not exists (
          select 1 from public.mission_tasks t
          where t.mission_id = m.id
            and not exists (
              select 1 from public.mission_progress mp
              where mp.task_id = t.id and mp.user_id = v_user
            )
        );
      if not found then
        raise exception 'mission not complete' using errcode = 'P0002';
      end if;
      v_key := v_id::text;
  end case;

  insert into public.points_ledger (user_id, reason, ref, amount, equipment_id)
  values (v_user, p_reason, v_key, v_amount, v_equipment)
  on conflict (user_id, reason, ref) do nothing;

  get diagnostics v_inserted = row_count;
  return case when v_inserted = 1 then v_amount else 0 end;
end;
$$;

revoke all on function public.award_points(public.points_reason, text) from public, anon;
grant execute on function public.award_points(public.points_reason, text) to authenticated;

-- ---------------------------------------------------------------------------
-- check_badges(user) -> codes newly unlocked by this call (empty array if none)
--
-- Stub rules; thresholds are placeholders to be tuned with the teacher:
--   first_scan       >= 1 scan
--   part_spotter     >= 10 distinct parts viewed
--   safety_first     >= 1 procedure completed
--   quiz_ace         an attempt where every question of the quiz was answered correctly
--   fault_finder     >= 3 distinct fault scenarios solved
--   mission_complete >= 1 mission with all tasks done
-- A caller may only check themselves; teachers/service role may check anyone.
-- ---------------------------------------------------------------------------
create function public.check_badges(p_user uuid default auth.uid())
returns text[]
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_caller uuid := (select auth.uid());
  v_new    text[];
begin
  if p_user is null then
    raise exception 'user required' using errcode = '22004';
  end if;
  if v_caller is not null and p_user <> v_caller and not public.is_teacher() then
    raise exception 'cannot check badges for another user' using errcode = '42501';
  end if;

  with earned (code) as (
    select 'first_scan'::text
    where exists (select 1 from public.scans s where s.user_id = p_user)

    union all
    select 'part_spotter'
    where (select count(*) from (
             select distinct pv.equipment_id, pv.part_id
             from public.part_views pv where pv.user_id = p_user) d) >= 10

    union all
    select 'safety_first'
    where exists (select 1 from public.procedure_completions pc where pc.user_id = p_user)

    union all
    select 'quiz_ace'
    where exists (
      select 1
      from public.quiz_attempts a
      join lateral (
        select count(*) as answered, count(*) filter (where qa.is_correct) as correct
        from public.quiz_answers qa where qa.attempt_id = a.id
      ) ans on true
      where a.user_id = p_user
        and ans.answered > 0
        and ans.correct = ans.answered
        and ans.answered = (select count(*) from public.quiz_questions qq where qq.quiz_id = a.quiz_id)
    )

    union all
    select 'fault_finder'
    where (select count(distinct fa.scenario_id)
           from public.fault_attempts fa
           where fa.user_id = p_user and fa.is_correct) >= 3

    union all
    select 'mission_complete'
    where exists (
      select 1 from public.missions m
      where exists (select 1 from public.mission_tasks t where t.mission_id = m.id)
        and not exists (
          select 1 from public.mission_tasks t
          where t.mission_id = m.id
            and not exists (
              select 1 from public.mission_progress mp
              where mp.task_id = t.id and mp.user_id = p_user)
        )
    )
  ),
  ins as (
    insert into public.user_badges (user_id, badge_code)
    select p_user, e.code from earned e
    join public.badges b on b.code = e.code
    on conflict do nothing
    returning badge_code
  )
  select coalesce(array_agg(badge_code), '{}') into v_new from ins;

  return v_new;
end;
$$;

revoke all on function public.check_badges(uuid) from public, anon;
grant execute on function public.check_badges(uuid) to authenticated;
