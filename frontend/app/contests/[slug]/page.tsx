"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest, publicApiRequest, useAuth } from "../../../lib/auth";
import { useContestMessages } from "../../../lib/messages/contest";
import { useLanguage } from "../../../lib/i18n";
import { BrandMark } from "../../../components/brand-mark";
import styles from "../contest.module.css";

type ContestRow = {
  id: number; slug: string; title: string; mode: string; status: string;
  startAt: string; endAt: string; freezeMinutes: number; penaltyMinutes: number;
  problemCount: number; participantCount: number;
};
type PublicProblem = { label: string; score: number; displayOrder: number; slug?: string | null; title?: string | null; difficulty?: string | null };
type Announcement = { id: number; body: string; createdAt: string };
type PublicDetail = { contest: ContestRow; descriptionMd?: string; problems: PublicProblem[]; announcements: Announcement[]; problemsHidden: boolean; registered?: boolean };

type StandingCell = { label: string; state: string; acceptedAtSeconds: number | null; wrongAttempts: number };
type Standing = { rank: number; userId: number; username: string; displayName: string; solved: number; penaltySeconds: number; cells: StandingCell[] };

/** 秒 → mm:ss / h:mm:ss（榜单里的 AC 用时与罚时都用它）。 */
function clock(seconds: number | null) {
  if (seconds == null) return "--";
  const pad = (value: number) => String(value).padStart(2, "0");
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return (hours > 0 ? `${hours}:` : "") + `${pad(hours > 0 ? minutes : Math.floor(seconds / 60))}:${pad(seconds % 60)}`;
}

