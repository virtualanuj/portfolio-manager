"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { NAV_ITEMS } from "@/components/nav";
import { cn } from "@/lib/cn";

function isActive(pathname: string, href: string): boolean {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

function Icon({ path }: { path: string }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={path} />
    </svg>
  );
}

/** Left sidebar from 768px up; a bottom tab bar below that. */
export function Sidebar() {
  const pathname = usePathname();
  return (
    <nav
      aria-label="Main"
      className={cn(
        "fixed inset-x-0 bottom-0 z-40 flex justify-around border-t border-line bg-surface pb-[env(safe-area-inset-bottom)]",
        "md:static md:inset-auto md:z-auto md:w-56 md:shrink-0 md:flex-col md:justify-start md:gap-1 md:border-t-0 md:border-r md:p-3 md:pb-3",
      )}
    >
      <div className="hidden px-3 pt-2 pb-4 text-base font-semibold md:block">Portfolio</div>
      {NAV_ITEMS.map((item) => {
        const active = isActive(pathname, item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active ? "page" : undefined}
            aria-label={item.label}
            className={cn(
              "flex flex-1 flex-col items-center gap-0.5 px-1 py-2 text-[0.7rem] md:flex-none md:flex-row md:gap-3 md:rounded-control md:px-3 md:text-sm",
              active ? "font-semibold text-accent md:bg-sunken" : "text-muted hover:text-ink",
            )}
          >
            <Icon path={item.icon} />
            <span className="max-md:sr-only">{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
