"use client";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { authRequest, useAuth } from "../../lib/auth";

export default function RegisterPage() {
  const router = useRouter(); const setSession = useAuth((s) => s.setSession); const [form, setForm] = useState({ username: "", email: "", password: "", displayName: "" }); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(""); try { const session = await authRequest<any>("/api/auth/register", form); setSession(session); router.push("/workspace"); } catch (e) { setError(e instanceof Error ? e.message : "注册失败"); } finally { setBusy(false); } }
  return <main className="auth-shell"><form className="auth-card" onSubmit={submit}><p className="eyebrow">CODEAGENT OJ</p><h1>创建账号</h1><p className="auth-muted">注册后即可开始做题。</p>{([ ["username", "用户名"], ["email", "邮箱"], ["displayName", "显示名称"]] as const).map(([key, label]) => <label key={key}>{label}<input required={key !== "displayName"} type={key === "email" ? "email" : "text"} value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} /></label>)}<label>密码<input required minLength={8} type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>{error && <p className="form-error">{error}</p>}<button className="primary auth-submit" disabled={busy}>{busy ? "创建中..." : "创建账号"}</button><p className="auth-footer">已有账号？ <Link href="/login">返回登录</Link></p></form></main>;
}
