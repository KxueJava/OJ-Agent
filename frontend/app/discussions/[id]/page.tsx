"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiRequest, publicApiRequest, useAuth } from "../../../lib/auth";
import { useLanguage } from "../../../lib/i18n";
import { BrandMark } from "../../../components/brand-mark";
import SiteNav from "../../../components/site-nav";
import Markdown from "../../../components/markdown";
import styles from "../discussions.module.css";

/**
 * 帖子详情页：主题帖 + 楼层 + 回复 + 采纳。
 *
 * - 读取用 publicApiRequest（公开），写入用 apiRequest（带 token）；
 * - 正文与楼层都用共享的 Markdown 组件渲染（标题/列表/代码块/行内代码 ✓）；
 * - 「采纳」只在本人是楼主时显示（服务端仍会二次校验，越权返回 403）。
 */
type Post = { id: string; body: string; quotedPostId: string | null; upvotes: number; accepted: number | boolean; createdAt: string; authorName: string; authorUsername: string };
type Thread = {
  id: string; category: string; title: string; body: string; pinned: number | boolean; locked: number | boolean;
  views: number; replyCount: number; acceptedPostId: string | null; createdAt: string; lastReplyAt: string;
  authorName: string; authorUsername: string; problemSlug: string | null; problemTitle: string | null; posts: Post[];
};

const copy = {
  zh: {
    back: "返回讨论区", loading: "加载中…", notFound: "帖子不存在或已被删除", reply: "回复", replying: "发布中…",
    placeholder: "写下你的回复…支持 Markdown 与 ```java 代码块", accept: "采纳这条回复", accepted: "已采纳",
    signIn: "登录后才能回复", goLogin: "去登录", floor: (n: number) => `#${n} 楼`, owner: "楼主",
    views: (n: number) => `${n} 浏览`, replies: (n: number) => `${n} 回复`, locked: "该主题帖已锁定，不能再回复",
    useful: "有用", category: { SOLUTION: "题解", QUESTION: "提问", NOTICE: "公告", CHAT: "闲聊" } as Record<string, string>,
  },
  en: {
    back: "Back to discussions", loading: "Loading…", notFound: "Thread not found", reply: "Reply", replying: "Posting…",
    placeholder: "Write a reply… Markdown and ```java blocks supported", accept: "Accept this reply", accepted: "Accepted",
    signIn: "Sign in to reply", goLogin: "Sign in", floor: (n: number) => `#${n}`, owner: "author",
    views: (n: number) => `${n} views`, replies: (n: number) => `${n} replies`, locked: "This thread is locked",
    useful: "useful", category: { SOLUTION: "Solution", QUESTION: "Question", NOTICE: "Notice", CHAT: "Chat" } as Record<string, string>,
  },
} as const;

