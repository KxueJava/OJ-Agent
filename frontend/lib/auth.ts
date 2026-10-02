"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";

export type User = { id: number; username: string; email: string; displayName: string; role: "USER" | "ADMIN" };
type Tokens = { accessToken: string; refreshToken: string; expiresAt: string; user: User };
type AuthState = Tokens & { setSession: (session: Tokens) => void; clearSession: () => void };

export const useAuth = create<AuthState>()(persist((set) => ({
  accessToken: "", refreshToken: "", expiresAt: "", user: null as unknown as User,
  setSession: (session) => set(session),
  clearSession: () => set({ accessToken: "", refreshToken: "", expiresAt: "", user: null as unknown as User }),
}), { name: "codeagent-oj-auth" }));

const apiBase = () => process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** 把 HTTP 状态带进错误信息：Spring Security 的 401/403 响应体里没有 detail，旧写法会只剩一句"请求失败"，无从排查。 */
function failure(status: number, detail?: string) {
  if (detail) return new Error(detail);
  if (status === 401) return new Error("请求失败（HTTP 401）：登录态已失效，请重新登录");
  // 403 有多种成因（缺 ADMIN 角色、比赛期间禁用 Agent 助手…），所以文案保持中性并给出方向
  if (status === 403) return new Error("请求失败（HTTP 403）：没有权限执行此操作（可能是比赛期间禁用了 Agent 助手，或该功能需要 ADMIN 角色；提权后要重新登录）");
  return new Error(`请求失败（HTTP ${status}）`);
}

/**
 * 用 refresh token 换新的 access token。
 *
 * 存在的原因：access token 只有 30 分钟有效期，而此前前端从不续期 ——
 * 于是每 30 分钟用户就会被"自动退出登录"（用户实测反馈）。
 *
 * 单飞：并发的 401 只触发一次刷新，其余等同一个 Promise ——
 * 服务端刷新是"轮换式"的（旧 refresh token 会被吊销），并发刷新会把彼此作废。
 */
let refreshing: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  if (refreshing) return refreshing;
  refreshing = (async () => {
    const { refreshToken } = useAuth.getState();
    if (!refreshToken) return false;
    try {
      const response = await fetch(`${apiBase()}/api/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) { useAuth.getState().clearSession(); return false; }
      const payload = await response.json();
      const tokens = payload?.data as { accessToken?: string; refreshToken?: string; expiresAt?: string; user?: User } | undefined;
      if (!tokens?.accessToken) { useAuth.getState().clearSession(); return false; }
      useAuth.getState().setSession({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken ?? refreshToken,
        expiresAt: tokens.expiresAt ?? "",
        user: (tokens.user ?? useAuth.getState().user) as User,
      });
      return true;
    } catch {
      // 网络故障不要清会话：网络恢复后还能继续用，否则会误报"退出登录"
      return false;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

/** access token 快过期（< 60 秒）就先换一张：避免"闲置半小时后第一次点击就被登出"。 */
async function ensureFreshToken(): Promise<void> {
  const { accessToken, expiresAt } = useAuth.getState();
  if (!accessToken) return;
  const expires = expiresAt ? Date.parse(expiresAt) : 0;
  if (!expires || Number.isNaN(expires)) return;
  if (expires - Date.now() < 60_000) await refreshSession();
}

export async function authRequest<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${apiBase()}${path}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  const payload = await response.json();
  if (!response.ok) throw failure(response.status, payload.detail);
  return payload.data as T;
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  await ensureFreshToken();
  const send = async () => {
    const accessToken = useAuth.getState().accessToken;
    // 带 body 时必须声明 JSON：否则 Spring 的 @RequestBody 会直接返回 415 Unsupported Media Type
    return fetch(`${apiBase()}${path}`, {
      ...init,
      headers: {
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...(init?.headers ?? {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    });
  };

  let response = await send();
  if (response.status === 401) {
    // 兜底：主动续期没赶上（例如时钟漂移或服务端提前失效）时，刷新一次再重试原请求
    const refreshed = await refreshSession();
    if (refreshed) response = await send();
    else useAuth.getState().clearSession();
  }
  const payload = await response.json();
  if (!response.ok) throw failure(response.status, payload.detail);
  return payload.data as T;
}

export async function publicApiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase()}${path}`, { ...init, headers: { ...(init?.headers ?? {}) } });
  const text = await response.text();
  let payload: { data?: T; detail?: string } = {};
  try { payload = text ? JSON.parse(text) : {}; } catch { throw new Error("题库服务返回了无效响应"); }
  if (!response.ok) throw new Error(payload.detail ?? "请求失败");
  return payload.data as T;
}
