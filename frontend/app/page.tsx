"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { ArrowUpRight, Bot, Check, CircleHelp, Lightbulb, Send } from "lucide-react";
import { apiRequest, useAuth } from "../lib/auth";
import { useLanguage, type Language } from "../lib/i18n";
import { useHomeMessages, type HomeKey } from "../lib/messages/home";
import Markdown from "../components/markdown";
import { useContestLock } from "../lib/contest-lock";

type Dashboard = { continueItem: { slug:string; title:string; topic:string; state:string; updatedAt:string; submissionCount:number } | null; queue: { slug:string; title:string; difficulty:string; topic:string; state:string; latestRuntimeMs?:number }[]; totalProblems:number; solvedCount:number; totalSubmissions:number; acceptedSubmissions:number; recentSubmissions:number; activeDays:number };
type LearningOverview = { today: { completedCount:number; targetCount:number; recommendations:{slug:string;title:string;topic:string;reason:string}[] }; recommendations:{slug:string;title:string;topic:string;reason:string}[] };

const weekdays = ["SUNDAY","MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY"];
const months = ["JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"];
const weekdayKeys = ["home.date.weekday.0", "home.date.weekday.1", "home.date.weekday.2", "home.date.weekday.3", "home.date.weekday.4", "home.date.weekday.5", "home.date.weekday.6"] as const;
function todayLabel(language: Language, t: (key: HomeKey) => string) { const now = new Date(); if (language === "zh") return `${now.getFullYear()}${t("home.date.yearSuffix")}${now.getMonth() + 1}${t("home.date.monthSuffix")}${now.getDate()}${t("home.date.daySuffix")}${t(weekdayKeys[now.getDay()])}`; return `${weekdays[now.getDay()]} / ${String(now.getDate()).padStart(2, "0")} ${months[now.getMonth()]} ${now.getFullYear()}`; }
function traceOf(raw?: string) { if (!raw) return []; try { const value = JSON.parse(raw); return Array.isArray(value) ? value.map(String) : []; } catch { return []; } }