export default function DiscussionDetail({ params }: { params: Promise<{ id: string }> }) {
  const { language } = useLanguage();
  const text = copy[language === "en" ? "en" : "zh"];
  const token = useAuth((state) => state.accessToken);
  const me = useAuth((state) => state.user);

  const [id, setId] = useState("");
  const [thread, setThread] = useState<Thread | null>(null);
  const [error, setError] = useState("");
  const [body, setBody] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  useEffect(() => { params.then((resolved) => setId(resolved.id)); }, [params]);

  const load = useCallback(() => {
    if (!id) return;
    publicApiRequest<Thread>(`/api/discussions/${id}`)
      .then((data) => { setThread(data); setError(""); })
      .catch((cause) => { setThread(null); setError(cause instanceof Error ? cause.message : text.notFound); });
  }, [id, text.notFound]);

  useEffect(load, [load]);

  const reply = async () => {
    if (!token) { setNotice(text.signIn); return; }
    if (!body.trim()) { setNotice(language === "en" ? "Reply cannot be empty." : "回复内容不能为空。"); return; }
    setBusy(true); setNotice("");
    try {
      const updated = await apiRequest<Thread>(`/api/discussions/${id}/posts`, { method: "POST", body: JSON.stringify({ body }) });
      setThread(updated); setBody("");
    } catch (cause) { setNotice(cause instanceof Error ? cause.message : "发布失败"); }
    finally { setBusy(false); }
  };

  const accept = async (postId: string) => {
    try {
      const updated = await apiRequest<Thread>(`/api/discussions/${id}/posts/${postId}/accept`, { method: "POST" });
      setThread(updated);
    } catch (cause) { setNotice(cause instanceof Error ? cause.message : "操作失败"); }
  };

  const stamp = (value: string) => String(value).slice(0, 16).replace("T", " ");
  const isOwner = thread != null && me != null && thread.authorUsername === me.username;

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
        <p style={{ margin: "0 0 14px" }}><Link href="/discussions" style={{ color: "#d37737" }}>← {text.back}</Link></p>

        {error && <p className={styles.error}>{error}</p>}
        {notice && <p className={styles.error}>{notice}</p>}
        {!thread && !error && <p className={styles.empty}>{text.loading}</p>}

        {thread && (
          <>
            <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
              <span className={`${styles.badge} ${thread.category === "SOLUTION" ? styles.badgeSolution : thread.category === "QUESTION" ? styles.badgeQuestion : thread.category === "NOTICE" ? styles.badgeNotice : ""}`}>
                {text.category[thread.category] ?? thread.category}
              </span>
              {thread.acceptedPostId && <span className={`${styles.badge} ${styles.badgeAccepted}`}>{text.accepted}</span>}
              {(thread.locked === 1 || thread.locked === true) && <span className={styles.badge}>{language === "en" ? "Locked" : "已锁定"}</span>}
            </div>
            <h1 style={{ margin: "10px 0 6px" }}>{thread.title}</h1>
            <div className={styles.tMeta} style={{ marginBottom: 16 }}>
              <span style={{ color: "#3f4540" }}>{thread.authorName}</span><span>·</span><span>{stamp(thread.createdAt)}</span>
              <span>·</span><span>{text.views(thread.views)}</span><span>·</span><span>{text.replies(thread.replyCount)}</span>
              {thread.problemSlug && <Link className={styles.tag} href={`/problems/${thread.problemSlug}`}>{thread.problemSlug}</Link>}
            </div>

            {/* 楼主帖 */}
            <article style={{ border: "1px solid #d8d5ce", background: "#f7f5ef", padding: "16px 18px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10, paddingBottom: 10, borderBottom: "1px solid #e6e3db", color: "#858981", font: "10px Consolas,monospace" }}>
                <b style={{ color: "#252a2d", fontFamily: "inherit", fontSize: 12 }}>{thread.authorName}</b>
                <span>{stamp(thread.createdAt)}</span>
                <span style={{ marginLeft: "auto" }}>{text.owner} · {text.floor(1)}</span>
              </div>
              <div style={{ marginTop: 12 }}><Markdown text={thread.body} /></div>
            </article>

            {/* 楼层 */}
            {thread.posts.map((post, index) => (
              <article key={post.id} style={{ marginTop: 12, border: `1px solid ${post.accepted ? "#9fc4b1" : "#d8d5ce"}`, background: post.accepted ? "#eaf3ee" : "#f7f5ef", padding: "14px 18px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10, paddingBottom: 10, borderBottom: "1px solid #e6e3db", color: "#858981", font: "10px Consolas,monospace" }}>
                  <b style={{ color: "#252a2d", fontFamily: "inherit", fontSize: 12 }}>{post.authorName}</b>
                  <span>{stamp(post.createdAt)}</span>
                  {post.accepted && <span style={{ color: "#277456", fontWeight: 700 }}>{text.accepted}</span>}
                  <span style={{ marginLeft: "auto" }}>{text.floor(index + 2)}</span>
                </div>
                <div style={{ marginTop: 12 }}><Markdown text={post.body} /></div>
                <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 12, color: "#858981", font: "10px Consolas,monospace" }}>
                  <span>▲ {text.useful} {post.upvotes}</span>
                  {isOwner && !post.accepted && (
                    <button type="button" onClick={() => void accept(post.id)} className={styles.btn} style={{ height: 26, padding: "0 10px", font: "11px inherit" }}>{text.accept}</button>
                  )}
                </div>
              </article>
            ))}

            {/* 回复框 */}
            {(thread.locked === 1 || thread.locked === true)
              ? <p className={styles.error}>{text.locked}</p>
              : (
                <section className={styles.composer} style={{ marginTop: 16 }}>
                  <textarea value={body} onChange={(event) => setBody(event.target.value)} placeholder={text.placeholder} />
                  <div className={styles.composerFoot}>
                    <span>{token ? (language === "en" ? "Markdown supported" : "支持 Markdown 与代码块") : text.signIn}</span>
                    <button className={styles.btn} disabled={busy || !token} onClick={() => void reply()}>{busy ? text.replying : text.reply}</button>
                  </div>
                </section>
              )}
          </>
        )}
      </div>
    </main>
  );
}
