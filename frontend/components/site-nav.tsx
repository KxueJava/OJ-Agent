"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useLanguage } from "../lib/i18n";

/**
 * 全站共享导航（站点级组件）。
 *
 * 为什么需要它：此前每个页面各写一份内联导航（8 处重复 ✗），
 * 而且各页的 `t` 来自不同作用域 —— 想"统一加入口"时就会踩到
 * "这个页面的 t 不认识 nav.* 键"的坑（本会话真实踩过 ✗）。
 *
 * 本组件**自己**调用共享的 useLanguage()，因此与页面自身的文案作用域无关，
 * 任何页面都能安全使用；样式用内联写法，不依赖各页的 CSS Module。
 */
const items = [
  { href: "/", key: "nav.home" },
  { href: "/problems", key: "nav.problems" },
  { href: "/favorites", key: "nav.favorites" },
  { href: "/leaderboard", key: "nav.leaderboard" },
  { href: "/contests", key: "nav.contests" },
  { href: "/discussions", key: "nav.discussions" },
  { href: "/submissions", key: "nav.submissions" },
] as const;

export default function SiteNav() {
  const { t } = useLanguage();
  const pathname = usePathname() ?? "/";

  return (
    <nav style={{ display: "flex", gap: 18, fontSize: 13, flexWrap: "wrap" }}>
      {items.map((item) => {
        const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            style={{ color: active ? "#d37737" : "#858981", fontWeight: active ? 700 : 400, textDecoration: "none" }}
          >
            {t(item.key)}
          </Link>
        );
      })}
    </nav>
  );
}