export default function Home() {
  // 首页没有题目上下文 → hook 会走"我参加的任意进行中比赛"的全局判定（比赛期间暂停教练）
  const lock = useContestLock();
  const [filter, setFilter] = useState("全部");
  const { t: nav, language } = useLanguage();
  const t = useHomeMessages();
  const [notice, setNotice] = useState("");
  const [agentPrompt, setAgentPrompt] = useState("");
  const [agentReply, setAgentReply] = useState("先完成一道简单题，我会根据你的提交结果调整训练建议。");
  const [hintLevel, setHintLevel] = useState(1);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null); const [learning, setLearning] = useState<LearningOverview | null>(null); const [error, setError] = useState(""); const user = useAuth((state) => state.user); const token = useAuth((state) => state.accessToken); const [digest, setDigest] = useState<{ kind: "DIAGNOSIS" | "RECOMMENDATION"; route: string; reply: string; trace: string[]; href: string } | null>(null);
  useEffect(() => { if (!user?.id) return; Promise.all([apiRequest<Dashboard>("/api/dashboard"), apiRequest<LearningOverview>("/api/learning/overview")]).then(([nextDashboard, nextLearning]) => { setDashboard(nextDashboard); setLearning(nextLearning); }).catch((cause) => setError(cause instanceof Error ? cause.message : "暂时无法读取学习数据")); }, [user?.id]);
  // AGENT COACH 卡片取真实数据：优先展示最近一次失败提交的多 Agent 诊断，否则退回规则推荐
  useEffect(() => { if (!token) return; let cancelled = false; type Item = { id:number; slug:string; title:string; status:string; diagnosed:boolean };
    const load = async () => { try { let latest: Item | undefined;
      for (let attempt = 0; attempt < 3 && !cancelled; attempt++) {
        const page = await apiRequest<{ items: Item[] }>("/api/submissions?size=1");
        latest = page.items[0];
        if (!latest || latest.diagnosed || latest.status === "AC" || !["WA","CE","RE","TLE","MLE"].includes(latest.status)) break;
        await new Promise((resolve) => setTimeout(resolve, 3000));
      }
      if (cancelled) return;
      if (latest?.diagnosed) {
        const view = await apiRequest<{ available:boolean; content:string; findingsJson?:string; traceJson?:string }>(`/api/agent/submissions/${latest.id}/diagnosis`);
        if (cancelled) return;
        if (view.available) {
          let findings: { message?:string; suggestion?:string }[] = [];
          try { const parsed = JSON.parse(view.findingsJson ?? "[]"); if (Array.isArray(parsed)) findings = parsed; } catch { findings = []; }
          const first = findings[0];
          setDigest({ kind: "DIAGNOSIS", route: "Debugger + Reviewer", reply: first ? `${first.message ?? ""}${first.suggestion ? ` → ${first.suggestion}` : ""}` : view.content.slice(0, 140), trace: traceOf(view.traceJson), href: `/submissions/${latest.id}` });
          return;
        }
      }
      const recommended = learning?.today.recommendations[0];
      if (recommended && !cancelled) setDigest({ kind: "RECOMMENDATION", route: "Learning（规则推荐）", reply: `${t("home.digest.recommendPrefix")}${recommended.title}${t("home.digest.recommendMiddle")}${recommended.topic}${t("home.digest.recommendSuffix")}${recommended.reason}`, trace: ["Learning(rule, 依据你的真实提交记录)"], href: `/workbench/${recommended.slug}` });
    } catch { /* 卡片是增强信息，取不到就不显示，不影响首页其它数据 */ } };
    void load(); return () => { cancelled = true; }; }, [token, learning]);
  const visibleProblems = useMemo(() => (dashboard?.queue ?? []).filter((problem) => filter === "全部" || (filter === "简单" ? problem.difficulty === "EASY" : problem.difficulty === "MEDIUM")), [dashboard, filter]);
  const showNotice = (message: string) => { setNotice(message); window.setTimeout(() => setNotice(""), 2200); };
  const contextSlug = learning?.today.recommendations[0]?.slug ?? dashboard?.queue[0]?.slug ?? dashboard?.continueItem?.slug;
  const ask = async (message: string) => { if (!token) { showNotice("登录后即可向 Agent 提问"); return; } if (!contextSlug) { showNotice("先在题库选一道题，我才能基于题目回答"); return; }
    setAgentReply("正在思考…");
    try { const problem = await apiRequest<{ problemVersionId: number }>(`/api/workspace/problems/${contextSlug}`);
      const reply = await apiRequest<{ route:string; content:string; trace:string[] }>("/api/agent/ask", { method: "POST", body: JSON.stringify({ problemVersion: problem.problemVersionId, message }) });
      setAgentReply(reply.content);
      setDigest({ kind: "DIAGNOSIS", route: reply.route, reply: reply.content, trace: reply.trace ?? [], href: `/workbench/${contextSlug}` });
    } catch (cause) { setAgentReply(cause instanceof Error ? cause.message : "Agent 暂时不可用，稍后再试"); } };
  const askAgent = (event: React.FormEvent) => { event.preventDefault(); const prompt = agentPrompt.trim(); if (!prompt) return; setAgentPrompt(""); void ask(prompt); };

  const today = todayLabel(language, t);

  return <main className="home-shell">
    <aside className="home-rail">
      <Link className="rail-brand" href="/"><span className="rail-mark" aria-label="Claude mark" style={{ width: 38, height: 30, border: "0", borderRadius: 0, background: "transparent", boxShadow: "none", overflow: "visible" }}><svg viewBox="0 0 104 64" aria-hidden="true" style={{ display: "block", width: 31, height: 20, shapeRendering: "crispEdges" }}><path fill="#df7657" d="M16 8h72v48H16zM8 20h88v24H8zM0 24h104v16H0zM28 56h8v8h-8zM44 56h8v8h-8zM68 56h8v8h-8zM84 56h8v8h-8z"/><path fill="#202936" d="M34 24h8v12h-8zM62 24h8v12h-8z"/></svg></span><span>CodeAgent OJ</span></Link>
      <nav className="rail-nav" aria-label="主导航">
        <Link className="rail-link active" href="/">{nav("nav.overview")}</Link><Link className="rail-link" href="/discussions">{nav("nav.discussions")}</Link>
        <Link className="rail-link" href="/problems">{nav("nav.problems")} <small>{dashboard?.totalProblems ?? 0}</small></Link>
        <Link className="rail-link" href="/leaderboard">{nav("nav.leaderboard")}</Link>
        <Link className="rail-link" href="/contests">{nav("nav.contests")}</Link>
        <Link className="rail-link" href="/learning">{nav("nav.mistakes")}</Link>
        <Link className="rail-link" href="/submissions">{nav("nav.submissions")}</Link>
        <Link className="rail-link" href="/settings">{nav("nav.settings")}</Link>
        {user?.role === "ADMIN" && <Link className="rail-link admin-link" href="/admin">{nav("nav.admin")} <small>ADMIN</small></Link>}
      </nav>
      <div className="rail-bottom"><span className="service-dot" />API 在线</div>
    </aside>
    <section className="home-content">
      <header className="home-header"><div><p className="section-kicker">{today}</p><span className="header-status"><i />{t("home.status.online")}</span></div><div className="header-actions"><Link className="outline-button" href="/problems">{t("home.header.problems")}</Link><Link className="solid-button" href={dashboard?.continueItem ? `/workbench/${dashboard.continueItem.slug}` : "/problems"}>{dashboard?.continueItem ? t("home.header.resume") : t("home.header.start")}</Link></div></header>
      <section className="practice-pulse" aria-label="今日练习状态"><div className="pulse-main"><strong>{t("home.progress")}</strong><small>{t("home.progress.solvedPrefix")}{String(dashboard?.solvedCount ?? 0)}{t("home.progress.solvedMiddle")}{String(dashboard?.queue.length ?? 0)}{t("home.progress.solvedSuffix")}</small><div className="pulse-meter"><i style={{ width: `${dashboard?.queue.length ? Math.min(100, ((dashboard?.solvedCount ?? 0) / dashboard.queue.length) * 100) : 0}%` }} /></div></div><div className="pulse-metric"><strong>{dashboard?.solvedCount ?? 0}</strong><span>{t("home.metric.solved")}</span></div><div className="pulse-metric"><strong>{dashboard?.totalSubmissions ?? 0}</strong><span>{t("home.metric.submissions")}</span></div><div className="pulse-metric"><strong>{dashboard?.totalSubmissions ? `${Math.round((dashboard.acceptedSubmissions / dashboard.totalSubmissions) * 100)}%` : "0%"}</strong><span>{t("home.metric.acceptance")}</span></div></section>
      {dashboard?.continueItem ? <section className="continue-strip"><div className="continue-index">01</div><div className="continue-copy"><span className="section-kicker">{t("home.continue.lastStopped")}</span><h2>{dashboard.continueItem.title}</h2><p>{dashboard.continueItem.topic} <span>／</span> {new Date(dashboard.continueItem.updatedAt).toLocaleString("zh-CN", { hour:"2-digit", minute:"2-digit" })}{t("home.continue.updatedSuffix")}</p></div><div className="continue-progress"><span>{t("home.continue.submissionCount")}</span><strong>{dashboard.continueItem.submissionCount}</strong><div><i style={{ width: `${Math.min(100, dashboard.continueItem.submissionCount * 12)}%` }} /></div></div><Link className="solid-button" href={`/workbench/${dashboard.continueItem.slug}`}>{t("home.continue.resume")}</Link></section> : <section className="continue-strip"><div className="continue-index">01</div><div className="continue-copy"><span className="section-kicker">{t("home.continue.emptyTitle")}</span><h2>{t("home.continue.emptyHeading")}</h2><p>{t("home.continue.emptyHint")}</p></div><Link className="solid-button" href="/problems">{t("home.continue.openProblems")}</Link></section>}
      <div className="home-grid">
        <section className="feed-column"><div className="section-heading"><div><p className="section-kicker">{t("home.queue.kicker")}</p><h2>{t("home.queue.title")}</h2></div><div className="filter-group" role="group" aria-label="难度筛选">{["全部", "简单", "中等"].map((item) => <button key={item} className={filter === item ? "selected" : ""} onClick={() => setFilter(item)}>{t(item === "全部" ? "home.queue.filter.all" : item === "简单" ? "home.queue.filter.easy" : "home.queue.filter.medium")}</button>)}</div></div><div className="problem-feed">{visibleProblems.map((problem, index) => <article className="problem-line" key={problem.slug} style={{ "--line-index": index } as React.CSSProperties}><span className={`difficulty-mark ${problem.difficulty === "MEDIUM" ? "medium" : "easy"}`} /><div><h3>{problem.title}</h3><p>{problem.topic}</p></div><span className="problem-time">{problem.latestRuntimeMs ? `${problem.latestRuntimeMs} ms` : t("home.queue.notSubmitted")}</span><Link href={`/workbench/${problem.slug}`} className="line-action">{problem.state} <b>→</b></Link></article>)}{!visibleProblems.length && <div className="empty-inline">{error || t("home.queue.empty")}</div>}</div><div className="feed-footer"><span>{t("home.queue.showingPrefix")}{String(visibleProblems.length)}{t("home.queue.showingMiddle")}{String(dashboard?.queue.length ?? 0)}{t("home.queue.showingSuffix")}</span><Link href="/problems">{t("home.queue.viewAll")}</Link></div></section>
        <aside className="insight-column"><section className="progress-block"><div className="section-heading"><div><p className="section-kicker">{t("home.stats.kicker")}</p><h2>{t("home.stats.title")}</h2></div><span className="progress-number">{dashboard?.solvedCount ?? 0}<span> {t("home.stats.solved")}</span></span></div><div className="large-progress"><i style={{ width: `${dashboard?.queue.length ? Math.min(100, ((dashboard?.solvedCount ?? 0) / dashboard.queue.length) * 100) : 0}%` }} /></div><div className="progress-meta"><span>{t("home.stats.totalSolved")}</span><span>{t("home.stats.recentPrefix")}{String(dashboard?.recentSubmissions ?? 0)}{t("home.stats.recentSuffix")}</span></div></section><section className="agent-coach" aria-labelledby="agent-coach-title"><div className="agent-coach-head"><div><p className="section-kicker">AGENT COACH / 01</p><h2 id="agent-coach-title">{t("home.coach.title")}</h2></div><span className="agent-live"><i />{t("home.coach.online")}</span></div><div className="agent-route">{digest?.kind === "DIAGNOSIS" ? <><span>Supervisor</span><b>→</b><span>{digest.route}</span></> : <span>{digest?.route ?? "Learning（规则推荐）"}</span>}</div><div className="agent-reply"><Bot size={15} strokeWidth={1.7} /><div style={{ flex: 1, minWidth: 0 }}>{lock.locked ? <p style={{ margin: 0, color: "#8a5a15" }}>比赛进行中，Agent 教练已暂停{lock.slug ? `（${lock.slug}）` : ""} —— 为避免影响公平性，比赛结束后自动恢复。</p> : <><Markdown text={digest?.reply ?? agentReply ?? ""} />{digest?.href && <Link className="agent-reply-link" href={digest.href}>{t("home.coach.details")}</Link>}</>}</div></div>{learning?.today.recommendations[0]&&<div className="agent-task"><div className="agent-task-top"><span className="agent-task-index">01</span><div><strong>{learning.today.recommendations[0].title}</strong><small>{learning.today.recommendations[0].topic} · {learning.today.recommendations[0].reason}</small></div><Check size={15} /></div><div className="agent-task-progress"><i style={{ width: `${learning.today.completedCount / Math.max(1, learning.today.targetCount) * 100}%` }} /></div><Link href={`/workbench/${learning.today.recommendations[0].slug}`} className="agent-task-link">{t("home.coach.startPractice")} <ArrowUpRight size={14} /></Link></div>}{learning?.today.recommendations.length===0&&<p className="empty-inline">{t("home.coach.allDone")}</p>}<div className="agent-hints"><div className="agent-hints-label"><span>{t("home.coach.hintPrompt")}</span><small>{t("home.coach.hintNote")}</small></div><div className="hint-row">{[1, 2, 3].map((level) => <button key={level} className={hintLevel === level ? "active" : ""} onClick={() => { setHintLevel(level); void ask(`给我第 ${level} 级提示，循序渐进，不要直接给完整可提交代码`); }}>{t("home.coach.hintPrefix")}{String(level)}</button>)}</div>{digest?.trace?.length ? <p className="agent-trace">{digest.trace.join(" → ")}</p> : <p className="hint-copy">提问会带上你正在练的这道题，答案只基于题目公开信息。</p>}</div><form className="agent-ask" onSubmit={askAgent}><CircleHelp size={15} /><input value={agentPrompt} onChange={(event) => setAgentPrompt(event.target.value)} placeholder={t("home.ask.placeholder")} aria-label={t("home.ask.placeholder")} /><button type="submit" aria-label="发送问题" title="发送问题"><Send size={14} /></button></form></section><section className="focus-block"><p className="section-kicker">{t("home.focus.kicker")}</p><h2>{dashboard?.activeDays ? t("home.focus.activePrefix") + String(dashboard.activeDays) + t("home.focus.activeSuffix") : t("home.focus.noActivity")}</h2><p>{dashboard?.totalSubmissions ? t("home.focus.dataNote") : t("home.focus.emptyNote")}</p><Link href="/problems">{t("home.focus.browse")} <b>→</b></Link></section><section className="stats-block"><div><strong>{dashboard?.solvedCount ?? 0}</strong><span>{t("home.stats.solved")}</span></div><div><strong>{dashboard?.totalSubmissions ? `${Math.round((dashboard.acceptedSubmissions / dashboard.totalSubmissions) * 100)}%` : "0%"}</strong><span>{t("home.metric.acceptance")}</span></div><div><strong>{dashboard?.recentSubmissions ?? 0}</strong><span>{t("home.stats.recentLabel")}</span></div></section></aside>
      </div>
      <footer className="home-footer"><span>{t("home.footer.workspace")}</span><span>{t("home.footer.note")}</span></footer>
    </section>
    {notice && <div className="home-toast" role="status">{notice}</div>}
  </main>;
}
