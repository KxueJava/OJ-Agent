"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiRequest, publicApiRequest, useAuth } from "../../lib/auth";
import { useLanguage } from "../../lib/i18n";
import { BrandMark } from "../../components/brand-mark";
import SiteNav from "../../components/site-nav";
import styles from "./discussions.module.css";

/**
 * 讨论区列表页（阶段三前端）。
 *
 * - 读用 publicApiRequest（列表/详情是公开的，不含 per-user 字段 —— 避免"读了却没带 token"那类坑）；
 * - 写用 apiRequest（带 token）；发帖成功后重新拉列表；
 * - 赛中禁止讨论赛题的判定在服务端（403），这里只负责把 detail 显示出来。
 */
type Thread = {
  id: string; category: string; title: string; pinned: number | boolean; locked: number | boolean;
  views: number; replyCount: number; acceptedPostId: string | null; createdAt: string; lastReplyAt: string;
  authorName: string; problemSlug: string | null; problemTitle: string | null;
};

const copy = {
  zh: {
    kicker: "DISCUSSION", title: "讨论区",
    lead: "提问、题解与公告都在这里。发帖可关联题目；比赛进行中的赛题不允许讨论（服务端强制）。",
    all: "全部", solution: "题解", question: "提问", notice: "公告", chat: "闲聊",
    search: "搜索标题、正文或题目 slug", sortLatest: "最新回复", sortNew: "最新发布", sortHot: "最多浏览", sortUnanswered: "未回复优先",
    newThread: "发布新帖", cancel: "取消", composerTitle: "发布新帖",
    phTitle: "标题（100 字内）", phBody: "正文，支持 Markdown 与 ```java 代码块", phProblem: "关联题目 slug（可空，如 two-sum）",
    submit: "发布", submitting: "发布中…", loading: "加载中…", empty: "还没有帖子，来发第一帖。",
    prev: "上一页", next: "下一页", pinned: "置顶", lockedBadge: "已锁定", acceptedBadge: "已解决",
    replies: (n: number) => `${n} 回复`, views: (n: number) => `${n} 浏览`, blocked: "发布",
    signIn: "登录后即可发帖、回复。", goLogin: "去登录", rules: "发帖须知",
    rule1: "题解请写清思路来源，不要只贴代码。", rule2: "提问请附语言、错误信息与题目链接。",
    rule3: "比赛进行中的赛题不允许讨论（服务端会拒绝）。", rule4: "禁止灌水与人身攻击。", hotTags: "热门标签",
  },
  en: {
    kicker: "DISCUSSION", title: "Discussion",
    lead: "Questions, solutions and notices. Threads can link a problem; problems in a running contest cannot be discussed (enforced server-side).",
    all: "All", solution: "Solution", question: "Question", notice: "Notice", chat: "Chat",
    search: "Search title, body or problem slug", sortLatest: "Latest reply", sortNew: "Newest", sortHot: "Most viewed", sortUnanswered: "Unanswered first",
    newThread: "New thread", cancel: "Cancel", composerTitle: "New thread",
    phTitle: "Title", phBody: "Body — Markdown and ```java blocks supported", phProblem: "Linked problem slug (optional)",
    submit: "Post", submitting: "Posting…", loading: "Loading…", empty: "No threads yet — be the first.",
    prev: "Prev", next: "Next", pinned: "Pinned", lockedBadge: "Locked", acceptedBadge: "Solved",
    replies: (n: number) => `${n} replies`, views: (n: number) => `${n} views`, blocked: "Post",
    signIn: "Sign in to post or reply.", goLogin: "Sign in", rules: "Posting rules",
    rule1: "Explain the idea, not just code.", rule2: "Include language, error message and problem link.",
    rule3: "Problems in a running contest cannot be discussed.", rule4: "No spam or personal attacks.", hotTags: "Hot tags",
  },
} as const;

const categories = ["SOLUTION", "QUESTION", "NOTICE", "CHAT"] as const;

