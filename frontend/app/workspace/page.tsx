"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { authRequest, useAuth } from "../../lib/auth";

export default function WorkspacePage() { const router = useRouter(); const { user, refreshToken, clearSession } = useAuth(); useEffect(() => { if (!user?.id) router.replace("/login"); }, [user, router]); async function logout() { try { if (refreshToken) await authRequest<void>("/api/auth/logout", { refreshToken }); } finally { clearSession(); router.replace("/login"); } } if (!user?.id) return <main className="auth-shell"><p>正在检查登录状态...</p></main>; return <main className="shell"><header className="topbar"><div className="brand">CodeAgent OJ</div><button className="ghost" onClick={logout}>退出登录</button></header><section className="intro"><p className="eyebrow">WORKSPACE</p><h1>你好，{user.displayName}</h1><p>登录态已恢复。题库和做题工作台将在下一阶段接入。</p></section></main>; }
