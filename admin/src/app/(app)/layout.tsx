"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Sidebar } from "@/components/shell/sidebar";
import { useSession } from "@/lib/auth/use-session";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const user = useSession();
  const router = useRouter();

  useEffect(() => {
    if (user === null) router.replace("/login");
  }, [user, router]);

  if (!user) return null;

  return (
    <div className="flex min-h-screen">
      <Sidebar user={user} />
      <main className="min-w-0 flex-1">
        <div className="mx-auto max-w-5xl px-6 py-10 md:px-12">{children}</div>
      </main>
    </div>
  );
}
