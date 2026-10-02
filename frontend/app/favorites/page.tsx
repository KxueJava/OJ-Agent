"use client";

import Link from "next/link";
import SiteNav from "../../components/site-nav";
import { useEffect, useState } from "react";
import { Star } from "lucide-react";
import { apiRequest, useAuth } from "../../lib/auth";
import { useLanguage } from "../../lib/i18n";
import { BrandMark } from "../../components/brand-mark";

/**
 * 收藏页（独立于题库）：
 * - 只列我收藏的题，带 AC 徽章与标签；
 * - 星标是"取消收藏"按钮，点了**直接从列表移除**（不像题库页那样只变空心星）；
 * - 未登录时给出登录引导，而不是空列表。
 * 复用 globals.css 里题库那套 catalog 样式，避免再维护一份 CSS。
 */
type Problem = { slug: string; title: string; difficulty: "EASY" | "MEDIUM" | "HARD"; tags: { slug: string; name: string }[]; favorite: boolean };

const copy = {
  zh: {
    kicker: "FAVORITES", title: "收藏", subtitle: "你标记过的题目都在这里，方便回来看第二遍。",
    empty: "还没有收藏的题目。", toCatalog: "去题库挑几道 →", remove: "取消收藏",
    loading: "加载中…", signIn: "登录后就能看到你的收藏。", goLogin: "去登录", count: (n: number) => `共 ${n} 道`,
    difficulty: { EASY: "简单", MEDIUM: "中等", HARD: "困难" } as Record<string, string>,
  },
  en: {
    kicker: "FAVORITES", title: "Favorites", subtitle: "Problems you starred, kept in one place.",
    empty: "No favorites yet.", toCatalog: "Browse the catalog →", remove: "Remove",
    loading: "Loading…", signIn: "Sign in to see your favorites.", goLogin: "Sign in", count: (n: number) => `${n} problems`,
    difficulty: { EASY: "Easy", MEDIUM: "Medium", HARD: "Hard" } as Record<string, string>,
  },
} as const;

export default function FavoritesPage() {
  const { language } = useLanguage();
  const text = copy[language === "en" ? "en" : "zh"];
  const token = useAuth((state) => state.accessToken);
  const [items, setItems] = useState<Problem[] | null>(null);
  const [error, setError] = useState("");
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    if (!token) { setItems([]); return; }
    apiRequest<{ items: Problem[] }>("/api/problems?favoriteOnly=true&size=50")
      .then((page) => { setItems(page.items ?? []); setError(""); })
      .catch((cause) => { setItems([]); setError(cause instanceof Error ? cause.message : "请求失败"); });
  }, [token, nonce]);

  /** 取消收藏：先本地移除该行（即时反馈），失败则重拉列表纠正。 */
  const remove = async (slug: string) => {
    setItems((list) => (list ?? []).filter((item) => item.slug !== slug));
    try { await apiRequest(`/api/problems/${slug}/favorite?enabled=false`, { method: "PUT" }); }
    catch { setNonce((value) => value + 1); }
  };

  const signedOut = !token;

  return (
    <main className="catalog">
      <header className="app-topbar">
        <Link className="brand" href="/"><BrandMark />CodeAgent OJ</Link>
        <SiteNav />
        <Link className="top-login" href="/workspace">{language === "en" ? "Workspace" : "我的工作台"}</Link>
      </header>

      <section className="catalog-heading">
        <div>
          <p className="eyebrow">{text.kicker}</p>
          <h1>{text.title}</h1>
          <p>{text.subtitle}</p>
        </div>
      </section>

      {error && <p className="catalog-error">{error}</p>}

      {signedOut && <p className="catalog-error">{text.signIn} <Link href="/login" style={{ color: "#d37737", textDecoration: "underline" }}>{text.goLogin}</Link></p>}

      {!signedOut && items === null && <p className="catalog-error">{text.loading}</p>}

      {!signedOut && items !== null && items.length === 0 && (
        <p className="catalog-error">{text.empty} <Link href="/problems" style={{ color: "#d37737", textDecoration: "underline" }}>{text.toCatalog}</Link></p>
      )}

      {!signedOut && items !== null && items.length > 0 && (
        <section className="problem-table">
          <div className="problem-table-head">
            <span>{language === "en" ? "Problem" : "题目"}</span>
            <span>{language === "en" ? "Difficulty" : "难度"}</span>
            <span>{language === "en" ? "Tags" : "标签"}</span>
            <span>{text.count(items.length)}</span>
          </div>
          {items.map((item) => (
            <div className="catalog-row" key={item.slug}>
              <Link href={`/problems/${item.slug}`} style={{ color: "inherit", textDecoration: "none" }}><strong>{item.title}</strong></Link>
              <span className={`difficulty ${item.difficulty.toLowerCase()}`}>{text.difficulty[item.difficulty] ?? item.difficulty}</span>
              <span className="catalog-tags">{item.tags.map((tag) => <i key={tag.slug}>{tag.name}</i>)}</span>
              <button
                type="button"
                onClick={() => void remove(item.slug)}
                aria-label={text.remove}
                title={text.remove}
                style={{ border: 0, background: "transparent", padding: 0, cursor: "pointer", color: "#d37737", justifySelf: "end" }}
              >
                <Star size={16} fill="currentColor" />
              </button>
            </div>
          ))}
        </section>
      )}
    </main>
  );
}
