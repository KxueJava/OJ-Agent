"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { apiRequest, useAuth } from "../../../lib/auth";
import { useLanguage } from "../../../lib/i18n";
import { BrandMark } from "../../../components/brand-mark";
import ProblemPicker from "../../../components/problem-picker";
import styles from "./page.module.css";

type ContestRow = {
  id: number; slug: string; title: string; mode: string; status: string;
  startAt: string; endAt: string; freezeMinutes: number; penaltyMinutes: number;
  problemCount: number; participantCount: number; publishedAt?: string; finalizedAt?: string;
};
type ProblemView = { id: number; problemId: number; problemVersionId: number; label: string; score: number; displayOrder: number; slug: string; title: string; difficulty: string };
type IssueView = { code: string; message: string };
type AnnouncementView = { id: number; body: string; createdAt: string };
type ContestDetail = { contest: ContestRow; descriptionMd?: string; problems: ProblemView[]; issues: IssueView[]; announcements: AnnouncementView[]; editable: boolean };
type PublishResult = { published: boolean; issues: IssueView[]; contest: ContestRow };

/** 管理端文案内联在页面里：管理端不面向终端用户，避免再开一个 catalog 文件。 */
const dictionary = {
  zh: {
    title: "竞赛管理", lead: "创建草稿、选题、发布、取消与定榜；发布前会逐条列出校验结果。",
    create: "新建竞赛", slug: "标识（小写字母/数字/连字符）", name: "名称",
    start: "开始时间", end: "结束时间", freeze: "冻结（分钟）", penalty: "罚时（分钟/次）",
    save: "创建草稿", problems: "题目", addProblem: "添加题目（填题库 slug）", add: "添加", remove: "移除",
    publish: "发布", cancel: "取消比赛", finalize: "定榜", refresh: "刷新",
    checklist: "发布校验", publishOk: "发布成功，状态已变为未开始", publishFail: "发布被拒，请按下面的清单修正",
    noIssues: "校验通过，可以发布", editable: "可编辑", locked: "已锁定（进行中/已结束不可改配置）",
    announcements: "公告", announce: "发公告", announcePlaceholder: "公告内容",
    empty: "还没有竞赛，先创建一场草稿。", loading: "加载中…", pick: "从左侧选择一场竞赛查看详情",
    statusDRAFT: "草稿", statusSCHEDULED: "未开始", statusRUNNING: "进行中", statusENDED: "已结束", statusFINALIZED: "已定榜", statusCANCELLED: "已取消",
    notAdmin: "只有管理员可以访问竞赛管理。", back: "返回管理端",
  },
  en: {
    title: "Contest admin", lead: "Create drafts, pick problems, publish, cancel and finalize. Publishing lists every validation result.",
    create: "New contest", slug: "Slug (lowercase/digits/hyphen)", name: "Title",
    start: "Starts at", end: "Ends at", freeze: "Freeze (min)", penalty: "Penalty (min per wrong)",
    save: "Create draft", problems: "Problems", addProblem: "Add problem (bank slug)", add: "Add", remove: "Remove",
    publish: "Publish", cancel: "Cancel contest", finalize: "Finalize", refresh: "Refresh",
    checklist: "Publish checklist", publishOk: "Published. Status is now Upcoming.", publishFail: "Publish rejected — fix the items below",
    noIssues: "All checks passed, ready to publish", editable: "Editable", locked: "Locked (running/ended contests cannot be edited)",
    announcements: "Announcements", announce: "Post announcement", announcePlaceholder: "Announcement text",
    empty: "No contests yet — create a draft first.", loading: "Loading…", pick: "Select a contest on the left",
    statusDRAFT: "Draft", statusSCHEDULED: "Upcoming", statusRUNNING: "Running", statusENDED: "Ended", statusFINALIZED: "Finalized", statusCANCELLED: "Cancelled",
    notAdmin: "Only admins can open contest management.", back: "Back to admin",
  },
} as const;
type Key = keyof typeof dictionary.zh;

