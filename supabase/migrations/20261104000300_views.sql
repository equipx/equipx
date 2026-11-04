-- EquipX: reporting views.
--
-- student_results: one row per (student, equipment) with any activity, for the admin Results page.
-- security_invoker = true => the view runs with the CALLER's privileges and RLS, so a teacher sees
-- every student and a student only sees their own row(s).
create view public.student_results
with (security_invoker = true) as
with attempts as (
  select a.user_id,
         q.equipment_id,
         count(*)                  as quiz_attempts,
         avg(a.duration_sec)       as avg_attempt_sec
  from public.quiz_attempts a
  join public.quizzes q on q.id = a.quiz_id
  group by a.user_id, q.equipment_id
),
answers as (
  select a.user_id,
         q.equipment_id,
         count(*)                                 as questions_answered,
         count(*) filter (where qa.is_correct)    as correct_answers
  from public.quiz_answers qa
  join public.quiz_attempts a on a.id = qa.attempt_id
  join public.quizzes q on q.id = a.quiz_id
  group by a.user_id, q.equipment_id
),
pts as (
  select l.user_id, l.equipment_id, sum(l.amount)::integer as points
  from public.points_ledger l
  where l.equipment_id is not null
  group by l.user_id, l.equipment_id
),
pairs as (
  select user_id, equipment_id from attempts
  union
  select user_id, equipment_id from pts
  union
  select s.user_id, s.equipment_id from public.scans s
)
select p.id                                   as user_id,
       p.display_name,
       p.class_group,
       e.id                                   as equipment_id,
       e.name                                 as equipment_name,
       coalesce(att.quiz_attempts, 0)          as quiz_attempts,
       coalesce(an.questions_answered, 0)     as questions_answered,
       coalesce(an.correct_answers, 0)        as correct_answers,
       round(100.0 * an.correct_answers / nullif(an.questions_answered, 0), 1) as accuracy_pct,
       round(att.avg_attempt_sec::numeric, 1)  as avg_attempt_sec,
       coalesce(pt.points, 0)                 as points
from pairs
join public.profiles p  on p.id = pairs.user_id and p.role = 'student'
join public.equipment e on e.id = pairs.equipment_id
left join attempts att on att.user_id = pairs.user_id and att.equipment_id = pairs.equipment_id
left join answers  an on an.user_id = pairs.user_id and an.equipment_id = pairs.equipment_id
left join pts      pt on pt.user_id = pairs.user_id and pt.equipment_id = pairs.equipment_id;

revoke all on public.student_results from anon;
grant select on public.student_results to authenticated;
