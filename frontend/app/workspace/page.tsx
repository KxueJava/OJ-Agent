"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { authRequest, useAuth } from "../../lib/auth";
import { useWorkspaceMessages } from "../../lib/messages/workspace";

export default function WorkspacePage() { const t = useWorkspaceMessages(); const router = useRouter(); const { user, refreshToken, clearSession } = useAuth(); useEffect(() => { if (!user?.id) router.replace("/login"); }, [user, router]); async function logout() { try { if (refreshToken) await authRequest<void>("/api/auth/logout", { refreshToken }); } finally { clearSession(); router.replace("/login"); } } if (!user?.id) return <main className="auth-shell"><p>{t("workspace.checkingSession")}</p></main>; return <main className="shell"><header className="topbar"><div className="brand">CodeAgent OJ</div><button className="ghost" onClick={logout}>{t("workspace.signOut")}</button></header><section className="intro"><p className="eyebrow">WORKSPACE</p><h1>{t("workspace.greeting")}{user.displayName}</h1><p>{t("workspace.sessionRestored")}</p></section></main>; }
