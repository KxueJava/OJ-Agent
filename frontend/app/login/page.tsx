"use client";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { authRequest, useAuth } from "../../lib/auth";
import { Eye, EyeOff } from "lucide-react";

export default function LoginPage() {
  const router = useRouter(); const setSession = useAuth((s) => s.setSession); const [identifier, setIdentifier] = useState(""); const [password, setPassword] = useState(""); const [showPassword, setShowPassword] = useState(false); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(""); try { const session = await authRequest<any>("/api/auth/login", { identifier, password }); setSession(session); router.push("/workspace"); } catch (e) { setError(e instanceof Error ? e.message : "登录失败"); } finally { setBusy(false); } }
  return <main className="auth-shell"><form className="auth-card" onSubmit={submit}><p className="eyebrow">CODEAGENT OJ</p><h1>登录</h1><p className="auth-muted">继续你的 Java 练习。</p><label>用户名或邮箱<input required value={identifier} onChange={(e) => setIdentifier(e.target.value)} /></label><label>密码<span className="password-field"><input required type={showPassword ? "text" : "password"} value={password} onChange={(e) => setPassword(e.target.value)} /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "隐藏密码" : "显示密码"} title={showPassword ? "隐藏密码" : "显示密码"}>{showPassword ? <EyeOff size={16} /> : <Eye size={16} />}</button></span></label>{error && <p className="form-error">{error}</p>}<button className="primary auth-submit" disabled={busy}>{busy ? "登录中..." : "登录"}</button><p className="auth-footer">还没有账号？ <Link href="/register">注册</Link></p></form></main>;
}
