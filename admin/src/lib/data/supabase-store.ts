import { getSupabase, unwrap } from "@/lib/supabase/client";
import type { DataStore } from "./store";
import type {
  Device,
  DeviceIndexEntry,
  Fault,
  Layout,
  Mission,
  MissionTask,
  MissionTaskType,
  Part,
  Procedure,
  Quiz,
} from "./types";

type Row = Record<string, unknown>;
type Vec3 = [number, number, number];

const toVec3 = (v: unknown): Vec3 | null =>
  Array.isArray(v) && v.length === 3 ? [Number(v[0]), Number(v[1]), Number(v[2])] : null;

/** Supabase-backed DataStore: Device <-> equipment/parts/safety_rules/procedures/faults/quizzes tables. */
export class SupabaseStore implements DataStore {
  private db = getSupabase();

  async listDevices(): Promise<DeviceIndexEntry[]> {
    const rows = unwrap(
      await this.db.from("equipment").select("id,name,category,summary").order("name"),
      "List equipment",
    ) as DeviceIndexEntry[];
    return rows;
  }

  async getDevice(id: string): Promise<Device | null> {
    const db = this.db;
    const eq = unwrap(await db.from("equipment").select("*").eq("id", id).maybeSingle(), "Load equipment") as Row | null;
    if (!eq) return null;

    const [parts, rules, procs, faults, related, quizzes] = await Promise.all([
      db.from("parts").select("*").eq("equipment_id", id).order("number"),
      db.from("safety_rules").select("text,sort_order").eq("equipment_id", id).order("sort_order"),
      db.from("procedures").select("id,slug,title,sort_order").eq("equipment_id", id).order("sort_order"),
      db.from("fault_scenarios").select("id,slug,symptom,explanation,sort_order").eq("equipment_id", id).order("sort_order"),
      db.from("fault_related_parts").select("scenario_id,part_id").eq("equipment_id", id),
      db.from("quizzes").select("id,slug,title,time_limit_sec,sort_order").eq("equipment_id", id).order("sort_order"),
    ]);
    const procRows = unwrap(procs, "Load procedures") as Row[];
    const faultRows = unwrap(faults, "Load faults") as Row[];
    const quizRows = unwrap(quizzes, "Load quizzes") as Row[];
    const procIds = procRows.map((p) => p.id as string);

    // Answer-key columns are only readable through the teacher-only functions.
    const [steps, options, questions] = await Promise.all([
      procIds.length
        ? db.from("procedure_steps").select("procedure_id,step_number,text,part_id").in("procedure_id", procIds).order("step_number")
        : Promise.resolve({ data: [], error: null }),
      db.rpc("teacher_fault_options"),
      db.rpc("teacher_quiz_questions"),
    ]);
    const stepRows = unwrap(steps, "Load steps") as Row[];
    const scenarioIds = new Set(faultRows.map((f) => f.id as string));
    const quizIds = new Set(quizRows.map((q) => q.id as string));
    const optionRows = (unwrap(options, "Load fault options") as Row[]).filter((o) => scenarioIds.has(o.scenario_id as string));
    const questionRows = (unwrap(questions, "Load quiz questions") as Row[]).filter((q) => quizIds.has(q.quiz_id as string));
    const relatedRows = unwrap(related, "Load related parts") as Row[];

    const partRows = unwrap(parts, "Load parts") as Row[];
    const hotspotOverrides: NonNullable<Device["hotspotOverrides"]> = {};
    const outParts: Part[] = partRows.map((p) => {
      const pos = toVec3(p.hotspot_position);
      const normal = toVec3(p.hotspot_normal);
      if (pos && normal) hotspotOverrides[p.id as string] = { position: pos, normal };
      return {
        id: p.id as string,
        number: p.number as number,
        name: p.name as string,
        function: p.function as string,
        usage: p.usage as string,
        safety: (p.safety as string[]) ?? [],
      };
    });

    const procedures: Procedure[] = procRows.map((p) => ({
      id: p.slug as string,
      title: p.title as string,
      steps: stepRows
        .filter((s) => s.procedure_id === p.id)
        .map((s) => ({ text: s.text as string, partId: (s.part_id as string | null) ?? "" })),
    }));

    const faultsOut: Fault[] = faultRows.map((f) => {
      const opts = optionRows.filter((o) => o.scenario_id === f.id);
      return {
        id: f.slug as string,
        symptom: f.symptom as string,
        options: opts.map((o) => ({ id: o.slug as string, text: o.text as string })),
        correctOptionId: (opts.find((o) => o.is_correct)?.slug as string | undefined) ?? "",
        explanation: f.explanation as string,
        relatedPartIds: relatedRows.filter((r) => r.scenario_id === f.id).map((r) => r.part_id as string),
      };
    });

    const quizzesOut: Quiz[] = quizRows.map((z) => ({
      id: z.slug as string,
      title: z.title as string,
      timeLimitSec: (z.time_limit_sec as number | null) ?? null,
      questions: questionRows
        .filter((q) => q.quiz_id === z.id)
        .map((q) => ({
          id: q.slug as string,
          prompt: q.prompt as string,
          options: q.options as string[],
          correctIndex: q.correct_index as number,
          explanation: q.explanation as string,
        })),
    }));

    const device: Device = {
      id: eq.id as string,
      name: eq.name as string,
      category: eq.category as string,
      summary: eq.summary as string,
      overview: eq.overview as string,
      model: {
        asset: eq.model_asset as string,
        hotspots: `models/${eq.id}.hotspots.json`,
        cameraOrbit: eq.default_camera_orbit as string,
        credit: (eq.model_credit as string | null) ?? null,
      },
      layout: eq.layout as Layout,
      parts: outParts,
      safetyRules: (rules ? (unwrap(rules, "Load safety rules") as Row[]) : []).map((r) => r.text as string),
      procedures,
      faults: faultsOut,
      quizzes: quizzesOut,
    };
    if (Object.keys(hotspotOverrides).length) device.hotspotOverrides = hotspotOverrides;
    return device;
  }

