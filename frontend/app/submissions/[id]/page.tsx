"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiRequest, useAuth } from "../../../lib/auth";
import { useLanguage } from "../../../lib/i18n";
import { useSubmissionsMessages } from "../../../lib/messages/submissions";
import { BrandMark } from "../../../components/brand-mark";
import Markdown from "../../../components/markdown";
import styles from "./page.module.css";

type Summary = { id: number; status: string; verdictMessage?: string; runtimeMs?: number; memoryKb?: number; createdAt: string; finishedAt?: string; compileMs?: number; failureKind?: string; rejudgeCount?: number };
type CaseSummary = { order: number; verdict: string; runtimeMs?: number; memoryKb?: number; outputSummary?: string };
type Detail = { submission: Summary; cases: CaseSummary[]; language: string; sourceCode: string; slug: string };
type Finding = { file?: string; line?: number; severity?: string; message?: string; suggestion?: string };
type Diagnosis = { available: boolean; status: string; content: string; safetyStatus: string; findingsJson?: string; traceJson?: string; reason?: string };

const terminal = new Set(["AC", "WA", "CE", "RE", "TLE", "MLE"]);

function parseFindings(raw?: string): Finding[] {
  if (!raw) return [];
  try { const value = JSON.parse(raw); return Array.isArray(value) ? (value as Finding[]) : []; } catch { return []; }
}
function parseTrace(raw?: string): string[] {
  if (!raw) return [];
  try { const value = JSON.parse(raw); return Array.isArray(value) ? value.map(String) : []; } catch { return []; }
}
function time(value?: string) {
  if (!value) return "--";
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

export default function SubmissionDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { t: nav } = useLanguage();
  const t = useSubmissionsMessages();
  const token = useAuth((state) => state.accessToken);
  const [id, setId] = useState("");
  const [detail, setDetail] = useState<Detail | null>(null);
  const [diagnosis, setDiagnosis] = useState<Diagnosis | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const statusText: Record<string, string> = {
    PENDING: t("submissions.status.PENDING"), RUNNING: t("submissions.status.RUNNING"),
    AC: t("submissions.status.AC"), WA: t("submissions.status.WA"),
    CE: t("submissions.status.CE"), RE: t("submissions.status.RE"),
    TLE: t("submissions.status.TLE"), MLE: t("submissions.status.MLE"),
  };

  useEffect(() => { params.then((value) => setId(value.id)); }, [params]);

  useEffect(() => {
    if (!id || !token) return;
    setError("");
    apiRequest<Detail>(`/api/submissions/${id}`)
      .then(setDetail)
      .catch((cause) => setError(cause instanceof Error ? cause.message : t("submissionDetail.loadFailed")));
  }, [id, token]);

  // 非 AC 且还没有诊断时轮询，最多 30 秒；拿到 reason 也提前结束，避免"干等"
  useEffect(() => {
    if (!detail || !token) return;
    const status = detail.submission.status;
    if (status === "AC" || !terminal.has(status)) return;
    let cancelled = false;
    let tries = 0;
    const tick = async () => {
      if (cancelled) return;
      try {
        const view = await apiRequest<Diagnosis>(`/api/agent/submissions/${id}/diagnosis`);
        if (cancelled) return;
        setDiagnosis(view);
        if (view.available || view.status === "NONE") return;
      } catch { /* 诊断不可用不影响详情页 */ }
      if (++tries < 15) setTimeout(tick, 2000);
    };
    tick();
    return () => { cancelled = true; };
  }, [detail, id, token]);

  const findings = parseFindings(diagnosis?.findingsJson);
  const trace = parseTrace(diagnosis?.traceJson);
  const failureKind = detail?.submission.failureKind;
  const severityHint = failureKind === "INFRA"
    ? t("submissionDetail.hint.infra")
    : failureKind === "INTERNAL"
      ? t("submissionDetail.hint.internal")
      : null;

  const reload = useCallback(async () => { const next = await apiRequest<Detail>(`/api/submissions/${id}`); setDetail(next); return next; }, [id]);
  const rejudge = async () => {
    setBusy(true); setError(""); setDiagnosis(null);
    try {
      await apiRequest(`/api/submissions/${id}/rejudge`, { method: "POST" });
      setDetail((current) => current ? { ...current, submission: { ...current.submission, status: "PENDING", verdictMessage: t("submissionDetail.rejudgeQueued") } } : current);
      let next = await reload();
      for (let attempt = 0; attempt < 60 && !terminal.has(next.submission.status); attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        next = await reload();
      }
    } catch (cause) { setError(cause instanceof Error ? cause.message : t("submissionDetail.rejudgeFailed")); }
    finally { setBusy(false); }
  };

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
        {!token && <p className={styles.state}><Link href="/login">{t("submissions.loginLink")}</Link>{t("submissionDetail.loginSuffix")}</p>}
        {error && <p className={styles.state}>{error}</p>}
        {token && !detail && !error && <p className={styles.state}>{t("submissionDetail.loading")}</p>}

        {detail && <>
          <div className={styles.breadcrumb}>
            <Link href="/submissions">{t("submissionDetail.back")}</Link>
            <span>#{detail.submission.id}</span>
          </div>

          <div className={styles.summary}>
            <div>
              <p className={styles.eyebrow}>VERDICT</p>
              <h1 className={terminal.has(detail.submission.status) ? styles[`v_${detail.submission.status}`] : styles.v_PENDING}>
                {statusText[detail.submission.status] ?? detail.submission.status}
              </h1>
              <p className={styles.message}>{detail.submission.verdictMessage ?? t("submissionDetail.verdictPending")}</p>
              {severityHint && <p className={styles.severity}>{severityHint}</p>}
            </div>
            <dl className={styles.metrics}>
              <div><dt>{t("submissionDetail.metric.language")}</dt><dd>{detail.language.replace("_", " ")}</dd></div>
              <div><dt>{t("submissionDetail.metric.compileMs")}</dt><dd>{detail.submission.compileMs == null ? "--" : `${detail.submission.compileMs}${t("submissions.unit.ms")}`}</dd></div>
              <div><dt>{t("submissionDetail.metric.runtimeMs")}</dt><dd>{detail.submission.runtimeMs == null ? "--" : `${detail.submission.runtimeMs}${t("submissions.unit.ms")}`}</dd></div>
              <div><dt>{t("submissionDetail.metric.memoryKb")}</dt><dd>{detail.submission.memoryKb == null ? "--" : `${detail.submission.memoryKb}${t("submissions.unit.kb")}`}</dd></div>
              <div><dt>{t("submissionDetail.metric.rejudgeCount")}</dt><dd>{detail.submission.rejudgeCount ?? 0}</dd></div>
              <div><dt>{t("submissionDetail.metric.createdAt")}</dt><dd>{time(detail.submission.createdAt)}</dd></div>
            </dl>
          </div>

          <section className={styles.block}>
            <h2>{t("submissionDetail.cases.title")}</h2>
            {detail.cases.length === 0
              ? <p className={styles.state}>{t("submissionDetail.cases.empty")}</p>
              : <div className={styles.cases}>
                  {detail.cases.map((item) => (
                    <div key={item.order}>
                      <span>{t("submissionDetail.cases.itemPrefix")}{item.order}</span>
                      <b className={item.verdict === "AC" ? styles.pass : styles.fail}>{item.verdict}</b>
                      <small>{item.runtimeMs == null ? "" : `${item.runtimeMs}${t("submissions.unit.ms")}`}{item.outputSummary ? ` · ${item.outputSummary}` : ""}</small>
                    </div>
                  ))}
                </div>}
            <p className={styles.note}>{t("submissionDetail.cases.note")}</p>
          </section>

          <section className={styles.block}>
            <h2>{t("submissionDetail.diagnosis.title")}</h2>
            {findings.length > 0
              ? <div className={styles.findings}>
                  {findings.map((finding, index) => (
                    <div className={styles.finding} key={index}>
                      <b>{finding.file ?? t("submissionDetail.finding.codeFallback")}{finding.line ? `:${finding.line}` : ""}</b>
                      <span>{finding.message}{finding.suggestion ? ` → ${finding.suggestion}` : ""}</span>
                    </div>
                  ))}
                </div>
              : <div className={styles.content}><Markdown text={diagnosis?.content ?? t("submissionDetail.diagnosis.pending")} /></div>}
            {diagnosis && !diagnosis.available && diagnosis.reason && <p className={styles.note}>{diagnosis.reason}</p>}
            {diagnosis?.available && !findings.length && diagnosis.reason && <p className={styles.note}>{diagnosis.reason}</p>}
            {trace.length > 1 && <p className={styles.trace}>{trace.join(" → ")}</p>}
            {diagnosis?.available && <p className={styles.note}>
              {diagnosis.safetyStatus === "FALLBACK" ? t("submissionDetail.diagnosis.sourceFallback") : t("submissionDetail.diagnosis.sourcePipeline")}
            </p>}
          </section>

          <section className={styles.block}>
            <h2>{t("submissionDetail.code.title")}</h2>
            <pre className={styles.code}>{detail.sourceCode}</pre>
          </section>

          <div className={styles.actions}>
            <Link className={styles.primary} href="/submissions">{t("submissionDetail.actions.back")}</Link>
            <button type="button" className={styles.ghost} onClick={rejudge} disabled={busy}>{busy ? t("submissionDetail.actions.rejudging") : t("submissionDetail.actions.rejudge")}</button>
            <Link className={styles.ghost} href={`/workbench/${detail.slug}`}>{t("submissionDetail.actions.workbench")}</Link>
            <Link className={styles.ghost} href={`/problems/${detail.slug}`}>{t("submissionDetail.actions.problem")}</Link>
          </div>
        </>}
      </section>
    </main>
  );
}
