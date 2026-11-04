import { StubPage } from "@/components/shell/stub-page";

export default function Page() {
  return (
    <StubPage
      href="/results"
      description="Student scores, accuracy and time."
      emptyTitle="No results yet"
      emptyDescription="Results will appear once students complete quizzes and missions."
    />
  );
}