  async saveDevice(d: Device): Promise<void> {
    const db = this.db;
    const e = d.id;

    // equipment: keep `published` on update; new devices created in the admin panel are published.
    const existing = unwrap(await db.from("equipment").select("id").eq("id", e).maybeSingle(), "Check equipment") as Row | null;
    const eqRow = {
      id: e,
      name: d.name,
      category: d.category,
      summary: d.summary,
      overview: d.overview,
      model_asset: d.model.asset,
      default_camera_orbit: d.model.cameraOrbit,
      model_credit: d.model.credit,
      layout: d.layout,
    };
    if (existing) unwrap(await db.from("equipment").update(eqRow).eq("id", e), "Update equipment");
    else unwrap(await db.from("equipment").insert({ ...eqRow, published: true }), "Create equipment");

    // parts (+ hotspot overrides)
    const partRows = d.parts.map((p) => {
      const o = d.hotspotOverrides?.[p.id];
      return {
        equipment_id: e,
        id: p.id,
        number: p.number,
        name: p.name,
        function: p.function,
        usage: p.usage,
        safety: p.safety,
        node_name: p.id,
        hotspot_position: o?.position ?? null,
        hotspot_normal: o?.normal ?? null,
      };
    });
    if (partRows.length)
      unwrap(await db.from("parts").upsert(partRows, { onConflict: "equipment_id,id" }), "Save parts");
    await this.deleteMissing("parts", { equipment_id: e }, "id", d.parts.map((p) => p.id));

    // safety rules: replace
    unwrap(await db.from("safety_rules").delete().eq("equipment_id", e), "Clear safety rules");
    if (d.safetyRules.length)
      unwrap(
        await db.from("safety_rules").insert(d.safetyRules.map((text, i) => ({ equipment_id: e, sort_order: i + 1, text }))),
        "Save safety rules",
      );

    // procedures keep their uuid (missions and completions reference it); steps are replaced
    if (d.procedures.length) {
      const saved = unwrap(
        await db
          .from("procedures")
          .upsert(
            d.procedures.map((p, i) => ({ equipment_id: e, slug: p.id, title: p.title, sort_order: i + 1 })),
            { onConflict: "equipment_id,slug" },
          )
          .select("id,slug"),
        "Save procedures",
      ) as Row[];
      const idBySlug = new Map(saved.map((r) => [r.slug as string, r.id as string]));
      const pids = [...idBySlug.values()];
      unwrap(await this.db.from("procedure_steps").delete().in("procedure_id", pids), "Clear steps");
      const stepRows = d.procedures.flatMap((p) =>
        p.steps.map((s, j) => ({
          procedure_id: idBySlug.get(p.id)!,
          equipment_id: e,
          step_number: j + 1,
          text: s.text,
          part_id: s.partId || null,
        })),
      );
      if (stepRows.length) unwrap(await db.from("procedure_steps").insert(stepRows), "Save steps");
    }
    await this.deleteMissing("procedures", { equipment_id: e }, "slug", d.procedures.map((p) => p.id));

    // fault scenarios: options upserted by slug (attempts reference option ids)
    if (d.faults.length) {
      const saved = unwrap(
        await db
          .from("fault_scenarios")
          .upsert(
            d.faults.map((f, i) => ({
              equipment_id: e,
              slug: f.id,
              symptom: f.symptom,
              explanation: f.explanation,
              sort_order: i + 1,
            })),
            { onConflict: "equipment_id,slug" },
          )
          .select("id,slug"),
        "Save faults",
      ) as Row[];
      const idBySlug = new Map(saved.map((r) => [r.slug as string, r.id as string]));
      for (const f of d.faults) {
        const sid = idBySlug.get(f.id)!;
        // at most one correct option per scenario: clear first, then upsert
        unwrap(await db.from("fault_options").update({ is_correct: false }).eq("scenario_id", sid), "Reset options");
        await this.saveByKey(
          "fault_options",
          "scenario_id",
          sid,
          f.options.map((o, j) => ({ slug: o.id, text: o.text, is_correct: o.id === f.correctOptionId, sort_order: j + 1 })),
        );
        await this.deleteMissing("fault_options", { scenario_id: sid }, "slug", f.options.map((o) => o.id));
        unwrap(await db.from("fault_related_parts").delete().eq("scenario_id", sid), "Clear related parts");
        const rel = [...new Set(f.relatedPartIds)].map((pid) => ({ scenario_id: sid, equipment_id: e, part_id: pid }));
        if (rel.length) unwrap(await db.from("fault_related_parts").insert(rel), "Save related parts");
      }
    }
    await this.deleteMissing("fault_scenarios", { equipment_id: e }, "slug", d.faults.map((f) => f.id));

    // quizzes + questions (upsert by slug; attempts reference question ids)
    if (d.quizzes.length) {
      const saved = unwrap(
        await db
          .from("quizzes")
          .upsert(
            d.quizzes.map((z, i) => ({
              equipment_id: e,
              slug: z.id,
              title: z.title,
              time_limit_sec: z.timeLimitSec,
              sort_order: i + 1,
            })),
            { onConflict: "equipment_id,slug" },
          )
          .select("id,slug"),
        "Save quizzes",
      ) as Row[];
      const idBySlug = new Map(saved.map((r) => [r.slug as string, r.id as string]));
      for (const z of d.quizzes) {
        const qid = idBySlug.get(z.id)!;
        await this.saveByKey(
          "quiz_questions",
          "quiz_id",
          qid,
          z.questions.map((q, j) => ({
            slug: q.id,
            sort_order: j + 1,
            prompt: q.prompt,
            options: q.options,
            correct_index: q.correctIndex,
            explanation: q.explanation,
          })),
        );
        await this.deleteMissing("quiz_questions", { quiz_id: qid }, "slug", z.questions.map((q) => q.id));
      }
    }
    await this.deleteMissing("quizzes", { equipment_id: e }, "slug", d.quizzes.map((z) => z.id));
  }

