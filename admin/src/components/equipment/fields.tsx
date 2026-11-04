"use client";

import { useState } from "react";
import { Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

export function Field({ label, children, className }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <div className={className}>
      <Label className="mb-1 block text-xs text-muted-foreground">{label}</Label>
      {children}
    </div>
  );
}

/** Number input that keeps its own text so partial input ("0.", "-") can be typed. */
export function NumField({
  value,
  onChange,
  step = "any",
  min,
  className,
  label,
}: {
  value: number;
  onChange: (n: number) => void;
  step?: string;
  min?: number;
  className?: string;
  label?: string;
}) {
  const [text, setText] = useState<string | null>(null);
  return (
    <Input
      type="number"
      aria-label={label}
      step={step}
      min={min}
      className={className}
      value={text ?? String(value)}
      onChange={(e) => {
        setText(e.target.value);
        const n = parseFloat(e.target.value);
        if (Number.isFinite(n)) onChange(n);
      }}
      onBlur={() => setText(null)}
    />
  );
}

export function Pick<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  options: readonly T[];
  onChange: (v: T) => void;
  label?: string;
}) {
  return (
    <Select value={value} onValueChange={(v) => v != null && onChange(v as T)}>
      <SelectTrigger size="sm" className="w-full" aria-label={label}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {options.map((o) => (
          <SelectItem key={o} value={o}>
            {o}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function StringList({
  items,
  onChange,
  addLabel,
  placeholder,
}: {
  items: string[];
  onChange: (items: string[]) => void;
  addLabel: string;
  placeholder?: string;
}) {
  return (
    <div className="flex flex-col gap-2">
      {items.map((s, i) => (
        <div key={i} className="flex gap-2">
          <Input
            value={s}
            placeholder={placeholder}
            aria-label={`${addLabel} ${i + 1}`}
            onChange={(e) => onChange(items.map((x, j) => (j === i ? e.target.value : x)))}
          />
          <Button
            variant="ghost"
            size="icon"
            aria-label="Remove"
            onClick={() => onChange(items.filter((_, j) => j !== i))}
          >
            <X />
          </Button>
        </div>
      ))}
      <div>
        <Button variant="outline" size="sm" onClick={() => onChange([...items, ""])}>
          <Plus />
          {addLabel}
        </Button>
      </div>
    </div>
  );
}
