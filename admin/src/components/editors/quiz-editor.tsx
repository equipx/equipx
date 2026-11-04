"use client";

import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { Quiz, QuizQuestion } from "@/lib/data";
import { Card, Field, MoveButtons, inputCls, move, uniqueId } from "./common";
import { DeviceItemEditor } from "./item-editor";
import { validateQuiz } from "./validate";

export function QuizEditor({ deviceId, itemId }: { deviceId: string; itemId: string }) {
  return (
    <DeviceItemEditor<Quiz>
      deviceId={deviceId}
      itemId={itemId}
      collection="quizzes"
      basePath="/quizzes"
      noun="quiz"
      makeNew={() => ({
        id: "new_quiz",
        title: "",
        timeLimitSec: null,
        questions: [blankQuestion("q1")],
      })}
      validate={(q) => validateQuiz(q)}
      nameOf={(q) => q.title}
      render={(q, set) => {
        const setQ = (i: number, patch: Partial<QuizQuestion>) =>
          set({ ...q, questions: q.questions.map((x, j) => (j === i ? { ...x, ...patch } : x)) });
        return (
          <div className="space-y-6">
            <div className="grid gap-4 md:grid-cols-[1fr_12rem]">
              <Field label="Title">
                <input
                  className={inputCls}
                  value={q.title}
                  onChange={(e) => set({ ...q, title: e.target.value })}
                  placeholder="Multimeter basics"
                />
              </Field>
              <Field label="Time limit (seconds, optional)">
                <input
                  className={inputCls}
                  type="number"
                  min={1}
                  value={q.timeLimitSec ?? ""}
                  onChange={(e) =>
                    set({ ...q, timeLimitSec: e.target.value === "" ? null : Number(e.target.value) })
                  }
                />
              </Field>
            </div>
            <div className="space-y-3">
              <h2 className="text-sm font-semibold">Questions</h2>
              {q.questions.map((x, i) => (
                <Card key={i} className="space-y-3">
                  <div className="flex items-start gap-3">
                    <span className="mt-1.5 w-6 shrink-0 text-sm text-muted-foreground">{i + 1}.</span>
                    <Textarea
                      aria-label={`Question ${i + 1} prompt`}
                      rows={2}
                      value={x.prompt}
                      onChange={(e) => setQ(i, { prompt: e.target.value })}
                      placeholder="Question prompt"
                    />
                    <MoveButtons
                      index={i}
                      count={q.questions.length}
                      removeDisabled={q.questions.length <= 1}
                      onMove={(dir) => set({ ...q, questions: move(q.questions, i, dir) })}
                      onRemove={() =>
                        set({ ...q, questions: q.questions.filter((_, j) => j !== i) })
                      }
                    />
                  </div>
                  <div className="space-y-2 pl-9">
                    {x.options.map((o, k) => (
                      <div key={k} className="flex items-center gap-2">
                        <input
                          type="radio"
                          name={`correct-${i}`}
                          aria-label={`Question ${i + 1} option ${k + 1} is correct`}
                          checked={x.correctIndex === k}
                          onChange={() => setQ(i, { correctIndex: k })}
                          className="size-4 accent-[#2383E2]"
                        />
                        <input
                          className={inputCls}
                          aria-label={`Question ${i + 1} option ${k + 1}`}
                          value={o}
                          placeholder={`Option ${k + 1}`}
                          onChange={(e) =>
                            setQ(i, {
                              options: x.options.map((v, m) => (m === k ? e.target.value : v)),
                            })
                          }
                        />
                      </div>
                    ))}
                    <Textarea
                      aria-label={`Question ${i + 1} explanation`}
                      rows={2}
                      value={x.explanation}
                      onChange={(e) => setQ(i, { explanation: e.target.value })}
                      placeholder="Explanation shown after answering"
                    />
                  </div>
                </Card>
              ))}
              <Button
                variant="outline"
                onClick={() =>
                  set({
                    ...q,
                    questions: [
                      ...q.questions,
                      blankQuestion(
                        uniqueId(`q${q.questions.length + 1}`, q.questions.map((x) => x.id)),
                      ),
                    ],
                  })
                }
              >
                <Plus /> Add question
              </Button>
            </div>
          </div>
        );
      }}
    />
  );
}

function blankQuestion(id: string): QuizQuestion {
  return { id, prompt: "", options: ["", "", "", ""], correctIndex: 0, explanation: "" };
}
