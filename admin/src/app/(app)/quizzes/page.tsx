import { StubPage } from "@/components/shell/stub-page";

export default function Page() {
  return (
    <StubPage
      href="/quizzes"
      description="Quizzes that test knowledge of each device."
      emptyTitle="No quizzes yet"
      emptyDescription="Quizzes are authored per device and will appear here."
    />
  );
}
