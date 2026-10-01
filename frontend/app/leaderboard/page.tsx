"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest, useAuth } from "../../lib/auth";
import { BrandMark } from "../../components/brand-mark";
import styles from "./leaderboard.module.css";

type Period = "week" | "month" | "all";
type Entry = { userId: number; displayName: string; username: string; avatarUrl?: string | null; avatarColor: string; acceptedCount: number; rank: number };
type Leaderboard = { period: Period; entries: Entry[]; totalAccepted: number; activeUsers: number; currentUserRank: number | null };

const labels: Record<Period, string> = { week: "本周", month: "本月", all: "总榜" };
const initials = (entry: Entry) => (entry.displayName || entry.username).trim().slice(0, 2).toUpperCase();

export default function LeaderboardPage() {
  const [period, setPeriod] = useState<Period>("week");
  const [data, setData] = useState<Leaderboard | null>(null);
  const [error, setError] = useState("");
  const user = useAuth((state) => state.user);

  useEffect(() => {
    let cancelled = false;
    setError("");
    setData(null);
    apiRequest<Leaderboard>(`/api/leaderboard?period=${period}`)
      .then((next) => { if (!cancelled) setData(next); })
      .catch((cause) => { if (!cancelled) setError(cause instanceof Error ? cause.message : "排行榜暂时无法读取"); });
    return () => { cancelled = true; };
  }, [period]);

  const entries = data?.entries ?? [];
  const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  return <main className={styles.page}>
    <header className={styles.topbar}>
      <Link className={styles.brand} href="/"><BrandMark />CodeAgent OJ</Link>
      <nav className={styles.nav} aria-label="主导航"><Link href="/">主页</Link><Link href="/problems">题库</Link><Link href="/leaderboard">排行榜</Link></nav>
      <Link className={styles.avatar} href={user ? "/profile" : "/login"} aria-label={user ? "个人资料" : "登录"}>{user ? (user.displayName || user.username).slice(0, 1).toUpperCase() : "·"}</Link>
    </header>
    <section className={styles.wrap}>
      <div className={styles.heading}><div><p className={styles.eyebrow}>Accepted leaderboard</p><h1>排行榜</h1></div><p>按 Accepted 题数排名，记录每一次真正完成的题目。</p></div>
      <div className={styles.tools}><div className={styles.tabs} role="tablist" aria-label="榜单周期">{(Object.keys(labels) as Period[]).map((item) => <button key={item} className={period === item ? styles.selected : ""} type="button" onClick={() => setPeriod(item)}>{labels[item]}</button>)}</div><span className={styles.sort}>SORTED BY / AC COUNT</span></div>
      {error ? <div className={styles.state}><strong>排行榜暂时不可用</strong><span>{error}</span></div> : !data ? <div className={styles.state}>正在整理最新排名…</div> : entries.length === 0 ? <div className={styles.state}><strong>榜单还没有数据</strong><span>完成第一道题后，你会出现在这里。</span></div> : <section className={styles.table} aria-label="Accepted 排行榜">
        <div className={styles.thead}><span>Rank</span><span>Coder</span><span>Accepted</span></div>
        {entries.map((entry, index) => <div className={`${styles.item} ${entry.rank === data.currentUserRank ? styles.mine : ""}`} key={entry.userId} style={{ "--row": index } as React.CSSProperties}><span className={`${styles.rank} ${entry.rank <= 3 ? styles.top : ""}`}>{String(entry.rank).padStart(2, "0")}</span><div className={styles.user}>{entry.avatarUrl ? <img className={styles.mark} src={`${apiBase}${entry.avatarUrl}`} alt={`${entry.displayName || entry.username} 的头像`} /> : <span className={`${styles.mark} ${styles[entry.avatarColor] ?? ""}`}>{initials(entry)}</span>}<div><div className={styles.name}>{entry.displayName || entry.username}</div><div className={styles.meta}>{entry.username}</div></div></div><span className={styles.count}>{entry.acceptedCount}</span></div>)}
      </section>}
      {data && <p className={styles.note}>{data.activeUsers} 位用户 · 只统计独立题目的 Accepted 结果</p>}
    </section>
  </main>;
}