  async deleteDevice(id: string): Promise<void> {
    unwrap(await this.db.from("equipment").delete().eq("id", id), "Delete equipment");
  }

  async listMissions(): Promise<Mission[]> {
    const db = this.db;
    const [missions, tasks, procs, quizzes] = await Promise.all([
      db.from("missions").select("id,slug,title,description,equipment_id,created_at").order("created_at").order("slug"),
      db.from("mission_tasks").select("*").order("sort_order"),
      db.from("procedures").select("id,slug"),
      db.from("quizzes").select("id,slug"),
    ]);
    const procSlug = new Map((unwrap(procs, "Load procedures") as Row[]).map((r) => [r.id as string, r.slug as string]));
    const quizSlug = new Map((unwrap(quizzes, "Load quizzes") as Row[]).map((r) => [r.id as string, r.slug as string]));
    const taskRows = unwrap(tasks, "Load mission tasks") as Row[];
    return (unwrap(missions, "Load missions") as Row[]).map((m) => {
      const out: Mission = {
        id: m.slug as string,
        title: m.title as string,
        description: m.description as string,
        tasks: taskRows
          .filter((t) => t.mission_id === m.id)
          .map((t) => {
            const task: MissionTask = {
              id: `t${t.sort_order}`,
              type: t.task_type as MissionTaskType,
              title: t.title as string,
              deviceId: (t.equipment_id as string | null) ?? "",
            };
            if (t.part_id) task.partId = t.part_id as string;
            if (t.procedure_id) task.procedureId = procSlug.get(t.procedure_id as string);
            if (t.quiz_id) {
              task.quizId = quizSlug.get(t.quiz_id as string);
              task.minScorePct = t.min_score_pct as number;
            }
            return task;
          }),
      };
      if (m.equipment_id) out.deviceId = m.equipment_id as string;
      return out;
    });
  }