function remaining(target: string, now: number) {
  const diff = Math.max(0, new Date(target).getTime() - now);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${pad(Math.floor(diff / 3600000))}:${pad(Math.floor((diff % 3600000) / 60000))}:${pad(Math.floor((diff % 60000) / 1000))}`;
}

export default function ContestDetailPage({ params }: { params: Promise<{ slug: string }> }) {
  const t = useContestMessages();
  const { t: nav } = useLanguage();
  const [slug, setSlug] = useState("");
  const [detail, setDetail] = useState<PublicDetail | null>(null);
  const [error, setError] = useState("");
  const [now, setNow] = useState(() => Date.now());
  const [standings, setStandings] = useState<Standing[] | null>(null);
  const [registered, setRegistered] = useState(false);
  const [notice, setNotice] = useState("");
  const token = useAuth((state) => state.accessToken);

  // 榜单：选手视角（冻结期内的提交后端不会返回）
  useEffect(() => {
    if (!slug) return;
    publicApiRequest<Standing[]>(`/api/contests/${slug}/standings`).then(setStandings).catch(() => setStandings([]));
  }, [slug]);

  const register = async () => {
    if (!token) { setNotice(t("contest.loginToRegister")); return; }
    try {
      const result = await apiRequest<{ registered: boolean; participantCount: number }>(`/api/contests/${slug}/register`, { method: "POST" });
      setRegistered(result.registered);
      setNotice(`${t("contest.registered")} · ${result.participantCount}`);
    } catch (cause) { setNotice(cause instanceof Error ? cause.message : t("contest.registerClosed")); }
  };

  useEffect(() => { params.then((value) => setSlug(value.slug)); }, [params]);
  useEffect(() => {
    if (!slug) return;
    // 用 apiRequest 而不是 publicApiRequest：它会带上 Authorization，后端才能返回 registered（我是否已报名）
    apiRequest<PublicDetail>(`/api/contests/${slug}`)
      .then((next) => { setDetail(next); setRegistered(Boolean(next.registered)); })
      .catch((cause) => setError(cause instanceof Error && cause.message ? cause.message : t("contest.notFound")));
  }, [slug, t]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  if (error) return <main className={styles.page}><section className={styles.wrap}><p className={styles.state}>{error}</p><Link className={styles.ghost} href="/contests">{t("contest.backToList")}</Link></section></main>;
  if (!detail) return <main className={styles.page}><section className={styles.wrap}><p className={styles.state}>{t("contest.loading")}</p></section></main>;

  const row = detail.contest;
  const running = row.status === "RUNNING";
  const statusText = row.status === "SCHEDULED" ? t("contest.status.scheduled") : running ? t("contest.status.running") : row.status === "ENDED" ? t("contest.status.ended") : t("contest.status.finalized");
  const badgeClass = running ? `${styles.badge} ${styles.running}` : row.status === "SCHEDULED" ? `${styles.badge} ${styles.scheduled}` : `${styles.badge} ${styles.finalized}`;
  const stamp = (value: string) => new Date(value).toLocaleString("zh-CN", { hour12: false, month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });

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
        <div className={styles.hero}>
          <div>
            <p className={styles.eyebrow}>{t("contest.eyebrow")} / {row.mode}</p>
            <h1>{row.title}</h1>
            {detail.descriptionMd && <p className={styles.state} style={{ marginTop: 12 }}>{detail.descriptionMd}</p>}
            <div className={styles.chips}>
              <span className={badgeClass}><i />{statusText}</span>
              <span className={styles.chip}>{stamp(row.startAt)} → {stamp(row.endAt)}</span>
              <span className={styles.chip}>{t("contest.meta.problems")} {row.problemCount}</span>
              <span className={styles.chip}>{t("contest.meta.participants")} {row.participantCount}</span>
            </div>
          </div>
          <div className={styles.live}>
            <span className={styles.label}><i style={{ width: 7, height: 7, borderRadius: "50%", background: running ? "#277456" : "#858981" }} />{statusText}</span>
            <div className={styles.clock}>{running ? remaining(row.endAt, now) : row.status === "SCHEDULED" ? remaining(row.startAt, now) : "—"}</div>
            <small className={styles.state}>{running ? stamp(row.endAt) : row.status === "SCHEDULED" ? stamp(row.startAt) : stamp(row.endAt)}</small>
          </div>
        </div>

        <div className={styles.grid}>
          <div>
            <div className={styles.panel}>
              <h2>{t("contest.problems")}</h2>
              {detail.problemsHidden && <p className={styles.hiddenNote}>{t("contest.problems.hidden")}</p>}
              <table className={styles.table}>
                <thead><tr><th>#</th><th>{t("contest.problems")}</th><th className={styles.score}>{t("contest.score").replace("{n}", "")}</th></tr></thead>
                <tbody>
                  {detail.problems.map((problem) => (
                    <tr key={problem.label}>
                      <td className={styles.labelCell}>{problem.label}</td>
                      <td>
                        {problem.title && problem.slug
                          ? <Link href={`/contests/${slug}/workbench/${problem.slug}`}>{problem.title}</Link>
                          : <span className={styles.state}>{t("contest.problems.hidden")}</span>}
                      </td>
                      <td className={styles.score}>{problem.score}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className={styles.panel} id="standings">
              <h2>{t("contest.standings")}</h2>
              {standings && standings.length === 0 && <p className={styles.state}>{t("contest.standings.empty")}</p>}
              {standings && standings.length > 0 && (
                <div className={styles.standings}>
                  <table className={styles.standingsTable}>
                    <thead>
                      <tr>
                        <th className={styles.colRank}>#</th>
                        <th className={styles.colName}>{nav("nav.leaderboard")}</th>
                        <th className={styles.colNum}>{t("contest.standings.solved")}</th>
                        <th className={styles.colNum}>{t("contest.standings.penalty")}</th>
                        {detail.problems.map((problem) => <th className={styles.colProblem} key={problem.label}>{problem.label}</th>)}
                      </tr>
                    </thead>
                    <tbody>
                      {standings.map((row) => (
                        <tr key={row.userId} className={row.userId === useAuth.getState().user?.id ? styles.rowMine : undefined}>
                          <td className={styles.colRank}>{row.rank}</td>
                          <td className={styles.colName}><b>{row.displayName}</b><small>@{row.username}</small></td>
                          <td className={styles.colNum}>{row.solved}</td>
                          <td className={styles.colNum}>{clock(row.penaltySeconds)}</td>
                          {row.cells.map((cell) => (
                            <td className={styles.colProblem} key={cell.label}>
                              {cell.state === "AC"
                                ? <span className={styles.cellAc}>{clock(cell.acceptedAtSeconds)}{cell.wrongAttempts > 0 && <em>+{cell.wrongAttempts}</em>}</span>
                                : cell.state === "FROZEN"
                                  ? <span style={{ color: "#95611e", font: "700 12px Consolas,monospace" }} title="该题在冻结期内有提交，赛后揭晓">?</span>
                                  : cell.state === "FAIL"
                                    ? <span className={styles.cellFail}>×{cell.wrongAttempts}</span>
                                    : <span className={styles.cellNone}>·</span>}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {/* 三种情况分开说，避免"已报名"却还提示"登录并报名"这种自相矛盾的文案 */}
              <p className={styles.state}>
                {row.freezeMinutes > 0 && now >= new Date(row.endAt).getTime() - row.freezeMinutes * 60000
                  ? t("contest.standings.frozen").replace("{n}", String(row.freezeMinutes))
                  : registered && token
                    ? t("contest.standings.registeredHint")
                    : t("contest.standings.loginHint")}
              </p>
            </div>

            <div className={styles.actions}>
              <button type="button" className={styles.primary} onClick={() => void register()} disabled={registered || row.status === "ENDED" || row.status === "FINALIZED"}>
                {registered ? t("contest.registered") : t("contest.register")}
              </button>
              {notice && <span className={styles.state}>{notice}</span>}
            </div>

            <div className={styles.actions}>
              <Link className={styles.primary} href="/contests">{t("contest.backToList")}</Link>
              <Link className={styles.ghost} href="/problems">{nav("nav.problems")}</Link>
            </div>
          </div>

          <aside>
            <div className={styles.panel}>
              <h2>{t("contest.announcements")}</h2>
              {detail.announcements.length === 0 && <p className={styles.state}>{t("contest.noAnnouncements")}</p>}
              {detail.announcements.map((item) => (
                <div className={styles.notice} key={item.id}><em>{stamp(item.createdAt)}</em>{item.body}</div>
              ))}
            </div>
            <div className={styles.panel}>
              <h2>{t("contest.rules")}</h2>
              <div className={styles.rule}><span>{t("contest.meta.rule")}</span><strong>{row.mode}</strong></div>
              <div className={styles.rule}><span>{t("contest.rule.penalty")}</span><strong>+{row.penaltyMinutes} min</strong></div>
              <div className={styles.rule}><span>{t("contest.rule.freeze")}</span><strong>{t("contest.rule.freezeValue").replace("{n}", String(row.freezeMinutes))}</strong></div>
              <div className={styles.rule}><span>{t("contest.rule.languages")}</span><strong>Java 21 · C++17 · C17</strong></div>
            </div>
          </aside>
        </div>
      </section>
    </main>
  );
}
