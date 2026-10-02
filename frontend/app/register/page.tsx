"use client";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { authRequest, useAuth } from "../../lib/auth";
import { useAuthMessages } from "../../lib/messages/auth";

export default function RegisterPage() {
  const t = useAuthMessages();
  const router = useRouter(); const setSession = useAuth((s) => s.setSession); const [form, setForm] = useState({ username: "", email: "", password: "", displayName: "" }); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(""); try { const session = await authRequest<any>("/api/auth/register", form); setSession(session); router.push("/workspace"); } catch (e) { setError(e instanceof Error ? e.message : t("auth.registerFailed")); } finally { setBusy(false); } }
  return <main className="auth-shell"><form className="auth-card" onSubmit={submit}><p className="eyebrow">CODEAGENT OJ</p><h1>{t("auth.createAccount")}</h1><p className="auth-muted">{t("auth.registerSubtitle")}</p>{([ ["username", t("auth.username")], ["email", t("auth.email")], ["displayName", t("auth.displayName")]] as const).map(([key, label]) => <label key={key}>{label}<input required={key !== "displayName"} type={key === "email" ? "email" : "text"} value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} /></label>)}<label>{t("auth.password")}<input required minLength={8} type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>{error && <p className="form-error">{error}</p>}<button className="primary auth-submit" disabled={busy}>{busy ? t("auth.creatingAccount") : t("auth.createAccount")}</button><p className="auth-footer">{t("auth.haveAccount")} <Link href="/login">{t("auth.backToLogin")}</Link></p></form></main>;
}
