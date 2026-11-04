import { StubPage } from "@/components/shell/stub-page";

export default function Page() {
  return (
    <StubPage
      href="/missions"
      description="Missions that guide students through tasks and earn points."
      emptyTitle="No missions yet"
      emptyDescription="Create missions to challenge students."
    />
  );
}