export default function DiscussionsPage() {
  const { language } = useLanguage();
  const text = copy[language === "en" ? "en" : "zh"];
  const token = useAuth((state) => state.accessToken);

  const [category, setCategory] = useState("");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("latest");
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<Thread[] | null>(null);
  const [error, setError] = useState("");
  const [composing, setComposing] = useState(false);
  const [draft, setDraft] = useState({ category: "QUESTION", title: "", body: "", problemSlug: "" });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  const pageSize = 20;

  const load = useCallback(() => {
    const params = new URLSearchParams({ page: String(page), size: String(pageSize), sort });
    if (category) params.set("category", category);
    if (query.trim()) params.set("query", query.trim());
    setError("");
    publicApiRequest<Thread[]>(`/api/discussions?${params.toString()}`)
      .then((rows) => setItems(rows ?? []))
      .catch((cause) => { setItems([]); setError(cause instanceof Error ? cause.message : "加载失败"); });
  }, [category, query, sort, page]);

  useEffect(load, [load]);

  const submit = async () => {
    if (!token) { setNotice(text.signIn); return; }
    if (!draft.title.trim() || !draft.body.trim()) { setNotice(language === "en" ? "Title and body are required." : "标题和正文都要填。"); return; }
    setBusy(true); setNotice("");
    try {
      await apiRequest("/api/discussions", {
        method: "POST",
        body: JSON.stringify({ category: draft.category, title: draft.title, body: draft.body, problemSlug: draft.problemSlug.trim() || null }),
      });
      setDraft({ category: "QUESTION", title: "", body: "", problemSlug: "" });
      setComposing(false);
      setPage(0);
      load();
    } catch (cause) { setNotice(cause instanceof Error ? cause.message : "发布失败"); }
    finally { setBusy(false); }
  };

  const badgeClass = (value: string) => value === "SOLUTION" ? styles.badgeSolution
    : value === "QUESTION" ? styles.badgeQuestion : value === "NOTICE" ? styles.badgeNotice : styles.badge;
  const categoryLabel = (value: string) => value === "SOLUTION" ? text.solution
    : value === "QUESTION" ? text.question : value === "NOTICE" ? text.notice : text.chat;
  const stamp = (value: string) => String(value).slice(0, 16).replace("T", " ");

  return (
    <main className={styles.page}>
      <header className={styles.topbar}>
        <Link className={styles.brand} href="/"><BrandMark />CodeAgent OJ</Link>
        <SiteNav />
        <div className={styles.topRight}>
          {token ? <span>{language === "en" ? "Signed in" : "已登录"}</span> : <Link href="/login" style={{ color: "#d37737" }}>{text.goLogin}</Link>}
        </div>
      </header>

      <div className={styles.wrap}>
        <p className={styles.kicker}>{text.kicker}</p>
        <h1>{text.title}</h1>
        <p className={styles.lead}>{text.lead}</p>

        <div className={styles.toolbar}>
          <div className={styles.tabs}>
            <button className={category === "" ? styles.on : ""} onClick={() => { setCategory(""); setPage(0); }}>{text.all}</button>
            {categories.map((item) => (
              <button key={item} className={category === item ? styles.on : ""} onClick={() => { setCategory(item); setPage(0); }}>{categoryLabel(item)}</button>
            ))}
          </div>
          <label className={styles.search}>
            <span style={{ fontFamily: "Consolas,monospace" }}>⌕</span>
            <input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0); }} placeholder={text.search} />
          </label>
          <select className={styles.sort} value={sort} onChange={(event) => { setSort(event.target.value); setPage(0); }}>
            <option value="latest">{text.sortLatest}</option>
            <option value="new">{text.sortNew}</option>
            <option value="hot">{text.sortHot}</option>
            <option value="unanswered">{text.sortUnanswered}</option>
          </select>
          <button className={styles.btn} onClick={() => setComposing((value) => !value)}>{composing ? text.cancel : text.newThread}</button>
        </div>

        {!token && <p className={styles.error}>{text.signIn} <Link href="/login" style={{ color: "#d37737" }}>{text.goLogin}</Link></p>}
        {notice && <p className={styles.error}>{notice}</p>}
        {error && <p className={styles.error}>{error}</p>}

        {composing && (
          <section className={styles.composer}>
            <h3>{text.composerTitle}</h3>
            <div className={styles.composerRow}>
              <select value={draft.category} onChange={(event) => setDraft({ ...draft, category: event.target.value })}>
                {categories.map((item) => <option key={item} value={item}>{categoryLabel(item)}</option>)}
              </select>
              <input value={draft.title} maxLength={100} onChange={(event) => setDraft({ ...draft, title: event.target.value })} placeholder={text.phTitle} />
              <input value={draft.problemSlug} onChange={(event) => setDraft({ ...draft, problemSlug: event.target.value })} placeholder={text.phProblem} style={{ maxWidth: 260 }} />
            </div>
            <textarea value={draft.body} onChange={(event) => setDraft({ ...draft, body: event.target.value })} placeholder={text.phBody} />
            <div className={styles.composerFoot}>
              <span>{language === "en" ? "Markdown supported" : "支持 Markdown 与代码块"}</span>
              <button className={styles.btn} disabled={busy} onClick={() => void submit()}>{busy ? text.submitting : text.submit}</button>
            </div>
          </section>
        )}

        <div className={styles.grid} style={{ marginTop: 18 }}>
          <section>
            {items === null && <p className={styles.empty}>{text.loading}</p>}
            {items !== null && items.length === 0 && <p className={styles.empty}>{text.empty}</p>}
            {items !== null && items.length > 0 && (
              <div className={styles.threads}>
                {items.map((row) => (
                  <article className={styles.thread} key={row.id}>
                    <div>
                      <div className={styles.tHead}>
                        {(row.pinned === 1 || row.pinned === true) && <span className={`${styles.badge} ${styles.badgePin}`}>{text.pinned}</span>}
                        <span className={`${styles.badge} ${badgeClass(row.category)}`}>{categoryLabel(row.category)}</span>
                        <Link className={styles.tTitle} href={`/discussions/${row.id}`}>{row.title}</Link>
                        {row.acceptedPostId && <span className={`${styles.badge} ${styles.badgeAccepted}`}>{text.acceptedBadge}</span>}
                        {(row.locked === 1 || row.locked === true) && <span className={styles.badge}>{text.lockedBadge}</span>}
                      </div>
                      <div className={styles.tMeta}>
                        <span>{row.authorName}</span><span>·</span><span>{stamp(row.createdAt)}</span>
                        {row.problemSlug && <span className={styles.tag}>{row.problemSlug}</span>}
                      </div>
                    </div>
                    <div className={styles.tStats}>
                      <b>{row.views}</b> {language === "en" ? "views" : "浏览"}<br />
                      {text.replies(row.replyCount)} · {stamp(row.lastReplyAt).slice(5)}
                    </div>
                  </article>
                ))}
              </div>
            )}

            <div className={styles.pager}>
              <button disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>{text.prev}</button>
              <span>{page + 1}</span>
              <button disabled={(items?.length ?? 0) < pageSize} onClick={() => setPage((value) => value + 1)}>{text.next}</button>
            </div>
          </section>

          <aside className={styles.rail}>
            <section className={styles.card}>
              <h3>{text.hotTags}</h3>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                <span className={styles.k}>hash-table</span><span className={styles.k}>binary-search</span>
                <span className={styles.k}>dynamic-programming</span><span className={styles.k}>greedy</span>
                <span className={styles.k}>graph</span><span className={styles.k}>math</span>
              </div>
            </section>
            <section className={styles.card}>
              <h3>{text.rules}</h3>
              <ul className={styles.rules}>
                <li>{text.rule1}</li><li>{text.rule2}</li><li>{text.rule3}</li><li>{text.rule4}</li>
              </ul>
            </section>
          </aside>
        </div>
      </div>
    </main>
  );
}
