"use client";

import { useEffect, useState } from "react";
import { apiRequest, useAuth } from "./auth";

/**
 * 竞赛期间的 Agent 锁（A1/A2 在服务端强制；这里只负责前端提示）。
 *
 * - 结果在模块级缓存 30 秒，避免每个用到它的组件各发一次请求；
 * - 未登录直接 unlocked，不发请求；
 * - 服务端才是真正的防线：即使前端被绕过，/api/agent/ask 与自动诊断也会拒绝。
 */
type LockState = { locked: boolean; slug?: string };
let cache: { at: number; state: LockState; userId?: number } | null = null;
const TTL_MS = 30_000;

/**
 * 从当前路径取题目 slug：/workbench/<slug>、/problems/<slug>、/solve/<slug>。
 * 桌宠挂在 template.tsx 上（每次路由切换都会重新挂载），所以这里读 location 是安全的。
 */
function currentProblemSlug(): string | undefined {
  if (typeof window === "undefined") return undefined;
  const match = window.location.pathname.match(/^\/(?:workbench|problems|solve|agent)\/([^/?#]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

export function useContestLock() {
  const userId = useAuth((state) => state.user?.id);
  const [state, setState] = useState<LockState>(cache && cache.userId === userId ? cache.state : { locked: false });

  useEffect(() => {
    if (!userId) { setState({ locked: false }); return; }
    if (cache && cache.userId === userId && Date.now() - cache.at < TTL_MS) { setState(cache.state); return; }
    let cancelled = false;
    const slug = currentProblemSlug();
    // 必须带 problemSlug：只有"这道题正是我在进行中比赛的赛题"才锁，否则普通题库的题也会被误锁
    const query = slug ? `?problemSlug=${encodeURIComponent(slug)}` : "";
    apiRequest<{ locked: boolean; slug?: string }>(`/api/contests/active${query}`)
      .then((next) => {
        const resolved = { locked: Boolean(next?.locked), slug: next?.slug };
        cache = { at: Date.now(), state: resolved, userId };
        if (!cancelled) setState(resolved);
      })
      .catch(() => { if (!cancelled) setState({ locked: false }); });
    return () => { cancelled = true; };
  }, [userId]);

  return state;
}
