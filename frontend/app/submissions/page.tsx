"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest, useAuth } from "../../lib/auth";
import { useLanguage } from "../../lib/i18n";
import { useSubmissionsMessages } from "../../lib/messages/submissions";
import { BrandMark } from "../../components/brand-mark";
import styles from "./page.module.css";

type Item = {
  id: number; slug: string; title: string; language: string; status: string;
  verdictMessage?: string; runtimeMs?: number; memoryKb?: number;
  createdAt: string; finishedAt?: string; diagnosed: boolean;
};
type Page = { items: Item[]; page: number; size: number; total: number };

const terminal = new Set(["AC", "WA", "CE", "RE", "TLE", "MLE"]);

function time(value?: string) {
  if (!value) return "--";
  const date = new Date(value);
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export default function SubmissionsPage() {
  const token = useAuth((state) => state.accessToken);
  const { t: nav } = useLanguage();
  const t = useSubmissionsMessages();
  const [data, setData] = useState<Page | null>(null);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");

  const statusText: Record<string, string> = {
    PENDING: t("submissions.status.PENDING"), RUNNING: t("submissions.status.RUNNING"),
    AC: t("submissions.status.AC"), WA: t("submissions.status.WA"),
    CE: t("submissions.status.CE"), RE: t("submissions.status.RE"),
    TLE: t("submissions.status.TLE"), MLE: t("submissions.status.MLE"),
  };
  const filters = [
    { value: "", label: t("submissions.filter.all") },
    { value: "AC", label: t("submissions.filter.ac") },
    { value: "WA", label: t("submissions.filter.wa") },
    { value: "CE", label: t("submissions.filter.ce") },
    { value: "RE", label: t("submissions.filter.re") },
    { value: "TLE", label: t("submissions.filter.tle") },
  ];

  useEffect(() => {
    if (!token) return;
    const query = new URLSearchParams({ page: String(page), size: "20" });
    if (status) query.set("status", status);
    setError("");
    apiRequest<Page>(`/api/submissions?${query.toString()}`)
      .then(setData)
      .catch((cause) => setError(cause instanceof Error ? cause.message : t("submissions.loadFailed")));
  }, [token, page, status]);

  const pageCount = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <main className={styles.page}>
      <header className="app-topbar">
        <Link className="brand" href="/"><BrandMark />CodeAgent OJ</Link>
        <nav aria-label="主导航">
          <Link href="/">{nav("nav.home")}</Link>
          <Link href="/problems">{nav("nav.problems")}</Link>
          <Link href="/leaderboard">{nav("nav.leaderboard")}</Link>
          <Link href="/contests">{nav("nav.contests")}</Link>
          <Link className="active" href="/submissions">{nav("nav.submissions")}</Link>
        </nav>
      </header>

      <section className={styles.wrap}>
        <div className={styles.heading}>
          <div>
            <p className={styles.eyebrow}>{nav("submissions.eyebrow")}</p>
            <h1>{nav("submissions.title")}</h1>
            <p>{t("submissions.desc")}</p>
          </div>
          <div className={styles.filters}>
            {filters.map((item) => (
              <button key={item.value} type="button"
                className={status === item.value ? styles.filterActive : styles.filter}
                onClick={() => { setStatus(item.value); setPage(0); }}>{item.label}</button>
            ))}
          </div>
        </div>

        {!token && <p className={styles.state}><Link href="/login">{t("submissions.loginLink")}</Link>{t("submissions.loginSuffix")}</p>}
        {error && <p className={styles.state}>{error}</p>}
        {token && !data && !error && <p className={styles.state}>{t("submissions.loading")}</p>}
        {data && data.items.length === 0 && !error && <p className={styles.state}>{t("submissions.emptyPrefix")}<Link href="/problems">{t("submissions.emptyLink")}</Link>{t("submissions.emptySuffix")}</p>}

        {data && data.items.length > 0 && <>
          <div className={styles.table}>
            <div className={styles.head}>
              <span>{t("submissions.table.problem")}</span><span>{t("submissions.table.language")}</span><span>{t("submissions.table.status")}</span><span>{t("submissions.table.time")}</span><span>{t("submissions.table.memory")}</span><span>{t("submissions.table.createdAt")}</span><span>{t("submissions.table.diagnosis")}</span>
            </div>
            {data.items.map((item) => (
              <Link className={styles.row} key={item.id} href={`/submissions/${item.id}`}>
                <span className={styles.title}>{item.title}</span>
                <span className={styles.mono}>{item.language.replace("_", " ")}</span>
                <span className={styles[`s_${terminal.has(item.status) ? item.status : "PENDING"}`] ?? styles.mono}>
                  {statusText[item.status] ?? item.status}
                </span>
                <span className={styles.mono}>{item.runtimeMs == null ? "--" : `${item.runtimeMs}${t("submissions.unit.ms")}`}</span>
                <span className={styles.mono}>{item.memoryKb == null ? "--" : `${item.memoryKb}${t("submissions.unit.kb")}`}</span>
                <span className={styles.mono}>{time(item.createdAt)}</span>
                <span className={styles.diagnosed}>{item.diagnosed ? t("submissions.table.diagnosed") : "—"}</span>
              </Link>
            ))}
          </div>

          <div className={styles.pager}>
            <span>{t("submissions.pager.prefix")}{data.total}{t("submissions.pager.middle")}{data.page + 1}/{pageCount}{t("submissions.pager.suffix")}</span>
            <div>
              <button type="button" disabled={data.page <= 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>{t("submissions.pager.prev")}</button>
              <button type="button" disabled={data.page + 1 >= pageCount} onClick={() => setPage((value) => value + 1)}>{t("submissions.pager.next")}</button>
            </div>
          </div>
        </>}
      </section>
    </main>
  );
}