  async saveMission(m: Mission): Promise<void> {
    const db = this.db;
    const row = { slug: m.id, title: m.title, description: m.description, equipment_id: m.deviceId || null };
    const existing = unwrap(await db.from("missions").select("id").eq("slug", m.id).maybeSingle(), "Check mission") as Row | null;
    let missionId: string;
    if (existing) {
      missionId = existing.id as string;
      unwrap(await db.from("missions").update(row).eq("id", missionId), "Update mission");
    } else {
      const created = unwrap(
        await db.from("missions").insert({ ...row, published: true }).select("id").single(),
        "Create mission",
      ) as Row;
      missionId = created.id as string;
    }

    // Resolve procedure/quiz slugs to uuids (scoped by the task's device).
    const [procs, quizzes] = await Promise.all([
      db.from("procedures").select("id,slug,equipment_id"),
      db.from("quizzes").select("id,slug,equipment_id"),
    ]);
    const procId = new Map((unwrap(procs, "Load procedures") as Row[]).map((r) => [`${r.equipment_id}|${r.slug}`, r.id as string]));
    const quizId = new Map((unwrap(quizzes, "Load quizzes") as Row[]).map((r) => [`${r.equipment_id}|${r.slug}`, r.id as string]));

    const rows = m.tasks.map((t, i) => {
      const procedure_id = t.procedureId ? procId.get(`${t.deviceId}|${t.procedureId}`) : null;
      const quiz_id = t.quizId ? quizId.get(`${t.deviceId}|${t.quizId}`) : null;
      if (t.procedureId && !procedure_id) throw new Error(`Task "${t.title}": procedure ${t.procedureId} not found on ${t.deviceId}`);
      if (t.quizId && !quiz_id) throw new Error(`Task "${t.title}": quiz ${t.quizId} not found on ${t.deviceId}`);
      return {
        mission_id: missionId,
        sort_order: i + 1,
        task_type: t.type,
        title: t.title,
        equipment_id: t.deviceId || null,
        part_id: t.partId ?? null,
        procedure_id: procedure_id ?? null,
        quiz_id: quiz_id ?? null,
        min_score_pct: t.minScorePct ?? 70,
      };
    });
    if (rows.length) unwrap(await db.from("mission_tasks").upsert(rows, { onConflict: "mission_id,sort_order" }), "Save tasks");
    unwrap(await db.from("mission_tasks").delete().eq("mission_id", missionId).gt("sort_order", rows.length), "Remove old tasks");
  }

  async deleteMission(id: string): Promise<void> {
    unwrap(await this.db.from("missions").delete().eq("slug", id), "Delete mission");
  }

  /** Not applicable to a live database (it would mean wiping content); re-seed with `npm run db:seed-sql` instead. */
  async reset(): Promise<void> {
    throw new Error("Reset is only available with the offline mock data. Re-seed the database from /content instead.");
  }

  /**
   * Insert-or-update by (parent, slug) WITHOUT ON CONFLICT: the answer-key columns have no SELECT grant,
   * and Postgres requires SELECT on every column an ON CONFLICT DO UPDATE touches. Existing rows keep their uuid.
   */
  private async saveByKey(table: string, parentCol: string, parentId: string, rows: (Row & { slug: string })[]) {
    const existing = unwrap(await this.db.from(table).select("id,slug").eq(parentCol, parentId), `Load ${table}`) as Row[];
    const idBySlug = new Map(existing.map((r) => [r.slug as string, r.id as string]));
    const fresh = rows.filter((r) => !idBySlug.has(r.slug)).map((r) => ({ ...r, [parentCol]: parentId }));
    if (fresh.length) unwrap(await this.db.from(table).insert(fresh), `Insert ${table}`);
    for (const r of rows) {
      const id = idBySlug.get(r.slug);
      if (id) unwrap(await this.db.from(table).update(r).eq("id", id), `Update ${table}`);
    }
  }

  /** Deletes rows matching `scope` whose `column` value is not in `keep`. */
  private async deleteMissing(table: string, scope: Record<string, string>, column: string, keep: string[]) {
    let q = this.db.from(table).delete();
    for (const [k, v] of Object.entries(scope)) q = q.eq(k, v);
    if (keep.length) q = q.not(column, "in", `(${keep.map((k) => `"${k.replace(/"/g, '\\"')}"`).join(",")})`);
    unwrap(await q, `Delete removed ${table}`);
  }
}
