-- EquipX: schema (tables, enums, constraints, indexes, updated_at triggers, badge catalog).
-- Functions/triggers that depend on these tables: 20261104000100_functions.sql
-- Row level security:                              20261104000200_rls.sql
-- Views:                                           20261104000300_views.sql
--
-- Conventions
--   * Content ids are snake_case text where the content contract (docs/CONTRACT.md) gives them
--     (equipment.id, parts.id). Everything else uses uuid surrogate keys plus a stable `slug`
--     (the id used in /content/*.json) that is unique within its parent.
--   * Requires PostgreSQL 15+ (ON DELETE SET NULL (column list), security_invoker views).

-- ---------------------------------------------------------------------------
-- Enums
-- ---------------------------------------------------------------------------
create type public.user_role as enum ('student', 'teacher');

create type public.mission_task_type as enum ('scan', 'identify_part', 'complete_procedure', 'pass_quiz');

-- Matches TASKS.md T2.2: scan +10, first part view +2, quiz correct +5, fault fixed +20, mission complete +50
create type public.points_reason as enum ('scan', 'first_part_view', 'quiz_correct', 'fault_fixed', 'mission_complete');

-- ---------------------------------------------------------------------------
-- Shared trigger function: keep updated_at current
-- ---------------------------------------------------------------------------
create function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- profiles (1:1 with auth.users; row is created by trigger, see functions migration)
-- ---------------------------------------------------------------------------
create table public.profiles (
  id           uuid primary key references auth.users (id) on delete cascade,
  role         public.user_role not null default 'student',
  display_name text not null default '',
  class_group  text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create index profiles_role_idx on public.profiles (role);

-- ---------------------------------------------------------------------------
-- Content: equipment
-- ---------------------------------------------------------------------------
create table public.equipment (
  id                   text primary key check (id ~ '^[a-z][a-z0-9_]*$'),
  name                 text not null,
  category             text not null,
  summary              text not null default '',
  overview             text not null default '',
  model_asset          text not null,                      -- GLB path or URL, e.g. models/digital_multimeter.glb
  default_camera_orbit text not null default '0deg 75deg auto',
  model_credit         text,                               -- attribution line for CC BY models, else null
  layout               jsonb not null default '{}'::jsonb, -- generator input (see CONTRACT.md "layout")
  published            boolean not null default false,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),
  constraint equipment_layout_is_object check (jsonb_typeof(layout) = 'object')
);
create index equipment_published_idx on public.equipment (published);
create index equipment_category_idx on public.equipment (category);

-- ---------------------------------------------------------------------------
-- Content: parts
-- Key choice: COMPOSITE primary key (equipment_id, id). The part id is the
-- human-readable contract id (also the glTF node name) and is only unique per
-- device, so two devices may both have a `power_button`. Child tables reference
-- (equipment_id, part_id), which also guarantees a step/hotspot/view can never
-- point at a part of a different device.
-- ---------------------------------------------------------------------------
create table public.parts (
  equipment_id     text not null references public.equipment (id) on delete cascade on update cascade,
  id               text not null check (id ~ '^[a-z][a-z0-9_]*$'),
  number           integer not null check (number > 0),      -- hotspot marker number
  name             text not null,
  function         text not null default '',
  usage            text not null default '',
  safety           text[] not null default '{}',
  node_name        text not null,                            -- glTF node; defaults to id (trigger below)
  hotspot_position double precision[],                       -- optional override [x,y,z] metres
  hotspot_normal   double precision[],                       -- optional override [x,y,z] unit vector
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  primary key (equipment_id, id),
  unique (equipment_id, number),
  unique (equipment_id, node_name),
  constraint parts_hotspot_position_len check (hotspot_position is null or cardinality(hotspot_position) = 3),
  constraint parts_hotspot_normal_len   check (hotspot_normal   is null or cardinality(hotspot_normal)   = 3)
);

create function public.parts_default_node_name()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.node_name is null or new.node_name = '' then
    new.node_name := new.id;
  end if;
  return new;
end;
$$;

create trigger parts_default_node_name
  before insert or update on public.parts
  for each row execute function public.parts_default_node_name();

-- ---------------------------------------------------------------------------
-- Content: safety rules
-- ---------------------------------------------------------------------------
create table public.safety_rules (
  id           uuid primary key default gen_random_uuid(),
  equipment_id text not null references public.equipment (id) on delete cascade on update cascade,
  sort_order   integer not null default 0,
  text         text not null,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create index safety_rules_equipment_idx on public.safety_rules (equipment_id, sort_order);

-- ---------------------------------------------------------------------------
-- Content: procedures + ordered steps (optional part reference)
-- ---------------------------------------------------------------------------
create table public.procedures (
  id           uuid primary key default gen_random_uuid(),
  equipment_id text not null references public.equipment (id) on delete cascade on update cascade,
  slug         text not null check (slug ~ '^[a-z][a-z0-9_]*$'),
  title        text not null,
  description  text not null default '',
  sort_order   integer not null default 0,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  unique (equipment_id, slug),
  unique (id, equipment_id)
);
create index procedures_equipment_idx on public.procedures (equipment_id, sort_order);

create table public.procedure_steps (
  id           uuid primary key default gen_random_uuid(),
  procedure_id uuid not null,
  equipment_id text not null,
  step_number  integer not null check (step_number > 0),
  text         text not null,
  part_id      text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  unique (procedure_id, step_number),
  foreign key (procedure_id, equipment_id) references public.procedures (id, equipment_id) on delete cascade,
  foreign key (equipment_id, part_id) references public.parts (equipment_id, id)
    on delete set null (part_id) on update cascade
);
create index procedure_steps_equipment_part_idx on public.procedure_steps (equipment_id, part_id);

-- ---------------------------------------------------------------------------
-- Content: fault scenarios + options (exactly one correct option, see index)
-- ---------------------------------------------------------------------------
create table public.fault_scenarios (
  id           uuid primary key default gen_random_uuid(),
  equipment_id text not null references public.equipment (id) on delete cascade on update cascade,
  slug         text not null check (slug ~ '^[a-z][a-z0-9_]*$'),
  symptom      text not null,
  explanation  text not null default '',
  sort_order   integer not null default 0,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  unique (equipment_id, slug),
  unique (id, equipment_id)
);
create index fault_scenarios_equipment_idx on public.fault_scenarios (equipment_id, sort_order);

create table public.fault_options (
  id          uuid primary key default gen_random_uuid(),
  scenario_id uuid not null references public.fault_scenarios (id) on delete cascade,
  slug        text not null check (slug ~ '^[a-z][a-z0-9_]*$'),
  text        text not null,
  is_correct  boolean not null default false,
  sort_order  integer not null default 0,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (scenario_id, slug),
  unique (id, scenario_id)
);
create index fault_options_scenario_idx on public.fault_options (scenario_id, sort_order);
-- At most one correct option per scenario (editors should ensure there is exactly one).
create unique index fault_options_one_correct_idx on public.fault_options (scenario_id) where is_correct;

-- Parts the student should inspect for a scenario (contract: relatedPartIds)
create table public.fault_related_parts (
  scenario_id  uuid not null,
  equipment_id text not null,
  part_id      text not null,
  primary key (scenario_id, part_id),
  foreign key (scenario_id, equipment_id) references public.fault_scenarios (id, equipment_id) on delete cascade,
  foreign key (equipment_id, part_id) references public.parts (equipment_id, id) on delete cascade on update cascade
);
create index fault_related_parts_part_idx on public.fault_related_parts (equipment_id, part_id);

-- ---------------------------------------------------------------------------
-- Content: quizzes + questions
-- ---------------------------------------------------------------------------
create table public.quizzes (
  id             uuid primary key default gen_random_uuid(),
  equipment_id   text not null references public.equipment (id) on delete cascade on update cascade,
  slug           text not null check (slug ~ '^[a-z][a-z0-9_]*$'),
  title          text not null,
  time_limit_sec integer check (time_limit_sec is null or time_limit_sec > 0), -- null = untimed
  sort_order     integer not null default 0,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  unique (equipment_id, slug)
);
create index quizzes_equipment_idx on public.quizzes (equipment_id, sort_order);

create table public.quiz_questions (
  id            uuid primary key default gen_random_uuid(),
  quiz_id       uuid not null references public.quizzes (id) on delete cascade,
  slug          text not null,
  sort_order    integer not null default 0,
  prompt        text not null,
  options       text[] not null check (cardinality(options) >= 2),
  correct_index integer not null check (correct_index >= 0),
  explanation   text not null default '',
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  unique (quiz_id, slug),
  constraint quiz_questions_correct_in_range check (correct_index < cardinality(options))
);
create index quiz_questions_quiz_idx on public.quiz_questions (quiz_id, sort_order);

-- ---------------------------------------------------------------------------
-- Content: missions + ordered tasks
-- ---------------------------------------------------------------------------
create table public.missions (
  id           uuid primary key default gen_random_uuid(),
  slug         text not null unique check (slug ~ '^[a-z][a-z0-9_]*$'),
  title        text not null,
  description  text not null default '',
  equipment_id text references public.equipment (id) on delete set null on update cascade,
  published    boolean not null default false,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create index missions_equipment_idx on public.missions (equipment_id);

create table public.mission_tasks (
  id            uuid primary key default gen_random_uuid(),
  mission_id    uuid not null references public.missions (id) on delete cascade,
  sort_order    integer not null,
  task_type     public.mission_task_type not null,
  title         text not null default '',
  -- target refs; which ones are required depends on task_type (checked below)
  equipment_id  text references public.equipment (id) on delete cascade on update cascade,
  part_id       text,
  procedure_id  uuid references public.procedures (id) on delete cascade,
  quiz_id       uuid references public.quizzes (id) on delete cascade,
  min_score_pct integer not null default 70 check (min_score_pct between 0 and 100), -- pass_quiz threshold
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  unique (mission_id, sort_order),
  unique (id, mission_id),
  foreign key (equipment_id, part_id) references public.parts (equipment_id, id) on delete cascade on update cascade,
  constraint mission_tasks_target_matches_type check (
       (task_type = 'scan'               and equipment_id is not null)
    or (task_type = 'identify_part'      and equipment_id is not null and part_id is not null)
    or (task_type = 'complete_procedure' and procedure_id is not null)
    or (task_type = 'pass_quiz'          and quiz_id is not null)
  )
);
create index mission_tasks_equipment_part_idx on public.mission_tasks (equipment_id, part_id);
create index mission_tasks_procedure_idx on public.mission_tasks (procedure_id);
create index mission_tasks_quiz_idx on public.mission_tasks (quiz_id);

-- ---------------------------------------------------------------------------
-- Student activity (append-only; user_id defaults to the caller)
-- ---------------------------------------------------------------------------
create table public.scans (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  equipment_id text not null references public.equipment (id) on delete cascade on update cascade,
  created_at   timestamptz not null default now()
);
create index scans_user_idx on public.scans (user_id, created_at desc);
create index scans_equipment_idx on public.scans (equipment_id);

create table public.part_views (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  equipment_id text not null,
  part_id      text not null,
  created_at   timestamptz not null default now(),
  foreign key (equipment_id, part_id) references public.parts (equipment_id, id) on delete cascade on update cascade
);
create index part_views_user_part_idx on public.part_views (user_id, equipment_id, part_id);
create index part_views_part_idx on public.part_views (equipment_id, part_id);

create table public.quiz_attempts (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  quiz_id      uuid not null references public.quizzes (id) on delete cascade,
  duration_sec integer check (duration_sec is null or duration_sec >= 0),
  created_at   timestamptz not null default now()
);
create index quiz_attempts_user_idx on public.quiz_attempts (user_id, created_at desc);
create index quiz_attempts_quiz_idx on public.quiz_attempts (quiz_id);

create table public.quiz_answers (
  id             uuid primary key default gen_random_uuid(),
  attempt_id     uuid not null references public.quiz_attempts (id) on delete cascade,
  question_id    uuid not null references public.quiz_questions (id) on delete cascade,
  selected_index integer check (selected_index is null or selected_index >= 0), -- null = timed out / skipped
  is_correct     boolean not null default false,  -- overwritten by trigger from the question's correct_index
  time_ms        integer check (time_ms is null or time_ms >= 0),
  created_at     timestamptz not null default now(),
  unique (attempt_id, question_id)
);
create index quiz_answers_question_idx on public.quiz_answers (question_id);

create table public.fault_attempts (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  scenario_id uuid not null references public.fault_scenarios (id) on delete cascade,
  option_id   uuid not null,
  is_correct  boolean not null default false,     -- overwritten by trigger from fault_options.is_correct
  created_at  timestamptz not null default now(),
  foreign key (option_id, scenario_id) references public.fault_options (id, scenario_id) on delete cascade
);
create index fault_attempts_user_idx on public.fault_attempts (user_id, created_at desc);
create index fault_attempts_scenario_idx on public.fault_attempts (scenario_id);
create index fault_attempts_option_idx on public.fault_attempts (option_id, scenario_id);

create table public.procedure_completions (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  procedure_id uuid not null references public.procedures (id) on delete cascade,
  duration_sec integer check (duration_sec is null or duration_sec >= 0),
  created_at   timestamptz not null default now()
);
create index procedure_completions_user_idx on public.procedure_completions (user_id, created_at desc);
create index procedure_completions_procedure_idx on public.procedure_completions (procedure_id);

-- One row per (student, mission task) when the student finishes that task.
-- A mission is complete when every one of its tasks has a row for the student.
create table public.mission_progress (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  mission_id   uuid not null,
  task_id      uuid not null,
  completed_at timestamptz not null default now(),
  unique (user_id, task_id),
  foreign key (task_id, mission_id) references public.mission_tasks (id, mission_id) on delete cascade
);
create index mission_progress_mission_idx on public.mission_progress (mission_id);
create index mission_progress_task_idx on public.mission_progress (task_id, mission_id);

-- ---------------------------------------------------------------------------
-- Points + badges (written only by SECURITY DEFINER functions)
-- ---------------------------------------------------------------------------
create table public.points_ledger (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references public.profiles (id) on delete cascade,
  reason       public.points_reason not null,
  ref          text not null,                       -- what was rewarded; see award_points() for the key per reason
  amount       integer not null check (amount > 0),
  equipment_id text references public.equipment (id) on delete set null on update cascade, -- for per-device results
  created_at   timestamptz not null default now(),
  unique (user_id, reason, ref)                     -- idempotent: each reward is granted once
);
create index points_ledger_user_idx on public.points_ledger (user_id, created_at desc);
create index points_ledger_equipment_idx on public.points_ledger (equipment_id);

create table public.badges (
  code        text primary key check (code ~ '^[a-z][a-z0-9_]*$'),
  name        text not null,
  description text not null default '',
  sort_order  integer not null default 0,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create table public.user_badges (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  badge_code text not null references public.badges (code) on delete cascade on update cascade,
  awarded_at timestamptz not null default now(),
  primary key (user_id, badge_code)
);
create index user_badges_badge_idx on public.user_badges (badge_code);

-- Badge catalog (reference data; rules live in check_badges())
insert into public.badges (code, name, description, sort_order) values
  ('first_scan',       'First Scan',       'Scanned your first device.',                1),
  ('part_spotter',     'Part Spotter',     'Explored 10 different parts.',              2),
  ('safety_first',     'Safety First',     'Completed a procedure.',                    3),
  ('quiz_ace',         'Quiz Ace',         'Scored 100% on a quiz.',                    4),
  ('fault_finder',     'Fault Finder',     'Fixed 3 faults in the troubleshooter.',     5),
  ('mission_complete', 'Mission Complete', 'Completed a mission.',                      6);

-- ---------------------------------------------------------------------------
-- updated_at triggers (mutable tables only)
-- ---------------------------------------------------------------------------
do $$
declare t text;
begin
  foreach t in array array[
    'profiles', 'equipment', 'parts', 'safety_rules', 'procedures', 'procedure_steps',
    'fault_scenarios', 'fault_options', 'quizzes', 'quiz_questions',
    'missions', 'mission_tasks', 'badges'
  ] loop
    execute format(
      'create trigger set_updated_at before update on public.%I
         for each row execute function public.set_updated_at()', t);
  end loop;
end;
$$;
