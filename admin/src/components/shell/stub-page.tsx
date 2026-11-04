import { NAV_ITEMS } from "./nav";
import { EmptyState } from "./empty-state";
import { PageHeader } from "./page-header";

export function StubPage({
  href,
  description,
  emptyTitle,
  emptyDescription,
}: {
  href: string;
  description: string;
  emptyTitle: string;
  emptyDescription: string;
}) {
  const item = NAV_ITEMS.find((n) => n.href === href)!;
  return (
    <>
      <PageHeader title={item.label} description={description} />
      <EmptyState icon={item.icon} title={emptyTitle} description={emptyDescription} />
    </>
  );
}
