export type NavItem = { href: string; label: string; icon: string };

/** Sidebar and bottom tab bar entries. `icon` is an SVG path drawn on a 24px grid. */
export const NAV_ITEMS: NavItem[] = [
  { href: "/", label: "Dashboard", icon: "M3 12l9-8 9 8M5 10v10h5v-6h4v6h5V10" },
  { href: "/holdings", label: "Holdings", icon: "M4 6h16M4 12h16M4 18h16" },
  {
    href: "/allocation",
    label: "Allocation",
    icon: "M12 3a9 9 0 1 0 9 9h-9zM15 3.5A9 9 0 0 1 20.5 9H15z",
  },
  { href: "/history", label: "History", icon: "M3 17l6-6 4 4 8-8M15 7h6v6" },
  {
    href: "/transactions",
    label: "Transactions",
    icon: "M7 4v16M7 4L3 8M7 4l4 4M17 20V4M17 20l-4-4M17 20l4-4",
  },
  { href: "/import", label: "Import", icon: "M12 3v12M12 15l-4-4M12 15l4-4M4 20h16" },
  {
    href: "/settings",
    label: "Settings",
    icon: "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19 12a7 7 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7 7 0 0 0-2-1.2L14 3h-4l-.5 2.6a7 7 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6a7 7 0 0 0 0 2.4l-2 1.6 2 3.4 2.4-1a7 7 0 0 0 2 1.2L10 21h4l.5-2.6a7 7 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6c.1-.4.1-.8.1-1.2z",
  },
];
