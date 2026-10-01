"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiRequest, useAuth } from "../../../lib/auth";
import { BrandMark } from "../../../components/brand-mark";
import styles from "./page.module.css";

type Summary = { id: number; status: string; verdictMessage?: string; runtimeMs?: number; memoryKb?: number; createdAt: string; finishedAt?: string; compileMs?: number; failureKind?: string; rejudgeCount?: number };
type CaseSummary = { order: number; verdict: string; runtimeMs?: number; memoryKb?: number; outputSummary?: string };
type Detail = { submission: Summary; cases: CaseSummary[]; language: string; sourceCode: string; slug: string };
type Finding = { file?: string; line?: number; severity?: string; message?: string; suggestion?: string };
type Diagnosis = { available: boolean; status: string; content: string; safetyStatus: string; findingsJson?: string; traceJson?: string; reason?: string };

const statusText: Record<string, string> = {
  PENDING: "排队中", RUNNING: "判题中", AC: "通过", WA: "答案错误",
  CE: "编译错误", RE: "运行错误", TLE: "超出时间", MLE: "超出内存",
};
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
  const token = useAuth((state) => state.accessToken);
  const [id, setId] = useState("");
  const [detail, setDetail] = useState<Detail | null>(null);
  const [diagnosis, setDiagnosis] = useState<Diagnosis | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => { params.then((value) => setId(value.id)); }, [params]);

  useEffect(() => {
    if (!id || !token) return;
    setError("");
    apiRequest<Detail>(`/api/submissions/${id}`)
      .then(setDetail)
      .catch((cause) => setError(cause instanceof Error ? cause.message : "加载提交详情失败"));
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
    ? "这是判题基础设施的问题（沙箱不可用），不是你代码的错误 —— 可以用下面的「重新判题」再跑一次。"
    : failureKind === "INTERNAL"
      ? "这是判题器内部异常，不是你代码的错误 —— 请稍后重新判题。"
      : null;

  const reload = useCallback(async () => { const next = await apiRequest<Detail>(`/api/submissions/${id}`); setDetail(next); return next; }, [id]);
  const rejudge = async () => {
    setBusy(true); setError(""); setDiagnosis(null);
    try {
      await apiRequest(`/api/submissions/${id}/rejudge`, { method: "POST" });
      setDetail((current) => current ? { ...current, submission: { ...current.submission, status: "PENDING", verdictMessage: "已重新提交判题队列" } } : current);
      let next = await reload();
      for (let attempt = 0; attempt < 60 && !terminal.has(next.submission.status); attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        next = await reload();
      }
    } catch (cause) { setError(cause instanceof Error ? cause.message : "重新判题失败"); }
    finally { setBusy(false); }
  };

  return (
    <main className={styles.page}>
      <header className="app-topbar">
        <Link className="brand" href="/"><BrandMark />CodeAgent OJ</Link>
        <nav aria-label="主导航">
          <Link href="/">主页</Link>
          <Link href="/problems">题库</Link>
          <Link href="/leaderboard">排行榜</Link>
          <Link className="active" href="/submissions">提交记录</Link>
        </nav>
      </header>

      <section className={styles.wrap}>
        {!token && <p className={styles.state}><Link href="/login">登录</Link>后查看提交详情。</p>}
        {error && <p className={styles.state}>{error}</p>}
        {token && !detail && !error && <p className={styles.state}>正在加载…</p>}

        {detail && <>
          <div className={styles.breadcrumb}>
            <Link href="/submissions">← 提交记录</Link>
            <span>#{detail.submission.id}</span>
          </div>

          <div className={styles.summary}>
            <div>
              <p className={styles.eyebrow}>VERDICT</p>
              <h1 className={terminal.has(detail.submission.status) ? styles[`v_${detail.submission.status}`] : styles.v_PENDING}>
                {statusText[detail.submission.status] ?? detail.submission.status}
              </h1>
              <p className={styles.message}>{detail.submission.verdictMessage ?? "判题队列已接收，等待结果"}</p>
              {severityHint && <p className={styles.severity}>{severityHint}</p>}
            </div>
            <dl className={styles.metrics}>
              <div><dt>语言</dt><dd>{detail.language.replace("_", " ")}</dd></div>
              <div><dt>编译耗时</dt><dd>{detail.submission.compileMs == null ? "--" : `${detail.submission.compileMs} ms`}</dd></div>
              <div><dt>运行时间</dt><dd>{detail.submission.runtimeMs == null ? "--" : `${detail.submission.runtimeMs} ms`}</dd></div>
              <div><dt>内存占用</dt><dd>{detail.submission.memoryKb == null ? "--" : `${detail.submission.memoryKb} KB`}</dd></div>
              <div><dt>重判次数</dt><dd>{detail.submission.rejudgeCount ?? 0}</dd></div>
              <div><dt>提交时间</dt><dd>{time(detail.submission.createdAt)}</dd></div>
            </dl>
          </div>

          <section className={styles.block}>
            <h2>公开用例结果</h2>
            {detail.cases.length === 0
              ? <p className={styles.state}>这次提交没有公开用例结果。</p>
              : <div className={styles.cases}>
                  {detail.cases.map((item) => (
                    <div key={item.order}>
                      <span>测试 {item.order}</span>
                      <b className={item.verdict === "AC" ? styles.pass : styles.fail}>{item.verdict}</b>
                      <small>{item.runtimeMs == null ? "" : `${item.runtimeMs} ms`}{item.outputSummary ? ` · ${item.outputSummary}` : ""}</small>
                    </div>
                  ))}
                </div>}
            <p className={styles.note}>隐藏用例的内容不会被展示，只参与判题与自动诊断的计数。</p>
          </section>

          <section className={styles.block}>
            <h2>自动诊断</h2>
            {findings.length > 0
              ? <div className={styles.findings}>
                  {findings.map((finding, index) => (
                    <div className={styles.finding} key={index}>
                      <b>{finding.file ?? "代码"}{finding.line ? `:${finding.line}` : ""}</b>
                      <span>{finding.message}{finding.suggestion ? ` → ${finding.suggestion}` : ""}</span>
                    </div>
                  ))}
                </div>
              : <p className={styles.content}>{diagnosis?.content ?? "诊断尚未生成，稍候会自动出现。"}</p>}
            {diagnosis && !diagnosis.available && diagnosis.reason && <p className={styles.note}>{diagnosis.reason}</p>}
            {diagnosis?.available && !findings.length && diagnosis.reason && <p className={styles.note}>{diagnosis.reason}</p>}
            {trace.length > 1 && <p className={styles.trace}>{trace.join(" → ")}</p>}
            {diagnosis?.available && <p className={styles.note}>
              {diagnosis.safetyStatus === "FALLBACK" ? "规则化建议（模型不可用时生成）" : "由 Debugger / Reviewer 多 Agent 流水线生成"}
            </p>}
          </section>

          <section className={styles.block}>
            <h2>提交的代码</h2>
            <pre className={styles.code}>{detail.sourceCode}</pre>
          </section>

          <div className={styles.actions}>
            <Link className={styles.primary} href="/submissions">返回提交记录</Link>
            <button type="button" className={styles.ghost} onClick={rejudge} disabled={busy}>{busy ? "重新判题中…" : "重新判题"}</button>
            <Link className={styles.ghost} href={`/workbench/${detail.slug}`}>去工作台改这题</Link>
            <Link className={styles.ghost} href={`/problems/${detail.slug}`}>查看题面</Link>
          </div>
        </>}
      </section>
    </main>
  );
}
