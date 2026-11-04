import { StubPage } from "@/components/shell/stub-page";

export default function Page() {
  return (
    <StubPage
      href="/faults"
      description="Fault scenarios students diagnose."
      emptyTitle="No fault scenarios yet"
      emptyDescription="Fault scenarios are authored per device and will appear here."
    />
  );
}
