"use client";
import { useLanguage } from "../../lib/i18n";
import { useLeaderboardMessages } from "../../lib/messages/leaderboard";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest, useAuth } from "../../lib/auth";
import { BrandMark } from "../../components/brand-mark";
import styles from "./leaderboard.module.css";

type Period = "week" | "month" | "all";
type Entry = { userId: number; displayName: string; username: string; avatarUrl?: string | null; avatarColor: string; acceptedCount: number; rank: number };
type Leaderboard = { period: Period; entries: Entry[]; totalAccepted: number; activeUsers: number; currentUserRank: number | null };

const labels = { week: "leaderboard.period.week", month: "leaderboard.period.month", all: "leaderboard.period.all" } as const;
const initials = (entry: Entry) => (entry.displayName || entry.username).trim().slice(0, 2).toUpperCase();

export default function LeaderboardPage() {
  const { t } = useLanguage();
  const tp = useLeaderboardMessages();
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
      .catch((cause) => { if (!cancelled) setError(cause instanceof Error ? cause.message : tp("leaderboard.loadError")); });
    return () => { cancelled = true; };
  }, [period]);

  const entries = data?.entries ?? [];
  const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  return <main className={styles.page}>
    <header className={styles.topbar}>
      <Link className={styles.brand} href="/"><BrandMark />CodeAgent OJ</Link>
      <nav className={styles.nav} aria-label={tp("leaderboard.navAria")}><Link href="/">{t("nav.home")}</Link><Link href="/problems">{t("nav.problems")}</Link><Link href="/leaderboard">{t("nav.leaderboard")}</Link><Link href="/contests">{t("nav.contests")}</Link><Link href="/submissions">{t("nav.submissions")}</Link></nav>
      <Link className={styles.avatar} href={user ? "/profile" : "/login"} aria-label={user ? tp("leaderboard.profileAria") : tp("leaderboard.loginAria")}>{user ? (user.displayName || user.username).slice(0, 1).toUpperCase() : "·"}</Link>
    </header>
    <section className={styles.wrap}>
      <div className={styles.heading}><div><p className={styles.eyebrow}>Accepted leaderboard</p><h1>{tp("leaderboard.title")}</h1></div><p>{tp("leaderboard.subtitle")}</p></div>
      <div className={styles.tools}><div className={styles.tabs} role="tablist" aria-label={tp("leaderboard.periodAria")}>{(Object.keys(labels) as Period[]).map((item) => <button key={item} className={period === item ? styles.selected : ""} type="button" onClick={() => setPeriod(item)}>{tp(labels[item])}</button>)}</div><span className={styles.sort}>SORTED BY / AC COUNT</span></div>
      {error ? <div className={styles.state}><strong>{tp("leaderboard.unavailable")}</strong><span>{error}</span></div> : !data ? <div className={styles.state}>{tp("leaderboard.loading")}</div> : entries.length === 0 ? <div className={styles.state}><strong>{tp("leaderboard.emptyTitle")}</strong><span>{tp("leaderboard.emptyDesc")}</span></div> : <section className={styles.table} aria-label={tp("leaderboard.tableAria")}>
        <div className={styles.thead}><span>Rank</span><span>Coder</span><span>Accepted</span></div>
        {entries.map((entry, index) => <div className={`${styles.item} ${entry.rank === data.currentUserRank ? styles.mine : ""}`} key={entry.userId} style={{ "--row": index } as React.CSSProperties}><span className={`${styles.rank} ${entry.rank <= 3 ? styles.top : ""}`}>{String(entry.rank).padStart(2, "0")}</span><div className={styles.user}>{entry.avatarUrl ? <img className={styles.mark} src={`${apiBase}${entry.avatarUrl}`} alt={`${entry.displayName || entry.username}${tp("leaderboard.avatarAlt")}`} /> : <span className={`${styles.mark} ${styles[entry.avatarColor] ?? ""}`}>{initials(entry)}</span>}<div><div className={styles.name}>{entry.displayName || entry.username}</div><div className={styles.meta}>{entry.username}</div></div></div><span className={styles.count}>{entry.acceptedCount}</span></div>)}
      </section>}
      {data && <p className={styles.note}>{tp("leaderboard.activeUsersPrefix")}{String(data.activeUsers)}{tp("leaderboard.activeUsersSuffix")}{tp("leaderboard.noteSuffix")}</p>}
    </section>
  </main>;
}
