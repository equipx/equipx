import { PageHeader } from "@/components/shell/page-header";
import { ResultsView } from "@/components/results/results-view";

export default function ResultsPage() {
  return (
    <>
      <PageHeader title="Results" description="Student scores, accuracy and time. Showing generated sample data." />
      <ResultsView />
    </>
  );
}
