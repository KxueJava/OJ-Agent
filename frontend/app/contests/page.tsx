"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest, publicApiRequest, useAuth } from "../../lib/auth";
import { useContestMessages } from "../../lib/messages/contest";
import { useLanguage } from "../../lib/i18n";
import { BrandMark } from "../../components/brand-mark";
import styles from "./contest.module.css";

type ContestRow = {
  id: number; slug: string; title: string; mode: string; status: string;
  startAt: string; endAt: string; freezeMinutes: number; penaltyMinutes: number;
  problemCount: number; participantCount: number;
};

const statusClass: Record<string, string> = { RUNNING: styles["badge"], SCHEDULED: styles.badge, ENDED: styles.badge, FINALIZED: styles.badge };
const runningClass = `${styles.badge} ${styles.running}`;
const scheduledClass = `${styles.badge} ${styles.scheduled}`;
const finalizedClass = `${styles.badge} ${styles.finalized}`;

export default function ContestsPage() {
  const t = useContestMessages();
  const { t: nav } = useLanguage();
  const [items, setItems] = useState<ContestRow[] | null>(null);
  const [mine, setMine] = useState<{ id: string; slug: string; title: string; status: string; startAt: string; endAt: string; rankFinal: number | null; solvedFinal: number | null; penaltyFinal: number | null }[]>([]);
  const userId = useAuth((state) => state.user?.id);
  useEffect(() => {
    if (!userId) { setMine([]); return; }
    apiRequest<{ id: string; slug: string; title: string; status: string; startAt: string; endAt: string; rankFinal: number | null; solvedFinal: number | null; penaltyFinal: number | null }[]>("/api/contests/mine").then((list) => setMine(list ?? [])).catch(() => setMine([]));
  }, [userId]);
  const [filter, setFilter] = useState("ALL");
  const [error, setError] = useState("");

  useEffect(() => {
    publicApiRequest<ContestRow[]>("/api/contests?size=50")
      .then(setItems)
      .catch(() => setError(t("contest.error")));
  }, [t]);

  const statusText = (status: string) => {
    if (status === "SCHEDULED") return t("contest.status.scheduled");
    if (status === "RUNNING") return t("contest.status.running");
    if (status === "ENDED") return t("contest.status.ended");
    if (status === "FINALIZED") return t("contest.status.finalized");
    return status;
  };
  const statusStyle = (status: string) => {
    if (status === "RUNNING") return runningClass;
    if (status === "SCHEDULED") return scheduledClass;
    if (status === "FINALIZED") return finalizedClass;
    return statusClass[status] ?? styles.badge;
  };
  const duration = (row: ContestRow) => Math.max(0, Math.round((new Date(row.endAt).getTime() - new Date(row.startAt).getTime()) / 60000));
  const stamp = (value: string) => new Date(value).toLocaleString("zh-CN", { hour12: false, month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
  const visible = (items ?? []).filter((row) => filter === "ALL" || row.status === filter);
  const tabs = [
    { value: "ALL", label: t("contest.status.all") },
    { value: "SCHEDULED", label: t("contest.status.scheduled") },
    { value: "RUNNING", label: t("contest.status.running") },
    { value: "ENDED", label: t("contest.status.ended") },
    { value: "FINALIZED", label: t("contest.status.finalized") },
  ];

  return (
    <main className={styles.page}>
      <header className="app-topbar">
        <Link className="brand" href="/"><BrandMark />CodeAgent OJ</Link>
        <nav aria-label="主导航">
          <Link href="/">{nav("nav.home")}</Link>
          <Link href="/problems">{nav("nav.problems")}</Link>
          <Link href="/leaderboard">{nav("nav.leaderboard")}</Link>
          <Link href="/submissions">{nav("nav.submissions")}</Link>
        </nav>
      </header>

      <section className={styles.wrap}>
        <div className={styles.head}>
          <div>
            <p className={styles.eyebrow}>{t("contest.eyebrow")}</p>
            <h1>{t("contest.title")}</h1>
            <p>{t("contest.lead")}</p>
          </div>
          <div className={styles.tabs}>
            {tabs.map((tab) => (
              <button key={tab.value} type="button" className={filter === tab.value ? styles.on : undefined} onClick={() => setFilter(tab.value)}>{tab.label}</button>
            ))}
          </div>
        </div>

        {error && <p className={styles.state}>{error}</p>}
        {!items && !error && <p className={styles.state}>{t("contest.loading")}</p>}
        {items && visible.length === 0 && !error && <p className={styles.state}>{t("contest.empty")}</p>}

        <div className={styles.list}>
          {mine.length > 0 && (
            <section style={{ margin: "18px 0 20px", padding: "14px 16px", border: "1px solid #d8d5ce", background: "#f7f5ef" }}>
              <p style={{ margin: "0 0 10px", color: "#d37737", font: "10px Consolas,monospace", letterSpacing: ".13em", textTransform: "uppercase" }}>MY CONTESTS · 我参加的</p>
              <div style={{ display: "grid", gap: 8 }}>
                {mine.map((row) => (
                  <Link key={row.id} href={`/contests/${row.slug}`} style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 10, color: "#252a2d", textDecoration: "none" }}>
                    <strong style={{ fontSize: 13 }}>{row.title}</strong>
                    <span style={{ padding: "2px 7px", border: "1px solid #c9c7bf", color: "#5d625d", font: "10px Consolas,monospace" }}>{({ DRAFT: "草稿", SCHEDULED: "未开始", RUNNING: "进行中", ENDED: "已结束", FINALIZED: "已定榜", CANCELLED: "已取消" } as Record<string, string>)[row.status] ?? row.status}</span>
                    <small style={{ color: "#858981", font: "10px Consolas,monospace" }}>{String(row.startAt).slice(5, 16).replace("T", " ")} → {String(row.endAt).slice(5, 16).replace("T", " ")}</small>
                    {row.rankFinal != null && <small style={{ color: "#277456", font: "10px Consolas,monospace" }}>定榜 #{row.rankFinal} · {row.solvedFinal} 题 · {row.penaltyFinal}s</small>}
                  </Link>
                ))}
              </div>
            </section>
          )}
          {visible.map((row) => (
            <Link className={styles.card} key={row.slug} href={`/contests/${row.slug}`}>
              <div>
                <span className={statusStyle(row.status)}><i />{statusText(row.status)}</span>
                <b style={{ marginTop: 10 }}>{row.title}</b>
                <small>{stamp(row.startAt)} → {stamp(row.endAt)} · {row.mode}</small>
              </div>
              <div className={styles.metrics}>
                <span>{t("contest.meta.problems")}<strong>{row.problemCount}</strong></span>
                <span>{t("contest.meta.duration")}<strong>{t("contest.duration.minutes").replace("{n}", String(duration(row)))}</strong></span>
                <span>{t("contest.meta.rule")}<strong>{row.mode}</strong></span>
                <span>{t("contest.open")}<strong>→</strong></span>
              </div>
            </Link>
          ))}
        </div>
      </section>
    </main>
  );
}
