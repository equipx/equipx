"use client";

import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  BADGES, formatDate, formatDuration, studentDetail, type Filters, type ResultsData, type StudentRow,
} from "@/lib/results";

const pct = (n: number | null) => (n == null ? "-" : `${n}%`);

export function StudentSheet({
  student, data, filters, onClose,
}: {
  student: StudentRow | null;
  data: ResultsData;
  filters: Filters;
  onClose: () => void;
}) {
  const detail = student ? studentDetail(data, student.id, filters) : null;
  return (
    <Sheet open={!!student} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-xl">
        {student && detail && (
          <>
            <SheetHeader>
              <SheetTitle>{student.name}</SheetTitle>
              <SheetDescription>{student.classGroup} - {student.college}</SheetDescription>
            </SheetHeader>
            <div className="space-y-6 px-4 pb-6">
              <div className="grid grid-cols-3 gap-3">
                <Stat label="Accuracy" value={pct(student.accuracy)} />
                <Stat label="Points" value={String(student.points)} />
                <Stat label="Avg time" value={formatDuration(student.avgSec)} />
              </div>
              <section>
                <h3 className="mb-2 text-sm font-medium">Badges ({student.badges.length}/{BADGES.length})</h3>
                <div className="flex flex-wrap gap-2">
                  {BADGES.map((b) => (
                    <Badge key={b.id} variant={student.badges.includes(b.id) ? "default" : "outline"} title={b.description}
                      className={student.badges.includes(b.id) ? "" : "text-muted-foreground"}>
                      {b.label}
                    </Badge>
                  ))}
                </div>
              </section>
              <section>
                <h3 className="mb-2 text-sm font-medium">Per-device breakdown</h3>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Device</TableHead>
                      <TableHead className="text-right">Attempts</TableHead>
                      <TableHead className="text-right">Accuracy</TableHead>
                      <TableHead className="text-right">Avg time</TableHead>
                      <TableHead className="text-right">Points</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {detail.perDevice.length === 0 && <EmptyRow cols={5} />}
                    {detail.perDevice.map((d) => (
                      <TableRow key={d.deviceId}>
                        <TableCell>{d.name}</TableCell>
                        <TableCell className="text-right">{d.attempts}</TableCell>
                        <TableCell className="text-right">{pct(d.accuracy)}</TableCell>
                        <TableCell className="text-right">{formatDuration(d.avgSec)}</TableCell>
                        <TableCell className="text-right">{d.points}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </section>
              <section>
                <h3 className="mb-2 text-sm font-medium">Attempt history</h3>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>When</TableHead>
                      <TableHead>Device</TableHead>
                      <TableHead className="text-right">Score</TableHead>
                      <TableHead className="text-right">Time</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {detail.history.length === 0 && <EmptyRow cols={4} />}
                    {detail.history.map((h) => (
                      <TableRow key={h.id}>
                        <TableCell className="whitespace-nowrap">{formatDate(h.at)}</TableCell>
                        <TableCell>{h.device}</TableCell>
                        <TableCell className="text-right">{h.correct}/{h.total}</TableCell>
                        <TableCell className="text-right">{formatDuration(h.durationSec)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </section>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border bg-secondary px-3 py-2">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-lg font-semibold">{value}</p>
    </div>
  );
}

function EmptyRow({ cols }: { cols: number }) {
  return (
    <TableRow>
      <TableCell colSpan={cols} className="py-6 text-center text-muted-foreground">No activity for these filters.</TableCell>
    </TableRow>
  );
}
