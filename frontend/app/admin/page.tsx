"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowLeft, ExternalLink, RefreshCw, ShieldCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { apiRequest, authRequest, useAuth } from "../../lib/auth";
import styles from "./admin.module.css";

type ProblemRow = {
  slug: string;
  title: string;
  difficulty: string;
  status: string;
  publishedVersion: number | null;
  updatedAt: string;
};

type Audit = {
  id: number;
  sessionId: number;
  intent: string;
  route: string;
  safetyStatus: string;
  blockedReason?: string | null;
  createdAt: string;
};
type UserRow = { id:number; username:string; email:string; displayName:string; role:string; enabled:boolean; createdAt:string };

export default function AdminPage() {
  const router = useRouter();
  const { user, refreshToken, clearSession } = useAuth();
  const [problems, setProblems] = useState<ProblemRow[]>([]);
  const [audits, setAudits] = useState<Audit[]>([]);
  const [users, setUsers] = useState<UserRow[]>([]);
  const [view, setView] = useState<"problems" | "users">("problems");
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  async function load() {
    setLoading(true);
    try {
      const [nextProblems, nextAudits, nextUsers] = await Promise.all([
        apiRequest<ProblemRow[]>("/api/admin/problems"),
        apiRequest<Audit[]>("/api/admin/agent/audits?limit=8"),
        apiRequest<UserRow[]>("/api/admin/users"),
      ]);
      setProblems(nextProblems);
      setAudits(nextAudits);
      setUsers(nextUsers);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "管理数据加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function changeUserStatus(account: UserRow) {
    const action = account.enabled ? "禁用" : "恢复";
    if (!window.confirm(`确认${action}用户「${account.displayName}」吗？`)) return;
    try { await apiRequest(`/api/admin/users/${account.id}/status?enabled=${!account.enabled}`, { method:"POST" }); setMessage(`${account.displayName} 已${action}`); await load(); }
    catch (error) { setMessage(error instanceof Error ? error.message : "用户状态更新失败"); }
  }

  async function logout() {
    try { if (refreshToken) await authRequest<void>("/api/auth/logout", { refreshToken }); }
    finally { clearSession(); router.replace("/login"); }
  }

  useEffect(() => {
    if (!user?.id) {
      router.replace("/login");
      return;
    }
    if (user.role !== "ADMIN") {
      router.replace("/");
      return;
    }
    void load();
  }, [router, user]);

  async function changeStatus(problem: ProblemRow, action: "publish" | "offline") {
    setMessage("");
    try {
      await apiRequest(`/api/admin/problems/${problem.slug}/${action}`, { method: "POST" });
      setMessage(action === "publish" ? `${problem.title} 已发布` : `${problem.title} 已下线`);
      await load();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "操作失败");
    }
  }

  if (!user?.id || user.role !== "ADMIN") return <main className={styles.loading}>正在检查管理员权限...</main>;

  return <main className={styles.page}>
    <header className={styles.header}>
      <div>
        <Link href="/" className={styles.back}><ArrowLeft size={14} /> 返回工作台</Link>
        <p className={styles.kicker}>ADMIN CONSOLE / ACCESS CONTROLLED</p>
        <h1>管理端</h1>
        <p className={styles.subtitle}>题目版本、发布状态与 Agent 审计。</p>
      </div>
      <div className={styles.identity}><ShieldCheck size={16} /><span>{user.displayName}</span><small>ADMIN</small><button onClick={() => void logout()} title="退出登录" style={{ marginLeft: 4, padding: "3px 0", border: 0, borderBottom: "1px solid transparent", color: "#a85d3b", background: "transparent", font: "700 11px 'Avenir Next', 'Segoe UI', Arial, sans-serif", cursor: "pointer" }}>退出</button></div>
    </header>

    <section className={styles.toolbar}>
      <div><strong>{problems.length}</strong><span>题目记录</span></div>
      <div><strong>{problems.filter((problem) => problem.status === "PUBLISHED").length}</strong><span>已发布</span></div>
      <div><strong>{audits.length}</strong><span>近期审计</span></div>
      <button className={styles.refresh} onClick={() => void load()} disabled={loading} title="刷新管理数据"><RefreshCw size={15} className={loading ? styles.spin : ""} /> 刷新</button>
    </section>

    <div role="tablist" aria-label="管理视图" style={{display:"flex",justifyContent:"center",gap:4,maxWidth:1200,margin:"24px auto 2px",padding:4,border:"1px solid #d8d5ce",background:"#ebe9e2",width:"fit-content"}}>
      <button role="tab" aria-selected={view === "problems"} onClick={() => setView("problems")} style={{border:0,padding:"10px 17px",background:view === "problems" ? "#252a2d" : "transparent",color:view === "problems" ? "#f8f7f3" : "#6f736e",font:"600 12px 'Avenir Next','Segoe UI',Arial,sans-serif",cursor:"pointer",transition:"transform .2s ease, background .2s ease"}}>题目管理</button>
      <button role="tab" aria-selected={view === "users"} onClick={() => setView("users")} style={{border:0,padding:"10px 17px",background:view === "users" ? "#252a2d" : "transparent",color:view === "users" ? "#f8f7f3" : "#6f736e",font:"600 12px 'Avenir Next','Segoe UI',Arial,sans-serif",cursor:"pointer",transition:"transform .2s ease, background .2s ease"}}>用户管理 <span style={{marginLeft:6,color:view === "users" ? "#e2a078" : "#a2a49d",fontFamily:"Consolas,monospace"}}>{users.length}</span></button>
      {/* 竞赛管理是独立路由（不是本页的 tab 状态），用普通 <a> 跳转即可，避免依赖 next/link 的 import */}
      <a href="/admin/contests" style={{display:"inline-block",padding:"10px 17px",color:"#6f736e",font:"600 12px 'Avenir Next','Segoe UI',Arial,sans-serif",textDecoration:"none",transition:"transform .2s ease, background .2s ease"}}>竞赛管理</a>
    </div>

    {message && <p className={styles.message} role="status">{message}</p>}
    {view === "problems" && <div className={styles.grid}>
      <section className={styles.panel}>
        <div className={styles.panelHead}><div><p className={styles.kicker}>PROBLEM CATALOG</p><h2>题目发布</h2></div><Link href="/problems" className={styles.textLink}>查看题库 <ExternalLink size={13} /></Link></div>
        {loading ? <p className={styles.empty}>正在加载题目...</p> : <div className={styles.tableWrap}><table><thead><tr><th>题目</th><th>难度</th><th>状态</th><th>版本</th><th /></tr></thead><tbody>{problems.map((problem) => <tr key={problem.slug}><td><strong>{problem.title}</strong><small>{problem.slug}</small></td><td><span className={`${styles.badge} ${styles[problem.difficulty.toLowerCase()]}`}>{problem.difficulty}</span></td><td><span className={`${styles.status} ${problem.status === "PUBLISHED" ? styles.published : ""}`}>{problem.status}</span></td><td>v{problem.publishedVersion ?? "-"}</td><td className={styles.actions}>{problem.status === "PUBLISHED" ? <button onClick={() => void changeStatus(problem, "offline")}>下线</button> : <button onClick={() => void changeStatus(problem, "publish")}>发布</button>}<Link href={`/problems/${problem.slug}`} aria-label={`查看${problem.title}`}>查看</Link></td></tr>)}</tbody></table></div>}
      </section>
      <section className={styles.panel}>
        <div className={styles.panelHead}><div><p className={styles.kicker}>AGENT AUDIT LOG</p><h2>安全审计</h2></div><span className={styles.muted}>最近 8 条</span></div>
        {audits.length === 0 ? <p className={styles.empty}>暂无 Agent 审计记录。</p> : <div className={styles.auditList}>{audits.map((audit) => <article key={audit.id} className={styles.audit}><div><strong>{audit.route}</strong><span>{audit.intent} · 会话 #{audit.sessionId}</span></div><div className={audit.safetyStatus === "BLOCKED" ? styles.blocked : styles.safe}>{audit.safetyStatus}</div><time>{new Date(audit.createdAt).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" })}</time></article>)}</div>}
      </section>
    </div>}
    {view === "users" && <section className={styles.panel}>
      <div className={styles.panelHead}><div><p className={styles.kicker}>USER ACCESS</p><h2>用户管理</h2></div><span className={styles.muted}>{users.length} 个账号</span></div>
      {loading ? <p className={styles.empty}>正在加载用户...</p> : <div className={styles.tableWrap}><table><thead><tr><th>用户</th><th>邮箱</th><th>角色</th><th>状态</th><th>注册时间</th><th /></tr></thead><tbody>{users.map((account) => <tr key={account.id}><td><strong>{account.displayName}</strong><small>@{account.username}</small></td><td>{account.email}</td><td><span className={styles.badge}>{account.role}</span></td><td><span className={`${styles.status} ${account.enabled ? styles.published : styles.blocked}`}>{account.enabled ? "正常" : "已禁用"}</span></td><td>{new Date(account.createdAt).toLocaleDateString("zh-CN")}</td><td className={styles.actions}>{account.id === user.id ? <span className={styles.muted}>当前账号</span> : <button onClick={() => void changeUserStatus(account)}>{account.enabled ? "禁用" : "恢复"}</button>}</td></tr>)}</tbody></table></div>}
    </section>}
  </main>;
}
