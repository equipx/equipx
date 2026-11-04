import {
  BookOpen,
  ClipboardList,
  Cpu,
  LayoutDashboard,
  Target,
  TriangleAlert,
  BarChart3,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
}

export const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/equipment", label: "Equipment", icon: Cpu },
  { href: "/procedures", label: "Procedures", icon: ClipboardList },
  { href: "/faults", label: "Faults", icon: TriangleAlert },
  { href: "/quizzes", label: "Quizzes", icon: BookOpen },
  { href: "/missions", label: "Missions", icon: Target },
  { href: "/results", label: "Results", icon: BarChart3 },
];
