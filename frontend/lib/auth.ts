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

export async function authRequest<T>(path: string, body: unknown): Promise<T> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const response = await fetch(`${base}${path}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.detail ?? "请求失败");
  return payload.data as T;
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const accessToken = useAuth.getState().accessToken;
  const response = await fetch(`${base}${path}`, { ...init, headers: { ...(init?.headers ?? {}), ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) } });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.detail ?? "请求失败");
  return payload.data as T;
}

export async function publicApiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const response = await fetch(`${base}${path}`, { ...init, headers: { ...(init?.headers ?? {}) } });
  const text = await response.text();
  let payload: { data?: T; detail?: string } = {};
  try { payload = text ? JSON.parse(text) : {}; } catch { throw new Error("题库服务返回了无效响应"); }
  if (!response.ok) throw new Error(payload.detail ?? "请求失败");
  return payload.data as T;
}