/** datetime-local 需要**本地时间**字符串：不能用 toISOString（那是 UTC，会差 8 小时）。 */
const localInput = (value?: string) => {
  if (!value) return "";
  const date = new Date(value);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

export default function AdminContestsPage() {
  const { language } = useLanguage();
  const t = (key: Key) => dictionary[language][key] ?? dictionary.zh[key];
  const user = useAuth((state) => state.user);
  const [rows, setRows] = useState<ContestRow[] | null>(null);
  const [selected, setSelected] = useState<ContestDetail | null>(null);
  const [message, setMessage] = useState("");
  const [issues, setIssues] = useState<IssueView[] | null>(null);
  const [problemSlug, setProblemSlug] = useState("");
  const [announcement, setAnnouncement] = useState("");
  const [draft, setDraft] = useState({ slug: "", title: "", startAt: "", endAt: "", freezeMinutes: "20", penaltyMinutes: "20" });
  /** 选中某场比赛后，用它回填"编辑基本信息"表单（这是比赛的正式名称等字段）。 */
  const [edit, setEdit] = useState({ title: "", startAt: "", endAt: "", freezeMinutes: "20", penaltyMinutes: "20" });

  useEffect(() => {
    if (!selected) return;
    setEdit({
      title: selected.contest.title,
      startAt: localInput(selected.contest.startAt),
      endAt: localInput(selected.contest.endAt),
      freezeMinutes: String(selected.contest.freezeMinutes),
      penaltyMinutes: String(selected.contest.penaltyMinutes),
    });
  }, [selected]);

  const saveEdit = async () => {
    if (!selected) return;
    try {
      await apiRequest(`/api/admin/contests/${selected.contest.id}`, {
        method: "PUT",
        body: JSON.stringify({
          title: edit.title,
          startAt: edit.startAt ? new Date(edit.startAt).toISOString() : undefined,
          endAt: edit.endAt ? new Date(edit.endAt).toISOString() : undefined,
          freezeMinutes: Number(edit.freezeMinutes),
          penaltyMinutes: Number(edit.penaltyMinutes),
        }),
      });
      setMessage(language === "en" ? "Saved." : "已保存。");
      await load(); await open(selected.contest.id);
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "save failed"); }
  };

  const isAdmin = user?.role === "ADMIN";

  const load = useCallback(async () => {
    try { setRows(await apiRequest<ContestRow[]>("/api/admin/contests?size=50")); }
    catch (cause) { setMessage(cause instanceof Error ? cause.message : "load failed"); }
  }, []);
  const open = useCallback(async (id: number) => {
    try { setSelected(await apiRequest<ContestDetail>(`/api/admin/contests/${id}`)); setIssues(null); setMessage(""); }
    catch (cause) { setMessage(cause instanceof Error ? cause.message : "load failed"); }
  }, []);

  useEffect(() => { if (isAdmin) void load(); }, [isAdmin, load]);

  const create = async () => {
    try {
      const body = {
        slug: draft.slug, title: draft.title,
        startAt: draft.startAt ? new Date(draft.startAt).toISOString() : undefined,
        endAt: draft.endAt ? new Date(draft.endAt).toISOString() : undefined,
        freezeMinutes: Number(draft.freezeMinutes), penaltyMinutes: Number(draft.penaltyMinutes),
      };
      const created = await apiRequest<ContestDetail>("/api/admin/contests", { method: "POST", body: JSON.stringify(body) });
      setMessage(""); setDraft({ slug: "", title: "", startAt: "", endAt: "", freezeMinutes: "20", penaltyMinutes: "20" });
      await load(); await open(created.contest.id);
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "create failed"); }
  };
  const act = async (id: number, action: "publish" | "cancel" | "finalize") => {
    try {
      if (action === "publish") {
        const result = await apiRequest<PublishResult>(`/api/admin/contests/${id}/publish`, { method: "POST" });
        setIssues(result.issues);
        setMessage(result.published ? t("publishOk") : t("publishFail"));
      } else {
        await apiRequest(`/api/admin/contests/${id}/${action}`, { method: "POST" });
        setMessage("");
      }
      await load(); await open(id);
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "action failed"); }
  };
  /** 竞赛内批量重判（P5 接口）：赛中发现数据问题或赛后修正口径时用。 */
  const rejudgeContest = async (id: number) => {
    try {
      const result = await apiRequest<{ rejudged: number }>(`/api/admin/contests/${id}/rejudge`, { method: "POST" });
      setMessage(language === "en" ? `Rejudging ${result.rejudged} submissions.` : `已把 ${result.rejudged} 条提交重新投递判题。`);
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "rejudge failed"); }
  };

  /** 批量加题：后端没有批量接口，顺序逐道调用；失败的（已在赛中/不可用）汇总提示。 */
  const addMany = async (slugs: string[]) => {
    if (!selected || slugs.length === 0) return;
    const failed: string[] = [];
    for (const slug of slugs) {
      try {
        await apiRequest(`/api/admin/contests/${selected.contest.id}/problems`, { method: "POST", body: JSON.stringify({ problemSlug: slug }) });
      } catch { failed.push(slug); }
    }
    await open(selected.contest.id);
    if (failed.length > 0) {
      setMessage(language === "en"
        ? `Not added (already in contest or unavailable): ${failed.join(", ")}`
        : `未能添加（已在赛中或不可用）：${failed.join(", ")}`);
    }
  };
  const removeProblem = async (problemId: number) => {
    if (!selected) return;
    try {
      await apiRequest(`/api/admin/contests/${selected.contest.id}/problems/${problemId}`, { method: "DELETE" });
      await open(selected.contest.id);
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "remove failed"); }
  };
  const announce = async () => {
    if (!selected || !announcement.trim()) return;
    try {
      await apiRequest(`/api/admin/contests/${selected.contest.id}/announcements`, { method: "POST", body: JSON.stringify({ body: announcement.trim() }) });
      setAnnouncement(""); await open(selected.contest.id);
    } catch (cause) { setMessage(cause instanceof Error ? cause.message : "announce failed"); }
  };

  const statusText = (status: string) => (t as (key: Key) => string)(`status${status}` as Key);
  const stamp = (value?: string) => (value ? new Date(value).toLocaleString(language === "en" ? "en-US" : "zh-CN", { hour12: false, month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" }) : "--");

  if (!isAdmin) {
    return <main className={styles.page}><section className={styles.wrap}>
      <p className={styles.state}>{t("notAdmin")} <Link href="/admin">{t("back")}</Link></p>
    </section></main>;
  }

  return (
    <main className={styles.page}>
      <header className="app-topbar">
        <Link className="brand" href="/"><BrandMark />CodeAgent OJ</Link>
        <nav aria-label="主导航"><Link href="/admin">{t("back")}</Link></nav>
      </header>

      <section className={styles.wrap}>
        <div className={styles.heading}>
          <div>
            <p className={styles.eyebrow}>CONTEST ADMIN</p>
            <h1>{t("title")}</h1>
            <p>{t("lead")}</p>
          </div>
          <button type="button" className={styles.ghost} onClick={() => void load()}>{t("refresh")}</button>
        </div>

        {message && <p className={styles.message}>{message}</p>}

        <div className={styles.grid}>
          <div>
            <section className={styles.panel}>
              <h2>{t("create")}</h2>
              <div className={styles.form}>
                <label>{t("slug")}<input value={draft.slug} onChange={(event) => setDraft({ ...draft, slug: event.target.value })} placeholder="weekly-08" /></label>
                <label>{t("name")}<input value={draft.title} onChange={(event) => setDraft({ ...draft, title: event.target.value })} /></label>
                <label>{t("start")}<input type="datetime-local" value={draft.startAt} onChange={(event) => setDraft({ ...draft, startAt: event.target.value })} /></label>
                <label>{t("end")}<input type="datetime-local" value={draft.endAt} onChange={(event) => setDraft({ ...draft, endAt: event.target.value })} /></label>
                <label>{t("freeze")}<input value={draft.freezeMinutes} onChange={(event) => setDraft({ ...draft, freezeMinutes: event.target.value })} /></label>
                <label>{t("penalty")}<input value={draft.penaltyMinutes} onChange={(event) => setDraft({ ...draft, penaltyMinutes: event.target.value })} /></label>
                <button type="button" className={styles.primary} onClick={() => void create()} disabled={!draft.slug || !draft.title}>{t("save")}</button>
              </div>
            </section>

            <section className={styles.panel}>
              <h2>{t("title")}</h2>
              {!rows && <p className={styles.state}>{t("loading")}</p>}
              {rows && rows.length === 0 && <p className={styles.state}>{t("empty")}</p>}
              {rows?.map((row) => (
                <div className={selected?.contest.id === row.id ? styles.rowActive : styles.row} key={row.id}>
                  <button type="button" className={styles.rowMain} onClick={() => void open(row.id)}>
                    <b>{row.title}</b>
                    <small>{row.slug} · {stamp(row.startAt)} → {stamp(row.endAt)} · {row.problemCount} {t("problems")}</small>
                  </button>
                  <span className={styles[`badge${row.status}`] ?? styles.badge}>{statusText(row.status)}</span>
                </div>
              ))}
            </section>
          </div>

          <aside>
            {!selected && <p className={styles.state}>{t("pick")}</p>}
            {selected && <>
              <section className={styles.panel}>
                <h2>{selected.contest.title}</h2>
                <p className={styles.state}>{selected.editable ? t("editable") : t("locked")}</p>
                <div className={styles.statusLine}><span>{statusText(selected.contest.status)}</span><span>{stamp(selected.contest.publishedAt)} / {stamp(selected.contest.finalizedAt)}</span></div>
                <div className={styles.actions}>
                  {/* 按状态禁用：只有 草稿/未开始 能发布，只有 已结束 能定榜，已定榜/已取消不能取消 —— 避免点了注定 409 的按钮 */}
                  <button type="button" className={styles.primary} disabled={!(selected.contest.status === "DRAFT" || selected.contest.status === "SCHEDULED")} onClick={() => void act(selected.contest.id, "publish")}>{t("publish")}</button>
                  <button type="button" className={styles.ghost} disabled={selected.contest.status !== "ENDED"} onClick={() => void act(selected.contest.id, "finalize")}>{t("finalize")}</button>
                  <button type="button" className={styles.ghost} disabled={!["DRAFT", "SCHEDULED", "RUNNING"].includes(selected.contest.status)} onClick={() => void act(selected.contest.id, "cancel")}>{t("cancel")}</button>
                  <button type="button" className={styles.ghost} onClick={() => void rejudgeContest(selected.contest.id)}>{language === "en" ? "Rejudge submissions" : "重判本场提交"}</button>
                </div>
              </section>

              <section className={styles.panel}>
                <h2>{language === "en" ? "Basic info" : "基本信息"}</h2>
                <div className={styles.form}>
                  <label>{t("name")}<input value={edit.title} disabled={!selected.editable} onChange={(event) => setEdit({ ...edit, title: event.target.value })} /></label>
                  <label>{t("freeze")}<input value={edit.freezeMinutes} disabled={!selected.editable} onChange={(event) => setEdit({ ...edit, freezeMinutes: event.target.value })} /></label>
                  <label>{t("start")}<input type="datetime-local" value={edit.startAt} disabled={!selected.editable} onChange={(event) => setEdit({ ...edit, startAt: event.target.value })} /></label>
                  <label>{t("end")}<input type="datetime-local" value={edit.endAt} disabled={!selected.editable} onChange={(event) => setEdit({ ...edit, endAt: event.target.value })} /></label>
                  <label>{t("penalty")}<input value={edit.penaltyMinutes} disabled={!selected.editable} onChange={(event) => setEdit({ ...edit, penaltyMinutes: event.target.value })} /></label>
                  <button type="button" className={styles.primary} disabled={!selected.editable} onClick={() => void saveEdit()}>{language === "en" ? "Save" : "保存修改"}</button>
                </div>
                {!selected.editable && <p className={styles.state}>{t("locked")}</p>}
              </section>

              <section className={styles.panel}>
                <h2>{t("checklist")}</h2>
                {/* 只有可发布的状态才显示"校验通过"；已取消/已定榜等状态即使校验干净也不能发布，否则会误导 */}
                {!["DRAFT", "SCHEDULED"].includes(selected.contest.status) && (
                  <p className={styles.issue}>{language === "en" ? `Status is ${selected.contest.status} — this contest can no longer be published.` : `当前状态为「${statusText(selected.contest.status)}」，已不能发布。`}</p>
                )}
                {issues === null && selected.issues.length > 0 && selected.issues.map((issue) => <p className={styles.issue} key={issue.code}>{issue.message}</p>)}
                {["DRAFT", "SCHEDULED"].includes(selected.contest.status) && issues === null && selected.issues.length === 0 && <p className={styles.ok}>{t("noIssues")}</p>}
                {["DRAFT", "SCHEDULED"].includes(selected.contest.status) && issues !== null && issues.length === 0 && <p className={styles.ok}>{t("noIssues")}</p>}
                {issues !== null && issues.map((issue) => <p className={styles.issue} key={issue.code}>{issue.message}</p>)}
              </section>

              <section className={styles.panel}>
                <h2>{t("problems")}</h2>
                {selected.problems.map((problem) => (
                  <div className={styles.problemRow} key={problem.id}>
                    <span className={styles.label}>{problem.label}</span>
                    <span>{problem.title}<small>{problem.slug} · {problem.score}</small></span>
                    <button type="button" className={styles.ghost} onClick={() => void removeProblem(problem.problemId)} disabled={!selected.editable}>{t("remove")}</button>
                  </div>
                ))}
                <div className={styles.inline}>
                  <ProblemPicker
                    addedSlugs={selected.problems.map((problem) => problem.slug)}
                    disabled={!selected.editable}
                    onConfirm={addMany}
                    labels={{
                      trigger: language === "en" ? "Pick problems" : "选择题目（可多选）",
                      search: language === "en" ? "Search by title or slug" : "搜索题目名或 slug",
                      empty: language === "en" ? "No matching problems" : "没有匹配的题目",
                      loading: t("loading"),
                      end: language === "en" ? "— end of list —" : "— 已到底 —",
                      confirm: (count) => (language === "en" ? `Add ${count} selected` : `添加所选 ${count} 道`),
                      already: language === "en" ? "added" : "已添加",
                      selected: (count) => `(${count})`,
                      close: language === "en" ? "Hold Ctrl / ⌘ and click to select more than one" : "按住 Ctrl / ⌘ 点击可多选",
                    }}
                  />
                </div>
              </section>

              <section className={styles.panel}>
                <h2>{t("announcements")}</h2>
                {selected.announcements.map((item) => (
                  <p className={styles.notice} key={item.id}><em>{stamp(item.createdAt)}</em>{item.body}</p>
                ))}
                <div className={styles.inline}>
                  <input value={announcement} onChange={(event) => setAnnouncement(event.target.value)} placeholder={t("announcePlaceholder")} />
                  <button type="button" className={styles.ghost} onClick={() => void announce()}>{t("announce")}</button>
                </div>
              </section>
            </>}
          </aside>
        </div>
      </section>
    </main>
  );
}
