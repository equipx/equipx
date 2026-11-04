import { StubPage } from "@/components/shell/stub-page";

export default function Page() {
  return (
    <StubPage
      href="/procedures"
      description="Step-by-step usage procedures for each device."
      emptyTitle="No procedures yet"
      emptyDescription="Procedures are authored per device and will appear here."
    />
  );
}
