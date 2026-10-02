"use client";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { authRequest, useAuth } from "../../lib/auth";
import { Eye, EyeOff } from "lucide-react";
import { useAuthMessages } from "../../lib/messages/auth";

export default function LoginPage() {
  const t = useAuthMessages();
  const router = useRouter(); const setSession = useAuth((s) => s.setSession); const [identifier, setIdentifier] = useState(""); const [password, setPassword] = useState(""); const [showPassword, setShowPassword] = useState(false); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(""); try { const session = await authRequest<any>("/api/auth/login", { identifier, password }); setSession(session); router.push("/workspace"); } catch (e) { setError(e instanceof Error ? e.message : t("auth.loginFailed")); } finally { setBusy(false); } }
  return <main className="auth-shell"><form className="auth-card" onSubmit={submit}><p className="eyebrow">CODEAGENT OJ</p><h1>{t("auth.login")}</h1><p className="auth-muted">{t("auth.loginSubtitle")}</p><label>{t("auth.identifier")}<input required value={identifier} onChange={(e) => setIdentifier(e.target.value)} /></label><label>{t("auth.password")}<span className="password-field"><input required type={showPassword ? "text" : "password"} value={password} onChange={(e) => setPassword(e.target.value)} /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? t("auth.hidePassword") : t("auth.showPassword")} title={showPassword ? t("auth.hidePassword") : t("auth.showPassword")}>{showPassword ? <EyeOff size={16} /> : <Eye size={16} />}</button></span></label>{error && <p className="form-error">{error}</p>}<button className="primary auth-submit" disabled={busy}>{busy ? t("auth.signingIn") : t("auth.login")}</button><p className="auth-footer">{t("auth.noAccount")} <Link href="/register">{t("auth.register")}</Link></p></form></main>;
}
